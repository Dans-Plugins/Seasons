package com.dansplugins.seasons;

import com.dansplugins.seasons.sun.DayLength;
import com.dansplugins.seasons.sun.DayLengthClock;
import com.dansplugins.seasons.sun.Daylight;
import com.dansplugins.seasons.sun.RealYear;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Entry point for Seasons. Features are scoped in MVP.md; so far the calendar, its persistence,
 * per-season crop growth, per-season rain and thunder, the {@code /season} command, the
 * season-change announcement and an opt-in day length from a real place (RFC 0005) exist, and
 * nothing advances the date yet.
 */
public final class Seasons extends JavaPlugin {

    static final String CONFIG_FILE_NAME = "config.yml";
    static final String DATA_FILE_NAME = "calendar.yml";
    /** The plugin that owns the world clock when it is enabled; Seasons then only publishes its target. */
    static final String TIMESCALE = "Timescale";

    private SeasonCalendar calendar;
    private CalendarStore store;
    private CalendarDate date;
    private GrowthPolicy growth;
    private WeatherPolicy weather;
    private Announcement announcement;
    private DayLengthSettings dayLengthSettings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            ConfigFile.requireReadable(new File(getDataFolder(), CONFIG_FILE_NAME));
            calendar = SeasonCalendar.fromConfig(getConfig());
            store = new CalendarStore(new File(getDataFolder(), DATA_FILE_NAME));
            date = store.load(calendar);
            growth = GrowthPolicy.fromConfig(getConfig());
            weather = WeatherPolicy.fromConfig(getConfig());
            announcement = Announcement.fromConfig(getConfig());
            dayLengthSettings = DayLengthSettings.fromConfig(getConfig());
        } catch (IllegalArgumentException | IOException e) {
            getLogger().severe("Disabling Seasons: " + e.getMessage());
            calendar = null;
            store = null;
            date = null;
            growth = null;
            weather = null;
            announcement = null;
            dayLengthSettings = null;
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
        if (dayLengthSettings.isEnabled()) {
            startDayLength();
        }
    }

    /**
     * Publishes the real-place day length as a service and, unless Timescale is enabled, shapes the
     * overworld clocks to it every tick.
     */
    private void startDayLength() {
        DayLength dayLength = new DayLength(calendar, new RealYear(dayLengthSettings.getSpringStart()),
                dayLengthSettings.getLatitude(), dayLengthSettings.getLongitude(), () -> date);
        getServer().getServicesManager().register(DayLength.class, dayLength, this, ServicePriority.Normal);
        Daylight today = dayLength.realDaylightOn(date);
        getLogger().info(String.format("Day length follows a real place: %s stands for %s, with %.1f h of"
                        + " daylight (%.1f h applied).", date, dayLength.realDateFor(date).toString().substring(5),
                today.daylightHours(), dayLength.daylightFraction() * 24.0));
        if (getServer().getPluginManager().isPluginEnabled(TIMESCALE)) {
            getLogger().info("Timescale is enabled, so it keeps the world clock; Seasons leaves day length to it"
                    + " and publishes its target as a service.");
        }
        getServer().getScheduler().runTaskTimer(this, new DayLengthClock(this::overworldTimes,
                dayLength::daylightFraction, () -> getServer().getPluginManager().isPluginEnabled(TIMESCALE)),
                1L, 1L);
    }

    /** The worlds with a day cycle (the overworld environment), as {@link DayLengthClock} sees them. */
    private List<DayLengthClock.WorldTime> overworldTimes() {
        List<DayLengthClock.WorldTime> times = new ArrayList<>();
        for (World world : getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                times.add(new DayLengthClock.WorldTime() {
                    @Override
                    public UUID getId() {
                        return world.getUID();
                    }

                    @Override
                    public long getFullTime() {
                        return world.getFullTime();
                    }

                    @Override
                    public void setFullTime(long time) {
                        world.setFullTime(time);
                    }
                });
            }
        }
        return times;
    }

    /**
     * Makes the given date the current one, announces a change of season, and saves it at once, so
     * it survives a crash.
     */
    private void setDate(CalendarDate newDate) {
        String message = announcement.messageFor(date, newDate);
        date = newDate;
        if (message != null) {
            getServer().broadcastMessage(message);
        }
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
