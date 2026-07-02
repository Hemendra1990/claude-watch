package dev.hemendra.claudewatch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Opens the changed file, jumps to the first change, paints a fading line highlight. EDT only. */
final class EditorOpener {

    private static final Color ADD_COLOR = new JBColor(new Color(0x3FB950), new Color(0x238636));
    private static final Color MOD_COLOR = new JBColor(new Color(0x539BF5), new Color(0x1F6FEB));
    private static final int FADE_STEPS = 8;

    private final Project project;
    private final Disposable parent;

    EditorOpener(@NotNull Project project, @NotNull Disposable parent) {
        this.project = project;
        this.parent = parent;
    }

    /** @param changedRanges 0-based [startLine, endLine) ranges on the new side; empty -> just open + jump. */
    void open(@NotNull VirtualFile file, @NotNull ChangeRecord record,
              @NotNull List<int[]> changedRanges, boolean focus) {
        if (project.isDisposed() || !file.isValid()) return;

        int line = Math.max(0, record.firstChangedLine());
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, line, 0);
        Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, focus);
        if (editor == null) return;

        ClaudeWatchSettings settings = ClaudeWatchSettings.getInstance();
        if (!settings.highlight || changedRanges.isEmpty()) return;

        Color base = record.type() == ChangeRecord.Type.CREATE ? ADD_COLOR : MOD_COLOR;
        Color bg = editor.getColorsScheme().getDefaultBackground();

        startFade(editor, changedRanges, base, bg, Math.max(500, settings.fadeMs));
    }

    private void startFade(Editor editor, List<int[]> ranges, Color base, Color bg, int fadeMs) {
        MarkupModel markup = editor.getMarkupModel();
        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
        int stepDelay = Math.max(40, fadeMs / FADE_STEPS);
        fadeStep(markup, ranges, base, bg, alarm, stepDelay, 0, new ArrayList<>());
    }

    private void fadeStep(MarkupModel markup, List<int[]> ranges, Color base, Color bg,
                          Alarm alarm, int stepDelay, int step, List<RangeHighlighter> current) {
        try {
            for (RangeHighlighter h : current) {
                if (h.isValid()) markup.removeHighlighter(h);
            }
            if (step >= FADE_STEPS) {
                Disposer.dispose(alarm);   // fade done: free the Disposable registration immediately
                return;
            }

            double ratio = (double) step / FADE_STEPS;    // 0 -> opaque, grows toward bg
            Color blended = ColorUtil.mix(base, bg, ratio);
            TextAttributes attrs = new TextAttributes();
            attrs.setBackgroundColor(blended);

            Document doc = markup.getDocument();
            int lineCount = doc.getLineCount();
            List<RangeHighlighter> next = new ArrayList<>(ranges.size());
            for (int[] r : ranges) {                       // one highlighter per contiguous run
                int startLine = r[0];
                int endLine = Math.min(r[1], lineCount) - 1;  // inclusive last line
                if (startLine < 0 || startLine >= lineCount || endLine < startLine) continue;
                next.add(markup.addRangeHighlighter(
                        doc.getLineStartOffset(startLine), doc.getLineEndOffset(endLine),
                        HighlighterLayer.SELECTION - 1, attrs, HighlighterTargetArea.LINES_IN_RANGE));
            }
            alarm.addRequest(
                    () -> fadeStep(markup, ranges, base, bg, alarm, stepDelay, step + 1, next),
                    stepDelay);
        } catch (RuntimeException ignored) {
            // editor/document gone mid-fade; nothing to clean up beyond the removals above
            Disposer.dispose(alarm);
        }
    }
}
