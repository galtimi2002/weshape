package com.weshape.backend.bio;

import com.weshape.backend.wind.TestCogWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** A small biomass world laid out like the workflow's output, for tests. */
final class BioTestFiles {

    private BioTestFiles() {}

    /** Sea between 0 and 10° E north of 30° N; everywhere else Cluj-like values. */
    static void write(Path dir) throws IOException {
        Path base = dir.resolve("bio");
        writeOne(base, "density", 152);  // 1.58 t/ha
        writeOne(base, "crop", 171);     // 124 kt
        writeOne(base, "forest", 161);   // 82 kt
        writeOne(base, "energy", 185);   // 890 GWh
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        Path tiles = base.resolve("tiles");
        Files.createDirectories(tiles);
        for (String layer : BioData.LAYERS) {
            Files.write(tiles.resolve(layer + ".tiles"), png);
            ByteArrayOutputStream index = new ByteArrayOutputStream();
            index.write(ByteBuffer.allocate(12).put("WSTI".getBytes()).putInt(1).putInt(1).array());
            index.write(ByteBuffer.allocate(20).putLong(0L).putLong(0).putInt(png.length).array());
            Files.write(tiles.resolve(layer + ".index"), index.toByteArray());
        }
    }

    private static void writeOne(Path base, String name, int code) throws IOException {
        TestCogWriter.write(base.resolve(name + ".tif"), (c, r) -> {
            double lat = TestCogWriter.ORIGIN_Y - (r + 0.5) * TestCogWriter.PIXEL;
            double lon = TestCogWriter.ORIGIN_X + (c + 0.5) * TestCogWriter.PIXEL;
            return lon > 0 && lon < 10 && lat > 30 ? 255 : code;
        }, false);
    }
}
