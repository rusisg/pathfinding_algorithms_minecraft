package dev.pathlab;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;

/**
 * Runs once at startup and checks, by reflection, that the game classes have the members this mod relies on.
 * Writes pathlab/selfcheck.txt. It also dumps the real signatures of PathFinder, which is the first thing
 * to look at if a Mixin error appears after a game update.
 */
public final class SelfCheck {
    private SelfCheck() {}

    public static boolean run() {
        List<String> lines = new ArrayList<>();
        int[] bad = {0};
        check(lines, bad, "PathFinder.findPath(Node, Map, float, int, float)  [private search loop]",
                () -> PathFinder.class.getDeclaredMethod("findPath", Node.class, Map.class, float.class, int.class, float.class));
        check(lines, bad, "PathFinder.findPath(PathNavigationRegion, Mob, Set, float, int, float)",
                () -> PathFinder.class.getDeclaredMethod("findPath", PathNavigationRegion.class, Mob.class, Set.class, float.class, int.class, float.class));
        check(lines, bad, "PathFinder.nodeEvaluator (field)", () -> PathFinder.class.getDeclaredField("nodeEvaluator"));
        check(lines, bad, "PathFinder.maxVisitedNodes (field)", () -> PathFinder.class.getDeclaredField("maxVisitedNodes"));
        check(lines, bad, "NodeEvaluator.getNeighbors(Node[], Node)", () -> NodeEvaluator.class.getMethod("getNeighbors", Node[].class, Node.class));
        check(lines, bad, "Node.distanceTo(Node)", () -> Node.class.getMethod("distanceTo", Node.class));
        check(lines, bad, "Node.distanceManhattan(Node)", () -> Node.class.getMethod("distanceManhattan", Node.class));
        for (String f : new String[]{"cameFrom", "g", "closed", "walkedDistance", "costMalus", "x", "y", "z"}) {
            check(lines, bad, "Node." + f + " (field)", () -> Node.class.getField(f));
        }
        check(lines, bad, "Target.updateBest(float, Node)", () -> Target.class.getMethod("updateBest", float.class, Node.class));
        check(lines, bad, "Target.getBestNode()", () -> Target.class.getMethod("getBestNode"));
        check(lines, bad, "Target.setReached()", () -> Target.class.getMethod("setReached"));
        check(lines, bad, "Path(List, BlockPos, boolean)", () -> net.minecraft.world.level.pathfinder.Path.class.getConstructor(List.class, BlockPos.class, boolean.class));
        check(lines, bad, "Path.getNodeCount()", () -> net.minecraft.world.level.pathfinder.Path.class.getMethod("getNodeCount"));
        check(lines, bad, "Path.getDistToTarget()", () -> net.minecraft.world.level.pathfinder.Path.class.getMethod("getDistToTarget"));
        lines.add("");
        lines.add("Declared members of PathFinder in this game version:");
        for (Method m : PathFinder.class.getDeclaredMethods()) lines.add("  method " + m);
        Arrays.stream(PathFinder.class.getDeclaredFields()).forEach(f -> lines.add("  field  " + f));

        String head = bad[0] == 0 ? "SELF-CHECK PASSED" : "SELF-CHECK FOUND " + bad[0] + " PROBLEM(S)";
        lines.add(0, head);
        try {
            Path dir = Path.of("pathlab");
            Files.createDirectories(dir);
            Files.write(dir.resolve("selfcheck.txt"), lines);
        } catch (Exception ignored) { }
        PathLog.say(head + " (details: pathlab/selfcheck.txt)");
        if (bad[0] > 0) lines.stream().filter(l -> l.startsWith("MISSING")).forEach(PathLog::say);
        return bad[0] == 0;
    }

    private interface Probe { Object get() throws Exception; }

    private static void check(List<String> lines, int[] bad, String what, Probe p) {
        try { p.get(); lines.add("OK       " + what); }
        catch (Throwable t) { bad[0]++; lines.add("MISSING  " + what + "   (" + t.getClass().getSimpleName() + ")"); }
    }
}
