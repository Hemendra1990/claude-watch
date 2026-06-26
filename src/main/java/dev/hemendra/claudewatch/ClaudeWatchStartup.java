package dev.hemendra.claudewatch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Eagerly instantiates and starts {@link ClaudeWatchService} when a project opens.
 * Implements the Kotlin {@code ProjectActivity} suspend interface from Java by
 * returning {@code Unit.INSTANCE} without suspending.
 */
public final class ClaudeWatchStartup implements ProjectActivity {
    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        project.getService(ClaudeWatchService.class).start();
        return Unit.INSTANCE;
    }
}
