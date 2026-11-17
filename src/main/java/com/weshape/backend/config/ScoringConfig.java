package com.weshape.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.weshape.backend.calc.Scoring;

/**
 * Reads the expert's scoring numbers ({@code calc/scoring.xlsx}) at start-up. A broken file stops the
 * start with the reason, so a mistake in the Excel file never reaches users.
 */
@Configuration
public class ScoringConfig {

    @Bean
    public Scoring scoring() {
        return Scoring.bundled();
    }
}
