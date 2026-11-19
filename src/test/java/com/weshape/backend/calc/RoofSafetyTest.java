package com.weshape.backend.calc;

import static com.weshape.backend.calc.RoofSafety.Era.BEFORE_1960;
import static com.weshape.backend.calc.RoofSafety.Era.FROM_1960;
import static com.weshape.backend.calc.RoofSafety.Era.FROM_1990;
import static com.weshape.backend.calc.RoofSafety.Era.FROM_2010;
import static com.weshape.backend.calc.RoofSafety.Material.BRICK;
import static com.weshape.backend.calc.RoofSafety.Material.CONCRETE;
import static com.weshape.backend.calc.RoofSafety.Material.STONE_ADOBE;
import static com.weshape.backend.calc.RoofSafety.Material.WOOD;
import static com.weshape.backend.calc.RoofSafety.Verdict.CHECK;
import static com.weshape.backend.calc.RoofSafety.Verdict.SAFE;
import static com.weshape.backend.calc.RoofSafety.Verdict.UNSAFE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Whether the roof can carry panels; docs/decisions/2026-11-19-question-changes.md. */
@DisplayName("Roof safety from material, age and renovation")
class RoofSafetyTest {

    @Test
    @DisplayName("Timi's examples: a 1960 concrete house is safe, a 1960 or older wooden one with an old roof is not")
    void examples() {
        assertThat(RoofSafety.roof(CONCRETE, BEFORE_1960, false)).isEqualTo(SAFE);
        assertThat(RoofSafety.roof(WOOD, BEFORE_1960, false)).isEqualTo(UNSAFE);
        assertThat(RoofSafety.roof(WOOD, BEFORE_1960, true)).isEqualTo(SAFE);
    }

    @Test
    @DisplayName("a renovated roof is safe whatever the building")
    void renovated() {
        for (var m : RoofSafety.Material.values()) {
            for (var e : RoofSafety.Era.values()) {
                assertThat(RoofSafety.roof(m, e, true)).as(m + ", " + e).isEqualTo(SAFE);
            }
        }
        assertThat(RoofSafety.roof(null, null, true)).isEqualTo(SAFE);
    }

    @Test
    @DisplayName("wood: before 1960 unsafe, 1960–1989 or unknown to be checked, from 1990 safe")
    void wood() {
        assertThat(RoofSafety.roof(WOOD, FROM_1960, false)).isEqualTo(CHECK);
        assertThat(RoofSafety.roof(WOOD, RoofSafety.Era.UNKNOWN, false)).isEqualTo(CHECK);
        assertThat(RoofSafety.roof(WOOD, null, false)).isEqualTo(CHECK);
        assertThat(RoofSafety.roof(WOOD, FROM_1990, false)).isEqualTo(SAFE);
        assertThat(RoofSafety.roof(WOOD, FROM_2010, false)).isEqualTo(SAFE);
    }

    @Test
    @DisplayName("stone, adobe or an unknown material: old or unknown age to be checked, newer safe")
    void stoneAndUnknown() {
        for (var m : new RoofSafety.Material[] {STONE_ADOBE, RoofSafety.Material.UNKNOWN}) {
            assertThat(RoofSafety.roof(m, BEFORE_1960, false)).as(m.name()).isEqualTo(CHECK);
            assertThat(RoofSafety.roof(m, RoofSafety.Era.UNKNOWN, false)).as(m.name()).isEqualTo(CHECK);
            assertThat(RoofSafety.roof(m, FROM_1960, false)).as(m.name()).isEqualTo(SAFE);
        }
        assertThat(RoofSafety.roof(null, BEFORE_1960, false)).isEqualTo(CHECK);
    }

    @Test
    @DisplayName("brick and concrete are safe at any age")
    void brickConcrete() {
        for (var e : RoofSafety.Era.values()) {
            assertThat(RoofSafety.roof(BRICK, e, false)).as(e.name()).isEqualTo(SAFE);
            assertThat(RoofSafety.roof(CONCRETE, e, false)).as(e.name()).isEqualTo(SAFE);
        }
    }
}
