package com.dansplugins.seasons.sun;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaylightTest {

    @Test
    void londonSummerDaysAreLongAndWinterDaysShort() {
        // Geometric horizon: 2 * acos(-tan(51.5) tan(23.44)) / 15 = 16.40 h at the June solstice.
        assertEquals(16.40, Daylight.on(LocalDate.of(2026, 6, 21), 51.5, -0.13).daylightHours(), 0.05);
        assertEquals(7.60, Daylight.on(LocalDate.of(2026, 12, 21), 51.5, -0.13).daylightHours(), 0.05);
    }

    @Test
    void equatorDaysStayNearTwelveHours() {
        for (int month = 1; month <= 12; month++) {
            assertEquals(12.0, Daylight.on(LocalDate.of(2026, month, 15), 0.0, 0.0).daylightHours(), 0.01);
        }
    }

    @Test
    void equinoxDaysAreTwelveHoursEverywhereOffThePoles() {
        assertEquals(12.0, Daylight.on(LocalDate.of(2026, 3, 20), 60.0, 10.0).daylightHours(), 0.15);
    }

    @Test
    void arcticSummerHasNoSunsetAndWinterNoSunrise() {
        Daylight june = Daylight.on(LocalDate.of(2026, 6, 21), 69.65, 18.96);
        assertTrue(june.isPolarDay());
        assertTrue(june.sunrise().isEmpty());
        Daylight december = Daylight.on(LocalDate.of(2026, 12, 21), 69.65, 18.96);
        assertTrue(december.isPolarNight());
        assertTrue(december.sunset().isEmpty());
    }

    @Test
    void southernHemisphereIsTheOtherWayRound() {
        assertTrue(Daylight.on(LocalDate.of(2026, 12, 21), -33.9, 151.2).daylightHours() > 14.0);
        assertTrue(Daylight.on(LocalDate.of(2026, 6, 21), -33.9, 151.2).daylightHours() < 10.0);
    }

    @Test
    void sunIsOnTheHorizonAtSunriseAndSunsetAndHighestAtNoon() {
        double lat = 40.6;
        double lon = -105.1;
        Daylight day = Daylight.on(LocalDate.of(2026, 11, 10), lat, lon);
        assertFalse(day.isPolarDay() || day.isPolarNight());
        assertEquals(0.0, elevation(day.sunrise().orElseThrow(), lat, lon), 0.05);
        assertEquals(0.0, elevation(day.sunset().orElseThrow(), lat, lon), 0.05);
        assertEquals(0.0, SolarPosition.at(seconds(day.solarNoon()), lat, lon).hourAngleDegrees(), 0.01);
    }

    private static double elevation(Instant at, double lat, double lon) {
        return SolarPosition.at(seconds(at), lat, lon).elevationDegrees();
    }

    private static double seconds(Instant at) {
        return at.getEpochSecond() + at.getNano() / 1e9;
    }
}
