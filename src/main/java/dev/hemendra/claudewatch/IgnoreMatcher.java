package dev.hemendra.claudewatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Cheap path filter. Bare tokens ({@code node_modules}) match any path segment;
 * {@code *.ext} tokens match a filename suffix. Pure string ops, safe off-EDT.
 */
final class IgnoreMatcher {

    private final List<String> segments = new ArrayList<>();
    private final List<String> suffixes = new ArrayList<>();

    IgnoreMatcher(String patterns) {
        if (patterns == null) return;
        for (String raw : patterns.split("[\\s,]+")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("*.")) {
                suffixes.add(t.substring(1)); // ".ext"
            } else {
                segments.add(t);
            }
        }
    }

    boolean isIgnored(String path) {
        if (path == null) return true;
        String normalized = path.replace('\\', '/');
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix)) return true;
        }
        if (segments.isEmpty()) return false;
        // segment match against '/'-delimited path parts
        int start = 0;
        final int len = normalized.length();
        while (start <= len) {
            int slash = normalized.indexOf('/', start);
            int end = (slash == -1) ? len : slash;
            if (end > start) {
                String part = normalized.substring(start, end);
                if (segments.contains(part)) return true;
            }
            if (slash == -1) break;
            start = slash + 1;
        }
        return false;
    }
}
