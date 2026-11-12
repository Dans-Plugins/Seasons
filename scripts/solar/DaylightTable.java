import com.dansplugins.seasons.sun.DayLength;
import com.dansplugins.seasons.sun.Daylight;

import java.time.LocalDate;

/**
 * Prints, as CSV, the daylight the plugin's own sun code gives every day of one year at a few
 * places: {@link Daylight#on} (the real share of the day the sun is up) and
 * {@link DayLength#clamp} (the share the plugin applies). A development tool for
 * plot_daylight.py; it is not part of the plugin or its build.
 *
 * <pre>
 *   ./gradlew compileJava
 *   java -cp build/classes/java/main scripts/solar/DaylightTable.java 2026 > daylight.csv
 * </pre>
 */
public final class DaylightTable {

    private static final Object[][] PLACES = {
        {"Singapore", 1.35, 103.82},
        {"Sydney", -33.87, 151.21},
        {"London", 51.5, -0.13},
        {"Tromso", 69.65, 18.96},
    };

    public static void main(String[] args) {
        int year = args.length > 0 ? Integer.parseInt(args[0]) : 2026;
        System.out.println("place,latitude,longitude,date,daylight_fraction,applied_fraction");
        for (Object[] place : PLACES) {
            double lat = (Double) place[1];
            double lon = (Double) place[2];
            for (LocalDate d = LocalDate.of(year, 1, 1); d.getYear() == year; d = d.plusDays(1)) {
                double f = Daylight.on(d, lat, lon).daylightFraction();
                System.out.printf("%s,%s,%s,%s,%.6f,%.6f%n", place[0], lat, lon, d, f, DayLength.clamp(f));
            }
        }
    }
}
