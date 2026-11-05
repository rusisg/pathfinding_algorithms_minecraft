# PathLab: clean start-to-finish guide (Void Linux, sway, TLauncher)
Verified by you: Java 25 + Fabric template + this source builds, loads in 26.3, SELF-CHECK PASSED, zombie handled by ASTAR.

## A. One-time setup
1. `sudo xbps-install -S openjdk25 git`
2. Each new terminal (or put in ~/.profile):
   `export JAVA_HOME=/usr/lib/jvm/openjdk25 PATH=/usr/lib/jvm/openjdk25/bin:$PATH` ; `java -version` must say 25
3. https://fabricmc.net/develop/template/ -> Minecraft 26.3, mod name PathLab, package dev.pathlab, no Kotlin / datagen / split sources -> Download.
4. `mkdir -p ~/pathlab-build ~/pathlab-src && cd ~/pathlab-build && unzip ~/Downloads/<template>.zip` (cd into the folder that has build.gradle)
5. `unzip ~/Downloads/pathlab-mods.zip -d ~/pathlab-src`
6. In ~/pathlab-build: `rm -rf src && cp -r ~/pathlab-src/pathlab-mods/{src,tools,pathlab.gradle,build-all.sh} .`
7. Add as the LAST line of build.gradle: `apply from: 'pathlab.gradle'`
8. `chmod +x gradlew && ./gradlew --stop && sh build-all.sh` -> `ls dist` shows four jars (first build is slow).

## B. Install (TLauncher)
Fabric 26.3 version in TLauncher (Accept mods ticked; Java path = /usr/lib/jvm/openjdk25/bin/java if needed).
Exactly ONE jar in ~/.minecraft/mods/ at a time (`rm ~/.minecraft/mods/pathlab-*.jar` before swapping). Empty mods/ = vanilla baseline.

## C. Check it works
`grep PathLab ~/.minecraft/logs/latest.log` -> "loaded. algorithm = X", "SELF-CHECK PASSED", and after a zombie exists "first path request handled for zombie by X". CSVs: ~/.minecraft/pathlab/.

## D. Experiment (repeat for BFS, DIJKSTRA, GREEDY_BFS, ASTAR, and vanilla)
Same Superflat creative world every time. Use /gamerule (Tab) to turn off mob spawning. /time set midnight.
Arena A (river + 3-wide bridge): see README section 4. Zombie one side, NoAI villager bait other side. >=20 trials per algorithm
(kill zombie, summon again). Copy latest.log and the CSV after each algorithm.
Then: `python3 -I tools/summarize.py ~/.minecraft/pathlab`
