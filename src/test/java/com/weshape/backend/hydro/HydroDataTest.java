package com.weshape.backend.hydro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weshape.backend.wind.FileRangeSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Hydro data")
class HydroDataTest {

    @TempDir Path dir;

    private HydroData data() throws Exception {
        TestMvtWriter.write(dir, TestMvtWriter.cluj());
        return new HydroData(new FileRangeSource(dir), "hydro/tiles/");
    }

    @Test
    @DisplayName("reads lines and values back from a vector tile")
    void readsTile() throws Exception {
        var river = TestMvtWriter.cluj().get(0);
        byte[] tile = TestMvtWriter.tile(List.of(river), 10, 579, 361);
        MvtReader.Layer layer = MvtReader.layer(tile, "rivers");
        assertThat(layer).isNotNull();
        assertThat(layer.extent()).isEqualTo(4096);
        assertThat(layer.lines()).hasSize(1);
        assertThat(layer.lines().get(0).parts().get(0)).hasSize(6);
        assertThat(layer.lines().get(0).values()).containsEntry("qa", 16.0).containsEntry("h", 7.0);
        assertThat(MvtReader.layer(tile, "roads")).isNull();
    }

    @Test
    @DisplayName("a click near a river gives that reach, with its distance")
    void nearest() throws Exception {
        HydroData hydro = data();
        assertThat(hydro.available()).isTrue();
        HydroPoint p = hydro.point(46.770, 23.590);
        assertThat(p.meanFlow()).isEqualTo(16.0);
        assertThat(p.availableFlow()).isEqualTo(14.4);
        assertThat(p.potentialKw()).isEqualTo(790.0);
        assertThat(p.driestMonthFlow()).isEqualTo(5.0);
        assertThat(p.wettestMonthFlow()).isEqualTo(42.0);
        assertThat(p.headM()).isEqualTo(7.0);
        // The line passes about 0.2 km north of the click.
        assertThat(p.distanceKm()).isBetween(0.1, 0.4);

        HydroPoint stream = hydro.point(46.712, 23.318);
        assertThat(stream.headM()).isEqualTo(24.0);
    }

    @Test
    @DisplayName("finds a river in the next tile when the click is near a tile edge")
    void acrossTiles() throws Exception {
        // Tile 578 ends at lon 23.203125; the river is 0.5 km east of a click just west of it.
        TestMvtWriter.write(dir, List.of(new TestMvtWriter.River(
                new double[][] {{46.70, 23.210}, {46.74, 23.210}}, Map.of("qa", 3.0, "h", 2.0))));
        HydroData hydro = new HydroData(new FileRangeSource(dir), "hydro/tiles/");
        HydroPoint p = hydro.point(46.72, 23.200);
        assertThat(p.meanFlow()).isEqualTo(3.0);
        assertThat(p.distanceKm()).isBetween(0.6, 0.9);
    }

    @Test
    @DisplayName("zoomed out, a click takes the river the map shows, not a small stream that is not drawn yet")
    void visibleRiver() throws Exception {
        // A big river and, closer to the click, a small stream (shown from zoom 10 only).
        TestMvtWriter.write(dir, List.of(
                new TestMvtWriter.River(new double[][] {{47.20, 7.70}, {47.20, 7.76}}, Map.of("qa", 300.0, "h", 1.0)),
                new TestMvtWriter.River(new double[][] {{47.205, 7.70}, {47.205, 7.76}}, Map.of("qa", 0.14, "h", 2.5))));
        HydroData hydro = new HydroData(new FileRangeSource(dir), "hydro/tiles/");
        assertThat(hydro.point(47.206, 7.73).meanFlow()).isEqualTo(0.14);
        assertThat(hydro.point(47.206, 7.73, 12.0).meanFlow()).isEqualTo(0.14);
        assertThat(hydro.point(47.206, 7.73, 8.5).meanFlow()).isEqualTo(300.0);
        // At zoom 6 a click 5 km from the river still finds it.
        assertThat(hydro.point(47.245, 7.73, 6.0).meanFlow()).isEqualTo(300.0);
        assertThatThrownBy(() -> hydro.point(47.245, 7.73)).isInstanceOf(NoHydroDataException.class);
        assertThat(HydroData.minFlowShownAt(10)).isZero();
        assertThat(HydroData.minFlowShownAt(7)).isEqualTo(5.0);
        assertThat(HydroData.minFlowShownAt(1)).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("where a tributary flows in, a click on the main river gives the main river")
    void confluence() throws Exception {
        // The tributary (shown from zoom 9) runs 111 m from the main river, the click lies between.
        TestMvtWriter.write(dir, List.of(
                new TestMvtWriter.River(new double[][] {{47.20, 7.70}, {47.20, 7.76}}, Map.of("qa", 1950.0, "h", 0.0)),
                new TestMvtWriter.River(new double[][] {{47.201, 7.70}, {47.201, 7.76}}, Map.of("qa", 0.6, "h", 16.0))));
        HydroData hydro = new HydroData(new FileRangeSource(dir), "hydro/tiles/");
        HydroPoint zoomedOut = hydro.point(47.2008, 7.73, 9.0);
        assertThat(zoomedOut.meanFlow()).isEqualTo(1950.0);
        assertThat(zoomedOut.distanceKm()).isBetween(0.07, 0.11);
        // Close up the two lines are far apart on screen: the nearer one, as clicked.
        assertThat(hydro.point(47.2008, 7.73, 15.0).meanFlow()).isEqualTo(0.6);
    }

    @Test
    @DisplayName("no river within reach is 404; no tiles at all is unavailable")
    void none() throws Exception {
        HydroData hydro = data();
        assertThatThrownBy(() -> hydro.point(45.0, 25.0)).isInstanceOf(NoHydroDataException.class);
        assertThat(hydro.tile(10, 0, 0)).isEmpty();
        assertThat(hydro.tile(12, 0, 0)).isEmpty();

        Path empty = Files.createTempDirectory("no-hydro");
        HydroData missing = new HydroData(new FileRangeSource(empty), "hydro/tiles/");
        assertThat(missing.available()).isFalse();
        assertThatThrownBy(() -> missing.point(46.77, 23.59)).isInstanceOf(HydroData.HydroUnavailableException.class);
        assertThat(new HydroData(null, "hydro/tiles/").available()).isFalse();
    }
}
