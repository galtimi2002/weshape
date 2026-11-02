package com.weshape.backend.hydro;

import com.weshape.backend.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** River map: layer list and vector tiles (public), and the nearest reach to a place (signed in). */
@RestController
@RequestMapping("/api/v1/hydro")
public class HydroController {

    static final String ATTRIBUTION = "River data: <a href=\"https://www.hydrosheds.org/hydroatlas\" target=\"_blank\" "
            + "rel=\"noopener\">RiverATLAS</a> v1.0, Linke et al. 2019 (CC BY 4.0)";
    static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");
    private static final List<String> COLORS = List.of("#9FD7E3", "#52B1C8", "#1F7FA0", "#0D4864");

    private final HydroData hydro;

    public HydroController(HydroData hydro) {
        this.hydro = hydro;
    }

    @GetMapping("/layers")
    public Map<String, Object> layers() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", hydro.available());
        out.put("bounds", List.of(-180.0, -60.0, 180.0, 84.0));
        out.put("maxZoom", HydroData.MAX_ZOOM);
        out.put("tiles", "/api/v1/hydro/tiles/{z}/{x}/{y}.pbf");
        out.put("sourceLayer", HydroData.LAYER);
        // Line width follows the river's mean flow.
        out.put("widthProperty", "qa");
        out.put("ecologicalShare", HydroData.ECOLOGICAL_SHARE);
        out.put("efficiency", HydroData.EFFICIENCY);
        out.put("source", HydroData.SOURCE);
        out.put("attribution", ATTRIBUTION);
        out.put("layers", List.of(
                layer("potential", "p", "kW", 10, 10_000),
                layer("flow", "q", "m³/s", 0.1, 5_000),
                layer("head", "h", "m", 1, 100)));
        return out;
    }

    private static Map<String, Object> layer(String id, String property, String unit, double min, double max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("property", property);
        m.put("unit", unit);
        m.put("min", min);
        m.put("max", max);
        m.put("scale", "log");
        m.put("colors", COLORS);
        return m;
    }

    @GetMapping("/tiles/{z}/{x}/{y}.pbf")
    public ResponseEntity<byte[]> tile(@PathVariable int z, @PathVariable int x, @PathVariable int y) {
        if (z < 0 || z > 16 || x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z)) {
            throw new ValidationException("tile", "No such tile");
        }
        byte[] data = hydro.tile(z, x, y);
        CacheControl cache = CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic();
        if (data.length == 0) {
            // No rivers here: the map draws nothing.
            return ResponseEntity.noContent().cacheControl(cache).build();
        }
        var response = ResponseEntity.ok().cacheControl(cache).contentType(PROTOBUF);
        if (data.length > 1 && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b) {
            response.header(HttpHeaders.CONTENT_ENCODING, "gzip");
        }
        return response.body(data);
    }

    @GetMapping("/point")
    public HydroPoint point(@RequestParam double lat, @RequestParam double lon,
                            @RequestParam(required = false) Double zoom) {
        if (!(lat >= -90 && lat <= 90)) throw new ValidationException("lat", "Latitude must be between -90 and 90");
        if (!(lon >= -180 && lon <= 180)) throw new ValidationException("lon", "Longitude must be between -180 and 180");
        if (zoom != null && !(zoom >= 0 && zoom <= 24)) throw new ValidationException("zoom", "Zoom must be between 0 and 24");
        return hydro.point(lat, lon, zoom);
    }

    @ExceptionHandler(HydroData.HydroUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(HydroData.HydroUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "error", "Service Unavailable", "message", e.getMessage()));
    }
}
