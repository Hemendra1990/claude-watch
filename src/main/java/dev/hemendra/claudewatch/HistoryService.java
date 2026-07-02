package dev.hemendra.claudewatch;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bounded, newest-first history of detected changes. Notifies listeners (tool
 * window, status bar) on every mutation. EDT-confined writes via the orchestrator.
 * Pre-change content ({@code oldText}) is retained only for the most recent
 * {@link #KEEP_CONTENT} records to bound memory; older records keep metadata only.
 */
public final class HistoryService {

    private static final int KEEP_CONTENT = 25;

    public interface Listener {
        void historyChanged();
    }

    private final List<ChangeRecord> records = new ArrayList<>(); // index 0 = newest
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private long totalSeen = 0;

    @SuppressWarnings("unused")
    public HistoryService(Project project) {
    }

    public void add(@NotNull ChangeRecord record) {
        addAll(List.of(record));
    }

    /** Batch insert with a single listener notification — bursts fire UI updates once, not per record. */
    public void addAll(@NotNull List<ChangeRecord> newRecords) {
        if (newRecords.isEmpty()) return;
        int cap = Math.max(1, ClaudeWatchSettings.getInstance().maxHistory);
        for (ChangeRecord record : newRecords) {
            records.add(0, record);
            totalSeen++;
        }
        // drop retained content on records that just aged out of the content window
        int limit = Math.min(records.size(), KEEP_CONTENT + newRecords.size());
        for (int i = KEEP_CONTENT; i < limit; i++) {
            ChangeRecord aged = records.get(i);
            if (aged.oldText() != null) records.set(i, aged.withoutOldText());
        }
        while (records.size() > cap) {
            records.remove(records.size() - 1);
        }
        fire();
    }

    public void clear() {
        records.clear();
        fire();
    }

    public @NotNull List<ChangeRecord> snapshot() {
        return new ArrayList<>(records);
    }

    public long totalSeen() {
        return totalSeen;
    }

    public void addListener(@NotNull Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NotNull Listener listener) {
        listeners.remove(listener);
    }

    private void fire() {
        for (Listener l : listeners) {
            l.historyChanged();
        }
    }
}
