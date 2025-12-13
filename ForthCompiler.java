import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ForthCompiler {

    private static final StringBuilder rodata = new StringBuilder();
    private static final StringBuilder bss = new StringBuilder();
    private static final StringBuilder mainText = new StringBuilder();
    private static final StringBuilder defsText = new StringBuilder();

    private static final Map<String, String> dict = new HashMap<>();
    private static int labelCounter = 0;

    private enum CtrlType { IF, BEGIN, BEGIN_WHILE, MARK }

    private static final class Ctrl {
        final CtrlType type;
        final String elseLabel;
        final String endLabel;
        boolean elseEmitted;
        final String beginLabel;
        final String loopEndLabel;

        private Ctrl(CtrlType type, String elseLabel, String endLabel, boolean elseEmitted,
                     String beginLabel, String loopEndLabel) {
            this.type = type;
            this.elseLabel = elseLabel;
            this.endLabel = endLabel;
            this.elseEmitted = elseEmitted;
            this.beginLabel = beginLabel;
            this.loopEndLabel = loopEndLabel;
        }

        static Ctrl mark() { return new Ctrl(CtrlType.MARK, null, null, false, null, null); }
        static Ctrl mkIf(String elseLabel, String endLabel) { return new Ctrl(CtrlType.IF, elseLabel, endLabel, false, null, null); }
        static Ctrl mkBegin(String beginLabel) { return new Ctrl(CtrlType.BEGIN, null, null, false, beginLabel, null); }
        static Ctrl mkBeginWhile(String beginLabel, String loopEndLabel) { return new Ctrl(CtrlType.BEGIN_WHILE, null, null, false, beginLabel, loopEndLabel); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) System.exit(1);

        String inputPath = args[0];
        List<String> tokens = readTokensByteByByte(inputPath);

        preScanDefinitions(tokens);

        setupAsmPreamble();
        compileProgram(tokens);

        String base = stripExtension(Path.of(inputPath).getFileName().toString());
        String asmFile = base + ".s";

        String asm = rodata.toString() + bss.toString() + mainText.toString() + defsText.toString();
        Files.write(Paths.get(asmFile), asm.getBytes(StandardCharsets.UTF_8));

        runCommand("gcc", "-no-pie", asmFile, "-o", "code");
    }

    private static void preScanDefinitions(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(":")) {
                if (i + 1 >= tokens.size()) System.exit(1);
                String name = tokens.get(i + 1);
                dict.putIfAbsent(name, "w_" + sanitizeLabel(name));
                i++;
            }
        }
    }

    private static void setupAsmPreamble() {
        rodata.append(".section .rodata\n\nfmt:\n\t.asciz \"%ld\\n\"\n\n");
        bss.append(".section .bss\n.lcomm dstack, 65536\n\n");

        mainText.append(".section .text\n.globl main\n.extern printf\n\nmain:\n");
        mainText.append("\tpush %rbp\n\tmov %rsp, %rbp\n");
        mainText.append("\tlea dstack(%rip), %r15\n");
    }

    private static void compileProgram(List<String> tokens) {
        Deque<Ctrl> mainCtrl = new ArrayDeque<>();
        int i = 0;

        while (i < tokens.size()) {
            String t = tokens.get(i);

            if (t.equals(":")) {
                if (i + 1 >= tokens.size()) System.exit(1);
                String name = tokens.get(i + 1);
                String label = dict.getOrDefault(name, "w_" + sanitizeLabel(name));
                dict.put(name, label);

                i += 2;

                Deque<Ctrl> defCtrl = new ArrayDeque<>();
                defsText.append("\n").append(label).append(":\n");
                defCtrl.push(Ctrl.mark());

                while (i < tokens.size() && !tokens.get(i).equals(";")) {
                    compileToken(defsText, defCtrl, tokens.get(i));
                    i++;
                }
                if (i >= tokens.size()) System.exit(1);
                if (defCtrl.isEmpty() || defCtrl.peek().type != CtrlType.MARK) System.exit(1);
                defCtrl.pop();

                defsText.append("\tret\n");
                i++;
                continue;
            }

            compileToken(mainText, mainCtrl, t);
            i++;
        }

        if (!mainCtrl.isEmpty()) System.exit(1);
        mainText.append("\n\txor %eax, %eax\n\tleave\n\tret\n");
    }

    private static void compileToken(StringBuilder out, Deque<Ctrl> ctrl, String token) {
        if (token.matches("[+-]?\\d+")) {
            long n = Long.parseLong(token);
            out.append(asmPushImm(n));
            return;
        }

        switch (token) {
            case "dup" -> out.append(asmDup());
            case "drop" -> out.append(asmDrop());
            case "swap" -> out.append(asmSwap());
            case "over" -> out.append(asmOver());

            case "+" -> out.append(asmAdd());
            case "-" -> out.append(asmSub());
            case "*" -> out.append(asmMul());
            case "/" -> out.append(asmDiv());
            case "mod" -> out.append(asmMod());

            case "." -> out.append(asmPrint());

            case "if" -> compileIf(out, ctrl);
            case "else" -> compileElse(out, ctrl);
            case "then" -> compileThen(out, ctrl);

            case "begin" -> compileBegin(out, ctrl);
            case "until" -> compileUntil(out, ctrl);
            case "again" -> compileAgain(out, ctrl);
            case "while" -> compileWhile(out, ctrl);
            case "repeat" -> compileRepeat(out, ctrl);

            default -> {
                String label = dict.get(token);
                if (label != null) out.append("\tcall ").append(label).append("\n");
                else System.exit(1);
            }
        }
    }

    private static void compileIf(StringBuilder out, Deque<Ctrl> ctrl) {
        String elseL = freshLabel("else");
        String endL = freshLabel("endif");
        ctrl.push(Ctrl.mkIf(elseL, endL));
        out.append(asmPopTo("rax"));
        out.append("\tcmp $0, %rax\n");
        out.append("\tje ").append(elseL).append("\n");
    }

    private static void compileElse(StringBuilder out, Deque<Ctrl> ctrl) {
        if (ctrl.isEmpty() || ctrl.peek().type != CtrlType.IF) System.exit(1);
        Ctrl c = ctrl.peek();
        out.append("\tjmp ").append(c.endLabel).append("\n");
        out.append(c.elseLabel).append(":\n");
        c.elseEmitted = true;
    }

    private static void compileThen(StringBuilder out, Deque<Ctrl> ctrl) {
        if (ctrl.isEmpty() || ctrl.peek().type != CtrlType.IF) System.exit(1);
        Ctrl c = ctrl.pop();
        if (!c.elseEmitted) out.append(c.elseLabel).append(":\n");
        out.append(c.endLabel).append(":\n");
    }

    private static void compileBegin(StringBuilder out, Deque<Ctrl> ctrl) {
        String beginL = freshLabel("begin");
        ctrl.push(Ctrl.mkBegin(beginL));
        out.append(beginL).append(":\n");
    }

    private static void compileUntil(StringBuilder out, Deque<Ctrl> ctrl) {
        if (ctrl.isEmpty() || ctrl.peek().type != CtrlType.BEGIN) System.exit(1);
        Ctrl c = ctrl.pop();
        out.append(asmPopTo("rax"));
        out.append("\tcmp $0, %rax\n");
        out.append("\tje ").append(c.beginLabel).append("\n");
    }

    private static void compileAgain(StringBuilder out, Deque<Ctrl> ctrl) {
        if (ctrl.isEmpty() || ctrl.peek().type != CtrlType.BEGIN) System.exit(1);
        Ctrl c = ctrl.pop();
        out.append("\tjmp ").append(c.beginLabel).append("\n");
    }

    private static void compileWhile(StringBuilder out, Deque<Ctrl> ctrl) {
        if (ctrl.isEmpty() || ctrl.peek().type != CtrlType.BEGIN) System.exit(1);
        Ctrl c0 = ctrl.pop();
        String end = freshLabel("endwhile");
        ctrl.push(Ctrl.mkBeginWhile(c0.beginLabel, end));
        out.append(asmPopTo("rax"));
        out.append("\tcmp $0, %rax\n");
        out.append("\tje ").append(end).append("\n");
    }

    private static void compileRepeat(StringBuilder out, Deque<Ctrl> ctrl) {
        if (ctrl.isEmpty() || ctrl.peek().type != CtrlType.BEGIN_WHILE) System.exit(1);
        Ctrl c = ctrl.pop();
        out.append("\tjmp ").append(c.beginLabel).append("\n");
        out.append(c.loopEndLabel).append(":\n");
    }

    private static String asmPushImm(long n) {
        return "\n\tmov $" + n + ", %rax\n\tmov %rax, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmPopTo(String reg) {
        return "\tsub $8, %r15\n\tmov (%r15), %" + reg + "\n";
    }

    private static String asmDup() {
        return "\n" + asmPopTo("rax") +
                "\tmov %rax, (%r15)\n\tadd $8, %r15\n" +
                "\tmov %rax, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmDrop() { return "\n\tsub $8, %r15\n"; }

    private static String asmSwap() {
        return "\n" + asmPopTo("rax") + asmPopTo("rbx") +
                "\tmov %rax, (%r15)\n\tadd $8, %r15\n" +
                "\tmov %rbx, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmOver() {
        return "\n" + asmPopTo("rax") + asmPopTo("rbx") +
                "\tmov %rbx, (%r15)\n\tadd $8, %r15\n" +
                "\tmov %rax, (%r15)\n\tadd $8, %r15\n" +
                "\tmov %rbx, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmAdd() {
        return "\n" + asmPopTo("rbx") + asmPopTo("rcx") +
                "\tadd %rbx, %rcx\n" +
                "\tmov %rcx, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmSub() {
        return "\n" + asmPopTo("rbx") + asmPopTo("rcx") +
                "\tsub %rbx, %rcx\n" +
                "\tmov %rcx, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmMul() {
        return "\n" + asmPopTo("rbx") + asmPopTo("rcx") +
                "\timul %rbx, %rcx\n" +
                "\tmov %rcx, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmDiv() {
        return "\n" + asmPopTo("rbx") + asmPopTo("rax") +
                "\tcqo\n\tidiv %rbx\n" +
                "\tmov %rax, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmMod() {
        return "\n" + asmPopTo("rbx") + asmPopTo("rax") +
                "\tcqo\n\tidiv %rbx\n" +
                "\tmov %rdx, (%r15)\n\tadd $8, %r15\n";
    }

    private static String asmPrint() {
        return "\n" + asmPopTo("rsi") +
                "\tlea fmt(%rip), %rdi\n" +
                "\txor %eax, %eax\n" +
                "\tcall printf\n";
    }

    private static List<String> readTokensByteByByte(String filePath) throws IOException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        try (InputStream in = new FileInputStream(filePath)) {
            int b;
            while ((b = in.read()) != -1) {
                char ch = (char) b;
                if (Character.isWhitespace(ch)) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                } else current.append(ch);
            }
        }

        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private static void runCommand(String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        if (p.waitFor() != 0) System.exit(1);
    }

    private static String freshLabel(String p) { return p + "_" + (labelCounter++); }

    private static String stripExtension(String n) {
        int d = n.lastIndexOf('.');
        return d == -1 ? n : n.substring(0, d);
    }

    private static String sanitizeLabel(String n) {
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray())
            sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        return sb.toString();
    }
}

