package com.weshape.backend.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checked against docs/research/co2.md (C1–C4); expected values recomputed by hand. */
@DisplayName("CO₂ per kWh against the research")
class Co2Test {

    @Test
    @DisplayName("natural gas: 202 g burning + 0.6 methane and N₂O + 33 supply chain = 236 g/kWh")
    void naturalGas() {
        Range g = Co2.perKwh(Co2.Fuel.NATURAL_GAS);
        assertThat(g.typical()).isCloseTo(236.065, within(0.001));
        assertThat(g.low()).isCloseTo(212.405, within(0.001));
        assertThat(g.high()).isCloseTo(278.724, within(0.001));
        assertThat(56100 * Co2.KG_PER_TJ_TO_G_PER_KWH).isCloseTo(201.96, within(1e-9));
    }

    @Test
    @DisplayName("LPG 255, kerosene 315, heating oil 331, coal 433, lignite 457 g/kWh")
    void oilAndCoal() {
        assertThat(Co2.perKwh(Co2.Fuel.HEATING_OIL).typical()).isCloseTo(331.332, within(0.001));
        assertThat(Co2.perKwh(Co2.Fuel.COAL).typical()).isCloseTo(433.468, within(0.001));
        assertThat(Co2.perKwh(Co2.Fuel.LPG).typical()).isCloseTo(255.155, within(0.001));
        assertThat(Co2.perKwh(Co2.Fuel.KEROSENE).typical()).isCloseTo(314.742, within(0.001));
        assertThat(Co2.perKwh(Co2.Fuel.LIGNITE).typical()).isCloseTo(456.508, within(0.001));
        // Order of the fossil fuels, cleanest first.
        assertThat(Co2.perKwh(Co2.Fuel.NATURAL_GAS).typical()).isLessThan(Co2.perKwh(Co2.Fuel.LPG).typical());
        assertThat(Co2.perKwh(Co2.Fuel.LPG).typical()).isLessThan(Co2.perKwh(Co2.Fuel.KEROSENE).typical());
        assertThat(Co2.perKwh(Co2.Fuel.KEROSENE).typical()).isLessThan(Co2.perKwh(Co2.Fuel.HEATING_OIL).typical());
        assertThat(Co2.perKwh(Co2.Fuel.HEATING_OIL).typical()).isLessThan(Co2.perKwh(Co2.Fuel.COAL).typical());
        assertThat(Co2.perKwh(Co2.Fuel.COAL).typical()).isLessThan(Co2.perKwh(Co2.Fuel.LIGNITE).typical());
    }

    @Test
    @DisplayName("wood: CO₂ not counted; methane, N₂O and supply chain give 46 g (18–128); pellets 71")
    void wood() {
        Range g = Co2.perKwh(Co2.Fuel.FIREWOOD);
        assertThat(g.typical()).isCloseTo(45.861, within(0.001));
        assertThat(g.low()).isCloseTo(17.579, within(0.001));
        assertThat(g.high()).isCloseTo(127.762, within(0.001));
        assertThat(Co2.perKwh(Co2.Fuel.PELLETS).typical()).isCloseTo(70.531, within(0.001));
        assertThat(Co2.biogenicCo2PerKwh(Co2.Fuel.FIREWOOD).typical()).isCloseTo(403.2, within(1e-9));
        assertThat(Co2.biogenicCo2PerKwh(Co2.Fuel.NATURAL_GAS)).isEqualTo(Range.exactly(0));
    }

    @Test
    @DisplayName("electricity: Romania 251 (243–251, 2025), Hungary 163, India 670; lower case works")
    void countries() {
        Co2.Electricity ro = Co2.electricity("ro");
        assertThat(ro.known()).isTrue();
        assertThat(ro.year()).isEqualTo(2025);
        assertThat(ro.gPerKwh()).isEqualTo(new Range(243, 251, 251));
        assertThat(Co2.electricity("HU").gPerKwh().typical()).isEqualTo(163);
        assertThat(Co2.electricity("IN").gPerKwh().typical()).isEqualTo(670);
        assertThat(Co2.electricity("FR").gPerKwh().typical()).isEqualTo(41);
        assertThat(Co2.electricity("XK").gPerKwh().typical()).isEqualTo(901);
    }

    @Test
    @DisplayName("unknown country or none: world average 458 (2025), marked as not known")
    void unknown() {
        for (String c : new String[] {"ZZ", "CF", null}) {
            Co2.Electricity e = Co2.electricity(c);
            assertThat(e.known()).isFalse();
            assertThat(e.gPerKwh()).isEqualTo(new Range(458, 458, 483));
        }
    }

    @Test
    @DisplayName("the table: 212 countries, two-letter codes, data from 2022 on, no zero values")
    void table() throws Exception {
        assertThat(Co2.countries()).isEqualTo(212);
        try (var in = getClass().getClassLoader().getResourceAsStream("calc/electricity-co2.csv")) {
            List<String> rows = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .filter(l -> !l.startsWith("#") && !l.startsWith("country")).toList();
            assertThat(rows).hasSize(212);
            for (String row : rows) {
                String[] p = row.split(",");
                assertThat(p[0]).matches("[A-Z]{2}");
                assertThat(Integer.parseInt(p[1])).isBetween(2022, 2026);
                assertThat(Double.parseDouble(p[2])).isGreaterThan(10);
                assertThat(Double.parseDouble(p[4])).isLessThan(1400);
            }
        }
    }

    @Test
    @DisplayName("Romania, 10,000 kWh of heat: air heat pump about 740 kg, condensing gas boiler about 2,840 kg")
    void heatPumpAgainstGas() {
        Range heat = Range.exactly(10000);
        Range hp = Co2.kg(HeatPump.electricityKwh(heat, HeatPump.seasonalFactor(HeatPump.Kind.AIR, HeatDemand.Zone.COOL_TEMPERATE)),
                Co2.electricity("RO").gPerKwh());
        Range gas = Co2.kg(Boiler.fuelKwh(heat, Boiler.Kind.GAS_CONDENSING), Co2.perKwh(Co2.Fuel.NATURAL_GAS));
        assertThat(hp.typical()).isCloseTo(738.2, within(0.1));
        assertThat(gas.typical()).isCloseTo(2844.2, within(0.1));
        assertThat(hp.high()).isLessThan(gas.low());
    }
}
