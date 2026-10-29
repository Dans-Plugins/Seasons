package com.dansplugins.seasons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonsTest {

    @Test
    void mainClassIsAJavaPlugin() {
        assertTrue(JavaPlugin.class.isAssignableFrom(Seasons.class));
    }

    @Test
    void pluginYmlPointsAtMainClass() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml must be on the classpath");
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: " + Seasons.class.getName()), yml);
            assertTrue(!yml.contains("${version}"), "version placeholder must be expanded");
        }
    }

    @Test
    void pluginYmlRegistersTheSeasonCommandAndItsPermissions() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml must be on the classpath");
            // Permission names contain dots, so paths here are separated by slashes instead.
            YamlConfiguration yml = new YamlConfiguration();
            yml.options().pathSeparator('/');
            yml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals("/season [set <season> [day]]", yml.getString("commands/season/usage"));
            assertEquals("seasons.season", yml.getString("commands/season/permission"));
            assertEquals("true", yml.getString("permissions/seasons.season/default"));
            assertEquals("op", yml.getString("permissions/" + SeasonCommand.SET_PERMISSION + "/default"));
        }
    }
}
