package com.weshape.backend.geo;

import com.weshape.backend.wind.TestCogWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** A small geothermal world laid out like the workflow's output, for tests. */
final class GeoTestFiles {

    private GeoTestFiles() {}

    static double lat(int row) {
        return TestCogWriter.ORIGIN_Y - (row + 0.5) * TestCogWriter.PIXEL;
    }

    static double lon(int col) {
        return TestCogWriter.ORIGIN_X + (col + 0.5) * TestCogWriter.PIXEL;
    }

    /** Sea between 0 and 10° E north of 30° N; ground 10.5 °C; heat flow 68 mW/m² except east of 100° E. */
    static void write(Path dir) throws IOException {
        Path base = dir.resolve("geo");
        TestCogWriter.write(base.resolve("ground.tif"), (c, r) -> sea(c, r) ? 255 : (int) Math.round((10.5 + 30) * 4), false);
        TestCogWriter.write(base.resolve("heatflow.tif"), (c, r) -> sea(c, r) || lon(c) > 100 ? 255 : 68, false);
        // One stored tile per layer: z0.
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        Path tiles = base.resolve("tiles");
        Files.createDirectories(tiles);
        for (String layer : GeoData.LAYERS) {
            Files.write(tiles.resolve(layer + ".tiles"), png);
            ByteArrayOutputStream index = new ByteArrayOutputStream();
            index.write(ByteBuffer.allocate(12).put("WSTI".getBytes()).putInt(1).putInt(1).array());
            index.write(ByteBuffer.allocate(20).putLong(0L).putLong(0).putInt(png.length).array());
            Files.write(tiles.resolve(layer + ".index"), index.toByteArray());
        }
    }

    private static boolean sea(int col, int row) {
        double la = lat(row);
        double lo = lon(col);
        return lo > 0 && lo < 10 && la > 30;
    }
}
