package com.weshape.backend.solar;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Solar data")
class SolarDataTest {

    private static String cluj() throws IOException {
        try (InputStream in = SolarDataTest.class.getResourceAsStream("/pvgis/cluj.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("a PVGIS answer becomes yearly, monthly and tilt values")
    void parsesPvgis() throws IOException {
        SolarPoint p = new PvgisClient("http://unused", Duration.ofSeconds(1)).parse(cluj(), 46.77, 23.59);
        assertThat(p.pvout()).isEqualTo(1212.84);
        assertThat(p.gti()).isEqualTo(1526.6);
        assertThat(p.opta()).isEqualTo(38);
        assertThat(p.pvoutMonthly()[6]).isEqualTo(138.79);
        assertThat(p.gtiMonthly()[0]).isEqualTo(61.86);
        assertThat(p.source()).isEqualTo("PVGIS (PVGIS-SARAH3)");
        assertThat(p.example()).isFalse();
    }

    @Test
    @DisplayName("asks for modules facing the equator, falls back to the global database, reports the sea")
    void talksToPvgis() throws Exception {
        List<String> queries = new CopyOnWriteArrayList<>();
        String body = cluj();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            String q = exchange.getRequestURI().getQuery();
            queries.add(q);
            String answer;
            int status;
            if (q.contains("lat=10.00000")) {
                status = 400;
                answer = "{\"message\":\"Location over the sea. Please, select another location\",\"status\":400}";
            } else if (q.contains("lat=-33") && !q.contains("raddatabase")) {
                status = 400;
                answer = "{\"message\":\"Location not covered by the selected solar radiation database\",\"status\":400}";
            } else {
                status = 200;
                answer = body;
            }
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            PvgisClient client = new PvgisClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api", Duration.ofSeconds(5));
            assertThat(client.fetch(46.77, 23.59).pvout()).isEqualTo(1212.84);
            assertThat(queries.get(0)).contains("aspect=0").contains("optimalinclination=1").contains("peakpower=1");

            client.fetch(-33.87, 151.21);
            assertThat(queries.get(1)).contains("aspect=180");
            assertThat(queries.get(2)).contains("raddatabase=PVGIS-ERA5");

            assertThatThrownBy(() -> client.fetch(10, -30)).isInstanceOf(NoSolarDataException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("the grid fills gaps and interpolates between samples")
    void grid() throws IOException {
        String csv = String.join("\n",
                "lat,lon,gti_kwh_m2,pvout_kwh_kwp,opta_deg",
                "46.0,23.0,1500,1200,38",
                "46.0,24.0,1600,1300,38",
                "47.0,23.0,,,", // sea
                "");
        SolarGrid grid = new SolarGrid(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(grid.isLoaded()).isTrue();
        assertThat(grid.value(SolarLayer.PVOUT, 46.0, 23.0)).isCloseTo(1200, within(0.01));
        assertThat(grid.value(SolarLayer.PVOUT, 46.0, 23.5)).isBetween(1200.0, 1300.0);
        assertThat(grid.value(SolarLayer.PVOUT, 46.0, 23.25)).isBetween(1200.0, 1300.0);
        assertThat(grid.value(SolarLayer.OPTA, 10, -100)).isCloseTo(38, within(0.01)); // filled from far away
        assertThat(grid.value(SolarLayer.GTI, 80, 0)).isNaN();
    }

    @Test
    @DisplayName("the monthly check knows which PVGIS versions would be newer")
    void newerVersions() {
        assertThat(SolarDataCheck.newerVersions("https://re.jrc.ec.europa.eu/api/v5_3/PVcalc")).containsExactly(
                "https://re.jrc.ec.europa.eu/api/v5_4/PVcalc",
                "https://re.jrc.ec.europa.eu/api/v5_5/PVcalc",
                "https://re.jrc.ec.europa.eu/api/v6_0/PVcalc",
                "https://re.jrc.ec.europa.eu/api/v6_1/PVcalc");
    }

    @Test
    @DisplayName("the grid builder samples Europe finely and the world coarsely")
    void builderPoints() {
        List<double[]> points = SolarGridBuilder.points();
        assertThat(points).hasSizeBetween(18_000, 21_000);
        assertThat(points.stream().filter(p -> p[0] == 46.5 && p[1] == 23.5)).hasSize(1);
        assertThat(points.stream().filter(p -> p[0] == -34 && p[1] == 150)).hasSize(1);
    }
}
