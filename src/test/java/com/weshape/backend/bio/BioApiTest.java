package com.weshape.backend.bio;

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
@DisplayName("Biomass API")
class BioApiTest {

    private static final String ADA = "Bearer dev:ada:ada@weshape.energy";
    private static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("bio-api");
            BioTestFiles.write(DIR);
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
        mockMvc.perform(get("/api/v1/bio/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.radiusKm").value(25.0))
                .andExpect(jsonPath("$.layers.length()").value(3))
                .andExpect(jsonPath("$.layers[1].id").value("resource"))
                .andExpect(jsonPath("$.layers[2].unit").value("GWh"));
        mockMvc.perform(get("/api/v1/bio/tiles/resource/0/0/0.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/v1/bio/tiles/peat/0/0/0.png")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("values for a place need a signed-in user; the sea is 404")
    void point() throws Exception {
        mockMvc.perform(get("/api/v1/bio/point").param("lat", "46.77").param("lon", "23.59"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/bio/point").param("lat", "46.77").param("lon", "23.59").header("Authorization", ADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.density").value(1.58))
                .andExpect(jsonPath("$.homes").value(59400));
        mockMvc.perform(get("/api/v1/bio/point").param("lat", "35").param("lon", "5").header("Authorization", ADA))
                .andExpect(status().isNotFound());
    }
}
