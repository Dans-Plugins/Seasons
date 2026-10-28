package com.dansplugins.seasons;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * How likely rain and thunder are in each season.
 *
 * <p>Each chance is the probability, from {@code 0} to {@code 1}, that a new weather period is
 * rainy (or thundery). Vanilla decides when a period ends; this policy decides what the next one
 * is.
 */
public final class WeatherPolicy {

    /** Configuration path holding one weather profile per season. */
    public static final String WEATHER_PATH = "weather";

    static final String RAIN_CHANCE_KEY = "rain-chance";
    static final String THUNDER_CHANCE_KEY = "thunder-chance";

    private static final Map<Season, Double> DEFAULT_RAIN = new EnumMap<>(Season.class);
    private static final Map<Season, Double> DEFAULT_THUNDER = new EnumMap<>(Season.class);

    static {
        DEFAULT_RAIN.put(Season.SPRING, 0.6);
        DEFAULT_RAIN.put(Season.SUMMER, 0.3);
        DEFAULT_RAIN.put(Season.AUTUMN, 0.5);
        DEFAULT_RAIN.put(Season.WINTER, 0.4);
        DEFAULT_THUNDER.put(Season.SPRING, 0.2);
        DEFAULT_THUNDER.put(Season.SUMMER, 0.3);
        DEFAULT_THUNDER.put(Season.AUTUMN, 0.4);
        DEFAULT_THUNDER.put(Season.WINTER, 0.05);
    }

    private final Map<Season, Double> rainChances;
    private final Map<Season, Double> thunderChances;

    public WeatherPolicy(Map<Season, Double> rainChances, Map<Season, Double> thunderChances) {
        this.rainChances = copyValid(rainChances, RAIN_CHANCE_KEY);
        this.thunderChances = copyValid(thunderChances, THUNDER_CHANCE_KEY);
    }

    /**
     * Returns the rain chance a season uses when the configuration does not set one.
     */
    public static double defaultRainChance(Season season) {
        return DEFAULT_RAIN.get(season);
    }

    /**
     * Returns the thunder chance a season uses when the configuration does not set one.
     */
    public static double defaultThunderChance(Season season) {
        return DEFAULT_THUNDER.get(season);
    }

    /**
     * Reads chances from {@code weather.<season>.rain-chance} and
     * {@code weather.<season>.thunder-chance}. A missing key falls back to its default.
     *
     * @throws IllegalArgumentException if a key is present but not a number from 0 to 1
     */
    public static WeatherPolicy fromConfig(ConfigurationSection config) {
        return new WeatherPolicy(readChances(config, RAIN_CHANCE_KEY, DEFAULT_RAIN),
                readChances(config, THUNDER_CHANCE_KEY, DEFAULT_THUNDER));
    }

    /**
     * Returns the chance that a new weather period in the given season is rainy.
     */
    public double rainChanceFor(Season season) {
        return rainChances.get(season);
    }

    /**
     * Returns the chance that a new weather period in the given season is thundery.
     */
    public double thunderChanceFor(Season season) {
        return thunderChances.get(season);
    }

    /**
     * Decides whether the next weather period in the given season is rainy.
     *
     * @param roll a uniformly random number in {@code [0, 1)}
     */
    public boolean rains(Season season, double roll) {
        return roll < rainChanceFor(season);
    }

    /**
     * Decides whether the next thunder period in the given season is thundery.
     *
     * @param roll a uniformly random number in {@code [0, 1)}
     */
    public boolean thunders(Season season, double roll) {
        return roll < thunderChanceFor(season);
    }

    private static Map<Season, Double> readChances(ConfigurationSection config, String key,
                                                   Map<Season, Double> defaults) {
        Map<Season, Double> chances = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            String path = WEATHER_PATH + "." + season.key() + "." + key;
            if (!config.contains(path)) {
                chances.put(season, defaults.get(season));
                continue;
            }
            Object value = config.get(path);
            if (!(value instanceof Number) || !isValid(((Number) value).doubleValue())) {
                throw new IllegalArgumentException(
                        path + " must be a number from 0 to 1, but was '" + value + "'");
            }
            chances.put(season, ((Number) value).doubleValue());
        }
        return chances;
    }

    private static Map<Season, Double> copyValid(Map<Season, Double> chances, String key) {
        EnumMap<Season, Double> copy = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            Double chance = chances.get(season);
            if (chance == null) {
                throw new IllegalArgumentException("No " + key + " given for " + season.key());
            }
            if (!isValid(chance)) {
                throw new IllegalArgumentException("The " + season.key() + " " + key
                        + " must be a number from 0 to 1, but was " + chance);
            }
            copy.put(season, chance);
        }
        return copy;
    }

    private static boolean isValid(double chance) {
        return chance >= 0 && chance <= 1;
    }
}
