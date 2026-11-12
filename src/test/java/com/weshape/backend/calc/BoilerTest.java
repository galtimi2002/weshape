package com.weshape.backend.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checked against the sources in docs/research/boilers.md (B1–B6). */
@DisplayName("Boilers and stoves against the research")
class BoilerTest {

    @Test
    @DisplayName("B1: field averages pellet 78%, log 81% (net); B2: pellets 65–85% in Austrian homes")
    void biomassField() {
        assertThat(Boiler.Kind.PELLET_BOILER.efficiency.typical()).isEqualTo(0.78);
        assertThat(Boiler.Kind.LOG_BOILER.efficiency.typical()).isEqualTo(0.81);
        assertThat(Boiler.Kind.PELLET_BOILER.efficiency.low()).isEqualTo(0.65);
        assertThat(Boiler.Kind.PELLET_BOILER.efficiency.high()).isEqualTo(0.85);
        // Measured in homes, never above the lab (77–86% gross [B1], 90–94% [B2]).
        assertThat(Boiler.Kind.LOG_BOILER.efficiency.high()).isLessThanOrEqualTo(0.86);
    }

    @Test
    @DisplayName("B3: condensing gas boilers about 83%, most 80–90%")
    void gas() {
        assertThat(Boiler.Kind.GAS_CONDENSING.efficiency).isEqualTo(new Range(0.80, 0.83, 0.90));
        assertThat(Boiler.Kind.GAS_CONDENSING.efficiency.contains(0.8286)).isTrue();
        assertThat(Boiler.Kind.GAS_CONDENSING.efficiency.contains(0.8321)).isTrue();
    }

    @Test
    @DisplayName("B5: stoves at least 65%, pellet stoves 79%, open fireplaces 30% at most")
    void ecodesign() {
        assertThat(Boiler.Kind.STOVE_MODERN.efficiency.low()).isEqualTo(0.65);
        assertThat(Boiler.Kind.PELLET_STOVE.efficiency.low()).isEqualTo(0.79);
        assertThat(Boiler.Kind.OPEN_FIREPLACE.efficiency.high()).isEqualTo(0.30);
        assertThat(Boiler.ECODESIGN_BOILER_MIN).isEqualTo(0.75);
    }

    @Test
    @DisplayName("B6: an old stove needs about 1.5 times the wood of a certified one")
    void oldStove() {
        double ratio = Boiler.Kind.STOVE_MODERN.efficiency.low() / Boiler.Kind.STOVE_OLD.efficiency.typical();
        assertThat(ratio).isCloseTo(1.5, within(0.02));
    }

    @Test
    @DisplayName("fuel = heat ÷ efficiency; replacing an old stove by a log boiler needs about half the wood")
    void fuel() {
        Range heat = Range.exactly(20000);
        assertThat(Boiler.fuelKwh(heat, Boiler.Kind.LOG_BOILER).typical()).isCloseTo(20000 / 0.81, within(0.01));
        assertThat(Boiler.fuelKwh(heat, Boiler.Kind.LOG_BOILER).low()).isCloseTo(20000 / 0.86, within(0.01));
        assertThat(Boiler.fuelKwh(heat, Boiler.Kind.LOG_BOILER).high()).isCloseTo(20000 / 0.70, within(0.01));
        double saved = 1 - Boiler.fuelKwh(heat, Boiler.Kind.LOG_BOILER).typical() / Boiler.fuelKwh(heat, Boiler.Kind.STOVE_OLD).typical();
        assertThat(saved).isCloseTo(1 - 0.43 / 0.81, within(1e-9));
    }

    @Test
    @DisplayName("heat from last winter's fuel; pellets about 5 MWh a tonne")
    void heatAndPellets() {
        assertThat(Boiler.heatKwh(10000, Boiler.Kind.STOVE_OLD)).isEqualTo(new Range(3500, 4300, 5500));
        // 15,600 kWh of heat with a pellet boiler at 78%: 20,000 kWh of pellets, 4 tonnes.
        assertThat(Boiler.pelletTonnes(Range.exactly(15600), Boiler.Kind.PELLET_BOILER).typical()).isCloseTo(4.0, within(1e-9));
        assertThatThrownBy(() -> Boiler.heatKwh(-1, Boiler.Kind.LOG_BOILER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("every efficiency is a proper share, and only the open fire is below a modern stove")
    void sane() {
        for (Boiler.Kind k : Boiler.Kind.values()) {
            assertThat(k.efficiency.low()).isGreaterThan(0);
            assertThat(k.efficiency.high()).isLessThanOrEqualTo(1);
        }
        assertThat(Boiler.Kind.STOVE_OLD.efficiency.typical()).isLessThan(Boiler.Kind.STOVE_MODERN.efficiency.typical());
        assertThat(Boiler.Kind.OPEN_FIREPLACE.efficiency.typical()).isLessThan(Boiler.Kind.STOVE_OLD.efficiency.typical());
    }
}
