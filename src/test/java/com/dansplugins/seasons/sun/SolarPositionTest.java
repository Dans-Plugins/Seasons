package com.dansplugins.seasons.sun;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolarPositionTest {

    private static double unix(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toEpochSecond();
    }

    @Test
    void noonSunAtFortyNorthFollowsTheSeasons() {
        // strata.h: at 40 degrees north the noon sun stands about 73 degrees high at the June
        // solstice and 27 degrees at the December one. Longitude 0, so noon is close to 12:00 UTC.
        assertEquals(73.4, SolarPosition.at(unix(2026, 6, 21, 12, 2), 40.0, 0.0).elevationDegrees(), 0.2);
        assertEquals(26.6, SolarPosition.at(unix(2026, 12, 21, 11, 58), 40.0, 0.0).elevationDegrees(), 0.2);
    }

    @Test
    void declinationSpansTheTropics() {
        assertEquals(23.44, SolarPosition.at(unix(2026, 6, 21, 12, 0), 0.0, 0.0).declinationDegrees(), 0.02);
        assertEquals(-23.44, SolarPosition.at(unix(2026, 12, 21, 12, 0), 0.0, 0.0).declinationDegrees(), 0.02);
    }

    @Test
    void morningSunIsInTheEastAndBelowNoon() {
        SolarPosition morning = SolarPosition.at(unix(2026, 3, 20, 8, 0), 51.5, 0.0);
        assertEquals(true, morning.hourAngleDegrees() < 0.0);
        assertEquals(true, morning.azimuthDegrees() > 90.0 && morning.azimuthDegrees() < 180.0);
        assertEquals(true, morning.toSunX() > 0.0, "east is +X");
    }

    @Test
    void directionIsAUnitVector() {
        SolarPosition p = SolarPosition.at(unix(2026, 11, 10, 17, 0), 40.6, -105.1);
        assertEquals(1.0, p.toSunX() * p.toSunX() + p.toSunY() * p.toSunY() + p.toSunZ() * p.toSunZ(), 1e-12);
        assertEquals(Math.toDegrees(Math.asin(p.toSunY())), p.elevationDegrees(), 1e-9);
    }

    @Test
    void refusesWhatStrataRefuses() {
        assertThrows(IllegalArgumentException.class, () -> SolarPosition.at(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> SolarPosition.at(0.0, 90.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> SolarPosition.at(0.0, 0.0, -180.5));
        assertThrows(IllegalArgumentException.class, () -> SolarPosition.at(0.0, Double.POSITIVE_INFINITY, 0.0));
    }
}
