package com.weshape.backend.calc;

import java.util.HashMap;
import java.util.Map;

/**
 * The table of g CO₂e per kWh of electricity by country ({@code electricity-co2.csv}), checked before
 * use. The app ships one copy; a newer one is uploaded to R2 once a year by the "Refresh calculation
 * data" workflow and picked up by {@code CalcData} without a new release.
 *
 * <p>Format: lines starting with {@code #} are comments ({@code # version: ...} names the version);
 * then {@code country,year,low,typical,high}.
 */
public record ElectricityTable(Map<String, Co2.Electricity> countries, String version) {

    /** Fewer countries than this: the file is broken or cut off. */
    public static final int MIN_COUNTRIES = 200;
    /** g CO₂e per kWh: anything outside is a data error (no grid is cleaner than hydro, none dirtier than lignite). */
    public static final double MIN_G = 10;
    public static final double MAX_G = 1400;

    /** Reads and checks the table; throws IllegalArgumentException with the reason when it is not usable. */
    public static ElectricityTable parse(String text) {
        Map<String, Co2.Electricity> map = new HashMap<>();
        String version = "unknown";
        boolean header = false;
        int lineNo = 0;
        for (String line : text.split("\\R")) {
            lineNo++;
            String l = line.strip();
            if (l.isEmpty()) continue;
            if (l.startsWith("#")) {
                String c = l.substring(1).strip();
                if (c.startsWith("version:")) version = c.substring("version:".length()).strip();
                continue;
            }
            if (!header) {
                if (!l.equals("country,year,low,typical,high")) {
                    throw new IllegalArgumentException("Unexpected header: " + l);
                }
                header = true;
                continue;
            }
            String[] p = l.split(",");
            if (p.length != 5 || !p[0].matches("[A-Z]{2}")) {
                throw new IllegalArgumentException("Line " + lineNo + " is not a country row: " + l);
            }
            int year;
            Range g;
            try {
                year = Integer.parseInt(p[1]);
                g = new Range(Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + lineNo + ": " + e.getMessage());
            }
            if (g.low() < MIN_G || g.high() > MAX_G) {
                throw new IllegalArgumentException("Line " + lineNo + ": value outside " + MIN_G + "–" + MAX_G + ": " + l);
            }
            if (year < 2015 || year > 2100) {
                throw new IllegalArgumentException("Line " + lineNo + ": year " + year);
            }
            if (map.put(p[0], new Co2.Electricity(g, year, true)) != null) {
                throw new IllegalArgumentException("Country twice: " + p[0]);
            }
        }
        if (map.size() < MIN_COUNTRIES) {
            throw new IllegalArgumentException("Only " + map.size() + " countries");
        }
        return new ElectricityTable(Map.copyOf(map), version);
    }
}
