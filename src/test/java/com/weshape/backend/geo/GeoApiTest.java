package com.weshape.backend.geo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Geothermal API")
class GeoApiTest {

    private static final String ADA = "Bearer dev:ada:ada@weshape.energy";
    private static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("geo-api");
            GeoTestFiles.write(DIR);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void files(DynamicPropertyRegistry registry) {
        registry.add("weshape.wind.local-dir", DIR::toString);
    }

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("the layer list and the tiles are public")
    void publicParts() throws Exception {
        mockMvc.perform(get("/api/v1/geo/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.maxZoom").value(7))
                .andExpect(jsonPath("$.depths.length()").value(4))
                .andExpect(jsonPath("$.layers[0].id").value("ground"))
                .andExpect(jsonPath("$.layers[1].ranges.5[1]").value(260))
                .andExpect(jsonPath("$.layers[2].unit").value("mW/m²"));
        mockMvc.perform(get("/api/v1/geo/tiles/depth-3/0/0/0.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/v1/geo/tiles/ground/5/3/3.png")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/geo/tiles/lava/0/0/0.png")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("values for a place need a signed-in user; the sea is 404")
    void point() throws Exception {
        mockMvc.perform(get("/api/v1/geo/point").param("lat", "46.77").param("lon", "23.59"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/geo/point").param("lat", "46.77").param("lon", "23.59").header("Authorization", ADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groundTemperature").value(10.5))
                .andExpect(jsonPath("$.heatFlow").value(68.0))
                .andExpect(jsonPath("$.depths[3].depthKm").value(5))
                .andExpect(jsonPath("$.conductivity").value(2.5));
        mockMvc.perform(get("/api/v1/geo/point").param("lat", "35").param("lon", "5").header("Authorization", ADA))
                .andExpect(status().isNotFound());
    }
}
