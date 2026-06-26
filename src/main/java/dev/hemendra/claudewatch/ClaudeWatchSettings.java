package dev.hemendra.claudewatch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/** Persisted, application-wide settings for Claude Watch. */
@State(name = "ClaudeWatchSettings", storages = @Storage("claude-watch.xml"))
public final class ClaudeWatchSettings implements PersistentStateComponent<ClaudeWatchSettings> {

    public boolean autoOpen = true;
    public boolean stealFocus = true;
    public boolean highlight = true;
    public boolean notify = true;
    public int fadeMs = 5000;
    // volatile: read on background threads (refresh loop, prepareChange, pooled compute), written on EDT.
    public volatile int refreshIntervalMs = 1500;
    public int burstWindowMs = 400;
    public int maxHistory = 100;
    public int maxOpenPerBurst = 5;
    public volatile int diffSizeCapKb = 512;
    public volatile boolean paused = false;
    /** Opt-in: fall back to IntelliJ Local History for the "before" content of files never seen by the plugin. */
    public volatile boolean useLocalHistory = false;

    /** Newline/space separated ignore patterns: bare names match a path segment, {@code *.ext} matches suffix. */
    public volatile String ignorePatterns =
            ".git node_modules target build .idea out dist .gradle .next .venv __pycache__ *.class *.lock";

    public static ClaudeWatchSettings getInstance() {
        return ApplicationManager.getApplication().getService(ClaudeWatchSettings.class);
    }

    @Override
    public @NotNull ClaudeWatchSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ClaudeWatchSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
