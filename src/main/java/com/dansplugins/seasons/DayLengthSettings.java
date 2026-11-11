package com.dansplugins.seasons;

import com.dansplugins.seasons.sun.RealYear;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code day-length} section of {@code config.yml}: whether the day length follows a real place,
 * and which place and which real date spring starts on.
 */
public final class DayLengthSettings {

    /** Configuration path of the section. */
    public static final String PATH = "day-length";

    /** {@code mode} value that leaves the day length to vanilla (or to Timescale). */
    public static final String MODE_OFF = "off";
    /** {@code mode} value that takes the day length from a real place and date. */
    public static final String MODE_REAL_PLACE = "real-place";
    /** {@code spring-starts} value that picks the equinox of the configured place's hemisphere. */
    public static final String SPRING_STARTS_AUTO = "auto";

    /** Latitude used when none is configured: London. */
    public static final double DEFAULT_LATITUDE = 51.5;
    /** Longitude used when none is configured: London. */
    public static final double DEFAULT_LONGITUDE = -0.13;

    private static final List<String> KEYS = List.of("mode", "latitude", "longitude", "spring-starts");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})-(\\d{1,2})");

    private final boolean enabled;
    private final double latitude;
    private final double longitude;
    private final LocalDate springStart;

    public DayLengthSettings(boolean enabled, double latitude, double longitude, LocalDate springStart) {
        if (!isLatitude(latitude) || !isLongitude(longitude)) {
            throw new IllegalArgumentException("Latitude must be in [-90, 90] and longitude in [-180, 180], but were "
                    + latitude + ", " + longitude);
        }
        this.enabled = enabled;
        this.latitude = latitude;
        this.longitude = longitude;
        this.springStart = springStart != null ? springStart : RealYear.springFor(latitude);
    }

    /**
     * Reads {@code day-length.*}. A missing key uses its default ({@code mode: off}, London,
     * {@code spring-starts: auto}). Every key is checked even while the mode is {@code off}, so a typo
     * is found before the feature is turned on.
     *
     * @throws IllegalArgumentException if a key holds a value it cannot take, or the section is
     *         misshapen; the message names the key
     */
    public static DayLengthSettings fromConfig(ConfigurationSection config) {
        ConfigFile.requireOnlyKeys(config, PATH, KEYS);

        boolean enabled = false;
        String modePath = PATH + ".mode";
        if (config.contains(modePath)) {
            Object mode = config.get(modePath);
            // An unquoted off is read by YAML as the boolean false, so that is taken as 'off' too.
            if (MODE_REAL_PLACE.equals(mode)) {
                enabled = true;
            } else if (!MODE_OFF.equals(mode) && !Boolean.FALSE.equals(mode)) {
                throw new IllegalArgumentException(modePath + " must be '" + MODE_OFF + "' or '"
                        + MODE_REAL_PLACE + "', but was '" + mode + "'");
            }
        }

        double latitude = number(config, PATH + ".latitude", DEFAULT_LATITUDE, -90.0, 90.0);
        double longitude = number(config, PATH + ".longitude", DEFAULT_LONGITUDE, -180.0, 180.0);

        LocalDate springStart = null;
        String springPath = PATH + ".spring-starts";
        if (config.contains(springPath)) {
            Object value = config.get(springPath);
            if (!SPRING_STARTS_AUTO.equals(value)) {
                springStart = monthDay(springPath, value);
            }
        }
        return new DayLengthSettings(enabled, latitude, longitude, springStart);
    }

    /** True when the day length follows the configured place. */
    public boolean isEnabled() {
        return enabled;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /** The real date that spring day 1 stands for, in {@link RealYear#REFERENCE_YEAR}. */
    public LocalDate getSpringStart() {
        return springStart;
    }

    private static double number(ConfigurationSection config, String path, double fallback, double min, double max) {
        if (!config.contains(path)) {
            return fallback;
        }
        Object value = config.get(path);
        if (!(value instanceof Number) || !inRange(((Number) value).doubleValue(), min, max)) {
            throw new IllegalArgumentException(path + " must be a number from " + format(min) + " to "
                    + format(max) + ", but was '" + value + "'");
        }
        return ((Number) value).doubleValue();
    }

    private static LocalDate monthDay(String path, Object value) {
        String rule = path + " must be '" + SPRING_STARTS_AUTO + "' or a month and day such as '03-20', but was '"
                + value + "'";
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(rule);
        }
        Matcher matcher = MONTH_DAY.matcher(((String) value).trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(rule);
        }
        try {
            return LocalDate.of(RealYear.REFERENCE_YEAR, Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(rule + " (" + e.getMessage() + ")", e);
        }
    }

    private static boolean isLatitude(double value) {
        return inRange(value, -90.0, 90.0);
    }

    private static boolean isLongitude(double value) {
        return inRange(value, -180.0, 180.0);
    }

    private static boolean inRange(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
