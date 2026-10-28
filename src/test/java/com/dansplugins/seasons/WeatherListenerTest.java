package com.dansplugins.seasons;

import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests run without a server, so events here carry no world; the listener never reads it.
 */
class WeatherListenerTest {

    /** A listener whose current season has the given chances and every other season the opposite. */
    private static WeatherListener listener(Season season, double rain, double thunder, double roll) {
        Map<Season, Double> rainChances = new EnumMap<>(Season.class);
        Map<Season, Double> thunderChances = new EnumMap<>(Season.class);
        for (Season each : Season.values()) {
            rainChances.put(each, 1.0 - rain);
            thunderChances.put(each, 1.0 - thunder);
        }
        rainChances.put(season, rain);
        thunderChances.put(season, thunder);
        return new WeatherListener(new WeatherPolicy(rainChances, thunderChances), () -> season, () -> roll);
    }

    private static WeatherChangeEvent weather(boolean toRain, WeatherChangeEvent.Cause cause) {
        return new WeatherChangeEvent(null, toRain, cause);
    }

    private static ThunderChangeEvent thunder(boolean toThunder, ThunderChangeEvent.Cause cause) {
        return new ThunderChangeEvent(null, toThunder, cause);
    }

    @Test
    void cancelsNaturalRainTheSeasonDidNotPick() {
        WeatherChangeEvent event = weather(true, WeatherChangeEvent.Cause.NATURAL);

        listener(Season.SUMMER, 0.3, 0.0, 0.5).onWeatherChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void letsNaturalRainTheSeasonPickedThrough() {
        WeatherChangeEvent event = weather(true, WeatherChangeEvent.Cause.NATURAL);

        listener(Season.SUMMER, 0.3, 0.0, 0.1).onWeatherChange(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void keepsItRainingWhenTheSeasonPicksRainAgain() {
        WeatherChangeEvent event = weather(false, WeatherChangeEvent.Cause.NATURAL);

        listener(Season.SPRING, 0.6, 0.0, 0.1).onWeatherChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void letsRainStopWhenTheSeasonPicksClear() {
        WeatherChangeEvent event = weather(false, WeatherChangeEvent.Cause.NATURAL);

        listener(Season.SPRING, 0.6, 0.0, 0.9).onWeatherChange(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void usesTheCurrentSeasonsRainChance() {
        WeatherChangeEvent event = weather(true, WeatherChangeEvent.Cause.NATURAL);

        listener(Season.WINTER, 0.0, 0.0, 0.0).onWeatherChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void leavesCommandAndSleepWeatherAlone() {
        WeatherChangeEvent command = weather(true, WeatherChangeEvent.Cause.COMMAND);
        WeatherChangeEvent sleep = weather(false, WeatherChangeEvent.Cause.SLEEP);

        WeatherListener listener = listener(Season.SPRING, 0.0, 0.0, 0.5);
        listener.onWeatherChange(command);
        listener(Season.SPRING, 1.0, 0.0, 0.5).onWeatherChange(sleep);

        assertFalse(command.isCancelled());
        assertFalse(sleep.isCancelled());
    }

    @Test
    void cancelsNaturalThunderTheSeasonDidNotPick() {
        ThunderChangeEvent event = thunder(true, ThunderChangeEvent.Cause.NATURAL);

        listener(Season.WINTER, 0.0, 0.05, 0.5).onThunderChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void letsNaturalThunderTheSeasonPickedThrough() {
        ThunderChangeEvent event = thunder(true, ThunderChangeEvent.Cause.NATURAL);

        listener(Season.AUTUMN, 0.0, 0.4, 0.1).onThunderChange(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void keepsItThunderingWhenTheSeasonPicksThunderAgain() {
        ThunderChangeEvent event = thunder(false, ThunderChangeEvent.Cause.NATURAL);

        listener(Season.AUTUMN, 0.0, 0.4, 0.1).onThunderChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void leavesCommandThunderAlone() {
        ThunderChangeEvent event = thunder(true, ThunderChangeEvent.Cause.COMMAND);

        listener(Season.WINTER, 0.0, 0.0, 0.5).onThunderChange(event);

        assertFalse(event.isCancelled());
    }
}
