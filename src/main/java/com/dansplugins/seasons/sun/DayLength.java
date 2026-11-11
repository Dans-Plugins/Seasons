package com.dansplugins.seasons.sun;

import com.dansplugins.seasons.CalendarDate;
import com.dansplugins.seasons.SeasonCalendar;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The day length Seasons wants for the current in-game day, from a real place and date (RFC 0005).
 *
 * <p>Each Seasons date is looked up as a real date with {@link RealYear}, and the share of that real
 * day the sun is up at the configured place, from {@link Daylight}, becomes the share of the
 * in-game day that is daytime. The total length of the in-game day is not changed: only how it is
 * split between the day half (time of day 0-11999) and the night half (12000-23999).
 *
 * <p>Registered with Bukkit's {@code ServicesManager} while {@code day-length.mode} is
 * {@code real-place}, so a clock plugin such as Timescale can read the target instead of Seasons
 * moving the clock itself. {@link #dayRatio()} and {@link #nightRatio()} are in Timescale's terms: a
 * ratio of {@code r} makes that half of the day {@code r} times as long as vanilla.
 */
public final class DayLength {

    /**
     * The shortest daylight share applied: one real hour, so beyond the polar circles a polar night
     * still has a short day, and Timescale's smallest accepted ratio (1/24000) is never reached.
     */
    public static final double MIN_DAYLIGHT_FRACTION = 1.0 / 24.0;

    /** The longest daylight share applied: 23 real hours, the mirror of {@link #MIN_DAYLIGHT_FRACTION}. */
    public static final double MAX_DAYLIGHT_FRACTION = 23.0 / 24.0;

    private final SeasonCalendar calendar;
    private final RealYear year;
    private final double latitude;
    private final double longitude;
    private final Supplier<CalendarDate> today;

    private CalendarDate cachedDate;
    private double cachedFraction;

    /**
     * @param today the current Seasons date, read on every call
     * @throws IllegalArgumentException for a latitude or longitude out of range
     */
    public DayLength(SeasonCalendar calendar, RealYear year, double latitude, double longitude,
                     Supplier<CalendarDate> today) {
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.year = Objects.requireNonNull(year, "year");
        this.today = Objects.requireNonNull(today, "today");
        // Fails here, at enable, rather than on the first tick.
        Daylight.on(year.springStart(), latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** Clamps a real daylight share to {@link #MIN_DAYLIGHT_FRACTION}..{@link #MAX_DAYLIGHT_FRACTION}. */
    public static double clamp(double daylightFraction) {
        return Math.max(MIN_DAYLIGHT_FRACTION, Math.min(MAX_DAYLIGHT_FRACTION, daylightFraction));
    }

    /** Returns the real date the given Seasons date stands for. */
    public LocalDate realDateFor(CalendarDate date) {
        return year.dateFor(calendar, date);
    }

    /** Returns the real, unclamped daylight on the date the given Seasons date stands for. */
    public Daylight realDaylightOn(CalendarDate date) {
        return Daylight.on(realDateFor(date), latitude, longitude);
    }

    /** Returns the clamped share, 0 to 1, of the given Seasons date that is daytime. */
    public double daylightFractionOn(CalendarDate date) {
        return clamp(realDaylightOn(date).daylightFraction());
    }

    /** Returns the clamped share, 0 to 1, of the current in-game day that is daytime. */
    public synchronized double daylightFraction() {
        CalendarDate date = today.get();
        if (!date.equals(cachedDate)) {
            cachedFraction = daylightFractionOn(date);
            cachedDate = date;
        }
        return cachedFraction;
    }

    /** How many times longer than vanilla the day half of the current in-game day runs. */
    public double dayRatio() {
        return 2.0 * daylightFraction();
    }

    /** How many times longer than vanilla the night half of the current in-game day runs. */
    public double nightRatio() {
        return 2.0 * (1.0 - daylightFraction());
    }
}
