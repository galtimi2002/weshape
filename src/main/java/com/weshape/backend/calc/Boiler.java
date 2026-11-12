package com.weshape.backend.calc;

/**
 * Boilers and stoves: how much of the fuel's energy becomes heat in the home over a season (net
 * calorific value). Our own research, November 2026 (docs/research/boilers.md); the expert input
 * gives no values.
 *
 * <p>Measured values are used where they exist: the UK field trial of 67 biomass boilers [B1], an
 * Austrian field study of pellet boilers [B2] and the UK field trial of condensing gas boilers [B3].
 * The EU ecodesign minimums [B4][B5] set the floor for new appliances. Values without a field source
 * are marked [Judgement, WeShape] and must be confirmed by an expert.
 */
public final class Boiler {

    /** Heat from the fuel per kWh of fuel (net), over a season. */
    public enum Kind {
        /** Field average 78% net [B1]; Austria 65–85% against 90–94% in the lab [B2]. */
        PELLET_BOILER(new Range(0.65, 0.78, 0.85)),
        /**
         * Field average 81% net, the most even of the biomass boilers [B1]. Low end [Judgement,
         * WeShape]; high end the top of the lab range [B1].
         */
        LOG_BOILER(new Range(0.70, 0.81, 0.86)),
        /** Closed-fronted wood stove sold since 2022: at least 65% [B5]; high end [Judgement, WeShape]. */
        STOVE_MODERN(new Range(0.65, 0.70, 0.80)),
        /**
         * An older, uncertified stove: a certified one uses about a third less wood for the same heat
         * [B6], so about 65% ÷ 1.5 ≈ 43% [Derived]; range [Judgement, WeShape].
         */
        STOVE_OLD(new Range(0.35, 0.43, 0.55)),
        /** Pellet stove sold since 2022: at least 79% [B5]; high end [Judgement, WeShape]. */
        PELLET_STOVE(new Range(0.79, 0.82, 0.88)),
        /** Open fireplace: the EU minimum is only 30% [B5]; typical and low [Judgement, WeShape]. */
        OPEN_FIREPLACE(new Range(0.10, 0.20, 0.30)),
        /** Condensing gas boiler in homes: 82.9% and 83.2% on average, most 80–90% [B3]. */
        GAS_CONDENSING(new Range(0.80, 0.83, 0.90));

        public final Range efficiency;

        Kind(Range efficiency) {
            this.efficiency = efficiency;
        }
    }

    /** A new solid-fuel boiler up to 20 kW must reach at least 75% (EU, from 2020) [B4]. */
    public static final double ECODESIGN_BOILER_MIN = 0.75;
    /** Pellets carry about 5 MWh per tonne (expert input, §9) [S23]. */
    public static final double PELLETS_KWH_PER_TONNE = 5000;

    private Boiler() {
    }

    /** Fuel needed for a heat demand, kWh of fuel: heat ÷ efficiency (the best efficiency gives the least fuel). */
    public static Range fuelKwh(Range heatKwh, Kind kind) {
        Range e = kind.efficiency;
        return new Range(heatKwh.low() / e.high(), heatKwh.typical() / e.typical(), heatKwh.high() / e.low());
    }

    /** Heat from fuel already used (e.g. last winter's wood), kWh: fuel × efficiency. */
    public static Range heatKwh(double fuelKwh, Kind kind) {
        if (fuelKwh < 0) throw new IllegalArgumentException("Fuel cannot be negative");
        return kind.efficiency.times(fuelKwh);
    }

    /** Tonnes of pellets for a heat demand. */
    public static Range pelletTonnes(Range heatKwh, Kind kind) {
        return fuelKwh(heatKwh, kind).times(1 / PELLETS_KWH_PER_TONNE);
    }
}
