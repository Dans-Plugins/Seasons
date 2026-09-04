# Seasons — MVP

## Problem

A long-running survival server has no elapsed time. Day 4 and day 400 look identical. Players asked for seasons because they want the world to mark the passage of a campaign, not just a night.

## Target environment

Spigot, Minecraft 26.1, **vanilla clients**. No mod or resource pack may be required to see the effect. That constraint decides most of the non-goals below.

## The smallest shippable slice

### v1 does

1. **A calendar.** A year of four seasons, each a configurable number of in-game days. The current season and day-of-year persist across restarts.
2. **Weather bias.** Each season carries a weather profile — chance of rain, chance of thunder, and whether precipitation falls as snow. Winter makes rain fall as snow wherever the biome allows it.
3. **Growth rates.** A per-season multiplier on crop growth. Winter is slow; spring is fast. Implemented by intercepting growth events, not by touching `randomTickSpeed`, so it does not also change ore-block behaviour or fire spread.
4. **Day length.** A per-season day length, so summer days run long and winter days run short.
5. **Commands.** `/season` to read the current season and date. An admin command to set the season, for testing and for events.
6. **A season-change announcement**, configurable and disableable.

### v1 does not

- **Recolour leaves, grass, or water.** Foliage colour is computed client-side from the biome. A server cannot change it without either swapping biomes underneath players — which corrupts the map's identity and fights with worldgen — or shipping a resource pack. Both are out of scope for a first release.
- **Simulate temperature per biome.** No freezing damage, no per-block temperature model.
- **Accumulate or melt snow layers.** Snow depth that builds through winter and thaws in spring is the obvious v2 feature and the obvious way to make v1 late.
- **Support per-world seasons.** One calendar per server.
- **Provide a calendar GUI or a scoreboard.** A command and a chat announcement are enough to prove the idea.
- **Touch mob spawning.**

## Why these non-goals

The visual half of "seasons" is the half a Spigot plugin cannot deliver. Being explicit about that up front is better than shipping something a player calls broken because the trees stayed green. If the visual side matters, it is a resource-pack decision made alongside the [Conquest pack question](https://github.com/Dans-Plugins/Conquest-Recipes) — not something this plugin can solve.

## Open questions

- Should day length be implemented here at all, given [Timescale](https://github.com/Dans-Plugins/Timescale) exists to scale time? Two plugins both driving `doDaylightCycle` will fight. Likely resolution: Seasons exposes a target day length and defers to Timescale when it is present, or the feature moves to Timescale entirely and Seasons only publishes the season.
- Does the calendar advance on real time or on in-game days? In-game days is the intuitive answer and couples this to whatever is scaling time.
- Does a season change fire an event other plugins can listen for? Cheap to add, and the thing an economy or a merchant plugin would want.

## Dependencies

None required. Soft integration with [Timescale](https://github.com/Dans-Plugins/Timescale) if the day-length question resolves that way.
