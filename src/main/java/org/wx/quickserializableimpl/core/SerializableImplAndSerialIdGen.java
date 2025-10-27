package org.wx.quickserializableimpl.core;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import org.wx.quickserializableimpl.SerialVersionUIDBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author wuxin
 * @date 2025/10/27 22:36:14
 *
 */
public class SerializableImplAndSerialIdGen {

    public static void doSerializableImplAndSerialIdGen(Project project,
                                                 PsiFile psiFile,
                                                 List<PsiClass> classes, Integer rewriteSerId){

        if(psiFile instanceof PsiJavaFile javaFile){
            if(Objects.isNull(classes) || classes.isEmpty()){
                Notifications.Bus.notify(
                        new Notification(
                                "SerializableImplement",
                                "Unable to generate",
                                "No classes need to implement the serialization interface!",
                                NotificationType.WARNING
                        )
                );
                return;
            }


//            PsiClass[] classes = javaFile.getClasses();
            PsiImportList importList = javaFile.getImportList();
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            for (PsiClass psiClass : classes) {
                if (psiClass.isInterface()) {
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
                    if (rewriteSerId == Messages.YES) {
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            serialVersionUID.get().delete();
                            Set<String> importedList = try2ImportNeed2Import(Serializable.class.getName(),javaFile, importList, project,factory);
                            generateSerId(importedList,project,psiClass);
                        });
                    }
                    continue;
                }
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    Set<String> importedList = try2ImportNeed2Import(Serial.class.getName(),javaFile, importList, project,factory);
                    generateSerId(importedList,project,psiClass);
                });
            }
        }
    }


    public static Integer psiSerIdFileConfirm(List<PsiClass> psiClasses, Project project){
        if(psiClasses == null || psiClasses.isEmpty()){
            return 1;
        }
        return psiClasses.stream().map(PsiClass::getFields)
                .flatMap(Arrays::stream)
                .filter(fields -> fields != null && fields.getName().equals("serialVersionUID"))
                .findFirst().map(filed -> Messages.showYesNoDialog(
                        project,
                        "The file you selected already contains a serialVersionUID field. Would you like to regenerate it?",
                        "Overwrite reminder",
                        "Confirm",
                        "Cancel",
                        Messages.getQuestionIcon()
                )).orElse(1);
    }


    private static void generateSerId(Set<String> importedList, Project project,PsiClass psiClass){

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



    private static Set<String> try2ImportNeed2Import(String className,
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


}

