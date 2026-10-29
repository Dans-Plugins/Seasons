package com.dansplugins.seasons;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Entry point for Seasons. Features are scoped in MVP.md; so far the calendar, its persistence,
 * per-season crop growth, per-season rain and thunder and the {@code /season} command exist, and
 * nothing advances the date yet.
 */
public final class Seasons extends JavaPlugin {

    static final String DATA_FILE_NAME = "calendar.yml";

    private SeasonCalendar calendar;
    private CalendarStore store;
    private CalendarDate date;
    private GrowthPolicy growth;
    private WeatherPolicy weather;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            calendar = SeasonCalendar.fromConfig(getConfig());
            store = new CalendarStore(new File(getDataFolder(), DATA_FILE_NAME));
            date = store.load(calendar);
            growth = GrowthPolicy.fromConfig(getConfig());
            weather = WeatherPolicy.fromConfig(getConfig());
        } catch (IllegalArgumentException | IOException e) {
            getLogger().severe("Disabling Seasons: " + e.getMessage());
            calendar = null;
            store = null;
            date = null;
            growth = null;
            weather = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("The date is " + date + " (day " + calendar.dayOfYear(date)
                + " of " + calendar.yearLength() + ").");
        getServer().getPluginManager().registerEvents(new GrowthListener(growth, () -> date.getSeason(),
                () -> ThreadLocalRandom.current().nextDouble()), this);
        getServer().getPluginManager().registerEvents(new WeatherListener(weather, () -> date.getSeason(),
                () -> ThreadLocalRandom.current().nextDouble(),
                task -> getServer().getScheduler().runTask(this, task)), this);
        SeasonCommand command = new SeasonCommand(calendar, () -> date, this::setDate);
        PluginCommand season = getCommand("season");
        season.setExecutor(command);
        season.setTabCompleter(command);
    }

    /**
     * Makes the given date the current one and saves it at once, so it survives a crash.
     */
    private void setDate(CalendarDate newDate) {
        date = newDate;
        try {
            store.save(date);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save the calendar date", e);
        }
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
