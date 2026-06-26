package dev.hemendra.claudewatch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;

/**
 * Opt-in fallback baseline from IntelliJ's Local History — accessed ONLY via
 * reflection so the (experimental/internal) {@code com.intellij.history} API is
 * never referenced at compile time. That keeps the plugin verifier clean; if the
 * API is absent or changed, this silently returns null.
 *
 * Returns the content of the most recent Local History revision strictly older than
 * {@code cutoffMillis} — i.e. the state just before the change our refresh detected.
 */
final class LocalHistoryBaseline {

    private LocalHistoryBaseline() {
    }

    static @Nullable String before(Project project, VirtualFile file, long cutoffMillis) {
        try {
            Class<?> lhClass = Class.forName("com.intellij.history.LocalHistory");
            Object lh = lhClass.getMethod("getInstance").invoke(null);

            Class<?> cmpClass = Class.forName("com.intellij.history.FileRevisionTimestampComparator");
            Object comparator = Proxy.newProxyInstance(
                    LocalHistoryBaseline.class.getClassLoader(),
                    new Class[]{cmpClass},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isSuitable" -> ((Long) args[0]) < cutoffMillis;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "ClaudeWatchRevisionComparator";
                        default -> null;
                    });

            Object content = lhClass
                    .getMethod("getByteContent", VirtualFile.class, cmpClass)
                    .invoke(lh, file, comparator);

            if (content instanceof byte[] bytes) {
                return new String(bytes, file.getCharset());
            }
            return null;
        } catch (Throwable t) {
            return null; // API missing/changed/disabled -> no baseline from history
        }
    }
}
