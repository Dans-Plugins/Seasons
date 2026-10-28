"""Render "a year in thirty seconds": the default Seasons calendar, crop growth and
weather bias, played over a small plains farm.

    python scripts/preview/render_year.py docs/images/seasons-year

writes ``seasons-year.gif`` (for the README), ``seasons-year.mp4`` and
``seasons-year.webm`` (small, muted) and ``seasons-year-poster.png``.

What comes from the plugin, and what does not:

- **From the plugin.** The season lengths (``calendar.days-per-season``), growth
  multipliers (``growth.multiplier``) and weather chances (``weather.<season>.rain-chance``
  and ``thunder-chance``) are read from ``src/main/resources/config.yml``. Each crop's
  growth steps go through a Python copy of ``GrowthPolicy.stagesFor``, and each
  natural weather change through a copy of ``WeatherListener``: whenever vanilla ends
  a weather period, the season's chance picks whether the next one is rainy (or
  thundery), and a change the other way is cancelled.
- **Vanilla, as modelled here.** Weather period lengths are vanilla's (Spigot 1.21.1
  ``ServerLevel``): clear for 12000-180000 ticks, rain for 12000-24000, no thunder
  for 12000-180000, thunder for 3600-15600. When a change is cancelled, vanilla
  draws a fresh period for the weather that stayed. Thunder runs on its own timer
  and is only seen while it is raining. Wheat on hydrated farmland in a uniform
  field gets a random tick about 24000 * 3 / 4096 times a day (``randomTickSpeed``
  3) and grows a stage on one in six of them (growth factor 5); the whole field is
  harvested and replanted once nine crops in ten are ripe, as a player would.
- **Not changed by the plugin, so not changed here.** The grass and leaves keep
  their plains colours all year (foliage colour is client-side, an MVP non-goal),
  no snow falls or settles, and the sun stays where Minecraft puts it at 15:00
  (time 9000) every day: neither Seasons nor Timescale changes the sun's path, and
  Seasons does not change day length yet. The farmhouse and trees are decoration.
- **Not yet in the plugin.** Nothing advances the date yet (see README), so this
  shows what the configured calendar does once it does.

Needs Linux x86-64, ``libEGL.so.1`` with a Mesa driver (``LIBGL_ALWAYS_SOFTWARE=1
GALLIUM_DRIVER=llvmpipe`` without a GPU), NumPy, Pillow, PyYAML, an ``ffmpeg`` with
libx264 and libvpx-vp9 (``--ffmpeg``, else ``imageio-ffmpeg``'s, else ``PATH``), and
Strata's official Python binding at the v0.6.0 tag::

    pip install numpy pillow pyyaml imageio-ffmpeg \\
      "strata-engine @ git+https://github.com/Preponderous-Software/strata@v0.6.0#subdirectory=bindings/python"

The library is Strata's v0.6.0 release, downloaded by the binding and checked
against the SHA-256 below. ``--frames DIR`` also writes every frame as a PNG.
This script is a development tool only; it is not part of the plugin or its build.
"""

from __future__ import annotations

import argparse
import math
import shutil
import subprocess
import tempfile
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
TICKS_PER_DAY = 24000
# Vanilla weather period lengths in ticks, inclusive (ServerLevel, Spigot 1.21.1).
CLEAR_TICKS, RAIN_TICKS = (12000, 180000), (12000, 24000)
NO_THUNDER_TICKS, THUNDER_TICKS = (12000, 180000), (3600, 15600)
RANDOM_TICKS_PER_DAY = TICKS_PER_DAY * 3 / 4096
VANILLA_STEP_CHANCE = 1 / 6
RIPE = 7
HARVEST_AT = 0.9  # the field is harvested once nine crops in ten are ripe
FIELD = 12
SUN_TIME = 9000  # 15:00; the sun is overhead at 6000 and rises in the east (+X)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
LABEL = {"spring": (120, 200, 110), "summer": (240, 200, 70), "autumn": (225, 120, 50), "winter": (150, 190, 235)}
RAIN_RGB, STORM_RGB = (90, 150, 235), (175, 120, 235)


def lin(hex_rgb: str):
    """A hex colour as the 0-1 RGB triple Strata takes (written out as given, unlit)."""
    return tuple(int(hex_rgb[i:i + 2], 16) / 255 for i in (0, 2, 4))


