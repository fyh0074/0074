import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

public class Ts2Log {

  static class Pair { final double delay; final int nbytes;
    Pair(double d, int n) { this.delay = d; this.nbytes = n; } }

  static class Args {
    String timing, data; boolean stripAnsi=false, stripCr=false;
    String unit="auto"; OffsetDateTime start=null;
  }

  public static void main(String[] argv) throws Exception {
    Args a = parseArgs(argv); if (a==null) { usage(); System.exit(2); }

    List<Pair> pairs = readTiming(a.timing);
    String unit = "seconds";
    if ("auto".equals(a.unit)) unit = detectUnit(pairs);
    else unit = a.unit;
    double factor = "micros".equals(unit) ? 1e-6 : 1.0;

    Pattern ansi = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    try (InputStream in = new BufferedInputStream(new FileInputStream(a.data))) {
      double tRel = 0.0;
      ByteArrayOutputStream buf = new ByteArrayOutputStream(4096);
      for (Pair p : pairs) {
        tRel += p.delay * factor;
        byte[] chunk = readExact(in, p.nbytes);
        if (chunk == null) break;
        for (byte b : chunk) {
          if (a.stripCr && b=='\r') continue;
          if (b=='\n') { emitLine(buf, tRel, a.start, a.stripAnsi?ansi:null); }
          else buf.write(b);
        }
      }
      if (buf.size()>0) emitLine(buf, tRel, a.start, a.stripAnsi?ansi:null);
    }
  }

  private static void emitLine(ByteArrayOutputStream buf, double tRel,
                               OffsetDateTime start, Pattern ansi) {
    String line = buf.toString(StandardCharsets.UTF_8);
    buf.reset();
    if (ansi!=null) line = ansi.matcher(line).replaceAll("");
    String stamp;
    if (start!=null) {
      long ms = Math.round(tRel*1000.0);
      stamp = start.plusMillis(ms).toString();        // ISO8601
    } else {
      int ms = (int)Math.round((tRel - Math.floor(tRel))*1000.0);
      int hh = (int)Math.floor(tRel/3600.0);
      int mm = (int)Math.floor((tRel%3600)/60.0);
      int ss = (int)Math.floor(tRel%60.0);
      stamp = String.format("T+%02d:%02d:%02d.%03d", hh, mm, ss, ms);
    }
    System.out.println("[" + stamp + "] " + line);
  }

  private static byte[] readExact(InputStream in, int n) throws IOException {
    if (n<=0) return new byte[0];
    byte[] buf = new byte[n]; int rTot=0;
    while (rTot<n) { int r=in.read(buf,rTot,n-rTot); if (r<0) break; rTot+=r; }
    return rTot==0 ? null : (rTot==n ? buf : Arrays.copyOf(buf, rTot));
  }

  private static List<Pair> readTiming(String path) throws IOException {
    List<Pair> out = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(
          new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
      String ln;
      while ((ln = br.readLine()) != null) {
        ln = ln.trim(); if (ln.isEmpty() || ln.startsWith("#")) continue;
        String[] p = ln.split("\\s+"); if (p.length < 2) continue; // 经典两列
        try {
          double d = Double.parseDouble(p[0]);
          int n = (int)Double.parseDouble(p[1]); // 某些实现会写成浮点
          if (n>=0) out.add(new Pair(d,n));
        } catch (NumberFormatException ignore) {}
      }
    }
    return out;
  }

  private static String detectUnit(List<Pair> pairs) {
    if (pairs.isEmpty()) return "seconds";
    int tall=0, microLike=0;
    for (Pair p : pairs) { tall++; if (p.delay>=1000 && Math.floor(p.delay)==p.delay) microLike++; }
    return (tall>0 && microLike >= 0.8*tall) ? "micros" : "seconds";
  }

  private static Args parseArgs(String[] argv) {
    Args a = new Args(); List<String> rest=new ArrayList<>();
    for (int i=0;i<argv.length;i++) {
      switch (argv[i]) {
        case "--strip-ansi": a.stripAnsi=true; break;
        case "--strip-cr": a.stripCr=true; break;
        case "--unit": if (++i>=argv.length) return null; a.unit=argv[i]; break;
        case "--start":
          if (++i>=argv.length) return null;
          try { a.start = OffsetDateTime.parse(argv[i]); }
          catch (DateTimeParseException e) { return null; }
          break;
        case "-h": case "--help": return null;
        default: rest.add(argv[i]);
      }
    }
    if (rest.size()!=2) return null;
    a.timing = rest.get(0); a.data = rest.get(1); return a;
  }

  private static void usage() {
    System.err.println("Usage: java Ts2Log [--start ISO8601] [--unit seconds|micros|auto] [--strip-ansi] [--strip-cr] TIMING_FILE DATA_FILE");
  }
}
