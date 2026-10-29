package com.dansplugins.seasons;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests run without a server, so they call {@link SeasonCommand#reply} and
 * {@link SeasonCommand#complete} with the sender's permissions as a predicate.
 */
class SeasonCommandTest {

    private static final Predicate<String> ADMIN = permission -> true;
    private static final Predicate<String> PLAYER = permission -> false;

    /** Dates the command set, in order. */
    private final List<CalendarDate> set = new ArrayList<>();

    /** A calendar of 10 days of spring, 20 of summer, 30 of autumn and 40 of winter. */
    private static SeasonCalendar calendar() {
        Map<Season, Integer> days = new EnumMap<>(Season.class);
        days.put(Season.SPRING, 10);
        days.put(Season.SUMMER, 20);
        days.put(Season.AUTUMN, 30);
        days.put(Season.WINTER, 40);
        return new SeasonCalendar(days);
    }

    private SeasonCommand command(CalendarDate today) {
        return new SeasonCommand(calendar(), () -> today, set::add);
    }

    private static String[] args(String... args) {
        return args;
    }

    @Test
    void reportsTheCurrentDate() {
        String reply = command(new CalendarDate(Season.AUTUMN, 7)).reply(args(), PLAYER);

        assertEquals("It is autumn, day 7 of 30 (day 37 of 100 in the year).", reply);
        assertTrue(set.isEmpty());
    }

    @Test
    void setsTheFirstDayOfASeason() {
        String reply = command(CalendarDate.startOfYear()).reply(args("set", "winter"), ADMIN);

        assertEquals(List.of(new CalendarDate(Season.WINTER, 1)), set);
        assertEquals("The date is now winter day 1. It is winter, day 1 of 40 (day 61 of 100 in the year).", reply);
    }

    @Test
    void setsAGivenDay() {
        command(CalendarDate.startOfYear()).reply(args("SET", "Summer", "20"), ADMIN);

        assertEquals(List.of(new CalendarDate(Season.SUMMER, 20)), set);
    }

    @Test
    void refusesToSetWithoutPermission() {
        String reply = command(CalendarDate.startOfYear()).reply(args("set", "winter"), PLAYER);

        assertEquals("You do not have permission to set the season.", reply);
        assertTrue(set.isEmpty());
    }

    @Test
    void checksTheSetPermissionNode() {
        List<String> asked = new ArrayList<>();

        command(CalendarDate.startOfYear()).reply(args("set", "winter"), permission -> asked.add(permission));

        assertEquals(List.of("seasons.set"), asked);
    }

    @Test
    void showsUsageWhenTheSeasonIsMissing() {
        assertEquals(SeasonCommand.SET_USAGE, command(CalendarDate.startOfYear()).reply(args("set"), ADMIN));
        assertEquals(SeasonCommand.SET_USAGE,
                command(CalendarDate.startOfYear()).reply(args("set", "winter", "1", "2"), ADMIN));
        assertTrue(set.isEmpty());
    }

    @Test
    void rejectsAnUnknownSeason() {
        String reply = command(CalendarDate.startOfYear()).reply(args("set", "monsoon"), ADMIN);

        assertEquals("Unknown season 'monsoon'. Choose spring, summer, autumn or winter.", reply);
        assertTrue(set.isEmpty());
    }

    @Test
    void rejectsADayBeyondTheSeason() {
        String reply = command(CalendarDate.startOfYear()).reply(args("set", "spring", "11"), ADMIN);

        assertEquals("The day must be a whole number from 1 to 10 for spring.", reply);
        assertTrue(set.isEmpty());
    }

    @Test
    void rejectsADayThatIsNotAWholeNumberOfAtLeastOne() {
        SeasonCommand command = command(CalendarDate.startOfYear());

        command.reply(args("set", "spring", "0"), ADMIN);
        command.reply(args("set", "spring", "-3"), ADMIN);
        command.reply(args("set", "spring", "two"), ADMIN);
        command.reply(args("set", "spring", "1.5"), ADMIN);

        assertTrue(set.isEmpty());
    }

    @Test
    void acceptsTheLastDayOfTheSeason() {
        command(CalendarDate.startOfYear()).reply(args("set", "spring", "10"), ADMIN);

        assertEquals(List.of(new CalendarDate(Season.SPRING, 10)), set);
    }

    @Test
    void rejectsAnUnknownSubcommand() {
        String reply = command(CalendarDate.startOfYear()).reply(args("advance"), ADMIN);

        assertEquals("Unknown subcommand 'advance'. Use /season or /season set <season> [day].", reply);
    }

    @Test
    void completesTheSetSubcommandForAdmins() {
        SeasonCommand command = command(CalendarDate.startOfYear());

        assertEquals(List.of("set"), command.complete(args(""), ADMIN));
        assertEquals(List.of("set"), command.complete(args("s"), ADMIN));
        assertEquals(List.of(), command.complete(args("x"), ADMIN));
    }

    @Test
    void completesSeasonNames() {
        SeasonCommand command = command(CalendarDate.startOfYear());

        assertEquals(List.of("spring", "summer", "autumn", "winter"), command.complete(args("set", ""), ADMIN));
        assertEquals(List.of("spring", "summer"), command.complete(args("set", "S"), ADMIN));
        assertEquals(List.of(), command.complete(args("set", "winter", ""), ADMIN));
    }

    @Test
    void completesNothingWithoutPermission() {
        assertEquals(List.of(), command(CalendarDate.startOfYear()).complete(args(""), PLAYER));
    }
}
