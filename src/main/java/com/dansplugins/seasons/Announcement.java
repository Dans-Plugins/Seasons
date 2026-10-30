package com.dansplugins.seasons;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The chat message broadcast when the season changes.
 *
 * <p>The message may use {@code &} colour codes and the placeholder {@code {season}}, which is
 * replaced with the new season's name. A date change that stays in the same season announces
 * nothing.
 */
public final class Announcement {

    /** Configuration path of the on/off switch. */
    public static final String ENABLED_PATH = "announcement.enabled";

    /** Configuration path of the message text. */
    public static final String MESSAGE_PATH = "announcement.message";

    /** Replaced in the message with the new season's name. */
    public static final String SEASON_PLACEHOLDER = "{season}";

    static final boolean DEFAULT_ENABLED = true;
    static final String DEFAULT_MESSAGE = "&6It is now {season}.";

    private final boolean enabled;
    private final String message;

    public Announcement(boolean enabled, String message) {
        if (message == null) {
            throw new IllegalArgumentException("No announcement message given");
        }
        this.enabled = enabled;
        this.message = message;
    }

    /**
     * Reads {@code announcement.enabled} and {@code announcement.message}. A missing key falls back
     * to its default.
     *
     * @throws IllegalArgumentException if a key is present but of the wrong type
     */
    public static Announcement fromConfig(ConfigurationSection config) {
        boolean enabled = DEFAULT_ENABLED;
        if (config.contains(ENABLED_PATH)) {
            Object value = config.get(ENABLED_PATH);
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException(
                        ENABLED_PATH + " must be true or false, but was '" + value + "'");
            }
            enabled = (Boolean) value;
        }
        String message = DEFAULT_MESSAGE;
        if (config.contains(MESSAGE_PATH)) {
            Object value = config.get(MESSAGE_PATH);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException(
                        MESSAGE_PATH + " must be text, but was '" + value + "'");
            }
            message = (String) value;
        }
        return new Announcement(enabled, message);
    }

    /**
     * Returns the message to broadcast when the date moves from {@code oldDate} to {@code newDate},
     * or {@code null} if nothing should be announced: the announcement is disabled, the message is
     * blank, or the season did not change.
     */
    public String messageFor(CalendarDate oldDate, CalendarDate newDate) {
        if (!enabled || message.trim().isEmpty()) {
            return null;
        }
        if (oldDate != null && oldDate.getSeason() == newDate.getSeason()) {
            return null;
        }
        String text = message.replace(SEASON_PLACEHOLDER, newDate.getSeason().key());
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
