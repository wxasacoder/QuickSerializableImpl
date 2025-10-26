package org.wx.quickserializableimpl;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

public class SerialVersionUIDBuilder {

    /**
     * 根据 PsiClass 计算默认的 serialVersionUID.
     *
     * @param psiClass 要计算的 PsiClass 对象
     * @return 计算出的 serialVersionUID
     */
    public static long computeDefaultSUID(PsiClass psiClass) {
        if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass.isRecord()) {
             return 0L;
        }

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);


            dout.writeUTF(psiClass.getQualifiedName());


            int classMods = getPsiClassModifiers(psiClass) &
                    (Modifier.PUBLIC | Modifier.FINAL | Modifier.INTERFACE | Modifier.ABSTRACT);
            dout.writeInt(classMods);


            String[] interfaceNames = Arrays.stream(psiClass.getInterfaces())
                    .map(PsiClass::getQualifiedName)
                    .sorted()
                    .toArray(String[]::new);
            for (String name : interfaceNames) {
                dout.writeUTF(name);
            }


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


            if (hasStaticInitializer(psiClass)) {
                dout.writeUTF("<clinit>");
                dout.writeInt(Modifier.STATIC);
                dout.writeUTF("()V");
            }


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
                    dout.writeUTF(getSignature(constructor).replace('/', '.'));
                }
            }


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
                    dout.writeUTF(getSignature(method).replace('/', '.'));
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

    /**
     * 获取 PsiClass 的修饰符.
     */
    private static int getPsiClassModifiers(PsiClass psiClass) {
        int modifiers = 0;
        if (psiClass.hasModifierProperty(PsiModifier.PUBLIC)) modifiers |= Modifier.PUBLIC;
        if (psiClass.hasModifierProperty(PsiModifier.FINAL)) modifiers |= Modifier.FINAL;
        if (psiClass.isInterface()) modifiers |= Modifier.INTERFACE;
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers |= Modifier.ABSTRACT;
        return modifiers;
    }

    /**
     * 获取 PsiModifierListOwner (如字段、方法) 的修饰符.
     */
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

    /**
     * 检查 PsiClass 是否有静态初始化块.
     */
    private static boolean hasStaticInitializer(PsiClass psiClass) {
        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
            if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 PsiMethod 的 JNI 签名.
     */
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


//    private static String getSignature(PsiType type) {
//        if (type == null) {
//            return "V"; // void
//        }
//        if (type instanceof PsiPrimitiveType) {
//            if (type.equals(PsiTypes.byteType())) return "B";
//            if (type.equals(PsiTypes.charType())) return "C";
//            if (type.equals(PsiTypes.doubleType())) return "D";
//            if (type.equals(PsiTypes.floatType())) return "F";
//            if (type.equals(PsiTypes.intType())) return "I";
//            if (type.equals(PsiTypes.longType())) return "J";
//            if (type.equals(PsiTypes.shortType())) return "S";
//            if (type.equals(PsiTypes.booleanType())) return "Z";
//            if (type.equals(PsiTypes.voidType())) return "V";
//        } else if (type instanceof PsiArrayType) {
//            return "[" + getSignature(((PsiArrayType) type).getComponentType());
//        } else if (type instanceof PsiClassType) {
//            PsiClass psiClass = ((PsiClassType) type).resolve();
//            if (psiClass != null && psiClass.getQualifiedName() != null) {
//                return "L" + psiClass.getQualifiedName().replace('.', '/') + ";";
//            }
//        }
//        // 对于泛型等复杂情况，可能需要更复杂的处理
//        return "Ljava/lang/Object;";
//    }
//
//
    private static String getSignature(PsiType type) {
        if (type == null) {
            return "V"; // void
        }

        // 关键步骤：执行类型擦除
        // serialVersionUID 的计算是基于擦除后的类型，这会移除所有泛型信息。
        // 例如, List<String> 会被擦除为 List。
        PsiType erasedType = TypeConversionUtil.erasure(type);

        // 在擦除后的类型上进行操作
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
            // 递归调用，处理多维数组
            return "[" + getSignature(((PsiArrayType) erasedType).getComponentType());
        } else if (erasedType instanceof PsiClassType) {
            // 对于类类型，解析出 PsiClass
            PsiClass psiClass = ((PsiClassType) erasedType).resolve();
            if (psiClass != null) {
                String qName = psiClass.getQualifiedName();
                if (qName != null) {
                    // JNI 签名使用 '/' 作为包分隔符
                    return "L" + qName.replace('.', '/') + ";";
                }
            }
        }

        // 如果类型无法解析
        // 提供一个最安全的默认值，与 JDK 的行为类似。
        return "Ljava/lang/Object;";
    }
}