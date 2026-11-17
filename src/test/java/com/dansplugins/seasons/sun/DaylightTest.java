package com.dansplugins.seasons.sun;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void atThePolesTheSunIsUpAllDayInSummerAndDownAllDayInWinter() {
        // cos(90 degrees) is all but zero, so the poles take their own branch, not the half-day angle.
        Daylight northJune = Daylight.on(LocalDate.of(2026, 6, 21), 90.0, 0.0);
        assertTrue(northJune.isPolarDay());
        assertEquals(24.0, northJune.daylightHours(), 0.0);
        Daylight northDecember = Daylight.on(LocalDate.of(2026, 12, 21), 90.0, 0.0);
        assertTrue(northDecember.isPolarNight());
        assertEquals(0.0, northDecember.daylightHours(), 0.0);

        assertTrue(Daylight.on(LocalDate.of(2026, 6, 21), -90.0, 0.0).isPolarNight());
        assertTrue(Daylight.on(LocalDate.of(2026, 12, 21), -90.0, 0.0).isPolarDay());
    }

    @Test
    void thePolarCircleOnAGeometricHorizonIsNinetyDegreesLessTheSunsDeclination() {
        // At the June solstice the declination is about 23.44, so the circle lies near 66.56 N.
        Daylight justInside = Daylight.on(LocalDate.of(2026, 6, 21), 66.0, 0.0);
        assertFalse(justInside.isPolarDay());
        assertTrue(justInside.daylightHours() > 20.0);
        assertTrue(Daylight.on(LocalDate.of(2026, 6, 21), 67.0, 0.0).isPolarDay());
    }

    @Test
    void anOrdinaryDayIsNeitherPolarDayNorPolarNightAndHasBothSunriseAndSunset() {
        Daylight day = Daylight.on(LocalDate.of(2026, 6, 21), 51.5, -0.13);
        assertFalse(day.isPolarDay());
        assertFalse(day.isPolarNight());
        assertTrue(day.sunrise().isPresent());
        assertTrue(day.sunset().isPresent());
        assertEquals(day.daylightFraction() * 24.0, day.daylightHours(), 1e-12);
    }

    @Test
    void aPolarDayHasNoSunsetAndAPolarNightNoSunrise() {
        Daylight june = Daylight.on(LocalDate.of(2026, 6, 21), 69.65, 18.96);
        assertTrue(june.sunset().isEmpty());
        assertEquals(24.0, june.daylightHours(), 0.0);
        Daylight december = Daylight.on(LocalDate.of(2026, 12, 21), 69.65, 18.96);
        assertTrue(december.sunrise().isEmpty());
        assertEquals(0.0, december.daylightHours(), 0.0);
    }

    @Test
    void sunriseAndSunsetLieHalfTheDaylightEitherSideOfSolarNoon() {
        Daylight day = Daylight.on(LocalDate.of(2026, 6, 21), 51.5, -0.13);
        double noon = seconds(day.solarNoon());
        double halfDaylight = day.daylightHours() * 3600.0 / 2.0;
        assertEquals(noon - halfDaylight, seconds(day.sunrise().orElseThrow()), 0.001);
        assertEquals(noon + halfDaylight, seconds(day.sunset().orElseThrow()), 0.001);
    }

    @Test
    void solarNoonFollowsLongitudeAndTheEquationOfTime() {
        // In early November the sun runs about 16 minutes ahead of mean time, so noon at Greenwich
        // comes near 11:44 UTC; in mid-February it runs about 14 minutes behind, so noon comes near 12:14.
        assertEquals(Instant.parse("2026-11-03T11:43:36Z").getEpochSecond(),
                Daylight.on(LocalDate.of(2026, 11, 3), 0.0, 0.0).solarNoon().getEpochSecond(), 60.0);
        assertEquals(Instant.parse("2026-02-11T12:14:12Z").getEpochSecond(),
                Daylight.on(LocalDate.of(2026, 2, 11), 0.0, 0.0).solarNoon().getEpochSecond(), 60.0);
        // Each 15 degrees east brings noon an hour earlier; 90 E is six hours ahead of Greenwich.
        Instant greenwich = Daylight.on(LocalDate.of(2026, 11, 3), 0.0, 0.0).solarNoon();
        Instant ninetyEast = Daylight.on(LocalDate.of(2026, 11, 3), 0.0, 90.0).solarNoon();
        assertEquals(6.0 * 3600.0, greenwich.getEpochSecond() - ninetyEast.getEpochSecond(), 5.0);
    }

    @Test
    void solarNoonFallsOnTheGivenUtcDate() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        assertEquals(date, Daylight.on(date, 0.0, 0.0).solarNoon().atZone(ZoneOffset.UTC).toLocalDate());
        assertEquals(date, Daylight.on(date, 35.7, 139.7).solarNoon().atZone(ZoneOffset.UTC).toLocalDate());
        assertEquals(date, Daylight.on(date, 40.7, -74.0).solarNoon().atZone(ZoneOffset.UTC).toLocalDate());
    }

    @Test
    void aLatitudeOrLongitudeOutOfRangeIsRefused() {
        LocalDate date = LocalDate.of(2026, 6, 21);
        assertThrows(IllegalArgumentException.class, () -> Daylight.on(date, 90.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Daylight.on(date, -90.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Daylight.on(date, 0.0, 180.5));
        assertThrows(IllegalArgumentException.class, () -> Daylight.on(date, 0.0, -180.5));
        assertThrows(IllegalArgumentException.class, () -> Daylight.on(date, Double.NaN, 0.0));
    }

    private static double elevation(Instant at, double lat, double lon) {
        return SolarPosition.at(seconds(at), lat, lon).elevationDegrees();
    }

    private static double seconds(Instant at) {
        return at.getEpochSecond() + at.getNano() / 1e9;
    }
}
