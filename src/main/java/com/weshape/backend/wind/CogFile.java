package com.weshape.backend.wind;

import io.airlift.compress.zstd.ZstdDecompressor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A single-band, 8-bit, tiled (cloud-optimised) GeoTIFF read through byte ranges:
 * the header and tile index once, then only the tiles that are needed.
 *
 * Supports classic TIFF and BigTIFF, ZSTD / DEFLATE / no compression, horizontal
 * predictor, and the internal overviews (each one a level, 0 = full resolution).
 */
public class CogFile {

    private static final int HEADER_BLOCK = 256 * 1024;

    /** One resolution level. */
    public record Level(int width, int height, int tileWidth, int tileHeight, long[] offsets, long[] counts,
                        double originX, double originY, double pixelX, double pixelY) {
        int tilesAcross() {
            return (width + tileWidth - 1) / tileWidth;
        }
    }

    private final RangeSource source;
    private final String key;
    private final Map<Long, byte[]> headerBlocks = new HashMap<>();
    private final List<Level> levels;
    private final int compression;
    private final int predictor;
    private final int nodata;
    private ByteOrder order;
    private boolean big;

    public CogFile(RangeSource source, String key) throws IOException {
        this.source = source;
        this.key = key;
        byte[] head = bytes(0, 16);
        if (head[0] == 'I' && head[1] == 'I') {
            order = ByteOrder.LITTLE_ENDIAN;
        } else if (head[0] == 'M' && head[1] == 'M') {
            order = ByteOrder.BIG_ENDIAN;
        } else {
            throw new IOException(key + " is not a TIFF file");
        }
        ByteBuffer h = ByteBuffer.wrap(head).order(order);
        int magic = h.getShort(2) & 0xffff;
        big = magic == 43;
        if (!big && magic != 42) {
            throw new IOException(key + " is not a TIFF file");
        }
        long ifd = big ? h.getLong(8) : h.getInt(4) & 0xffffffffL;

        List<Map<Integer, long[]>> ifds = new ArrayList<>();
        Map<Integer, byte[]> firstRaw = null;
        while (ifd != 0 && ifds.size() < 32) {
            Map<Integer, byte[]> raw = new HashMap<>();
            Map<Integer, long[]> tags = new HashMap<>();
            ifd = readIfd(ifd, tags, raw);
            long subfile = tags.containsKey(254) ? tags.get(254)[0] : 0;
            if ((subfile & 4) != 0) {
                continue; // a mask, not data
            }
            ifds.add(tags);
            if (firstRaw == null) {
                firstRaw = raw;
            }
        }
        if (ifds.isEmpty()) {
            throw new IOException(key + " has no image");
        }
        Map<Integer, long[]> first = ifds.get(0);
        if (!first.containsKey(322)) {
            throw new IOException(key + " is not tiled");
        }
        long bits = first.containsKey(258) ? first.get(258)[0] : 1;
        long samples = first.containsKey(277) ? first.get(277)[0] : 1;
        if (bits != 8 || samples != 1) {
            throw new IOException(key + ": only single-band 8-bit images are supported");
        }
        compression = first.containsKey(259) ? (int) first.get(259)[0] : 1;
        predictor = first.containsKey(317) ? (int) first.get(317)[0] : 1;
        if (compression != 1 && compression != 8 && compression != 32946 && compression != 50000) {
            throw new IOException(key + ": compression " + compression + " is not supported");
        }
        byte[] nodataText = firstRaw.get(42113);
        nodata = nodataText == null ? -1
                : (int) Math.round(Double.parseDouble(new String(nodataText, StandardCharsets.US_ASCII).replace("\0", "").trim()));

        double[] scale = doubles(firstRaw.get(33550), 3);
        double[] tie = doubles(firstRaw.get(33922), 6);
        if (scale == null || tie == null) {
            throw new IOException(key + " has no georeference");
        }
        double px = scale[0];
        double py = scale[1];
        double ox = tie[3] - tie[0] * px;
        double oy = tie[4] + tie[1] * py;
        if (pixelIsPoint(firstRaw.get(34735))) {
            ox -= px / 2;
            oy += py / 2;
        }
        int fullWidth = (int) first.get(256)[0];
        int fullHeight = (int) first.get(257)[0];
        List<Level> list = new ArrayList<>();
        for (Map<Integer, long[]> t : ifds) {
            int w = (int) t.get(256)[0];
            int hgt = (int) t.get(257)[0];
            double sx = px * fullWidth / (double) w;
            double sy = py * fullHeight / (double) hgt;
            list.add(new Level(w, hgt, (int) t.get(322)[0], (int) t.get(323)[0], t.get(324), t.get(325), ox, oy, sx, sy));
        }
        levels = List.copyOf(list);
        headerBlocks.clear();
    }

    public List<Level> levels() {
        return levels;
    }

    /** The code for "no data" (sea beyond the coast, outside the atlas), or -1 when the file has none. */
    public int nodata() {
        return nodata;
    }

    public String key() {
        return key;
    }

    /** Index of tile (tx, ty) on a level. */
    public static long tileId(int level, int tx, int ty) {
        return ((long) level << 40) | ((long) ty << 20) | tx;
    }