GRASS = lin("91BD59")    # plains grass colour
FOLIAGE = lin("77AB2F")  # plains foliage colour
LOG, PLANKS, ROOF, STONE = lin("6B5133"), lin("B8945F"), lin("8E3B2E"), lin("7F7F7F")
FARMLAND, WATER, SAND = lin("5A3A22"), lin("3F76E4"), lin("DBD3A0")
SKY = {"clear": lin("78A7FF"), "rain": lin("6F7C8C"), "thunder": lin("3A4049")}
DIM = {"clear": 1.0, "rain": 0.80, "thunder": 0.58}  # the sky darkens in rain and more in thunder


def read_config(path: Path):
    cfg = yaml.safe_load(path.read_text(encoding="utf-8"))
    days = {s: int(cfg["calendar"]["days-per-season"][s]) for s in SEASONS}
    growth = {s: float(cfg["growth"]["multiplier"][s]) for s in SEASONS}
    rain = {s: float(cfg["weather"][s]["rain-chance"]) for s in SEASONS}
    thunder = {s: float(cfg["weather"][s]["thunder-chance"]) for s in SEASONS}
    return days, growth, rain, thunder


def stages_for(multiplier: float, roll: np.ndarray) -> np.ndarray:
    """GrowthPolicy.stagesFor, vectorised: whole stages, plus one with the fractional chance."""
    whole = math.floor(multiplier)
    return np.where(roll < multiplier - whole, whole + 1, whole)


def season_at(tick: int, days) -> str:
    day = tick // TICKS_PER_DAY
    for s in SEASONS:
        if day < days[s]:
            return s
        day -= days[s]
    return SEASONS[-1]


def weather_spans(days, chance, on_ticks, off_ticks, rng):
    """One vanilla weather timer under WeatherListener: [(start, end, on)].

    The world starts with the weather off. Whenever a period ends, the season at that
    tick picks the next state with its chance (a change the other way is cancelled, so
    the roll decides either way) and vanilla draws a period for the state in place.
    """
    total = sum(days.values()) * TICKS_PER_DAY
    spans, t, on = [], 0, False
    while t < total:
        lo, hi = on_ticks if on else off_ticks
        end = t + int(rng.integers(lo, hi + 1))
        spans.append((t, min(end, total), on))
        t = end
        on = bool(rng.random() < chance[season_at(t, days)]) if t < total else on
    return spans


def mean(span):
    return (span[0] + span[1]) / 2


def long_run(rain_chance: float, thunder_chance: float):
    """The share of time it rains, and thunders, in a season that never ends.

    Each period's weather is picked afresh, so the share is the chance-weighted mean
    period length of that weather over the chance-weighted mean of both."""
    def share(p, on, off):
        return p * mean(on) / (p * mean(on) + (1 - p) * mean(off)) if p else 0.0
    wet = share(rain_chance, RAIN_TICKS, CLEAR_TICKS)
    return wet, wet * share(thunder_chance, THUNDER_TICKS, NO_THUNDER_TICKS)


def typical_seed(days, rain, thunder, steps_per_day: int, candidates: int) -> int:
    """The seed, of 0..candidates-1, whose year's rainy days per season are closest to
    the long-run share. One random year can easily rain more in summer than in spring;
    this keeps the clip from implying that."""
    best, best_err = 0, float("inf")
    for seed in range(candidates):
        rng = np.random.default_rng(seed)  # simulate() draws the weather first, the same way
        rs = weather_spans(days, rain, RAIN_TICKS, CLEAR_TICKS, rng)
        weather_spans(days, thunder, THUNDER_TICKS, NO_THUNDER_TICKS, rng)
        err, tick0 = 0.0, 0
        for s in SEASONS:
            n = days[s] * steps_per_day
            wet = sum(state_at(rs, tick0 + int((k + 0.5) * TICKS_PER_DAY / steps_per_day)) for k in range(n)) / steps_per_day
            err += (wet - days[s] * long_run(rain[s], thunder[s])[0]) ** 2
            tick0 += days[s] * TICKS_PER_DAY
        if err < best_err:
            best, best_err = seed, err
    return best


def state_at(spans, tick: int) -> bool:
    for a, b, on in spans:
        if a <= tick < b:
            return on
    return False


