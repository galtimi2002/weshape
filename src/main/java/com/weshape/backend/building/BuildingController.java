package com.weshape.backend.building;

import com.weshape.backend.exception.ValidationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Building outlines for the home assessment: which building is at a place (signed in). */
@RestController
@RequestMapping("/api/v1/buildings")
public class BuildingController {

    private final BuildingData buildings;

    public BuildingController(BuildingData buildings) {
        this.buildings = buildings;
    }

    /** The building at the place, or the nearest one close by, and its neighbours. */
    @GetMapping("/at")
    public BuildingsAt at(@RequestParam double lat, @RequestParam double lon) {
        if (!(lat >= -90 && lat <= 90)) throw new ValidationException("lat", "Latitude must be between -90 and 90");
        if (!(lon >= -180 && lon <= 180)) throw new ValidationException("lon", "Longitude must be between -180 and 180");
        return buildings.at(lat, lon);
    }

    @ExceptionHandler(BuildingData.BuildingsUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(BuildingData.BuildingsUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "error", "Service Unavailable", "message", e.getMessage()));
    }
}
