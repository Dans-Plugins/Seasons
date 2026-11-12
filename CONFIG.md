# Seasons Configuration

Seasons writes `plugins/Seasons/config.yml` on first start. Edit it and restart the server to apply changes.

If the file is not valid YAML (a tab used for indentation, for example), the plugin does not enable, and the console names the file and what is wrong with it. `calendar.yml` is left as it is, so the saved date is safe; fix the file and restart.

The same happens if `calendar`, `growth`, `weather`, `announcement`, `day-length` or a section inside them is given a plain value instead of its keys (`days-per-season: 60`), or holds a key that is not in the tables below (`fall`, `Summer`, or a key indented one level too shallow). The console names the offending path. Keys are case-sensitive, and the season names are `spring`, `summer`, `autumn` and `winter`.

## Calendar

| Key | Default | Description |
|---|---|---|
| `calendar.days-per-season.spring` | `28` | In-game days that spring lasts. |
| `calendar.days-per-season.summer` | `28` | In-game days that summer lasts. |
| `calendar.days-per-season.autumn` | `28` | In-game days that autumn lasts. |
| `calendar.days-per-season.winter` | `28` | In-game days that winter lasts. |

Each value must be a whole number of at least 1. A missing key uses the default. Any other value (zero, a negative number, text) stops the plugin from enabling, and the console names the offending key.

The year runs spring, summer, autumn, winter, then back to spring. Its length is the sum of the four values.

## Growth

| Key | Default | Description |
|---|---|---|
| `growth.multiplier.spring` | `1.5` | Crop growth speed in spring. |
| `growth.multiplier.summer` | `1.0` | Crop growth speed in summer. |
| `growth.multiplier.autumn` | `0.75` | Crop growth speed in autumn. |
| `growth.multiplier.winter` | `0.25` | Crop growth speed in winter. |

`1.0` is vanilla speed. Each value must be a number of at least 0. A missing key uses the default. Any other value (a negative number, text) stops the plugin from enabling, and the console names the offending key.

