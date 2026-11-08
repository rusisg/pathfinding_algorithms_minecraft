package com.example.pathbench.command;

import com.example.pathbench.VillagerPathBenchMod;
import com.example.pathbench.profiler.PathfindingProfiler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * Registers the real-time algorithm hot-swap and benchmark trigger commands:
 * - /pathfind mode <algorithm_name>
 * - /pathfind test [<target_pos>]
 */
public final class PathfindCommand {

    private static final SuggestionProvider<CommandSourceStack> ALGORITHM_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    VillagerPathBenchMod.getAvailableAlgorithmNames(), builder);

    private PathfindCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("pathfind")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("mode")
                                .executes(PathfindCommand::queryCurrentMode)
                                .then(Commands.argument("algorithm_name", StringArgumentType.word())
                                        .suggests(ALGORITHM_SUGGESTIONS)
                                        .executes(PathfindCommand::setMode)))
                        .then(Commands.literal("test")
                                .executes(PathfindCommand::testNearestVillagerToJobSite)
                                .then(Commands.argument("target", BlockPosArgument.blockPos())
                                        .executes(ctx -> testNearestVillagerToPos(
                                                ctx, BlockPosArgument.getLoadedBlockPos(ctx, "target")))))
        );
    }

    private static int queryCurrentMode(CommandContext<CommandSourceStack> context) {
        String active = VillagerPathBenchMod.getActivePathfinder().getName();
        context.getSource().sendSuccess(
                () -> Component.literal("[PathBench] Active Villager POI algorithm: " + active
                        + " | CSV rows logged: " + PathfindingProfiler.getTotalLoggedCount()
                        + " (Available: " + String.join(", ", VillagerPathBenchMod.getAvailableAlgorithmNames()) + ")"),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandContext<CommandSourceStack> context) {
        String requested = StringArgumentType.getString(context, "algorithm_name");
        if (VillagerPathBenchMod.setActivePathfinder(requested)) {
            String active = VillagerPathBenchMod.getActivePathfinder().getName();
            context.getSource().sendSuccess(
                    () -> Component.literal("[PathBench] Switched active Villager POI pathfinding algorithm to: " + active),
                    true
            );
            return Command.SINGLE_SUCCESS;
        } else {
            context.getSource().sendFailure(
                    Component.literal("[PathBench] Unknown algorithm '" + requested + "'. Valid options: "
                            + String.join(", ", VillagerPathBenchMod.getAvailableAlgorithmNames()))
            );
            return 0;
        }
    }

    private static int testNearestVillagerToJobSite(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());

        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class, new AABB(origin).inflate(64.0), Villager::isAlive);
        if (villagers.isEmpty()) {
            source.sendFailure(Component.literal("[PathBench] No Villager found within 64 blocks."));
            return 0;
        }

        int triggered = 0;
        for (Villager villager : villagers) {
            BlockPos poiPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
                    .map( net.minecraft.core.GlobalPos::pos )
                    .orElse(origin);

            Path path = villager.getNavigation().createPath(Set.of(poiPos), 1);
            if (path != null) {
                villager.getNavigation().moveTo(path, 0.5D);
            }
            triggered++;
        }

        int finalTriggered = triggered;
        source.sendSuccess(
                () -> Component.literal("[PathBench] Triggered POI pathfinding on " + finalTriggered
                        + " Villager(s) using '" + VillagerPathBenchMod.getActivePathfinder().getName()
                        + "'. Total CSV rows: " + PathfindingProfiler.getTotalLoggedCount()),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int testNearestVillagerToPos(CommandContext<CommandSourceStack> context, BlockPos targetPos) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());

        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class, new AABB(origin).inflate(64.0), Villager::isAlive);
        if (villagers.isEmpty()) {
            source.sendFailure(Component.literal("[PathBench] No Villager found within 64 blocks."));
            return 0;
        }

        Villager nearest = villagers.getFirst();
        // Force benchmark run to targetPos and log to CSV
        Path path = PathfindingProfiler.benchmarkDirect(nearest, level, Set.of(targetPos), 1);
        if (path != null) {
            nearest.getNavigation().moveTo(path, 0.5D);
        }

        source.sendSuccess(
                () -> Component.literal("[PathBench] Benchmarked Villager path to ("
                        + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ()
                        + ") with '" + VillagerPathBenchMod.getActivePathfinder().getName()
                        + "'. Total CSV rows: " + PathfindingProfiler.getTotalLoggedCount()),
                true
        );
        return Command.SINGLE_SUCCESS;
    }
}
