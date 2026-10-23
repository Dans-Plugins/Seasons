package com.dansplugins.seasons;

import java.util.Locale;

/**
 * The four seasons of the year, in calendar order.
 */
public enum Season {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    /**
     * Returns the season that follows this one, wrapping from winter back to spring.
     */
    public Season next() {
        Season[] seasons = values();
        return seasons[(ordinal() + 1) % seasons.length];
    }

    /**
     * Returns the lower-case name used as a key in configuration and data files.
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks up a season by its key, ignoring case.
     *
     * @return the season, or {@code null} if the key names no season
     */
    public static Season fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (Season season : values()) {
            if (season.key().equalsIgnoreCase(key.trim())) {
                return season;
            }
        }
        return null;
    }
}
