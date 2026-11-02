package com.weshape.backend.hydro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Hydro API")
class HydroApiTest {

    private static final String ADA = "Bearer dev:ada:ada@weshape.energy";
    private static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("hydro-api");
            TestMvtWriter.write(DIR, TestMvtWriter.cluj());
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
        mockMvc.perform(get("/api/v1/hydro/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.maxZoom").value(10))
                .andExpect(jsonPath("$.layers.length()").value(3))
                .andExpect(jsonPath("$.layers[0].id").value("potential"))
                .andExpect(jsonPath("$.layers[1].property").value("q"))
                .andExpect(jsonPath("$.layers[2].unit").value("m"))
                .andExpect(jsonPath("$.attribution").value(org.hamcrest.Matchers.containsString("RiverATLAS")));
        mockMvc.perform(get("/api/v1/hydro/tiles/10/579/361.pbf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Encoding", "gzip"))
                .andExpect(header().string("Content-Type", "application/x-protobuf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));
        mockMvc.perform(get("/api/v1/hydro/tiles/10/0/0.pbf")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/hydro/tiles/2/9/0.pbf")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the nearest reach needs a signed-in user; far from rivers is 404")
    void point() throws Exception {
        mockMvc.perform(get("/api/v1/hydro/point").param("lat", "46.77").param("lon", "23.59"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hydro/point").param("lat", "46.77").param("lon", "23.59").header("Authorization", ADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.potentialKw").value(790.0))
                .andExpect(jsonPath("$.availableFlow").value(14.4))
                .andExpect(jsonPath("$.driestMonthFlow").value(5.0))
                .andExpect(jsonPath("$.headM").value(7.0))
                .andExpect(jsonPath("$.ecologicalShare").value(0.1))
                .andExpect(jsonPath("$.source").value(org.hamcrest.Matchers.containsString("RiverATLAS")));
        mockMvc.perform(get("/api/v1/hydro/point").param("lat", "45").param("lon", "25").header("Authorization", ADA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/hydro/point").param("lat", "95").param("lon", "5").header("Authorization", ADA))
                .andExpect(status().isBadRequest());
    }
}
