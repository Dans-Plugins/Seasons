package com.dansplugins.seasons;

import com.dansplugins.seasons.sun.RealYear;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayLengthSettingsTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    private static String messageFor(String text) throws InvalidConfigurationException {
        YamlConfiguration config = yaml(text);
        return assertThrows(IllegalArgumentException.class, () -> DayLengthSettings.fromConfig(config)).getMessage();
    }

    @Test
    void aMissingSectionIsOffAtLondonWithTheMarchEquinox() throws Exception {
        DayLengthSettings settings = DayLengthSettings.fromConfig(yaml(""));
        assertFalse(settings.isEnabled());
        assertEquals(DayLengthSettings.DEFAULT_LATITUDE, settings.getLatitude());
        assertEquals(DayLengthSettings.DEFAULT_LONGITUDE, settings.getLongitude());
        assertEquals(RealYear.NORTHERN_SPRING, settings.getSpringStart());
    }

    @Test
    void theBundledConfigLeavesDayLengthOff() throws IOException, InvalidConfigurationException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            DayLengthSettings settings = DayLengthSettings.fromConfig(config);
            assertFalse(settings.isEnabled());
            assertEquals(51.5, settings.getLatitude());
            assertEquals(-0.13, settings.getLongitude());
            assertEquals(RealYear.NORTHERN_SPRING, settings.getSpringStart());
        }
    }

    @Test
    void realPlaceIsReadWithItsPlace() throws Exception {
        DayLengthSettings settings = DayLengthSettings.fromConfig(yaml(
                "day-length:\n  mode: real-place\n  latitude: 40\n  longitude: -105.1\n  spring-starts: 03-01\n"));
        assertTrue(settings.isEnabled());
        assertEquals(40.0, settings.getLatitude());
        assertEquals(-105.1, settings.getLongitude());
        assertEquals(LocalDate.of(RealYear.REFERENCE_YEAR, 3, 1), settings.getSpringStart());
    }

    @Test
    void anUnquotedOffIsOff() throws Exception {
        assertFalse(DayLengthSettings.fromConfig(yaml("day-length:\n  mode: off\n")).isEnabled());
    }

    @Test
    void autoFollowsTheHemisphere() throws Exception {
        assertEquals(RealYear.SOUTHERN_SPRING, DayLengthSettings.fromConfig(
                yaml("day-length:\n  latitude: -33.9\n  spring-starts: auto\n")).getSpringStart());
        assertEquals(RealYear.NORTHERN_SPRING, DayLengthSettings.fromConfig(
                yaml("day-length:\n  latitude: 0\n")).getSpringStart());
    }

    @Test
    void aSingleDigitMonthAndDayIsAccepted() throws Exception {
        assertEquals(LocalDate.of(RealYear.REFERENCE_YEAR, 2, 4), DayLengthSettings.fromConfig(
                yaml("day-length:\n  spring-starts: 2-4\n")).getSpringStart());
    }

    @Test
    void anUnknownModeIsRefusedByName() throws Exception {
        assertTrue(messageFor("day-length:\n  mode: real\n").startsWith("day-length.mode must be"));
        assertTrue(messageFor("day-length:\n  mode: true\n").startsWith("day-length.mode must be"));
    }

    @Test
    void aPlaceOutOfRangeIsRefusedByName() throws Exception {
        assertTrue(messageFor("day-length:\n  latitude: 91\n").startsWith("day-length.latitude must be a number from -90 to 90"));
        assertTrue(messageFor("day-length:\n  latitude: north\n").startsWith("day-length.latitude"));
        assertTrue(messageFor("day-length:\n  longitude: -180.5\n").startsWith("day-length.longitude must be a number from -180 to 180"));
        assertTrue(messageFor("day-length:\n  longitude: .nan\n").startsWith("day-length.longitude"));
    }

    @Test
    void theEdgesOfTheRangesAreAccepted() throws Exception {
        DayLengthSettings settings = DayLengthSettings.fromConfig(yaml("day-length:\n  latitude: -90\n  longitude: 180\n"));
        assertEquals(-90.0, settings.getLatitude());
        assertEquals(180.0, settings.getLongitude());
    }

    @Test
    void aBadSpringStartIsRefusedByName() throws Exception {
        for (String value : new String[] {"03/20", "13-01", "02-30", "02-29", "march", "320", "'2026-03-20'"}) {
            assertTrue(messageFor("day-length:\n  spring-starts: " + value + "\n").startsWith("day-length.spring-starts must be"),
                    value);
        }
    }

    @Test
    void anUnknownKeyOrAPlainValueIsRefused() throws Exception {
        assertEquals("day-length.lat is not a setting. Use mode, latitude, longitude, spring-starts.",
                messageFor("day-length:\n  lat: 51.5\n"));
        assertTrue(messageFor("day-length: real-place\n").startsWith("day-length must be a section"));
    }
}
