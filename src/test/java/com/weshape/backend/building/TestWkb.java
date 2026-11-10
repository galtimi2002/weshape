package com.weshape.backend.building;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Builds WKB outlines for the tests: rectangles in metres around a place. */
final class TestWkb {

    static final double LAT = 46.7704;
    static final double LON = 23.5914;
    private static final double M_PER_DEG_LAT = Math.toRadians(1) * 6_371_008.8;

    private TestWkb() {
    }

    static double lat(double northM) {
        return LAT + northM / M_PER_DEG_LAT;
    }

    static double lon(double eastM) {
        return LON + eastM / (M_PER_DEG_LAT * Math.cos(Math.toRadians(LAT)));
    }

    /** A rectangle from (x0, y0) to (x1, y1) metres east/north of the place, counter-clockwise. */
    static double[][] rect(double x0, double y0, double x1, double y1) {
        return new double[][] {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}, {x0, y0}};
    }

    /** A Polygon in WKB, little-endian 2D, from rings of metre points. */
    static byte[] polygon(double[][]... rings) {
        return polygon(ByteOrder.LITTLE_ENDIAN, 3, 2, rings);
    }

    static byte[] polygon(ByteOrder order, int type, int dims, double[][]... rings) {
        int size = 1 + 4 + 4;
        for (double[][] r : rings) {
            size += 4 + r.length * dims * 8;
        }
        ByteBuffer b = ByteBuffer.allocate(size).order(order);
        b.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 1 : 0)).putInt(type).putInt(rings.length);
        for (double[][] r : rings) {
            b.putInt(r.length);
            for (double[] p : r) {
                b.putDouble(lon(p[0])).putDouble(lat(p[1]));
                for (int d = 2; d < dims; d++) {
                    b.putDouble(5.0);
                }
            }
        }
        return b.array();
    }

    /** A MultiPolygon of the given Polygon WKBs. */
    static byte[] multi(byte[]... parts) {
        int size = 9;
        for (byte[] p : parts) {
            size += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 1).putInt(6).putInt(parts.length);
        for (byte[] p : parts) {
            b.put(p);
        }
        return b.array();
    }
}
