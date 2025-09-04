package com.demo.test01;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TypescriptToLog
 * 输入：NAME（原始字节流, UTF-8） + NAME.timing（两列：<delaySeconds> <bytes>）
 * 输出：按 NAME 的“行”输出日志：HH:mm:ss,yyyy/MM/dd HH:mm:ss,AAAA,内容
 * 处理：
 *   - 退格(\b)与回车覆盖(\r)
 *   - 去 ANSI 转义
 *   - 欢迎语/MOTD、Vim 屏幕/状态行、框线密集、空白行过滤
 *   - ⚠ 保留原始的左侧缩进与空格间隔（不 trim、不压缩空格）
 */
public class TypescriptToLog {

    // 固定标签
    private static final String FIXED_TAG = "AAAA";

    // 输出时间格式与时区
    private static final DateTimeFormatter ABS_FMT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tokyo");

    // timing 行解析：<seconds(double)> <bytes(int)>
    private static final Pattern TIMING_LINE =
            Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s+(\\d+)\\s*$");

    // 欢迎语/标记（常见 MOTD / 登录横幅 / typescript 标记）
    private static final Pattern WELCOME_PAT = Pattern.compile(
            "(?i)^(?:\\[?BEGIN\\s+TYPESCRIPT\\]?|\\[?END\\s+TYPESCRIPT\\]?|welcome\\s+to\\s+ubuntu|debian\\s+gnu/linux|documentation:|last\\s+login:|the\\s+programs\\s+included\\s+with\\s+the\\s+debian|\\*\\s+.+)"
    );

    // Vim 状态/屏幕特征
    private static final Pattern VIM_MODE = Pattern.compile("\\s--\\s(?:INSERT|VISUAL|REPLACE)\\s--");
    private static final Pattern VIM_FILE_NEW = Pattern.compile("^\\s*\"[^\"]+\"\\s*\\[(?:New File|New)\\]\\s*$");
    private static final Pattern VIM_FILE_STATS = Pattern.compile("^\\s*\"[^\"]+\"\\s*\\d+L,\\s*\\d+B\\s*$");
    private static final Pattern TILDE_LINE = Pattern.compile("^[\\s~]+$");
    private static final Pattern BOX_DRAW = Pattern.compile("[\\u2500-\\u257F\\u2580-\\u259F]+"); // 框线/方块

    public static void main(String[] args) throws Exception {
//        if (args.length < 3) {
//            System.err.println("用法: java TypescriptToLog <NAME> <NAME.timing> <output.log> [--start <ts>] [--raw]");
//            System.err.println("  <ts> 支持 ISO-8601 (例: 2025-09-03T21:34:01+09:00) 或 yyyy/MM/dd HH:mm:ss");
//            System.exit(1);
//        }
//
//        Path namePath   = Paths.get(args[0]);
//        Path timingPath = Paths.get(args[1]);
//        Path outPath    = Paths.get(args[2]);
        Path namePath = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b");
        Path timingPath     = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.timing");
        Path outPath        = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.log");

        String startStr = null;
        boolean rawMode = false; // --raw 仅影响“清理控制字符”的强度，但不会压缩/修剪空格
        for (int i = 3; i < args.length; i++) {
            if ("--start".equals(args[i]) && i + 1 < args.length) {
                startStr = args[++i];
            } else if ("--raw".equals(args[i])) {
                rawMode = true;
            }
        }

        Instant startInstant = resolveStartInstant(startStr);
        convert(namePath, timingPath, outPath, startInstant, rawMode);
        System.out.println("完成: " + outPath.toAbsolutePath());
    }

    // 解析绝对起点时间
    private static Instant resolveStartInstant(String startStr) {
        if (startStr == null || startStr.isEmpty()) {
            return ZonedDateTime.now(DEFAULT_ZONE).toInstant();
        }
        try { // ISO-8601
            return Instant.parse(startStr);
        } catch (Exception ignore) {}
        try { // yyyy/MM/dd HH:mm:ss
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime ldt = LocalDateTime.parse(startStr, fmt);
            return ldt.atZone(DEFAULT_ZONE).toInstant();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 --start 时间: " + startStr);
        }
    }

