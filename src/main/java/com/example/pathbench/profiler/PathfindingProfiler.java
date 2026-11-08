package com.example.pathbench.profiler;

import com.example.pathbench.VillagerPathBenchMod;
import com.example.pathbench.algorithm.Pathfinder;
import com.example.pathbench.mixin.PathFinderAccessor;
import com.example.pathbench.mixin.PathNavigationAccessor;
import com.sun.management.ThreadMXBean;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous CSV Profiler that logs every Villager workstation (POI) pathfinding event
 * to <gameDir>/config/villager-pathbench/pathfinding_metrics.csv and flushes immediately.
 */
public final class PathfindingProfiler {

    private static final String CSV_HEADER = String.join(",",
            "timestamp",
            "algorithm",
            "mob",
            "startX", "startY", "startZ",
            "targetX", "targetY", "targetZ",
            "reached",
            "partial",
            "capHit",
            "expanded",
            "cost",
            "nanos",
            "heapUsedBytes",
            "heapDeltaBytes",
            "threadAllocatedBytes",
            "cpuLoadPercent",
            "pathLength"
    );

    private static final BlockingQueue<String> LOG_QUEUE = new LinkedBlockingQueue<>(16384);
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicInteger TOTAL_LOGGED = new AtomicInteger(0);
    private static Thread writerThread;
    private static java.nio.file.Path csvFilePath;

    private static final ThreadMXBean THREAD_MX_BEAN = initThreadMxBean();
    private static final com.sun.management.OperatingSystemMXBean OS_MX_BEAN = initOsMxBean();

    private PathfindingProfiler() {
    }

