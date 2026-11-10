package com.weshape.backend.building;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Buildings API")
class BuildingApiTest {

    private static final String ADA = "Bearer dev:ada:ada@weshape.energy";
    private static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("buildings-api");
            Files.createDirectories(DIR.resolve("buildings"));
            Files.writeString(DIR.resolve("buildings/index.json"), BuildingDataTest.INDEX);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void files(DynamicPropertyRegistry registry) {
        registry.add("weshape.wind.local-dir", DIR::toString);
    }

    @TestConfiguration
    static class Reader {
        @Bean
        @Primary
        BuildingReader fakeBuildingReader() {
            return new BuildingDataTest.FakeReader();
        }
    }

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("the building at a place needs a signed-in user")
    void at() throws Exception {
        mockMvc.perform(get("/api/v1/buildings/at").param("lat", "46.7704").param("lon", "23.5914"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/buildings/at").param("lat", "46.7704").param("lon", "23.5914")
                        .header("Authorization", ADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.building.id").value("house"))
                .andExpect(jsonPath("$.building.areaM2").value(org.hamcrest.Matchers.closeTo(200.0, 0.5)))
                .andExpect(jsonPath("$.building.walls.length()").value(4))
                .andExpect(jsonPath("$.building.walls[0].facingDeg").isNumber())
                .andExpect(jsonPath("$.building.outline.length()").value(5))
                .andExpect(jsonPath("$.building.outline[0].length()").value(2))
                .andExpect(jsonPath("$.nearby[0].id").value("neighbour"))
                .andExpect(jsonPath("$.release").value("2026-10-21.0"));
        mockMvc.perform(get("/api/v1/buildings/at").param("lat", "95").param("lon", "5").header("Authorization", ADA))
                .andExpect(status().isBadRequest());
    }
}
