package com.dansplugins.seasons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherPolicyTest {

    private static Map<Season, Double> chances(double spring, double summer, double autumn, double winter) {
        Map<Season, Double> chances = new EnumMap<>(Season.class);
        chances.put(Season.SPRING, spring);
        chances.put(Season.SUMMER, summer);
        chances.put(Season.AUTUMN, autumn);
        chances.put(Season.WINTER, winter);
        return chances;
    }

    private static Map<Season, Double> all(double chance) {
        return chances(chance, chance, chance, chance);
    }

    @Test
    void rainsBelowTheChanceAndNotAtOrAboveIt() {
        WeatherPolicy policy = new WeatherPolicy(chances(1.0, 1.0, 1.0, 0.4), all(0.0));
        assertTrue(policy.rains(Season.WINTER, 0.0));
        assertTrue(policy.rains(Season.WINTER, 0.3999));
        assertFalse(policy.rains(Season.WINTER, 0.4));
        assertFalse(policy.rains(Season.WINTER, 0.999));
    }

    @Test
    void thundersBelowTheChanceAndNotAtOrAboveIt() {
        WeatherPolicy policy = new WeatherPolicy(all(0.0), chances(1.0, 1.0, 0.25, 1.0));
        assertTrue(policy.thunders(Season.AUTUMN, 0.2499));
        assertFalse(policy.thunders(Season.AUTUMN, 0.25));
    }

    @Test
    void zeroNeverAndOneAlways() {
        WeatherPolicy policy = new WeatherPolicy(chances(0.0, 1.0, 0.5, 0.5), chances(1.0, 0.0, 0.5, 0.5));
        assertFalse(policy.rains(Season.SPRING, 0.0));
        assertTrue(policy.rains(Season.SUMMER, 0.999));
        assertTrue(policy.thunders(Season.SPRING, 0.999));
        assertFalse(policy.thunders(Season.SUMMER, 0.0));
    }

    @Test
    void rainAndThunderAreIndependent() {
        WeatherPolicy policy = new WeatherPolicy(all(0.9), all(0.1));
        assertTrue(policy.rains(Season.SPRING, 0.5));
        assertFalse(policy.thunders(Season.SPRING, 0.5));
    }

    @Test
    void eachSeasonUsesItsOwnChance() {
        WeatherPolicy policy = new WeatherPolicy(chances(0.8, 0.2, 0.6, 0.0), all(0.0));
        assertTrue(policy.rains(Season.SPRING, 0.5));
        assertFalse(policy.rains(Season.SUMMER, 0.5));
        assertTrue(policy.rains(Season.AUTUMN, 0.5));
        assertFalse(policy.rains(Season.WINTER, 0.0));
    }

    @Test
    void constructorRejectsMissingSeason() {
        Map<Season, Double> rain = new EnumMap<>(Season.class);
        rain.put(Season.SPRING, 0.5);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WeatherPolicy(rain, all(0.5)));
        assertTrue(e.getMessage().contains("summer"), e.getMessage());
        assertTrue(e.getMessage().contains("rain-chance"), e.getMessage());
    }

    @Test
    void constructorRejectsChancesOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherPolicy(chances(-0.1, 0.5, 0.5, 0.5), all(0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherPolicy(all(0.5), chances(0.5, 1.1, 0.5, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherPolicy(chances(Double.NaN, 0.5, 0.5, 0.5), all(0.5)));
    }

    @Test
    void emptyConfigUsesDefaults() {
        WeatherPolicy policy = WeatherPolicy.fromConfig(new YamlConfiguration());
        for (Season season : Season.values()) {
            assertEquals(WeatherPolicy.defaultRainChance(season), policy.rainChanceFor(season));
            assertEquals(WeatherPolicy.defaultThunderChance(season), policy.thunderChanceFor(season));
        }
    }

    @Test
    void configReadsIntegersAndDecimals() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("weather.summer.rain-chance", 0);
        config.set("weather.autumn.thunder-chance", 0.75);
        WeatherPolicy policy = WeatherPolicy.fromConfig(config);
        assertEquals(0.0, policy.rainChanceFor(Season.SUMMER));
        assertEquals(0.75, policy.thunderChanceFor(Season.AUTUMN));
        assertEquals(WeatherPolicy.defaultThunderChance(Season.SUMMER), policy.thunderChanceFor(Season.SUMMER));
        assertEquals(WeatherPolicy.defaultRainChance(Season.AUTUMN), policy.rainChanceFor(Season.AUTUMN));
    }

    @Test
    void configRejectsValueAboveOneNamingTheKey() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("weather.winter.rain-chance", 2);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> WeatherPolicy.fromConfig(config));
        assertTrue(e.getMessage().contains("weather.winter.rain-chance"), e.getMessage());
    }

    @Test
    void configRejectsTextNamingTheKey() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("weather.spring.thunder-chance", "often");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> WeatherPolicy.fromConfig(config));
        assertTrue(e.getMessage().contains("weather.spring.thunder-chance"), e.getMessage());
    }

    @Test
    void bundledConfigMatchesDefaults() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml must be on the classpath");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            WeatherPolicy policy = WeatherPolicy.fromConfig(config);
            for (Season season : Season.values()) {
                String base = WeatherPolicy.WEATHER_PATH + "." + season.key() + ".";
                assertTrue(config.contains(base + "rain-chance"), base + "rain-chance missing from config.yml");
                assertTrue(config.contains(base + "thunder-chance"),
                        base + "thunder-chance missing from config.yml");
                assertEquals(WeatherPolicy.defaultRainChance(season), policy.rainChanceFor(season));
                assertEquals(WeatherPolicy.defaultThunderChance(season), policy.thunderChanceFor(season));
            }
        }
    }
}
