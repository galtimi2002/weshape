package com.weshape.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Sign-in hint after a failed email sign-in")
class SignInHintTest {

    @TestConfiguration
    static class Accounts {
        /** One Google-only account; every other address is unknown or has a password. */
        @Bean
        @Primary
        SignInHints testHints() {
            return email -> email.equalsIgnoreCase("anna@gmail.com");
        }
    }

    @Autowired private MockMvc mockMvc;

    private org.springframework.test.web.servlet.ResultActions ask(String json, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/sign-in-hint")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip)
                .content(json));
    }

    @Test
    @DisplayName("open without a token; true only for a Google-only account")
    void answers() throws Exception {
        ask("{\"email\":\"anna@gmail.com\"}", "10.0.0.1").andExpect(status().isOk()).andExpect(jsonPath("$.google").value(true));
        ask("{\"email\":\" Anna@gmail.com \"}", "10.0.0.1").andExpect(jsonPath("$.google").value(true));
        ask("{\"email\":\"bela@example.com\"}", "10.0.0.1").andExpect(jsonPath("$.google").value(false));
        ask("{\"email\":\"not an email\"}", "10.0.0.1").andExpect(jsonPath("$.google").value(false));
        ask("{}", "10.0.0.1").andExpect(status().isOk()).andExpect(jsonPath("$.google").value(false));
    }

    @Test
    @DisplayName("after the hourly limit a client only gets false")
    void limited() throws Exception {
        for (int i = 0; i < SignInHintController.MAX_PER_HOUR; i++) {
            ask("{\"email\":\"anna@gmail.com\"}", "10.9.9.9").andExpect(jsonPath("$.google").value(true));
        }
        ask("{\"email\":\"anna@gmail.com\"}", "10.9.9.9").andExpect(jsonPath("$.google").value(false));
        ask("{\"email\":\"anna@gmail.com\"}", "10.9.9.8").andExpect(jsonPath("$.google").value(true));
    }

    @Test
    @DisplayName("the limiter lets a client in again once the window has passed")
    void window() {
        AtomicLong now = new AtomicLong(0);
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        HintLimiter l = new HintLimiter(2, 1000, clock);
        assertThat(l.allow("a")).isTrue();
        assertThat(l.allow("a")).isTrue();
        assertThat(l.allow("a")).isFalse();
        assertThat(l.allow("b")).isTrue();
        now.set(1000);
        assertThat(l.allow("a")).isTrue();
    }
}
