package com.weshape.backend.calc;

import static com.weshape.backend.calc.Scoring.Criterion.COVERAGE;
import static com.weshape.backend.calc.Scoring.Criterion.ENVIRONMENT;
import static com.weshape.backend.calc.Scoring.Criterion.MAINTENANCE;
import static com.weshape.backend.calc.Scoring.Criterion.PAYBACK;
import static com.weshape.backend.calc.Scoring.Criterion.RELIABILITY;
import static com.weshape.backend.calc.Scoring.Criterion.SAVINGS;
import static com.weshape.backend.calc.Scoring.Criterion.SITE_FIT;
import static com.weshape.backend.calc.Scoring.Criterion.UPFRONT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The expert's scoring numbers from calc/scoring.xlsx; docs/decisions/2026-11-17-scoring-inputs.md. */
@DisplayName("Scoring numbers from the Excel file")
class ScoringTest {

    private static final Scoring S = Scoring.bundled();

    @Test
    @DisplayName("the shipped file has the expert's reference values")
    void references() {
        assertThat(S.references().get(PAYBACK)).isEqualTo(new Scoring.Reference(3, 20, false));
        assertThat(S.references().get(SAVINGS)).isEqualTo(new Scoring.Reference(1, 0, false));
        assertThat(S.references().get(UPFRONT)).isEqualTo(new Scoring.Reference(450, 36000, true));
        assertThat(S.references().get(MAINTENANCE)).isEqualTo(new Scoring.Reference(0, 100, false));
        assertThat(S.references().get(RELIABILITY)).isEqualTo(new Scoring.Reference(23, 4, false));
        assertThat(S.references().get(SITE_FIT)).isEqualTo(new Scoring.Reference(100, 0, false));
        assertThat(S.otherSegments().get(Scoring.Segment.COMMUNITY)).isEqualTo(new Scoring.Reference(45000, 1800000, true));
    }

    @Test
    @DisplayName("the shipped file has the expert's weights, each column 100%")
    void weights() {
        var upfront = S.weights().get(Scoring.Priority.UPFRONT);
        assertThat(upfront.get(UPFRONT)).isCloseTo(0.35, within(1e-9));
        assertThat(S.weights().get(Scoring.Priority.ENVIRONMENT).get(ENVIRONMENT)).isCloseTo(0.40, within(1e-9));
        assertThat(S.weights().get(Scoring.Priority.RELIABLE).get(RELIABILITY)).isCloseTo(0.30, within(1e-9));
        assertThat(S.weights().get(Scoring.Priority.RUNNING).get(SAVINGS)).isCloseTo(0.25, within(1e-9));
        for (var p : Scoring.Priority.values()) {
            assertThat(S.weights().get(p).values().stream().mapToDouble(Double::doubleValue).sum())
                    .as(p.name()).isCloseTo(1, within(1e-9));
        }
    }

    @Test
    @DisplayName("the shipped file has the expert's limits")
    void limits() {
        assertThat(S.limit(Scoring.Limit.PAYBACK_MAX_YEARS)).isEqualTo(20);
        assertThat(S.limit(Scoring.Limit.SAVINGS_MIN_SHARE)).isCloseTo(0.10, within(1e-9));
        assertThat(S.limit(Scoring.Limit.COVERAGE_MIN_SHARE)).isCloseTo(0.25, within(1e-9));
        assertThat(S.limit(Scoring.Limit.RELIABILITY_MIN_HOURS)).isEqualTo(4);
    }

    @Test
    @DisplayName("points: 100 at best, 0 at worst, proportional between, log for cost")
    void points() {
        assertThat(S.points(PAYBACK, 3)).isEqualTo(100);
        assertThat(S.points(PAYBACK, 20)).isEqualTo(0);
        assertThat(S.points(PAYBACK, 11.5)).isCloseTo(50, within(1e-9));
        assertThat(S.points(PAYBACK, 1)).isEqualTo(100);
        assertThat(S.points(PAYBACK, 40)).isEqualTo(0);
        assertThat(S.points(SAVINGS, 0.26)).isCloseTo(26, within(1e-9));
        assertThat(S.points(RELIABILITY, 13.5)).isCloseTo(50, within(1e-9));
        // Log scale: the geometric middle of €450 and €36,000 (about €4,025) gets 50 points.
        assertThat(S.points(UPFRONT, Math.sqrt(450 * 36000))).isCloseTo(50, within(1e-9));
        assertThat(S.points(UPFRONT, 1000)).isGreaterThan(80);
    }

    @Test
    @DisplayName("score: weighted sum; perfect everywhere is 100, a missing criterion counts 0")
    void score() {
        Map<Scoring.Criterion, Double> best = Map.of(PAYBACK, 3.0, SAVINGS, 1.0, COVERAGE, 1.0, UPFRONT, 450.0,
                MAINTENANCE, 0.0, ENVIRONMENT, 1.0, RELIABILITY, 23.0, SITE_FIT, 100.0);
        for (var p : Scoring.Priority.values()) assertThat(S.score(p, best)).isCloseTo(100, within(1e-9));
        // Only the upfront cost known and perfect: 35 points under "lower upfront cost".
        assertThat(S.score(Scoring.Priority.UPFRONT, Map.of(UPFRONT, 450.0))).isCloseTo(35, within(1e-9));
    }

    @Test
    @DisplayName("a column not adding up to 100% is refused with the reason")
    void badWeights() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/calc/scoring-weights-not-100.xlsx")) {
            assertThatThrownBy(() -> Scoring.parse(in))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("\"upfront\" adds up to 108");
        }
    }

    @Test
    @DisplayName("a missing criterion row is refused with the reason")
    void missingRow() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/calc/scoring-row-missing.xlsx")) {
            assertThatThrownBy(() -> Scoring.parse(in))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Criteria: no row with the key \"upfront\"");
        }
    }

    @Test
    @DisplayName("a file that is not an Excel workbook is refused")
    void notXlsx() {
        assertThatThrownBy(() -> Scoring.parse(new ByteArrayInputStream("hello".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
