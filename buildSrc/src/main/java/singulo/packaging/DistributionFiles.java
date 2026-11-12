package singulo.packaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class DistributionFiles {
    // Match whole string tokens so spaces, escaped quotes and number literals survive verbatim.
    private static final Pattern JSON_TOKENS = Pattern.compile("(\"(?:[^\"\\\\]++|\\\\.)*+\")|[ \\t\\r\\n]+");

    public static void compact(Path file) throws IOException {
        String name = file.getFileName().toString();
        byte[] input = Files.readAllBytes(file);
        byte[] result;
        if (name.endsWith(".class")) {
            result = stripLocals(input);
        } else if (name.endsWith(".png")) {
            result = LosslessPng.compact(input);
        } else {
            String json = new String(input, StandardCharsets.UTF_8);
            String compact = JSON_TOKENS.matcher(json).replaceAll("$1");
            result = compact.getBytes(StandardCharsets.UTF_8);
        }
        if (!java.util.Arrays.equals(input, result)) {
            Files.write(file, result);
        }
    }

    private static byte[] stripLocals(byte[] input) {
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(reader, 0);
        // SKIP_DEBUG would also discard stack-trace line numbers, source names and parameters.
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitLocalVariable(String name, String descriptor, String signature,
                                                   Label start, Label end, int index) {
                        // Omit only the LocalVariableTable and LocalVariableTypeTable entries.
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
