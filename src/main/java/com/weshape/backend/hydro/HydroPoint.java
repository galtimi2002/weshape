package com.weshape.backend.hydro;

/**
 * The river reach nearest to a place, with its values.
 *
 * @param distanceKm        from the place to the river line
 * @param potentialKw       9.81 x available flow x head x turbine efficiency
 * @param availableFlow     mean flow minus the share left in the river for nature (m³/s)
 * @param meanFlow          long-term mean flow (m³/s)
 * @param driestMonthFlow   mean flow of the driest month (m³/s)
 * @param wettestMonthFlow  mean flow of the wettest month (m³/s)
 * @param headM             how far the water drops along the reach (m)
 * @param reachKm           length of the reach
 * @param ecologicalShare   share of the mean flow left in the river (0.1 = 10 %)
 * @param efficiency        turbine and generator efficiency used for the potential
 */
public record HydroPoint(double lat, double lon, double distanceKm, double potentialKw, double availableFlow,
                         double meanFlow, double driestMonthFlow, double wettestMonthFlow, double headM,
                         double reachKm, double ecologicalShare, double efficiency, String source) {}
