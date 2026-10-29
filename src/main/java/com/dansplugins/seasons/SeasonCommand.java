package com.dansplugins.seasons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Handles {@code /season}, which reports the current date, and {@code /season set <season> [day]},
 * which moves the calendar to another date for testing and events.
 *
 * <p>The replies are worked out by {@link #reply} and {@link #complete}, which need no server, so the
 * Bukkit methods only pass the sender's permissions in and the reply out.
 */
final class SeasonCommand implements CommandExecutor, TabCompleter {

    static final String SET_PERMISSION = "seasons.set";
    static final String SET_USAGE = "Usage: /season set <season> [day]";

    private final SeasonCalendar calendar;
    private final Supplier<CalendarDate> currentDate;
    private final Consumer<CalendarDate> setDate;

    /**
     * @param setDate makes the given date the current one
     */
    SeasonCommand(SeasonCalendar calendar, Supplier<CalendarDate> currentDate, Consumer<CalendarDate> setDate) {
        this.calendar = calendar;
        this.currentDate = currentDate;
        this.setDate = setDate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(reply(args, sender::hasPermission));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return complete(args, sender::hasPermission);
    }

    /**
     * Runs the command and returns the message to send back.
     */
    String reply(String[] args, Predicate<String> hasPermission) {
        if (args.length == 0) {
            return describe(currentDate.get());
        }
        if (!args[0].equalsIgnoreCase("set")) {
            return "Unknown subcommand '" + args[0] + "'. Use /season or /season set <season> [day].";
        }
        if (!hasPermission.test(SET_PERMISSION)) {
            return "You do not have permission to set the season.";
        }
        if (args.length < 2 || args.length > 3) {
            return SET_USAGE;
        }
        Season season = Season.fromKey(args[1]);
        if (season == null) {
            return "Unknown season '" + args[1] + "'. Choose spring, summer, autumn or winter.";
        }
        int day = 1;
        if (args.length == 3) {
            day = parseDay(args[2], calendar.daysIn(season));
            if (day < 1) {
                return "The day must be a whole number from 1 to " + calendar.daysIn(season)
                        + " for " + season.key() + ".";
            }
        }
        CalendarDate date = new CalendarDate(season, day);
        setDate.accept(date);
        return "The date is now " + date + ". " + describe(date);
    }

    /**
     * Returns the tab completions for the arguments typed so far.
     */
    List<String> complete(String[] args, Predicate<String> hasPermission) {
        if (!hasPermission.test(SET_PERMISSION)) {
            return Collections.emptyList();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("set");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (Season season : Season.values()) {
                options.add(season.key());
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }

    private String describe(CalendarDate date) {
        return "It is " + date.getSeason().key() + ", day " + date.getDayOfSeason() + " of "
                + calendar.daysIn(date.getSeason()) + " (day " + calendar.dayOfYear(date) + " of "
                + calendar.yearLength() + " in the year).";
    }

    /**
     * Returns the day as a number, or 0 if it is not a whole number from 1 to the season's length.
     */
    private static int parseDay(String text, int daysInSeason) {
        int day;
        try {
            day = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
        return day >= 1 && day <= daysInSeason ? day : 0;
    }
}
