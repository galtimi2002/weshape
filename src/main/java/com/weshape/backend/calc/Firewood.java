package com.weshape.backend.calc;

/**
 * Energy in firewood, per stacked cubic metre (1 m³ of stacked split logs, about 0.7 m³ of solid
 * wood: "erdei köbméter", "metru ster", "Raummeter"). Our own research (docs/research/firewood.md).
 *
 * <p>Bavarian State Institute of Forestry [W1], kWh per stacked m³: beech 1,907 / 1,885 / 1,687, birch
 * 1,798 / 1,777 / 1,591, pine 1,533 / 1,516 / 1,364, spruce 1,348 / 1,333 / 1,199 at 15 / 20 / 50%
 * water content. Per volume, fresh wood gives only about 11% less than dry wood; the species matters
 * much more (beech against spruce about 40%). So the app asks the kind of wood, not the moisture.
 */
public final class Firewood {

    /** The app's answer "what wood do you burn". */
    public enum Kind {
        /** Beech, oak, acacia: birch fresh to beech dry [W1]; 1,800 used. */
        HARDWOOD(new Range(1591, 1800, 1907)),
        /** Spruce, pine, poplar: spruce fresh to pine dry [W1]; 1,400 used. */
        SOFTWOOD(new Range(1199, 1400, 1533)),
        /** A mix of both [Judgement, WeShape]. */
        MIXED(new Range(1400, 1600, 1800)),
        /** Not known: the whole span of the table [W1]; 1,600 used. */
        UNKNOWN(new Range(1199, 1600, 1907));

        public final Range kwhPerM3;

        Kind(Range kwhPerM3) {
            this.kwhPerM3 = kwhPerM3;
        }
    }

    private Firewood() {
    }

    /** Energy in the wood burnt in a season, kWh. */
    public static Range energyKwh(double stackedM3, Kind kind) {
        if (stackedM3 < 0) throw new IllegalArgumentException("Wood cannot be negative");
        return kind.kwhPerM3.times(stackedM3);
    }

    /** Heat the home really got from it, kWh: energy × the efficiency of the stove or boiler. */
    public static Range heatKwh(double stackedM3, Kind kind, Boiler.Kind burner) {
        return energyKwh(stackedM3, kind).times(burner.efficiency);
    }

    /** Stacked m³ of wood for a heat demand. */
    public static Range m3For(Range heatKwh, Kind kind, Boiler.Kind burner) {
        Range fuel = Boiler.fuelKwh(heatKwh, burner);
        Range e = kind.kwhPerM3;
        return new Range(fuel.low() / e.high(), fuel.typical() / e.typical(), fuel.high() / e.low());
    }
}
