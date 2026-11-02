package com.weshape.backend.hydro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the lines and their values from one Mapbox Vector Tile (protobuf, already unzipped).
 * Only what the river tiles use: line features with number values.
 */
final class MvtReader {

    /** A line feature: its parts (x0, y0, x1, y1, ... in tile units 0..extent) and its values. */
    record Line(List<int[]> parts, Map<String, Double> values) {}

    record Layer(String name, int extent, List<Line> lines) {}

    private final byte[] buf;
    private int pos;
    private final int end;

    private MvtReader(byte[] buf, int pos, int end) {
        this.buf = buf;
        this.pos = pos;
        this.end = end;
    }

    /** The named layer of the tile, or null when the tile has no such layer. */
    static Layer layer(byte[] tile, String name) throws IOException {
        MvtReader r = new MvtReader(tile, 0, tile.length);
        while (r.pos < r.end) {
            long key = r.varint();
            if ((key >>> 3) == 3 && (key & 7) == 2) {
                MvtReader sub = r.sub();
                Layer layer = sub.readLayer(name);
                if (layer != null) {
                    return layer;
                }
            } else {
                r.skip((int) (key & 7));
            }
        }
        return null;
    }

    private Layer readLayer(String wanted) throws IOException {
        String name = null;
        int extent = 4096;
        List<String> keys = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<int[]> featureSpans = new ArrayList<>();
        while (pos < end) {
            long key = varint();
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 1 && wire == 2) {
                name = string();
            } else if (field == 2 && wire == 2) {
                int len = (int) varint();
                featureSpans.add(new int[] {pos, pos + len});
                pos += len;
            } else if (field == 3 && wire == 2) {
                keys.add(string());
            } else if (field == 4 && wire == 2) {
                values.add(sub().readValue());
            } else if (field == 5 && wire == 0) {
                extent = (int) varint();
            } else {
                skip(wire);
            }
        }
        if (name == null || !name.equals(wanted)) {
            return null;
        }
        List<Line> lines = new ArrayList<>();
        for (int[] span : featureSpans) {
            Line line = new MvtReader(buf, span[0], span[1]).readFeature(keys, values);
            if (line != null) {
                lines.add(line);
            }
        }
        return new Layer(name, extent, lines);
    }

    private Double readValue() throws IOException {
        Double v = null;
        while (pos < end) {
            long key = varint();
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            switch (field) {
                case 2 -> v = (double) Float.intBitsToFloat(fixed32());
                case 3 -> v = Double.longBitsToDouble(fixed64());
                case 4, 5 -> v = (double) varint();
                case 6 -> {
                    long z = varint();
                    v = (double) ((z >>> 1) ^ -(z & 1));
                }
                case 7 -> v = varint() != 0 ? 1.0 : 0.0;
                default -> skip(wire);
            }
        }
        return v;
    }

    private Line readFeature(List<String> keys, List<Double> values) throws IOException {
        int[] tags = new int[0];
        int[] geometry = new int[0];
        int type = 0;
        while (pos < end) {
            long key = varint();
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 2 && wire == 2) {
                tags = sub().packed();
            } else if (field == 3 && wire == 0) {
                type = (int) varint();
            } else if (field == 4 && wire == 2) {
                geometry = sub().packed();
            } else {
                skip(wire);
            }
        }
        if (type != 2) {
            return null; // only lines
        }
        Map<String, Double> props = new HashMap<>();
        for (int i = 0; i + 1 < tags.length; i += 2) {
            if (tags[i] < keys.size() && tags[i + 1] < values.size() && values.get(tags[i + 1]) != null) {
                props.put(keys.get(tags[i]), values.get(tags[i + 1]));
            }
        }
        List<int[]> parts = new ArrayList<>();
        int x = 0;
        int y = 0;
        int i = 0;
        List<Integer> current = null;
        while (i < geometry.length) {
            int cmd = geometry[i] & 7;
            int count = geometry[i] >>> 3;
            i++;
            if (cmd == 7) {
                continue;
            }
            for (int k = 0; k < count && i + 1 < geometry.length; k++) {
                x += zigzag(geometry[i++]);
                y += zigzag(geometry[i++]);
                if (cmd == 1) {
                    if (current != null && current.size() >= 4) {
                        parts.add(toArray(current));
                    }
                    current = new ArrayList<>();
                }
                if (current != null) {
                    current.add(x);
                    current.add(y);
                }
            }
        }
        if (current != null && current.size() >= 4) {
            parts.add(toArray(current));
        }
        return parts.isEmpty() ? null : new Line(parts, props);
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static int zigzag(int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    // ---- protobuf basics ----

    private MvtReader sub() throws IOException {
        int len = (int) varint();
        if (len < 0 || pos + len > end) {
            throw new IOException("broken vector tile");
        }
        MvtReader r = new MvtReader(buf, pos, pos + len);
        pos += len;
        return r;
    }

    private int[] packed() throws IOException {
        int[] out = new int[16];
        int n = 0;
        while (pos < end) {
            if (n == out.length) {
                out = java.util.Arrays.copyOf(out, n * 2);
            }
            out[n++] = (int) varint();
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private String string() throws IOException {
        int len = (int) varint();
        if (len < 0 || pos + len > end) {
            throw new IOException("broken vector tile");
        }
        String s = new String(buf, pos, len, java.nio.charset.StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    private long varint() throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (pos >= end) {
                throw new IOException("broken vector tile");
            }
            byte b = buf[pos++];
            result |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("broken vector tile");
    }

    private int fixed32() throws IOException {
        if (pos + 4 > end) {
            throw new IOException("broken vector tile");
        }
        int v = (buf[pos] & 0xff) | (buf[pos + 1] & 0xff) << 8 | (buf[pos + 2] & 0xff) << 16 | (buf[pos + 3] & 0xff) << 24;
        pos += 4;
        return v;
    }

    private long fixed64() throws IOException {
        long lo = fixed32() & 0xffffffffL;
        long hi = fixed32() & 0xffffffffL;
        return lo | hi << 32;
    }

    private void skip(int wire) throws IOException {
        switch (wire) {
            case 0 -> varint();
            case 1 -> pos += 8;
            case 2 -> pos += (int) varint();
            case 5 -> pos += 4;
            default -> throw new IOException("broken vector tile");
        }
        if (pos > end) {
            throw new IOException("broken vector tile");
        }
    }
}
