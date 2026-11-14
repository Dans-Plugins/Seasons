package com.dansplugins.seasons.sun;

import com.dansplugins.seasons.CalendarDate;
import com.dansplugins.seasons.Season;
import com.dansplugins.seasons.SeasonCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DayLengthTest {

    private static SeasonCalendar defaults() {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            days.put(season, SeasonCalendar.DEFAULT_DAYS_PER_SEASON);
        }
        return new SeasonCalendar(days);
    }

    private static DayLength at(double latitude, AtomicReference<CalendarDate> today) {
        return new DayLength(defaults(), new RealYear(RealYear.springFor(latitude)), latitude, 0.0, today::get);
    }

    @Test
    void londonSummerAndWinterGiveTimescaleStyleRatios() {
        AtomicReference<CalendarDate> today = new AtomicReference<>(new CalendarDate(Season.SUMMER, 1));
        DayLength dayLength = at(51.5, today);
        // RFC 0005: London near the June solstice, f = 0.683, is about 1.37 / 0.63.
        assertEquals(0.683, dayLength.daylightFraction(), 0.005);
        assertEquals(1.37, dayLength.dayRatio(), 0.01);
        assertEquals(0.63, dayLength.nightRatio(), 0.01);
        assertEquals(2.0, dayLength.dayRatio() + dayLength.nightRatio(), 1e-12);

        today.set(new CalendarDate(Season.WINTER, 1));
        assertEquals(7.6 / 24.0, dayLength.daylightFraction(), 0.005);
    }

    @Test
    void theSouthernHemisphereGetsItsSummerInSummerWithTheAutomaticStart() {
        AtomicReference<CalendarDate> today = new AtomicReference<>(new CalendarDate(Season.SUMMER, 1));
        DayLength dayLength = at(-33.9, today);
        double summer = dayLength.daylightFraction();
        today.set(new CalendarDate(Season.WINTER, 1));
        double winter = dayLength.daylightFraction();
        assertEquals(true, summer * 24.0 > 14.0, "summer " + summer * 24.0);
        assertEquals(true, winter * 24.0 < 10.0, "winter " + winter * 24.0);
    }

    @Test
    void polarDayAndNightAreClampedToOneHour() {
        AtomicReference<CalendarDate> today = new AtomicReference<>(new CalendarDate(Season.SUMMER, 1));
        DayLength dayLength = at(69.65, today);
        assertEquals(1.0, dayLength.realDaylightOn(today.get()).daylightFraction());
        assertEquals(DayLength.MAX_DAYLIGHT_FRACTION, dayLength.daylightFraction());
        today.set(new CalendarDate(Season.WINTER, 1));
        assertEquals(0.0, dayLength.realDaylightOn(today.get()).daylightFraction());
        assertEquals(DayLength.MIN_DAYLIGHT_FRACTION, dayLength.daylightFraction());
        // Both ratios stay well above Timescale's smallest accepted ratio of 1/24000.
        assertEquals(1.0 / 12.0, dayLength.dayRatio(), 1e-12);
    }

    @Test
    void clampLeavesOrdinaryDaysAlone() {
        assertEquals(0.5, DayLength.clamp(0.5));
        assertEquals(DayLength.MIN_DAYLIGHT_FRACTION, DayLength.clamp(0.0));
        assertEquals(DayLength.MAX_DAYLIGHT_FRACTION, DayLength.clamp(1.0));
    }

    @Test
    void theCurrentDateIsReadOnEveryCallEvenWhenItReturnsToAnEarlierDay() {
        CalendarDate spring = new CalendarDate(Season.SPRING, 10);
        CalendarDate autumn = new CalendarDate(Season.AUTUMN, 20);
        AtomicReference<CalendarDate> today = new AtomicReference<>(spring);
        DayLength dayLength = at(51.5, today);

        double springFraction = dayLength.daylightFraction();
        assertEquals(dayLength.daylightFractionOn(spring), springFraction);
        today.set(autumn);
        assertEquals(dayLength.daylightFractionOn(autumn), dayLength.daylightFraction());
        assertNotEquals(springFraction, dayLength.daylightFraction());
        // Back to the first date: the value must be for spring again, not the last one computed.
        today.set(spring);
        assertEquals(springFraction, dayLength.daylightFraction());
        assertEquals(2.0 * springFraction, dayLength.dayRatio(), 1e-12);
        assertEquals(2.0 * (1.0 - springFraction), dayLength.nightRatio(), 1e-12);
    }

    @Test
    void theRealDateComesFromTheConfiguredYear() {
        SeasonCalendar calendar = defaults();
        RealYear year = new RealYear(LocalDate.of(RealYear.REFERENCE_YEAR, 3, 1));
        DayLength dayLength = new DayLength(calendar, year, 51.5, 0.0, CalendarDate::startOfYear);
        for (Season season : Season.values()) {
            CalendarDate date = new CalendarDate(season, 7);
            assertEquals(year.dateFor(calendar, date), dayLength.realDateFor(date), season.name());
        }
    }

    @Test
    void theConfiguredLongitudeIsUsedForTheRealDaylight() {
        CalendarDate date = new CalendarDate(Season.SUMMER, 5);
        DayLength denver = new DayLength(defaults(), new RealYear(RealYear.NORTHERN_SPRING), 40.0, -105.1, () -> date);
        LocalDate realDate = denver.realDateFor(date);
        assertEquals(Daylight.on(realDate, 40.0, -105.1).daylightFraction(),
                denver.realDaylightOn(date).daylightFraction());
        assertNotEquals(Daylight.on(realDate, 40.0, 0.0).daylightFraction(),
                denver.realDaylightOn(date).daylightFraction());
    }

    @Test
    void missingCollaboratorsAreRefusedByName() {
        RealYear year = new RealYear(RealYear.NORTHERN_SPRING);
        assertEquals("calendar", assertThrows(NullPointerException.class,
                () -> new DayLength(null, year, 0.0, 0.0, CalendarDate::startOfYear)).getMessage());
        assertEquals("year", assertThrows(NullPointerException.class,
                () -> new DayLength(defaults(), null, 0.0, 0.0, CalendarDate::startOfYear)).getMessage());
        assertEquals("today", assertThrows(NullPointerException.class,
                () -> new DayLength(defaults(), year, 0.0, 0.0, null)).getMessage());
    }

    @Test
    void aPlaceOutOfRangeIsRefusedAtOnce() {
        assertThrows(IllegalArgumentException.class, () -> new DayLength(defaults(),
                new RealYear(RealYear.NORTHERN_SPRING), 95.0, 0.0, () -> CalendarDate.startOfYear()));
    }
}
