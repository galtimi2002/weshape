package com.weshape.backend.wind;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Where each stored map tile sits in a layer's tile file (written by
 * tool/gwa_render_tiles.py): "WSTI", version 1, count, then per tile, sorted by key,
 * key = z &lt;&lt; 48 | x &lt;&lt; 24 | y, offset, length; all big-endian.
 */
final class TileIndex {

    private final long[] keys;
    private final long[] offsets;
    private final int[] lengths;

    private TileIndex(long[] keys, long[] offsets, int[] lengths) {
        this.keys = keys;
        this.offsets = offsets;
        this.lengths = lengths;
    }

    static TileIndex parse(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data);
        if (data.length < 12 || b.get() != 'W' || b.get() != 'S' || b.get() != 'T' || b.get() != 'I') {
            throw new IOException("not a tile index");
        }
        int version = b.getInt();
        int count = b.getInt();
        if (version != 1 || count < 0 || data.length < 12 + (long) count * 20) {
            throw new IOException("tile index version " + version + ", " + count + " tiles");
        }
        long[] keys = new long[count];
        long[] offsets = new long[count];
        int[] lengths = new int[count];
        for (int i = 0; i < count; i++) {
            keys[i] = b.getLong();
            offsets[i] = b.getLong();
            lengths[i] = b.getInt();
        }
        return new TileIndex(keys, offsets, lengths);
    }

    static long key(int z, int x, int y) {
        return ((long) z << 48) | ((long) x << 24) | y;
    }

    int size() {
        return keys.length;
    }

    /** {offset, length} of the tile, or null when there is no tile (no data there). */
    long[] find(int z, int x, int y) {
        int i = Arrays.binarySearch(keys, key(z, x, y));
        return i < 0 ? null : new long[] {offsets[i], lengths[i]};
    }
}
