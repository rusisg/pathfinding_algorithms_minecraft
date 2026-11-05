#!/usr/bin/env python3
"""Summarise the CSV files that the mods write (pathlab/*.csv). Standard library only.
Usage:  python3 tools/summarize.py [folder-with-csv-files]      (default: ./pathlab)"""
import csv, glob, os, statistics, sys
from collections import defaultdict

folder = sys.argv[1] if len(sys.argv) > 1 else "pathlab"
rows = defaultdict(list)
for path in sorted(glob.glob(os.path.join(folder, "*.csv"))):
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            rows[(r["algorithm"], r["mob"])].append(r)

if not rows:
    sys.exit(f"No CSV rows found in {folder!r}. Spawn a zombie/golem/villager first (see README, section 6).")

def mean(xs): return sum(xs) / len(xs) if xs else float("nan")
print(f"{'algorithm':11} {'mob':11} {'n':>6} {'reached%':>9} {'partial%':>9} {'cap%':>6} "
      f"{'expanded':>9} {'median':>7} {'cost*':>8} {'ms':>8}")
for (algo, mob), rs in sorted(rows.items()):
    n = len(rs)
    reached = [r for r in rs if r["reached"] == "true"]
    exp = [int(r["expanded"]) for r in rs]
    print(f"{algo:11} {mob:11} {n:6d} {100*len(reached)/n:9.1f} "
          f"{100*sum(r['partial']=='true' for r in rs)/n:9.1f} {100*sum(r['capHit']=='true' for r in rs)/n:6.1f} "
          f"{mean(exp):9.1f} {statistics.median(exp):7.0f} "
          f"{mean([float(r['cost']) for r in reached]):8.2f} {mean([int(r['nanos']) for r in rs])/1e6:8.3f}")
print("\n* cost = mean path cost over requests where the goal was reached. "
      "Compare algorithms only on identical scenarios.")
