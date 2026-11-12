package com.dansplugins.seasons.sun;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * How long the sun is up on one real date at one place: the sunrise, sunset and the share of
 * the day between them, from {@link SolarPosition}.
 *
 * <p>The horizon is geometric (the sun's centre at elevation 0), to agree with
 * {@link SolarPosition}, which applies no refraction. Published sunrise tables use -0.833 degrees
 * for refraction and the sun's radius, so their days run a few minutes longer than these.
 */
public final class Daylight {

    private static final double DEG = Math.PI / 180.0;
    private static final double SECONDS_PER_DAY = 86400.0;

    private final double solarNoonUnixSeconds;
    private final double daylightFraction;

    private Daylight(double solarNoonUnixSeconds, double daylightFraction) {
        this.solarNoonUnixSeconds = solarNoonUnixSeconds;
        this.daylightFraction = daylightFraction;
    }

    /**
     * Returns the daylight on {@code date} (its solar noon falls on that UTC date, or within a
     * few minutes of it at the date line) at the given latitude and longitude.
     *
     * @throws IllegalArgumentException for a latitude or longitude out of range
     */
    public static Daylight on(LocalDate date, double latitudeDegrees, double longitudeDegrees) {
        double midnight = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        // Mean solar noon first, then corrected by the equation of time at that moment.
        double meanNoon = midnight + (720.0 - 4.0 * longitudeDegrees) * 60.0;
        double eot = SolarPosition.at(meanNoon, latitudeDegrees, longitudeDegrees).equationOfTimeMinutes();
        double solarNoon = meanNoon - eot * 60.0;

        SolarPosition noon = SolarPosition.at(solarNoon, latitudeDegrees, longitudeDegrees);
        double lat = latitudeDegrees * DEG;
        double dec = noon.declinationDegrees() * DEG;
        double denominator = Math.cos(lat) * Math.cos(dec);
        double fraction;
        if (Math.abs(denominator) < 1e-12) {
            // At a pole the sun circles at its declination's height all day.
            fraction = Math.signum(latitudeDegrees) * dec > 0.0 ? 1.0 : 0.0;
        } else {
            double cosHalfDay = -Math.sin(lat) * Math.sin(dec) / denominator;
            if (cosHalfDay <= -1.0) {
                fraction = 1.0; // the sun never sets
            } else if (cosHalfDay >= 1.0) {
                fraction = 0.0; // the sun never rises
            } else {
                fraction = 2.0 * Math.acos(cosHalfDay) / DEG / 360.0;
            }
        }
        return new Daylight(solarNoon, fraction);
    }

    /** The share of the day, 0 to 1, that the sun is above the horizon. */
    public double daylightFraction() {
        return daylightFraction;
    }

    /** Daylight in hours, 0 to 24. */
    public double daylightHours() {
        return daylightFraction * 24.0;
    }

    /** The moment the sun is highest. */
    public Instant solarNoon() {
        return toInstant(solarNoonUnixSeconds);
    }

    /** True when the sun stays above the horizon all day. */
    public boolean isPolarDay() {
        return daylightFraction >= 1.0;
    }

    /** True when the sun stays below the horizon all day. */
    public boolean isPolarNight() {
        return daylightFraction <= 0.0;
    }

    /** Sunrise, or empty on a polar day or night. */
    public Optional<Instant> sunrise() {
        return isPolarDay() || isPolarNight() ? Optional.empty()
                : Optional.of(toInstant(solarNoonUnixSeconds - daylightFraction * SECONDS_PER_DAY / 2.0));
    }

    /** Sunset, or empty on a polar day or night. */
    public Optional<Instant> sunset() {
        return isPolarDay() || isPolarNight() ? Optional.empty()
                : Optional.of(toInstant(solarNoonUnixSeconds + daylightFraction * SECONDS_PER_DAY / 2.0));
    }

    private static Instant toInstant(double unixSeconds) {
        long whole = (long) Math.floor(unixSeconds);
        return Instant.ofEpochSecond(whole, Math.round((unixSeconds - whole) * 1e9));
    }
}
