package com.dansplugins.seasons;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Applies the current season's {@link WeatherPolicy} to weather changes.
 *
 * <p>When vanilla ends a weather period, the season decides what the next one is: a change towards
 * the state the season did not pick is cancelled, so the current weather carries on for another
 * vanilla period.
 *
 * <p>The Spigot API does not say why the weather changed, so changes made by the {@code /weather}
 * command or by players sleeping through the night are recognised by the event that comes just
 * before them, and every weather change for the rest of that tick is left alone.
 */
final class WeatherListener implements Listener {

    private final WeatherPolicy policy;
    private final Supplier<Season> currentSeason;
    private final DoubleSupplier random;
    private final Consumer<Runnable> nextTick;
    private boolean exempt;

    /**
     * @param nextTick runs a task on the next server tick; used to end an exemption
     */
    WeatherListener(WeatherPolicy policy, Supplier<Season> currentSeason, DoubleSupplier random,
                    Consumer<Runnable> nextTick) {
        this.policy = policy;
        this.currentSeason = currentSeason;
        this.random = random;
        this.nextTick = nextTick;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (exempt) {
            return;
        }
        if (policy.rains(currentSeason.get(), random.getAsDouble()) != event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (exempt) {
            return;
        }
        if (policy.thunders(currentSeason.get(), random.getAsDouble()) != event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isWeatherCommand(event.getMessage())) {
            exemptThisTick();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (isWeatherCommand(event.getCommand())) {
            exemptThisTick();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (event.getSkipReason() == TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            exemptThisTick();
        }
    }

    /**
     * Returns whether a command line runs vanilla's {@code /weather}, with or without the leading
     * slash or the {@code minecraft:} namespace.
     */
    static boolean isWeatherCommand(String commandLine) {
        String line = commandLine.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        int space = line.indexOf(' ');
        String label = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        return label.equals("weather") || label.equals("minecraft:weather");
    }

    private void exemptThisTick() {
        if (exempt) {
            return;
        }
        exempt = true;
        nextTick.accept(() -> exempt = false);
    }
}
