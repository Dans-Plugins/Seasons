"""Render "a year in thirty seconds": the default Seasons calendar and crop growth,
played over a small wheat farm under Strata's seasonal sun.

    python scripts/preview/render_year.py docs/images/seasons-year.gif

What comes from the plugin, and what does not:

- **From the plugin.** The season lengths (``calendar.days-per-season``) and growth
  multipliers (``growth.multiplier``) are read from ``src/main/resources/config.yml``.
  Each crop's growth steps go through a Python copy of ``GrowthPolicy.stagesFor``: below
  1.0 a vanilla step happens with that chance, above 1.0 the fraction is the chance of
  one more stage, and no crop passes fully grown.
- **Vanilla, as modelled here.** Wheat on hydrated farmland in a uniform field gets a
  random tick about 24000 * 3 / 4096 times a day (``randomTickSpeed`` 3) and grows a
  stage on one in six of them (growth factor 5). The whole field is harvested and replanted
  once nine crops in ten are ripe, as a player would.
- **Decoration only.** The sun is Strata's solar position (NOAA) at 15:00 local time,
  40 N 105 W, with each season laid over the matching quarter of 2026; Minecraft's own
  sun does not follow seasons, and Seasons does not yet change day length or weather.
  Grass and leaf colours change with the season for the same reason.
- **Not yet in the plugin.** Nothing advances the date yet (see README), so this shows
  what the configured calendar does once it does.

Needs Linux x86-64, ``libEGL.so.1`` with a Mesa driver (``LIBGL_ALWAYS_SOFTWARE=1
GALLIUM_DRIVER=llvmpipe`` without a GPU), NumPy, Pillow, PyYAML, and Strata's
official Python binding at the v0.6.0 tag::

    pip install numpy pillow pyyaml \\
      "strata-engine @ git+https://github.com/Preponderous-Software/strata@v0.6.0#subdirectory=bindings/python"

The library is Strata's v0.6.0 release, downloaded by the binding and checked
against the SHA-256 below. ``--frames DIR`` also writes every frame as a PNG.
"""

from __future__ import annotations

import argparse
import datetime as dt
import math
from pathlib import Path

import numpy as np
import yaml
from PIL import Image, ImageDraw, ImageFont

import strata as S

STRATA_VERSION = "0.6.0"
STRATA_SHA256 = "3815068f90afa67b42177359a7ea918a5de321d7f7bad8706fa5275c4906af2a"  # libstrata-0.6.0-linux-x64.so, from the release's SHA256SUMS
ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "src" / "main" / "resources" / "config.yml"
SEASONS = ("spring", "summer", "autumn", "winter")
# The 2026 equinoxes and solstices that each season is laid over (decoration only).
SEASON_START = {"spring": dt.date(2026, 3, 20), "summer": dt.date(2026, 6, 21),
                "autumn": dt.date(2026, 9, 22), "winter": dt.date(2026, 12, 21)}
LAT, LON, LOCAL_HOUR = 40.0, -105.0, 15.0
RANDOM_TICKS_PER_DAY = 24000 * 3 / 4096
VANILLA_STEP_CHANCE = 1 / 6
RIPE = 7
HARVEST_AT = 0.9  # the field is harvested once nine crops in ten are ripe
FIELD = 12
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# Decoration: ground, leaves and sky for each season.
PALETTE = {
    "spring": dict(grass=(0.42, 0.72, 0.30), leaves=(0.40, 0.70, 0.32), blossom=(0.95, 0.72, 0.82), label=(120, 200, 110)),
    "summer": dict(grass=(0.36, 0.62, 0.22), leaves=(0.22, 0.50, 0.18), blossom=None, label=(240, 200, 70)),
    "autumn": dict(grass=(0.55, 0.56, 0.26), leaves=(0.86, 0.46, 0.14), blossom=None, label=(225, 120, 50)),
    "winter": dict(grass=(0.82, 0.86, 0.90), leaves=None, blossom=None, label=(150, 190, 235)),
}


