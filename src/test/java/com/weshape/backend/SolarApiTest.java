package com.weshape.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Solar API")
class SolarApiTest {

    private static final String ADA = "Bearer dev:ada:ada@weshape.energy";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("values for a place need a signed-in user")
    void pointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/solar/point").param("lat", "46.77").param("lon", "23.59"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cluj-Napoca gets plausible values (from the PVGIS grid, PVGIS itself is off in tests), and the months add up to the year")
    void pointValues() throws Exception {
        String json = mockMvc.perform(get("/api/v1/solar/point").param("lat", "46.77").param("lon", "23.59")
                        .header("Authorization", ADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gtiMonthly.length()").value(12))
                .andExpect(jsonPath("$.pvoutMonthly.length()").value(12))
                .andExpect(jsonPath("$.example").isBoolean())
                .andExpect(jsonPath("$.source").isString())
                .andReturn().getResponse().getContentAsString();

        double gti = ((Number) JsonPath.read(json, "$.gti")).doubleValue();
        double pvout = ((Number) JsonPath.read(json, "$.pvout")).doubleValue();
        int opta = ((Number) JsonPath.read(json, "$.opta")).intValue();
        assertThat(pvout).isBetween(1000.0, 1400.0);
        assertThat(gti).isBetween(pvout * 1.1, pvout * 1.35);
        assertThat(opta).isBetween(33, 39);

        List<Number> months = JsonPath.read(json, "$.pvoutMonthly");
        double sum = months.stream().mapToDouble(Number::doubleValue).sum();
        assertThat(sum).isCloseTo(pvout, within(2.0));
        assertThat(months.get(6).doubleValue()).isGreaterThan(months.get(0).doubleValue() * 3);
    }

    @Test
    @DisplayName("more sun in the south than in the north")
    void southBeatsNorth() throws Exception {
        double seville = pvout("37.39", "-5.98");
        double oslo = pvout("59.91", "10.75");
        assertThat(seville).isGreaterThan(oslo + 500);
    }

    @Test
    @DisplayName("works anywhere: deserts beat the rainforest, the south has its summer in January")
    void worldwide() throws Exception {
        assertThat(pvout("-23.6", "-69.0")).isGreaterThan(pvout("-3.1", "-60.0") + 300);
        String json = mockMvc.perform(get("/api/v1/solar/point").param("lat", "-33.87").param("lon", "151.21")
                        .header("Authorization", ADA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> months = JsonPath.read(json, "$.pvoutMonthly");
        assertThat(months.get(0).doubleValue()).isGreaterThan(months.get(6).doubleValue());
    }

    @Test
    @DisplayName("coordinates out of range are refused")
    void badCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/solar/point").param("lat", "95").param("lon", "23")
                        .header("Authorization", ADA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lat").exists());
    }

    @Test
    @DisplayName("the layer list and pictures are public and line up")
    void layers() throws Exception {
        mockMvc.perform(get("/api/v1/solar/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layers.length()").value(3))
                .andExpect(jsonPath("$.layers[0].id").value("gti"))
                .andExpect(jsonPath("$.layers[1].unit").value("kWh/kWp"))
                .andExpect(jsonPath("$.bounds.length()").value(4));

        byte[] png = mockMvc.perform(get("/api/v1/solar/layers/pvout.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(2048);
        assertThat(image.getHeight()).isEqualTo(1500);

        mockMvc.perform(get("/api/v1/solar/layers/wind.png")).andExpect(status().isNotFound());
    }

    private double pvout(String lat, String lon) throws Exception {
        String json = mockMvc.perform(get("/api/v1/solar/point").param("lat", lat).param("lon", lon)
                        .header("Authorization", ADA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.pvout")).doubleValue();
    }
}