def simulate(days, growth, rain, thunder, steps_per_day: int, seed: int):
    """Per step: dict(season, day, stages, harvests, weather) with weather clear/rain/thunder."""
    rng = np.random.default_rng(seed)
    rain_spans = weather_spans(days, rain, RAIN_TICKS, CLEAR_TICKS, rng)
    thunder_spans = weather_spans(days, thunder, THUNDER_TICKS, NO_THUNDER_TICKS, rng)
    stages = np.zeros((FIELD, FIELD), dtype=np.int64)
    ticks = RANDOM_TICKS_PER_DAY / steps_per_day
    out, tick0 = [], 0
    for season in SEASONS:
        harvests = 0
        for step in range(days[season] * steps_per_day):
            if (stages >= RIPE).mean() >= HARVEST_AT:
                harvests += 1
                stages = np.zeros_like(stages)  # the whole field is cut and replanted
            n = rng.binomial(rng.poisson(ticks, size=stages.shape), VANILLA_STEP_CHANCE)
            grow = np.zeros_like(stages)
            for k in range(int(n.max()) if n.size else 0):
                grow += np.where(n > k, stages_for(growth[season], rng.random(stages.shape)), 0)
            stages = np.minimum(stages + grow, RIPE)
            tick = tick0 + int((step + 0.5) * TICKS_PER_DAY / steps_per_day)
            raining = state_at(rain_spans, tick)
            weather = ("thunder" if state_at(thunder_spans, tick) else "rain") if raining else "clear"
            out.append(dict(season=season, day=step // steps_per_day + 1, stages=stages.copy(),
                            harvests=harvests, weather=weather))
        tick0 += days[season] * TICKS_PER_DAY
    return out


# -- the farm ---------------------------------------------------------------------------

TERRAIN_N, TERRAIN_ORIGIN = 64, (-32.0, 0.0, -32.0)
POND = (9.0, -9.0, 4.2)  # centre x, z and radius


def ground_height(x, z):
    """Flat round the farm, rising into low hills behind it, with a pond to the right."""
    back = np.clip((-z - 9.0) / 10.0, 0, 1) + np.clip((-x - 13.0) / 10.0, 0, 1)
    hills = back * (2.4 + 1.3 * np.sin(x * 0.31) * np.cos(z * 0.27))
    d = np.hypot(x - POND[0], z - POND[1])
    pond = -1.4 * np.clip(1.0 - d / POND[2], 0, 1) ** 0.6
    return hills + pond


def terrain_tile():
    n = TERRAIN_N
    xs = TERRAIN_ORIGIN[0] + np.arange(n)
    zs = TERRAIN_ORIGIN[2] + np.arange(n)
    X, Z = np.meshgrid(xs, zs)  # row by row along +Z, x fastest
    H = ground_height(X, Z).astype(np.float32)
    shade = 1.0 + 0.05 * (((np.floor(X) + np.floor(Z)) % 2) * 2 - 1)  # a faint block grid
    cols = np.empty((n, n, 3), dtype=np.float32)
    cols[:] = np.array(GRASS, dtype=np.float32)
    d = np.hypot(X - POND[0], Z - POND[1])
    cols[d < POND[2] + 1.2] = SAND
    cols *= shade[..., None]
    heights = np.ascontiguousarray(H.ravel())
    colors = np.ascontiguousarray(cols.reshape(-1))
    tile = S.TerrainTile()
    tile.origin[:] = TERRAIN_ORIGIN
    tile.cell_size = 1.0
    tile.samples_x = tile.samples_z = n
    tile.heights = heights.ctypes.data_as(S.ctypes.POINTER(S.ctypes.c_float))
    tile.colors = colors.ctypes.data_as(S.ctypes.POINTER(S.ctypes.c_float))
    tile._keep = (heights, colors)
    return tile


def cube(x, y, z, sx, sy, sz, rgb, a=1.0):
    return S.RenderItem(S.PRIMITIVE_CUBE, (x, y, z), (sx, sy, sz), (*rgb, a))


