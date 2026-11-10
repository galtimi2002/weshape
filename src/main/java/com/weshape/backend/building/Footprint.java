package com.weshape.backend.building;

import java.util.ArrayList;
import java.util.List;

/**
 * A building outline in metres on a flat plane around one place (x east, y north). Good enough for
 * the few hundred metres a lookup covers: area, walls and which way they face, distances.
 */
public final class Footprint {

    private static final double EARTH_RADIUS_M = 6_371_008.8;
    /** Walls shorter than this are corners and steps, not walls. */
    static final double MIN_WALL_M = 1.0;
    /** Neighbouring edges that turn less than this are one wall. */
    static final double SAME_WALL_DEG = 12;

    /** A straight wall: its length and the compass bearing it faces (0 north, 90 east). */
    public record Wall(double lengthM, double facingDeg) {
    }

    private final double originLat;
    private final double originLon;
    private final double cosLat;
    /** Per polygon: the outer ring, then the holes; each ring an array of {x, y}, closed. */
    private final List<List<double[][]>> polygons = new ArrayList<>();

    public Footprint(List<Wkb.Polygon> source, double originLat, double originLon) {
        this.originLat = originLat;
        this.originLon = originLon;
        this.cosLat = Math.cos(Math.toRadians(originLat));
        for (Wkb.Polygon p : source) {
            if (p.outer().size() < 4) {
                continue;
            }
            List<double[][]> rings = new ArrayList<>();
            rings.add(project(p.outer()));
            for (List<double[]> hole : p.holes()) {
                if (hole.size() >= 4) {
                    rings.add(project(hole));
                }
            }
            polygons.add(rings);
        }
    }

    public boolean isEmpty() {
        return polygons.isEmpty();
    }

    /** Metres east and north of the origin. */
    public double[] toLocal(double lat, double lon) {
        return new double[] {
                Math.toRadians(lon - originLon) * EARTH_RADIUS_M * cosLat,
                Math.toRadians(lat - originLat) * EARTH_RADIUS_M};
    }

    /** Longitude, latitude of a local point. */
    public double[] toLonLat(double x, double y) {
        return new double[] {
                originLon + Math.toDegrees(x / (EARTH_RADIUS_M * cosLat)),
                originLat + Math.toDegrees(y / EARTH_RADIUS_M)};
    }

    private double[][] project(List<double[]> ring) {
        double[][] out = new double[ring.size()][];
        for (int i = 0; i < ring.size(); i++) {
            out[i] = toLocal(ring.get(i)[1], ring.get(i)[0]);
        }
        return out;
    }

    /** Ground area in m²: outer rings less holes. */
    public double area() {
        double sum = 0;
        for (List<double[][]> rings : polygons) {
            sum += Math.abs(signedArea(rings.get(0)));
            for (int i = 1; i < rings.size(); i++) {
                sum -= Math.abs(signedArea(rings.get(i)));
            }
        }
        return Math.max(0, sum);
    }

    /** Length of the outer walls, m. */
    public double perimeter() {
        double sum = 0;
        for (List<double[][]> rings : polygons) {
            double[][] r = rings.get(0);
            for (int i = 0; i + 1 < r.length; i++) {
                sum += Math.hypot(r[i + 1][0] - r[i][0], r[i + 1][1] - r[i][1]);
            }
        }
        return sum;
    }

    /** The outer ring of the biggest part, as {lon, lat} points (closed). */
    public List<double[]> outline() {
        double[][] r = main();
        List<double[]> out = new ArrayList<>(r.length);
        for (double[] p : r) {
            out.add(toLonLat(p[0], p[1]));
        }
        return out;
    }

    /** Area-weighted centre of the biggest part, local metres. */
    public double[] centroid() {
        double[][] r = main();
        double a = 0, cx = 0, cy = 0;
        for (int i = 0; i + 1 < r.length; i++) {
            double cross = r[i][0] * r[i + 1][1] - r[i + 1][0] * r[i][1];
            a += cross;
            cx += (r[i][0] + r[i + 1][0]) * cross;
            cy += (r[i][1] + r[i + 1][1]) * cross;
        }
        if (Math.abs(a) < 1e-9) {
            return r[0].clone();
        }
        return new double[] {cx / (3 * a), cy / (3 * a)};
    }

