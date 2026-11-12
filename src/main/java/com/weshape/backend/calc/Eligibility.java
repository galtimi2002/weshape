package com.weshape.backend.calc;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which options are shown at all, before anything is scored. Expert input v2, sections 6, 7 and 9.
 */
public final class Eligibility {

    public enum Option {
        ROOFTOP_PV, PLUG_IN_PV, BATTERY, SOLAR_WATER_HEATER, AIR_HEAT_PUMP, GROUND_HEAT_PUMP,
        SMALL_WIND, MICRO_HYDRO, LOG_BOILER, PELLET_BOILER, BIOGAS, CLEAN_COOKING
    }

    /** "No upkeep" excludes wind, hydro, biomass and biogas [expert input, section 7]. */
    static final Set<Option> NEEDS_UPKEEP = EnumSet.of(Option.SMALL_WIND, Option.MICRO_HYDRO,
            Option.LOG_BOILER, Option.PELLET_BOILER, Option.BIOGAS);

    /**
     * What a tenant can do without the landlord's works [expert input, section 6]: plug-in panels where
     * legal, efficiency steps (heat-pump or solar hot water and induction or pressure cookers need the
     * landlord in many homes, so only cooking is kept here) and portable kits.
     */
    static final Set<Option> FOR_TENANTS = EnumSet.of(Option.PLUG_IN_PV, Option.CLEAN_COOKING, Option.BATTERY);

    /** A sensible minimum heat demand for a wood or pellet boiler, kWh/yr [Judgement]. */
    public static final double MIN_BOILER_HEAT_KWH = 15000;
    /** Manure that can be collected each day for biogas, kg [S22]: 3–5 stabled cattle, 8–12 pigs, 16–20 sheep or goats. */
    public static final double MIN_MANURE_KG_PER_DAY = 20;

    private Eligibility() {
    }

    /** The options left after the household's own limits. */
    public static Set<Option> filter(Set<Option> candidates, boolean noUpkeep, boolean tenant) {
        Set<Option> out = EnumSet.noneOf(Option.class);
        out.addAll(candidates);
        if (noUpkeep) out.removeAll(NEEDS_UPKEEP);
        if (tenant) out.retainAll(FOR_TENANTS);
        return out;
    }

    /**
     * A wood or pellet boiler: rural or edge-of-village, heat demand above about 15 MWh a year, and not
     * in a dense area with air-quality problems [S23; Judgement].
     */
    public static boolean boilerFits(double heatDemandKwh, boolean rural) {
        return rural && heatDemandKwh > MIN_BOILER_HEAT_KWH;
    }

    /** Biogas from manure: at least 20–40 kg a day; food waste alone is not enough [S22]. */
    public static boolean biogasFits(double manureKgPerDay) {
        return manureKgPerDay >= MIN_MANURE_KG_PER_DAY;
    }
}