def read_config(path: Path):
    cfg = yaml.safe_load(path.read_text(encoding="utf-8"))
    days = {s: int(cfg["calendar"]["days-per-season"][s]) for s in SEASONS}
    growth = {s: float(cfg["growth"]["multiplier"][s]) for s in SEASONS}
    return days, growth


def stages_for(multiplier: float, roll: np.ndarray) -> np.ndarray:
    """GrowthPolicy.stagesFor, vectorised: whole stages, plus one with the fractional chance."""
    whole = math.floor(multiplier)
    return np.where(roll < multiplier - whole, whole + 1, whole)


def lerp(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def sky_for(elevation: float):
    low, high = (0.95, 0.76, 0.56), (0.55, 0.74, 0.93)
    return lerp(low, high, float(np.clip((elevation - 5) / 35, 0, 1)))


def cube(x, y, z, sx, sy, sz, rgb, a=1.0):
    return S.RenderItem(S.PRIMITIVE_CUBE, (x, y, z), (sx, sy, sz), (*rgb, a))


def wheat_colour(stage: int):
    young, ripe = (0.36, 0.70, 0.24), (0.86, 0.72, 0.30)
    return lerp(young, ripe, stage / RIPE)


def scene(season: str, stages: np.ndarray):
    p = PALETTE[season]
    items = [
        cube(0, -0.5, 0, 40, 1, 40, p["grass"]),                          # ground
        cube(0, -0.02, 0, FIELD + 1, 0.06, FIELD + 1, (0.40, 0.27, 0.16)),  # farmland
        cube(0, -0.01, 0, FIELD + 1, 0.06, 1, (0.20, 0.42, 0.80)),         # the water row
    ]
    half = FIELD / 2
    for i in range(FIELD):
        for j in range(FIELD):
            x, z = -half + 0.5 + i, -half + 0.5 + j
            if abs(z) < 0.6:
                continue  # the water row
            s = int(stages[i, j])
            h = 0.12 + 0.11 * s
            items.append(cube(x, h / 2, z, 0.78, h, 0.78, wheat_colour(s)))
    # farmhouse
    hx, hz = -10.5, -4.0
    items += [cube(hx, 1.5, hz, 6, 3, 5, (0.70, 0.55, 0.36)),
              cube(hx, 3.4, hz, 6.6, 0.8, 5.6, (0.55, 0.22, 0.18)),
              cube(hx, 4.1, hz, 5.0, 0.6, 4.0, (0.55, 0.22, 0.18)),
              cube(hx + 3.01, 1.0, hz + 1.0, 0.1, 2.0, 1.0, (0.30, 0.20, 0.12)),
              cube(hx - 1.5, 4.6, hz + 1.2, 0.8, 1.6, 0.8, (0.45, 0.45, 0.48))]
    if season == "winter":
        items.append(cube(hx, 4.45, hz, 4.4, 0.12, 3.4, (0.92, 0.94, 0.97)))
    # trees
    for tx, tz in ((9.0, -10.0), (-1.0, -12.5), (-16.0, 4.0), (-11.0, 10.5)):
        items.append(cube(tx, 1.5, tz, 0.8, 3.0, 0.8, (0.40, 0.28, 0.16)))
        if p["leaves"]:
            items.append(cube(tx, 3.6, tz, 3.2, 2.0, 3.2, p["leaves"]))
            items.append(cube(tx, 4.9, tz, 2.0, 1.0, 2.0, p["leaves"]))
            if p["blossom"]:
                items.append(cube(tx + 0.9, 4.0, tz + 0.9, 0.6, 0.6, 0.6, p["blossom"]))
                items.append(cube(tx - 1.0, 3.3, tz + 0.4, 0.6, 0.6, 0.6, p["blossom"]))
        else:
            items.append(cube(tx, 3.2, tz, 0.4, 1.4, 0.4, (0.40, 0.28, 0.16)))
            items.append(cube(tx, 3.1, tz, 2.2, 0.2, 0.2, (0.40, 0.28, 0.16)))
    return items


def simulate(days, growth, steps_per_day: int, seed: int):
    """Per step: (season, day of season, stages array, fields harvested so far this season)."""
    rng = np.random.default_rng(seed)
    stages = np.zeros((FIELD, FIELD), dtype=np.int64)
    ticks = RANDOM_TICKS_PER_DAY / steps_per_day
    out = []
    for season in SEASONS:
        harvests = 0
        for step in range(days[season] * steps_per_day):
            if (stages >= RIPE).mean() >= HARVEST_AT:
                harvests += 1
                stages = np.zeros_like(stages)  # the whole field is cut and replanted
            # vanilla growth steps this interval, then each one through stagesFor
            n = rng.binomial(rng.poisson(ticks, size=stages.shape), VANILLA_STEP_CHANCE)
            grow = np.zeros_like(stages)
            for k in range(int(n.max()) if n.size else 0):
                active = n > k
                grow += np.where(active, stages_for(growth[season], rng.random(stages.shape)), 0)
            stages = np.minimum(stages + grow, RIPE)
            out.append((season, step // steps_per_day + 1, stages.copy(), harvests))
    return out


def solar_unix(season: str, day: int, days: int) -> float:
    start = SEASON_START[season]
    nxt = SEASON_START[SEASONS[(SEASONS.index(season) + 1) % 4]]
    span = (nxt - start).days % 365 or 91
    date = start + dt.timedelta(days=(day - 1) * span / days)
    local_noon = dt.datetime(date.year, date.month, date.day, tzinfo=dt.timezone.utc).timestamp()
    return local_noon + (LOCAL_HOUR - LON / 15.0) * 3600  # local mean time to UTC


def hud(img: Image.Image, season, day, days, growth, harvests, scale: float):
    d = ImageDraw.Draw(img, "RGBA")
    s = lambda v: int(round(v * scale))
    big, small = ImageFont.truetype(FONT_BOLD, s(26)), ImageFont.truetype(FONT, s(15))
    d.rounded_rectangle((s(14), s(14), s(330), s(112)), radius=s(10), fill=(18, 22, 30, 180))
    d.text((s(28), s(22)), season.capitalize(), font=big, fill=PALETTE[season]["label"])
    d.text((s(28), s(58)), f"day {day} of {days[season]}   growth x{growth[season]:g}", font=small, fill=(235, 238, 243))
    ripen = RIPE / (RANDOM_TICKS_PER_DAY * VANILLA_STEP_CHANCE * growth[season]) if growth[season] else float("inf")
    d.text((s(28), s(80)), f"ripens in ~{ripen:.1f} days   harvests: {harvests}", font=small, fill=(235, 238, 243))
    # the year, one bar per season, scaled to its length
    total = sum(days.values())
    x0, x1, y = s(14), img.width - s(14), img.height - s(52)
    d.rectangle((0, y - s(26), img.width, img.height), fill=(18, 22, 30, 170))
    x = x0
    for name in SEASONS:
        w = (x1 - x0) * days[name] / total
        done = 1.0 if SEASONS.index(name) < SEASONS.index(season) else (day / days[name] if name == season else 0.0)
        d.rectangle((x + 1, y, x + w - 1, y + s(10)), fill=(255, 255, 255, 70))
        d.rectangle((x + 1, y, x + 1 + (w - 2) * done, y + s(10)), fill=(*PALETTE[name]["label"], 235))
        tag = f"{name}  {days[name]} days  x{growth[name]:g}"
        d.text((x + s(4), y - s(21)), tag, font=small, fill=(235, 238, 243))
        x += w
    cap = ImageFont.truetype(FONT, s(13))
    d.rectangle((0, img.height - s(26), img.width, img.height), fill=(18, 22, 30, 190))
    d.text((s(10), img.height - s(21)),
           "Seasons' default config.yml: calendar and growth multipliers. Sun and colours are decoration. Rendered with Strata "
           + STRATA_VERSION, font=cap, fill=(235, 238, 243))
    return img


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("out", help="output .gif")
    ap.add_argument("--poster", help="also write this still (.png/.jpg), from late spring")
    ap.add_argument("--frames", help="also write every frame as a PNG into this directory")
    ap.add_argument("--size", nargs=2, type=int, default=[960, 540])
    ap.add_argument("--steps-per-day", type=int, default=2)
    ap.add_argument("--gif-width", type=int, default=600)
    ap.add_argument("--gif-ms", type=int, default=100)
    ap.add_argument("--seed", type=int, default=2026)
    ap.add_argument("--strata", help="a local libstrata instead of the v0.6.0 release")
    args = ap.parse_args(argv)

    days, growth = read_config(CONFIG)
    steps = simulate(days, growth, args.steps_per_day, args.seed)
    lib = S.load(args.strata) if args.strata else S.load_release(STRATA_VERSION, sha256=STRATA_SHA256)
    w, h = args.size
    frames, totals, poster = [], {}, None
    with S.Engine(lib, w, h) as eng:
        light = lib.lighting_defaults(S.LIGHTING_SUN)
        light.cascade_count, light.shadow_map_size, light.shadow_distance, light.fog = 4, 2048, 70.0, 0
        eng.set_lighting(light)
        eng.set_camera(lib.look_at((16.0, 13.0, 18.0), (-2.0, 0.0, -1.0), (0.0, 1.0, 0.0), 40.0, w / h, 0.5, 200.0))
        prev = None
        for i, (season, day, stages, harvests) in enumerate(steps):
            if prev and prev[0] != season:
                totals[prev[0]] = prev[1]
            prev = (season, harvests)
            sp = lib.solar_position(solar_unix(season, day, days[season]), LAT, LON)
            eng.set_sun_direction(tuple(sp.to_sun))
            for _ in range(2 if i == 0 else 1):  # shadows settle on the second frame
                eng.begin_frame(clear_color=(*sky_for(sp.elevation_degrees), 1.0))
                eng.submit_primitives(scene(season, stages))
                eng.end_frame()
            rgba, fw, fh = eng.read_pixels()
            img = Image.frombytes("RGBA", (fw, fh), rgba).convert("RGB")
            img = hud(img, season, day, days, growth, harvests, w / 960)
            frames.append(img)
            if season == "spring" and day == days["spring"] - 4 and poster is None:
                poster = img
    if args.frames:
        fd = Path(args.frames)
        fd.mkdir(parents=True, exist_ok=True)
        for i, im in enumerate(frames):
            im.save(fd / f"f{i:04d}.png")
    if args.poster:
        Path(args.poster).parent.mkdir(parents=True, exist_ok=True)
        (poster or frames[0]).save(args.poster, optimize=True, quality=88)
    write_gif(frames, Path(args.out), args.gif_width, args.gif_ms)
    print(f"{len(frames)} frames; fields harvested per season: {totals | {prev[0]: prev[1]}}")
    return 0


def write_gif(images, out: Path, width: int, ms: int) -> None:
    if images[0].width > width:
        hh = round(images[0].height * width / images[0].width)
        images = [im.resize((width, hh), Image.LANCZOS) for im in images]
    step = max(1, len(images) // 24)
    sheet = Image.new("RGB", (images[0].width, images[0].height * len(images[::step])))
    for k, im in enumerate(images[::step]):
        sheet.paste(im, (0, k * im.height))
    palette = sheet.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    pal = [im.quantize(palette=palette, dither=Image.Dither.NONE) for im in images]
    out.parent.mkdir(parents=True, exist_ok=True)
    pal[0].save(out, save_all=True, append_images=pal[1:], duration=ms, loop=0, optimize=True)


if __name__ == "__main__":
    raise SystemExit(main())