def lerp(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def wheat_colour(stage: int):
    return lerp(lin("4E9A2A"), lin("DCBB4E"), stage / RIPE)


TREES = ((10.0, -15.0), (2.0, -17.0), (-8.0, -15.5), (-18.0, -4.0), (-17.0, 7.0), (14.0, -9.0))


def static_scene():
    items = [cube(0, -0.02, 0, FIELD + 1, 0.08, FIELD + 1, FARMLAND),
             cube(0, -0.01, 0, FIELD + 1, 0.08, 1, WATER),                       # the water row
             cube(POND[0], -0.12, POND[1], 2 * POND[2] - 0.6, 0.06, 2 * POND[2] - 0.6, WATER, 0.85)]
    hx, hz = -11.0, 3.0  # farmhouse
    items += [cube(hx, 1.5, hz, 5, 3, 6, PLANKS), cube(hx, 3.35, hz, 5.6, 0.7, 6.6, ROOF),
              cube(hx, 4.0, hz, 4.2, 0.6, 6.0, ROOF), cube(hx, 4.55, hz, 2.6, 0.5, 6.0, ROOF),
              cube(hx + 2.51, 1.0, hz - 1.0, 0.1, 2.0, 1.0, LOG),
              cube(hx - 1.2, 4.8, hz + 2.0, 0.8, 1.6, 0.8, STONE)]
    for tx, tz in TREES:  # oaks: a four-log trunk under blocky leaves, plains colours all year
        y0 = float(ground_height(np.array(tx), np.array(tz)))
        items += [cube(tx, y0 + 2.0, tz, 1, 4, 1, LOG),
                  cube(tx, y0 + 4.0, tz, 5, 2, 5, FOLIAGE),
                  cube(tx, y0 + 5.5, tz, 3, 1, 3, FOLIAGE),
                  cube(tx + 0.5, y0 + 6.3, tz, 1.0, 0.6, 2.0, FOLIAGE)]
    return items


def crops(stages: np.ndarray):
    half, items = FIELD / 2, []
    for i in range(FIELD):
        for j in range(FIELD):
            x, z = -half + 0.5 + i, -half + 0.5 + j
            if abs(z) < 0.6:
                continue  # the water row
            s = int(stages[i, j])
            h = 0.12 + 0.11 * s
            items.append(cube(x, h / 2, z, 0.78, h, 0.78, wheat_colour(s)))
    return items


def rain_lines(frame: int, storm: bool, rng_seed: int = 7):
    rng = np.random.default_rng(rng_seed)
    n = 1400 if storm else 900
    pos = rng.uniform((-26, 0, -26), (26, 20, 26), size=(n, 3))
    pos[:, 1] = (pos[:, 1] - frame * 6.5) % 20.0
    colour = (0.55, 0.60, 0.75, 0.55)
    out = []
    for x, y, z in pos:
        out += [S.LineVertex((x, y + 0.9, z), colour), S.LineVertex((x - 0.08, y, z), colour)]
    return out


def sun_direction(time: int):
    a = (time - 6000) / TICKS_PER_DAY * 2 * math.pi  # 0 at noon, + towards the west
    return (-math.sin(a), math.cos(a), 0.0)


# -- overlay ----------------------------------------------------------------------------

def hud(img, st, days, growth, rain, thunder, history, scale: float):
    d = ImageDraw.Draw(img, "RGBA")
    s = lambda v: int(round(v * scale))
    big, small = ImageFont.truetype(FONT_BOLD, s(26)), ImageFont.truetype(FONT, s(15))
    season, day = st["season"], st["day"]
    d.rounded_rectangle((s(14), s(14), s(392), s(156)), radius=s(10), fill=(18, 22, 30, 185))
    d.text((s(28), s(22)), season.capitalize(), font=big, fill=LABEL[season])
    weather = {"clear": "clear", "rain": "rain", "thunder": "thunderstorm"}[st["weather"]]
    wcol = {"clear": (235, 238, 243), "rain": RAIN_RGB, "thunder": STORM_RGB}[st["weather"]]
    d.text((s(28) + d.textlength(season.capitalize(), font=big) + s(14), s(32)), weather, font=small, fill=wcol)
    d.text((s(28), s(58)), f"day {day} of {days[season]}   growth x{growth[season]:g}", font=small, fill=(235, 238, 243))
    ripen = RIPE / (RANDOM_TICKS_PER_DAY * VANILLA_STEP_CHANCE * growth[season]) if growth[season] else float("inf")
    d.text((s(28), s(80)), f"wheat ripens in ~{ripen:.1f} days   harvests: {st['harvests']}", font=small, fill=(235, 238, 243))
    d.text((s(28), s(102)), f"rain-chance {rain[season]:g}   thunder-chance {thunder[season]:g}", font=small, fill=(235, 238, 243))
    wet, storm = long_run(rain[season], thunder[season])
    d.text((s(28), s(124)), f"long run: raining {pct(wet)}, thundering {pct(storm)} of the time", font=small, fill=(190, 196, 206))
    # the year: one bar per season, scaled to its length, with the weather so far beneath it
    total = sum(days.values())
    x0, x1, y = s(14), img.width - s(14), img.height - s(60)
    d.rectangle((0, y - s(26), img.width, img.height), fill=(18, 22, 30, 175))
    x = x0
    for name in SEASONS:
        w = (x1 - x0) * days[name] / total
        idx = SEASONS.index(name)
        done = 1.0 if idx < SEASONS.index(season) else (day / days[name] if name == season else 0.0)
        d.rectangle((x + 1, y, x + w - 1, y + s(8)), fill=(255, 255, 255, 70))
        d.rectangle((x + 1, y, x + 1 + (w - 2) * done, y + s(8)), fill=(*LABEL[name], 235))
        d.text((x + s(4), y - s(20)), name, font=small, fill=LABEL[name])
        x += w
    seen, n_all = history[1], history[0]
    for k, wx in enumerate(seen):
        if wx == "clear":
            continue
        a = x0 + (x1 - x0) * k / n_all
        b = x0 + (x1 - x0) * (k + 1) / n_all
        d.rectangle((a, y + s(11), max(b, a + 1), y + s(19)), fill=(*(STORM_RGB if wx == "thunder" else RAIN_RGB), 255))
    cap = ImageFont.truetype(FONT, s(12))
    d.rectangle((0, img.height - s(28), img.width, img.height), fill=(18, 22, 30, 200))
    d.text((s(10), img.height - s(22)),
           "Seasons' default config.yml; vanilla timers modelled. Sun and colours unchanged: "
           "the plugin changes neither. Rendered with Strata " + STRATA_VERSION,
           font=cap, fill=(235, 238, 243))
    return img


def pct(share: float) -> str:
    return "<1%" if 0 < share < 0.005 else f"{share:.0%}"


def find_ffmpeg(given):
    if given:
        return given
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return shutil.which("ffmpeg")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("out", help="output path without extension, e.g. docs/images/seasons-year")
    ap.add_argument("--frames", help="also write every frame as a PNG into this directory")
    ap.add_argument("--size", nargs=2, type=int, default=[960, 540])
    ap.add_argument("--steps-per-day", type=int, default=4)
    ap.add_argument("--fps", type=int, default=15, help="video frame rate (4 steps a day at 15 fps is ~30 s)")
    ap.add_argument("--gif-width", type=int, default=600)
    ap.add_argument("--gif-every", type=int, default=2, help="keep every Nth frame in the GIF")
    ap.add_argument("--seed", type=int, help="the year's random seed (default: the most typical of --candidates)")
    ap.add_argument("--candidates", type=int, default=200, help="seeds searched for the most typical year")
    ap.add_argument("--strata", help="a local libstrata instead of the v0.6.0 release")
    ap.add_argument("--ffmpeg", help="ffmpeg to encode the MP4 and WebM with")
    args = ap.parse_args(argv)

    days, growth, rain, thunder = read_config(CONFIG)
    seed = args.seed if args.seed is not None else typical_seed(days, rain, thunder, args.steps_per_day, args.candidates)
    print(f"seed {seed}")
    steps = simulate(days, growth, rain, thunder, args.steps_per_day, seed)
    lib = S.load(args.strata) if args.strata else S.load_release(STRATA_VERSION, sha256=STRATA_SHA256)
    w, h = args.size
    frames, poster_at = [], None
    still = static_scene()
    with S.Engine(lib, w, h) as eng:
        light = lib.lighting_defaults(S.LIGHTING_SUN)
        light.cascade_count, light.shadow_map_size, light.shadow_distance, light.fog = 4, 2048, 80.0, 0
        eng.set_lighting(light)
        eng.set_sun_direction(sun_direction(SUN_TIME))
        eng.set_terrain(terrain_tile())
        eng.set_camera(lib.look_at((19.0, 14.0, 21.0), (-1.5, 0.5, -3.0), (0.0, 1.0, 0.0), 42.0, w / h, 0.5, 200.0))
        seen = []
        for i, st in enumerate(steps):
            seen.append(st["weather"])
            for _ in range(2 if i == 0 else 1):  # shadows settle on the second frame
                eng.begin_frame(clear_color=(*SKY[st["weather"]], 1.0))
                eng.submit_primitives(still + crops(st["stages"]))
                if st["weather"] != "clear":
                    eng.submit_lines(rain_lines(i, st["weather"] == "thunder"))
                eng.end_frame()
            rgba, fw, fh = eng.read_pixels()
            img = Image.frombytes("RGBA", (fw, fh), rgba).convert("RGB")
            if DIM[st["weather"]] < 1.0:
                img = Image.eval(img, lambda v, k=DIM[st["weather"]]: int(v * k))
            img = hud(img, st, days, growth, rain, thunder, (len(steps), seen), w / 960)
            frames.append(img)
            # the poster: the last rainy step of spring, else late spring
            if st["season"] == "spring" and (st["weather"] != "clear" or poster_at is None and st["day"] >= days["spring"] - 4):
                poster_at = i
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    frames[poster_at if poster_at is not None else 0].save(out.with_name(out.name + "-poster.png"), optimize=True)
    with tempfile.TemporaryDirectory() as tmp:
        fd = Path(args.frames) if args.frames else Path(tmp)
        fd.mkdir(parents=True, exist_ok=True)
        for k, im in enumerate(frames):
            im.save(fd / f"f{k:04d}.png")
        encode(find_ffmpeg(args.ffmpeg), fd, args.fps, out)
    write_gif(frames[::args.gif_every], out.with_suffix(".gif") if out.suffix == "" else Path(str(out) + ".gif"),
              args.gif_width, round(1000 * args.gif_every / args.fps))
    report(steps, days, rain, thunder, args.steps_per_day, args.fps)
    return 0


def encode(ffmpeg, frames_dir: Path, fps: int, out: Path) -> None:
    if not ffmpeg:
        raise SystemExit("no ffmpeg found: pass --ffmpeg or pip install imageio-ffmpeg")
    src = ["-framerate", str(fps), "-i", str(frames_dir / "f%04d.png"), "-an", "-pix_fmt", "yuv420p"]
    subprocess.run([ffmpeg, "-v", "error", "-y", *src, "-c:v", "libx264", "-preset", "slow", "-crf", "35",
                    "-movflags", "+faststart", "-g", str(fps * 4), str(out) + ".mp4"], check=True)
    subprocess.run([ffmpeg, "-v", "error", "-y", *src, "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "50",
                    "-row-mt", "1", "-deadline", "good", "-cpu-used", "2", "-g", str(fps * 4), str(out) + ".webm"], check=True)


def report(steps, days, rain, thunder, per_day: int, fps: int) -> None:
    print(f"{len(steps)} frames, {len(steps) / fps:.1f} s at {fps} fps")
    for s in SEASONS:
        mine = [st for st in steps if st["season"] == s]
        wet = sum(st["weather"] != "clear" for st in mine) / per_day
        storm = sum(st["weather"] == "thunder" for st in mine) / per_day
        lr = long_run(rain[s], thunder[s])
        print(f"  {s}: {days[s]} days, rain {wet:g} days (thunder {storm:g}; long run {days[s] * lr[0]:.1f} "
              f"({days[s] * lr[1]:.1f})), harvests {mine[-1]['harvests']}")


def write_gif(images, out: Path, width: int, ms: int) -> None:
    if images[0].width > width:
        hh = round(images[0].height * width / images[0].width)
        images = [im.resize((width, hh), Image.LANCZOS) for im in images]
    step = max(1, len(images) // 48)  # rainy frames are rare; sample enough to keep their greys
    sheet = Image.new("RGB", (images[0].width, images[0].height * len(images[::step])))
    for k, im in enumerate(images[::step]):
        sheet.paste(im, (0, k * im.height))
    palette = sheet.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    pal = [im.quantize(palette=palette, dither=Image.Dither.NONE) for im in images]
    out.parent.mkdir(parents=True, exist_ok=True)
    pal[0].save(out, save_all=True, append_images=pal[1:], duration=ms, loop=0, optimize=True)


if __name__ == "__main__":
    raise SystemExit(main())
