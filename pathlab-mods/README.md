# PathLab: BFS / Dijkstra / A* / Greedy Best-First mods for Minecraft (Fabric)

Four jars built from ONE source tree. Each jar replaces only the search loop of the game's
`PathFinder` for **zombie, iron golem and villager**. Everything else (what the mob wants, how
nodes/neighbours are generated, block costs, node cap, path following) stays vanilla.

| Jar | Priority of the open list | Optimal? |
|---|---|---|
| `pathlab-bfs`        | FIFO (fewest steps)            | only for uniform costs |
| `pathlab-dijkstra`   | g (cost so far)                | yes |
| `pathlab-greedy_bfs` | h (estimate to goal)           | no |
| `pathlab-astar`      | g + w·h, **w = 1.0** (standard A*) | yes (reach range 0) |

Vanilla Minecraft is A* with w = 1.5. To imitate it: `-Dpathlab.weight=1.5` on the ASTAR jar.

## 0. HONEST STATUS (read this first)
* The algorithm core (`core/`) and the glue are tested with stand-in classes: A* (w=1.5) gives
  the same path as a copy of the documented vanilla loop in 600/600 random worlds; A* (w=1) and
  Dijkstra have equal cost in all 497 solvable worlds; fail-safe fallback works.
* I could NOT compile against the real Minecraft 26.3 here (sandbox cannot reach Fabric/Maven/Mojang).
  The names of the private `PathFinder` members, `Node` fields, `Target.updateBest/setReached`
  and the `Path` constructor come from my knowledge of 1.21.x mappings. If one is wrong you get a
  compile error or a startup message that tells you exactly which one (section 7). Expect to spend
  up to an hour fixing 1-3 names. After that it is stable.
* If anything throws at runtime, the mod switches itself off and the game uses vanilla pathfinding
  (console says `DISABLED`). So "mod does nothing" = look at the log, not at the mob.
* Differences from vanilla you must mention in your report: ties are broken by insertion order
  (vanilla uses a binary heap) so equal-cost paths can differ; standard A* uses w=1.0.

## 1. One-time setup (Void Linux, sway)
```sh
sudo xbps-install -S openjdk25 git      # if the package is not found: xbps-query -Rs openjdk
java -version                           # must say 25
```
1. Generate the template at https://fabricmc.net/develop/template/ : Minecraft **26.3**
   (if not offered, pick the newest listed and tell me), mod id anything, package `dev.pathlab`,
   **no** Kotlin, **no** data generation, "Split client/common" off. Download and unzip.
2. In the unzipped template: delete `src/` (all of it). Copy from this project into the template:
   `src/`, `pathlab.gradle`, `build-all.sh`, `tools/`, `devtest/` (optional).
3. Open `build.gradle`, add as the **last line**: `apply from: 'pathlab.gradle'`
4. If the template lists `fabric-api` dependency, it can stay (not needed).
5. Check `gradle.properties` has `org.gradle.java.home` unset or pointing to JDK 25.
6. Build all four: `sh build-all.sh` -> `dist/pathlab-{bfs,dijkstra,greedy_bfs,astar}-1.0.0.jar`
   (one: `./gradlew clean build -Palgo=BFS`). Open the folder in IntelliJ as a Gradle project.

