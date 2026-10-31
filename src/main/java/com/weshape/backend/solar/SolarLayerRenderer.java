package com.weshape.backend.solar;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Paints a layer as one PNG covering {@link #BOUNDS}, ready to lay over the map.
 *
 * <p>Rows are spaced evenly in Web Mercator, the projection web maps use, so the picture lines
 * up with the base map at every latitude. Images are made once and kept: the data does not
 * change while the server runs.
 */
@Component
public class SolarLayerRenderer {

    /** West, south, east, north in degrees: the whole world between Antarctica and the far north. */
    public static final double[] BOUNDS = {-180, -60, 180, 75};

    static final int WIDTH = 2048;
    static final int HEIGHT = 1500;

    private final SolarResource resource;
    private final Map<SolarLayer, byte[]> cache = new EnumMap<>(SolarLayer.class);

    public SolarLayerRenderer(SolarResource resource) {
        this.resource = resource;
    }

    public synchronized byte[] png(SolarLayer layer) {
        return cache.computeIfAbsent(layer, this::render);
    }

    private byte[] render(SolarLayer layer) {
        int[][] stops = parse(layer.colors());
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        double top = mercatorY(BOUNDS[3]);
        double bottom = mercatorY(BOUNDS[1]);
        for (int y = 0; y < HEIGHT; y++) {
            double lat = latitude(top + (bottom - top) * (y + .5) / HEIGHT);
            for (int x = 0; x < WIDTH; x++) {
                double lon = BOUNDS[0] + (BOUNDS[2] - BOUNDS[0]) * (x + .5) / WIDTH;
                double v = resource.value(layer, lat, lon);
                int alpha = (int) Math.round(255 * edgeFade(x, y));
                image.setRGB(x, y, (alpha << 24) | color(stops, (v - layer.min()) / (layer.max() - layer.min())));
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fades the picture out at its northern and southern edges, so there is no hard border. */
    static double edgeFade(int x, int y) {
        double f = Math.min(1, Math.min(y, HEIGHT - 1 - y) / (HEIGHT * .04));
        return f * f * (3 - 2 * f);
    }

    static int color(int[][] stops, double t) {
        t = Math.max(0, Math.min(1, t));
        double f = t * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) Math.floor(f));
        double u = f - i;
        int rgb = 0;
        for (int c = 0; c < 3; c++) {
            int v = (int) Math.round(stops[i][c] + (stops[i + 1][c] - stops[i][c]) * u);
            rgb = (rgb << 8) | v;
        }
        return rgb;
    }

    private static int[][] parse(String[] hex) {
        int[][] out = new int[hex.length][3];
        for (int i = 0; i < hex.length; i++) {
            int v = Integer.parseInt(hex[i].substring(1), 16);
            out[i] = new int[]{(v >> 16) & 255, (v >> 8) & 255, v & 255};
        }
        return out;
    }

    static double mercatorY(double lat) {
        return Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2));
    }

    static double latitude(double mercatorY) {
        return Math.toDegrees(2 * Math.atan(Math.exp(mercatorY)) - Math.PI / 2);
    }
}
