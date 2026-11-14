package com.dansplugins.seasons.sun;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayLengthClockTest {

    /** A world whose vanilla cycle adds one tick per server tick, and which counts writes. */
    private static final class FakeWorld implements DayLengthClock.WorldTime {
        private final UUID id = UUID.randomUUID();
        long time;
        int writes;

        FakeWorld(long time) {
            this.time = time;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public long getFullTime() {
            return time;
        }

        @Override
        public void setFullTime(long time) {
            this.time = time;
            writes++;
        }

        void vanillaTick() {
            time++;
        }
    }

    private static long serverTicksForOneDay(FakeWorld world, DayLengthClock clock) {
        long start = world.time;
        long ticks = 0;
        clock.run();
        while (world.time < start + DayLengthClock.TICKS_PER_DAY) {
            world.vanillaTick();
            clock.run();
            ticks++;
        }
        return ticks;
    }

    @Test
    void anEvenSplitLeavesTheClockAlone() {
        FakeWorld world = new FakeWorld(0);
        DayLengthClock clock = new DayLengthClock(() -> List.of(world), () -> 0.5, () -> false);
        assertEquals(24000, serverTicksForOneDay(world, clock));
        assertEquals(0, world.writes);
    }

    @Test
    void aLongSummerDayRunsLongAndItsNightShortWithinOneVanillaDay() {
        FakeWorld world = new FakeWorld(0);
        double f = 0.683;
        DayLengthClock clock = new DayLengthClock(() -> List.of(world), () -> f, () -> false);
        clock.run();
        long dayTicks = 0;
        while (world.time < DayLengthClock.NIGHT_START) {
            world.vanillaTick();
            clock.run();
            dayTicks++;
        }
        long nightTicks = 0;
        while (world.time < DayLengthClock.TICKS_PER_DAY) {
            world.vanillaTick();
            clock.run();
            nightTicks++;
        }
        assertEquals(24000 * f, dayTicks, 2.0);
        assertEquals(24000 * (1 - f), nightTicks, 2.0);
        assertEquals(24000, dayTicks + nightTicks, 2.0);
    }

    @Test
    void aJumpFromSleepOrTimeSetIsLeftAloneAndFollowedFromItsNewTime() {
        FakeWorld world = new FakeWorld(13000);
        DayLengthClock clock = new DayLengthClock(() -> List.of(world), () -> 0.25, () -> false);
        clock.run();
        world.time = 24000; // players slept through the night
        clock.run();
        assertEquals(24000, world.time);
        assertEquals(0, world.writes);
        // Picked up again: a short day of f = 0.25 moves two ticks per server tick.
        world.vanillaTick();
        clock.run();
        assertEquals(24002, world.time);
    }

    @Test
    void aStoppedClockIsNotMoved() {
        FakeWorld world = new FakeWorld(500);
        DayLengthClock clock = new DayLengthClock(() -> List.of(world), () -> 0.1, () -> false);
        for (int i = 0; i < 50; i++) {
            clock.run(); // the daylight cycle is off, so vanilla adds nothing
        }
        assertEquals(500, world.time);
        assertEquals(0, world.writes);
    }

    @Test
    void nothingIsTouchedWhileStandingDownForTimescale() {
        FakeWorld world = new FakeWorld(0);
        boolean[] timescale = {true};
        DayLengthClock clock = new DayLengthClock(() -> List.of(world), () -> 0.9, () -> timescale[0]);
        for (int i = 0; i < 100; i++) {
            clock.run();
            world.vanillaTick();
        }
        assertEquals(100, world.time);
        assertEquals(0, world.writes);
        timescale[0] = false;
        clock.run();
        world.vanillaTick();
        clock.run();
        // Taken up again: f = 0.9 moves the day half 1/1.8 of a tick per tick, so this tick is held back.
        assertEquals(100, world.time);
        assertEquals(1, world.writes);
    }

    @Test
    void standingDownForgetsWhereEachWorldWas() {
        FakeWorld world = new FakeWorld(0);
        boolean[] timescale = {false};
        DayLengthClock clock = new DayLengthClock(() -> List.of(world), () -> 0.25, () -> timescale[0]);
        clock.run();
        world.vanillaTick();
        clock.run();
        assertEquals(2, world.time); // f = 0.25: the day half moves two ticks per tick
        assertEquals(1, world.writes);

        timescale[0] = true;
        clock.run();
        timescale[0] = false;
        world.vanillaTick();
        clock.run();
        // One tick past where Seasons last left it, but the clock was Timescale's in between,
        // so this tick only takes the world up again.
        assertEquals(3, world.time);
        assertEquals(1, world.writes);
    }

    @Test
    void aWorldThatLeavesAndComesBackIsTakenUpAfresh() {
        FakeWorld world = new FakeWorld(0);
        List<FakeWorld> worlds = new ArrayList<>(List.of(world));
        DayLengthClock clock = new DayLengthClock(() -> worlds, () -> 0.25, () -> false);
        clock.run();
        world.vanillaTick();
        clock.run();
        assertEquals(2, world.time);
        assertEquals(1, world.writes);

        worlds.clear(); // unloaded
        clock.run();
        worlds.add(world); // loaded again
        world.vanillaTick();
        clock.run();
        assertEquals(3, world.time);
        assertEquals(1, world.writes);
    }

    @Test
    void aNewDaylightShareTakesEffectOnTheNextTick() {
        FakeWorld world = new FakeWorld(0);
        double[] fraction = {0.5};
        DayLengthClock clock = new DayLengthClock(() -> List.of(world), () -> fraction[0], () -> false);
        clock.run();
        world.vanillaTick();
        clock.run();
        assertEquals(1, world.time);
        assertEquals(0, world.writes);

        fraction[0] = 0.25; // the season changed
        world.vanillaTick();
        clock.run();
        assertEquals(3, world.time);
        assertEquals(1, world.writes);
    }

    @Test
    void eachWorldIsShapedOnItsOwn() {
        FakeWorld a = new FakeWorld(0);
        FakeWorld b = new FakeWorld(12000);
        List<FakeWorld> worlds = new ArrayList<>(List.of(a, b));
        DayLengthClock clock = new DayLengthClock(() -> worlds, () -> 0.25, () -> false);
        clock.run();
        for (int i = 0; i < 100; i++) {
            a.vanillaTick();
            b.vanillaTick();
            clock.run();
        }
        // f = 0.25: the day half moves 2 ticks per tick, the night half 2/3.
        assertEquals(200, a.time);
        assertEquals(12000 + 66, b.time, 1);
    }

    @Test
    void dayAndNightHalvesFollowTheVanillaCycle() {
        assertFalse(DayLengthClock.isNight(0));
        assertFalse(DayLengthClock.isNight(11999));
        assertTrue(DayLengthClock.isNight(12000));
        assertTrue(DayLengthClock.isNight(-1));
        assertFalse(DayLengthClock.isNight(-12001));
        // Full world time counts every elapsed day; only the time of day decides the half.
        assertTrue(DayLengthClock.isNight(36000));
        assertTrue(DayLengthClock.isNight(47999));
        assertFalse(DayLengthClock.isNight(48000));
        assertEquals(2.0, DayLengthClock.ticksPerTick(0, 0.25));
        assertEquals(2.0 / 3.0, DayLengthClock.ticksPerTick(12000, 0.25), 1e-12);
    }
}
