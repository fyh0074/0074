import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

public final class TypescriptToLog {

    public enum Unit { SECONDS, MICROS, AUTO }

    public static final class Options {
        /** 绝对起点；若为 null 则使用 OffsetDateTime.now() */
        public OffsetDateTime start = null;
        /** 去掉 ANSI/VT100 控制序列（颜色、光标移动等 CSI 类） */
        public boolean stripAnsi = false;
        /** 去掉回车 \r，避免“回车覆盖”造成的行内刷新假象 */
        public boolean stripCr = false;
        /** timing 第一列单位（秒、微秒或自动判定） */
        public Unit unit = Unit.AUTO;
        /** 文本解码字符集（默认 UTF-8） */
        public Charset charset = StandardCharsets.UTF_8;

        public Options() {}
        public Options start(OffsetDateTime v) { this.start = v; return this; }
        public Options stripAnsi(boolean v)     { this.stripAnsi = v; return this; }
        public Options stripCr(boolean v)       { this.stripCr = v; return this; }
        public Options unit(Unit v)             { this.unit = v; return this; }
        public Options charset(Charset cs)      { this.charset = cs; return this; }
    }

    private static final java.time.format.DateTimeFormatter ABS_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private TypescriptToLog() {} // 工具类，禁止实例化

    /** 将 NAME.timing + NAME 转为“[绝对时间] 行文本”的列表（小文件/一次性结果适用） */
    public static List<String> convertToLines(File timingFile, File dataFile, Options opt) throws IOException {
        Objects.requireNonNull(timingFile, "timingFile");
        Objects.requireNonNull(dataFile, "dataFile");
        Objects.requireNonNull(opt, "options");
        List<TimingPair> pairs = readTiming(timingFile);
        double factor = computeFactor(pairs, opt.unit);
        Pattern ansi = opt.stripAnsi ? ansiPattern() : null;

        List<String> out = new ArrayList<>();
        OffsetDateTime base = (opt.start != null) ? opt.start : OffsetDateTime.now();

        try (InputStream in = new BufferedInputStream(new FileInputStream(dataFile))) {
            double tRel = 0.0;
            ByteArrayOutputStream buf = new ByteArrayOutputStream(4096);
            for (TimingPair p : pairs) {
                tRel += p.delay * factor;
                byte[] chunk = readExact(in, p.nbytes);
                if (chunk == null) break;
                for (byte b : chunk) {
                    if (opt.stripCr && b == '\r') continue;
                    if (b == '\n') {
                        out.add(formatLine(buf, tRel, base, ansi, opt.charset));
                    } else {
                        buf.write(b);
                    }
                }
            }
            if (buf.size() > 0) {
                out.add(formatLine(buf, tRel, base, ansi, opt.charset));
            }
        }
        return out;
    }

    /** 将结果直接写入 Writer（大文件/流式处理推荐） */
    public static void convertToWriter(File timingFile, File dataFile, Options opt, Writer writer) throws IOException {
        Objects.requireNonNull(timingFile, "timingFile");
        Objects.requireNonNull(dataFile, "dataFile");
        Objects.requireNonNull(opt, "options");
        Objects.requireNonNull(writer, "writer");

        List<TimingPair> pairs = readTiming(timingFile);
        double factor = computeFactor(pairs, opt.unit);
        Pattern ansi = opt.stripAnsi ? ansiPattern() : null;

        OffsetDateTime base = (opt.start != null) ? opt.start : OffsetDateTime.now();

        try (InputStream in = new BufferedInputStream(new FileInputStream(dataFile))) {
            double tRel = 0.0;
            ByteArrayOutputStream buf = new ByteArrayOutputStream(4096);
            for (TimingPair p : pairs) {
                tRel += p.delay * factor;
                byte[] chunk = readExact(in, p.nbytes);
                if (chunk == null) break;
                for (byte b : chunk) {
                    if (opt.stripCr && b == '\r') continue;
                    if (b == '\n') {
                        writer.write(formatLine(buf, tRel, base, ansi, opt.charset));
                        writer.write(System.lineSeparator());
                    } else {
                        buf.write(b);
                    }
                }
            }
            if (buf.size() > 0) {
                writer.write(formatLine(buf, tRel, base, ansi, opt.charset));
                writer.write(System.lineSeparator());
            }
            writer.flush();
        }
    }

    // ---------- 内部实现 ----------

    private static final class TimingPair {
        final double delay;  // 原始 delay 值（单位未归一）
        final int nbytes;
        TimingPair(double d, int n) { this.delay = d; this.nbytes = n; }
    }

    private static List<TimingPair> readTiming(File path) throws IOException {
        List<TimingPair> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                ln = ln.trim();
                if (ln.isEmpty() || ln.startsWith("#")) continue;
                String[] p = ln.split("\\s+");
                if (p.length < 2) continue;
                try {
                    double d = Double.parseDouble(p[0]);
                    int n = (int) Double.parseDouble(p[1]); // 少数实现写成浮点
                    if (n >= 0) out.add(new TimingPair(d, n));
                } catch (NumberFormatException ignored) {}
            }
        }
        return out;
    }

    private static double computeFactor(List<TimingPair> pairs, Unit unit) {
        if (unit == Unit.SECONDS) return 1.0;
        if (unit == Unit.MICROS)  return 1e-6;
        // AUTO：经验规则——大多数 delay 为“≥1000 且整数”，判为微秒
        int tall = 0, microLike = 0;
        for (TimingPair p : pairs) {
            tall++;
            if (p.delay >= 1000 && Math.floor(p.delay) == p.delay) microLike++;
        }
        return (tall > 0 && microLike >= 0.8 * tall) ? 1e-6 : 1.0;
    }

    private static Pattern ansiPattern() {
        // 仅清除常见 CSI（ESC [ ...）序列；足够用于“转日志”
        return Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        if (n <= 0) return new byte[0];
        byte[] buf = new byte[n];
        int rTot = 0;
        while (rTot < n) {
            int r = in.read(buf, rTot, n - rTot);
            if (r < 0) break;
            rTot += r;
        }
        if (rTot == 0) return null;                // 真正的 EOF
        return (rTot == n) ? buf : Arrays.copyOf(buf, rTot);
    }

    private static String formatLine(ByteArrayOutputStream buf, double tRelSec,
                                     OffsetDateTime base, Pattern ansi, Charset cs) {
        String line = buf.toString(cs);
        buf.reset();
        if (ansi != null) line = ansi.matcher(line).replaceAll("");

        long ms = Math.round(tRelSec * 1000.0);
        OffsetDateTime ts = base.plus(java.time.Duration.ofMillis(ms));
        String stamp = ts.format(ABS_FMT);     // yyyy/MM/dd HH:mm:ss
        return "[" + stamp + "] " + line;
    }
}
