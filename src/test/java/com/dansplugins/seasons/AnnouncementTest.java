package com.dansplugins.seasons;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementTest {

    private static final CalendarDate AUTUMN_7 = new CalendarDate(Season.AUTUMN, 7);
    private static final CalendarDate AUTUMN_20 = new CalendarDate(Season.AUTUMN, 20);
    private static final CalendarDate WINTER_1 = new CalendarDate(Season.WINTER, 1);

    @Test
    void changeOfSeasonReplacesThePlaceholder() {
        Announcement announcement = new Announcement(true, "Welcome to {season}!");
        assertEquals("Welcome to winter!", announcement.messageFor(AUTUMN_7, WINTER_1));
    }

    @Test
    void colourCodesAreTranslated() {
        Announcement announcement = new Announcement(true, "&6It is now {season}.");
        assertEquals(ChatColor.GOLD + "It is now winter.", announcement.messageFor(AUTUMN_7, WINTER_1));
    }

    @Test
    void sameSeasonAnnouncesNothing() {
        Announcement announcement = new Announcement(true, "It is now {season}.");
        assertNull(announcement.messageFor(AUTUMN_7, AUTUMN_20));
        assertNull(announcement.messageFor(AUTUMN_7, AUTUMN_7));
    }

    @Test
    void disabledAnnouncesNothing() {
        Announcement announcement = new Announcement(false, "It is now {season}.");
        assertNull(announcement.messageFor(AUTUMN_7, WINTER_1));
    }

    @Test
    void blankMessageAnnouncesNothing() {
        assertNull(new Announcement(true, "").messageFor(AUTUMN_7, WINTER_1));
        assertNull(new Announcement(true, "   ").messageFor(AUTUMN_7, WINTER_1));
    }

    @Test
    void noPreviousDateAnnouncesTheNewSeason() {
        Announcement announcement = new Announcement(true, "{season}");
        assertEquals("winter", announcement.messageFor(null, WINTER_1));
    }

    @Test
    void emptyConfigUsesDefaults() {
        Announcement announcement = Announcement.fromConfig(new YamlConfiguration());
        assertEquals(ChatColor.translateAlternateColorCodes('&',
                        Announcement.DEFAULT_MESSAGE.replace(Announcement.SEASON_PLACEHOLDER, "winter")),
                announcement.messageFor(AUTUMN_7, WINTER_1));
    }

    @Test
    void configCanDisableTheAnnouncement() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(Announcement.ENABLED_PATH, false);
        assertNull(Announcement.fromConfig(config).messageFor(AUTUMN_7, WINTER_1));
    }

    @Test
    void configSetsTheMessage() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(Announcement.MESSAGE_PATH, "Season: {season}");
        assertEquals("Season: winter", Announcement.fromConfig(config).messageFor(AUTUMN_7, WINTER_1));
    }

    @Test
    void configRejectsNonBooleanEnabledNamingTheKey() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(Announcement.ENABLED_PATH, "sometimes");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Announcement.fromConfig(config));
        assertTrue(e.getMessage().contains(Announcement.ENABLED_PATH), e.getMessage());
    }

    @Test
    void configRejectsNonTextMessageNamingTheKey() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(Announcement.MESSAGE_PATH, 42);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Announcement.fromConfig(config));
        assertTrue(e.getMessage().contains(Announcement.MESSAGE_PATH), e.getMessage());
    }

    @Test
    void bundledConfigMatchesDefaults() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml must be on the classpath");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(Announcement.DEFAULT_ENABLED, config.getBoolean(Announcement.ENABLED_PATH));
            assertEquals(Announcement.DEFAULT_MESSAGE, config.getString(Announcement.MESSAGE_PATH));
        }
    }
}
