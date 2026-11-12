package com.weshape.backend.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checked against docs/research/firewood.md (W1, Bavarian State Institute of Forestry). */
@DisplayName("Firewood against the research")
class FirewoodTest {

    @Test
    @DisplayName("W1: the ranges are the table's ends; not known spans spruce fresh to beech dry")
    void ranges() {
        assertThat(Firewood.Kind.UNKNOWN.kwhPerM3.low()).isEqualTo(1199); // spruce, 50% water
        assertThat(Firewood.Kind.UNKNOWN.kwhPerM3.high()).isEqualTo(1907); // beech, 15% water
        assertThat(Firewood.Kind.HARDWOOD.kwhPerM3.high()).isEqualTo(1907);
        assertThat(Firewood.Kind.SOFTWOOD.kwhPerM3.high()).isEqualTo(1533); // pine, 15% water
        // The values used, as in docs/research/firewood.md.
        assertThat(Firewood.Kind.HARDWOOD.kwhPerM3).isEqualTo(new Range(1591, 1800, 1907));
        assertThat(Firewood.Kind.SOFTWOOD.kwhPerM3).isEqualTo(new Range(1199, 1400, 1533));
        assertThat(Firewood.Kind.MIXED.kwhPerM3).isEqualTo(new Range(1400, 1600, 1800));
        assertThat(Firewood.Kind.UNKNOWN.kwhPerM3).isEqualTo(new Range(1199, 1600, 1907));
        for (Firewood.Kind k : Firewood.Kind.values()) {
            assertThat(k.kwhPerM3.low()).isGreaterThanOrEqualTo(1199);
            assertThat(k.kwhPerM3.high()).isLessThanOrEqualTo(1907);
        }
    }

    @Test
    @DisplayName("W1: per m³, fresh wood gives about 11% less than dry; beech against spruce about 40% more")
    void moistureAndSpecies() {
        assertThat(1 - 1687.0 / 1907).isCloseTo(0.115, within(0.002));
        assertThat(1 - 1199.0 / 1348).isCloseTo(0.11, within(0.002));
        assertThat(1907.0 / 1348).isCloseTo(1.41, within(0.01));
    }

    @Test
    @DisplayName("8 m³ in an old stove: about 12,800 kWh in the wood, about 5,500 kWh of heat")
    void oldStove() {
        Range energy = Firewood.energyKwh(8, Firewood.Kind.UNKNOWN);
        assertThat(energy.typical()).isEqualTo(12800);
        Range heat = Firewood.heatKwh(8, Firewood.Kind.UNKNOWN, Boiler.Kind.STOVE_OLD);
        assertThat(heat.typical()).isCloseTo(5504, within(1.0));
        assertThat(heat.low()).isCloseTo(8 * 1199 * 0.35, within(0.01));
        assertThat(heat.high()).isCloseTo(8 * 1907 * 0.55, within(0.01));
    }

    @Test
    @DisplayName("the same heat from a log boiler needs about 4.2 m³ instead of 8")
    void logBoiler() {
        Range heat = Firewood.heatKwh(8, Firewood.Kind.UNKNOWN, Boiler.Kind.STOVE_OLD);
        Range m3 = Firewood.m3For(Range.exactly(heat.typical()), Firewood.Kind.UNKNOWN, Boiler.Kind.LOG_BOILER);
        assertThat(m3.typical()).isCloseTo(8 * 0.43 / 0.81, within(1e-9));
        assertThat(m3.typical()).isCloseTo(4.25, within(0.01));
        assertThatThrownBy(() -> Firewood.energyKwh(-1, Firewood.Kind.MIXED)).isInstanceOf(IllegalArgumentException.class);
    }
}
