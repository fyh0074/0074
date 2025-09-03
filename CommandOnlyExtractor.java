package com.demo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class CommandOnlyExtractor {

    // 絶対時間：yyyy-MM-dd HH:mm:ss
    private static final DateTimeFormatter TS_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE       = ZoneId.of("Asia/Tokyo");

    public static void main(String[] args) throws Exception {
        Path typescript = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b");
        Path timing     = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.timing");
        Path out        = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.log");
        boolean stamp   = true;
        ZonedDateTime startTs = ZonedDateTime.now(DEFAULT_ZONE);

        extract(typescript, timing, out, stamp, startTs);
        System.out.println("完了: " + out.toAbsolutePath());
    }

    /** 2列のtiming：countごとに読み込む；改行がなければバッファに追加、改行があれば分割；出力は「プロンプト+コマンド」のみ。
     *  各行の出力形式： HH:mm:ss,yyyy-MM-dd HH:mm:ss,AAAAA, <prompt><command>
     */
    public static void extract(Path namePath, Path timingPath, Path outPath,
                               boolean stamp, ZonedDateTime startTs) throws IOException {
        Files.createDirectories(outPath.getParent() != null ? outPath.getParent() : Paths.get("."));

        try (BufferedInputStream tsIn = new BufferedInputStream(Files.newInputStream(namePath));
             BufferedReader tr = Files.newBufferedReader(timingPath, StandardCharsets.UTF_8);
             BufferedWriter out = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            StringBuilder bufLine = new StringBuilder(4096);
            byte[] buf = new byte[64 * 1024];
            double elapsed = 0.0; // 秒（小数あり）

            String currentPrompt = ""; // 最新のプロンプト（末尾の空白付き）

            String line;
            while ((line = tr.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;

                double delay; long count;
                try {
                    delay = Double.parseDouble(parts[0]);
                    count = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) { continue; }

                // 相対時間を累計
                elapsed += delay;
                ZonedDateTime absTs = startTs.plusNanos((long)(elapsed * 1_000_000_000L));
                String relStr = formatRel(elapsed);
                String absStr = TS_FMT.format(absTs);

                // この区間のバイトを読み込む
                ByteArrayOutputStream chunkBytes = new ByteArrayOutputStream((int)Math.min(count, 1_048_576));
                long remaining = count;
                while (remaining > 0) {
                    int want = (int)Math.min(buf.length, remaining);
                    int n = tsIn.read(buf, 0, want);
                    if (n < 0) break;
                    remaining -= n;
                    chunkBytes.write(buf, 0, n);
                }

                String chunk = sanitize(new String(chunkBytes.toByteArray(), StandardCharsets.UTF_8));

                if (chunk.indexOf('\n') < 0) {
                    // 改行なし：バッファに追加
                    bufLine.append(chunk);
                } else {
                    // 改行あり：結合＋分割
                    String merged = bufLine.append(chunk).toString();
                    bufLine.setLength(0);

                    String[] rows = merged.split("\n", -1);
                    for (int i = 0; i < rows.length - 1; i++) {
                        String row = rows[i];
                        if (row.isEmpty()) continue;

                        // 1) 純粋なプロンプト：更新のみ、出力しない
                        if (PROMPT_ONLY.matcher(row).matches()) {
                            currentPrompt = ensureSpace(row);
                            continue;
                        }

                        // 2) プロンプト+コマンド：出力（REL,ABS,AAAAA, <prompt><cmd>）
                        java.util.regex.Matcher m = PROMPT_WITH_CMD.matcher(row);
                        if (m.matches()) {
                            String prompt = ensureSpace(m.group(1));
                            String cmd    = m.group(2).trim();
                            if (!cmd.isEmpty()) {
                                out.write(relStr);
                                out.write(",");
                                out.write(absStr);
                                out.write(",AAAAA,");
                                out.write(prompt);
                                out.write(cmd);
                                out.write('\n');
                            }
                            currentPrompt = prompt;
                            continue;
                        }

                        // 3) その他の行は無視
                    }

                    // 残りの半行
                    String tail = rows[rows.length - 1];
                    if (!tail.isEmpty()) bufLine.append(tail);
                }
            }

            // 最後の半行：もう一度判定
            if (bufLine.length() > 0) {
                String row = bufLine.toString();
                java.util.regex.Matcher m = PROMPT_WITH_CMD.matcher(row);
                if (m.matches()) {
                    String prompt = ensureSpace(m.group(1));
                    String cmd    = m.group(2).trim();
                    if (!cmd.isEmpty()) {
                        // 最終的なelapsed/absTsを使用
                        String relStr = formatRel(elapsed);
                        String absStr = TS_FMT.format(startTs.plusNanos((long)(elapsed * 1_000_000_000L)));
                        try (BufferedWriter out2 = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                            out2.write(relStr);
                            out2.write(",");
                            out2.write(absStr);
                            out2.write(",AAAAA, ");
                            out2.write(prompt);
                            out2.write(cmd);
                            out2.write('\n');
                        }
                    }
                }
            }
        }
    }

    // —— 相対時間：0から開始、形式 HH:mm:ss ——
    private static String formatRel(double elapsedSeconds) {
        long totalSeconds = (long) elapsedSeconds;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /* —— 「プロンプト+コマンド」または「純粋なプロンプト」のみを判定 —— */
    private static final Pattern PROMPT_ONLY = Pattern.compile(
        "^(.*(?:\\$|#|>|%|\\]#|(?i:PS>)|(?i:mysql>)) )$"
    );

    private static final Pattern PROMPT_WITH_CMD = Pattern.compile(
        "^(.*(?:\\$|#|>|%|\\]#|(?i:PS>)|(?i:mysql>))\\s)(\\S.*)$"
    );

    private static String ensureSpace(String s) { return s.endsWith(" ") ? s : (s + " "); }

    /* —— 正規化処理（\nは保持；ANSI/OSC/CSI、バックスペース、CR上書きを削除）—— */
    private static String sanitize(String s) {
        // 8-bit C1
        s = s.replaceAll("\\u009B[\\x20-\\x3F]*[\\x40-\\x7E]", "");
        s = s.replaceAll("\\u009D[\\s\\S]*?(\\u0007|\\u009C)", "");
        s = s.replaceAll("[\\u0090\\u009E\\u009F\\u0098][\\s\\S]*?\\u009C", "");
        // 7-bit ESC（OSC/DCS/CSIなど）
        s = s.replaceAll("\\u001B\\][\\s\\S]*?(\\u0007|\\u001B\\\\)", "");
        s = s.replaceAll("\\u001B[P^_X][\\s\\S]*?\\u001B\\\\", "");
        s = s.replaceAll("\\u001B\\[[\\x20-\\x3F]*[\\x40-\\x7E]", "");
        s = s.replaceAll("\\u001B.", "");
        // キャレット可視化
        s = s.replaceAll("\\^\\[\\[[0-9;]*[A-Za-z]", "");
        s = s.replaceAll("\\^\\[", "");
        s = s.replaceAll("\\^M", "\n");
        // バックスペース
        s = applyBackspaces(s);
        // CR上書き
        s = applyCarriageReturns(s);
        // \n\tは保持、それ以外のC0は削除
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1A\\x1C-\\x1F]", "");
        return s;
    }

    private static String applyBackspaces(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\b') { if (out.length() > 0) out.deleteCharAt(out.length() - 1); }
            else out.append(c);
        }
        return out.toString();
    }

    private static String applyCarriageReturns(String s) {
        String[] lines = s.split("\n", -1);
        StringBuilder result = new StringBuilder(s.length());
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            if (line.indexOf('\r') >= 0) {
                String[] parts = line.split("\\r");
                String rebuilt = "";
                for (String part : parts) {
                    if (part.length() >= rebuilt.length()) rebuilt = part;
                    else rebuilt = part + rebuilt.substring(part.length());
                }
                line = rebuilt;
            }
            result.append(line);
            if (li < lines.length - 1) result.append('\n');
        }
        return result.toString();
    }
}
