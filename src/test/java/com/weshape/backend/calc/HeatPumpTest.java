package com.weshape.backend.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checked against the sources in docs/research/heat-pumps.md (R1–R6). */
@DisplayName("Heat pumps against the research")
class HeatPumpTest {

    @Test
    @DisplayName("R1: air 3.4 (2.6–4.9) and ground 4.3 (3.6–5.4) in cool-temperate homes")
    void field() {
        assertThat(HeatPump.seasonalFactor(HeatPump.Kind.AIR, HeatDemand.Zone.COOL_TEMPERATE)).isEqualTo(new Range(2.6, 3.4, 4.9));
        assertThat(HeatPump.seasonalFactor(HeatPump.Kind.GROUND, HeatDemand.Zone.COOL_TEMPERATE)).isEqualTo(new Range(3.6, 4.3, 5.4));
        assertThat(HeatPump.seasonalFactor(HeatPump.Kind.GROUND, HeatDemand.Zone.COLD)).isEqualTo(new Range(3.6, 4.3, 5.4));
    }

    @Test
    @DisplayName("R3: colder climate about 14% worse, warmer about 25% better than average")
    void climateRatios() {
        // 4.44 ÷ 5.15 = 0.862, 3.12 ÷ 3.63 = 0.860; 6.51 ÷ 5.15 = 1.264, 4.47 ÷ 3.63 = 1.231.
        assertThat(HeatPump.COLDER_RATIO).isCloseTo(0.861, within(0.001));
        assertThat(HeatPump.WARMER_RATIO).isCloseTo(1.248, within(0.001));
        Range cold = HeatPump.seasonalFactor(HeatPump.Kind.AIR, HeatDemand.Zone.COLD);
        assertThat(cold.typical()).isCloseTo(2.93, within(0.01));
        assertThat(cold.low()).isCloseTo(2.24, within(0.01));
        assertThat(cold.high()).isCloseTo(4.22, within(0.01));
        Range mild = HeatPump.seasonalFactor(HeatPump.Kind.AIR, HeatDemand.Zone.MILD);
        assertThat(mild.typical()).isCloseTo(4.24, within(0.01));
        assertThat(mild.low()).isCloseTo(3.24, within(0.01));
        assertThat(mild.high()).isCloseTo(6.11, within(0.01));
    }

    @Test
    @DisplayName("no heating season in hot zones")
    void hot() {
        assertThatThrownBy(() -> HeatPump.seasonalFactor(HeatPump.Kind.AIR, HeatDemand.Zone.HOT_HUMID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeatPump.seasonalFactor(HeatPump.Kind.GROUND, HeatDemand.Zone.HOT_ARID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("electricity = heat ÷ factor; the best factor gives the least electricity")
    void electricity() {
        Range heat = new Range(20000, 25000, 30000);
        Range kwh = HeatPump.electricityKwh(heat, HeatPump.seasonalFactor(HeatPump.Kind.AIR, HeatDemand.Zone.COOL_TEMPERATE));
        assertThat(kwh.low()).isCloseTo(20000 / 4.9, within(0.01));
        assertThat(kwh.typical()).isCloseTo(25000 / 3.4, within(0.01));
        assertThat(kwh.high()).isCloseTo(30000 / 2.6, within(0.01));
    }

    @Test
    @DisplayName("R4, R5: 1-star 3.1–3.3, 3-star 3.7–4.38, 5-star 5–5.85")
    void coolingClasses() {
        assertThat(HeatPump.Cooling.OLD.seer.low()).isEqualTo(3.1);
        assertThat(HeatPump.Cooling.STANDARD.seer.high()).isEqualTo(4.38);
        assertThat(HeatPump.Cooling.EFFICIENT.seer.high()).isEqualTo(5.85);
    }

    @Test
    @DisplayName("R5 cross-check: a 5-star unit uses about 740 kWh where a 3-star uses 1,008 (−27%)")
    void coolingAgainstLabels() {
        Range load = Range.exactly(1008 * 4.0); // the load a 3-star unit (4.0) covers with 1,008 kWh
        double efficient = HeatPump.coolingElectricityKwh(load, HeatPump.Cooling.EFFICIENT).typical();
        assertThat(efficient).isCloseTo(740, within(15.0));
        assertThat(1 - efficient / 1008).isCloseTo(1 - 740.0 / 1008, within(0.015));
    }

    @Test
    @DisplayName("R6: a heat pump water heater saves about 62% against an electric tank")
    void waterHeater() {
        Range saving = HeatPump.waterHeaterSaving();
        assertThat(saving.low()).isCloseTo(0.627, within(0.001)); // COP 2.2
        assertThat(saving.high()).isCloseTo(0.672, within(0.001)); // COP 2.5
        // Measured: 61.5% (60-gallon unit) and 62.5% on average at 60 gallons a day.
        assertThat(saving.low()).isCloseTo(0.62, within(0.01));
    }
}
