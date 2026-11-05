package dev.smartpath;

import dev.smartpath.config.SmartPathConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint for SmartPath on Minecraft 26.3 (Fabric).
 * Upgrades ground mob & villager navigation with Lazy Theta* any-angle pathfinding,
 * octile heuristics, indoor POI/workstation target resolution, and dynamic stuck recovery.
 */
public class SmartPathMod implements ModInitializer {
    public static final String MOD_ID = "smartpath";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SmartPathConfig config = SmartPathConfig.get();
        LOGGER.info(
            "[SmartPath] Initialized Lazy Theta* & Workstation Pathfinding for Minecraft 26.3 (LoS Smoothing={}, Indoor POI Fix={}, Stuck Penalty={})",
            config.enableThetaStarSmoothing,
            config.enableWorkstationTargetFix,
            config.enableStuckPenaltyMemory
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("smartpath")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("status").executes(ctx -> {
                        SmartPathConfig cfg = SmartPathConfig.get();
                        ctx.getSource().sendSuccess(
                            () -> Component.literal(String.format(
                                "[SmartPath] Enabled=%s | Theta* LoS=%s | Workstation Fix=%s | Heuristic Weight=%.2f | Searches=%d (Avg Nodes=%d)",
                                cfg.enabled,
                                cfg.enableThetaStarSmoothing,
                                cfg.enableWorkstationTargetFix,
                                cfg.heuristicWeight,
                                cfg.totalSearches.sum(),
                                cfg.getAverageVisitedNodes()
                            )),
                            false
                        );
                        return 1;
                    }))
                    .then(Commands.literal("toggle").executes(ctx -> {
                        SmartPathConfig cfg = SmartPathConfig.get();
                        cfg.enabled = !cfg.enabled;
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("[SmartPath] Custom pathfinder enabled: " + cfg.enabled),
                            true
                        );
                        return 1;
                    }))
            );
        });
    }
}
