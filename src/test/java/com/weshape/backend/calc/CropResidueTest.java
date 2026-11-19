package com.weshape.backend.calc;

import static com.weshape.backend.calc.CropResidue.AreaUnit.ACRE;
import static com.weshape.backend.calc.CropResidue.AreaUnit.HECTARE;
import static com.weshape.backend.calc.CropResidue.Crop.MAIZE;
import static com.weshape.backend.calc.CropResidue.Crop.VINEYARD_ORCHARD;
import static com.weshape.backend.calc.CropResidue.Crop.WHEAT_BARLEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Residue on the person's own fields; docs/decisions/2026-11-19-question-changes.md. */
@DisplayName("Crop residue")
class CropResidueTest {

    private static final double E = 1e-9;

    @Test
    @DisplayName("1 ha of maize leaves 5–8 t; 30–50% of it may be taken: 1.5–4 t, 2.6 t used")
    void maize() {
        assertThat(MAIZE.tonnesPerHa).isEqualTo(new Range(5, 6.5, 8));
        Range t = CropResidue.tonnes(MAIZE, 1, HECTARE);
        assertThat(t.low()).isCloseTo(1.5, within(E));
        assertThat(t.typical()).isCloseTo(2.6, within(E));
        assertThat(t.high()).isCloseTo(4.0, within(E));
    }

    @Test
    @DisplayName("acres: 1 acre = 0.4047 ha")
    void acres() {
        assertThat(CropResidue.hectares(10, ACRE)).isCloseTo(4.0468564224, within(E));
        assertThat(CropResidue.tonnes(WHEAT_BARLEY, 10, ACRE).typical())
                .isCloseTo(4 * 0.4 * 4.0468564224, within(E));
    }

    @Test
    @DisplayName("prunings are cut away anyway: 60–90% taken")
    void prunings() {
        Range t = CropResidue.tonnes(VINEYARD_ORCHARD, 2, HECTARE);
        assertThat(t.low()).isCloseTo(2 * 1.5 * 0.6, within(E));
        assertThat(t.high()).isCloseTo(2 * 3 * 0.9, within(E));
    }

    @Test
    @DisplayName("several crops add up; heat: 4.0–4.5 kWh a kg; 1 ha of maize about 11,000 kWh a year")
    void severalAndEnergy() {
        Map<CropResidue.Crop, Double> fields = new EnumMap<>(CropResidue.Crop.class);
        fields.put(MAIZE, 1.0);
        fields.put(WHEAT_BARLEY, 2.0);
        Range t = CropResidue.tonnes(fields);
        assertThat(t.typical()).isCloseTo(2.6 + 2 * 1.6, within(E));
        Range kwh = CropResidue.kwhPerYear(CropResidue.tonnes(MAIZE, 1, HECTARE));
        assertThat(kwh.typical()).isCloseTo(2600 * 4.2, within(1e-6));
        assertThat(kwh.low()).isCloseTo(1500 * 4.0, within(1e-6));
        assertThat(kwh.high()).isCloseTo(4000 * 4.5, within(1e-6));
    }

    @Test
    @DisplayName("no area, no residue; a negative area is refused")
    void edges() {
        assertThat(CropResidue.tonnes(MAIZE, 0, HECTARE)).isEqualTo(Range.exactly(0));
        assertThatThrownBy(() -> CropResidue.hectares(-1, HECTARE)).isInstanceOf(IllegalArgumentException.class);
    }
}
