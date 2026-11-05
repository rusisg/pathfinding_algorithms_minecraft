#!/bin/sh
# Builds the four jars into ./dist  (run from the project folder, after the one-time setup in README.md)
set -e
mkdir -p dist
for a in BFS DIJKSTRA GREEDY_BFS ASTAR; do
  echo "=== building $a ==="
  ./gradlew clean build -Palgo=$a
  l=$(echo "$a" | tr 'A-Z' 'a-z')
  for f in build/libs/pathlab-$l-*.jar; do
    case "$f" in *-sources.jar|*-dev.jar) ;; *) cp "$f" dist/ ;; esac
  done
done
echo; echo "Done. Jars:"; ls -1 dist
