package com.weshape.backend.calc;

import static com.weshape.backend.calc.GasBottle.Unit.GALLON;
import static com.weshape.backend.calc.GasBottle.Unit.KG;
import static com.weshape.backend.calc.GasBottle.Unit.LITRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bottled gas by number and size in litres, kilograms or gallons; docs/decisions/2026-11-19-question-changes.md. */
@DisplayName("Bottled gas")
class GasBottleTest {

    private static final double E = 1e-9;

    @Test
    @DisplayName("a size in kg is the gas itself")
    void kilograms() {
        assertThat(GasBottle.kgPerBottle(11, KG)).isEqualTo(Range.exactly(11));
    }

    @Test
    @DisplayName("a size in litres is the bottle's volume: filled to 80–85%, at 0.51–0.58 kg/L")
    void litres() {
        Range kg = GasBottle.kgPerBottle(26, LITRE);
        assertThat(kg.low()).isCloseTo(26 * 0.80 * 0.51, within(E));
        assertThat(kg.typical()).isCloseTo(26 * 0.82 * 0.54, within(E));
        assertThat(kg.high()).isCloseTo(26 * 0.85 * 0.58, within(E));
        // The Romanian 26 L bottle is sold as 11 kg.
        assertThat(kg.contains(11)).isTrue();
    }

    @Test
    @DisplayName("US gallons are gallons of liquid gas, no fill share: a 20 lb (9.07 kg) tank holds 4.7 gal")
    void gallons() {
        Range kg = GasBottle.kgPerBottle(4.7, GALLON);
        assertThat(kg.low()).isCloseTo(4.7 * 3.785411784 * 0.51, within(E));
        assertThat(kg.typical()).isCloseTo(4.7 * 3.785411784 * 0.54, within(E));
        assertThat(kg.low()).isCloseTo(9.07, within(0.01));
        // A US gallon of propane is about 25 kWh.
        assertThat(GasBottle.kwhPerBottle(1, GALLON).low()).isBetween(24.0, 25.0);
    }

    @Test
    @DisplayName("energy: 12.7–12.9 kWh a kg; an 11 kg bottle about 141 kWh")
    void energy() {
        Range kwh = GasBottle.kwhPerBottle(11, KG);
        assertThat(kwh.low()).isCloseTo(11 * 12.7, within(E));
        assertThat(kwh.typical()).isCloseTo(140.8, within(E));
        assertThat(kwh.high()).isCloseTo(11 * 12.9, within(E));
    }

    @Test
    @DisplayName("a year: bottles a month × 12 × energy per bottle; 2 bottles of 11 kg a month about 3,400 kWh")
    void year() {
        Range kwh = GasBottle.kwhPerYear(2, 11, KG);
        assertThat(kwh.typical()).isCloseTo(2 * 12 * 140.8, within(1e-6));
        assertThat(GasBottle.kwhPerYear(0, 11, KG)).isEqualTo(Range.exactly(0));
        // Half a bottle a month is fine.
        assertThat(GasBottle.kwhPerYear(0.5, 26, LITRE).typical())
                .isCloseTo(6 * 26 * 0.82 * 0.54 * 12.8, within(1e-6));
    }

    @Test
    @DisplayName("a negative or missing number or size is refused")
    void edges() {
        assertThatThrownBy(() -> GasBottle.kgPerBottle(-1, KG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GasBottle.kgPerBottle(Double.NaN, LITRE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GasBottle.kwhPerYear(-2, 11, KG)).isInstanceOf(IllegalArgumentException.class);
    }
}
