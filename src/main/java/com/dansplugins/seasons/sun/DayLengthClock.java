package com.dansplugins.seasons.sun;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Splits each in-game day between day and night by a daylight share, by nudging world time once per
 * server tick while vanilla's daylight cycle keeps running.
 *
 * <p>Vanilla advances the clock one tick per server tick. Over the day half (time of day 0-11999)
 * this task holds the clock back, or pushes it on, so the day half advances {@code 1 / (2f)} ticks
 * per server tick and the night half {@code 1 / (2(1 - f))}, for a daylight share {@code f}. The
 * whole cycle keeps its vanilla length of 24000 ticks. Fractions carry over between ticks so the sun
 * moves smoothly.
 *
 * <p>The daylight-cycle gamerule is never read or written, and sleep is left to vanilla. A world
 * whose clock did not move by exactly one tick since this task last saw it (the daylight cycle is
 * off, players slept through the night, {@code /time} was used, or another plugin moved it) is left
 * alone for that tick and picked up again from its new time. While {@code standDown} is true (Timescale
 * is enabled) no world is touched at all, so two plugins never drive one clock.
 *
 * <p>Has no Bukkit dependencies so it can be unit-tested without a server.
 */
public final class DayLengthClock implements Runnable {

    /** Ticks in one full in-game day. */
    public static final long TICKS_PER_DAY = 24000L;
    /** Time of day at which the night half starts. */
    public static final long NIGHT_START = 12000L;

    /** The parts of a world's clock this task reads and writes. */
    public interface WorldTime {

        UUID getId();

        /** Absolute world time, including elapsed days. */
        long getFullTime();

        void setFullTime(long time);
    }

    private static final class State {
        long lastTime;
        double remainder;

        State(long lastTime) {
            this.lastTime = lastTime;
        }
    }

    private final Supplier<? extends Iterable<? extends WorldTime>> worlds;
    private final DoubleSupplier daylightFraction;
    private final BooleanSupplier standDown;
    private final Map<UUID, State> states = new HashMap<>();

    /**
     * @param worlds           the worlds whose clock this task shapes, read every tick
     * @param daylightFraction the share of the current day that is daytime, strictly between 0 and 1
     * @param standDown        true while another plugin owns the clock
     */
    public DayLengthClock(Supplier<? extends Iterable<? extends WorldTime>> worlds, DoubleSupplier daylightFraction,
                          BooleanSupplier standDown) {
        this.worlds = worlds;
        this.daylightFraction = daylightFraction;
        this.standDown = standDown;
    }

    /** Returns whether {@code worldTime} falls in the night half of the cycle. */
    public static boolean isNight(long worldTime) {
        return Math.floorMod(worldTime, TICKS_PER_DAY) >= NIGHT_START;
    }

    /**
     * Returns how many world-time ticks one server tick is worth at the given time of day, for a
     * daylight share {@code fraction}.
     */
    public static double ticksPerTick(long worldTime, double fraction) {
        double share = isNight(worldTime) ? 1.0 - fraction : fraction;
        return 1.0 / (2.0 * share);
    }

    @Override
    public void run() {
        if (standDown.getAsBoolean()) {
            states.clear();
            return;
        }
        double fraction = daylightFraction.getAsDouble();
        Set<UUID> seen = new HashSet<>();
        for (WorldTime world : worlds.get()) {
            UUID id = world.getId();
            seen.add(id);
            long time = world.getFullTime();
            State state = states.get(id);
            if (state == null || time - state.lastTime != 1L) {
                // First sight of this world, or something other than vanilla's one tick moved it.
                states.put(id, new State(time));
                continue;
            }
            state.remainder += ticksPerTick(state.lastTime, fraction);
            long whole = (long) Math.floor(state.remainder);
            state.remainder -= whole;
            long target = state.lastTime + whole;
            if (target != time) {
                world.setFullTime(target);
            }
            state.lastTime = target;
        }
        states.keySet().retainAll(seen);
    }
}
