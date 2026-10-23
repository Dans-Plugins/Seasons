package com.dansplugins.seasons;

import java.util.Objects;

/**
 * A point in the year: a season and a day within it, counted from 1.
 */
public final class CalendarDate {

    private final Season season;
    private final int dayOfSeason;

    public CalendarDate(Season season, int dayOfSeason) {
        this.season = Objects.requireNonNull(season, "season");
        if (dayOfSeason < 1) {
            throw new IllegalArgumentException("Day of season must be at least 1, but was " + dayOfSeason);
        }
        this.dayOfSeason = dayOfSeason;
    }

    /**
     * Returns the first day of spring.
     */
    public static CalendarDate startOfYear() {
        return new CalendarDate(Season.SPRING, 1);
    }

    public Season getSeason() {
        return season;
    }

    public int getDayOfSeason() {
        return dayOfSeason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CalendarDate)) {
            return false;
        }
        CalendarDate other = (CalendarDate) o;
        return season == other.season && dayOfSeason == other.dayOfSeason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(season, dayOfSeason);
    }

    @Override
    public String toString() {
        return season.key() + " day " + dayOfSeason;
    }
}
