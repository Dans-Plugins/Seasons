package com.dansplugins.seasons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonCalendarTest {

    /** Spring 3, summer 4, autumn 5, winter 6: distinct lengths so off-by-one errors show. */
    private static SeasonCalendar uneven() {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        days.put(Season.SPRING, 3);
        days.put(Season.SUMMER, 4);
        days.put(Season.AUTUMN, 5);
        days.put(Season.WINTER, 6);
        return new SeasonCalendar(days);
    }

    @Test
    void yearLengthIsSumOfSeasons() {
        assertEquals(18, uneven().yearLength());
    }

    @Test
    void firstDayOfEachSeasonFollowsTheOneBefore() {
        SeasonCalendar calendar = uneven();
        assertEquals(1, calendar.firstDayOf(Season.SPRING));
        assertEquals(4, calendar.firstDayOf(Season.SUMMER));
        assertEquals(8, calendar.firstDayOf(Season.AUTUMN));
        assertEquals(13, calendar.firstDayOf(Season.WINTER));
    }

    @Test
    void dateOfMapsSeasonBoundaries() {
        SeasonCalendar calendar = uneven();
        assertEquals(new CalendarDate(Season.SPRING, 1), calendar.dateOf(1));
        assertEquals(new CalendarDate(Season.SPRING, 3), calendar.dateOf(3));
        assertEquals(new CalendarDate(Season.SUMMER, 1), calendar.dateOf(4));
        assertEquals(new CalendarDate(Season.AUTUMN, 5), calendar.dateOf(12));
        assertEquals(new CalendarDate(Season.WINTER, 1), calendar.dateOf(13));
        assertEquals(new CalendarDate(Season.WINTER, 6), calendar.dateOf(18));
    }

    @Test
    void dateOfRejectsDaysOutsideTheYear() {
        SeasonCalendar calendar = uneven();
        assertThrows(IllegalArgumentException.class, () -> calendar.dateOf(0));
        assertThrows(IllegalArgumentException.class, () -> calendar.dateOf(19));
    }

    @Test
    void dayOfYearInvertsDateOf() {
        SeasonCalendar calendar = uneven();
        for (int day = 1; day <= calendar.yearLength(); day++) {
            assertEquals(day, calendar.dayOfYear(calendar.dateOf(day)));
        }
    }

    @Test
    void dayOfYearRejectsDayBeyondSeasonLength() {
        assertThrows(IllegalArgumentException.class,
                () -> uneven().dayOfYear(new CalendarDate(Season.SPRING, 4)));
    }

    @Test
    void nextDayStaysInSeasonUntilItsLastDay() {
        assertEquals(new CalendarDate(Season.SUMMER, 3), uneven().nextDay(new CalendarDate(Season.SUMMER, 2)));
    }

    @Test
    void nextDayRollsIntoNextSeasonAfterLastDay() {
        assertEquals(new CalendarDate(Season.AUTUMN, 1), uneven().nextDay(new CalendarDate(Season.SUMMER, 4)));
    }

    @Test
    void nextDayRollsFromLastDayOfWinterToStartOfYear() {
        assertEquals(CalendarDate.startOfYear(), uneven().nextDay(new CalendarDate(Season.WINTER, 6)));
    }

    @Test
    void nextDayWalksTheWholeYearInOrder() {
        SeasonCalendar calendar = uneven();
        CalendarDate date = CalendarDate.startOfYear();
        for (int day = 2; day <= calendar.yearLength(); day++) {
            date = calendar.nextDay(date);
            assertEquals(calendar.dateOf(day), date);
        }
    }

    @Test
    void clampKeepsSeasonAndShortensDayToSeasonLength() {
        assertEquals(new CalendarDate(Season.SPRING, 3), uneven().clamp(new CalendarDate(Season.SPRING, 20)));
    }

    @Test
    void clampLeavesDateThatFitsUnchanged() {
        CalendarDate date = new CalendarDate(Season.WINTER, 6);
        assertSame(date, uneven().clamp(date));
    }

    @Test
    void constructorRejectsMissingOrNonPositiveLengths() {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        days.put(Season.SPRING, 1);
        days.put(Season.SUMMER, 1);
        days.put(Season.AUTUMN, 1);
        assertThrows(IllegalArgumentException.class, () -> new SeasonCalendar(days));
        days.put(Season.WINTER, 0);
        assertThrows(IllegalArgumentException.class, () -> new SeasonCalendar(days));
    }

    @Test
    void fromConfigReadsEachSeasonLength() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("calendar.days-per-season.spring", 3);
        config.set("calendar.days-per-season.summer", 4);
        config.set("calendar.days-per-season.autumn", 5);
        config.set("calendar.days-per-season.winter", 6);
        SeasonCalendar calendar = SeasonCalendar.fromConfig(config);
        assertEquals(3, calendar.daysIn(Season.SPRING));
        assertEquals(4, calendar.daysIn(Season.SUMMER));
        assertEquals(5, calendar.daysIn(Season.AUTUMN));
        assertEquals(6, calendar.daysIn(Season.WINTER));
    }

    @Test
    void fromConfigDefaultsMissingSeasons() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("calendar.days-per-season.winter", 10);
        SeasonCalendar calendar = SeasonCalendar.fromConfig(config);
        assertEquals(SeasonCalendar.DEFAULT_DAYS_PER_SEASON, calendar.daysIn(Season.SPRING));
        assertEquals(10, calendar.daysIn(Season.WINTER));
    }

    @Test
    void fromConfigRejectsNonPositiveOrNonNumericLengths() {
        YamlConfiguration zero = new YamlConfiguration();
        zero.set("calendar.days-per-season.summer", 0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SeasonCalendar.fromConfig(zero));
        assertTrue(e.getMessage().contains("calendar.days-per-season.summer"), e.getMessage());

        YamlConfiguration text = new YamlConfiguration();
        text.set("calendar.days-per-season.autumn", "long");
        assertThrows(IllegalArgumentException.class, () -> SeasonCalendar.fromConfig(text));
    }

    @Test
    void bundledConfigMatchesDefaults() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml must be on the classpath");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            SeasonCalendar calendar = SeasonCalendar.fromConfig(config);
            for (Season season : Season.values()) {
                assertTrue(config.contains("calendar.days-per-season." + season.key()), season.key());
                assertEquals(SeasonCalendar.DEFAULT_DAYS_PER_SEASON, calendar.daysIn(season));
            }
        }
    }
}
