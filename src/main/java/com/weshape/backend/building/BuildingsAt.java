package com.weshape.backend.building;

import java.util.List;

/**
 * The building at a place and the ones around it.
 *
 * @param building  the building the place is in, or the nearest within {@link BuildingData#PICK_M} m; null when none
 * @param nearby    other buildings within {@link BuildingData#NEARBY_M} m of it (of the place when there is none),
 *                  nearest first: candidates to pick instead, and what may shade it
 * @param release   the Overture Maps release the outlines come from
 */
public record BuildingsAt(Building building, List<Building> nearby, String release, String attribution) {

    /**
     * One building.
     *
     * @param outline     outer outline as [longitude, latitude] points, closed
     * @param distanceM   from the place (for the building) or from the building (for the others); 0 inside or touching
     * @param bearingDeg  from the place / the building's centre to this one's centre, 0 north, 90 east
     * @param heightM     height above ground, when known
     * @param floors      floors above ground, when known
     * @param roofShape   Overture's roof shape (flat, gabled, hipped ...), when known
     * @param roofDirectionDeg bearing of the roof's slope, when known
     * @param kind        Overture's class (house, apartments, garage ...), when known
     * @param walls       straight walls with the way they face (only for the building itself)
     * @param mainAxisDeg direction of the longest wall, 0–180 (only for the building itself)
     */
    public record Building(String id, List<double[]> outline, double areaM2, double perimeterM, double distanceM,
                           Double bearingDeg, Double heightM, Integer floors, String roofShape,
                           Double roofDirectionDeg, String kind, List<Footprint.Wall> walls, Double mainAxisDeg) {
    }
}
