package dev.hemendra.claudewatch;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
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

    /** Every Nth tick does a full recursive markDirty scan (catches volumes the native
     *  watcher misses); the other ticks only flush watcher-marked dirty files — cheap. */
    private static final int DEEP_SCAN_EVERY_N_TICKS = 10;
    /** Upper bound on highlight ranges kept per change; beyond this we still open + record. */
    private static final int MAX_HIGHLIGHT_RANGES = 500;

    private final Project project;
    private final EditorOpener opener;
    private final SnapshotService snapshots;
    private final Alarm refreshAlarm;
    private final Alarm burstAlarm;
    private final List<Pending> buffer = new ArrayList<>(); // EDT-confined
    private volatile boolean disposed = false;

    // Caches for the prepareChange hot path (it sees every VFS event batch IDE-wide).
    private volatile List<String> cachedRoots = List.of();   // refreshed once per tick
    private volatile MatcherCache matcherCache;              // rebuilt only when patterns change
    private int ticksSinceDeepScan = 0;                      // pooled refresh thread only

    public ClaudeWatchService(@NotNull Project project) {
        this.project = project;
        this.opener = new EditorOpener(project, this);
        this.snapshots = project.getService(SnapshotService.class);
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        this.burstAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    }

    /** Called once from the startup activity. */
    void start() {
        cachedRoots = projectRootPaths();
        VirtualFileManager.getInstance().addAsyncFileListener(new Listener(), this);
        // Prime a baseline whenever a file is opened, so a later external edit highlights correctly first time.
        project.getMessageBus().connect(this).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        if (file.isValid() && !file.isDirectory()
                                && file.getLength() <= (long) settings().diffSizeCapKb * 1024) {
                            Document doc = FileDocumentManager.getInstance().getDocument(file);
                            if (doc != null) snapshots.put(file.getPath(), doc.getText());
                        }
                    }
                });
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
            cachedRoots = projectRootPaths();
            if (++ticksSinceDeepScan >= DEEP_SCAN_EVERY_N_TICKS) {
                ticksSinceDeepScan = 0;
                VirtualFile[] roots = ApplicationManager.getApplication().runReadAction(
                        (Computable<VirtualFile[]>) () -> project.isDisposed() ? new VirtualFile[0]
                                : ProjectRootManager.getInstance(project).getContentRoots());
                if (roots.length > 0) {
                    VfsUtil.markDirtyAndRefresh(true, true, true, roots);
                }
            } else {
                // Cheap tick: only process files the native watcher already marked dirty.
                // This is what actually beats the focus-only refresh; the deep scan above
                // is a periodic safety net for roots the watcher can't cover.
                VirtualFileManager.getInstance().asyncRefresh(null);
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
            // Hot path: called for every VFS event batch IDE-wide. Bail before any real work
            // when the batch contains no refresh-originated (external) events at all.
            boolean anyExternal = false;
            for (VFileEvent e : events) {
                if (e.isFromRefresh()) { anyExternal = true; break; }
            }
            if (!anyExternal) return null;

            List<String> roots = cachedRoots;          // all content roots (+ base), tick-refreshed
            if (roots.isEmpty()) return null;
            IgnoreMatcher matcher = matcher(settings().ignorePatterns);
            FileDocumentManager fdm = FileDocumentManager.getInstance();
            int capKb = settings().diffSizeCapKb;

            List<Pending> pending = new ArrayList<>();
            for (VFileEvent e : events) {
                if (!e.isFromRefresh()) continue;          // external changes only
                String path = e.getPath();
                String root = matchRoot(roots, path);
                if (root == null) continue;                // outside this project's content
                if (matcher.isIgnored(path.substring(root.length()))) continue;  // project-relative match

                if (e instanceof VFileContentChangeEvent ce) {
                    VirtualFile f = ce.getFile();
                    if (f.isDirectory()) continue;
                    pending.add(new Pending(path, f.getName(), ChangeRecord.Type.MODIFY,
                            cachedOldText(fdm, f, capKb), f));
                } else if (e instanceof VFileCreateEvent createEvent) {
                    if (createEvent.isDirectory()) continue;
                    pending.add(new Pending(path, nameOf(path), ChangeRecord.Type.CREATE, "", null));
                } else if (e instanceof VFileDeleteEvent de) {
                    VirtualFile f = de.getFile();
                    if (f.isDirectory()) continue;
                    pending.add(new Pending(path, f.getName(), ChangeRecord.Type.DELETE,
                            cachedOldText(fdm, f, capKb), null));
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
        ClaudeWatchSettings s = settings();
        int capKb = s.diffSizeCapKb;
        List<Computed> out = new ArrayList<>();
        for (Pending p : batch) {
            if (disposed) return;
            VirtualFile file = p.file != null ? p.file : LocalFileSystem.getInstance().findFileByPath(p.path);
            String newText = p.type == ChangeRecord.Type.DELETE ? "" : readText(file, capKb);

            // Resolve the "before" content: our snapshot store (durable, survives close/save)
            // -> cached editor Document captured pre-change -> optional Local History fallback.
            String baseline = snapshots.get(p.path);
            if (baseline == null) baseline = p.oldText;
            if (baseline == null && s.useLocalHistory && file != null && p.type != ChangeRecord.Type.DELETE) {
                long cutoff = System.currentTimeMillis() - Math.max(2000L, s.refreshIntervalMs * 2L);
                baseline = LocalHistoryBaseline.before(project, file, cutoff);
            }

            // no-op refresh: only suppress MODIFY whose known old content equals the new content.
            if (p.type == ChangeRecord.Type.MODIFY && baseline != null && baseline.equals(newText)) {
                snapshots.put(p.path, newText); // keep baseline fresh even on a no-op
                continue;
            }

            int added = -1, removed = 0, firstLine = 0;   // -1 = change detected but not diffed
            List<int[]> changedRanges = new ArrayList<>(); // [startLine, endLine) on the new side
            if (baseline != null && newText != null && p.type != ChangeRecord.Type.DELETE) {
                added = 0;
                try {
                    List<LineFragment> frags = ComparisonManager.getInstance().compareLines(
                            baseline, newText, ComparisonPolicy.DEFAULT, new EmptyProgressIndicator());
                    boolean first = true;
                    for (LineFragment f : frags) {
                        added += (f.getEndLine2() - f.getStartLine2());
                        removed += (f.getEndLine1() - f.getStartLine1());
                        if (f.getEndLine2() > f.getStartLine2() && changedRanges.size() < MAX_HIGHLIGHT_RANGES) {
                            changedRanges.add(new int[]{f.getStartLine2(), f.getEndLine2()});
                        }
                        if (first) { firstLine = f.getStartLine2(); first = false; }
                    }
                } catch (Throwable ignored) {
                    // diff failed (binary, huge) -> still open + record, no highlight
                }
            }

            // Update the baseline for next time.
            if (p.type == ChangeRecord.Type.DELETE) snapshots.remove(p.path);
            else if (newText != null) snapshots.put(p.path, newText);

            ChangeRecord record = new ChangeRecord(System.currentTimeMillis(), p.path, p.name,
                    p.type, added, removed, firstLine, baseline);
            out.add(new Computed(record, changedRanges, file));
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
            records.add(c.record);
        }
        history.addAll(records);                   // always record, even when paused; single fire

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
                opener.open(c.file, c.record, c.changedRanges, focus);
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

    /** Reuse the parsed IgnoreMatcher until the pattern string actually changes. */
    private IgnoreMatcher matcher(String patterns) {
        MatcherCache c = matcherCache;
        if (c == null || !c.patterns.equals(patterns)) {
            c = new MatcherCache(patterns, new IgnoreMatcher(patterns));
            matcherCache = c;
        }
        return c.matcher;
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

    private record Computed(ChangeRecord record, List<int[]> changedRanges, @Nullable VirtualFile file) {
    }

    private record MatcherCache(String patterns, IgnoreMatcher matcher) {
    }
}
