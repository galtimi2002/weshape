package com.weshape.backend.calc;

import static com.weshape.backend.calc.Biogas.Animal.CATTLE;
import static com.weshape.backend.calc.Biogas.Animal.HORSE;
import static com.weshape.backend.calc.Biogas.Animal.PIG;
import static com.weshape.backend.calc.Biogas.Animal.POULTRY;
import static com.weshape.backend.calc.Biogas.Animal.SHEEP_GOAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Biogas from the kind and number of animals, and from food waste; docs/decisions/2026-11-19-question-changes.md. */
@DisplayName("Biogas from animals and food waste")
class BiogasTest {

    private static final double E = 1e-9;

    @Test
    @DisplayName("every animal: a sensible manure and gas range, low ≤ used ≤ high")
    void table() {
        for (Biogas.Animal a : Biogas.Animal.values()) {
            assertThat(a.manureKgPerDay.low()).as(a + " manure").isGreaterThan(0);
            assertThat(a.m3PerKg.low()).as(a + " gas").isGreaterThan(0);
            assertThat(a.m3PerKg.high()).as(a + " gas").isLessThan(0.2);
        }
        // A cow gives far more manure than a pig, a pig more than a sheep, a sheep more than a hen.
        assertThat(CATTLE.manureKgPerDay.typical()).isGreaterThan(PIG.manureKgPerDay.typical());
        assertThat(PIG.manureKgPerDay.typical()).isGreaterThan(SHEEP_GOAT.manureKgPerDay.typical());
        assertThat(SHEEP_GOAT.manureKgPerDay.typical()).isGreaterThan(POULTRY.manureKgPerDay.typical());
    }

    @Test
    @DisplayName("manure adds up over the animals: 2 cows and 3 pigs")
    void manure() {
        Range m = Biogas.manureKgPerDay(Map.of(CATTLE, 2, PIG, 3));
        assertThat(m.low()).isCloseTo(2 * 5 + 3 * 2, within(E));
        assertThat(m.typical()).isCloseTo(2 * 20 + 3 * 3, within(E));
        assertThat(m.high()).isCloseTo(2 * 50 + 3 * 5, within(E));
        assertThat(Biogas.manureKgPerDay(Map.of())).isEqualTo(Range.exactly(0));
        assertThat(Biogas.manureKgPerDay(Map.of(HORSE, 0))).isEqualTo(Range.exactly(0));
    }

    @Test
    @DisplayName("gas: manure × gas per kg for each animal; one cow about 0.6 m³ a day")
    void gas() {
        Range cow = Biogas.manureM3PerDay(Map.of(CATTLE, 1));
        assertThat(cow.low()).isCloseTo(5 * 0.02, within(E));
        assertThat(cow.typical()).isCloseTo(20 * 0.03, within(E));
        assertThat(cow.high()).isCloseTo(50 * 0.04, within(E));
        Map<Biogas.Animal, Integer> mixed = new EnumMap<>(Biogas.Animal.class);
        mixed.put(CATTLE, 1);
        mixed.put(POULTRY, 50);
        Range both = Biogas.manureM3PerDay(mixed);
        assertThat(both.typical()).isCloseTo(20 * 0.03 + 50 * 0.12 * 0.08, within(E));
    }

    @Test
    @DisplayName("a year of gas: m³ a day × 365 × 5.5–6.5 kWh; one cow about 1,300 kWh")
    void energy() {
        Range kwh = Biogas.kwhPerYear(Biogas.manureM3PerDay(Map.of(CATTLE, 1)));
        assertThat(kwh.typical()).isCloseTo(0.6 * 365 * 6, within(1e-6));
        assertThat(kwh.low()).isCloseTo(0.1 * 365 * 5.5, within(1e-6));
        assertThat(kwh.high()).isCloseTo(2.0 * 365 * 6.5, within(1e-6));
    }

    @Test
    @DisplayName("food waste in kg or lb a week: 80–150 m³ of gas a tonne")
    void foodWaste() {
        Range kg = Biogas.foodWasteM3PerDay(70, Biogas.MassUnit.KG);
        assertThat(kg.low()).isCloseTo(10 * 0.08, within(E));
        assertThat(kg.typical()).isCloseTo(10 * 0.11, within(E));
        assertThat(kg.high()).isCloseTo(10 * 0.15, within(E));
        Range lb = Biogas.foodWasteM3PerDay(70, Biogas.MassUnit.LB);
        assertThat(lb.typical()).isCloseTo(10 * 0.45359237 * 0.11, within(E));
        assertThat(Biogas.kg(1, Biogas.MassUnit.LB)).isCloseTo(0.45359237, within(E));
        assertThatThrownBy(() -> Biogas.kg(-2, Biogas.MassUnit.KG)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("worth a digester from about 20 kg of manure a day: one cow yes, a few hens no")
    void worthIt() {
        assertThat(Biogas.worthIt(Map.of(CATTLE, 1))).isTrue();
        assertThat(Biogas.worthIt(Map.of(PIG, 5))).isFalse();
        assertThat(Biogas.worthIt(Map.of(PIG, 7))).isTrue();
        assertThat(Biogas.worthIt(Map.of(POULTRY, 30))).isFalse();
        assertThat(Biogas.worthIt(Map.of(SHEEP_GOAT, 14))).isTrue();
        assertThat(Biogas.worthIt(Map.of())).isFalse();
    }

    @Test
    @DisplayName("a negative number of animals is refused")
    void negative() {
        assertThatThrownBy(() -> Biogas.manureKgPerDay(Map.of(PIG, -1))).isInstanceOf(IllegalArgumentException.class);
    }
}
