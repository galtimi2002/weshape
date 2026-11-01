package com.weshape.backend.wind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
@DisplayName("Wind API")
class WindApiTest {

    private static final String ADA = "Bearer dev:ada:ada@weshape.energy";
    private static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("wind-api");
            TestCogWriter.writeWorld(DIR, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void windFiles(DynamicPropertyRegistry registry) {
        registry.add("weshape.wind.local-dir", DIR::toString);
    }

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("the layer list and map tiles are public")
    void publicParts() throws Exception {
        mockMvc.perform(get("/api/v1/wind/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.layers.length()").value(3))
                .andExpect(jsonPath("$.layers[0].id").value("speed"))
                .andExpect(jsonPath("$.layers[1].scale").value("log"))
                .andExpect(jsonPath("$.heights.length()").value(5))
                .andExpect(jsonPath("$.attribution").value(org.hamcrest.Matchers.containsString("Global Wind Atlas")));
        mockMvc.perform(get("/api/v1/wind/tiles/speed/0/0/0.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));
        mockMvc.perform(get("/api/v1/wind/tiles/height-150/1/1/0.png")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/wind/tiles/rain/0/0/0.png")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/wind/tiles/speed/2/9/0.png")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("values for a place need a signed-in user, and the sea gives 404")
    void point() throws Exception {
        mockMvc.perform(get("/api/v1/wind/point").param("lat", "46.77").param("lon", "23.59"))
                .andExpect(status().isUnauthorized());
        String json = mockMvc.perform(get("/api/v1/wind/point").param("lat", "46.77").param("lon", "23.59")
                        .header("Authorization", ADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speeds.length()").value(5))
                .andExpect(jsonPath("$.speeds[3].height").value(150))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).contains("speed100", "powerDensity100", "Global Wind Atlas");
        mockMvc.perform(get("/api/v1/wind/point").param("lat", "35").param("lon", "5").header("Authorization", ADA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/wind/point").param("lat", "95").param("lon", "5").header("Authorization", ADA))
                .andExpect(status().isBadRequest());
    }
}
