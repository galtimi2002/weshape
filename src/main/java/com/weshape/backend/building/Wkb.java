package com.weshape.backend.building;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads building outlines from Well-Known Binary, the way GeoParquet (Overture) stores them:
 * Polygon or MultiPolygon, with or without Z (ISO 1003/1006 or EWKB flag), either byte order.
 * Coordinates are longitude, latitude.
 */
public final class Wkb {

    /** One polygon: the outer ring first, then the holes; each ring a list of {lon, lat}, closed. */
    public record Polygon(List<double[]> outer, List<List<double[]>> holes) {
    }

    private Wkb() {
    }

    /** The polygons of a Polygon or MultiPolygon; an IllegalArgumentException for anything else. */
    public static List<Polygon> polygons(byte[] wkb) {
        ByteBuffer buf = ByteBuffer.wrap(wkb);
        List<Polygon> out = new ArrayList<>();
        int type = header(buf);
        int dims = dims(type);
        switch (base(type)) {
            case 3 -> out.add(polygon(buf, dims));
            case 6 -> {
                int n = count(buf);
                for (int i = 0; i < n; i++) {
                    int inner = header(buf);
                    if (base(inner) != 3) {
                        throw new IllegalArgumentException("MultiPolygon with a part of type " + inner);
                    }
                    out.add(polygon(buf, dims(inner)));
                }
            }
            default -> throw new IllegalArgumentException("Not a polygon: WKB type " + type);
        }
        return out;
    }

    /** Reads the byte order and returns the geometry type (with its Z/M information). */
    private static int header(ByteBuffer buf) {
        byte order = buf.get();
        buf.order(order == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int type = buf.getInt();
        if ((type & 0x20000000) != 0) {
            buf.getInt(); // EWKB SRID
        }
        return type;
    }

    private static int base(int type) {
        return (type & 0x0FFFFFFF) % 1000;
    }

    /** Numbers per point: 2, 3 (Z or M) or 4 (Z and M). */
    private static int dims(int type) {
        int dims = 2;
        boolean z = (type & 0x80000000) != 0;
        boolean m = (type & 0x40000000) != 0;
        int iso = (type & 0x0FFFFFFF) / 1000;
        if (iso == 1 || iso == 2) {
            dims++;
        } else if (iso == 3) {
            dims += 2;
        }
        if (z) dims++;
        if (m) dims++;
        return dims;
    }

    private static int count(ByteBuffer buf) {
        int n = buf.getInt();
        if (n < 0 || n > buf.remaining()) {
            throw new IllegalArgumentException("Bad WKB count " + n);
        }
        return n;
    }

    private static Polygon polygon(ByteBuffer buf, int dims) {
        int rings = count(buf);
        if (rings == 0) {
            return new Polygon(List.of(), List.of());
        }
        List<double[]> outer = ring(buf, dims);
        List<List<double[]>> holes = new ArrayList<>();
        for (int i = 1; i < rings; i++) {
            holes.add(ring(buf, dims));
        }
        return new Polygon(outer, holes);
    }

    private static List<double[]> ring(ByteBuffer buf, int dims) {
        int n = count(buf);
        List<double[]> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double x = buf.getDouble();
            double y = buf.getDouble();
            for (int d = 2; d < dims; d++) {
                buf.getDouble();
            }
            pts.add(new double[] {x, y});
        }
        return pts;
    }
}
