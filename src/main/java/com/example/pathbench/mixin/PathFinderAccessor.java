package com.example.pathbench.mixin;

import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin Accessor to read the private maxVisitedNodes field from Minecraft's PathFinder.
 */
@Mixin(PathFinder.class)
public interface PathFinderAccessor {
    @Accessor("maxVisitedNodes")
    int getMaxVisitedNodes();
}
