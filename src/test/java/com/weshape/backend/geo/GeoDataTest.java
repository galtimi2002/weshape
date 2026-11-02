package com.weshape.backend.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weshape.backend.wind.FileRangeSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Geothermal data")
class GeoDataTest {

    @TempDir Path dir;

    @Test
    @DisplayName("a place gives its ground temperature, heat flow and the estimate at each depth")
    void point() throws Exception {
        GeoTestFiles.write(dir);
        GeoData geo = new GeoData(new FileRangeSource(dir), "geo/");
        assertThat(geo.available()).isTrue();
        GeoPoint p = geo.point(46.77, 23.59);
        assertThat(p.groundTemperature()).isEqualTo(10.5);
        assertThat(p.heatFlow()).isEqualTo(68.0);
        assertThat(p.depths()).hasSize(4);
        // 10.5 + 68 / 2.5 x 2 = 64.9 °C at 2 km
        assertThat(p.depths().get(1).depthKm()).isEqualTo(2);
        assertThat(p.depths().get(1).temperature()).isEqualTo(64.9);
        assertThat(p.depths().get(3).temperature()).isEqualTo(146.5);
    }

    @Test
    @DisplayName("without a heat flow measurement nearby only the ground temperature comes back")
    void noHeatFlow() throws Exception {
        GeoTestFiles.write(dir);
        GeoPoint p = new GeoData(new FileRangeSource(dir), "geo/").point(30, 120);
        assertThat(p.groundTemperature()).isEqualTo(10.5);
        assertThat(p.heatFlow()).isNull();
        assertThat(p.depths()).isEmpty();
    }

    @Test
    @DisplayName("the sea is 404, tiles come from the stored files, nothing built is unavailable")
    void seaAndTiles() throws Exception {
        GeoTestFiles.write(dir);
        GeoData geo = new GeoData(new FileRangeSource(dir), "geo/");
        assertThatThrownBy(() -> geo.point(35, 5)).isInstanceOf(NoGeoDataException.class);
        assertThat(geo.tile("depth-2", 0, 0, 0)).hasSize(7);
        assertThat(geo.tile("ground", 3, 1, 1)).isNull();
        assertThatThrownBy(() -> geo.tile("rain", 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);

        GeoData empty = new GeoData(new FileRangeSource(Files.createTempDirectory("no-geo")), "geo/");
        assertThat(empty.available()).isFalse();
        assertThatThrownBy(() -> empty.point(46.77, 23.59)).isInstanceOf(GeoData.GeoUnavailableException.class);
        assertThat(new GeoData(null, "geo/").available()).isFalse();
    }
}
