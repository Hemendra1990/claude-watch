package dev.hemendra.claudewatch;

import org.jetbrains.annotations.Nullable;

/**
 * One detected external change. Immutable. {@code oldText} is the pre-change
 * content captured in {@code AsyncFileListener.prepareChange} so we can diff and
 * highlight later; it may be null (new file, too large, or read failed).
 */
public record ChangeRecord(
        long timestamp,
        String path,
        String name,
        Type type,
        int added,
        int removed,
        int firstChangedLine,
        @Nullable String oldText
) {
    public enum Type { CREATE, MODIFY, DELETE }

    /** added == -1 is a sentinel: change detected but not diffed (file not open, size-capped, or binary). */
    public String deltaText() {
        if (type == Type.DELETE) return "deleted";
        if (added < 0) return type == Type.CREATE ? "new" : "changed";
        if (type == Type.CREATE) return "new (+" + added + ")";
        return "+" + added + " −" + removed;
    }

    /** Copy without the retained pre-change content, to bound history memory. */
    public ChangeRecord withoutOldText() {
        return new ChangeRecord(timestamp, path, name, type, added, removed, firstChangedLine, null);
    }
}
