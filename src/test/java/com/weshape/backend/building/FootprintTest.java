package com.weshape.backend.building;

import static com.weshape.backend.building.TestWkb.LAT;
import static com.weshape.backend.building.TestWkb.LON;
import static com.weshape.backend.building.TestWkb.polygon;
import static com.weshape.backend.building.TestWkb.rect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Building outlines")
class FootprintTest {

    private static Footprint shape(byte[] wkb) {
        return new Footprint(Wkb.polygons(wkb), LAT, LON);
    }

    @Test
    @DisplayName("a 10 × 20 m house: area, walls and the way each faces")
    void rectangle() {
        Footprint f = shape(polygon(rect(0, 0, 10, 20)));
        assertThat(f.area()).isCloseTo(200, within(0.5));
        assertThat(f.perimeter()).isCloseTo(60, within(0.1));
        List<Footprint.Wall> walls = f.walls();
        assertThat(walls).hasSize(4);
        assertThat(walls).extracting(w -> Math.round(w.facingDeg()) % 360)
                .containsExactlyInAnyOrder(0L, 90L, 180L, 270L);
        Footprint.Wall south = walls.stream().filter(w -> Math.round(w.facingDeg()) == 180).findFirst().orElseThrow();
        assertThat(south.lengthM()).isCloseTo(10, within(0.05));
        // The long walls run north–south.
        assertThat(f.mainAxis()).isCloseTo(0, within(0.5));
        assertThat(f.contains(5, 5)).isTrue();
        assertThat(f.contains(-1, 5)).isFalse();
        assertThat(f.distanceTo(-3, 5)).isCloseTo(3, within(0.01));
        assertThat(f.distanceTo(5, 5)).isZero();
    }

    @Test
    @DisplayName("clockwise rings, big-endian and Z coordinates read the same")
    void orderAndDims() {
        double[][] cw = {{0, 0}, {0, 20}, {10, 20}, {10, 0}, {0, 0}};
        Footprint f = shape(polygon(ByteOrder.BIG_ENDIAN, 1003, 3, cw));
        assertThat(f.area()).isCloseTo(200, within(0.5));
        assertThat(f.walls()).extracting(w -> Math.round(w.facingDeg()) % 360)
                .containsExactlyInAnyOrder(0L, 90L, 180L, 270L);
    }

    @Test
    @DisplayName("an L-shaped house: small steps are not walls, straight runs are one wall")
    void lShape() {
        double[][] l = {{0, 0}, {6, 0}, {12, 0.3}, {12, 6}, {6, 6}, {6, 12}, {0, 12}, {0, 0}};
        Footprint f = shape(polygon(l));
        assertThat(f.area()).isCloseTo(6 * 12 + 6 * 6 - 0.9, within(0.1));
        // South wall (two edges almost in line), east, the inner corner (north, east), north-west run, west.
        assertThat(f.walls()).hasSize(6);
    }

    @Test
    @DisplayName("a courtyard does not count in the area; a point in it is outside")
    void hole() {
        Footprint f = shape(polygon(rect(0, 0, 30, 30), rect(10, 10, 20, 20)));
        assertThat(f.area()).isCloseTo(800, within(2.0));
        assertThat(f.contains(15, 15)).isFalse();
        assertThat(f.distanceTo(15, 15)).isCloseTo(5, within(0.01));
    }

    @Test
    @DisplayName("distance between two buildings; touching is 0")
    void between() {
        Footprint a = shape(polygon(rect(0, 0, 10, 10)));
        assertThat(a.distanceTo(shape(polygon(rect(15, 0, 25, 10))))).isCloseTo(5, within(0.01));
        assertThat(a.distanceTo(shape(polygon(rect(10, 0, 20, 10))))).isCloseTo(0, within(0.01));
        assertThat(a.distanceTo(shape(polygon(rect(5, -5, 7, 15))))).isZero();
        assertThat(a.distanceTo(shape(polygon(rect(2, 2, 4, 4))))).isZero();
    }

    @Test
    @DisplayName("a multipolygon has every part's area; the outline is the biggest part")
    void multi() {
        Footprint f = shape(TestWkb.multi(polygon(rect(0, 0, 4, 4)), polygon(rect(10, 0, 20, 10))));
        assertThat(f.area()).isCloseTo(116, within(0.5));
        assertThat(f.outline()).hasSize(5);
        assertThat(f.outline().get(0)[0]).isCloseTo(TestWkb.lon(10), within(1e-7));
    }

    @Test
    @DisplayName("bearings: north 0, east 90")
    void bearing() {
        assertThat(Footprint.bearing(0, 1)).isCloseTo(0, within(1e-9));
        assertThat(Footprint.bearing(1, 0)).isCloseTo(90, within(1e-9));
        assertThat(Footprint.bearing(0, -1)).isCloseTo(180, within(1e-9));
        assertThat(Footprint.bearing(-1, 0)).isCloseTo(270, within(1e-9));
    }

    @Test
    @DisplayName("anything but a polygon is refused")
    void notPolygon() {
        byte[] point = java.nio.ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 1).putInt(1).putDouble(1).putDouble(2).array();
        assertThatThrownBy(() -> Wkb.polygons(point)).isInstanceOf(IllegalArgumentException.class);
    }
}
