package dev.pathlab;

import dev.pathlab.core.SearchAlgorithm;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.*;

/** A flat MODEL of the two in-game test arenas (README section 6). Not the real game: it only shows
 *  what kind of difference to look for. Grid 61 x 41, origin at the player's feet. */
public class ArenaModel {
    static final int OX = 30, OZ = 30;
    static void fill(boolean[][] w, int x1, int z1, int x2, int z2) { for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) w[x + OX][z + OZ] = true; }
    static void water(float[][] m, int x1, int z1, int x2, int z2) { for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) m[x + OX][z + OZ] = 8f; }

    static void run(String name, boolean[][] wall, float[][] malus, int zx, int zz, int vx, int vz, int cap) {
        System.out.println("\n" + name + "   (node cap " + cap + ")");
        System.out.printf("  %-11s %8s %7s %9s %10s %8s %s%n", "algorithm", "reached", "capHit", "expanded", "pathNodes", "cost", "water cells on route");
        for (SearchAlgorithm alg : SearchAlgorithm.values()) {
            GridEvaluator ev = new GridEvaluator(wall, malus, zx + OX, zz + OZ);
            Target tg = ev.getTarget(vx + OX, 0, vz + OZ);
            dev.pathlab.mc.VanillaNodeProblem prob = new dev.pathlab.mc.VanillaNodeProblem(ev, ev.getStart(), List.of(tg), 0, 35f);
            dev.pathlab.core.SearchResult<Node> r = dev.pathlab.core.PathSearch.run(prob, alg, new dev.pathlab.core.SearchLimits(cap, 1.0, true));
            int wet = 0; for (Node n : r.path()) if (malus[n.x][n.z] >= 8f) wet++;
            System.out.printf("  %-11s %8s %7s %9d %10d %8.1f %d%n", alg, r.reached(), r.capHit(), r.expanded(), r.path().size(), r.cost(), wet);
        }
    }

    public static void main(String[] a) {
        // Arena 1: U-trap. Zombie inside a U that opens towards the south; villager north of the bar.
        boolean[][] w1 = new boolean[61][61]; float[][] m1 = new float[61][61];
        fill(w1, -6, -7, 6, -7); fill(w1, -6, -6, -6, -2); fill(w1, 6, -6, 6, -2);
        run("Arena 1: U-trap, zombie inside the U, villager behind the wall", w1, m1, 0, -4, 0, -12, 560);

        // Arena 2: river with a land gap. Water costs 8 per cell (the game's penalty for water, per the 2026 ITB paper).
        boolean[][] w2 = new boolean[61][61]; float[][] m2 = new float[61][61];
        water(m2, -20, -6, 20, -4);
        for (int x = 8; x <= 9; x++) for (int z = -6; z <= -4; z++) m2[x + OX][z + OZ] = 0f;   // land bridge
        run("Arena 2: river 3 wide, land bridge at x = 8..9", w2, m2, 0, 0, 0, -12, 560);
        run("Arena 2 with the cap of an iron golem (follow range 20 x 16)", w2, m2, 0, 0, 0, -12, 320);
        // Arena 3: smaller U so that more algorithms fit inside the cap
        boolean[][] w3 = new boolean[61][61]; float[][] m3 = new float[61][61];
        fill(w3, -4, -5, 4, -5); fill(w3, -4, -4, -4, -2); fill(w3, 4, -4, 4, -2);
        run("Arena 3: small U-trap (bar 9 wide, arms 3 long), villager 7 behind the bar", w3, m3, 0, -3, 0, -12, 560);
    }
}
