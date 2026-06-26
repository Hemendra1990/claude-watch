package dev.hemendra.claudewatch;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, in-memory "last known content" per file path — our own baseline for
 * diffing/highlighting an external change even when the file was never opened and
 * the change is already saved to disk. Access-ordered LRU with a total byte budget.
 * Thread-safe (read on the pooled compute thread, primed on the EDT).
 */
public final class SnapshotService {

    private static final long MAX_BYTES = 16L * 1024 * 1024; // ~16 MB ceiling across all snapshots

    private final LinkedHashMap<String, String> map = new LinkedHashMap<>(64, 0.75f, true);
    private long bytes = 0;

    @SuppressWarnings("unused")
    public SnapshotService(Project project) {
    }

    public synchronized @Nullable String get(@NotNull String path) {
        return map.get(path);
    }

    public synchronized void put(@NotNull String path, @Nullable String text) {
        if (text == null) return;
        String old = map.remove(path);
        if (old != null) bytes -= weight(old);
        map.put(path, text);
        bytes += weight(text);
        evictToBudget();
    }

    public synchronized void remove(@NotNull String path) {
        String old = map.remove(path);
        if (old != null) bytes -= weight(old);
    }

    public synchronized void clear() {
        map.clear();
        bytes = 0;
    }

    private void evictToBudget() {
        Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
        while (bytes > MAX_BYTES && it.hasNext()) {
            Map.Entry<String, String> eldest = it.next(); // access-order => least recently used first
            bytes -= weight(eldest.getValue());
            it.remove();
        }
    }

    private static long weight(String s) {
        return (long) s.length() * 2; // ~2 bytes/char, good enough for budgeting
    }
}
