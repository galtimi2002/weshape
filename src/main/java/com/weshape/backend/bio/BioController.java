package com.weshape.backend.bio;

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

/** Biomass map: layer list and map tiles (public), and the values for a place (signed in). */
@RestController
@RequestMapping("/api/v1/bio")
public class BioController {

    static final String ATTRIBUTION = "Biomass data: <a href=\"https://doi.org/10.7910/DVN/SWPENT\" target=\"_blank\" "
            + "rel=\"noopener\">MapSPAM 2020</a> (IFPRI, CC BY 4.0), <a href=\"https://climate.esa.int/en/projects/biomass/\" "
            + "target=\"_blank\" rel=\"noopener\">ESA CCI Biomass</a>";
    static final List<String> COLORS = List.of("#EAF4E0", "#A9D48C", "#5BAA5E", "#2A6B3B");
    private static final byte[] EMPTY_PNG = emptyPng();

    private final BioData bio;

    public BioController(BioData bio) {
        this.bio = bio;
    }

    @GetMapping("/layers")
    public Map<String, Object> layers() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", bio.available());
        out.put("bounds", List.of(-180.0, -60.0, 180.0, 84.0));
        out.put("maxZoom", BioData.MAX_ZOOM);
        out.put("radiusKm", BioData.RADIUS_KM);
        out.put("homeMwh", BioData.HOME_MWH);
        out.put("source", BioData.SOURCE);
        out.put("attribution", ATTRIBUTION);
        out.put("layers", List.of(
                layer("density", "t/ha", 0, 3),
                layer("resource", "kt", 0, 500),
                layer("energy", "GWh", 0, 2000)));
        return out;
    }

    private static Map<String, Object> layer(String id, String unit, double min, double max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("unit", unit);
        m.put("min", min);
        m.put("max", max);
        m.put("scale", "linear");
        m.put("colors", COLORS);
        m.put("tiles", "/api/v1/bio/tiles/" + id + "/{z}/{x}/{y}.png");
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
            png = bio.tile(layer, z, x, y);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("layer", "Unknown biomass layer: " + layer);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(png == null ? EMPTY_PNG : png);
    }

    @GetMapping("/point")
    public BioPoint point(@RequestParam double lat, @RequestParam double lon) {
        if (!(lat >= -90 && lat <= 90)) throw new ValidationException("lat", "Latitude must be between -90 and 90");
        if (!(lon >= -180 && lon <= 180)) throw new ValidationException("lon", "Longitude must be between -180 and 180");
        return bio.point(lat, lon);
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

    @ExceptionHandler(BioData.BioUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(BioData.BioUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "error", "Service Unavailable", "message", e.getMessage()));
    }
}
