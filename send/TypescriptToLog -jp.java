package com.demo.test04;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypescriptToLog {

    private static final DateTimeFormatter ABS_FMT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tokyo");
    private static final String FIXED_TAG = "AAAA";
    private static final BigDecimal BD_1E9 = new BigDecimal("1000000000"); // 秒→ナノ秒
    // Vim の状態/画面の特徴
    private static final Pattern VIM_MODE = Pattern.compile("\\s--\\s(?:INSERT|VISUAL|REPLACE)\\s--");
    private static final Pattern VIM_FILE_NEW = Pattern.compile("^\\s*\"[^\"]+\"\\s*\\[(?:New File|New)\\]\\s*$");
    private static final Pattern VIM_FILE_STATS = Pattern.compile("^\\s*\"[^\"]+\"\\s*\\d+L,\\s*\\d+B\\s*$");
    private static final Pattern TILDE_LINE = Pattern.compile("^[\\s~]+$");
    private static final Pattern BOX_DRAW = Pattern.compile("[\\u2500-\\u257F\\u2580-\\u259F]+"); // 罫線/ブロック
    private static long lastRelSec = -1; // 直前に出力した相対秒を記録

    public static void main(String[] args) throws Exception {
        Path namePath = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b");
        Path timingPath     = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.timing");
        Path outPath        = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.log");

        Instant start = LocalDateTime.parse("2025-09-03 19:41:45",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(DEFAULT_ZONE).toInstant();

        boolean stripAnsi = true;

        try (Writer writer = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            convert(namePath, timingPath, writer, DEFAULT_ZONE, start, FIXED_TAG, stripAnsi);
        }
        System.out.println("日志已输出到: " + outPath);
    }

    public static void convert(Path typescript, Path timing, Writer out,
                               ZoneId zone, Instant start, String tag, boolean stripAnsi) throws IOException {

        // ===== NAME の前処理：先頭行 [BEGIN TYPESCRIPT] と末尾行 [END TYPESCRIPT] を取り除く =====
        byte[] raw = Files.readAllBytes(typescript);

        int firstLineEnd = indexOfNewline(raw, 0);
        int startOffset = 0;
        if (firstLineEnd > 0) {
            String firstLine = decodeTrim(raw, 0, firstLineEnd);
            if ("[BEGIN TYPESCRIPT]".equals(firstLine)) {
                startOffset = firstLineEnd;
            }
        }

        int lastLineStart = lastIndexOfNewline(raw, raw.length);
        if (lastLineStart < 0) lastLineStart = 0;
        String lastLine = decodeTrim(raw, lastLineStart, raw.length);
        int endOffset = raw.length;
        if ("[END TYPESCRIPT]".equals(lastLine)) {
            endOffset = lastLineStart;
        }

        if (startOffset < 0 || startOffset > endOffset) startOffset = 0;
        byte[] filtered = new byte[endOffset - startOffset];
        System.arraycopy(raw, startOffset, filtered, 0, filtered.length);

        // ヘッダーをスキップする必要があるか
        boolean skipHeader = new String(filtered, StandardCharsets.UTF_8).contains("Last login:");

        try (BufferedInputStream tsIn = new BufferedInputStream(new ByteArrayInputStream(filtered));
             BufferedReader timingReader = Files.newBufferedReader(timing, StandardCharsets.UTF_8)) {

            BigDecimal relSecAccum = BigDecimal.ZERO;
            StringBuilder lineBuf = new StringBuilder();
            byte[] chunkBuf = new byte[64 * 1024];

            boolean reachedEnd = false;
            boolean crPendingClear = false;

            AnsiStripper stripper = new AnsiStripper();

            String tline;
            while (!reachedEnd && (tline = timingReader.readLine()) != null) {
                if (tline.isEmpty()) continue;
                String[] cols = tline.split("\\s+");
                if (cols.length < 2) continue;

                BigDecimal delaySec;
                long bytes;
                try {
                    delaySec = new BigDecimal(cols[0]);
                    bytes    = Long.parseLong(cols[1]);
                } catch (NumberFormatException e) {
                    continue;
                }

                relSecAccum = relSecAccum.add(delaySec);

                long remaining = bytes;
                while (!reachedEnd && remaining > 0) {
                    int toRead = (int) Math.min(chunkBuf.length, remaining);
                    int n = tsIn.read(chunkBuf, 0, toRead);
                    if (n < 0) { remaining = 0; break; }
                    remaining -= n;

                    String piece = new String(chunkBuf, 0, n, StandardCharsets.UTF_8);
                    String cleaned = stripAnsi ? stripper.process(piece) : piece;

                    for (int i = 0; !reachedEnd && i < cleaned.length(); i++) {
                        char ch = cleaned.charAt(i);

                        if (ch == '\b') {
                            if (lineBuf.length() > 0) lineBuf.deleteCharAt(lineBuf.length() - 1);
                            continue;
                        }

                        if (ch == '\r') { crPendingClear = true; continue; }
                        if (crPendingClear) {
                            if (ch == '\n') {
                                skipHeader = flushOneLine(lineBuf.toString(), out, relSecAccum, start, zone, tag, skipHeader);
                                lineBuf.setLength(0);
                                crPendingClear = false;
                                continue;
                            } else if (isLinePrintable(ch)) {
                                lineBuf.setLength(0);
                                crPendingClear = false;
                            } else {
                                crPendingClear = false;
                            }
                        }

                        lineBuf.append(ch);

                        if (ch == '\n') {
                            String oneLine = lineBuf.substring(0, lineBuf.length() - 1);
                            lineBuf.setLength(0);

                            if (oneLine.contains("[END TYPESCRIPT]")) {
                                reachedEnd = true;
                                break;
                            }

                            skipHeader = flushOneLine(oneLine, out, relSecAccum, start, zone, tag, skipHeader);
                        }
                    }
                }
            }

            if (!reachedEnd && lineBuf.length() > 0) {
                String tail = lineBuf.toString();
                if (!tail.contains("[END TYPESCRIPT]")) {
                    flushOneLine(tail, out, relSecAccum, start, zone, tag, skipHeader);
                }
            }
        }
    }
    // 行単位の厳格フィルタ：ウェルカムメッセージ/Vim/罫線が密集/空白
    private static boolean shouldDropLineStrict(String s) {
        if (s == null) return true;
        // 判定用の一時変数は trim して良いが、実際の出力には影響しない（内容自体は trim しない）
        String t = s.trim();
        if (t.isEmpty()) return true;

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

    /** 1行を処理し、更新後の skipHeader 状態を返す */
    private static boolean flushOneLine(String oneLine, Writer out,
                                        BigDecimal relSecAccum, Instant start, ZoneId zone, String tag,
                                        boolean skipHeader) throws IOException {
        String s = oneLine.replace("\r", "").replace("\n", "");
        if (skipHeader) {
            if (s.contains("Last login:")) {
                return false; // この行をスキップしてヘッダー処理を終了
            }
            return true; // 引き続きスキップ
        }

        String cleaned = s.strip();
        if (!cleaned.isEmpty()) {
            long relNanos = relSecAccum.multiply(BD_1E9).setScale(0, BigDecimal.ROUND_HALF_UP).longValue();
            Instant absInstant = start.plusNanos(relNanos);
            emit(out, relNanos, absInstant, zone, tag, cleaned);
        }
        return false;
    }

    private static int indexOfNewline(byte[] b, int off) {
        for (int i = off; i < b.length; i++) {
            if (b[i] == '\n') return i + 1;
            if (b[i] == '\r') return (i + 1 < b.length && b[i + 1] == '\n') ? i + 2 : i + 1;
        }
        return b.length;
    }

    private static int lastIndexOfNewline(byte[] b, int end) {
        for (int i = end - 1; i >= 0; i--) {
            if (b[i] == '\n' || b[i] == '\r') return i + 1;
        }
        return -1;
    }

    private static String decodeTrim(byte[] b, int from, int to) {
        if (from < 0) from = 0;
        if (to > b.length) to = b.length;
        if (from >= to) return "";
        return new String(b, from, to - from, StandardCharsets.UTF_8)
                .replace("\r", "").replace("\n", "").trim();
    }

    private static boolean isLinePrintable(char ch) {
        if (ch == '\t' || ch == ' ') return true;
        if (Character.isLetterOrDigit(ch)) return true;
        return "~!@#$%^&*()_+`-={}[]|\\:;\"'<>,.?/".indexOf(ch) >= 0;
    }

    private static void emit(Writer out, long relNanos, Instant absInstant, ZoneId zone,
                             String tag, String content) throws IOException {
    	if (shouldDropLineStrict(content)) return;
    	long relSec = relNanos / 1_000_000_000L;
    	String rel;
        if (relSec == lastRelSec) {
            rel = "        "; // 8 個の空白で代替
        } else {
            rel = formatRel(relNanos);
            lastRelSec = relSec;
        }
        String abs = ABS_FMT.format(ZonedDateTime.ofInstant(absInstant, zone));
        out.write(rel); out.write(','); out.write(abs); out.write(','); out.write(tag); out.write(','); out.write(content);
        out.write(System.lineSeparator());
    }

    private static String formatRel(long relNanos) {
        long totalSec = relNanos / 1_000_000_000L;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = (totalSec % 60);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    /**
     * ブロックをまたぐ制御シーケンス除去器：CSI/OSC/DCS/PM/APC/DEC プライベートモード
     */
    private static class AnsiStripper {
        private enum State { NORMAL, ESC, CSI, OSC, DCS, PM, APC, OSC_ESC, DCS_ESC, PM_ESC, APC_ESC }
        private State state = State.NORMAL;

        String process(String s) {
            StringBuilder out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                switch (state) {
                    case NORMAL:
                        if (ch == 0x1B) state = State.ESC; else out.append(ch);
                        break;
                    case ESC:
                        switch (ch) {
                            case '[': state = State.CSI; break;
                            case ']': state = State.OSC; break;
                            case 'P': state = State.DCS; break;
                            case '^': state = State.PM;  break;
                            case '_': state = State.APC; break;
                            case '\\': state = State.NORMAL; break;
                            default: state = State.NORMAL; break;
                        }
                        break;
                    case CSI:
                        if (ch >= 0x40 && ch <= 0x7E) state = State.NORMAL; // 最終バイトまで読み飛ばす
                        break;
                    case OSC:
                        if (ch == 0x07) state = State.NORMAL;              // BEL
                        else if (ch == 0x1B) state = State.OSC_ESC;        // ST?
                        break;
                    case OSC_ESC:
                        state = (ch == '\\') ? State.NORMAL : State.OSC;
                        break;
                    case DCS:
                        if (ch == 0x1B) state = State.DCS_ESC;
                        break;
                    case DCS_ESC:
                        state = (ch == '\\') ? State.NORMAL : State.DCS;
                        break;
                    case PM:
                        if (ch == 0x1B) state = State.PM_ESC;
                        break;
                    case PM_ESC:
                        state = (ch == '\\') ? State.NORMAL : State.PM;
                        break;
                    case APC:
                        if (ch == 0x1B) state = State.APC_ESC;
                        break;
                    case APC_ESC:
                        state = (ch == '\\') ? State.NORMAL : State.APC;
                        break;
                }
            }
            return out.toString();
        }
    }
}
