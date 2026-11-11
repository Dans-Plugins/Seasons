package com.dansplugins.seasons.sun;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the Java port to Strata's own output. {@code strata_solar_table.csv} was written by
 * {@code scripts/solar/strata_solar_table.c} calling {@code strata_solar_position} in the Strata
 * release named on its first line; see that script to regenerate it.
 *
 * <p>Strata computes in doubles and returns 32-bit floats, so the tolerances are a little above
 * float precision: a float near 360 is only good to about 3e-5.
 */
class SolarPositionCrossCheckTest {

    private static final String TABLE = "/solar/strata_solar_table.csv";
    private static final double DEGREES_TOLERANCE = 1e-4;
    private static final double MINUTES_TOLERANCE = 1e-4;
    private static final double VECTOR_TOLERANCE = 1e-6;

    @Test
    void everyRowOfStrataTableAgrees() throws IOException {
        List<String[]> rows = readTable();
        assertEquals(16 * 10 * 6, rows.size(), "rows in " + TABLE);
        List<String> failures = new ArrayList<>();
        for (String[] row : rows) {
            double t = Double.parseDouble(row[0]);
            double lat = Double.parseDouble(row[1]);
            double lon = Double.parseDouble(row[2]);
            SolarPosition p = SolarPosition.at(t, lat, lon);
            String where = "t=" + row[0] + " lat=" + row[1] + " lon=" + row[2] + ": ";
            check(failures, where + "to_sun.x", Double.parseDouble(row[3]), p.toSunX(), VECTOR_TOLERANCE);
            check(failures, where + "to_sun.y", Double.parseDouble(row[4]), p.toSunY(), VECTOR_TOLERANCE);
            check(failures, where + "to_sun.z", Double.parseDouble(row[5]), p.toSunZ(), VECTOR_TOLERANCE);
            check(failures, where + "elevation", Double.parseDouble(row[6]), p.elevationDegrees(), DEGREES_TOLERANCE);
            checkAngle(failures, where + "azimuth", Double.parseDouble(row[7]), p.azimuthDegrees());
            check(failures, where + "declination", Double.parseDouble(row[8]), p.declinationDegrees(),
                    DEGREES_TOLERANCE);
            checkAngle(failures, where + "hour angle", Double.parseDouble(row[9]), p.hourAngleDegrees());
            check(failures, where + "equation of time", Double.parseDouble(row[10]), p.equationOfTimeMinutes(),
                    MINUTES_TOLERANCE);
        }
        assertTrue(failures.isEmpty(), failures.size() + " mismatches, first: "
                + (failures.isEmpty() ? "" : failures.get(0)));
    }

    @Test
    void tableNamesTheStrataReleaseItCameFrom() throws IOException {
        try (BufferedReader reader = open()) {
            String first = reader.readLine();
            assertNotNull(first);
            assertTrue(first.startsWith("# strata_solar_position from Strata "), first);
        }
    }

    private static void check(List<String> failures, String what, double expected, double actual, double tol) {
        if (Math.abs(expected - actual) > tol) {
            failures.add(what + " expected " + expected + " but was " + actual);
        }
    }

    /** Angles compared around the circle, so 359.99995 and 0.00001 (or -180 and 180) agree. */
    private static void checkAngle(List<String> failures, String what, double expected, double actual) {
        double difference = Math.abs(expected - actual) % 360.0;
        if (Math.min(difference, 360.0 - difference) > DEGREES_TOLERANCE) {
            failures.add(what + " expected " + expected + " but was " + actual);
        }
    }

    private static List<String[]> readTable() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = open()) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] row = line.split(",");
                assertEquals(11, row.length, line);
                rows.add(row);
            }
        }
        return rows;
    }

    private static BufferedReader open() {
        InputStream in = SolarPositionCrossCheckTest.class.getResourceAsStream(TABLE);
        assertNotNull(in, TABLE + " is missing from the test resources");
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
