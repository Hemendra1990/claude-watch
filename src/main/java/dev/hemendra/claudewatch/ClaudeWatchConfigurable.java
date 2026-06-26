package dev.hemendra.claudewatch;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;

/** Tools > Claude Watch settings panel. */
public final class ClaudeWatchConfigurable implements Configurable {

    private JBCheckBox autoOpen;
    private JBCheckBox stealFocus;
    private JBCheckBox highlight;
    private JBCheckBox notify;
    private JBCheckBox paused;
    private JBCheckBox useLocalHistory;
    private JBIntSpinner refreshMs;
    private JBIntSpinner burstMs;
    private JBIntSpinner fadeMs;
    private JBIntSpinner maxHistory;
    private JBIntSpinner maxOpen;
    private JBIntSpinner diffCapKb;
    private JBTextArea ignore;
    private JComponent panel;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Claude Watch";
    }

    @Override
    public @Nullable JComponent createComponent() {
        autoOpen = new JBCheckBox("Auto-open changed files");
        stealFocus = new JBCheckBox("Steal focus + jump to first changed line");
        highlight = new JBCheckBox("Highlight changed lines (fading)");
        notify = new JBCheckBox("Show notifications");
        paused = new JBCheckBox("Paused (record only, do not open)");
        useLocalHistory = new JBCheckBox("Use IntelliJ Local History as a fallback baseline (for files never opened)");
        refreshMs = new JBIntSpinner(1500, 250, 60000);
        burstMs = new JBIntSpinner(400, 50, 10000);
        fadeMs = new JBIntSpinner(5000, 500, 60000);
        maxHistory = new JBIntSpinner(100, 1, 1000);
        maxOpen = new JBIntSpinner(5, 1, 100);
        diffCapKb = new JBIntSpinner(512, 1, 100000);
        ignore = new JBTextArea(5, 40);
        ignore.setLineWrap(true);
        ignore.setWrapStyleWord(true);

        panel = FormBuilder.createFormBuilder()
                .addComponent(autoOpen)
                .addComponent(stealFocus)
                .addComponent(highlight)
                .addComponent(notify)
                .addComponent(paused)
                .addComponent(useLocalHistory)
                .addLabeledComponent("Refresh interval (ms):", refreshMs)
                .addLabeledComponent("Burst window (ms):", burstMs)
                .addLabeledComponent("Highlight fade (ms):", fadeMs)
                .addLabeledComponent("Max history rows:", maxHistory)
                .addLabeledComponent("Max files opened per burst:", maxOpen)
                .addLabeledComponent("Diff size cap (KB):", diffCapKb)
                .addLabeledComponentFillVertically("Ignore patterns (space/newline):", new JBScrollPane(ignore))
                .getPanel();

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        ClaudeWatchSettings s = ClaudeWatchSettings.getInstance();
        return autoOpen.isSelected() != s.autoOpen
                || stealFocus.isSelected() != s.stealFocus
                || highlight.isSelected() != s.highlight
                || notify.isSelected() != s.notify
                || paused.isSelected() != s.paused
                || useLocalHistory.isSelected() != s.useLocalHistory
                || refreshMs.getNumber() != s.refreshIntervalMs
                || burstMs.getNumber() != s.burstWindowMs
                || fadeMs.getNumber() != s.fadeMs
                || maxHistory.getNumber() != s.maxHistory
                || maxOpen.getNumber() != s.maxOpenPerBurst
                || diffCapKb.getNumber() != s.diffSizeCapKb
                || !ignore.getText().trim().equals(s.ignorePatterns.trim());
    }

    @Override
    public void apply() {
        ClaudeWatchSettings s = ClaudeWatchSettings.getInstance();
        s.autoOpen = autoOpen.isSelected();
        s.stealFocus = stealFocus.isSelected();
        s.highlight = highlight.isSelected();
        s.notify = notify.isSelected();
        s.paused = paused.isSelected();
        s.useLocalHistory = useLocalHistory.isSelected();
        s.refreshIntervalMs = refreshMs.getNumber();
        s.burstWindowMs = burstMs.getNumber();
        s.fadeMs = fadeMs.getNumber();
        s.maxHistory = maxHistory.getNumber();
        s.maxOpenPerBurst = maxOpen.getNumber();
        s.diffSizeCapKb = diffCapKb.getNumber();
        s.ignorePatterns = ignore.getText().trim();
    }

    @Override
    public void reset() {
        ClaudeWatchSettings s = ClaudeWatchSettings.getInstance();
        autoOpen.setSelected(s.autoOpen);
        stealFocus.setSelected(s.stealFocus);
        highlight.setSelected(s.highlight);
        notify.setSelected(s.notify);
        paused.setSelected(s.paused);
        useLocalHistory.setSelected(s.useLocalHistory);
        refreshMs.setNumber(s.refreshIntervalMs);
        burstMs.setNumber(s.burstWindowMs);
        fadeMs.setNumber(s.fadeMs);
        maxHistory.setNumber(s.maxHistory);
        maxOpen.setNumber(s.maxOpenPerBurst);
        diffCapKb.setNumber(s.diffSizeCapKb);
        ignore.setText(s.ignorePatterns);
    }
}
