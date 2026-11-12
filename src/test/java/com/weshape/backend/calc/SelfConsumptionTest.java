package com.weshape.backend.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checked against docs/research/self-consumption.md (P1–P4). */
@DisplayName("Solar self-consumption against the research")
class SelfConsumptionTest {

    @Test
    @DisplayName("ratio 1, no battery: 30% (20–41%), covering 28% [P1] and just under 30% [P2]")
    void noBattery() {
        Range s = SelfConsumption.share(1, 0);
        assertThat(s).isEqualTo(new Range(0.20, 0.30, 0.41));
        assertThat(s.contains(0.28)).isTrue();
        assertThat(s.contains(0.33)).isTrue(); // P3 top without smart control
    }

    @Test
    @DisplayName("a battery adds 13–30 points from 0.5 kWh/kWp; less below, no more above")
    void battery() {
        assertThat(SelfConsumption.share(1, 0.5)).isEqualTo(new Range(0.33, 0.50, 0.71));
        assertThat(SelfConsumption.share(1, 1.5)).isEqualTo(SelfConsumption.share(1, 0.5));
        Range quarter = SelfConsumption.share(1, 0.25);
        assertThat(quarter.typical()).isCloseTo(0.40, within(1e-12));
        assertThat(quarter.low()).isCloseTo(0.265, within(1e-12));
        assertThat(quarter.high()).isCloseTo(0.56, within(1e-12));
    }

    @Test
    @DisplayName("P1 example: 10 kWp, 6 kWh battery, measured 47%, falls inside")
    void verbraucherzentraleExample() {
        // About 8,800 kWh used with the heat pump, about 10,000 kWh made: ratio about 1.1.
        Range s = SelfConsumption.share(10000.0 / 8800, 0.6);
        assertThat(s.contains(0.47)).isTrue();
        assertThat(s.typical()).isCloseTo(0.50 * 0.88, within(1e-9));
    }

    @Test
    @DisplayName("P2 example: 5 kWp with 4 kWh, about 60%, inside the range")
    void htwExample() {
        Range s = SelfConsumption.share(5 * 958 / 4700.0, 0.8);
        assertThat(s.contains(0.60)).isTrue();
    }

    @Test
    @DisplayName("a system twice the use: the share halves, the kWh used at home stay the same")
    void largeSystem() {
        Range one = SelfConsumption.usedKwh(4000, 4000, 0, 4);
        Range two = SelfConsumption.usedKwh(8000, 4000, 0, 8);
        assertThat(two.typical()).isCloseTo(one.typical(), within(1e-9));
        assertThat(two.typical()).isCloseTo(1200, within(1e-9));
        assertThat(SelfConsumption.share(2, 0).typical()).isCloseTo(0.15, within(1e-12));
    }

    @Test
    @DisplayName("a small system keeps the ratio-1 share; at most 71% even with a big battery")
    void smallSystem() {
        assertThat(SelfConsumption.share(0.4, 0)).isEqualTo(SelfConsumption.share(1, 0));
        assertThat(SelfConsumption.share(0.01, 5)).isEqualTo(new Range(0.33, 0.50, 0.71));
    }

    @Test
    @DisplayName("used plus exported is always all the solar electricity")
    void split() {
        Range used = SelfConsumption.usedKwh(5000, 4000, 5, 5);
        Range out = SelfConsumption.exportedKwh(5000, 4000, 5, 5);
        assertThat(used.low() + out.high()).isCloseTo(5000, within(1e-9));
        assertThat(used.typical() + out.typical()).isCloseTo(5000, within(1e-9));
        assertThat(used.high() + out.low()).isCloseTo(5000, within(1e-9));
        assertThat(used.typical()).isCloseTo(5000 * 0.50 / 1.25, within(1e-9));
    }

    @Test
    @DisplayName("bad input is refused")
    void badInput() {
        assertThatThrownBy(() -> SelfConsumption.share(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelfConsumption.share(1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelfConsumption.usedKwh(1000, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelfConsumption.usedKwh(1000, 1000, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
