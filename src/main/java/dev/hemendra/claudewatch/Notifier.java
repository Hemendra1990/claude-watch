package dev.hemendra.claudewatch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/** Balloon notifications. One change -> Open / Show Diff. A burst -> summary + timeline link. */
final class Notifier {

    static final String GROUP_ID = "Claude Watch";
    static final String TOOL_WINDOW_ID = "Claude Watch";

    private Notifier() {
    }

    static void notifyChanges(@NotNull Project project, @NotNull List<ChangeRecord> records) {
        if (records.isEmpty()) return;
        var group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID);

        Notification notification;
        if (records.size() == 1) {
            ChangeRecord r = records.get(0);
            notification = group.createNotification(
                    "Claude " + verb(r) + " " + r.name(), r.deltaText(), NotificationType.INFORMATION);
            notification.addAction(NotificationAction.createSimple("Open", () -> open(project, r)));
            if (r.type() != ChangeRecord.Type.DELETE) {
                notification.addAction(
                        NotificationAction.createSimple("Show Diff", () -> DiffPresenter.show(project, r)));
            }
        } else {
            String names = records.stream().limit(5)
                    .map(ChangeRecord::name).collect(Collectors.joining(", "));
            if (records.size() > 5) names += "  +" + (records.size() - 5) + " more";
            notification = group.createNotification(
                    "Claude edited " + records.size() + " files", names, NotificationType.INFORMATION);
            notification.addAction(
                    NotificationAction.createSimple("Show timeline", () -> activateToolWindow(project)));
        }
        notification.notify(project);
    }

    private static String verb(ChangeRecord r) {
        return switch (r.type()) {
            case CREATE -> "created";
            case DELETE -> "deleted";
            case MODIFY -> "edited";
        };
    }

    private static void open(Project project, ChangeRecord r) {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + r.path());
        if (file == null || !file.isValid()) return;
        FileEditorManager.getInstance(project).openTextEditor(
                new OpenFileDescriptor(project, file, Math.max(0, r.firstChangedLine()), 0), true);
    }

    private static void activateToolWindow(Project project) {
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (tw != null) tw.activate(null);
    }
}
