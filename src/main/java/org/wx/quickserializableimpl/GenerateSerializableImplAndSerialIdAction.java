package org.wx.quickserializableimpl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wuxin
 * @date 2025/10/24 00:03:18
 */
public class GenerateSerializableImplAndSerialIdAction extends AnAction{

    private Set<String> try2ImportNeed2Import(String className,
                                              PsiJavaFile javaFile,
                                       PsiImportList importList,
                                       Project project,
                                       PsiElementFactory factory){
        Set<String> importedClassNames = Arrays.stream(importList.getImportStatements()).map(PsiImportStatement::getQualifiedName).collect(Collectors.toSet());
        if(!importedClassNames.contains(className)){
            PsiClass serializableClass = JavaPsiFacade.getInstance(project)
                    .findClass(className, javaFile.getResolveScope());
            // 能找到的这个类那么则进行导入
            if(Objects.nonNull(serializableClass)){
                PsiImportStatement serializableClassImport = factory.createImportStatement(serializableClass);
                importList.add(serializableClassImport);
                importedClassNames.add(className);
            }
        }
        return importedClassNames;
    }

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
        if(psiFile instanceof PsiJavaFile javaFile){
            if(Objects.isNull(cursorUpPSIClassLooked)){
                Notifications.Bus.notify(
                        new Notification(
                                "SerializableImplement",
                                "Unable to generate",
                                "Please place the cursor within or on the class！",
                                NotificationType.INFORMATION
                        )
                );
                return;
            }

            ArrayList<PsiClass> classes = new ArrayList<>(){{
                add(cursorUpPSIClassLooked);
            }};

//            PsiClass[] classes = javaFile.getClasses();
            PsiImportList importList = javaFile.getImportList();
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            for (PsiClass psiClass : classes) {
                if (psiClass.isInterface()) {
                    Notifications.Bus.notify(
                            new Notification(
                                    "SerializableImplement",
                                    "Unable to generate",
                                    "The current file type does not require generation！",
                                    NotificationType.INFORMATION
                            )
                    );
                    continue;
                }
                PsiReferenceList implementsList = psiClass.getImplementsList();
                // 接口实现
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    Set<String> importedList = try2ImportNeed2Import(Serializable.class.getName(),javaFile, importList, project, factory);
                    // 实现 Serializable 接口

                    if (implementsList != null &&
                            importedList.contains("java.io.Serializable") &&
                            !Arrays.stream(psiClass.getImplementsListTypes()).anyMatch(t -> t.equalsToText("java.io.Serializable"))) {

                        PsiJavaCodeReferenceElement ref =
                                factory.createReferenceFromText("Serializable", psiClass);
                        implementsList.add(ref);
                    }
                });

                // 添加 serialVersionUID
                Optional<PsiField> serialVersionUID = Arrays.stream(psiClass.getFields()).filter(filed -> filed.getName().equals("serialVersionUID")).findFirst();
                if(serialVersionUID.isPresent()){
                    int result = Messages.showYesNoDialog(
                            project,
                            "A field named serialVersionUID already exists. Do you want to overwrite it?",
                            "Overwrite reminder",
                            "Confirm",
                            "Cancel",
                            Messages.getQuestionIcon()
                    );
                    if (result == Messages.YES) {
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            serialVersionUID.get().delete();
                            Set<String> importedList = try2ImportNeed2Import(Serializable.class.getName(),javaFile, importList, project,factory);
                            generateSerId(importedList,project,psiClass);
                        });
                    }
                    return;
                }
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    Set<String> importedList = try2ImportNeed2Import(Serial.class.getName(),javaFile, importList, project,factory);
                    generateSerId(importedList,project,psiClass);
                });
            }
        }
    }

    private void generateSerId(Set<String> importedList, Project project,PsiClass psiClass){

        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

        StringBuilder sb = new StringBuilder();
        if (importedList.contains(Serial.class.getName())) {
            sb.append("@Serial\n");
        }

//        long uid = new java.util.Random().nextLong();
        long uid = SerialVersionUIDBuilder.computeDefaultSUID(psiClass);

        sb.append("private static final long serialVersionUID = ").append(uid).append("L;");

        String fieldText = sb.toString();
        PsiField newField = factory.createFieldFromText(fieldText, psiClass);
        PsiField[] existingFields = psiClass.getFields();
        if (existingFields.length > 0) {
            psiClass.addBefore(newField, existingFields[0]);
        } else {
            psiClass.add(newField);
        }
    }


    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }


}
