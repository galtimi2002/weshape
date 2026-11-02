package com.weshape.backend.wind;

import io.airlift.compress.zstd.ZstdCompressor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.IntBinaryOperator;

/**
 * Writes small tiled, ZSTD-compressed GeoTIFFs laid out like the real wind files
 * (8-bit, horizontal predictor, nodata 255, internal overviews), for tests.
 */
public final class TestCogWriter {

    public static final double ORIGIN_X = -180;
    public static final double ORIGIN_Y = 80;
    public static final double PIXEL = 0.25;
    static final int WIDTH = 1440;
    static final int HEIGHT = 576;
    static final int TILE = 256;

    private TestCogWriter() {}

    /** Writes a file whose full-resolution cell (col, row) holds values.applyAsInt(col, row); overviews take every 2nd cell. */
    public static void write(Path file, IntBinaryOperator values, boolean bigTiff) throws IOException {
        List<int[]> sizes = List.of(new int[] {WIDTH, HEIGHT}, new int[] {WIDTH / 2, HEIGHT / 2}, new int[] {WIDTH / 4, HEIGHT / 4});
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int headerSize = bigTiff ? 16 : 8;
        List<long[]> offsets = new ArrayList<>();
        List<long[]> counts = new ArrayList<>();
        ZstdCompressor zstd = new ZstdCompressor();
        for (int level = 0; level < sizes.size(); level++) {
            int w = sizes.get(level)[0];
            int h = sizes.get(level)[1];
            int step = 1 << level;
            int across = (w + TILE - 1) / TILE;
            int down = (h + TILE - 1) / TILE;
            long[] off = new long[across * down];
            long[] cnt = new long[across * down];
            for (int ty = 0; ty < down; ty++) {
                for (int tx = 0; tx < across; tx++) {
                    byte[] raw = new byte[TILE * TILE];
                    for (int r = 0; r < TILE; r++) {
                        for (int c = 0; c < TILE; c++) {
                            int col = tx * TILE + c;
                            int row = ty * TILE + r;
                            int v = col < w && row < h ? values.applyAsInt(col * step, row * step) : 255;
                            raw[r * TILE + c] = (byte) v;
                        }
                    }
                    for (int r = 0; r < TILE; r++) {
                        for (int c = TILE - 1; c > 0; c--) {
                            raw[r * TILE + c] = (byte) (raw[r * TILE + c] - raw[r * TILE + c - 1]);
                        }
                    }
                    byte[] packed = new byte[zstd.maxCompressedLength(raw.length)];
                    int n = zstd.compress(raw, 0, raw.length, packed, 0, packed.length);
                    off[ty * across + tx] = headerSize + body.size();
                    cnt[ty * across + tx] = n;
                    body.write(packed, 0, n);
                }
            }
            offsets.add(off);
            counts.add(cnt);
        }

        // IFDs after the tile data, each followed by the values that do not fit in an entry.
        ByteBuffer out = ByteBuffer.allocate(headerSize + body.size() + 4_000_000).order(ByteOrder.LITTLE_ENDIAN);
        out.put((byte) 'I').put((byte) 'I');
        if (bigTiff) {
            out.putShort((short) 43).putShort((short) 8).putShort((short) 0).putLong(0);
        } else {
            out.putShort((short) 42).putInt(0);
        }
        out.put(body.toByteArray());
        long previousNextPointer = bigTiff ? 8 : 4;
        for (int level = 0; level < sizes.size(); level++) {
            TreeMap<Integer, Object[]> tags = new TreeMap<>();
            tags.put(254, new Object[] {4, new long[] {level == 0 ? 0 : 1}});
            tags.put(256, new Object[] {4, new long[] {sizes.get(level)[0]}});
            tags.put(257, new Object[] {4, new long[] {sizes.get(level)[1]}});
            tags.put(258, new Object[] {3, new long[] {8}});
            tags.put(259, new Object[] {3, new long[] {50000}});
            tags.put(262, new Object[] {3, new long[] {1}});
            tags.put(277, new Object[] {3, new long[] {1}});
            tags.put(284, new Object[] {3, new long[] {1}});
            tags.put(317, new Object[] {3, new long[] {2}});
            tags.put(322, new Object[] {3, new long[] {TILE}});
            tags.put(323, new Object[] {3, new long[] {TILE}});
            tags.put(324, new Object[] {bigTiff ? 16 : 4, offsets.get(level)});
            tags.put(325, new Object[] {bigTiff ? 16 : 4, counts.get(level)});
            if (level == 0) {
                tags.put(33550, new Object[] {12, new double[] {PIXEL, PIXEL, 0}});
                tags.put(33922, new Object[] {12, new double[] {0, 0, 0, ORIGIN_X, ORIGIN_Y, 0}});
                tags.put(34735, new Object[] {3, new long[] {1, 1, 0, 1, 1025, 0, 1, 1}});
                tags.put(42113, new Object[] {2, "255\0".getBytes(StandardCharsets.US_ASCII)});
            }
            long ifdAt = out.position();
            if (bigTiff) {
                out.putLong((int) previousNextPointer, ifdAt);
            } else {
                out.putInt((int) previousNextPointer, (int) ifdAt);
            }
            int entrySize = bigTiff ? 20 : 12;
            int inline = bigTiff ? 8 : 4;
            long extraAt = ifdAt + (bigTiff ? 8 : 2) + (long) tags.size() * entrySize + (bigTiff ? 8 : 4);
            ByteBuffer extra = ByteBuffer.allocate(2_000_000).order(ByteOrder.LITTLE_ENDIAN);
            if (bigTiff) {
                out.putLong(tags.size());
            } else {
                out.putShort((short) tags.size());
            }
            for (var e : tags.entrySet()) {
                int type = (int) e.getValue()[0];
                Object v = e.getValue()[1];
                byte[] data = encode(type, v);
                int count = type == 2 ? data.length : type == 12 ? ((double[]) v).length : ((long[]) v).length;
                out.putShort(e.getKey().shortValue()).putShort((short) type);
                if (bigTiff) {
                    out.putLong(count);
                } else {
                    out.putInt(count);
                }
                byte[] slot = new byte[inline];
                if (data.length <= inline) {
                    System.arraycopy(data, 0, slot, 0, data.length);
                } else {
                    ByteBuffer p = ByteBuffer.wrap(slot).order(ByteOrder.LITTLE_ENDIAN);
                    if (bigTiff) {
                        p.putLong(extraAt + extra.position());
                    } else {
                        p.putInt((int) (extraAt + extra.position()));
                    }
                    extra.put(data);
                    if (extra.position() % 2 == 1) {
                        extra.put((byte) 0);
                    }
                }
                out.put(slot);
            }
            previousNextPointer = out.position();
            if (bigTiff) {
                out.putLong(0);
            } else {
                out.putInt(0);
            }
            out.put(extra.array(), 0, extra.position());
        }
        byte[] all = new byte[out.position()];
        out.flip();
        out.get(all);
        Files.createDirectories(file.getParent());
        Files.write(file, all);
    }

