package com.example.pathbench.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin Accessor and Invoker to access private members on PathNavigation.
 */
@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
    @Accessor("pathFinder")
    PathFinder getPathFinder();

    @Invoker("getMaxPathLength")
    float invokeGetMaxPathLength();
}
