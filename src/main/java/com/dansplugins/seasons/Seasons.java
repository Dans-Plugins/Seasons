package com.dansplugins.seasons;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Entry point for Seasons. Features are scoped in MVP.md; so far only the calendar and its
 * persistence exist, and nothing advances it yet.
 */
public final class Seasons extends JavaPlugin {

    static final String DATA_FILE_NAME = "calendar.yml";

    private SeasonCalendar calendar;
    private CalendarStore store;
    private CalendarDate date;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            calendar = SeasonCalendar.fromConfig(getConfig());
            store = new CalendarStore(new File(getDataFolder(), DATA_FILE_NAME));
            date = store.load(calendar);
        } catch (IllegalArgumentException | IOException e) {
            getLogger().severe("Disabling Seasons: " + e.getMessage());
            calendar = null;
            store = null;
            date = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("The date is " + date + " (day " + calendar.dayOfYear(date)
                + " of " + calendar.yearLength() + ").");
    }

    @Override
    public void onDisable() {
        if (store == null || date == null) {
            return;
        }
        try {
            store.save(date);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save the calendar date", e);
        }
    }
}
