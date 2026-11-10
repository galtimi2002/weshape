package com.weshape.backend.building;

import static com.weshape.backend.building.TestWkb.LAT;
import static com.weshape.backend.building.TestWkb.LON;
import static com.weshape.backend.building.TestWkb.polygon;
import static com.weshape.backend.building.TestWkb.rect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.weshape.backend.wind.RangeSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Building lookup")
class BuildingDataTest {

    static final String INDEX = """
            {"release":"2026-10-21.0","built":"2026-11-10T00:00:00Z",
             "base":"https://example.org/release/2026-10-21.0/theme=buildings/type=building/",
             "files":[["part-1.parquet",20,40,30,50],["part-2.parquet",-10,-10,0,0]]}""";

    /** The house at the place, a neighbour 6 m east, a shed 30 m north, a block 200 m away. */
    static List<BuildingReader.Raw> street() {
        return List.of(
                new BuildingReader.Raw("block", polygon(rect(200, 0, 230, 30)), 18.0, 6, "flat", null, "apartments"),
                new BuildingReader.Raw("shed", polygon(rect(0, 30, 4, 34)), null, null, null, null, null),
                new BuildingReader.Raw("house", polygon(rect(-5, -5, 5, 15)), 7.5, 2, "gabled", 90.0, "house"),
                new BuildingReader.Raw("neighbour", polygon(rect(11, -5, 21, 5)), null, 1, null, null, "house"));
    }

    static class FakeReader implements BuildingReader {
        final List<List<String>> calls = new ArrayList<>();
        List<Raw> rows = street();

        @Override
        public List<Raw> read(List<String> files, double w, double s, double e, double n) {
            calls.add(files);
            assertThat(w).isLessThan(LON);
            assertThat(e).isGreaterThan(LON);
            assertThat(s).isLessThan(LAT);
            assertThat(n).isGreaterThan(LAT);
            return rows;
        }
    }

    static RangeSource source(String index) {
        return new RangeSource() {
            @Override
            public byte[] read(String key, long offset, int length) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public byte[] readAll(String key) throws IOException {
                if (index == null || !key.equals("buildings/index.json")) {
                    throw new FileNotFoundException(key);
                }
                return index.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String describe() {
                return "test";
            }
        };
    }

    @Test
    @DisplayName("the building at the place, with walls; neighbours within 50 m, nearest first")
    void inside() {
        FakeReader reader = new FakeReader();
        BuildingData data = new BuildingData(source(INDEX), "buildings/index.json", reader);
        BuildingsAt at = data.at(LAT, LON);
        assertThat(reader.calls).containsExactly(
                List.of("https://example.org/release/2026-10-21.0/theme=buildings/type=building/part-1.parquet"));
        assertThat(at.release()).isEqualTo("2026-10-21.0");
        assertThat(at.attribution()).contains("OpenStreetMap");
        BuildingsAt.Building house = at.building();
        assertThat(house.id()).isEqualTo("house");
        assertThat(house.areaM2()).isCloseTo(200, within(0.5));
        assertThat(house.distanceM()).isZero();
        assertThat(house.heightM()).isEqualTo(7.5);
        assertThat(house.floors()).isEqualTo(2);
        assertThat(house.roofShape()).isEqualTo("gabled");
        assertThat(house.kind()).isEqualTo("house");
        assertThat(house.walls()).hasSize(4);
        assertThat(house.mainAxisDeg()).isEqualTo(0.0);
        assertThat(house.outline()).hasSize(5);
        assertThat(at.nearby()).extracting(BuildingsAt.Building::id).containsExactly("neighbour", "shed");
        BuildingsAt.Building neighbour = at.nearby().get(0);
        assertThat(neighbour.distanceM()).isCloseTo(6, within(0.1));
        assertThat(neighbour.bearingDeg()).isBetween(100.0, 130.0);
        assertThat(neighbour.walls()).isNull();

        // Asked again: from memory.
        data.at(LAT, LON);
        assertThat(reader.calls).hasSize(1);
    }

    @Test
    @DisplayName("just outside a building: the nearest within 20 m; nothing near: no building")
    void outside() {
        BuildingsAt near = BuildingData.answer(street(), TestWkb.lat(-12), TestWkb.lon(0), "r");
        assertThat(near.building().id()).isEqualTo("house");
        assertThat(near.building().distanceM()).isCloseTo(7, within(0.1));

        BuildingsAt none = BuildingData.answer(street(), TestWkb.lat(-60), TestWkb.lon(100), "r");
        assertThat(none.building()).isNull();
        assertThat(none.nearby()).isEmpty();
    }

    @Test
    @DisplayName("a broken outline is skipped, not an error")
    void broken() {
        List<BuildingReader.Raw> rows = new ArrayList<>(street());
        rows.add(new BuildingReader.Raw("broken", new byte[] {1, 2, 3}, null, null, null, null, null));
        assertThat(BuildingData.answer(rows, LAT, LON, "r").building().id()).isEqualTo("house");
    }

    @Test
    @DisplayName("without an index, or when the files cannot be read: unavailable")
    void unavailable() {
        assertThat(new BuildingData(null, "buildings/index.json", new FakeReader()).available()).isFalse();
        BuildingData noIndex = new BuildingData(source(null), "buildings/index.json", new FakeReader());
        assertThat(noIndex.available()).isFalse();
        assertThatThrownBy(() -> noIndex.at(LAT, LON)).isInstanceOf(BuildingData.BuildingsUnavailableException.class);

        BuildingReader failing = (files, w, s, e, n) -> {
            throw new IOException("network");
        };
        BuildingData broken = new BuildingData(source(INDEX), "buildings/index.json", failing);
        assertThat(broken.available()).isTrue();
        assertThatThrownBy(() -> broken.at(LAT, LON)).isInstanceOf(BuildingData.BuildingsUnavailableException.class);
    }

    @Test
    @DisplayName("the index lists only the files that touch the square")
    void index() throws IOException {
        BuildingIndex idx = BuildingIndex.parse(INDEX.getBytes(StandardCharsets.UTF_8));
        assertThat(idx.files()).hasSize(2);
        assertThat(idx.filesFor(-5, -5, -4, -4)).containsExactly(idx.base() + "part-2.parquet");
        assertThat(idx.filesFor(100, 10, 101, 11)).isEmpty();
        assertThatThrownBy(() -> BuildingIndex.parse("{\"release\":\"x\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class);
    }
}
