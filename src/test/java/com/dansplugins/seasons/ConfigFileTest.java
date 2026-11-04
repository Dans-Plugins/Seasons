package com.dansplugins.seasons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest {

    private static final String BROKEN_YAML = "calendar:\n\tdays-per-season:\n    summer: 90\n";

    @TempDir
    File dir;

    private File write(String yaml) throws IOException {
        File file = new File(dir, "config.yml");
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void validFileIsAccepted() throws IOException {
        File file = write("calendar:\n  days-per-season:\n    summer: 90\n");
        assertDoesNotThrow(() -> ConfigFile.requireReadable(file));
    }

    @Test
    void emptyFileIsAccepted() throws IOException {
        File file = write("");
        assertDoesNotThrow(() -> ConfigFile.requireReadable(file));
    }

    @Test
    void invalidYamlIsRejectedNamingTheFile() throws IOException {
        File file = write(BROKEN_YAML);
        IOException e = assertThrows(IOException.class, () -> ConfigFile.requireReadable(file));
        assertTrue(e.getMessage().contains(file.toString()), e.getMessage());
    }

    @Test
    void fileThatIsNotAMappingIsRejected() throws IOException {
        File file = write("just some text\n");
        assertThrows(IOException.class, () -> ConfigFile.requireReadable(file));
    }

    @Test
    void missingFileIsRejected() {
        File file = new File(dir, "config.yml");
        assertThrows(IOException.class, () -> ConfigFile.requireReadable(file));
    }

    /**
     * The reason the check exists: on a broken file the loader behind {@code JavaPlugin#getConfig}
     * logs the error and returns a blank configuration (logging needs a server, so the blank one is
     * built directly here), and a saved date beyond the default season length would be clamped.
     */
    @Test
    void aBlankConfigurationFallsBackToDefaultsAndClampsTheSavedDate() {
        SeasonCalendar calendar = SeasonCalendar.fromConfig(new YamlConfiguration());

        assertEquals(SeasonCalendar.DEFAULT_DAYS_PER_SEASON, calendar.daysIn(Season.SUMMER));
        assertEquals(new CalendarDate(Season.SUMMER, SeasonCalendar.DEFAULT_DAYS_PER_SEASON),
                calendar.clamp(new CalendarDate(Season.SUMMER, 60)));
    }
}
