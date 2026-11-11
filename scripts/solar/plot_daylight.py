#!/usr/bin/env python3
"""Draws docs/images/daylight-by-latitude.png: the daylight Seasons' real-place day length
gives every day of one year at four places, from the plugin's own sun code.

A development tool; it is not part of the plugin or its build. It compiles nothing itself:

    ./gradlew compileJava
    pip install matplotlib
    python3 scripts/solar/plot_daylight.py            # runs DaylightTable.java with the JDK on PATH
    python3 scripts/solar/plot_daylight.py --csv daylight.csv   # or reads its output

The CSV comes from scripts/solar/DaylightTable.java, which calls Daylight.on and
DayLength.clamp from build/classes/java/main. The line drawn is the share the plugin applies
(clamped to 1..23 hours of 24); where that differs from the real sun, the real value is dotted.
"""
import argparse
import csv
import io
import os
import subprocess
import sys
from collections import defaultdict
from datetime import date

import matplotlib

matplotlib.use("Agg")
import matplotlib.dates as mdates  # noqa: E402
import matplotlib.pyplot as plt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# The validated categorical order (light surface); identity also carried by direct labels.
COLOURS = {"Singapore": "#eda100", "Sydney": "#1baf7a", "London": "#2a78d6", "Tromso": "#eb6834"}
LABELS = {
    "Singapore": "Singapore, 1.4° N",
    "Sydney": "Sydney, 33.9° S",
    "London": "London, 51.5° N (the default)",
    "Tromso": "Tromsø, 69.7° N",
}
SURFACE = "#fcfcfb"
INK = "#1f1f1e"
MUTED = "#6b6a63"
GRID = "#e4e3de"


def read_rows(args):
    if args.csv:
        with open(args.csv, newline="") as f:
            return list(csv.DictReader(f))
    java = os.path.join(os.environ["JAVA_HOME"], "bin", "java") if os.environ.get("JAVA_HOME") else "java"
    out = subprocess.run(
        [java, "-cp", os.path.join(ROOT, "build", "classes", "java", "main"),
         os.path.join(ROOT, "scripts", "solar", "DaylightTable.java"), str(args.year)],
        check=True, capture_output=True, text=True).stdout
    return list(csv.DictReader(io.StringIO(out)))


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--year", type=int, default=2026)
    p.add_argument("--csv", help="read DaylightTable.java's output instead of running it")
    p.add_argument("--out", default=os.path.join(ROOT, "docs", "images", "daylight-by-latitude.png"))
    args = p.parse_args()

    series = defaultdict(lambda: ([], [], []))
    for r in read_rows(args):
        d, real, applied = series[r["place"]]
        d.append(date.fromisoformat(r["date"]))
        real.append(24 * float(r["daylight_fraction"]))
        applied.append(24 * float(r["applied_fraction"]))

    plt.rcParams.update({"font.size": 11, "text.color": INK, "axes.labelcolor": INK,
                         "xtick.color": MUTED, "ytick.color": MUTED})
    fig, ax = plt.subplots(figsize=(10, 5.4), dpi=110)
    fig.patch.set_facecolor(SURFACE)
    ax.set_facecolor(SURFACE)
    for name in ["Tromso", "London", "Sydney", "Singapore"]:
        d, real, applied = series[name]
        ax.plot(d, applied, color=COLOURS[name], lw=2.2, label=LABELS[name], solid_capstyle="round")
        if any(abs(a - b) > 1e-9 for a, b in zip(real, applied)):
            ax.plot(d, real, color=COLOURS[name], lw=1.2, ls=(0, (1, 2.5)))
        # Direct label at the line's 31 December value, past the right edge.
        ax.annotate(LABELS[name].split(" (")[0], (d[-1], applied[-1]), xytext=(6, 0),
                    textcoords="offset points", va="center", fontsize=10, color=INK)

    ax.axhline(12, color=MUTED, lw=0.8, ls="--")
    ax.axhspan(0, 1, color=GRID, lw=0)
    ax.axhspan(23, 24, color=GRID, lw=0)
    ax.text(date(args.year, 1, 8), 23.5, "never more than 23 h: the plugin keeps one hour of night",
            va="center", fontsize=9, color=MUTED)
    ax.text(date(args.year, 1, 8), 0.5, "never less than 1 h of day", va="center", fontsize=9, color=MUTED)

    ax.set_ylim(0, 24)
    ax.set_yticks(range(0, 25, 3))
    ax.set_ylabel("Daylight (hours of a 24-hour day)")
    ax.set_xlim(date(args.year, 1, 1), date(args.year, 12, 31))
    ax.xaxis.set_major_locator(mdates.MonthLocator())
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%b"))
    ax.set_xlabel(f"Real date ({args.year})")
    ax.grid(axis="y", color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(MUTED)
    ax.set_title("Daylight through the year with day-length mode real-place", loc="left",
                 fontsize=13, color=INK, pad=12)
    leg = ax.legend(loc="lower left", bbox_to_anchor=(0.0, 0.06), frameon=True, fontsize=9.5)
    leg.get_frame().set_facecolor(SURFACE)
    leg.get_frame().set_edgecolor(GRID)
    fig.subplots_adjust(left=0.08, right=0.86, top=0.9, bottom=0.12)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    fig.savefig(args.out, facecolor=SURFACE)
    print(f"wrote {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
