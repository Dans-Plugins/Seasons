package com.dansplugins.seasons;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Applies the current season's {@link WeatherPolicy} to natural weather changes.
 *
 * <p>When vanilla ends a weather period, the season decides what the next one is: a change towards
 * the state the season did not pick is cancelled, so the current weather carries on for another
 * vanilla period. Changes made by a command, a plugin or sleeping are left alone.
 */
final class WeatherListener implements Listener {

    private final WeatherPolicy policy;
    private final Supplier<Season> currentSeason;
    private final DoubleSupplier random;

    WeatherListener(WeatherPolicy policy, Supplier<Season> currentSeason, DoubleSupplier random) {
        this.policy = policy;
        this.currentSeason = currentSeason;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.getCause() != WeatherChangeEvent.Cause.NATURAL) {
            return;
        }
        if (policy.rains(currentSeason.get(), random.getAsDouble()) != event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (event.getCause() != ThunderChangeEvent.Cause.NATURAL) {
            return;
        }
        if (policy.thunders(currentSeason.get(), random.getAsDouble()) != event.toThunderState()) {
            event.setCancelled(true);
        }
    }
}
