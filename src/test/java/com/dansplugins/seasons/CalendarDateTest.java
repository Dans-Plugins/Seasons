package com.dansplugins.seasons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalendarDateTest {

    @Test
    void startOfYearIsFirstDayOfSpring() {
        assertEquals(new CalendarDate(Season.SPRING, 1), CalendarDate.startOfYear());
    }

    @Test
    void dayOfSeasonBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CalendarDate(Season.SUMMER, 0));
    }

    @Test
    void missingSeasonIsRejected() {
        assertThrows(NullPointerException.class, () -> new CalendarDate(null, 1));
    }

    @Test
    void datesWithSameSeasonAndDayAreEqual() {
        assertEquals(new CalendarDate(Season.AUTUMN, 5), new CalendarDate(Season.AUTUMN, 5));
        assertEquals(new CalendarDate(Season.AUTUMN, 5).hashCode(), new CalendarDate(Season.AUTUMN, 5).hashCode());
        assertNotEquals(new CalendarDate(Season.AUTUMN, 5), new CalendarDate(Season.WINTER, 5));
        assertNotEquals(new CalendarDate(Season.AUTUMN, 5), new CalendarDate(Season.AUTUMN, 6));
    }

    @Test
    void toStringNamesSeasonAndDay() {
        assertEquals("winter day 3", new CalendarDate(Season.WINTER, 3).toString());
    }
}
