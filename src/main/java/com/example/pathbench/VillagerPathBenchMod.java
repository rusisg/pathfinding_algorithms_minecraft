package com.example.pathbench;

import com.example.pathbench.algorithm.AStarPathfinder;
import com.example.pathbench.algorithm.BfsPathfinder;
import com.example.pathbench.algorithm.CustomVoxelPathfinder;
import com.example.pathbench.algorithm.DijkstraPathfinder;
import com.example.pathbench.algorithm.GreedyBfsPathfinder;
import com.example.pathbench.algorithm.Pathfinder;
import com.example.pathbench.algorithm.VanillaPathfinder;
import com.example.pathbench.command.PathfindCommand;
import com.example.pathbench.profiler.PathfindingProfiler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Main Fabric Mod Initializer for Minecraft 26.3.
 * Registers all 6 pathfinding algorithms, initializes the CSV profiler,
 * and binds the /pathfind mode <algorithm_name> command.
 */
public class VillagerPathBenchMod implements ModInitializer {
    public static final String MOD_ID = "villager-pathbench";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<String, Pathfinder> REGISTRY = new LinkedHashMap<>();
    private static volatile Pathfinder activePathfinder;

    @Override
    public void onInitialize() {
        // Register all 6 required pathfinding algorithms
        registerAlgorithm(new VanillaPathfinder());
        registerAlgorithm(new AStarPathfinder());
        registerAlgorithm(new BfsPathfinder());
        registerAlgorithm(new DijkstraPathfinder());
        registerAlgorithm(new GreedyBfsPathfinder());
        registerAlgorithm(new CustomVoxelPathfinder(CustomVoxelPathfinder.VoxelHeuristic.OCTILE_3D));

        // Default to Vanilla baseline on startup
        activePathfinder = REGISTRY.get("vanilla");

        // Initialize the CSV profiler in the Fabric config directory
        PathfindingProfiler.init();

        // Register the /pathfind mode <algorithm_name> command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PathfindCommand.register(dispatcher);
        });

        // Ensure buffered CSV metrics are flushed cleanly on server stop
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PathfindingProfiler.shutdown();
        });

        LOGGER.info("Initialized Villager Pathfinding Benchmark for Minecraft 26.3. Active mode: {}",
                activePathfinder.getName());
    }

    public static void registerAlgorithm(Pathfinder pathfinder) {
        REGISTRY.put(pathfinder.getName().toLowerCase(Locale.ROOT), pathfinder);
    }

    public static Pathfinder getActivePathfinder() {
        return activePathfinder;
    }

    public static boolean setActivePathfinder(String name) {
        Pathfinder found = REGISTRY.get(name.toLowerCase(Locale.ROOT));
        if (found != null) {
            activePathfinder = found;
            LOGGER.info("Switched active Villager POI pathfinding algorithm to: {}", found.getName());
            return true;
        }
        return false;
    }

    public static Collection<String> getAvailableAlgorithmNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
