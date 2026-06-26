package dev.hemendra.claudewatch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

/** Status-bar pill: live change count + paused indicator. Click toggles pause. */
final class ClaudeStatusBarWidget
        implements StatusBarWidget, StatusBarWidget.TextPresentation, HistoryService.Listener {

    static final String ID = "ClaudeWatch";

    private final HistoryService history;
    private @Nullable StatusBar statusBar;

    ClaudeStatusBarWidget(@NotNull Project project) {
        this.history = project.getService(HistoryService.class);
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
        history.addListener(this);
    }

    @Override
    public void dispose() {
        history.removeListener(this);
    }

    @Override
    public void historyChanged() {
        if (statusBar != null) statusBar.updateWidget(ID);
    }

    // ---- TextPresentation ---------------------------------------------------

    @Override
    public @NotNull String getText() {
        boolean paused = ClaudeWatchSettings.getInstance().paused;
        String icon = paused ? "⏸" : "●";   // ⏸ / ●
        return icon + " Claude " + history.totalSeen();
    }

    @Override
    public float getAlignment() {
        return 0f;
    }

    @Override
    public @Nullable String getTooltipText() {
        boolean paused = ClaudeWatchSettings.getInstance().paused;
        return (paused ? "Claude Watch paused — recording only. " : "Claude Watch active. ")
                + "Click to " + (paused ? "resume" : "pause") + " auto-open.";
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
        return e -> {
            ClaudeWatchSettings s = ClaudeWatchSettings.getInstance();
            s.paused = !s.paused;
            if (statusBar != null) statusBar.updateWidget(ID);
        };
    }
}