    /** The decoded bytes of one tile (tileWidth x tileHeight, row by row); null when the tile is empty. */
    public byte[] readTile(int level, int tx, int ty) throws IOException {
        Level l = levels.get(level);
        int index = ty * l.tilesAcross() + tx;
        long offset = l.offsets()[index];
        long count = l.counts()[index];
        if (count == 0 || offset == 0) {
            return null;
        }
        byte[] packed = source.read(key, offset, (int) count);
        if (packed.length != count) {
            // The file is shorter than its header says: it was replaced since it was opened.
            throw new IOException(key + ": block " + index + " cut short (" + packed.length + " of " + count + " bytes)");
        }
        int size = l.tileWidth() * l.tileHeight();
        byte[] out = new byte[size];
        switch (compression) {
            case 1 -> System.arraycopy(packed, 0, out, 0, Math.min(size, packed.length));
            case 8, 32946 -> inflate(packed, out);
            case 50000 -> {
                int n = new ZstdDecompressor().decompress(packed, 0, packed.length, out, 0, size);
                if (n != size) {
                    throw new IOException(key + ": block " + index + " unpacks to " + n + " of " + size + " bytes");
                }
            }
            default -> throw new IOException("compression " + compression);
        }
        if (predictor == 2) {
            int w = l.tileWidth();
            for (int r = 0; r < l.tileHeight(); r++) {
                int base = r * w;
                for (int c = 1; c < w; c++) {
                    out[base + c] = (byte) (out[base + c] + out[base + c - 1]);
                }
            }
        }
        return out;
    }

    private static void inflate(byte[] packed, byte[] out) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            int n = 0;
            while (n < out.length && !inflater.finished()) {
                int got = inflater.inflate(out, n, out.length - n);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                n += got;
            }
        } catch (DataFormatException e) {
            throw new IOException("broken tile", e);
        } finally {
            inflater.end();
        }
    }

    // ---- header parsing ----

    private long readIfd(long at, Map<Integer, long[]> tags, Map<Integer, byte[]> raw) throws IOException {
        int countSize = big ? 8 : 2;
        int entrySize = big ? 20 : 12;
        ByteBuffer c = ByteBuffer.wrap(bytes(at, countSize)).order(order);
        long n = big ? c.getLong(0) : c.getShort(0) & 0xffff;
        ByteBuffer e = ByteBuffer.wrap(bytes(at + countSize, (int) (n * entrySize) + (big ? 8 : 4))).order(order);
        for (int i = 0; i < n; i++) {
            int p = i * entrySize;
            int tag = e.getShort(p) & 0xffff;
            int type = e.getShort(p + 2) & 0xffff;
            long count = big ? e.getLong(p + 4) : e.getInt(p + 4) & 0xffffffffL;
            int size = typeSize(type);
            long length = count * size;
            int inline = big ? 8 : 4;
            byte[] data;
            if (length <= inline) {
                data = Arrays.copyOfRange(e.array(), p + (big ? 12 : 8), p + (big ? 12 : 8) + (int) length);
            } else {
                long valueAt = big ? e.getLong(p + 12) : e.getInt(p + 8) & 0xffffffffL;
                data = bytes(valueAt, (int) length);
            }
            raw.put(tag, data);
            if (type == 3 || type == 4 || type == 16 || type == 1) {
                tags.put(tag, integers(data, type, (int) count));
            }
        }
        int nextAt = (int) (n * entrySize);
        return big ? e.getLong(nextAt) : e.getInt(nextAt) & 0xffffffffL;
    }

    private long[] integers(byte[] data, int type, int count) {
        ByteBuffer b = ByteBuffer.wrap(data).order(order);
        long[] out = new long[count];
        for (int i = 0; i < count; i++) {
            out[i] = switch (type) {
                case 1 -> data[i] & 0xff;
                case 3 -> b.getShort(i * 2) & 0xffff;
                case 4 -> b.getInt(i * 4) & 0xffffffffL;
                default -> b.getLong(i * 8);
            };
        }
        return out;
    }

    private double[] doubles(byte[] data, int min) {
        if (data == null || data.length < min * 8) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data).order(order);
        double[] out = new double[data.length / 8];
        for (int i = 0; i < out.length; i++) {
            out[i] = b.getDouble(i * 8);
        }
        return out;
    }

    /** GeoKeyDirectory: GTRasterTypeGeoKey (1025) = 2 means the tie point is a pixel centre. */
    private boolean pixelIsPoint(byte[] keys) {
        if (keys == null || keys.length < 8) {
            return false;
        }
        ByteBuffer b = ByteBuffer.wrap(keys).order(order);
        int n = b.getShort(6) & 0xffff;
        for (int i = 0; i < n && (i + 1) * 8 + 8 <= keys.length; i++) {
            int p = (i + 1) * 8;
            if ((b.getShort(p) & 0xffff) == 1025 && (b.getShort(p + 2) & 0xffff) == 0) {
                return (b.getShort(p + 6) & 0xffff) == 2;
            }
        }
        return false;
    }

    private static int typeSize(int type) {
        return switch (type) {
            case 1, 2, 6, 7 -> 1;
            case 3, 8 -> 2;
            case 4, 9, 11, 13 -> 4;
            default -> 8;
        };
    }

    /** Header bytes, read in large blocks so the IFDs and tile index take only a few requests. */
    private byte[] bytes(long offset, int length) throws IOException {
        byte[] out = new byte[length];
        int done = 0;
        while (done < length) {
            long at = offset + done;
            long block = at / HEADER_BLOCK;
            byte[] data = headerBlocks.get(block);
            if (data == null) {
                data = source.read(key, block * HEADER_BLOCK, HEADER_BLOCK);
                headerBlocks.put(block, data);
            }
            int from = (int) (at - block * HEADER_BLOCK);
            if (from >= data.length) {
                throw new IOException(key + ": header runs past the end of the file");
            }
            int n = Math.min(length - done, data.length - from);
            System.arraycopy(data, from, out, done, n);
            done += n;
        }
        return out;
    }
}
