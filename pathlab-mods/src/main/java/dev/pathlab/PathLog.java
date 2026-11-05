package dev.pathlab;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/** One CSV row per handled path request, plus a console line now and then so you can see it working. */
public final class PathLog {
    private PathLog() {}

    private static BufferedWriter out;
    private static int sinceFlush = 0;
    private static long total = 0, totalExpanded = 0, totalReached = 0;
    private static final Set<String> seen = new HashSet<>();

    public static void say(String msg) { System.out.println("[PathLab] " + msg); }

    private static void open() throws IOException {
        if (out != null) return;
        Path dir = Path.of("pathlab");
        Files.createDirectories(dir);
        Path file = dir.resolve(PathLabConfig.ACTIVE.algorithm() + "_" + PathLabConfig.RUN + ".csv");
        boolean fresh = !Files.exists(file) || Files.size(file) == 0;
        out = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (fresh) {
            out.write("epochMillis,algorithm,weight,mob,startX,startY,startZ,reached,hasPath,partial,cost,"
                    + "pathNodes,expanded,generated,peakOpen,capHit,nanos\n");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(PathLog::flush));
        say("logging to " + file.toAbsolutePath());
    }

    public static synchronized void write(String mob, int sx, int sy, int sz, boolean reached, boolean hasPath,
                                          double cost, int pathNodes, int expanded, int generated, int peakOpen,
                                          boolean capHit, long nanos) {
        try {
            open();
            out.write(System.currentTimeMillis() + "," + PathLabConfig.ACTIVE.algorithm() + ","
                    + PathLabConfig.ACTIVE.weight() + "," + mob + "," + sx + "," + sy + "," + sz + ","
                    + reached + "," + hasPath + "," + (hasPath && !reached) + "," + cost + "," + pathNodes + ","
                    + expanded + "," + generated + "," + peakOpen + "," + capHit + "," + nanos + "\n");
            if (++sinceFlush >= 25) { out.flush(); sinceFlush = 0; }
        } catch (IOException e) {
            e.printStackTrace();
        }
        total++; totalExpanded += expanded; if (reached) totalReached++;
        if (seen.add(mob)) say("first path request handled for " + mob + " by " + PathLabConfig.ACTIVE.algorithm());
        if (total % 200 == 0) {
            say(PathLabConfig.ACTIVE.algorithm() + ": " + total + " requests, mean expanded nodes "
                    + (totalExpanded / total) + ", goal reached in " + (100 * totalReached / total) + "%");
        }
    }

    public static synchronized void flush() {
        try { if (out != null) out.flush(); } catch (IOException ignored) { }
    }
}
