package com.dansplugins.seasons.sun;

/**
 * Where the sun is in the sky at one moment, seen from one place on Earth.
 *
 * <p>A line-for-line Java port of Strata's {@code strata_solar_position} (ABI 1.6,
 * {@code src/render/sun.cpp} in
 * <a href="https://github.com/Preponderous-Software/strata">Preponderous-Software/strata</a>,
 * unchanged from v0.7.0 to commit 63f3623): NOAA's solar position equations, after Meeus, good
 * to about a minute of arc between 1800 and 2100. The position is geometric: atmospheric
 * refraction, which lifts a sun near the horizon by up to about half a degree, is not applied.
 * {@code SolarPositionCrossCheckTest} holds this class to a table written by Strata's own C ABI.
 *
 * <p>Strata is MIT licensed, Copyright (c) 2026 Daniel McCoy Stephenson; its notice is kept in
 * {@code LICENSES/strata-MIT.txt}. The equations are ported here, not the renderer, and the plugin
 * does not load Strata.
 *
 * <p>Unlike the C ABI, which returns 32-bit floats, every field here is a double.
 */
public final class SolarPosition {

    private static final double DEG = Math.PI / 180.0;

    private final double toSunX;
    private final double toSunY;
    private final double toSunZ;
    private final double elevationDegrees;
    private final double azimuthDegrees;
    private final double declinationDegrees;
    private final double hourAngleDegrees;
    private final double equationOfTimeMinutes;

    private SolarPosition(double toSunX, double toSunY, double toSunZ, double elevationDegrees,
                          double azimuthDegrees, double declinationDegrees, double hourAngleDegrees,
                          double equationOfTimeMinutes) {
        this.toSunX = toSunX;
        this.toSunY = toSunY;
        this.toSunZ = toSunZ;
        this.elevationDegrees = elevationDegrees;
        this.azimuthDegrees = azimuthDegrees;
        this.declinationDegrees = declinationDegrees;
        this.hourAngleDegrees = hourAngleDegrees;
        this.equationOfTimeMinutes = equationOfTimeMinutes;
    }

    /**
     * Returns the sun's position at {@code unixSecondsUtc} (seconds since 1970-01-01 00:00 UTC,
     * fractions allowed) seen from {@code latitudeDegrees} (-90..90, north positive) and
     * {@code longitudeDegrees} (-180..180, east positive).
     *
     * @throws IllegalArgumentException for a non-finite argument or one out of range, as
     *         {@code strata_solar_position} refuses them with {@code STRATA_ERROR_INVALID_ARGUMENT}
     */
    public static SolarPosition at(double unixSecondsUtc, double latitudeDegrees, double longitudeDegrees) {
        if (!Double.isFinite(unixSecondsUtc) || !Double.isFinite(latitudeDegrees)
                || !Double.isFinite(longitudeDegrees) || Math.abs(latitudeDegrees) > 90.0
                || Math.abs(longitudeDegrees) > 180.0) {
            throw new IllegalArgumentException("solar position needs a finite time, latitude in [-90, 90] and "
                    + "longitude in [-180, 180], but was " + unixSecondsUtc + ", " + latitudeDegrees + ", "
                    + longitudeDegrees);
        }
        final double julianDay = unixSecondsUtc / 86400.0 + 2440587.5;
        final double jc = (julianDay - 2451545.0) / 36525.0; // Julian centuries since J2000.0

        final double meanLong = wrap360(280.46646 + jc * (36000.76983 + jc * 0.0003032));
        final double meanAnom = 357.52911 + jc * (35999.05029 - 0.0001537 * jc);
        final double eccent = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc);
        final double m = meanAnom * DEG;
        final double centre = Math.sin(m) * (1.914602 - jc * (0.004817 + 0.000014 * jc))
                + Math.sin(2.0 * m) * (0.019993 - 0.000101 * jc) + Math.sin(3.0 * m) * 0.000289;
        final double trueLong = meanLong + centre;
        final double omega = (125.04 - 1934.136 * jc) * DEG;
        final double apparentLong = trueLong - 0.00569 - 0.00478 * Math.sin(omega);
        final double meanObliquity =
                23.0 + (26.0 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0) / 60.0;
        final double obliquity = (meanObliquity + 0.00256 * Math.cos(omega)) * DEG;
        final double declination = Math.asin(Math.sin(obliquity) * Math.sin(apparentLong * DEG));

        final double y = Math.tan(obliquity / 2.0) * Math.tan(obliquity / 2.0);
        final double l0 = meanLong * DEG;
        final double eotRadians = y * Math.sin(2.0 * l0) - 2.0 * eccent * Math.sin(m)
                + 4.0 * eccent * y * Math.sin(m) * Math.cos(2.0 * l0)
                - 0.5 * y * y * Math.sin(4.0 * l0) - 1.25 * eccent * eccent * Math.sin(2.0 * m);
        final double eotMinutes = 4.0 * eotRadians / DEG;

        double dayMinutes = (unixSecondsUtc / 60.0) % 1440.0;
        if (dayMinutes < 0.0) {
            dayMinutes += 1440.0;
        }
        final double trueSolarMinutes = dayMinutes + eotMinutes + 4.0 * longitudeDegrees;
        final double hourAngle = wrap360(trueSolarMinutes / 4.0) - 180.0; // degrees, in [-180, 180)

        final double lat = latitudeDegrees * DEG;
        final double h = hourAngle * DEG;
        final double east = -Math.cos(declination) * Math.sin(h);
        final double north = Math.cos(lat) * Math.sin(declination)
                - Math.sin(lat) * Math.cos(declination) * Math.cos(h);
        final double up = Math.sin(lat) * Math.sin(declination)
                + Math.cos(lat) * Math.cos(declination) * Math.cos(h);

        // Strata's world frame: +X east, +Y up, +Z south.
        final double length = Math.sqrt(east * east + up * up + north * north);
        return new SolarPosition(
                east / length, up / length, -north / length,
                Math.asin(Math.max(-1.0, Math.min(1.0, up))) / DEG,
                wrap360(Math.atan2(east, north) / DEG),
                declination / DEG,
                hourAngle,
                eotMinutes);
    }

    /** {@code std::fmod} then shift into [0, 360); Java's {@code %} matches {@code fmod}'s sign rule. */
    private static double wrap360(double a) {
        a = a % 360.0;
        return a < 0.0 ? a + 360.0 : a;
    }

    /** East component of the unit vector towards the sun (Strata frame: +X east). */
    public double toSunX() {
        return toSunX;
    }

    /** Up component of the unit vector towards the sun (Strata frame: +Y up). */
    public double toSunY() {
        return toSunY;
    }

    /** South component of the unit vector towards the sun (Strata frame: +Z south). */
    public double toSunZ() {
        return toSunZ;
    }

    /** Degrees above the horizon; negative at night. */
    public double elevationDegrees() {
        return elevationDegrees;
    }

    /** Degrees clockwise from north: 90 east, 180 south, 270 west. */
    public double azimuthDegrees() {
        return azimuthDegrees;
    }

    /** -23.44 at the December solstice .. +23.44 at the June solstice. */
    public double declinationDegrees() {
        return declinationDegrees;
    }

    /** 0 at local solar noon, negative before it, in [-180, 180). */
    public double hourAngleDegrees() {
        return hourAngleDegrees;
    }

    /** Apparent minus mean solar time, in minutes. */
    public double equationOfTimeMinutes() {
        return equationOfTimeMinutes;
    }
}
