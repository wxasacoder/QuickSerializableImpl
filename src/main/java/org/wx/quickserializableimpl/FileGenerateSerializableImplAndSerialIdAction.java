package org.wx.quickserializableimpl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.wx.quickserializableimpl.core.SerializableImplAndSerialIdGen;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author wuxin
 * @date 2025/10/27 22:23:52
 *
 */
public class FileGenerateSerializableImplAndSerialIdAction extends AnAction {

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private List<PsiClass> filterValid(List<PsiClass> psiClassList) {
        if(psiClassList == null || psiClassList.isEmpty()){
            return psiClassList;
        }
        return psiClassList.stream().filter(e-> !e.isInterface()).collect(Collectors.toList());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            Messages.showErrorDialog("No project found!", "Error");
            return;
        }
        VirtualFile[] virtualFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (virtualFiles == null || virtualFiles.length == 0) {
            Messages.showErrorDialog("No files or directories selected!", "Error");
            return;
        }
        Map<PsiFile, List<PsiClass>> psiFileAndFileMap = Arrays.stream(virtualFiles).collect(HashMap::new, (c, vir) -> {
            if (vir.isDirectory()) {
                c.putAll(getClassesFromDirectory(vir, project));
            } else if ("java".equalsIgnoreCase(vir.getExtension())) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(vir);
                if (psiFile instanceof PsiJavaFile psiJavaFile) {
                    c.put(psiFile, getClassesFromJavaFile(psiJavaFile));
                }
            }
        }, Map::putAll);

        List<Map.Entry<PsiFile, List<PsiClass>>> psiClassEntry = psiFileAndFileMap.entrySet().stream().filter(entry ->
                entry.getValue() != null
                        && !entry.getValue().isEmpty()
                        // 只要非interface 的
                        && entry.getValue().stream().anyMatch(e -> !e.isInterface())).collect(Collectors.toList());
        if(psiClassEntry.isEmpty()){
            Notifications.Bus.notify(
                    new Notification(
                            "SerializableImplement",
                            "Unable to generate",
                            "Please select a Java class or a folder containing Java classes！",
                            NotificationType.WARNING
                    )
            );
            return;
        }
        List<PsiClass> allPsiClasses = psiClassEntry.stream().map(Map.Entry::getValue).flatMap(Collection::stream).collect(Collectors.toList());
        Integer rewrite = SerializableImplAndSerialIdGen.psiSerIdFileConfirm(allPsiClasses, project);

        psiClassEntry.forEach(entry -> {
                    List<PsiClass> allClass = entry.getValue().stream().filter(e -> !e.isInterface()).collect(Collectors.toList());
                    PsiFile spiFile = entry.getKey();
                    SerializableImplAndSerialIdGen.doSerializableImplAndSerialIdGen(project, spiFile,allClass, rewrite);
                });

        Notifications.Bus.notify(
                new Notification(
                        "SerializableImplement",
                        "Generated successfully.",
                        "All Java classes have completed the implementation of the serialization interface and the generation of serialization IDs!",
                        NotificationType.INFORMATION
                )
        );
    }

    private Map<PsiFile, List<PsiClass>> getClassesFromDirectory(VirtualFile directory, Project project) {
        Map<PsiFile, List<PsiClass>> allClasses = new HashMap<>();
        PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
        if (psiDirectory == null) {
            return allClasses;
        }
        for (PsiFile psiFile : psiDirectory.getFiles()) {
            if (psiFile instanceof PsiJavaFile psiJavaFile) {
                allClasses.put(psiFile, getClassesFromJavaFile(psiJavaFile));
            }
        }

        for (PsiDirectory subDir : psiDirectory.getSubdirectories()) {
            allClasses.putAll(getClassesFromDirectory(subDir.getVirtualFile(), project));
        }

        return allClasses;
    }

    // 获取文件内所有的类
    private List<PsiClass> getClassesFromJavaFile(PsiJavaFile javaFile) {
        List<PsiClass> allClasses = new ArrayList<>();
        for (PsiClass psiClass : javaFile.getClasses()) {
            allClasses.addAll(getAllClasses(psiClass));
        }
        return allClasses;
    }

    // 递归拿所有字类
    private List<PsiClass> getAllClasses(PsiClass psiClass) {
        List<PsiClass> allClasses = new ArrayList<>();
        allClasses.add(psiClass);
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            allClasses.addAll(getAllClasses(innerClass));
        }
        return allClasses;
    }

}
