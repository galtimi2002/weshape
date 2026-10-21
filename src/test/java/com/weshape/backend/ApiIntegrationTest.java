package com.weshape.backend;

import com.jayway.jsonpath.JsonPath;
import com.weshape.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WeShape API")
class ApiIntegrationTest {

    private static final String ADA = "Bearer dev:ada:ada@weshape.energy";
    private static final String BELA = "Bearer dev:bela:bela@weshape.energy";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void reset() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("ping and the language list are public")
    void publicEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/v1/languages").param("country", "HU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages.length()").value(19))
                .andExpect(jsonPath("$.default").value("en"))
                .andExpect(jsonPath("$.forCountry").value("hu"));
    }

    @Test
    @DisplayName("the profile needs a token")
    void profileRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    @Test
    @DisplayName("first sign-in creates the account with a unique code, English and system theme")
    void firstSignInCreatesAccount() throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me").header("Authorization", ADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@weshape.energy"))
                .andExpect(jsonPath("$.userCode", matchesPattern("WSE-[A-Z2-9]{4}-[A-Z2-9]{4}")))
                .andExpect(jsonPath("$.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.themeMode").value("SYSTEM"))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(body, "$.userCode");

        // The code is stable across calls...
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", ADA))
                .andExpect(jsonPath("$.userCode").value(code));

        // ...and a second person gets a different one.
        String other = mockMvc.perform(get("/api/v1/users/me").header("Authorization", BELA))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(other, "$.userCode")).isNotEqualTo(code);
        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("completing the profile without a language takes the country's language")
    void languageFollowsCountry() throws Exception {
        mockMvc.perform(put("/api/v1/users/me").header("Authorization", ADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ada","lastName":"Kovács","country":"hu"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("HU"))
                .andExpect(jsonPath("$.preferredLanguage").value("hu"))
                .andExpect(jsonPath("$.profileComplete").value(true));
    }

    @Test
    @DisplayName("a language chosen at registration wins over the country's")
    void explicitLanguageWins() throws Exception {
        mockMvc.perform(put("/api/v1/users/me").header("Authorization", ADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ada","lastName":"Pop","country":"RO","preferredLanguage":"en","themeMode":"DARK"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.themeMode").value("DARK"));
    }

    @Test
    @DisplayName("language and theme changes are saved and survive the next sign-in")
    void preferencesPersist() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences").header("Authorization", ADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLanguage":"zh","themeMode":"LIGHT"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/me/preferences").header("Authorization", ADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"themeMode":"DARK"}"""))
                .andExpect(jsonPath("$.preferredLanguage").value("zh"))
                .andExpect(jsonPath("$.themeMode").value("DARK"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", ADA))
                .andExpect(jsonPath("$.preferredLanguage").value("zh"))
                .andExpect(jsonPath("$.themeMode").value("DARK"));
    }

    @Test
    @DisplayName("unknown languages and countries are refused with a field error")
    void validation() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences").header("Authorization", ADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLanguage":"xx"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredLanguage").exists());

        mockMvc.perform(put("/api/v1/users/me").header("Authorization", ADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ada","lastName":"X","country":"ZZ"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.country").exists());

        mockMvc.perform(put("/api/v1/users/me").header("Authorization", ADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"","lastName":"X","country":"RO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
    }

    @Test
    @DisplayName("signing in with a new provider under a verified email keeps the same account and code")
    void accountLinking() throws Exception {
        String first = mockMvc.perform(get("/api/v1/users/me").header("Authorization", ADA))
                .andReturn().getResponse().getContentAsString();

        // Same email, different uid: dev tokens count as verified.
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer dev:ada-google:Ada@WeShape.energy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCode").value((String) JsonPath.read(first, "$.userCode")));

        assertThat(userRepository.count()).isEqualTo(1);
    }
}
