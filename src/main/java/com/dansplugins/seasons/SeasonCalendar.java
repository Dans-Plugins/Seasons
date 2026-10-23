package com.dansplugins.seasons;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * The shape of the year: how many in-game days each season lasts.
 *
 * <p>Days are numbered from 1, both within a season and within the year.
 */
public final class SeasonCalendar {

    /** Days per season used when the configuration does not set one. */
    public static final int DEFAULT_DAYS_PER_SEASON = 28;

    /** Configuration path holding one key per season. */
    public static final String DAYS_PER_SEASON_PATH = "calendar.days-per-season";

    private final Map<Season, Integer> daysPerSeason;

    public SeasonCalendar(Map<Season, Integer> daysPerSeason) {
        EnumMap<Season, Integer> copy = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            Integer days = daysPerSeason.get(season);
            if (days == null) {
                throw new IllegalArgumentException("No length given for " + season.key());
            }
            if (days < 1) {
                throw new IllegalArgumentException(
                        season.key() + " must last at least 1 day, but was " + days);
            }
            copy.put(season, days);
        }
        this.daysPerSeason = copy;
    }

    /**
     * Reads season lengths from {@code calendar.days-per-season.<season>}. A missing key falls back
     * to {@link #DEFAULT_DAYS_PER_SEASON}.
     *
     * @throws IllegalArgumentException if a key is present but not a whole number of at least 1
     */
    public static SeasonCalendar fromConfig(ConfigurationSection config) {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            String path = DAYS_PER_SEASON_PATH + "." + season.key();
            if (!config.contains(path)) {
                days.put(season, DEFAULT_DAYS_PER_SEASON);
                continue;
            }
            if (!config.isInt(path) || config.getInt(path) < 1) {
                throw new IllegalArgumentException(
                        path + " must be a whole number of at least 1, but was '" + config.get(path) + "'");
            }
            days.put(season, config.getInt(path));
        }
        return new SeasonCalendar(days);
    }

    /**
     * Returns how many days the given season lasts.
     */
    public int daysIn(Season season) {
        return daysPerSeason.get(season);
    }

    /**
     * Returns the total number of days in a year.
     */
    public int yearLength() {
        int total = 0;
        for (int days : daysPerSeason.values()) {
            total += days;
        }
        return total;
    }

    /**
     * Returns the day of the year on which the given season starts.
     */
    public int firstDayOf(Season season) {
        int day = 1;
        for (Season earlier : Season.values()) {
            if (earlier == season) {
                return day;
            }
            day += daysIn(earlier);
        }
        throw new IllegalStateException("Unreachable: " + season);
    }

    /**
     * Converts a day of the year to the date it falls on.
     *
     * @throws IllegalArgumentException if the day is outside {@code 1..yearLength()}
     */
    public CalendarDate dateOf(int dayOfYear) {
        if (dayOfYear < 1 || dayOfYear > yearLength()) {
            throw new IllegalArgumentException(
                    "Day of year must be between 1 and " + yearLength() + ", but was " + dayOfYear);
        }
        int remaining = dayOfYear;
        for (Season season : Season.values()) {
            if (remaining <= daysIn(season)) {
                return new CalendarDate(season, remaining);
            }
            remaining -= daysIn(season);
        }
        throw new IllegalStateException("Unreachable: " + dayOfYear);
    }

    /**
     * Returns the day of the year the given date falls on.
     *
     * @throws IllegalArgumentException if the date's day is beyond the length of its season
     */
    public int dayOfYear(CalendarDate date) {
        requireValid(date);
        return firstDayOf(date.getSeason()) + date.getDayOfSeason() - 1;
    }

    /**
     * Returns the date one day after the given one, rolling over into the next season and from the
     * end of winter back to the first day of spring.
     */
    public CalendarDate nextDay(CalendarDate date) {
        requireValid(date);
        if (date.getDayOfSeason() < daysIn(date.getSeason())) {
            return new CalendarDate(date.getSeason(), date.getDayOfSeason() + 1);
        }
        return new CalendarDate(date.getSeason().next(), 1);
    }

    /**
     * Fits a date to this calendar, keeping its season and clamping its day to the season's length.
     * Used when a saved date outlives a configuration change that shortened its season.
     */
    public CalendarDate clamp(CalendarDate date) {
        int days = daysIn(date.getSeason());
        if (date.getDayOfSeason() <= days) {
            return date;
        }
        return new CalendarDate(date.getSeason(), days);
    }

    private void requireValid(CalendarDate date) {
        if (date.getDayOfSeason() > daysIn(date.getSeason())) {
            throw new IllegalArgumentException(date + " is beyond the "
                    + daysIn(date.getSeason()) + "-day length of " + date.getSeason().key());
        }
    }
}