The multiplier applies to every vanilla growth step (Bukkit's `BlockGrowEvent`) while that season is current:

- Below `1.0`, each step happens only with that chance, so `0.25` lets one step in four through. `0` stops growth for the season.
- Above `1.0`, every step happens, and the part above each whole number is the chance of growing one more stage. `1.5` grows a crop one stage or two with equal chance; `2.0` always grows two. Extra stages never take a crop past fully grown.
- Extra stages only apply to crops that age in place, such as wheat, carrots and nether wart. Growth that places a new block, such as sugar cane, cactus, or a melon or pumpkin beside its stem, is slowed by a multiplier below `1.0` but not sped up by one above it.

The server's `randomTickSpeed` is never changed, so ore blocks, fire spread and other random-tick behaviour are unaffected.

## Weather

| Key | Default | Description |
|---|---|---|
| `weather.spring.rain-chance` | `0.6` | Chance that a new weather period in spring is rainy. |
| `weather.spring.thunder-chance` | `0.2` | Chance that a new thunder period in spring is thundery. |
| `weather.summer.rain-chance` | `0.3` | Chance that a new weather period in summer is rainy. |
| `weather.summer.thunder-chance` | `0.3` | Chance that a new thunder period in summer is thundery. |
| `weather.autumn.rain-chance` | `0.5` | Chance that a new weather period in autumn is rainy. |
| `weather.autumn.thunder-chance` | `0.4` | Chance that a new thunder period in autumn is thundery. |
| `weather.winter.rain-chance` | `0.4` | Chance that a new weather period in winter is rainy. |
| `weather.winter.thunder-chance` | `0.05` | Chance that a new thunder period in winter is thundery. |

Each value must be a number from 0 to 1. A missing key uses the default. Any other value (a negative number, a number above 1, text) stops the plugin from enabling, and the console names the offending key.

Vanilla still decides how long each weather period lasts. Whenever a period ends naturally, the current season decides what the next one is: with `rain-chance: 0.6`, each new period is rainy with a 60% chance and clear otherwise. If the season picks the weather that is already in place, it carries on, and on the next tick vanilla draws a fresh period of that weather: 12000 to 180000 ticks (half a day to seven and a half days) of clear sky, or 12000 to 24000 ticks (half a day to a day) of rain, as on Spigot 1.21.1. Thunder works the same way on its own timer, and is only seen while it is also raining. `0` means the weather never turns that way in that season; `1` means it never turns away.

The chance is per period, not a share of time: vanilla clear periods usually last much longer than rainy ones, so a `rain-chance` of `0.5` does not mean it rains half the time.

Weather set with vanilla's `/weather` (including `/minecraft:weather`, `/execute ... run weather`, and from the console, over RCON or from a command block), or cleared by players sleeping through the night, is never overridden. Spigot does not report why weather changed, so a change made by another plugin, by another plugin's weather command such as `/sun` or `/storm`, or by a `weather` command inside a data pack function, is treated like a natural one and the season may cancel it.

Rain does not yet turn to snow in winter. It already falls as snow in cold biomes, as in vanilla.

## Announcement

| Key | Default | Description |
|---|---|---|
| `announcement.enabled` | `true` | Whether a chat message is broadcast to everyone when the season changes. |
| `announcement.message` | `'&6It is now {season}.'` | The message. `{season}` is replaced with the new season (`spring`, `summer`, `autumn` or `winter`), and `&` colour codes such as `&6` (gold) are allowed. |

`announcement.enabled` must be `true` or `false`, and `announcement.message` must be text. A missing key uses the default. Any other value stops the plugin from enabling, and the console names the offending key. A blank message announces nothing, as does `enabled: false`.

The message is sent only when the season itself changes. Moving to another day of the same season announces nothing. For now only [`/season set`](COMMANDS.md) changes the date, so it is the only thing that triggers the announcement.

## Day length

| Key | Default | Description |
|---|---|---|
| `day-length.mode` | `'off'` | `'off'` leaves the day length to vanilla (or to Timescale). `real-place` makes each in-game day as long, in daylight, as the real date it stands for at the place below. |
| `day-length.latitude` | `51.5` | Latitude of the place, in degrees from -90 to 90, north positive. |
| `day-length.longitude` | `-0.13` | Longitude of the place, in degrees from -180 to 180, east positive. |
| `day-length.spring-starts` | `auto` | The real month and day that spring day 1 stands for, such as `03-20` or `3-1`. `auto` uses the March equinox (20 March) at or north of the equator and the September equinox (23 September) south of it. |

`mode` must be `'off'` or `real-place` (an unquoted `off` is read as `'off'` too). `latitude` and `longitude` must be numbers in their ranges, and `spring-starts` must be `auto` or a month and day that exist in a common year (so not `02-29`). A missing key uses the default. Any other value stops the plugin from enabling, and the console names the offending key. Every key is checked even while `mode` is `'off'`.

With `mode: real-place`, the Seasons year is stretched over one real year that starts on `spring-starts`: with four 28-day seasons, each in-game day stands for about 3.3 real days. For each in-game day, the share of the real day that the sun is above the horizon at the configured place becomes the share of the in-game day that is daytime. At the defaults (London), summer day 1 stands for 20 June and has 16.4 hours of daylight, so its day half runs 1.37 times as long as vanilla and its night half 0.63 times; winter day 1 stands for 20 December and has 7.6 hours. The whole in-game day keeps its vanilla length of 20 minutes, only split differently. With the March equinox as the start, the longest day falls at the start of summer, as in astronomical seasons; set `spring-starts` to about `02-04` for the solstice to fall in mid-summer, or to `03-01` for meteorological seasons.

- **What changes.** Only *when* the sun is up. Vanilla clients draw the sun on a fixed path, so how high it climbs and where it rises do not change. The day half is time of day 0 to 11999 and the night half 12000 to 23999, as in Timescale; the vanilla sun clears the horizon slightly before 0 and after 12000, so this is an approximation of "the sun is up".
- **Polar places.** Beyond the polar circles the real sun can stay up or down all day. The daylight applied is kept between 1 and 23 hours of the real day, so a polar night still has a short day and a polar summer a short night.
- **Horizon.** Sunrise and sunset are taken with the sun's centre on a geometric horizon, without refraction, to agree with [Strata](https://github.com/Preponderous-Software/strata)'s sun. Published sunrise tables count a few more minutes of daylight a day.
- **The clock.** Vanilla's daylight cycle keeps running, and sleeping through the night works as in vanilla. Once per server tick, Seasons holds the clock of each overworld back or moves it on a little. If the daylight cycle is turned off, or players sleep, `/time` is used or another plugin moves the clock, that world is left alone for that tick and followed from its new time. The nether and the end are not touched. Spigot reports each nudge to other plugins as a `TimeSkipEvent` with the `CUSTOM` reason.
- **Changing the date.** The day length follows the current date, so [`/season set`](COMMANDS.md) changes it from the next server tick. The clock is not moved: the rest of the current day or night half runs at the new date's length. While Timescale is enabled, only the published target below changes.
- **Timescale.** Seasons and Timescale must not both drive the world clock. While [Timescale](https://github.com/Dans-Plugins/Timescale) is enabled, Seasons does not touch any clock, and the day length is Timescale's. Seasons still registers its target, `com.dansplugins.seasons.sun.DayLength` (`daylightFraction()`, `dayRatio()` and `nightRatio()` in Timescale's terms), with Bukkit's services manager, for a clock plugin to read; Timescale does not read it yet.
- **Privacy.** The place is only read from this file. It is never shown to players, and the console shows only the real date the current day stands for and its daylight, once, when the plugin enables.

## Saved state

The current date is kept in `plugins/Seasons/calendar.yml`, separate from the configuration, and is written when the plugin disables and whenever [`/season set`](COMMANDS.md) changes the date. Each save goes to `calendar.yml.tmp`, which then replaces `calendar.yml` in one step, so a save interrupted part-way leaves the previous date in place. A leftover `calendar.yml.tmp` is safe to delete. Keep `calendar.yml` as a plain file in a writable folder: a symbolic link would be replaced by a plain file, and a file mounted on its own (a single-file Docker bind mount, for example) cannot be replaced, so the save fails and the console logs an error. The file holds two keys:

| Key | Description |
|---|---|
| `season` | `spring`, `summer`, `autumn` or `winter`. |
| `day-of-season` | The day within that season, counting from 1. |

The file stores the season rather than a bare day of the year, so changing season lengths never moves the server into a different season. If a season is shortened below the saved day, the date moves to that season's last day. A server with no `calendar.yml` starts on day 1 of spring.

If `calendar.yml` cannot be read as YAML, or holds an unknown `season` or a `day-of-season` that is not a whole number of at least 1, the plugin does not enable, and the console names the file and what is wrong with it. The file is left as it is, so fix or delete it and restart; deleting it starts the server on day 1 of spring.

Nothing advances the date on its own yet; only `/season set` changes it. Whether it follows in-game days or real time is an open question in [MVP.md](MVP.md).
