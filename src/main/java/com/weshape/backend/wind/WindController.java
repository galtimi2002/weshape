package com.weshape.backend.wind;

import com.weshape.backend.exception.ValidationException;
import java.util.ArrayList;
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

/** Wind map: layer list and map tiles (public), and the values for a point (signed in). */
@RestController
@RequestMapping("/api/v1/wind")
public class WindController {

    static final String ATTRIBUTION = "Wind data: <a href=\"https://globalwindatlas.info\" target=\"_blank\" rel=\"noopener\">"
            + "Global Wind Atlas</a> 4.0, DTU and World Bank Group (CC BY 4.0)";

    private final WindData wind;

    public WindController(WindData wind) {
        this.wind = wind;
    }

    @GetMapping("/layers")
    public Map<String, Object> layers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WindLayer l : WindLayer.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id);
            m.put("unit", l.unit);
            m.put("min", l.min);
            m.put("max", l.max);
            m.put("scale", l.logarithmic ? "log" : "linear");
            m.put("colors", l.colors);
            m.put("tiles", l == WindLayer.HEIGHT ? "/api/v1/wind/tiles/height-{h}/{z}/{x}/{y}.png"
                    : "/api/v1/wind/tiles/" + l.id + "/{z}/{x}/{y}.png");
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", wind.available());
        out.put("bounds", List.of(-180.0, -64.0, 180.0, 80.0));
        // Tiles exist up to this zoom; the map enlarges them beyond it.
        out.put("maxZoom", WindData.STORED_MAX_ZOOM);
        out.put("heights", WindLayer.HEIGHTS);
        out.put("source", WindData.SOURCE);
        out.put("attribution", ATTRIBUTION);
        out.put("layers", list);
        return out;
    }

    @GetMapping(value = "/tiles/{layer}/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> tile(@PathVariable String layer, @PathVariable int z, @PathVariable int x,
                                       @PathVariable int y) {
        if (z < 0 || z > 14 || x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z)) {
            throw new ValidationException("tile", "No such tile");
        }
        byte[] png;
        try {
            png = wind.tile(layer, z, x, y);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("layer", "Unknown wind layer: " + layer);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @GetMapping("/point")
    public WindPoint point(@RequestParam double lat, @RequestParam double lon) {
        if (!(lat >= -90 && lat <= 90)) throw new ValidationException("lat", "Latitude must be between -90 and 90");
        if (!(lon >= -180 && lon <= 180)) throw new ValidationException("lon", "Longitude must be between -180 and 180");
        return wind.point(lat, lon);
    }

    @ExceptionHandler(WindData.WindUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(WindData.WindUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "error", "Service Unavailable", "message", e.getMessage()));
    }
}
