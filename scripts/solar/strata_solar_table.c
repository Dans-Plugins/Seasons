/*
 * Writes the cross-check table for SolarPositionCrossCheckTest: the sun's
 * position from Strata's own C ABI (strata_solar_position, ABI 1.6) for a grid
 * of moments, latitudes and longitudes.
 *
 * The Java port in src/main/java/com/dansplugins/seasons/sun/SolarPosition.java
 * must agree with every row. Regenerate the table when Strata's solar math
 * changes (or to check a newer release) with:
 *
 *   gh release download v0.7.0 -R Preponderous-Software/strata \
 *     -p 'strata-0.7.0-linux-x64.tar.gz' -p SHA256SUMS
 *   sha256sum -c --ignore-missing SHA256SUMS
 *   mkdir strata && tar -xzf strata-0.7.0-linux-x64.tar.gz -C strata
 *   cc -O0 -o strata_solar_table scripts/solar/strata_solar_table.c \
 *     -Istrata/include -Lstrata/lib -lstrata
 *   LD_LIBRARY_PATH=strata/lib ./strata_solar_table \
 *     > src/test/resources/solar/strata_solar_table.csv
 *
 * This is a development tool. It is not part of the plugin or its build, and
 * the plugin does not load Strata.
 */
#include <stdio.h>
#include <stdlib.h>

#include <strata/strata.h>

int main(void) {
    if (strata_abi_minor() < 6) {
        fprintf(stderr, "strata_solar_position needs ABI 1.6; this library is 1.%u\n", strata_abi_minor());
        return EXIT_FAILURE;
    }

    /* Moments (Unix seconds, UTC): equinoxes and solstices across the range
     * NOAA's equations are good for, plus ordinary days and odd times. */
    static const double moments[] = {
        946728000.0,     /* 2000-01-01 12:00 (J2000.0) */
        953553600.0,     /* 2000-03-20 12:00 */
        961588800.0,     /* 2000-06-21 12:00 */
        977400000.0,     /* 2000-12-21 12:00 */
        1773964800.0,    /* 2026-03-20 00:00 */
        1774008000.0,    /* 2026-03-20 12:00 */
        1782043200.0,    /* 2026-06-21 12:00 */
        1782064800.0,    /* 2026-06-21 18:00 */
        1790157600.0,    /* 2026-09-23 10:00 */
        1797854400.0,    /* 2026-12-21 12:00 */
        1797830100.5,    /* 2026-12-21 05:15:00.5 */
        1794330000.0,    /* 2026-11-10 17:00 */
        315576000.0,     /* 1980-01-01 12:00 */
        -631108800.0,    /* 1950-01-01 12:00 */
        2539425600.0,    /* 2050-06-21 12:00 */
        4102401600.0,    /* 2099-12-31 12:00 */
    };
    static const double latitudes[] = {-78.0, -66.5, -45.0, -23.4, 0.0, 23.4, 40.6, 51.5, 66.6, 78.0};
    static const double longitudes[] = {-180.0, -105.1, 0.0, 12.5, 139.7, 180.0};

    printf("# strata_solar_position from Strata %s (ABI %u.%u), via scripts/solar/strata_solar_table.c\n",
           strata_version_string(), strata_abi_version(), strata_abi_minor());
    printf("unix_seconds_utc,latitude_degrees,longitude_degrees,to_sun_x,to_sun_y,to_sun_z,"
           "elevation_degrees,azimuth_degrees,declination_degrees,hour_angle_degrees,"
           "equation_of_time_minutes\n");
    for (size_t t = 0; t < sizeof moments / sizeof moments[0]; ++t) {
        for (size_t a = 0; a < sizeof latitudes / sizeof latitudes[0]; ++a) {
            for (size_t o = 0; o < sizeof longitudes / sizeof longitudes[0]; ++o) {
                StrataSolarPosition p;
                StrataStatus status = strata_solar_position(moments[t], latitudes[a], longitudes[o], &p);
                if (status != STRATA_OK) {
                    fprintf(stderr, "strata_solar_position failed (%d) for %.17g %.17g %.17g\n", (int)status,
                            moments[t], latitudes[a], longitudes[o]);
                    return EXIT_FAILURE;
                }
                printf("%.17g,%.17g,%.17g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g\n", moments[t], latitudes[a],
                       longitudes[o], p.to_sun[0], p.to_sun[1], p.to_sun[2], p.elevation_degrees,
                       p.azimuth_degrees, p.declination_degrees, p.hour_angle_degrees,
                       p.equation_of_time_minutes);
            }
        }
    }
    return EXIT_SUCCESS;
}
