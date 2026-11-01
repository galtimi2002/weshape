package com.weshape.backend.wind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Wind data")
class WindDataTest {

    @TempDir Path dir;

    @ParameterizedTest(name = "BigTIFF: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("reads the header, levels and georeference of a cloud-optimised GeoTIFF")
    void header(boolean big) throws Exception {
        TestCogWriter.writeWorld(dir, big);
        CogFile f = new CogFile(new FileRangeSource(dir), "wind/gwa/ws100.tif");
        assertThat(f.levels()).hasSize(3);
        CogFile.Level l0 = f.levels().get(0);
        assertThat(l0.width()).isEqualTo(1440);
        assertThat(l0.height()).isEqualTo(576);
        assertThat(l0.tileWidth()).isEqualTo(256);
        assertThat(l0.originX()).isCloseTo(-180, within(1e-9));
        assertThat(l0.originY()).isCloseTo(80, within(1e-9));
        assertThat(l0.pixelX()).isCloseTo(0.25, within(1e-9));
        assertThat(f.levels().get(2).pixelX()).isCloseTo(1.0, within(1e-9));
        assertThat(f.nodata()).isEqualTo(255);
        byte[] tile = f.readTile(0, 3, 0);
        // cell (col 3*256+5, row 7) holds 30 + col % 50
        assertThat(tile[7 * 256 + 5] & 0xff).isEqualTo(30 + (3 * 256 + 5) % 50);
    }

    @ParameterizedTest(name = "BigTIFF: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("gives the wind by height for a place, with 150 m worked out from 100 and 200 m")
    void point(boolean big) throws Exception {
        TestCogWriter.writeWorld(dir, big);
        WindData wind = new WindData(new FileRangeSource(dir), "wind/gwa/");
        assertThat(wind.available()).isTrue();
        WindPoint p = wind.point(46.77, 23.59);
        int col = (int) Math.floor((23.59 + 180) / 0.25);
        int code = 30 + col % 50;
        assertThat(p.speed100()).isCloseTo(code / 10.0, within(1e-9));
        assertThat(p.speeds()).extracting(WindPoint.Speed::height).containsExactly(10, 50, 100, 150, 200);
        double v100 = code / 10.0;
        double v200 = Math.round(code * 1.12) / 10.0;
        double expected150 = WindEncoding.between(v100, 100, v200, 200, 150);
        assertThat(p.speeds().get(3).speed()).isCloseTo(expected150, within(0.051));
        assertThat(p.speeds().get(0).speed()).isLessThan(p.speeds().get(4).speed());
        double speed = code / 10.0;
        assertThat(p.powerDensity100().doubleValue()).isCloseTo(0.6 * speed * speed * speed, within(0.6 * speed * speed * speed * 0.04 + 1));
        assertThat(p.source()).contains("Global Wind Atlas");
    }

    @Test
    @DisplayName("tiles drawn in advance are handed out as they are; a missing one inside a stored layer is empty")
    void storedTiles() throws Exception {
        TestCogWriter.writeWorld(dir, true);
        byte[] stored = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        Files.createDirectories(dir.resolve("wind/tiles/speed/3/4"));
        Files.write(dir.resolve("wind/tiles/speed/3/4/2.png"), stored);
        Files.writeString(dir.resolve("wind/tiles/speed.json"), "{\"layer\":\"speed\"}");
        WindData wind = new WindData(new FileRangeSource(dir), "wind/gwa/");
        assertThat(wind.tile("speed", 3, 4, 2)).isEqualTo(stored);
        BufferedImage missing = ImageIO.read(new ByteArrayInputStream(wind.tile("speed", 3, 4, 3)));
        assertThat(missing.getRGB(128, 128) >>> 24).isZero();
        // No stored tiles for this layer: drawn on the server as before.
        BufferedImage drawn = ImageIO.read(new ByteArrayInputStream(wind.tile("height-100", 0, 0, 0)));
        assertThat(drawn.getRGB(px(23.59), py(46.77, 0)) >>> 24).isEqualTo(255);
        assertThat(new FileRangeSource(dir).readAll("wind/tiles/speed.json")).isNotEmpty();
    }

    @Test
    @DisplayName("a missing power density file leaves the speeds working")
    void withoutPowerDensity() throws Exception {
        TestCogWriter.writeWorld(dir, true);
        Files.delete(dir.resolve("wind/gwa/pd100.tif"));
        WindData wind = new WindData(new FileRangeSource(dir), "wind/gwa/");
        WindPoint p = wind.point(46.77, 23.59);
        assertThat(p.speeds()).hasSize(5);
        assertThat(p.powerDensity100()).isNull();
        assertThatThrownBy(() -> wind.tile("power", 0, 0, 0)).isInstanceOf(WindData.WindUnavailableException.class);
    }

    @Test
    @DisplayName("open sea and places outside the atlas have no wind data")
    void noData() throws Exception {
        TestCogWriter.writeWorld(dir, true);
        WindData wind = new WindData(new FileRangeSource(dir), "wind/gwa/");
        assertThatThrownBy(() -> wind.point(35, 5)).isInstanceOf(NoWindDataException.class);
        assertThatThrownBy(() -> wind.point(-50, 5)).isInstanceOf(NoWindDataException.class);
        assertThatThrownBy(() -> wind.point(85, 5)).isInstanceOf(NoWindDataException.class);
    }

    @Test
    @DisplayName("map tiles: coloured over land, transparent where there is no data, from the matching overview")
    void tiles() throws Exception {
        TestCogWriter.writeWorld(dir, true);
        WindData wind = new WindData(new FileRangeSource(dir), "wind/gwa/");
        CogFile ref = new CogFile(new FileRangeSource(dir), "wind/gwa/ws100.tif");
        // 1.4° per map pixel at zoom 0: the 1° overview; 0.7° at zoom 1: the 0.5° one; then full resolution.
        assertThat(WindData.levelFor(ref, 0)).isEqualTo(2);
        assertThat(WindData.levelFor(ref, 1)).isEqualTo(1);
        assertThat(WindData.levelFor(ref, 2)).isEqualTo(0);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(wind.tile("speed", 0, 0, 0)));
        assertThat(img.getWidth()).isEqualTo(256);
        int europe = img.getRGB(px(23.59), py(46.77, 0));
        assertThat(europe >>> 24).isEqualTo(255);
        assertThat(europe & 0xff).isGreaterThan(europe >> 16 & 0xff); // blue scale
        assertThat(img.getRGB(px(23.59), 250) >>> 24).isZero(); // far south: no data
        assertThat(img.getRGB(px(5), py(35, 0)) >>> 24).isZero(); // the "sea" box

        for (String layer : new String[] {"power", "height-10", "height-150", "height-200"}) {
            BufferedImage other = ImageIO.read(new ByteArrayInputStream(wind.tile(layer, 0, 0, 0)));
            assertThat(other.getRGB(px(23.59), py(46.77, 0)) >>> 24).as(layer).isEqualTo(255);
        }
        assertThatThrownBy(() -> wind.tile("height-75", 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wind.tile("rain", 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);

        // A zoomed-in tile over the sea box is empty, and comes back as a transparent PNG.
        byte[] empty = wind.tile("speed", 6, 32 + 0, 25);
        BufferedImage e = ImageIO.read(new ByteArrayInputStream(empty));
        assertThat(e.getRGB(128, 128) >>> 24).isZero();
    }

    @Test
    @DisplayName("without settings or files the wind map reports itself unavailable")
    void unavailable() throws Exception {
        WindData none = new WindData((RangeSource) null, "wind/gwa/");
        assertThat(none.available()).isFalse();
        assertThatThrownBy(() -> none.point(46, 23)).isInstanceOf(WindData.WindUnavailableException.class);
        WindData missing = new WindData(new FileRangeSource(Files.createDirectories(dir.resolve("empty"))), "wind/gwa/");
        assertThat(missing.available()).isFalse();
        assertThatThrownBy(() -> new FileRangeSource(dir).read("nope.tif", 0, 10)).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("colour scales are fixed and run from the first to the last colour")
    void colours() {
        assertThat(WindLayer.SPEED.argb(0)).isEqualTo(0xffE3EEF8);
        assertThat(WindLayer.SPEED.argb(50)).isEqualTo(0xff163E78);
        assertThat(WindLayer.POWER.colors).isEqualTo(WindLayer.SPEED.colors); // one blue scale
        assertThat(WindLayer.HEIGHT.colors).isEqualTo(WindLayer.SPEED.colors);
        assertThat(WindLayer.POWER.scale(50)).isZero();
        assertThat(WindLayer.POWER.scale(800)).isEqualTo(1.0);
        assertThat(WindLayer.POWER.scale(200)).isCloseTo(0.5, within(1e-9)); // halfway on a log scale
        assertThat(WindEncoding.speed(52)).isEqualTo(5.2);
        assertThat(WindEncoding.powerDensity(0)).isZero();
        assertThat(WindEncoding.between(5, 100, 6, 200, 200)).isCloseTo(6, within(1e-9));
    }

    @Test
    @DisplayName("signs S3 requests like AWS's own example (GET with a range)")
    void signature() {
        Map<String, String> headers = AwsV4Signer.signGet("examplebucket.s3.amazonaws.com", "/test.txt",
                Map.of("Range", "bytes=0-9"), ZonedDateTime.of(2013, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC), "us-east-1",
                "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        assertThat(headers.get("authorization")).isEqualTo(
                "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,"
                        + "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,"
                        + "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
        assertThat(headers.get("x-amz-date")).isEqualTo("20130524T000000Z");
        assertThat(AwsV4Signer.encodePath("/wind/gwa/a b.tif")).isEqualTo("/wind/gwa/a%20b.tif");
    }

    private static int px(double lon) {
        return (int) Math.floor((lon + 180) / 360 * 256);
    }

    private static int py(double lat, int z) {
        double y = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2;
        return (int) Math.floor(y * 256 * (1 << z));
    }
}