    private static byte[] encode(int type, Object v) {
        if (type == 2) {
            return (byte[]) v;
        }
        if (type == 12) {
            double[] d = (double[]) v;
            ByteBuffer b = ByteBuffer.allocate(d.length * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (double x : d) {
                b.putDouble(x);
            }
            return b.array();
        }
        long[] l = (long[]) v;
        int size = type == 3 ? 2 : type == 4 ? 4 : 8;
        ByteBuffer b = ByteBuffer.allocate(l.length * size).order(ByteOrder.LITTLE_ENDIAN);
        for (long x : l) {
            if (size == 2) {
                b.putShort((short) x);
            } else if (size == 4) {
                b.putInt((int) x);
            } else {
                b.putLong(x);
            }
        }
        return b.array();
    }

    // ---- the test world ----

    /** Wind speed code at 100 m: 3.0 to 7.9 m/s across longitudes; no data south of 45° S and in a "sea" box. */
    static int ws100(int col, int row) {
        double lat = ORIGIN_Y - (row + 0.5) * PIXEL;
        double lon = ORIGIN_X + (col + 0.5) * PIXEL;
        if (lat < -45 || (lon > 0 && lon < 10 && lat > 30 && lat < 40)) {
            return 255;
        }
        return 30 + col % 50;
    }

    static IntBinaryOperator scaled(double factor) {
        return (c, r) -> {
            int v = ws100(c, r);
            return v == 255 ? 255 : (int) Math.min(254, Math.round(v * factor));
        };
    }

    static int pd100(int col, int row) {
        int v = ws100(col, row);
        if (v == 255) {
            return 255;
        }
        double speed = v / 10.0;
        return (int) Math.min(254, Math.round(Math.log1p(0.6 * speed * speed * speed) * 30));
    }

    /** All five layer files under dir/wind/gwa/. */
    static void writeWorld(Path dir, boolean bigTiff) throws IOException {
        Path base = dir.resolve("wind/gwa");
        write(base.resolve("ws100.tif"), TestCogWriter::ws100, bigTiff);
        write(base.resolve("ws10.tif"), scaled(0.6), bigTiff);
        write(base.resolve("ws50.tif"), scaled(0.85), bigTiff);
        write(base.resolve("ws200.tif"), scaled(1.12), bigTiff);
        write(base.resolve("pd100.tif"), TestCogWriter::pd100, bigTiff);
    }
}
