package org.wx.quickserializableimpl;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class SerialVersionUIDBuilder {

    /**
     * 根据 PsiClass 计算默认的 serialVersionUID.
     */
    public static long computeDefaultSUID(PsiClass psiClass) {
        if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass.isRecord()) {
            return 0L;
        }

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);

            // 类名
            String qname = psiClass.getQualifiedName();
            if (qname == null) qname = psiClass.getName();
            dout.writeUTF(qname);

            // 类修饰符
            int classMods = getPsiClassModifiers(psiClass) &
                    (Modifier.PUBLIC | Modifier.FINAL | Modifier.INTERFACE | Modifier.ABSTRACT);
            dout.writeInt(classMods);

            // 收集所有声明的接口（包括父类继承的）并排序
            String[] interfaceNames = getAllInterfaceNames(psiClass).stream().sorted().toArray(String[]::new);
            for (String name : interfaceNames) {
                dout.writeUTF(name);
            }

            // 字段：按名字排序
            PsiField[] fields = psiClass.getFields();
            Arrays.sort(fields, Comparator.comparing(PsiField::getName));
            for (PsiField field : fields) {
                int mods = getPsiModifiers(field) &
                        (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
                                Modifier.STATIC | Modifier.FINAL | Modifier.VOLATILE |
                                Modifier.TRANSIENT);


                if (((mods & Modifier.PRIVATE) == 0) || ((mods & (Modifier.STATIC | Modifier.TRANSIENT)) == 0)) {
                    dout.writeUTF(field.getName());
                    dout.writeInt(mods);
                    dout.writeUTF(getSignature(field.getType()));
                }
            }

            if (hasStaticInitializerOrNonConstantStaticField(psiClass)) {
                dout.writeUTF("<clinit>");
                dout.writeInt(Modifier.STATIC);
                dout.writeUTF("()V");
            }

            // 构造函数：按签名排序
            PsiMethod[] constructors = psiClass.getConstructors();
            Arrays.sort(constructors, Comparator.comparing(SerialVersionUIDBuilder::getSignature));
            for (PsiMethod constructor : constructors) {
                int mods = getPsiModifiers(constructor) &
                        (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
                                Modifier.STATIC | Modifier.FINAL | Modifier.SYNCHRONIZED |
                                Modifier.NATIVE | Modifier.ABSTRACT | Modifier.STRICT);
                if ((mods & Modifier.PRIVATE) == 0) {
                    dout.writeUTF("<init>");
                    dout.writeInt(mods);

                    dout.writeUTF(getSignature(constructor));
                }
            }

            // 普通方法：按 name + signature 排序
            PsiMethod[] methods = psiClass.getMethods();
            Arrays.sort(methods, Comparator.comparing(PsiMethod::getName).thenComparing(SerialVersionUIDBuilder::getSignature));
            for (PsiMethod method : methods) {
                int mods = getPsiModifiers(method) &
                        (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
                                Modifier.STATIC | Modifier.FINAL | Modifier.SYNCHRONIZED |
                                Modifier.NATIVE | Modifier.ABSTRACT | Modifier.STRICT);
                if ((mods & Modifier.PRIVATE) == 0) {
                    dout.writeUTF(method.getName());
                    dout.writeInt(mods);
                    dout.writeUTF(getSignature(method));
                }
            }

            dout.flush();

            MessageDigest md = MessageDigest.getInstance("SHA");
            byte[] hashBytes = md.digest(bout.toByteArray());
            long hash = 0;

            for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
                hash = (hash << 8) | (hashBytes[i] & 0xFF);
            }
            return hash;

        } catch (IOException e) {
            throw new InternalError(e);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException(e.getMessage());
        }
    }

    private static int getPsiClassModifiers(PsiClass psiClass) {
        int modifiers = 0;
        if (psiClass.hasModifierProperty(PsiModifier.PUBLIC)) modifiers |= Modifier.PUBLIC;
        if (psiClass.hasModifierProperty(PsiModifier.FINAL)) modifiers |= Modifier.FINAL;
        if (psiClass.isInterface()) modifiers |= Modifier.INTERFACE;
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers |= Modifier.ABSTRACT;
        return modifiers;
    }

    private static int getPsiModifiers(PsiModifierListOwner owner) {
        int modifiers = 0;
        if (owner.hasModifierProperty(PsiModifier.PUBLIC)) modifiers |= Modifier.PUBLIC;
        if (owner.hasModifierProperty(PsiModifier.PROTECTED)) modifiers |= Modifier.PROTECTED;
        if (owner.hasModifierProperty(PsiModifier.PRIVATE)) modifiers |= Modifier.PRIVATE;
        if (owner.hasModifierProperty(PsiModifier.STATIC)) modifiers |= Modifier.STATIC;
        if (owner.hasModifierProperty(PsiModifier.FINAL)) modifiers |= Modifier.FINAL;
        if (owner.hasModifierProperty(PsiModifier.SYNCHRONIZED)) modifiers |= Modifier.SYNCHRONIZED;
        if (owner.hasModifierProperty(PsiModifier.VOLATILE)) modifiers |= Modifier.VOLATILE;
        if (owner.hasModifierProperty(PsiModifier.TRANSIENT)) modifiers |= Modifier.TRANSIENT;
        if (owner.hasModifierProperty(PsiModifier.NATIVE)) modifiers |= Modifier.NATIVE;
        if (owner.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers |= Modifier.ABSTRACT;
        if (owner.hasModifierProperty(PsiModifier.STRICTFP)) modifiers |= Modifier.STRICT;
        return modifiers;
    }

    private static boolean hasStaticInitializerOrNonConstantStaticField(PsiClass psiClass) {
        // 显式的 static initializer
        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
            if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }

        // 如果存在 static 字段且该字段有 initializer 且不是编译时常量，则视为需要 <clinit>
        PsiField[] fields = psiClass.getFields();
        PsiConstantEvaluationHelper constHelper = JavaPsiFacade.getInstance(psiClass.getProject()).getConstantEvaluationHelper();
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() != null) {
                // 如果能计算为常量则跳过（例如 public static final int A = 1;）
                try {
                    Object constValue = constHelper.computeConstantExpression(field.getInitializer());
                    if (constValue == null) {
                        return true; // 非编译期常量的静态初始化
                    }
                } catch (Exception ignored) {
                    // 任何异常都认为不是常量，从而认为存在 <clinit>
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> getAllInterfaceNames(PsiClass psiClass) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Queue<PsiClass> queue = new ArrayDeque<>();
        queue.add(psiClass);
        while (!queue.isEmpty()) {
            PsiClass cur = queue.poll();
            if (cur == null) continue;
            for (PsiClass intf : cur.getInterfaces()) {
                if (intf == null) continue;
                String q = intf.getQualifiedName();
                if (q == null) q = intf.getName();
                if (q != null && set.add(q)) {
                    // 继续加入该接口的 super interfaces
                    queue.add(intf);
                }
            }
            // 也要处理父类上的接口
            PsiClass sup = cur.getSuperClass();
            if (sup != null) queue.add(sup);
        }
        return new ArrayList<>(set);
    }

    private static String getSignature(PsiMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            sb.append(getSignature(parameter.getType()));
        }
        sb.append(')');
        if (method.isConstructor()) {
            sb.append('V');
        } else {
            sb.append(getSignature(method.getReturnType()));
        }
        return sb.toString();
    }

    private static String getSignature(PsiType type) {
        if (type == null) {
            return "V"; // void
        }

        PsiType erasedType = TypeConversionUtil.erasure(type);

        if (erasedType instanceof PsiPrimitiveType) {
            if (erasedType.equals(PsiTypes.byteType())) return "B";
            if (erasedType.equals(PsiTypes.charType())) return "C";
            if (erasedType.equals(PsiTypes.doubleType())) return "D";
            if (erasedType.equals(PsiTypes.floatType())) return "F";
            if (erasedType.equals(PsiTypes.intType())) return "I";
            if (erasedType.equals(PsiTypes.longType())) return "J";
            if (erasedType.equals(PsiTypes.shortType())) return "S";
            if (erasedType.equals(PsiTypes.booleanType())) return "Z";
            if (erasedType.equals(PsiTypes.voidType())) return "V";
        } else if (erasedType instanceof PsiArrayType) {
            return "[" + getSignature(((PsiArrayType) erasedType).getComponentType());
        } else if (erasedType instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) erasedType).resolve();
            if (psiClass != null) {
                String qName = psiClass.getQualifiedName();
                if (qName != null) {
                    return "L" + qName.replace('.', '/') + ";";
                }
            }
        }

        return "Ljava/lang/Object;";
    }
}
