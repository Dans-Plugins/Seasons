package com.dansplugins.seasons.sun;

import com.dansplugins.seasons.CalendarDate;
import com.dansplugins.seasons.Season;
import com.dansplugins.seasons.SeasonCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void springForPicksMarchOnAndNorthOfTheEquatorAndSeptemberSouthOfIt() {
        assertEquals(RealYear.NORTHERN_SPRING, RealYear.springFor(0.0));
        assertEquals(RealYear.NORTHERN_SPRING, RealYear.springFor(51.5));
        assertEquals(RealYear.NORTHERN_SPRING, RealYear.springFor(90.0));
        assertEquals(RealYear.SOUTHERN_SPRING, RealYear.springFor(-0.01));
        assertEquals(RealYear.SOUTHERN_SPRING, RealYear.springFor(-33.9));
        assertEquals(RealYear.SOUTHERN_SPRING, RealYear.springFor(-90.0));
    }

    @Test
    void keepsTheGivenSpringStartAndRefusesNone() {
        LocalDate start = LocalDate.of(RealYear.REFERENCE_YEAR, 2, 4);
        assertEquals(start, new RealYear(start).springStart());
        assertThrows(NullPointerException.class, () -> new RealYear(null));
    }

    @Test
    void everyDayOfTheYearStandsForALaterDateWithinOneRealYear() {
        RealYear year = new RealYear(RealYear.NORTHERN_SPRING);
        SeasonCalendar calendar = defaults();
        CalendarDate date = CalendarDate.startOfYear();
        LocalDate previous = null;
        for (int day = 1; day <= calendar.yearLength(); day++) {
            LocalDate real = year.dateFor(calendar, date);
            assertFalse(real.isBefore(RealYear.NORTHERN_SPRING), date + " -> " + real);
            assertTrue(real.isBefore(RealYear.NORTHERN_SPRING.plusYears(1)), date + " -> " + real);
            if (previous != null) {
                long step = ChronoUnit.DAYS.between(previous, real);
                // 365.2422 / 112 is about 3.26, so each in-game day moves 3 or 4 real days.
                assertTrue(step == 3 || step == 4, date + " stepped " + step + " days");
            }
            previous = real;
            date = calendar.nextDay(date);
        }
    }

    @Test
    void unevenSeasonsShareTheRealYearOutByDayOfYear() {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        days.put(Season.SPRING, 10);
        days.put(Season.SUMMER, 30);
        days.put(Season.AUTUMN, 30);
        days.put(Season.WINTER, 30);
        SeasonCalendar calendar = new SeasonCalendar(days);
        RealYear year = new RealYear(RealYear.NORTHERN_SPRING);
        // Day d of 100 lands floor((d - 0.5) / 100 * 365.2422) days after 20 March.
        assertEquals(LocalDate.of(2026, 3, 21), year.dateFor(calendar, new CalendarDate(Season.SPRING, 1)));
        assertEquals(LocalDate.of(2026, 4, 27), year.dateFor(calendar, new CalendarDate(Season.SUMMER, 1)));
        assertEquals(LocalDate.of(2027, 3, 18), year.dateFor(calendar, new CalendarDate(Season.WINTER, 30)));
    }

    @Test
    void aOneDaySeasonStandsForTheMiddleOfItsQuarter() {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            days.put(season, 1);
        }
        SeasonCalendar calendar = new SeasonCalendar(days);
        RealYear year = new RealYear(RealYear.NORTHERN_SPRING);
        // 0.5 / 4 of 365.2422 days is 45.65, so spring's only day is 45 days after 20 March.
        assertEquals(LocalDate.of(2026, 5, 4), year.dateFor(calendar, new CalendarDate(Season.SPRING, 1)));
    }

    @Test
    void aLateSpringStartCarriesTheYearIntoTheNextRealYear() {
        RealYear year = new RealYear(LocalDate.of(RealYear.REFERENCE_YEAR, 12, 1));
        SeasonCalendar calendar = defaults();
        // Summer day 1 is 92 days in, as it is from 20 March (to 20 June).
        assertEquals(LocalDate.of(2027, 3, 3), year.dateFor(calendar, new CalendarDate(Season.SUMMER, 1)));
    }

    private static double hoursAt(RealYear year, SeasonCalendar calendar, CalendarDate date) {
        return Daylight.on(year.dateFor(calendar, date), 51.5, 0.0).daylightHours();
    }
}
