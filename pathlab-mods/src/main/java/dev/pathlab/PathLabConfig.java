package dev.pathlab;

import dev.pathlab.core.SearchAlgorithm;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Settings. The algorithm is baked into each jar (see pathlab.gradle) and can be overridden for testing:
 *   -Dpathlab.algorithm=BFS|DIJKSTRA|GREEDY_BFS|ASTAR
 *   -Dpathlab.weight=1.0        (ASTAR only; 1.0 = standard A*, 1.5 = what the unmodified game uses)
 *   -Dpathlab.enabled=false     (turn the replacement off: vanilla behaviour, for the reference run)
 *   -Dpathlab.mobs=zombie,iron_golem,villager
 *   -Dpathlab.run=<label>       (used in the CSV file name, e.g. zombie_village1)
 */
public final class PathLabConfig {
    private PathLabConfig() {}

    public record Settings(SearchAlgorithm algorithm, double weight, boolean enabled, Set<String> mobs) {}

    public static final String RUN = System.getProperty("pathlab.run", "run1");
    public static final Settings ACTIVE = load();

    private static Settings load() {
        String name = System.getProperty("pathlab.algorithm");
        if (name == null) {
            try (InputStream in = PathLabConfig.class.getResourceAsStream("/pathlab-default.properties")) {
                if (in != null) {
                    Properties p = new Properties();
                    p.load(in);
                    name = p.getProperty("algorithm");
                }
            } catch (Exception ignored) { }
        }
        if (name == null || name.isBlank() || name.contains("@")) name = "ASTAR";
        SearchAlgorithm algo = SearchAlgorithm.valueOf(name.trim().toUpperCase(Locale.ROOT));
        double weight = Double.parseDouble(System.getProperty("pathlab.weight", "1.0"));
        boolean enabled = Boolean.parseBoolean(System.getProperty("pathlab.enabled", "true"));
        Set<String> mobs = Set.of(System.getProperty("pathlab.mobs", "zombie,iron_golem,villager")
                .toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
        return new Settings(algo, weight, enabled, mobs);
    }

    private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Which of the three supported mobs this is, or null if the mod must leave it alone. */
    public static String label(Settings s, Mob mob) {
        if (mob == null) return null;
        // Compile-safe: avoids EntityType constants (their names changed in newer versions).
        // EntityType.toString() is the description id, e.g. "entity.minecraft.zombie".
        String id = String.valueOf(mob.getType());
        if (SEEN.size() < 8 && SEEN.add(id)) System.out.println("[PathLab] mob type seen: " + id);
        String label = id.endsWith(".zombie") ? "zombie"
                : id.endsWith(".iron_golem") ? "iron_golem"
                : id.endsWith(".villager") ? "villager" : null;
        return label != null && s.mobs().contains(label) ? label : null;
    }
}
