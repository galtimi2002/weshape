package com.weshape.backend.geo;

import com.weshape.backend.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Geothermal map: layer list and map tiles (public), and the values for a place (signed in). */
@RestController
@RequestMapping("/api/v1/geo")
public class GeoController {

    static final String ATTRIBUTION = "Geothermal data: <a href=\"https://doi.org/10.5281/zenodo.4558732\" target=\"_blank\" "
            + "rel=\"noopener\">SoilTemp</a> (Lembrechts et al. 2022, CC BY 4.0), "
            + "<a href=\"https://doi.org/10.5880/fidgeo.2024.014\" target=\"_blank\" rel=\"noopener\">Global Heat Flow "
            + "Database</a> 2024 (IHFC)";
    static final List<String> COLORS = List.of("#FCEBDD", "#F3A77A", "#D45F35", "#8C2A1B");
    /** A fully transparent 256 px tile, for the sea. */
    private static final byte[] EMPTY_PNG = emptyPng();

    private final GeoData geo;

    public GeoController(GeoData geo) {
        this.geo = geo;
    }

    @GetMapping("/layers")
    public Map<String, Object> layers() {
        Map<String, Object> depthRanges = new LinkedHashMap<>();
        depthRanges.put("1", List.of(20, 70));
        depthRanges.put("2", List.of(40, 120));
        depthRanges.put("3", List.of(60, 170));
        depthRanges.put("5", List.of(100, 260));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", geo.available());
        out.put("bounds", List.of(-180.0, -60.0, 180.0, 84.0));
        out.put("maxZoom", GeoData.MAX_ZOOM);
        out.put("depths", GeoData.DEPTHS);
        out.put("conductivity", GeoData.CONDUCTIVITY);
        out.put("source", GeoData.SOURCE);
        out.put("attribution", ATTRIBUTION);
        out.put("layers", List.of(
                layer("ground", "°C", 0, 20, "/api/v1/geo/tiles/ground/{z}/{x}/{y}.png", null),
                layer("depth", "°C", 40, 120, "/api/v1/geo/tiles/depth-{d}/{z}/{x}/{y}.png", depthRanges),
                layer("heatflow", "mW/m²", 30, 120, "/api/v1/geo/tiles/heatflow/{z}/{x}/{y}.png", null)));
        return out;
    }

    private static Map<String, Object> layer(String id, String unit, double min, double max, String tiles,
                                             Map<String, Object> ranges) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("unit", unit);
        m.put("min", min);
        m.put("max", max);
        m.put("scale", "linear");
        m.put("colors", COLORS);
        m.put("tiles", tiles);
        if (ranges != null) {
            m.put("ranges", ranges); // per depth (km): {min, max}
        }
        return m;
    }

    @GetMapping(value = "/tiles/{layer}/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> tile(@PathVariable String layer, @PathVariable int z, @PathVariable int x,
                                       @PathVariable int y) {
        if (z < 0 || z > 14 || x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z)) {
            throw new ValidationException("tile", "No such tile");
        }
        byte[] png;
        try {
            png = geo.tile(layer, z, x, y);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("layer", "Unknown geothermal layer: " + layer);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(png == null ? EMPTY_PNG : png);
    }

    @GetMapping("/point")
    public GeoPoint point(@RequestParam double lat, @RequestParam double lon) {
        if (!(lat >= -90 && lat <= 90)) throw new ValidationException("lat", "Latitude must be between -90 and 90");
        if (!(lon >= -180 && lon <= 180)) throw new ValidationException("lon", "Longitude must be between -180 and 180");
        return geo.point(lat, lon);
    }

    private static byte[] emptyPng() {
        try {
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                    "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @ExceptionHandler(GeoData.GeoUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(GeoData.GeoUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "error", "Service Unavailable", "message", e.getMessage()));
    }
}
