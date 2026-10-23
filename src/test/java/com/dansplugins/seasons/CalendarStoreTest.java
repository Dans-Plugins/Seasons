package com.dansplugins.seasons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarStoreTest {

    @TempDir
    File dir;

    private static SeasonCalendar calendarOf(int days) {
        Map<Season, Integer> lengths = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            lengths.put(season, days);
        }
        return new SeasonCalendar(lengths);
    }

    private void write(File file, String yaml) throws IOException {
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void missingFileStartsOnFirstDayOfSpring() throws IOException {
        CalendarStore store = new CalendarStore(new File(dir, "calendar.yml"));
        assertEquals(CalendarDate.startOfYear(), store.load(calendarOf(10)));
    }

    @Test
    void savedDateIsLoadedBack() throws IOException {
        File file = new File(dir, "calendar.yml");
        new CalendarStore(file).save(new CalendarDate(Season.AUTUMN, 7));
        assertEquals(new CalendarDate(Season.AUTUMN, 7), new CalendarStore(file).load(calendarOf(10)));
    }

    @Test
    void saveCreatesMissingDataFolder() throws IOException {
        File file = new File(new File(dir, "Seasons"), "calendar.yml");
        new CalendarStore(file).save(new CalendarDate(Season.SUMMER, 2));
        assertTrue(file.isFile());
    }

    @Test
    void saveWritesSeasonKeyAndDayOfSeason() throws IOException {
        File file = new File(dir, "calendar.yml");
        new CalendarStore(file).save(new CalendarDate(Season.WINTER, 4));
        String yaml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("season: winter"), yaml);
        assertTrue(yaml.contains("day-of-season: 4"), yaml);
    }

    @Test
    void loadClampsDayWhenSeasonWasShortened() throws IOException {
        File file = new File(dir, "calendar.yml");
        new CalendarStore(file).save(new CalendarDate(Season.SUMMER, 20));
        assertEquals(new CalendarDate(Season.SUMMER, 5), new CalendarStore(file).load(calendarOf(5)));
    }

    @Test
    void loadRejectsUnknownSeason() throws IOException {
        File file = new File(dir, "calendar.yml");
        write(file, "season: monsoon\nday-of-season: 1\n");
        IOException e = assertThrows(IOException.class, () -> new CalendarStore(file).load(calendarOf(5)));
        assertTrue(e.getMessage().contains("monsoon"), e.getMessage());
    }

    @Test
    void loadRejectsMissingOrNonPositiveDay() throws IOException {
        File file = new File(dir, "calendar.yml");
        write(file, "season: spring\n");
        assertThrows(IOException.class, () -> new CalendarStore(file).load(calendarOf(5)));
        write(file, "season: spring\nday-of-season: 0\n");
        assertThrows(IOException.class, () -> new CalendarStore(file).load(calendarOf(5)));
    }

    @Test
    void loadRejectsMalformedYaml() throws IOException {
        File file = new File(dir, "calendar.yml");
        write(file, "season: [spring\n");
        assertThrows(IOException.class, () -> new CalendarStore(file).load(calendarOf(5)));
    }
}
