package org.wx.quickserializableimpl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.wx.quickserializableimpl.core.SerializableImplAndSerialIdGen;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Objects;

/**
 * @author wuxin
 * @date 2025/10/24 00:03:18
 */
public class GenerateSerializableImplAndSerialIdAction extends AnAction{
    public GenerateSerializableImplAndSerialIdAction() {
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        PsiClass psiClass = cursorUpPSIClass(event);
        // 接口不展示 贯标不再类上不展示
        Boolean enable = Objects.nonNull(psiClass) && !psiClass.isInterface();
        presentation.setVisible(enable);
        presentation.setEnabled(enable);
    }

    private PsiClass cursorUpPSIClass(AnActionEvent event){
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return null;
        }
        PsiFile psiFile = event.getData(LangDataKeys.PSI_FILE);
        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAtCursor = psiFile.findElementAt(offset);
        if (elementAtCursor == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAtCursor, PsiClass.class);
    }

    @Override
    @Serial
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        // 找光标上面的类
        PsiClass cursorUpPSIClassLooked = cursorUpPSIClass(e);
        if(cursorUpPSIClassLooked == null){
                Notifications.Bus.notify(
                        new Notification(
                                "SerializableImplement",
                                "Unable to generate",
                                "Please place the cursor within or on the class!",
                                NotificationType.WARNING
                        )
                );
        }
        if(cursorUpPSIClassLooked.isInterface()){
            Notifications.Bus.notify(
                    new Notification(
                            "SerializableImplement",
                            "Unable to generate",
                            "The interface does not need to implement the serialization interface!",
                            NotificationType.WARNING
                    )
            );
        }
        ArrayList<PsiClass> psiClasses = new ArrayList<>() {{
            add(cursorUpPSIClassLooked);
        }};
        SerializableImplAndSerialIdGen.doSerializableImplAndSerialIdGen(project,
                psiFile,psiClasses,
                SerializableImplAndSerialIdGen.psiSerIdFileConfirm(psiClasses,project));
    }


    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }


}