    /**
     * 按 NAME 的“行”为单位输出：
     * - 逐字节推进 timing；每消费 1 字节推进一次游标
     * - 行尾（读到 '\n' 或文件末尾）时的累计时间即为该行相对时间
     */
    public static void convert(Path namePath,
                               Path timingPath,
                               Path outPath,
                               Instant absoluteStart,
                               boolean rawMode) throws IOException {

        Deque<TimingEntry> timing = loadTiming(timingPath);
        TimingCursor cursor = new TimingCursor(timing);

        Files.createDirectories(outPath.getParent() == null ? Paths.get(".") : outPath.getParent());
        try (InputStream dataIn = new BufferedInputStream(Files.newInputStream(namePath));
             BufferedWriter writer = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(4096);
            double elapsedAtLineEnd = 0.0;

            while (true) {
                int bi = dataIn.read();
                if (bi == -1) {
                    // 文件结束，若缓冲内有未换行的最后一行，也要输出
                    if (lineBuf.size() > 0) {
                        elapsedAtLineEnd = cursor.consumeBytes(0);
                        emitLine(writer, lineBuf.toByteArray(), elapsedAtLineEnd, absoluteStart, rawMode);
                        lineBuf.reset();
                    }
                    break;
                }

                // 如果 timing 已枯竭，则无法再精确计时，直接结束
                if (!cursor.hasMore() && cursor.bytesRemaining == 0) break;

                elapsedAtLineEnd = cursor.consumeBytes(1);
                lineBuf.write(bi);

                if (bi == '\n') {
                    byte[] rawLine = stripTrailingCR(lineBuf.toByteArray());
                    emitLine(writer, rawLine, elapsedAtLineEnd, absoluteStart, rawMode);
                    lineBuf.reset();
                }
            }
        }
    }

    // 读取 timing 文件为队列
    private static Deque<TimingEntry> loadTiming(Path timingPath) throws IOException {
        Deque<TimingEntry> q = new ArrayDeque<>();
        try (BufferedReader br = Files.newBufferedReader(timingPath, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                ln = ln.trim();
                if (ln.isEmpty() || ln.startsWith("#")) continue;
                Matcher m = TIMING_LINE.matcher(ln);
                if (!m.matches()) continue;
                double delay = Double.parseDouble(m.group(1));
                int bytes = Integer.parseInt(m.group(2));
                q.addLast(new TimingEntry(delay, bytes));
            }
        }
        return q;
    }

    // timing 条目与游标
    private static final class TimingEntry {
        final double delay;
        final int bytes;
        TimingEntry(double delay, int bytes) { this.delay = delay; this.bytes = bytes; }
    }
    private static final class TimingCursor {
        final Deque<TimingEntry> q;
        double elapsed = 0.0;
        int bytesRemaining = 0;

        TimingCursor(Deque<TimingEntry> q) { this.q = q; }
        boolean hasMore() { return !q.isEmpty(); }

        // 消费 n 个字节，返回当前累计相对时间
        // 规则：进入新块时先加 delay，再消费该块 bytes
        double consumeBytes(int n) {
            int left = n;
            while (left > 0) {
                if (bytesRemaining == 0) {
                    if (q.isEmpty()) break;
                    TimingEntry e = q.removeFirst();
                    elapsed += e.delay;
                    bytesRemaining = e.bytes;
                }
                int step = Math.min(bytesRemaining, left);
                bytesRemaining -= step;
                left -= step;
            }
            return elapsed;
        }
    }

    // 行尾 CRLF -> 去掉 CR
    private static byte[] stripTrailingCR(byte[] arr) {
        if (arr.length >= 2 && arr[arr.length - 2] == '\r' && arr[arr.length - 1] == '\n') {
            return Arrays.copyOf(arr, arr.length - 2);
        }
        return arr;
    }