## 2. Prism Launcher
Install (`xbps-install -S PrismLauncher` or the flatpak). Create **5 instances**, all Minecraft 26.3,
Fabric loader (latest), Java 25:
`vanilla`, `bfs`, `dijkstra`, `greedy`, `astar`. Put the matching jar into each instance's
`mods/` folder (right-click instance -> Folder -> mods). Use the same world for all (copy the
world folder into every instance's `saves/`). Fabric API is not required.

## 3. Check that it loaded
Launch an instance, then open `<instance>/minecraft/logs/latest.log` (Prism: right-click ->
Folder -> `minecraft/logs`). Search `PathLab`:
```
[PathLab] ===== PathLab loaded: algorithm=DIJKSTRA weight=1.0 enabled=true mobs=zombie,iron_golem,villager ...
[PathLab] SELF-CHECK PASSED
```
* No banner -> jar not in mods or wrong loader.
* `SELF-CHECK FOUND n PROBLEM(S)` -> open `minecraft/pathlab/selfcheck.txt`, section 7.
* Game crashes at start with a Mixin error -> section 7.

When a mob first computes a path you see `[PathLab] zombie: first path request handled by DIJKSTRA ...`.
Every request is appended to `minecraft/pathlab/<ALGO>_<run>.csv`.

## 4. Test arenas (creative, Superflat, `/gamemode creative`, `/time set midnight`, `/gamerule doMobSpawning false`, `/difficulty normal`)
Mobs must be created the same way in every instance. Stand at one spot (note x y z, call it `~ ~ ~`).
Clear first: `/fill ~-10 ~ ~-30 ~10 ~6 ~10 air`.

**Arena A - land bridge over a river (separates all four)**
```
/fill ~-15 ~-3 ~-9 ~15 ~-1 ~-5 minecraft:water   # river, 5 blocks wide, across the way
/fill ~-1 ~-3 ~-9 ~1 ~-1 ~-5 minecraft:stone        # 3-wide land bridge in the middle
```
Zombies chase villagers, so use a villager with NoAI as bait on the far bank: `/summon minecraft:villager ~ ~ ~-14 {NoAI:1b,PersistenceRequired:1b}`
`/summon minecraft:zombie ~ ~ ~ {PersistenceRequired:1b,CanBreakDoors:0b}`

**Arena B - small U-trap (greedy gets stuck / BFS wastes nodes)**
```
/fill ~-4 ~ ~-8 ~4 ~2 ~-8 minecraft:stone
/fill ~-4 ~ ~-8 ~-4 ~2 ~-5 minecraft:stone
/fill ~4 ~ ~-8 ~4 ~2 ~-5 minecraft:stone
```
Zombie at `~ ~ ~-5` (inside the cup, facing the wall), bait villager at `~ ~ ~-12` behind the wall.

**Iron golem:** golems attack hostile mobs, so summon a NoAI zombie as the target:
`/summon minecraft:zombie ~ ~ ~-12 {NoAI:1b,PersistenceRequired:1b}` and the golem
`/summon minecraft:iron_golem ~ ~ ~` (needs night / zombie in range; golem follow range is smaller, so
its node cap is smaller - BFS/Dijkstra fail more often).

**Villager:** rows appear when villagers wander, flee from a zombie, or walk to beds in a village.
Use your village world for this; it is the "natural" test.

**Repeat identically** in each instance (same coordinates, same seed, 20+ trials per cell). Then:
```sh
python3 -I tools/summarize.py <instance>/minecraft/pathlab
```
Columns: n requests, reached% (complete path to the target), partial% (best-effort path), cap%
(search stopped by the node cap), expanded (nodes expanded = work), cost (mean path cost on reached),
ms (search time). Model prediction (flat model, NOT measured in game): in Arena A A* and Greedy
succeed, BFS succeeds with a wet/longer path, Dijkstra often hits the cap; in Arena B BFS and
Dijkstra fail, Greedy and A* succeed. If the game disagrees, that is a finding, not a bug, unless
the cap/reached columns are impossible.

**Sanity check that the mod is faithful:** run `astar` with
`-Dpathlab.weight=1.5` (Prism -> Settings -> Java arguments) and compare with the `vanilla`
instance in the same arena: behaviour should look the same (not byte-identical, tie-breaking).

## 5. Useful switches (JVM args, Prism -> Edit -> Settings -> Java)
`-Dpathlab.enabled=false` (turn off), `-Dpathlab.mobs=zombie,villager`, `-Dpathlab.weight=1.5`,
`-Dpathlab.run=trial2` (new CSV name).

## 6. Pitfalls
* The jars conflict on purpose (`breaks`): only ONE PathLab jar per instance.
* The zombie only paths when it sees a target within follow range (35 blocks); keep arenas smaller.
* Doors/other mobs can change results; use a clean flat world with `doMobSpawning false`.
* Node cap = follow range x 16 (zombie 560). It is part of the vanilla design and is kept; it is
  also why BFS/Dijkstra fail on long routes. Report it, do not hide it.

## 7. If it does not compile / start
* **Compile error "cannot find symbol X"**: the member name differs in 26.3. In IntelliJ, Ctrl+click
  the class (e.g. `PathFinder`, `Node`, `Target`), read the real names, fix them in
  `mc/VanillaNodeProblem.java`, `PathLabHook.java`, `mixin/PathFinderMixin.java`. Only these three
  files touch Minecraft.
* **Mixin error "could not find target method"**: open `minecraft/pathlab/selfcheck.txt`; it lists
  every declared member of `PathFinder` with its descriptor. Copy the private search method's
  descriptor into the `@Inject(method = ...)` in `PathFinderMixin`.
* **Mixin compatibility level**: if the template uses another Java level, change
  `compatibilityLevel` in `pathlab.mixins.json` to match.
* Send me the exact error text and I will fix it.
