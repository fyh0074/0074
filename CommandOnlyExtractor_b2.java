package com.demo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class CommandOnlyExtractor_b2{

    private static final DateTimeFormatter ABS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tokyo");
    private static final String FIXED_TAG = "AAAAA";

    public static void main(String[] args) throws Exception {
        Path typescript = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b");
        Path timing     = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.timing");
        Path out        = Paths.get("C:\\Users\\Administrator\\Downloads\\raglogs\\raglogs\\5eeffdd9-afb4-320f-91ef-0c082d5a7c8b.log");

        ZonedDateTime startTs = ZonedDateTime.now(DEFAULT_ZONE);

        extract(typescript, timing, out, startTs);
        System.out.println("完了: " + out.toAbsolutePath());
    }

    /**
     * 最初の13行を破棄し、14行目から出力する。
     */
    public static void extract(Path namePath, Path timingPath, Path outPath, ZonedDateTime startTs) throws IOException {
        Files.createDirectories(outPath.getParent() != null ? outPath.getParent() : Paths.get("."));

        try (BufferedInputStream tsIn = new BufferedInputStream(Files.newInputStream(namePath));
             BufferedReader tr = Files.newBufferedReader(timingPath, StandardCharsets.UTF_8);
             BufferedWriter out = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            StringBuilder lineBuf = new StringBuilder(4096);
            byte[] buf = new byte[64 * 1024];
            double elapsed = 0.0;
            int lineCounter = 0; // 出力した行数のカウント

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
                } catch (NumberFormatException e) {
                    continue;
                }

                elapsed += delay;
                ZonedDateTime ts = startTs.plusNanos((long) (elapsed * 1_000_000_000L));

                ByteArrayOutputStream chunkBytes = new ByteArrayOutputStream((int) Math.min(count, 1_048_576));
                long remaining = count;
                while (remaining > 0) {
                    int want = (int) Math.min(buf.length, remaining);
                    int n = tsIn.read(buf, 0, want);
                    if (n < 0) break;
                    remaining -= n;
                    chunkBytes.write(buf, 0, n);
                }

                String chunk = sanitize(new String(chunkBytes.toByteArray(), StandardCharsets.UTF_8));

                if (chunk.indexOf('\n') < 0) {
                    lineBuf.append(chunk);
                } else {
                    String merged = lineBuf.append(chunk).toString();
                    lineBuf.setLength(0);

                    String[] rows = merged.split("\n", -1);
                    for (int i = 0; i < rows.length - 1; i++) {
                        String row = rows[i].trim();
                        if (row.isEmpty()) continue;

                        lineCounter++;
                        if (lineCounter > 13) { // 13行を超えたら出力
                            writeStamped(out, elapsed, ts, row);
                        }
                    }
                    String tail = rows[rows.length - 1];
                    if (!tail.isEmpty()) lineBuf.append(tail);
                }
            }

            if (lineBuf.length() > 0) {
                String row = lineBuf.toString().trim();
                if (!row.isEmpty()) {
                    lineCounter++;
                    if (lineCounter > 13) {
                        ZonedDateTime ts = startTs.plusNanos((long) (elapsed * 1_000_000_000L));
                        try (BufferedWriter out2 = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8,
                                StandardOpenOption.APPEND)) {
                            writeStamped(out2, elapsed, ts, row);
                        }
                    }
                }
            }
        }
    }

    private static void writeStamped(BufferedWriter out, double elapsed, ZonedDateTime ts, String row) throws IOException {
        out.write(formatRel(elapsed));
        out.write(',');
        out.write(ABS_FMT.format(ts));
        out.write(',');
        out.write(FIXED_TAG);
        out.write(',');
        out.write(row);
        out.write('\n');
    }

    // —— サニタイズ処理（制御シーケンス等を除去し、\nは保持） —— //
    private static String sanitize(String s) {
        s = s.replaceAll("\\u009B[\\x20-\\x3F]*[\\x40-\\x7E]", "");
        s = s.replaceAll("\\u009D[\\s\\S]*?(\\u0007|\\u009C)", "");
        s = s.replaceAll("[\\u0090\\u009E\\u009F\\u0098][\\s\\S]*?\\u009C", "");
        s = s.replaceAll("\\u001B\\][\\s\\S]*?(\\u0007|\\u001B\\\\)", "");
        s = s.replaceAll("\\u001B[P^_X][\\s\\S]*?\\u001B\\\\", "");
        s = s.replaceAll("\\u001B\\[[\\x20-\\x3F]*[\\x40-\\x7E]", "");
        s = s.replaceAll("\\u001B.", "");
        s = s.replaceAll("\\^\\[\\[[0-9;]*[A-Za-z]", "");
        s = s.replaceAll("\\^\\[", "");
        s = s.replaceAll("\\^M", "\n");
        s = applyBackspaces(s);
        s = applyCarriageReturns(s);
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1A\\x1C-\\x1F]", "");
        return s;
    }

    private static String applyBackspaces(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\b') {
                if (out.length() > 0) out.deleteCharAt(out.length() - 1);
            } else {
                out.append(c);
            }
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

    private static String formatRel(double elapsedSeconds) {
        long total = (long) Math.floor(elapsedSeconds);
        long hh = total / 3600;
        long mm = (total % 3600) / 60;
        long ss = total % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }
}
