package com.weshape.backend.solar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Asks PVGIS (the European Commission's Photovoltaic Geographical Information System) what a
 * 1 kWp system tilted at the optimum angle would produce at a place.
 *
 * <p>The answer gives everything the app shows: yearly and monthly PV output (kWh/kWp), yearly
 * and monthly irradiation on the tilted modules (kWh/m², the same quantity as GTI) and the
 * optimum tilt. Free, no key, at most 30 requests a second per address, and it refuses calls
 * straight from a browser, so the backend makes them.
 *
 * <p>Plain Java on purpose: {@link SolarGridBuilder} uses it without Spring.
 */
public class PvgisClient {

    public static final String DEFAULT_URL = "https://re.jrc.ec.europa.eu/api/v5_3/PVcalc";

    /** Covers the whole globe; used where the default satellite database does not reach. */
    private static final String GLOBAL_DATABASE = "PVGIS-ERA5";

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String url;
    private final Duration timeout;

    public PvgisClient(String url, Duration timeout) {
        this.url = url;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * Values for one place.
     *
     * @throws NoSolarDataException over the sea, or anywhere PVGIS has no data
     * @throws IOException          PVGIS could not be reached or answered with something unexpected
     */
    public SolarPoint fetch(double lat, double lon) throws IOException {
        Answer first = call(lat, lon, null);
        if (first.ok()) return parse(first.body(), lat, lon);
        if (first.overSea()) throw new NoSolarDataException("No solar data at this location: it is over the sea");
        Answer second = call(lat, lon, GLOBAL_DATABASE);
        if (second.ok()) return parse(second.body(), lat, lon);
        throw new NoSolarDataException("No solar data at this location: " + second.message());
    }

    /**
     * Which data PVGIS currently serves, e.g. "PVGIS-SARAH3 2005-2023", read from an answer for
     * a reference place. When this changes, the map grid is due for a rebuild.
     */
    public String dataVersion(double lat, double lon) throws IOException {
        Answer answer = call(lat, lon, null);
        if (!answer.ok()) throw new IOException("PVGIS refused the reference place: " + answer.message());
        JsonNode meteo = json.readTree(answer.body()).path("inputs").path("meteo_data");
        return meteo.path("radiation_db").asText("?") + " " + meteo.path("year_min").asText("?") + "-" + meteo.path("year_max").asText("?");
    }

    /** True when this address answers like a PVGIS PVcalc endpoint (used to spot a new API version). */
    public boolean answers(double lat, double lon) {
        try {
            return call(lat, lon, null).ok();
        } catch (IOException e) {
            return false;
        }
    }

    private record Answer(int status, String body, String message) {
        boolean ok() {
            return status == 200;
        }

        boolean overSea() {
            return message != null && message.toLowerCase(Locale.ROOT).contains("sea");
        }
    }

    private Answer call(double lat, double lon, String database) throws IOException {
        // Modules face the equator: south in the northern hemisphere (aspect 0), north in the southern (180).
        String query = String.format(Locale.ROOT,
                "?lat=%.5f&lon=%.5f&peakpower=1&loss=14&optimalinclination=1&aspect=%d&outputformat=json%s",
                lat, lon, lat >= 0 ? 0 : 180, database == null ? "" : "&raddatabase=" + database);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + query))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "WeShapeEnergy/0.1 (weshapeenergy.com)")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for PVGIS", e);
        }
        int status = response.statusCode();
        if (status == 200) return new Answer(200, response.body(), null);
        if (status == 400) return new Answer(400, null, message(response.body()));
        throw new IOException("PVGIS answered " + status);
    }

    private String message(String body) {
        try {
            JsonNode node = json.readTree(body);
            return node.path("message").asText(body);
        } catch (IOException e) {
            return body;
        }
    }

    /** Reads a PVcalc JSON answer. */
    SolarPoint parse(String body, double lat, double lon) throws IOException {
        JsonNode root = json.readTree(body);
        JsonNode totals = root.path("outputs").path("totals").path("fixed");
        JsonNode monthly = root.path("outputs").path("monthly").path("fixed");
        if (!totals.has("E_y") || !monthly.isArray() || monthly.size() != 12) {
            throw new IOException("Unexpected PVGIS answer");
        }
        double[] pvMonthly = new double[12];
        double[] gtiMonthly = new double[12];
        for (JsonNode m : monthly) {
            int month = m.path("month").asInt();
            if (month < 1 || month > 12) throw new IOException("Unexpected month in PVGIS answer");
            pvMonthly[month - 1] = m.path("E_m").asDouble();
            gtiMonthly[month - 1] = m.path("H(i)_m").asDouble();
        }
        double opta = root.path("inputs").path("mounting_system").path("fixed").path("slope").path("value").asDouble();
        String database = root.path("inputs").path("meteo_data").path("radiation_db").asText("");
        String source = database.isEmpty() ? "PVGIS" : "PVGIS (" + database + ")";
        return new SolarPoint(lat, lon, totals.path("H(i)_y").asDouble(), totals.path("E_y").asDouble(), opta,
                gtiMonthly, pvMonthly, source, false);
    }
}
