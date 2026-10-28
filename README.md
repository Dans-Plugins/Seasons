# Seasons

## Description

Seasons is a Minecraft plugin that gives a server a year. Spring, summer, autumn and winter advance on a configurable calendar, and each one changes how the world behaves — what weather rolls in, how fast crops grow, and how long the days run.

It exists because a survival server that runs for months has no sense of elapsed time. Every day is the same day. A season that a player can feel — a hard winter where the wheat crawls and the rain turns to snow — turns "how long have we been here" into something the world answers on its own.

This plugin was proposed from a Kingdom: First Era player request on 2026-09-04.

### Preview: a year of the default config

<picture>
  <source media="(prefers-reduced-motion: reduce)" srcset="docs/images/seasons-year-poster.png">
  <img src="docs/images/seasons-year.gif" width="600" alt="A small plains farm with a wheat field, a pond, oak trees and a farmhouse through one Seasons year of 112 days. The field is replanted after every harvest and ripens in about 1.6 days in spring (growth x1.5), 2.4 in summer (x1.0), 3.2 in autumn (x0.75) and 9.6 in winter (x0.25), so it is harvested 9, 8, 5 and 2 times. Rain comes and goes: about 5 days of it in spring, 2.5 in summer, 4 in autumn and 3 in winter, marked on a timeline under the year bar. The grass, leaves and sun stay the same all year.">
</picture>

*One year of the default `config.yml` (four 28-day seasons; growth x1.5 / x1.0 / x0.75 / x0.25; rain-chance 0.6 / 0.3 / 0.5 / 0.4; thunder-chance 0.2 / 0.3 / 0.4 / 0.05) over a plains farm at four steps a day, about 30 seconds. Also as [MP4](docs/images/seasons-year.mp4) and [WebM](docs/images/seasons-year.webm). Rendered headless with [Strata](https://github.com/Preponderous-Software/strata) 0.6.0 by [`scripts/preview/render_year.py`](scripts/preview/render_year.py), a development tool that is not part of the plugin or its build.*

*What is real: the calendar, multipliers and chances are read from the config file; every crop growth step goes through a copy of `GrowthPolicy.stagesFor`, and every natural weather change through a copy of `WeatherListener`'s rule, with vanilla's own weather period lengths (Spigot 1.21.1). The "long run" line is the share of time a season would rain, and thunder, if it never ended; the year shown is the one closest to those shares out of 200 random years. Thunder is rare at the defaults (about half a day a year, only while it is raining), and there is none in this year. What is not: the grass and leaves keep their plains colours, no snow falls, and the sun stays at 15:00, because Seasons changes none of these (foliage colour is an MVP non-goal; day length is not implemented yet). Nothing advances the date yet either, so this shows what the calendar will do once it does.*

## Project Status

**Pre-implementation.** The plugin loads a configurable four-season calendar and keeps the current date across restarts, and scales crop growth and biases rain and thunder by the current season, but nothing advances the date yet, so the season only changes if `calendar.yml` is edited. The scope of the first release is defined in [MVP.md](MVP.md) — read that before opening a pull request, because it says as much about what v1 will *not* do as what it will.

## Planned Features

- A configurable year divided into four seasons
- Per-season weather bias — snow in winter, storms in autumn
- Per-season crop growth rates
- Per-season day length
- A command to read the current season and date, and an admin command to set it

## Installation

Not yet released. Once there is a release, installation will follow the usual DPC pattern: drop the jar in `plugins/` and restart. It will also be available through [Dan's Plugin Manager](https://github.com/Dans-Plugins/Dans-Plugin-Manager).

### Building from source

Building requires JDK 21. The plugin is compiled for Java 17, so it still runs on the oldest supported server.

```bash
./gradlew clean build
```

The jar is written to `build/libs/Seasons-<version>.jar`.

### Dependencies

None. Seasons is intended to stand alone.

## Usage

### Documentation

- [MVP](MVP.md) – Scope of the first release, and its deliberate non-goals
- [Configuration](CONFIG.md) – Every `config.yml` key, including the per-season growth multipliers and weather chances, and the saved calendar file

`USER_GUIDE.md`, `COMMANDS.md` and `CHANGELOG.md` land with the first release, per [DOCUMENTATION_PRACTICES.md](https://github.com/Dans-Plugins/dpc-conventions/blob/main/docs/DOCUMENTATION_PRACTICES.md).

## Support

You can find the support Discord server [here](https://discord.gg/xXtuAQ2).

### Experiencing a bug?

Please fill out a bug report [here](https://github.com/Dans-Plugins/Seasons/issues/new).

## Contributing

This repository follows the standards in [dpc-conventions](https://github.com/Dans-Plugins/dpc-conventions). The build tooling is in place; `CONTRIBUTING.md` has not been written yet.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

See the [LICENSE](LICENSE) file for full details.