    // 输出一行：处理→过滤→格式化→写入
    private static void emitLine(BufferedWriter writer,
                                 byte[] rawLine,
                                 double elapsedAtEnd,
                                 Instant start,
                                 boolean rawMode) throws IOException {

        if (rawLine.length == 0) return;
        String s = new String(rawLine, StandardCharsets.UTF_8);

        // 退格/回车覆盖优先：保留覆盖结果
        s = applyBackspaceAndCR(s);
        // 去 ANSI
        s = stripAnsi(s);

        // 行级过滤：欢迎语/Vim 屏幕/框线密集/空白
        if (shouldDropLineStrict(s)) return;

        // 单行化（不 trim、不压缩空格；仅把 \r/\n 变成空格以保持一行）
        String content = rawMode ? toOneLinePreserveSpacesRaw(s) : sanitizeToOneLinePreserveSpaces(s);
        if (content.length() == 0) return;

        // 时间列
        String rel = formatRelHMS(elapsedAtEnd);
        String abs = formatAbs(start, elapsedAtEnd);

        writer.write(rel); writer.write(',');
        writer.write(abs); writer.write(',');
        writer.write(FIXED_TAG); writer.write(',');
        writer.write(content);
        writer.write(System.lineSeparator());
    }

    // ====== 内容处理/过滤 ======

    // 退格与回车覆盖
    private static String applyBackspaceAndCR(String s) {
        if (s == null || s.isEmpty()) return "";

        // 退格：逐字符删除
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\b') {
                int len = sb.length();
                if (len > 0) sb.deleteCharAt(len - 1);
            } else {
                sb.append(ch);
            }
        }

        // 回车覆盖：对每个“行段”，保留最后一个 \r 后的内容
        String[] parts = sb.toString().split("\n", -1);
        StringBuilder rebuilt = new StringBuilder(sb.length());
        for (int i = 0; i < parts.length; i++) {
            String ln = parts[i];
            int lastCR = ln.lastIndexOf('\r');
            if (lastCR >= 0) ln = ln.substring(lastCR + 1);
            rebuilt.append(ln);
            if (i < parts.length - 1) rebuilt.append('\n');
        }
        return rebuilt.toString();
    }

    // 去 ANSI 转义（CSI/OSC/其他 ESC 序列）
    private static String stripAnsi(String s) {
        if (s == null || s.isEmpty()) return "";
        String noCsi = s.replaceAll("\u001B\\[[0-9;?]*[ -/]*[@-~]", "");
        String noOsc = noCsi.replaceAll("\u001B\\][^\\u0007\u001B]*(?:\u0007|\u001B\\\\)", "");
        return noOsc.replaceAll("\u001B.", "");
    }

    // 原样单行化（不清理控制字符；仅用于 --raw）
    private static String toOneLinePreserveSpacesRaw(String s) {
        if (s == null || s.isEmpty()) return "";
        // 只把换行/回车替换为空格；保留制表符与所有空格；不 trim、不压缩
        return s.replace('\r', ' ').replace('\n', ' ');
    }

    // 清理后单行化：去除除 \n\r\t 外的控制字符，再把 \r/\n 变空格；不 trim、不压缩
    private static String sanitizeToOneLinePreserveSpaces(String s) {
        if (s == null || s.isEmpty()) return "";
        String noCtl = s.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "");
        return noCtl.replace('\r', ' ').replace('\n', ' ');
    }

    // 行级严格过滤：欢迎语/Vim/框线密集/空白
    private static boolean shouldDropLineStrict(String s) {
        if (s == null) return true;
        // 用于判断的临时变量可以 trim，但不影响实际输出（我们对内容不 trim）
        String t = s.trim();
        if (t.isEmpty()) return true;

        if (WELCOME_PAT.matcher(t).find()) return true;
        if (VIM_MODE.matcher(t).find()) return true;
        if (VIM_FILE_NEW.matcher(t).find()) return true;
        if (VIM_FILE_STATS.matcher(t).find()) return true;
        if (TILDE_LINE.matcher(t).matches()) return true;

        int boxChars = countMatches(BOX_DRAW, t);
        if (boxChars > 0 && (double) boxChars / Math.max(1, t.length()) > 0.3) return true;

        return false;
    }

    private static int countMatches(Pattern p, String s) {
        int c = 0;
        Matcher m = p.matcher(s);
        while (m.find()) c += m.group().length();
        return c;
    }

    // ====== 时间格式化 ======
    private static String formatRelHMS(double elapsedSec) {
        long total = (long)Math.floor(elapsedSec);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
    private static String formatAbs(Instant start, double elapsedSec) {
        Instant ts = start.plus((long)(elapsedSec * 1000.0), ChronoUnit.MILLIS);
        return ABS_FMT.format(ZonedDateTime.ofInstant(ts, DEFAULT_ZONE));
    }
}

