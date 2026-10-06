package com.jamesdpeters.chestsplusplus.devagent;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.CLASS_INIT_NAME;
import static java.util.stream.Collectors.toSet;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Initialises static fields that a swap adds. The JVM doesn't re-run {@code <clinit>}, so a new field such as Lombok's
 * {@code @Slf4j} logger would stay null. The swapped class gets a copy of its {@code <clinit>} that assigns only the new fields,
 * and {@link #initialize} runs it once the class is redefined.
 */
final class NewStatics {
    private static final String INITIALIZER = "hotSwap$initNewStatics";

    private NewStatics() {}

    static byte[] prepare(Class<?> type, byte[] bytes) {
        Set<String> existing = Arrays.stream(type.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(toSet());
        ClassModel model = ClassFile.of().parse(bytes);
        Optional<CodeModel> clinit = model.methods()
                .stream()
                .filter(method -> method.methodName().equalsString(CLASS_INIT_NAME))
                .findFirst()
                .flatMap(MethodModel::code);
        if (clinit.isEmpty() || model.fields().stream().noneMatch(field -> isNewStatic(field, existing))) return bytes;
        var resolver = ClassHierarchyResolver.defaultResolver().orElse(ClassHierarchyResolver.ofClassLoading(type.getClassLoader()));
        ClassTransform unfinal = (builder, element) -> {
            if (element instanceof FieldModel field && isNewStatic(field, existing)) builder.transformField(field, (fieldBuilder, part) -> {
                // Only <clinit> may write a static final field, and the copy below isn't <clinit>.
                if (part instanceof AccessFlags flags) fieldBuilder.withFlags(flags.flagsMask() & ~ClassFile.ACC_FINAL);
                else fieldBuilder.with(part);
            });
            else builder.with(element);
        };
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver))
                .transformClass(model, unfinal.andThen(ClassTransform.endHandler(builder -> builder
                        .withMethodBody(INITIALIZER, MethodTypeDesc.of(CD_void), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                                body -> copyAssigningNewOnly(clinit.get(), model, existing, body)))));
    }

    static void initialize(Class<?> type) {
        Method initializer;
        try {
            initializer = type.getDeclaredMethod(INITIALIZER);
        } catch (NoSuchMethodException e) {
            return;
        }
        try {
            initializer.setAccessible(true);
            initializer.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            HotSwapAgent.log("Couldn't initialise new static fields of %s: %s", type.getName(), e.getCause() == null ? e : e.getCause());
        }
    }

    private static boolean isNewStatic(FieldModel field, Set<String> existing) {
        return field.flags().has(AccessFlag.STATIC) && !existing.contains(field.fieldName().stringValue());
    }

    /** Copies {@code <clinit>}, dropping writes to existing static fields so their live values survive. */
    private static void copyAssigningNewOnly(CodeModel clinit, ClassModel model, Set<String> existing, CodeBuilder body) {
        for (CodeElement element : clinit) {
            if (element instanceof FieldInstruction field
                    && field.opcode() == Opcode.PUTSTATIC
                    && field.owner().asInternalName().equals(model.thisClass().asInternalName())
                    && existing.contains(field.name().stringValue())) {
                if (TypeKind.from(field.typeSymbol()).slotSize() == 2) body.pop2();
                else body.pop();
            } else {
                body.with(element);
            }
        }
    }
}
