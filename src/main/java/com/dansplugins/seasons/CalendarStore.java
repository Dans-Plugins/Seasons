package com.dansplugins.seasons;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Saves and loads the current calendar date so it survives a restart.
 *
 * <p>The file stores the season and the day within it rather than a bare day of the year, so a
 * change to season lengths in {@code config.yml} never moves the server into a different season.
 */
public final class CalendarStore {

    static final String SEASON_KEY = "season";
    static final String DAY_KEY = "day-of-season";
    static final String TEMPORARY_SUFFIX = ".tmp";

    private final File file;

    public CalendarStore(File file) {
        this.file = file;
    }

    /**
     * Loads the saved date, fitted to the given calendar. A missing file means a new server, which
     * starts on the first day of spring.
     *
     * @throws IOException if the file exists but cannot be read, or does not hold a valid date
     */
    public CalendarDate load(SeasonCalendar calendar) throws IOException {
        if (!file.exists()) {
            return CalendarDate.startOfYear();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            throw new IOException("Could not read " + file + ": " + e.getMessage(), e);
        }
        Season season = Season.fromKey(yaml.getString(SEASON_KEY));
        if (season == null) {
            throw new IOException(file + " has an unknown " + SEASON_KEY + ": '" + yaml.get(SEASON_KEY) + "'");
        }
        if (!yaml.isInt(DAY_KEY) || yaml.getInt(DAY_KEY) < 1) {
            throw new IOException(file + " has an invalid " + DAY_KEY + ": '" + yaml.get(DAY_KEY) + "'");
        }
        return calendar.clamp(new CalendarDate(season, yaml.getInt(DAY_KEY)));
    }

    /**
     * Writes the date, creating the parent directory if needed. It is written to a temporary file
     * that then replaces the real one in a single step, so a save that is interrupted part-way
     * leaves the previous date in place rather than a truncated file.
     */
    public void save(CalendarDate date) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(SEASON_KEY, date.getSeason().key());
        yaml.set(DAY_KEY, date.getDayOfSeason());
        File temporary = new File(file.getPath() + TEMPORARY_SUFFIX);
        yaml.save(temporary);
        Files.move(temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
