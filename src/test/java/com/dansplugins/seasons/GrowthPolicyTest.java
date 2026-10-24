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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthPolicyTest {

    private static GrowthPolicy policy(double spring, double summer, double autumn, double winter) {
        Map<Season, Double> multipliers = new EnumMap<>(Season.class);
        multipliers.put(Season.SPRING, spring);
        multipliers.put(Season.SUMMER, summer);
        multipliers.put(Season.AUTUMN, autumn);
        multipliers.put(Season.WINTER, winter);
        return new GrowthPolicy(multipliers);
    }

    @Test
    void vanillaMultiplierAlwaysGrowsOneStage() {
        GrowthPolicy policy = policy(1.0, 1.0, 1.0, 1.0);
        assertEquals(1, policy.stagesFor(Season.SPRING, 0.0));
        assertEquals(1, policy.stagesFor(Season.SPRING, 0.999));
    }

    @Test
    void zeroMultiplierAlwaysCancels() {
        GrowthPolicy policy = policy(1.0, 1.0, 1.0, 0.0);
        assertEquals(0, policy.stagesFor(Season.WINTER, 0.0));
        assertEquals(0, policy.stagesFor(Season.WINTER, 0.999));
    }

    @Test
    void fractionalMultiplierBelowOneLetsThatShareThrough() {
        GrowthPolicy policy = policy(1.0, 1.0, 1.0, 0.25);
        assertEquals(1, policy.stagesFor(Season.WINTER, 0.0));
        assertEquals(1, policy.stagesFor(Season.WINTER, 0.2499));
        assertEquals(0, policy.stagesFor(Season.WINTER, 0.25));
        assertEquals(0, policy.stagesFor(Season.WINTER, 0.999));
    }

    @Test
    void fractionalMultiplierAboveOneSometimesGrowsAnExtraStage() {
        GrowthPolicy policy = policy(1.5, 1.0, 1.0, 1.0);
        assertEquals(2, policy.stagesFor(Season.SPRING, 0.0));
        assertEquals(2, policy.stagesFor(Season.SPRING, 0.4999));
        assertEquals(1, policy.stagesFor(Season.SPRING, 0.5));
        assertEquals(1, policy.stagesFor(Season.SPRING, 0.999));
    }

    @Test
    void wholeMultiplierGrowsThatManyStages() {
        GrowthPolicy policy = policy(3.0, 1.0, 1.0, 1.0);
        assertEquals(3, policy.stagesFor(Season.SPRING, 0.0));
        assertEquals(3, policy.stagesFor(Season.SPRING, 0.999));
    }

    @Test
    void eachSeasonUsesItsOwnMultiplier() {
        GrowthPolicy policy = policy(2.0, 1.0, 0.5, 0.0);
        assertEquals(2, policy.stagesFor(Season.SPRING, 0.75));
        assertEquals(1, policy.stagesFor(Season.SUMMER, 0.75));
        assertEquals(0, policy.stagesFor(Season.AUTUMN, 0.75));
        assertEquals(0, policy.stagesFor(Season.WINTER, 0.0));
    }

    @Test
    void constructorRejectsMissingSeason() {
        Map<Season, Double> multipliers = new EnumMap<>(Season.class);
        multipliers.put(Season.SPRING, 1.0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new GrowthPolicy(multipliers));
        assertTrue(e.getMessage().contains("summer"), e.getMessage());
    }

    @Test
    void constructorRejectsNegativeAndNonFiniteMultipliers() {
        assertThrows(IllegalArgumentException.class, () -> policy(-0.1, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> policy(Double.NaN, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> policy(Double.POSITIVE_INFINITY, 1.0, 1.0, 1.0));
    }

    @Test
    void emptyConfigUsesDefaults() {
        GrowthPolicy policy = GrowthPolicy.fromConfig(new YamlConfiguration());
        for (Season season : Season.values()) {
            assertEquals(GrowthPolicy.defaultMultiplier(season), policy.multiplierFor(season));
        }
    }

    @Test
    void defaultsMakeSpringFastAndWinterSlow() {
        assertTrue(GrowthPolicy.defaultMultiplier(Season.SPRING) > 1.0);
        assertTrue(GrowthPolicy.defaultMultiplier(Season.WINTER) < 1.0);
    }

    @Test
    void configReadsIntegersAndDecimals() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("growth.multiplier.spring", 2);
        config.set("growth.multiplier.winter", 0.1);
        GrowthPolicy policy = GrowthPolicy.fromConfig(config);
        assertEquals(2.0, policy.multiplierFor(Season.SPRING));
        assertEquals(0.1, policy.multiplierFor(Season.WINTER));
        assertEquals(GrowthPolicy.defaultMultiplier(Season.SUMMER), policy.multiplierFor(Season.SUMMER));
    }

    @Test
    void configRejectsNegativeValueNamingTheKey() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("growth.multiplier.autumn", -1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GrowthPolicy.fromConfig(config));
        assertTrue(e.getMessage().contains("growth.multiplier.autumn"), e.getMessage());
    }

    @Test
    void configRejectsTextNamingTheKey() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("growth.multiplier.winter", "slow");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GrowthPolicy.fromConfig(config));
        assertTrue(e.getMessage().contains("growth.multiplier.winter"), e.getMessage());
    }

    @Test
    void bundledConfigMatchesDefaults() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml must be on the classpath");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            GrowthPolicy policy = GrowthPolicy.fromConfig(config);
            for (Season season : Season.values()) {
                String path = GrowthPolicy.MULTIPLIER_PATH + "." + season.key();
                assertTrue(config.contains(path), path + " missing from config.yml");
                assertEquals(GrowthPolicy.defaultMultiplier(season), policy.multiplierFor(season));
            }
        }
    }
}
