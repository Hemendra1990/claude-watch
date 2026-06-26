package dev.hemendra.claudewatch;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator. Drives VFS refresh on an interval (beating the focus-only refresh
 * limitation), listens for external changes, coalesces bursts, computes diffs, and
 * dispatches open / highlight / notify / history. One per open project.
 */
public final class ClaudeWatchService implements Disposable {

    private final Project project;
    private final EditorOpener opener;
    private final Alarm refreshAlarm;
    private final Alarm burstAlarm;
    private final List<Pending> buffer = new ArrayList<>(); // EDT-confined
    private volatile boolean disposed = false;

    public ClaudeWatchService(@NotNull Project project) {
        this.project = project;
        this.opener = new EditorOpener(project, this);
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        this.burstAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    }

    /** Called once from the startup activity. */
    void start() {
        VirtualFileManager.getInstance().addAsyncFileListener(new Listener(), this);
        scheduleRefresh();
    }

    private static ClaudeWatchSettings settings() {
        return ClaudeWatchSettings.getInstance();
    }

    // ---- RefreshDriver: the fix for VFS focus-only refresh ------------------

    private void scheduleRefresh() {
        if (disposed) return;
        refreshAlarm.addRequest(this::refreshAndReschedule, Math.max(250, settings().refreshIntervalMs));
    }

    private void refreshAndReschedule() {
        try {
            if (disposed || project.isDisposed()) return;
            VirtualFile[] roots = ApplicationManager.getApplication().runReadAction(
                    (Computable<VirtualFile[]>) () -> project.isDisposed() ? new VirtualFile[0]
                            : ProjectRootManager.getInstance(project).getContentRoots());
            if (roots.length > 0) {
                VfsUtil.markDirtyAndRefresh(true, true, true, roots);
            }
        } catch (Throwable ignored) {
            // never let the heartbeat die
        } finally {
            scheduleRefresh();
        }
    }

    // ---- Detection ----------------------------------------------------------

    private final class Listener implements AsyncFileListener {
        @Override
        public @Nullable ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
            List<String> roots = projectRootPaths();   // all content roots (+ base)
            if (roots.isEmpty()) return null;
            ClaudeWatchSettings s = settings();
            IgnoreMatcher matcher = new IgnoreMatcher(s.ignorePatterns);
            FileDocumentManager fdm = FileDocumentManager.getInstance();

            List<Pending> pending = new ArrayList<>();
            for (VFileEvent e : events) {
                if (!e.isFromRefresh()) continue;          // external changes only
                String path = e.getPath();
                if (path == null) continue;
                String root = matchRoot(roots, path);
                if (root == null) continue;                // outside this project's content
                if (matcher.isIgnored(path.substring(root.length()))) continue;  // project-relative match

                if (e instanceof VFileContentChangeEvent ce) {
                    VirtualFile f = ce.getFile();
                    if (f.isDirectory()) continue;
                    pending.add(new Pending(path, f.getName(), ChangeRecord.Type.MODIFY,
                            cachedOldText(fdm, f, s.diffSizeCapKb), f));
                } else if (e instanceof VFileCreateEvent createEvent) {
                    if (createEvent.isDirectory()) continue;
                    pending.add(new Pending(path, nameOf(path), ChangeRecord.Type.CREATE, "", null));
                } else if (e instanceof VFileDeleteEvent de) {
                    VirtualFile f = de.getFile();
                    if (f.isDirectory()) continue;
                    pending.add(new Pending(path, f.getName(), ChangeRecord.Type.DELETE,
                            cachedOldText(fdm, f, s.diffSizeCapKb), null));
                }
            }
            if (pending.isEmpty()) return null;

