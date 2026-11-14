package com.weshape.backend.calc;

import static com.weshape.backend.calc.Battery.Advice.BACKUP;
import static com.weshape.backend.calc.Battery.Advice.HYBRID_MAIN;
import static com.weshape.backend.calc.Battery.Advice.NONE;
import static com.weshape.backend.calc.Battery.Advice.OFF_GRID;
import static com.weshape.backend.calc.Battery.Advice.OPTIONAL;
import static com.weshape.backend.calc.Battery.CutLength.HOURS;
import static com.weshape.backend.calc.Battery.CutLength.OVER_DAY;
import static com.weshape.backend.calc.Battery.CutLength.UNDER_HOUR;
import static com.weshape.backend.calc.Battery.Cuts.DAILY;
import static com.weshape.backend.calc.Battery.Cuts.HARDLY;
import static com.weshape.backend.calc.Battery.Cuts.MONTHLY;
import static com.weshape.backend.calc.Battery.Cuts.NEVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The app's power cut answers onto the expert's table (section 4); docs/decisions/2026-11-14-power-cuts-back.md. */
@DisplayName("Battery advice from the app's answers")
class BatteryPlanTest {

    @Test
    @DisplayName("every answer pair gives the expert's row")
    void table() {
        assertThat(Battery.plan(true, false, NEVER, null)).isEqualTo(NONE);
        assertThat(Battery.plan(true, false, null, null)).isEqualTo(NONE);
        // Once or twice a year: under 60 min none, 60–300 min optional, a long event backup.
        assertThat(Battery.plan(true, false, HARDLY, UNDER_HOUR)).isEqualTo(NONE);
        assertThat(Battery.plan(true, false, HARDLY, HOURS)).isEqualTo(OPTIONAL);
        assertThat(Battery.plan(true, false, HARDLY, OVER_DAY)).isEqualTo(BACKUP);
        // Monthly: 1–3 short cuts optional; at least monthly backup; over 20 hours a month hybrid.
        assertThat(Battery.plan(true, false, MONTHLY, UNDER_HOUR)).isEqualTo(OPTIONAL);
        assertThat(Battery.plan(true, false, MONTHLY, HOURS)).isEqualTo(BACKUP);
        assertThat(Battery.plan(true, false, MONTHLY, OVER_DAY)).isEqualTo(HYBRID_MAIN);
        // Most days: short backup; hours or more hybrid.
        assertThat(Battery.plan(true, false, DAILY, UNDER_HOUR)).isEqualTo(BACKUP);
        assertThat(Battery.plan(true, false, DAILY, HOURS)).isEqualTo(HYBRID_MAIN);
        assertThat(Battery.plan(true, false, DAILY, OVER_DAY)).isEqualTo(HYBRID_MAIN);
    }

    @Test
    @DisplayName("no grid, or full independence wanted: off-grid, whatever the cuts")
    void offGrid() {
        assertThat(Battery.plan(false, false, null, null)).isEqualTo(OFF_GRID);
        assertThat(Battery.plan(true, true, NEVER, null)).isEqualTo(OFF_GRID);
        assertThat(Battery.plan(true, true, DAILY, HOURS)).isEqualTo(OFF_GRID);
        assertThatThrownBy(() -> Battery.plan(true, false, MONTHLY, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sizes: the advice's own band; cuts over a day essential loads × 1–3 days; off-grid daily use × 1–3 days")
    void sizes() {
        assertThat(Battery.kwh(NONE, OVER_DAY, null, false)).isEqualTo(Range.exactly(0));
        assertThat(Battery.kwh(OPTIONAL, HOURS, null, false)).isEqualTo(Range.of(5, 10));
        assertThat(Battery.kwh(BACKUP, UNDER_HOUR, null, false)).isEqualTo(Range.of(10, 13));
        assertThat(Battery.kwh(HYBRID_MAIN, HOURS, null, true)).isEqualTo(Range.of(15, 25));

        // A long event, use unknown: 5–10 kWh/day × 1–3 days ÷ 0.81 = 6.2–37.0, at least the backup's 10–13.
        Range longCut = Battery.kwh(BACKUP, OVER_DAY, null, false);
        assertThat(longCut.low()).isCloseTo(10, within(1e-9));
        assertThat(longCut.typical()).isCloseTo(7.5 * 2 / 0.81, within(1e-9));
        assertThat(longCut.high()).isCloseTo(10 * 3 / 0.81, within(1e-9));
        assertThat(longCut.high()).isCloseTo(Battery.sizeKwh(10, 3), within(1e-9)); // the expert's 30 kWh example

        // A small home (4–6 kWh a day) needs less than the essential-load band.
        Range small = Battery.kwh(BACKUP, OVER_DAY, new Range(4, 5, 6), false);
        assertThat(small.typical()).isCloseTo(5 * 2 / 0.81, within(1e-9));
        assertThat(small.high()).isCloseTo(6 * 3 / 0.81, within(1e-9));
        assertThat(small.low()).isCloseTo(10, within(1e-9));

        // Off-grid, 8–12 kWh a day: 9.9–44.4 kWh.
        Range off = Battery.kwh(OFF_GRID, null, new Range(8, 10, 12), false);
        assertThat(off.low()).isCloseTo(8 / 0.81, within(1e-9));
        assertThat(off.typical()).isCloseTo(10 * 2 / 0.81, within(1e-9));
        assertThat(off.high()).isCloseTo(12 * 3 / 0.81, within(1e-9));
        assertThatThrownBy(() -> Battery.kwh(OFF_GRID, null, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Battery.size(OFF_GRID, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
