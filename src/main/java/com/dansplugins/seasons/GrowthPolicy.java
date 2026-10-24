package com.dansplugins.seasons;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * How fast crops grow in each season, as a multiplier on vanilla growth.
 *
 * <p>A multiplier of {@code 1.0} is vanilla. Below 1, each vanilla growth step happens only with
 * that probability. Above 1, each vanilla growth step always happens and the fractional remainder
 * is the chance of growing extra stages, so {@code 1.5} grows one or two stages per step.
 */
public final class GrowthPolicy {

    /** Configuration path holding one multiplier per season. */
    public static final String MULTIPLIER_PATH = "growth.multiplier";

    private static final Map<Season, Double> DEFAULTS = new EnumMap<>(Season.class);

    static {
        DEFAULTS.put(Season.SPRING, 1.5);
        DEFAULTS.put(Season.SUMMER, 1.0);
        DEFAULTS.put(Season.AUTUMN, 0.75);
        DEFAULTS.put(Season.WINTER, 0.25);
    }

    private final Map<Season, Double> multipliers;

    public GrowthPolicy(Map<Season, Double> multipliers) {
        EnumMap<Season, Double> copy = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            Double multiplier = multipliers.get(season);
            if (multiplier == null) {
                throw new IllegalArgumentException("No growth multiplier given for " + season.key());
            }
            if (!isValid(multiplier)) {
                throw new IllegalArgumentException("The " + season.key()
                        + " growth multiplier must be a number of at least 0, but was " + multiplier);
            }
            copy.put(season, multiplier);
        }
        this.multipliers = copy;
    }

    /**
     * Returns the multiplier a season uses when the configuration does not set one.
     */
    public static double defaultMultiplier(Season season) {
        return DEFAULTS.get(season);
    }

    /**
     * Reads multipliers from {@code growth.multiplier.<season>}. A missing key falls back to
     * {@link #defaultMultiplier(Season)}.
     *
     * @throws IllegalArgumentException if a key is present but not a number of at least 0
     */
    public static GrowthPolicy fromConfig(ConfigurationSection config) {
        Map<Season, Double> multipliers = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            String path = MULTIPLIER_PATH + "." + season.key();
            if (!config.contains(path)) {
                multipliers.put(season, defaultMultiplier(season));
                continue;
            }
            Object value = config.get(path);
            if (!(value instanceof Number) || !isValid(((Number) value).doubleValue())) {
                throw new IllegalArgumentException(
                        path + " must be a number of at least 0, but was '" + value + "'");
            }
            multipliers.put(season, ((Number) value).doubleValue());
        }
        return new GrowthPolicy(multipliers);
    }

    /**
     * Returns the growth multiplier for the given season.
     */
    public double multiplierFor(Season season) {
        return multipliers.get(season);
    }

    /**
     * Decides how many stages one vanilla growth step becomes in the given season.
     *
     * @param roll a uniformly random number in {@code [0, 1)}
     * @return {@code 0} to cancel the step, {@code 1} for vanilla growth, more to grow extra stages
     */
    public int stagesFor(Season season, double roll) {
        double multiplier = multiplierFor(season);
        int whole = (int) Math.floor(multiplier);
        double fraction = multiplier - whole;
        return roll < fraction ? whole + 1 : whole;
    }

    private static boolean isValid(double multiplier) {
        return multiplier >= 0 && !Double.isInfinite(multiplier) && !Double.isNaN(multiplier);
    }
}
