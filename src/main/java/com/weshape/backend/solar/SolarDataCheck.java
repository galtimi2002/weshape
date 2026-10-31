package com.weshape.backend.solar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Tells whether the map grid is out of date: PVGIS has a newer API version, or the current
 * version now serves newer data (another database or more years).
 *
 * <p>Run by the monthly GitHub workflow (.github/workflows/pvgis-refresh.yml). Writes
 * {@code update}, {@code url} and {@code reason} to $GITHUB_OUTPUT when it is set, and prints
 * them either way.
 */
public final class SolarDataCheck {

    /** Cluj-Napoca: on land, inside PVGIS's main database. */
    static final double REFERENCE_LAT = 46.77;
    static final double REFERENCE_LON = 23.59;

    private static final Pattern HEADER = Pattern.compile("^# PVGIS source: (\\S+) \\| data: (.+)$");
    private static final Pattern VERSION = Pattern.compile("/api/v(\\d+)_(\\d+)/");

    private SolarDataCheck() {
    }

    static String header(String url, String dataVersion) {
        return "# PVGIS source: " + url + " | data: " + dataVersion;
    }

    public static void main(String[] args) throws Exception {
        Path grid = Path.of(args.length > 0 ? args[0] : "src/main/resources/" + SolarGrid.RESOURCE);
        String currentUrl = PvgisClient.DEFAULT_URL;
        String currentData = null;
        if (Files.exists(grid)) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(grid)), StandardCharsets.UTF_8))) {
                Matcher m = HEADER.matcher(String.valueOf(r.readLine()));
                if (m.matches()) {
                    currentUrl = m.group(1);
                    currentData = m.group(2).trim();
                }
            }
        }

        String url = currentUrl;
        String reason = null;
        for (String candidate : newerVersions(currentUrl)) {
            if (new PvgisClient(candidate, Duration.ofSeconds(40)).answers(REFERENCE_LAT, REFERENCE_LON)) {
                url = candidate;
                reason = "PVGIS has a new API version: " + candidate;
            }
        }
        String data = new PvgisClient(url, Duration.ofSeconds(40)).dataVersion(REFERENCE_LAT, REFERENCE_LON);
        if (reason == null && currentData == null) reason = "The map grid has not been built yet";
        if (reason == null && !data.equals(currentData)) reason = "PVGIS now serves " + data + " (the grid has " + currentData + ")";

        boolean update = reason != null;
        System.out.println("update=" + update);
        System.out.println("url=" + url);
        System.out.println("data=" + data);
        System.out.println("reason=" + (update ? reason : "Up to date: " + data));
        String out = System.getenv("GITHUB_OUTPUT");
        if (out != null) {
            Files.writeString(Path.of(out), "update=" + update + "\nurl=" + url + "\ndata=" + data + "\nreason="
                            + (update ? reason : "up to date") + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    /** The next few API versions after the one in [url], oldest first: v5_4, v5_5, v6_0, v6_1. */
    static List<String> newerVersions(String url) {
        List<String> list = new ArrayList<>();
        Matcher m = VERSION.matcher(url);
        if (!m.find()) return list;
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        for (int[] v : new int[][]{{major, minor + 1}, {major, minor + 2}, {major + 1, 0}, {major + 1, 1}}) {
            list.add(url.substring(0, m.start()) + "/api/v" + v[0] + "_" + v[1] + "/" + url.substring(m.end()));
        }
        return list;
    }
}
