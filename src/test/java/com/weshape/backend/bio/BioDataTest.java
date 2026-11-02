package com.weshape.backend.bio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weshape.backend.wind.FileRangeSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Biomass data")
class BioDataTest {

    @TempDir Path dir;

    @Test
    @DisplayName("a place gives its density, crop and forest residues within 25 km, energy and homes")
    void point() throws Exception {
        BioTestFiles.write(dir);
        BioData bio = new BioData(new FileRangeSource(dir), "bio/");
        assertThat(bio.available()).isTrue();
        BioPoint p = bio.point(46.77, 23.59);
        assertThat(p.density()).isEqualTo(1.58);
        assertThat(p.cropKt()).isEqualTo(124.2);
        assertThat(p.forestKt()).isEqualTo(81.8);
        assertThat(p.resourceKt()).isBetween(205.9, 206.2);
        assertThat(p.energyGwh()).isEqualTo(890.0);
        // about 891 GWh / 15 MWh a home, to the nearest hundred
        assertThat(p.homes()).isEqualTo(59_400);
        assertThat(p.radiusKm()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("decoding matches the builder's log scale")
    void decode() {
        assertThat(BioData.decode("density", 0)).isZero();
        assertThat(BioData.decode("density", 254)).isBetween(40.0, 50.0);
        assertThat(BioData.decode("crop", 171)).isBetween(124.0, 124.5);
    }

    @Test
    @DisplayName("the sea is 404, tiles come from the stored files, nothing built is unavailable")
    void seaAndTiles() throws Exception {
        BioTestFiles.write(dir);
        BioData bio = new BioData(new FileRangeSource(dir), "bio/");
        assertThatThrownBy(() -> bio.point(35, 5)).isInstanceOf(NoBioDataException.class);
        assertThat(bio.tile("energy", 0, 0, 0)).hasSize(7);
        assertThat(bio.tile("density", 3, 1, 1)).isNull();
        assertThatThrownBy(() -> bio.tile("peat", 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);

        BioData empty = new BioData(new FileRangeSource(Files.createTempDirectory("no-bio")), "bio/");
        assertThat(empty.available()).isFalse();
        assertThatThrownBy(() -> empty.point(46.77, 23.59)).isInstanceOf(BioData.BioUnavailableException.class);
    }
}
