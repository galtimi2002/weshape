package com.weshape.backend.bio;

/**
 * Biomass at one place.
 *
 * @param density      available biomass here, t/ha per year (crop + forest residues)
 * @param cropKt       crop residues within 25 km, thousand t per year
 * @param forestKt     forest residues within 25 km, thousand t per year
 * @param resourceKt   both together
 * @param energyGwh    their heating value within 25 km, GWh per year
 * @param homes        family homes that much heat would keep warm for a year
 * @param radiusKm     the collection radius (25)
 */
public record BioPoint(double lat, double lon, double density, double cropKt, double forestKt, double resourceKt,
                       double energyGwh, long homes, double radiusKm, String source) {}
