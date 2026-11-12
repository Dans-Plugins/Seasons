package com.dansplugins.seasons.sun;

import com.dansplugins.seasons.CalendarDate;
import com.dansplugins.seasons.Season;
import com.dansplugins.seasons.SeasonCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealYearTest {

    private static SeasonCalendar defaults() {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            days.put(season, SeasonCalendar.DEFAULT_DAYS_PER_SEASON);
        }
        return new SeasonCalendar(days);
    }

    @Test
    void eachSeasonStartsNearItsAstronomicalStart() {
        RealYear year = new RealYear(RealYear.NORTHERN_SPRING);
        SeasonCalendar calendar = defaults();
        assertEquals(LocalDate.of(2026, 3, 21), year.dateFor(calendar, new CalendarDate(Season.SPRING, 1)));
        assertEquals(LocalDate.of(2026, 6, 20), year.dateFor(calendar, new CalendarDate(Season.SUMMER, 1)));
        assertEquals(LocalDate.of(2026, 9, 20), year.dateFor(calendar, new CalendarDate(Season.AUTUMN, 1)));
        assertEquals(LocalDate.of(2026, 12, 20), year.dateFor(calendar, new CalendarDate(Season.WINTER, 1)));
        assertEquals(LocalDate.of(2027, 3, 18), year.dateFor(calendar, new CalendarDate(Season.WINTER, 28)));
    }

    @Test
    void summerDaysRunLongerThanWinterOnesAtAConfiguredPlace() {
        RealYear year = new RealYear(RealYear.NORTHERN_SPRING);
        SeasonCalendar calendar = defaults();
        // Anchored at the equinox, summer starts at the solstice, so its first day is the longest.
        double summerStart = hoursAt(year, calendar, new CalendarDate(Season.SUMMER, 1));
        double winterStart = hoursAt(year, calendar, new CalendarDate(Season.WINTER, 1));
        assertEquals(16.4, summerStart, 0.1);
        assertEquals(7.6, winterStart, 0.1);
        double summerMiddle = hoursAt(year, calendar, new CalendarDate(Season.SUMMER, 14));
        double winterMiddle = hoursAt(year, calendar, new CalendarDate(Season.WINTER, 14));
        assertTrue(summerMiddle > 15.0 && winterMiddle < 9.0, summerMiddle + " / " + winterMiddle);
    }

    private static double hoursAt(RealYear year, SeasonCalendar calendar, CalendarDate date) {
        return Daylight.on(year.dateFor(calendar, date), 51.5, 0.0).daylightHours();
    }
}
