#!/bin/sh
# Compiles the mod sources against stand-in game classes and runs the tests. Needs only a JDK (21+).
set -e
here=$(cd "$(dirname "$0")" && pwd)
out=$(mktemp -d)
javac -d "$out" $(find "$here/stubs" "$here/../src/main/java" "$here/tests" -name '*.java')
work=$(mktemp -d); cd "$work"
java -cp "$out" dev.pathlab.StandInTests
echo; echo "CSV written by the hook during the tests:"; ls pathlab/
cp -r pathlab "$here/last-test-csv" 2>/dev/null || true
