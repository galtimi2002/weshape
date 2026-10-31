package com.weshape.backend.solar;

import com.weshape.backend.exception.NotFoundException;
import com.weshape.backend.exception.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/solar")
public class SolarController {

    private final SolarResource resource;
    private final SolarLayerRenderer renderer;

    public SolarController(SolarResource resource, SolarLayerRenderer renderer) {
        this.resource = resource;
        this.renderer = renderer;
    }

    @Operation(summary = "Irradiation, PV output and optimum tilt for one place, with monthly figures")
    @GetMapping("/point")
    public Map<String, Object> point(@RequestParam double lat, @RequestParam double lon) {
        if (!(lat >= -90 && lat <= 90)) throw new ValidationException("lat", "Latitude must be between -90 and 90");
        if (!(lon >= -180 && lon <= 180)) throw new ValidationException("lon", "Longitude must be between -180 and 180");
        SolarPoint p = resource.at(lat, lon);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lat", p.lat());
        body.put("lon", p.lon());
        body.put("gti", round(p.gti()));
        body.put("pvout", round(p.pvout()));
        body.put("opta", Math.round(p.opta()));
        body.put("gtiMonthly", Arrays.stream(p.gtiMonthly()).map(SolarController::round).toArray());
        body.put("pvoutMonthly", Arrays.stream(p.pvoutMonthly()).map(SolarController::round).toArray());
        body.put("source", p.source());
        body.put("example", p.example());
        return body;
    }

    @Operation(summary = "The map layers: where they cover, their units and colour scales")
    @SecurityRequirements
    @GetMapping("/layers")
    public ResponseEntity<Map<String, Object>> layers() {
        List<Map<String, Object>> layers = Arrays.stream(SolarLayer.values()).map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id());
            m.put("unit", l.unit());
            m.put("min", l.min());
            m.put("max", l.max());
            m.put("colors", l.colors());
            m.put("image", "/api/v1/solar/layers/" + l.id() + ".png");
            return m;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bounds", SolarLayerRenderer.BOUNDS);
        body.put("layers", layers);
        body.put("source", resource.source());
        body.put("example", resource.isExample());
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(body);
    }

    @Operation(summary = "One layer as a PNG over the layer bounds, rows in Web Mercator")
    @SecurityRequirements
    @GetMapping(value = "/layers/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> layerImage(@PathVariable String id) {
        SolarLayer layer = SolarLayer.fromId(id).orElseThrow(() -> new NotFoundException("No such solar layer: " + id));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(renderer.png(layer));
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
