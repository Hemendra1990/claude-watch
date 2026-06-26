package dev.hemendra.claudewatch;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Opens IntelliJ's diff viewer comparing the captured pre-change text with the current file. */
final class DiffPresenter {

    private DiffPresenter() {
    }

    static void show(@NotNull Project project, @NotNull ChangeRecord record) {
        DiffContentFactory factory = DiffContentFactory.getInstance();

        String before = record.oldText() != null ? record.oldText() : "";
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(toUrl(record.path()));

        DiffContent left = (file != null && file.isValid())
                ? factory.create(project, before, file.getFileType())
                : factory.create(project, before);
        DiffContent right = (file != null && file.isValid())
                ? factory.create(project, file)
                : factory.create(project, "");

        SimpleDiffRequest request = new SimpleDiffRequest(
                "Claude changed: " + record.name(),
                left, right,
                "Before Claude", "After Claude");
        DiffManager.getInstance().showDiff(project, request);
    }

    private static String toUrl(@Nullable String path) {
        if (path == null) return "";
        return "file://" + path;
    }
}