            return new ChangeApplier() {
                @Override
                public void afterVfsChange() {     // EDT
                    if (disposed) return;
                    buffer.addAll(pending);
                    burstAlarm.cancelAllRequests();
                    burstAlarm.addRequest(ClaudeWatchService.this::flushBurst,
                            Math.max(50, settings().burstWindowMs));
                }
            };
        }
    }

    // ---- Burst coalescing + diff compute (off EDT) --------------------------

    private void flushBurst() {                    // EDT
        if (disposed || buffer.isEmpty()) return;
        List<Pending> batch = new ArrayList<>(buffer);
        buffer.clear();
        ApplicationManager.getApplication().executeOnPooledThread(() -> compute(batch));
    }

    private void compute(List<Pending> batch) {    // pooled thread
        int capKb = settings().diffSizeCapKb;
        List<Computed> out = new ArrayList<>();
        for (Pending p : batch) {
            if (disposed) return;
            VirtualFile file = p.file != null ? p.file : LocalFileSystem.getInstance().findFileByPath(p.path);
            String oldText = p.oldText;
            String newText = p.type == ChangeRecord.Type.DELETE ? "" : readText(file, capKb);

            // no-op refresh: only suppress MODIFY whose cached old content equals the new content.
            // CREATE/DELETE always survive; unopened files have oldText == null and are never dropped.
            if (p.type == ChangeRecord.Type.MODIFY && oldText != null && oldText.equals(newText)) continue;

            int added = -1, removed = 0, firstLine = 0;   // -1 = change detected but not diffed
            List<Integer> changedLines = new ArrayList<>();
            if (oldText != null && newText != null && p.type != ChangeRecord.Type.DELETE) {
                added = 0;
                try {
                    List<LineFragment> frags = ComparisonManager.getInstance().compareLines(
                            oldText, newText, ComparisonPolicy.DEFAULT, new EmptyProgressIndicator());
                    boolean first = true;
                    for (LineFragment f : frags) {
                        added += (f.getEndLine2() - f.getStartLine2());
                        removed += (f.getEndLine1() - f.getStartLine1());
                        for (int l = f.getStartLine2(); l < f.getEndLine2(); l++) changedLines.add(l);
                        if (first) { firstLine = f.getStartLine2(); first = false; }
                    }
                } catch (Throwable ignored) {
                    // diff failed (binary, huge) -> still open + record, no highlight
                }
            }

            ChangeRecord record = new ChangeRecord(System.currentTimeMillis(), p.path, p.name,
                    p.type, added, removed, firstLine, oldText);
            out.add(new Computed(record, changedLines, file));
        }
        if (!out.isEmpty()) {
            ApplicationManager.getApplication().invokeLater(() -> dispatch(out), project.getDisposed());
        }
    }

    // ---- Dispatch (EDT) -----------------------------------------------------

    private void dispatch(List<Computed> computed) {
        if (disposed || project.isDisposed()) return;
        ClaudeWatchSettings s = settings();
        HistoryService history = project.getService(HistoryService.class);

        List<ChangeRecord> records = new ArrayList<>(computed.size());
        for (Computed c : computed) {
            history.add(c.record);                 // always record, even when paused
            records.add(c.record);
        }

        if (s.paused) return;                      // muted: no open, no balloon

        if (s.autoOpen) {
            List<Computed> openable = new ArrayList<>();
            for (Computed c : computed) {
                if (c.record.type() != ChangeRecord.Type.DELETE && c.file != null && c.file.isValid()) {
                    openable.add(c);
                }
            }
            int n = Math.min(Math.max(1, s.maxOpenPerBurst), openable.size());
            for (int i = 0; i < n; i++) {
                Computed c = openable.get(i);
                boolean focus = s.stealFocus && (i == n - 1);  // focus only the last to avoid thrash
                opener.open(c.file, c.record, c.changedLines, focus);
            }
        }

        if (s.notify) {
            Notifier.notifyChanges(project, records);
        }
    }

    // ---- helpers ------------------------------------------------------------

    /** Absolute paths of all content roots (plus base) for this project; empty if disposed. */
    private List<String> projectRootPaths() {
        return ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () -> {
            if (project.isDisposed()) return List.<String>of();
            List<String> rs = new ArrayList<>();
            for (VirtualFile r : ProjectRootManager.getInstance(project).getContentRoots()) {
                rs.add(r.getPath());
            }
            String bp = project.getBasePath();
            if (bp != null && !rs.contains(bp)) rs.add(bp);
            return rs;
        });
    }

    private static @Nullable String matchRoot(List<String> roots, String path) {
        for (String r : roots) {
            if (path.equals(r) || path.startsWith(r + "/")) return r;
        }
        return null;
    }

    /**
     * Authoritative pre-change content: the cached editor Document (not yet reloaded at
     * prepareChange time). Returns null when the file is not loaded — {@code contentsToByteArray}
     * would read through to disk, which already holds the NEW content, so it must NOT be trusted
     * as "old". Null oldText means the change is never dropped by the no-op guard.
     */
    private static @Nullable String cachedOldText(FileDocumentManager fdm, @Nullable VirtualFile f, int capKb) {
        if (f == null || !f.isValid() || f.isDirectory()) return null;
        if (f.getLength() > (long) capKb * 1024) return null;
        Document doc = fdm.getCachedDocument(f);
        if (doc == null) return null;
        try {
            return ApplicationManager.getApplication().runReadAction((Computable<String>) doc::getText);
        } catch (Throwable t) {
            return null;
        }
    }

    private static @Nullable String readText(@Nullable VirtualFile f, int capKb) {
        if (f == null || !f.isValid() || f.isDirectory()) return null;
        if (f.getLength() > (long) capKb * 1024) return null;
        try {
            return new String(f.contentsToByteArray(), f.getCharset());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    @Override
    public void dispose() {
        disposed = true;
        buffer.clear();
        // Alarms are parented to this Disposable and cancel automatically.
    }

    private record Pending(String path, String name, ChangeRecord.Type type,
                           @Nullable String oldText, @Nullable VirtualFile file) {
    }

    private record Computed(ChangeRecord record, List<Integer> changedLines, @Nullable VirtualFile file) {
    }
}
