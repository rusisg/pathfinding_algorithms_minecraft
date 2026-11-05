package dev.smartpath.algorithm;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

/**
 * Tracks per-mob temporary pathfinding penalties for block positions where the mob
 * recently got stuck (e.g., carpet edges, trapdoors, crowded villager workstations, or fence corners).
 * Penalties automatically expire after EXPIRY_TICKS so the mob does not permanently avoid valid areas.
 */
public final class StuckPenaltyTracker {
    private static final long EXPIRY_TICKS = 300L; // 15 seconds at 20 TPS
    private static final float STUCK_MALUS_INCREMENT = 6.0F;
    private static final float MAX_STUCK_MALUS = 24.0F;

    private static final Map<Mob, MobStuckMemory> MEMORY_BY_MOB =
        Collections.synchronizedMap(new WeakHashMap<>());

    private StuckPenaltyTracker() {}

    public static void recordStuckPosition(Mob mob, BlockPos pos, long gameTime) {
        MobStuckMemory memory = MEMORY_BY_MOB.computeIfAbsent(mob, k -> new MobStuckMemory());
        synchronized (memory) {
            memory.record(pos.asLong(), gameTime);
        }
    }

    public static float getExtraMalus(Mob mob, int x, int y, int z, long gameTime) {
        MobStuckMemory memory = MEMORY_BY_MOB.get(mob);
        if (memory == null) {
            return 0.0F;
        }
        synchronized (memory) {
            return memory.getMalus(BlockPos.asLong(x, y, z), gameTime);
        }
    }

    private static final class MobStuckMemory {
        private final Long2FloatOpenHashMap malusByPos = new Long2FloatOpenHashMap();
        private final Long2LongOpenHashMap timestampByPos = new Long2LongOpenHashMap();

        void record(long packedPos, long gameTime) {
            pruneExpired(gameTime);
            float current = malusByPos.getOrDefault(packedPos, 0.0F);
            malusByPos.put(packedPos, Math.min(MAX_STUCK_MALUS, current + STUCK_MALUS_INCREMENT));
            timestampByPos.put(packedPos, gameTime);
        }

        float getMalus(long packedPos, long gameTime) {
            if (!timestampByPos.containsKey(packedPos)) {
                return 0.0F;
            }
            long recordedAt = timestampByPos.get(packedPos);
            if (gameTime - recordedAt > EXPIRY_TICKS) {
                timestampByPos.remove(packedPos);
                malusByPos.remove(packedPos);
                return 0.0F;
            }
            return malusByPos.get(packedPos);
        }

        private void pruneExpired(long gameTime) {
            if (timestampByPos.size() < 16) {
                return;
            }
            var iterator = timestampByPos.long2LongEntrySet().fastIterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (gameTime - entry.getLongValue() > EXPIRY_TICKS) {
                    malusByPos.remove(entry.getLongKey());
                    iterator.remove();
                }
            }
        }
    }
}
