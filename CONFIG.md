# Seasons Configuration

Seasons writes `plugins/Seasons/config.yml` on first start. Edit it and restart the server to apply changes.

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

Weather set with vanilla's `/weather` (including `/minecraft:weather` and from the console or a command block), or cleared by players sleeping through the night, is never overridden. Spigot does not report why weather changed, so a change made by another plugin, or by another plugin's weather command such as `/sun` or `/storm`, is treated like a natural one and the season may cancel it.

Rain does not yet turn to snow in winter. It already falls as snow in cold biomes, as in vanilla.

## Announcement

| Key | Default | Description |
|---|---|---|
| `announcement.enabled` | `true` | Whether a chat message is broadcast to everyone when the season changes. |
| `announcement.message` | `'&6It is now {season}.'` | The message. `{season}` is replaced with the new season (`spring`, `summer`, `autumn` or `winter`), and `&` colour codes such as `&6` (gold) are allowed. |

`announcement.enabled` must be `true` or `false`, and `announcement.message` must be text. A missing key uses the default. Any other value stops the plugin from enabling, and the console names the offending key. A blank message announces nothing, as does `enabled: false`.

The message is sent only when the season itself changes. Moving to another day of the same season announces nothing. For now only [`/season set`](COMMANDS.md) changes the date, so it is the only thing that triggers the announcement.

## Saved state

The current date is kept in `plugins/Seasons/calendar.yml`, separate from the configuration, and is written when the plugin disables and whenever [`/season set`](COMMANDS.md) changes the date. It holds two keys:

| Key | Description |
|---|---|
| `season` | `spring`, `summer`, `autumn` or `winter`. |
| `day-of-season` | The day within that season, counting from 1. |

The file stores the season rather than a bare day of the year, so changing season lengths never moves the server into a different season. If a season is shortened below the saved day, the date moves to that season's last day. A server with no `calendar.yml` starts on day 1 of spring.

If `calendar.yml` cannot be read as YAML, or holds an unknown `season` or a `day-of-season` that is not a whole number of at least 1, the plugin does not enable, and the console names the file and what is wrong with it. The file is left as it is, so fix or delete it and restart; deleting it starts the server on day 1 of spring.

Nothing advances the date on its own yet; only `/season set` changes it. Whether it follows in-game days or real time is an open question in [MVP.md](MVP.md).
