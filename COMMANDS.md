# Seasons Commands

## Commands

| Command | Permission | Description |
|---|---|---|
| `/season` | `seasons.season` | Shows the current season, the day within it, and the day of the year. |
| `/season set <season> [day]` | `seasons.set` | Moves the calendar to the given season and day. `day` defaults to 1. |

`/season` replies, for example, `It is autumn, day 7 of 28 (day 63 of 112 in the year).`

`/season set` accepts `spring`, `summer`, `autumn` or `winter` in any case. `day` must be a whole number from 1 to the length of that season in `calendar.days-per-season` (see [CONFIG.md](CONFIG.md)). The new date takes effect at once for crop growth and weather, and is saved to `calendar.yml` straight away. It is meant for testing and for events. Nothing advances the date on its own yet.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `seasons.season` | everyone | Use `/season` to read the current season and date. |
| `seasons.set` | operators | Use `/season set` to change the season and date. |
