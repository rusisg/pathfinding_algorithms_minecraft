# SmartPath: Lazy Theta* & Workstation Pathfinding for Minecraft 26.3 (Fabric)

**SmartPath** is a server-compatible Fabric 26.3 mod that replaces vanilla Minecraft's ground mob pathfinder (`PathFinder` + `WalkNodeEvaluator` + `GroundPathNavigation`) with an **Any-Angle Lazy Theta* + Adaptive Octile A*** hybrid algorithm designed to fix villager job-site/table navigation and improve general mob AI routing.

## Key Improvements Over Vanilla Pathfinding

| Feature | Vanilla Minecraft (`PathFinder`) | SmartPath (`ThetaStarPathFinder`) |
| :--- | :--- | :--- |
| **Core Search Algorithm** | Grid A* with fixed `1.5f` Euclidean fudge multiplier | **Lazy Theta*** (Any-Angle) + **Adaptive Octile A*** |
| **Heuristic Function** | Inadmissible `1.5f * Euclidean` (traps mobs in U-shaped pockets) | **3D Octile Heuristic** + Cross-Product Tie-Breaking + Dynamic Relaxation (`1.08 -> 1.00`) |
| **Step-Cost Metric** | `distanceManhattan` (charges `2.0` for a flat diagonal step) | True octile step cost (`sqrt(2) ≈ 1.414` for diagonals, asymmetric climb/drop costs) |
| **Path Smoothing** | None (strict 45°/90° grid zig-zagging) | **Supercover Bresenham 3D Line-of-Sight** parent relaxation + collinear string-pulling |
| **Villager Table / POI Target** | `findSurfacePosition` scans UP through solid ceilings onto roofs | **`WorkstationApproachResolver`** keeps indoor targets on the same floor and scores all 4 table sides |
| **Stuck Recovery** | Waits ~100+ ticks and recomputes the exact same blocked path | **`StuckPenaltyTracker`** penalizes stalled coordinates after 35 ticks and routes around obstacles |

## Project Structure

- `src/main/java/dev/smartpath/algorithm/ThetaStarPathFinder.java` — Drop-in `PathFinder` replacement implementing Lazy Theta* parent relaxation, 3D Octile heuristic with cross-product tie-breaking, supercover Bresenham line-of-sight checks, and string-pulling.
- `src/main/java/dev/smartpath/algorithm/SmartWalkNodeEvaluator.java` — Enhanced `WalkNodeEvaluator` that integrates per-mob stuck penalties and refines villager indoor traversal costs around trapdoors, carpets, fence gates, and wooden doors.
- `src/main/java/dev/smartpath/algorithm/WorkstationApproachResolver.java` — Prevents `GroundPathNavigation` from snapping solid indoor workstation targets (lecterns, smithing tables, brewing stands, barrels) onto building roofs and ranks adjacent standable tiles.
- `src/main/java/dev/smartpath/algorithm/StuckPenaltyTracker.java` — Time-decaying spatial memory (300-tick TTL) that adds temporary malus to coordinates where a mob recently stalled.
- `src/main/java/dev/smartpath/mixin/GroundPathNavigationMixin.java` — Injects `ThetaStarPathFinder`, `SmartWalkNodeEvaluator`, and indoor workstation target resolution into `GroundPathNavigation`.
- `src/main/java/dev/smartpath/mixin/PathNavigationStuckMixin.java` — Hooks `PathNavigation#doStuckDetection` for fast 35-tick anti-stuck recovery.

## Building & Running (Minecraft 26.3 / Java 25)

Minecraft 26.3 is unobfuscated and uses Mojang's official names built into the game with Fabric Loom `1.18.2` (`net.fabricmc.fabric-loom`) and Java 25.

1. Extract `smartpath-fabric-26.3.zip` and open a terminal in the project root.
2. Generate the Gradle wrapper (if not already present in your IDE) or open directly in IntelliJ IDEA 2025.3+ / Linen:
   ```bash
   gradle wrapper --gradle-version 9.7.1
   ./gradlew build
   ```
3. Launch the test client or server:
   ```bash
   ./gradlew runClient
   ```
4. Use `/smartpath status` or `/smartpath toggle` in-game (requires OP/gamemaster permission) to view live search telemetry or toggle between SmartPath and vanilla A* on the fly.