    /** True when the local point is inside (and not in a hole). */
    public boolean contains(double x, double y) {
        for (List<double[][]> rings : polygons) {
            if (inside(rings.get(0), x, y)) {
                boolean inHole = false;
                for (int i = 1; i < rings.size() && !inHole; i++) {
                    inHole = inside(rings.get(i), x, y);
                }
                if (!inHole) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Metres from a local point to the building: 0 inside. */
    public double distanceTo(double x, double y) {
        if (contains(x, y)) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (List<double[][]> rings : polygons) {
            for (double[][] r : rings) {
                for (int i = 0; i + 1 < r.length; i++) {
                    best = Math.min(best, segmentDistance(x, y, r[i], r[i + 1]));
                }
            }
        }
        return best;
    }

    /** Metres between two buildings (same origin): 0 when they touch or overlap. */
    public double distanceTo(Footprint other) {
        double best = Double.MAX_VALUE;
        for (double[][] a : outerRings()) {
            for (double[][] b : other.outerRings()) {
                for (int i = 0; i + 1 < a.length; i++) {
                    for (int j = 0; j + 1 < b.length; j++) {
                        if (crosses(a[i], a[i + 1], b[j], b[j + 1])) {
                            return 0;
                        }
                        best = Math.min(best, Math.min(
                                Math.min(segmentDistance(a[i][0], a[i][1], b[j], b[j + 1]),
                                        segmentDistance(a[i + 1][0], a[i + 1][1], b[j], b[j + 1])),
                                Math.min(segmentDistance(b[j][0], b[j][1], a[i], a[i + 1]),
                                        segmentDistance(b[j + 1][0], b[j + 1][1], a[i], a[i + 1]))));
                    }
                }
            }
        }
        // One inside the other without crossing edges.
        double[][] a0 = main(), b0 = other.main();
        if (other.contains(a0[0][0], a0[0][1]) || contains(b0[0][0], b0[0][1])) {
            return 0;
        }
        return best;
    }

    /**
     * The walls of the biggest part, going round with the inside on the left: neighbouring edges
     * that keep the same direction are one wall; tiny steps are left out.
     */
    public List<Wall> walls() {
        double[][] r = main();
        if (signedArea(r) < 0) {
            double[][] rev = new double[r.length][];
            for (int i = 0; i < r.length; i++) {
                rev[i] = r[r.length - 1 - i];
            }
            r = rev;
        }
        // Edges as {dx, dy}, without zero-length ones.
        List<double[]> edges = new ArrayList<>();
        for (int i = 0; i + 1 < r.length; i++) {
            double dx = r[i + 1][0] - r[i][0], dy = r[i + 1][1] - r[i][1];
            if (Math.hypot(dx, dy) > 0.01) {
                edges.add(new double[] {dx, dy});
            }
        }
        if (edges.isEmpty()) {
            return List.of();
        }
        // Start at a corner, so a wall is not split where the ring happens to begin.
        int start = 0;
        for (int i = 0; i < edges.size(); i++) {
            double[] prev = edges.get((i + edges.size() - 1) % edges.size());
            if (turn(prev, edges.get(i)) >= SAME_WALL_DEG) {
                start = i;
                break;
            }
        }
        List<double[]> merged = new ArrayList<>();
        double[] cur = null;
        for (int k = 0; k < edges.size(); k++) {
            double[] e = edges.get((start + k) % edges.size());
            if (cur != null && turn(cur, e) < SAME_WALL_DEG) {
                cur = new double[] {cur[0] + e[0], cur[1] + e[1]};
            } else {
                if (cur != null) {
                    merged.add(cur);
                }
                cur = e.clone();
            }
        }
        merged.add(cur);
        List<Wall> out = new ArrayList<>();
        for (double[] e : merged) {
            double len = Math.hypot(e[0], e[1]);
            if (len >= MIN_WALL_M) {
                // Outward normal of an edge with the inside on its left: turn right.
                out.add(new Wall(len, bearing(e[1], -e[0])));
            }
        }
        return out;
    }

    /** Direction of the longest wall, 0–180° (0 north–south, 90 east–west). */
    public double mainAxis() {
        Wall longest = null;
        for (Wall w : walls()) {
            if (longest == null || w.lengthM() > longest.lengthM()) {
                longest = w;
            }
        }
        if (longest == null) {
            return 0;
        }
        // A wall runs at right angles to the way it faces.
        return (longest.facingDeg() + 90) % 180;
    }

    /** Compass bearing of a local vector: 0 north, 90 east. */
    public static double bearing(double dx, double dy) {
        double deg = Math.toDegrees(Math.atan2(dx, dy));
        return deg < 0 ? deg + 360 : deg;
    }

    private List<double[][]> outerRings() {
        List<double[][]> out = new ArrayList<>();
        for (List<double[][]> rings : polygons) {
            out.add(rings.get(0));
        }
        return out;
    }

    private double[][] main() {
        double[][] best = null;
        double bestArea = -1;
        for (List<double[][]> rings : polygons) {
            double a = Math.abs(signedArea(rings.get(0)));
            if (a > bestArea) {
                bestArea = a;
                best = rings.get(0);
            }
        }
        if (best == null) {
            throw new IllegalStateException("Empty footprint");
        }
        return best;
    }

    private static double turn(double[] a, double[] b) {
        double d = Math.abs(Math.toDegrees(Math.atan2(b[1], b[0]) - Math.atan2(a[1], a[0])));
        return d > 180 ? 360 - d : d;
    }

    private static double signedArea(double[][] r) {
        double s = 0;
        for (int i = 0; i + 1 < r.length; i++) {
            s += r[i][0] * r[i + 1][1] - r[i + 1][0] * r[i][1];
        }
        return s / 2;
    }

    private static boolean inside(double[][] r, double x, double y) {
        boolean in = false;
        for (int i = 0, j = r.length - 1; i < r.length; j = i++) {
            if ((r[i][1] > y) != (r[j][1] > y)
                    && x < (r[j][0] - r[i][0]) * (y - r[i][1]) / (r[j][1] - r[i][1]) + r[i][0]) {
                in = !in;
            }
        }
        return in;
    }

    private static double segmentDistance(double x, double y, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / len2));
        return Math.hypot(x - (a[0] + t * dx), y - (a[1] + t * dy));
    }

    private static boolean crosses(double[] p1, double[] p2, double[] q1, double[] q2) {
        double d1 = cross(q1, q2, p1), d2 = cross(q1, q2, p2);
        double d3 = cross(p1, p2, q1), d4 = cross(p1, p2, q2);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }
}