    private static ThreadMXBean initThreadMxBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof ThreadMXBean sunBean) {
            try {
                if (sunBean.isThreadAllocatedMemorySupported() && !sunBean.isThreadAllocatedMemoryEnabled()) {
                    sunBean.setThreadAllocatedMemoryEnabled(true);
                }
                return sunBean;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static com.sun.management.OperatingSystemMXBean initOsMxBean() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            return sunOsBean;
        }
        return null;
    }

    public static synchronized void init() {
        if (RUNNING.get()) {
            return;
        }
        try {
            java.nio.file.Path configDir = FabricLoader.getInstance().getConfigDir().resolve(VillagerPathBenchMod.MOD_ID);
            Files.createDirectories(configDir);
            csvFilePath = configDir.resolve("pathfinding_metrics.csv");

            if (!Files.exists(csvFilePath) || Files.size(csvFilePath) == 0L) {
                Files.writeString(csvFilePath, CSV_HEADER + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }

            RUNNING.set(true);
            writerThread = new Thread(PathfindingProfiler::processQueue, "PathBench-CSV-Writer");
            writerThread.setDaemon(true);
            writerThread.start();

            VillagerPathBenchMod.LOGGER.info("CSV pathfinding profiler writing to: {}", csvFilePath.toAbsolutePath());
        } catch (IOException e) {
            VillagerPathBenchMod.LOGGER.error("Failed to initialize CSV pathfinding profiler", e);
        }
    }

    public static int getTotalLoggedCount() {
        return TOTAL_LOGGED.get();
    }

    public static long captureThreadAllocatedBytes() {
        if (THREAD_MX_BEAN != null && THREAD_MX_BEAN.isThreadAllocatedMemorySupported()) {
            return THREAD_MX_BEAN.getCurrentThreadAllocatedBytes();
        }
        return -1L;
    }

    public static long captureUsedHeapBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /**
     * Executes and logs a direct benchmark run for a mob to the specified target positions.
     */
    public static Path benchmarkDirect(Mob mob, Level level, Set<BlockPos> targets, int reachRange) {
        PathNavigation nav = mob.getNavigation();
        PathNavigationAccessor navAcc = (PathNavigationAccessor) nav;
        float followRange = navAcc.invokeGetMaxPathLength();
        BlockPos startPos = mob.blockPosition();
        int radius = (int) (followRange + 16.0F);
        PathNavigationRegion region = new PathNavigationRegion(
                level,
                startPos.offset(-radius, -radius, -radius),
                startPos.offset(radius, radius, radius)
        );

        int maxVisitedNodes = Math.max(1, ((PathFinderAccessor) navAcc.getPathFinder()).getMaxVisitedNodes());
        Pathfinder activeAlgorithm = VillagerPathBenchMod.getActivePathfinder();

        long heapBefore = captureUsedHeapBytes();
        long threadAllocBefore = captureThreadAllocatedBytes();
        long startNanos = System.nanoTime();

        Pathfinder.SearchResult result = activeAlgorithm.findPath(
                region,
                mob,
                targets,
                followRange,
                reachRange,
                maxVisitedNodes,
                nav.getNodeEvaluator()
        );

        long elapsedNanos = System.nanoTime() - startNanos;
        long heapAfter = captureUsedHeapBytes();
        long threadAllocAfter = captureThreadAllocatedBytes();

        recordEvent(
                activeAlgorithm.getName(),
                mob,
                startPos,
                targets,
                result,
                elapsedNanos,
                heapBefore,
                heapAfter,
                threadAllocBefore,
                threadAllocAfter
        );

        return result.path();
    }

    /**
     * Records a single pathfinding execution event and queues a CSV row.
     */
    public static void recordEvent(
            String algorithmName,
            Mob mob,
            BlockPos startPos,
            Set<BlockPos> requestedTargets,
            Pathfinder.SearchResult result,
            long nanos,
            long heapBeforeBytes,
            long heapAfterBytes,
            long threadAllocBefore,
            long threadAllocAfter
    ) {
        if (!RUNNING.get()) {
            return;
        }

        String mobType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        Path path = result.path();

        BlockPos targetPos = result.targetPos();
        if (targetPos == null && path != null && path.getTarget() != null) {
            targetPos = path.getTarget();
        }
        if (targetPos == null && requestedTargets != null && !requestedTargets.isEmpty()) {
            targetPos = requestedTargets.iterator().next();
        }
        if (targetPos == null) {
            targetPos = startPos;
        }

        int pathLength = (path != null) ? path.getNodeCount() : 0;
        boolean reached = result.reached();
        boolean partial = result.partial();
        boolean capHit = result.capHit();
        int expanded = result.expanded();
        float cost = result.cost();

        long heapDelta = Math.max(0L, heapAfterBytes - heapBeforeBytes);
        long threadAllocated = (threadAllocBefore >= 0 && threadAllocAfter >= threadAllocBefore)
                ? (threadAllocAfter - threadAllocBefore)
                : heapDelta;
        double cpuLoad = (OS_MX_BEAN != null) ? Math.max(0.0, OS_MX_BEAN.getProcessCpuLoad() * 100.0) : -1.0;

        String csvRow = String.format(Locale.ROOT,
                "%s,%s,%s,%d,%d,%d,%d,%d,%d,%b,%b,%b,%d,%.4f,%d,%d,%d,%d,%.2f,%d",
                Instant.now().toString(),
                escapeCsv(algorithmName),
                escapeCsv(mobType),
                startPos.getX(), startPos.getY(), startPos.getZ(),
                targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                reached,
                partial,
                capHit,
                expanded,
                cost,
                nanos,
                heapAfterBytes,
                heapDelta,
                threadAllocated,
                cpuLoad,
                pathLength
        );

        TOTAL_LOGGED.incrementAndGet();
        LOG_QUEUE.offer(csvRow);
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void processQueue() {
        try (BufferedWriter writer = Files.newBufferedWriter(
                csvFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (RUNNING.get() || !LOG_QUEUE.isEmpty()) {
                String line = LOG_QUEUE.poll(200, TimeUnit.MILLISECONDS);
                if (line != null) {
                    writer.write(line);
                    writer.newLine();
                    // Flush immediately on every row so the user sees live updates in the CSV file
                    writer.flush();
                }
            }
            writer.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            VillagerPathBenchMod.LOGGER.error("Error writing pathfinding benchmark CSV", e);
        }
    }

    public static synchronized void shutdown() {
        if (!RUNNING.compareAndSet(true, false)) {
            return;
        }
        if (writerThread != null) {
            try {
                writerThread.join(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
