package dev.hemendra.claudewatch;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Timeline of the last N external changes. Double-click opens; right-click shows diff. */
final class ClaudeWatchToolWindowPanel extends SimpleToolWindowPanel implements Disposable, HistoryService.Listener {

    private final Project project;
    private final HistoryService history;
    private final ChangeTableModel model = new ChangeTableModel();
    private final JBTable table = new JBTable(model);

    ClaudeWatchToolWindowPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;
        this.history = project.getService(HistoryService.class);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(2).setMaxWidth(120);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
            @Override public void mousePressed(MouseEvent e) { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
        });

        setToolbar(buildToolbar().getComponent());
        setContent(new JBScrollPane(table));

        history.addListener(this);
        reload();
    }

    private ActionToolbar buildToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ToggleAction("Pause Auto-Open", "Stop opening files (still records the timeline)", AllIcons.Actions.Pause) {
            @Override public boolean isSelected(@NotNull AnActionEvent e) {
                return ClaudeWatchSettings.getInstance().paused;
            }
            @Override public void setSelected(@NotNull AnActionEvent e, boolean state) {
                ClaudeWatchSettings.getInstance().paused = state;
            }
            @Override public @NotNull com.intellij.openapi.actionSystem.ActionUpdateThread getActionUpdateThread() {
                return com.intellij.openapi.actionSystem.ActionUpdateThread.EDT;
            }
        });
        group.add(new AnAction("Clear", "Clear the timeline", AllIcons.Actions.GC) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) { history.clear(); }
            @Override public @NotNull com.intellij.openapi.actionSystem.ActionUpdateThread getActionUpdateThread() {
                return com.intellij.openapi.actionSystem.ActionUpdateThread.BGT;
            }
        });
        group.add(new AnAction("Settings", "Open Claude Watch settings", AllIcons.General.Settings) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, ClaudeWatchConfigurable.class);
            }
            @Override public @NotNull com.intellij.openapi.actionSystem.ActionUpdateThread getActionUpdateThread() {
                return com.intellij.openapi.actionSystem.ActionUpdateThread.BGT;
            }
        });
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ClaudeWatch", group, true);
        toolbar.setTargetComponent(this);
        return toolbar;
    }

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) return;
        table.getSelectionModel().setSelectionInterval(row, row);
        ChangeRecord r = model.at(row);
        if (r == null) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(a -> open(r));
        menu.add(open);
        if (r.type() != ChangeRecord.Type.DELETE) {
            JMenuItem diff = new JMenuItem("Show Diff");
            diff.addActionListener(a -> DiffPresenter.show(project, r));
            menu.add(diff);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void openSelected() {
        ChangeRecord r = model.at(table.getSelectedRow());
        if (r != null) open(r);
    }

    private void open(ChangeRecord r) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(r.path());
        if (file == null || !file.isValid()) return;
        FileEditorManager.getInstance(project).openTextEditor(
                new OpenFileDescriptor(project, file, Math.max(0, r.firstChangedLine()), 0), true);
    }

    @Override
    public void historyChanged() {
        ApplicationManager.getApplication().invokeLater(this::reload, project.getDisposed());
    }

    private void reload() {
        model.setRows(history.snapshot());
    }

    @Override
    public void dispose() {
        history.removeListener(this);
    }

    // ---- table model --------------------------------------------------------

    private static final class ChangeTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "File", "Change", "Path"};
        private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
        private List<ChangeRecord> rows = new ArrayList<>();

        void setRows(List<ChangeRecord> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Nullable ChangeRecord at(int row) {
            return (row >= 0 && row < rows.size()) ? rows.get(row) : null;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ChangeRecord r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> fmt.format(new Date(r.timestamp()));
                case 1 -> r.name();
                case 2 -> r.deltaText();
                default -> r.path();
            };
        }
    }
}
