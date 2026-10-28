package com.dansplugins.seasons;

import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests run without a server, so events here carry no world or sender; the listener never reads
 * them. {@code PlayerCommandPreprocessEvent} needs a real player to construct, so the player
 * command path is covered through {@link WeatherListener#isWeatherCommand}.
 */
class WeatherListenerTest {

    /** Tasks the listener asked to run on the next tick, run by the test when it advances a tick. */
    private final List<Runnable> nextTick = new ArrayList<>();

    /** A listener whose current season has the given chances and every other season the opposite. */
    private WeatherListener listener(Season season, double rain, double thunder, double roll) {
        Map<Season, Double> rainChances = new EnumMap<>(Season.class);
        Map<Season, Double> thunderChances = new EnumMap<>(Season.class);
        for (Season each : Season.values()) {
            rainChances.put(each, 1.0 - rain);
            thunderChances.put(each, 1.0 - thunder);
        }
        rainChances.put(season, rain);
        thunderChances.put(season, thunder);
        return new WeatherListener(new WeatherPolicy(rainChances, thunderChances), () -> season, () -> roll,
                nextTick::add);
    }

    private void advanceTick() {
        List<Runnable> tasks = new ArrayList<>(nextTick);
        nextTick.clear();
        tasks.forEach(Runnable::run);
    }

    private static WeatherChangeEvent weather(boolean toRain) {
        return new WeatherChangeEvent(null, toRain);
    }

    private static ThunderChangeEvent thunder(boolean toThunder) {
        return new ThunderChangeEvent(null, toThunder);
    }

    @Test
    void cancelsRainTheSeasonDidNotPick() {
        WeatherChangeEvent event = weather(true);

        listener(Season.SUMMER, 0.3, 0.0, 0.5).onWeatherChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void letsRainTheSeasonPickedThrough() {
        WeatherChangeEvent event = weather(true);

        listener(Season.SUMMER, 0.3, 0.0, 0.1).onWeatherChange(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void keepsItRainingWhenTheSeasonPicksRainAgain() {
        WeatherChangeEvent event = weather(false);

        listener(Season.SPRING, 0.6, 0.0, 0.1).onWeatherChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void letsRainStopWhenTheSeasonPicksClear() {
        WeatherChangeEvent event = weather(false);

        listener(Season.SPRING, 0.6, 0.0, 0.9).onWeatherChange(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void usesTheCurrentSeasonsRainChance() {
        WeatherChangeEvent event = weather(true);

        listener(Season.WINTER, 0.0, 0.0, 0.0).onWeatherChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void cancelsThunderTheSeasonDidNotPick() {
        ThunderChangeEvent event = thunder(true);

        listener(Season.WINTER, 0.0, 0.05, 0.5).onThunderChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void letsThunderTheSeasonPickedThrough() {
        ThunderChangeEvent event = thunder(true);

        listener(Season.AUTUMN, 0.0, 0.4, 0.1).onThunderChange(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void keepsItThunderingWhenTheSeasonPicksThunderAgain() {
        ThunderChangeEvent event = thunder(false);

        listener(Season.AUTUMN, 0.0, 0.4, 0.1).onThunderChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void weatherCommandIsLeftAloneForTheRestOfTheTick() {
        WeatherListener listener = listener(Season.WINTER, 0.0, 0.0, 0.5);
        WeatherChangeEvent rain = weather(true);
        ThunderChangeEvent storm = thunder(true);

        listener.onServerCommand(new ServerCommandEvent(null, "weather thunder"));
        listener.onWeatherChange(rain);
        listener.onThunderChange(storm);

        assertFalse(rain.isCancelled());
        assertFalse(storm.isCancelled());
    }

    @Test
    void exemptionEndsOnTheNextTick() {
        WeatherListener listener = listener(Season.WINTER, 0.0, 0.0, 0.5);
        listener.onServerCommand(new ServerCommandEvent(null, "weather clear"));
        assertEquals(1, nextTick.size());

        advanceTick();
        WeatherChangeEvent rain = weather(true);
        listener.onWeatherChange(rain);

        assertTrue(rain.isCancelled());
    }

    @Test
    void otherCommandsDoNotExempt() {
        WeatherListener listener = listener(Season.WINTER, 0.0, 0.0, 0.5);
        WeatherChangeEvent rain = weather(true);

        listener.onServerCommand(new ServerCommandEvent(null, "time set day"));
        listener.onWeatherChange(rain);

        assertTrue(rain.isCancelled());
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void sleepingThroughTheNightClearsTheWeather() {
        WeatherListener listener = listener(Season.SPRING, 1.0, 1.0, 0.5);
        WeatherChangeEvent clear = weather(false);
        ThunderChangeEvent calm = thunder(false);

        listener.onTimeSkip(new TimeSkipEvent(null, TimeSkipEvent.SkipReason.NIGHT_SKIP, 1000));
        listener.onWeatherChange(clear);
        listener.onThunderChange(calm);

        assertFalse(clear.isCancelled());
        assertFalse(calm.isCancelled());
    }

    @Test
    void timeSetCommandSkipDoesNotExempt() {
        WeatherListener listener = listener(Season.SPRING, 1.0, 1.0, 0.5);
        WeatherChangeEvent clear = weather(false);

        listener.onTimeSkip(new TimeSkipEvent(null, TimeSkipEvent.SkipReason.COMMAND, 1000));
        listener.onWeatherChange(clear);

        assertTrue(clear.isCancelled());
    }

    @Test
    void severalTriggersInOneTickScheduleOneReset() {
        WeatherListener listener = listener(Season.WINTER, 0.0, 0.0, 0.5);

        listener.onServerCommand(new ServerCommandEvent(null, "weather rain"));
        listener.onServerCommand(new ServerCommandEvent(null, "weather rain"));

        assertEquals(1, nextTick.size());
    }

    @Test
    void recognisesTheWeatherCommandInEveryForm() {
        assertTrue(WeatherListener.isWeatherCommand("/weather rain"));
        assertTrue(WeatherListener.isWeatherCommand("weather clear 600"));
        assertTrue(WeatherListener.isWeatherCommand("/minecraft:weather thunder"));
        assertTrue(WeatherListener.isWeatherCommand("/WEATHER rain"));
        assertTrue(WeatherListener.isWeatherCommand("/weather"));
    }

    @Test
    void doesNotMistakeOtherCommandsForWeather() {
        assertFalse(WeatherListener.isWeatherCommand("/time set day"));
        assertFalse(WeatherListener.isWeatherCommand("/weatherman"));
        assertFalse(WeatherListener.isWeatherCommand("/say weather rain"));
        assertFalse(WeatherListener.isWeatherCommand(""));
    }
}
