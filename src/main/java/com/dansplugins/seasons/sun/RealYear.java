package com.dansplugins.seasons.sun;

import com.dansplugins.seasons.CalendarDate;
import com.dansplugins.seasons.SeasonCalendar;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Lays the Seasons year over a real one, so a calendar date can be looked up as a real date for
 * {@link Daylight}.
 *
 * <p>The Seasons year, however many days it has, is stretched over one real year that starts on
 * {@code springStart}. Day {@code d} of an {@code n}-day Seasons year lands {@code (d - 0.5) / n}
 * of the way through it, so a 112-day year steps about 3.3 real days per in-game day.
 */
public final class RealYear {

    /**
     * The year that a configured month and day are placed in. Any year would do to within a few
     * minutes of daylight; a common (non-leap) year is used, so 29 February is not a start date.
     */
    public static final int REFERENCE_YEAR = 2026;

    /** The 2026 March equinox, the start of astronomical spring in the northern hemisphere. */
    public static final LocalDate NORTHERN_SPRING = LocalDate.of(REFERENCE_YEAR, 3, 20);

    /** The 2026 September equinox (00:05 UTC on the 23rd), the start of spring in the southern hemisphere. */
    public static final LocalDate SOUTHERN_SPRING = LocalDate.of(REFERENCE_YEAR, 9, 23);

    private static final double TROPICAL_YEAR_DAYS = 365.2422;

    private final LocalDate springStart;

    public RealYear(LocalDate springStart) {
        this.springStart = Objects.requireNonNull(springStart, "springStart");
    }

    /**
     * Returns the equinox that starts spring in the hemisphere of the given latitude: March north of
     * the equator and on it, September south of it.
     */
    public static LocalDate springFor(double latitudeDegrees) {
        return latitudeDegrees < 0.0 ? SOUTHERN_SPRING : NORTHERN_SPRING;
    }

    /** Returns the real date that spring day 1 is stretched from. */
    public LocalDate springStart() {
        return springStart;
    }

    /** Returns the real date that the given Seasons date stands for. */
    public LocalDate dateFor(SeasonCalendar calendar, CalendarDate date) {
        int dayOfYear = calendar.dayOfYear(date);
        double fraction = (dayOfYear - 0.5) / calendar.yearLength();
        return springStart.plusDays((long) Math.floor(fraction * TROPICAL_YEAR_DAYS));
    }
}
