import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ForthCompiler {

    // Assembly template (same as your Node version)
    private static StringBuilder asmCode = new StringBuilder(
        ".section .rodata\n\n" +
        "fmt:\n" +
        "\t.asciz \"%d\\n\"\n" +   // NOTE: \\n here becomes \n in the .s file
        "\n.section .text\n" +
        ".globl _start\n" +
        "_start:\n"
    );

    public static void main(String[] args) throws Exception {
        // Usage: java ForthCompiler code.fs
        if (args.length < 1) {
            System.err.println("Usage: java ForthCompiler <source.fs>");
            System.exit(1);
        }

        String inputPath = args[0];

        // 1. Read tokens byte by byte
        List<String> tokens = readTokensByteByByte(inputPath);

        // 2. Parse each token and generate asm
        for (String token : tokens) {
            parseAndGenerate(token);
        }

        // 3. Add exit syscall
        asmCode.append(generateExit());

        // 4. Write to code.s
        Files.write(
            Paths.get("code.s"),
            asmCode.toString().getBytes(StandardCharsets.UTF_8)
        );

        // 5. Assemble and link to create ./code
        assembleAndLink("code");
    }

    // --------- tokenization (byte-by-byte) ---------

    private static List<String> readTokensByteByByte(String filePath) throws IOException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        try (InputStream in = new FileInputStream(filePath)) {
            int b;
            while ((b = in.read()) != -1) {    // -1 = EOF
                char ch = (char) b;

                // whitespace?
                if (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r') {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(ch);
                }
            }
        }

        // last token (if file doesn't end with whitespace)
        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    // --------- code generation helpers ---------

    private static void parseAndGenerate(String token) {
        // number = one or more digits, nothing else
        boolean isNumber = token.length() > 0 &&
                        token.chars().allMatch(Character::isDigit);

        if (isNumber) {
            int n = Integer.parseInt(token);   // safe: only digits
            asmCode.append(generatePushNumberCode(n));
            return;
        }

        // If it starts with + or -, treat that explicitly as an error if you want:
        if (token.startsWith("+") || token.startsWith("-")) {
            System.err.println("Signed numbers are not allowed: " + token);
            return;
        }

        // Forth words
        switch (token) {
            case "dup" -> asmCode.append(generateDuplicateCode());
            case "*"   -> asmCode.append(generateMultiplyCode());
            case "."   -> asmCode.append(generatePrintCode());
            default    -> System.err.println("Unrecognized token: " + token);
        }
    }

    private static String generatePushNumberCode(int n) {
        return "\n    push $" + n + "\n";
    }

    private static String generateDuplicateCode() {
        return """
            
            pop %rax
            push %rax
            push %rax
            """;
    }

    private static String generateMultiplyCode() {
        return """
            
            pop %rbx
            pop %rcx
            imul %rbx, %rcx
            push %rcx
            """;
    }

    private static String generatePrintCode() {
        return """
          
          pop %rsi
          mov $fmt, %rdi
          xor %rax, %rax

          call printf
            """;
    }

    private static String generateExit() {
        return """
            
            mov $60, %rax       # syscall: exit
            mov $0, %rdi        # status = 0
            syscall
            """;
    }

    // --------- run `as` and `ld` like in your Node version ---------

    private static void assembleAndLink(String outputBase) throws IOException, InterruptedException {
        // as -o code.o code.s
        runCommand("as", "-o", outputBase + ".o", outputBase + ".s");

        // ld -o code code.o -lc -dynamic-linker /lib64/ld-linux-x86-64.so.2
        runCommand(
            "ld",
            "-o", outputBase,
            outputBase + ".o",
            "-lc",
            "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2"
        );
    }

    private static void runCommand(String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO(); // show assembler/linker output in this terminal
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd));
        }
    }
}
