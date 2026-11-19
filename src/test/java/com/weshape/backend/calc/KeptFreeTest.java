package com.weshape.backend.calc;

import static com.weshape.backend.calc.KeptFree.Reason.HARD_TO_REACH;
import static com.weshape.backend.calc.KeptFree.Reason.NOT_WANTED;
import static com.weshape.backend.calc.KeptFree.Reason.RULES;
import static com.weshape.backend.calc.KeptFree.Reason.SOMETHING_THERE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.weshape.backend.calc.KeptFree.Obstacle;
import com.weshape.backend.calc.KeptFree.Thing;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parts kept free: something there, or nothing goes there; docs/decisions/2026-11-19-question-changes.md. */
@DisplayName("Parts of the roof or yard kept free")
class KeptFreeTest {

    private static final double E = 1e-9;

    @Test
    @DisplayName("not wanted, hard to reach for upkeep, or not allowed: nothing goes there")
    void nothing() {
        for (var r : List.of(NOT_WANTED, HARD_TO_REACH, RULES)) {
            assertThat(KeptFree.partlyUsable(r)).as(r.name()).isFalse();
            assertThat(KeptFree.usableM2(40, r, List.of())).as(r.name()).isEqualTo(Range.exactly(0));
        }
        assertThat(KeptFree.partlyUsable(SOMETHING_THERE)).isTrue();
    }

    @Test
    @DisplayName("a chimney takes 1–3.2 m², 2 m² used, without asking its size")
    void chimney() {
        Range left = KeptFree.usableM2(40, SOMETHING_THERE, List.of(Obstacle.chimney()));
        assertThat(left.low()).isCloseTo(36.8, within(E));
        assertThat(left.typical()).isCloseTo(38, within(E));
        assertThat(left.high()).isCloseTo(39, within(E));
    }

    @Test
    @DisplayName("a window of 1 × 1.2 m takes (1 + 0.6) × (1.2 + 0.6) = 2.88 m² with its margin")
    void window() {
        Obstacle w = new Obstacle(Thing.WINDOW, 1, 1.2);
        assertThat(w.area().typical()).isCloseTo(1.6 * 1.8, within(E));
        Range left = KeptFree.usableM2(30, SOMETHING_THERE, List.of(w, Obstacle.chimney()));
        assertThat(left.typical()).isCloseTo(30 - 2.88 - 2, within(E));
        assertThat(left.low()).isCloseTo(30 - 2.88 - 3.2, within(E));
    }

    @Test
    @DisplayName("nothing listed: the whole part; more taken than there is: nothing, never below 0")
    void edges() {
        assertThat(KeptFree.usableM2(25, SOMETHING_THERE, List.of())).isEqualTo(Range.exactly(25));
        Range left = KeptFree.usableM2(4, SOMETHING_THERE, List.of(new Obstacle(Thing.OTHER, 2, 2)));
        assertThat(left).isEqualTo(Range.exactly(0));
        assertThatThrownBy(() -> new Obstacle(Thing.WINDOW, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeptFree.usableM2(-1, SOMETHING_THERE, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
