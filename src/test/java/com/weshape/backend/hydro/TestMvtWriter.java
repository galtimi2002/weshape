package com.weshape.backend.hydro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/** Writes small river tile sets the way the build workflow does (gzip MVT + WSTI index), for tests. */
final class TestMvtWriter {

    static final int EXTENT = 4096;

    /** A river line: points as {lat, lon}, and its values (p, q, qa, ...). */
    record River(double[][] points, Map<String, Double> values) {}

    /** Writes hydro/tiles/rivers.tiles and .index under dir, with every river in each z10 tile it touches. */
    static void write(Path dir, List<River> rivers) throws IOException {
        int z = HydroData.MAX_ZOOM;
        Map<Long, List<River>> byTile = new TreeMap<>();
        for (River r : rivers) {
            for (double[] p : r.points()) {
                long key = com.weshape.backend.wind.TileIndex.key(z, (int) gx(p[1], z), (int) gy(p[0], z));
                List<River> list = byTile.computeIfAbsent(key, k -> new ArrayList<>());
                if (!list.contains(r)) {
                    list.add(r);
                }
            }
        }
        Path out = dir.resolve("hydro/tiles");
        Files.createDirectories(out);
        ByteArrayOutputStream pack = new ByteArrayOutputStream();
        ByteArrayOutputStream index = new ByteArrayOutputStream();
        ByteBuffer head = ByteBuffer.allocate(12).put("WSTI".getBytes()).putInt(1).putInt(byTile.size());
        index.write(head.array());
        for (var e : byTile.entrySet()) {
            long key = e.getKey();
            int tx = (int) ((key >>> 24) & 0xffffff);
            int ty = (int) (key & 0xffffff);
            byte[] tile = gzip(tile(e.getValue(), z, tx, ty));
            index.write(ByteBuffer.allocate(20).putLong(key).putLong(pack.size()).putInt(tile.length).array());
            pack.write(tile);
        }
        Files.write(out.resolve("rivers.tiles"), pack.toByteArray());
        Files.write(out.resolve("rivers.index"), index.toByteArray());
    }

    static double gx(double lon, int z) {
        return (lon + 180) / 360 * (1 << z);
    }

    static double gy(double lat, int z) {
        double r = Math.toRadians(lat);
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * (1 << z);
    }

    static byte[] tile(List<River> rivers, int z, int tx, int ty) throws IOException {
        Map<String, Integer> keys = new LinkedHashMap<>();
        Map<Double, Integer> values = new LinkedHashMap<>();
        ByteArrayOutputStream features = new ByteArrayOutputStream();
        for (River r : rivers) {
            List<Integer> tags = new ArrayList<>();
            for (var v : r.values().entrySet()) {
                tags.add(keys.computeIfAbsent(v.getKey(), k -> keys.size()));
                tags.add(values.computeIfAbsent(v.getValue(), k -> values.size()));
            }
            List<Integer> geom = new ArrayList<>();
            int cx = 0;
            int cy = 0;
            for (int i = 0; i < r.points().length; i++) {
                int x = (int) Math.round((gx(r.points()[i][1], z) - tx) * EXTENT);
                int y = (int) Math.round((gy(r.points()[i][0], z) - ty) * EXTENT);
                if (i == 0) {
                    geom.add(1 | (1 << 3));
                } else if (i == 1) {
                    geom.add(2 | ((r.points().length - 1) << 3));
                }
                geom.add(zig(x - cx));
                geom.add(zig(y - cy));
                cx = x;
                cy = y;
            }
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            packed(f, 2, tags);
            varint(f, (3 << 3));
            varint(f, 2);
            packed(f, 4, geom);
            bytes(features, 2, f.toByteArray());
        }
        ByteArrayOutputStream layer = new ByteArrayOutputStream();
        varint(layer, (15 << 3));
        varint(layer, 2);
        bytes(layer, 1, HydroData.LAYER.getBytes());
        layer.write(features.toByteArray());
        for (String k : keys.keySet()) {
            bytes(layer, 3, k.getBytes());
        }
        for (Double v : values.keySet()) {
            ByteArrayOutputStream val = new ByteArrayOutputStream();
            varint(val, (3 << 3) | 1);
            val.write(ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putDouble(v).array());
            bytes(layer, 4, val.toByteArray());
        }
        varint(layer, (5 << 3));
        varint(layer, EXTENT);
        ByteArrayOutputStream t = new ByteArrayOutputStream();
        bytes(t, 3, layer.toByteArray());
        return t.toByteArray();
    }

    private static int zig(int n) {
        return (n << 1) ^ (n >> 31);
    }

    private static void packed(ByteArrayOutputStream out, int field, List<Integer> ints) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int i : ints) {
            varint(b, i & 0xffffffffL);
        }
        bytes(out, field, b.toByteArray());
    }

    private static void bytes(ByteArrayOutputStream out, int field, byte[] data) {
        varint(out, ((long) field << 3) | 2);
        varint(out, data.length);
        out.writeBytes(data);
    }

    private static void varint(ByteArrayOutputStream out, long v) {
        while ((v & ~0x7fL) != 0) {
            out.write((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        }
        return out.toByteArray();
    }

    /** Someșul Mic through Cluj-Napoca, and a small mountain stream to the west. */
    static List<River> cluj() {
        return List.of(
                new River(new double[][] {{46.760, 23.540}, {46.772, 23.590}, {46.780, 23.640}},
                        Map.of("p", 790.0, "q", 14.4, "qa", 16.0, "qn", 5.0, "qx", 42.0, "h", 7.0, "l", 5.2)),
                new River(new double[][] {{46.700, 23.300}, {46.720, 23.330}},
                        Map.of("p", 85.0, "q", 0.45, "qa", 0.5, "qn", 0.1, "qx", 1.6, "h", 24.0, "l", 3.1)));
    }
}
