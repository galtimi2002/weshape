package com.weshape.backend.calc;

import java.util.Map;

/**
 * Biogas from the person's animals and food waste. The app asks only the kind of animal and how many
 * (decided with Timi, docs/decisions/2026-11-19-question-changes.md); the manure is worked out here.
 *
 * <p>Manure that can be collected, kg a day per animal, and biogas per kg of fresh manure, m³: the low
 * ends are those of small household digesters [S22] (20 kg a day ≈ 3–5 stabled cattle, 8–12 pigs or
 * 16–20 sheep or goats), the high ends those of animals housed all year [Reference, manure tables]
 * (a dairy cow up to about 50 kg); the value used between them is [Judgement, WeShape], for animals
 * kept part of the year outside. Biogas of 55–65% methane holds 5.5–6.5 kWh per m³ [Reference].
 */
public final class Biogas {

    /** The app's answer "which animals". */
    public enum Animal {
        CATTLE(new Range(5, 20, 50), new Range(0.02, 0.03, 0.04)),
        PIG(new Range(2, 3, 5), new Range(0.04, 0.05, 0.06)),
        /** Per bird: hens, ducks, geese. */
        POULTRY(new Range(0.08, 0.12, 0.15), new Range(0.065, 0.08, 0.116)),
        HORSE(new Range(10, 15, 25), new Range(0.02, 0.03, 0.04)),
        SHEEP_GOAT(new Range(1, 1.5, 2.5), new Range(0.04, 0.05, 0.06));

        /** Manure that can be collected, kg a day per animal. */
        public final Range manureKgPerDay;
        /** Biogas per kg of fresh manure, m³. */
        public final Range m3PerKg;

        Animal(Range manureKgPerDay, Range m3PerKg) {
            this.manureKgPerDay = manureKgPerDay;
            this.m3PerKg = m3PerKg;
        }
    }

    /** The unit the person chose for food waste. */
    public enum MassUnit { KG, LB }

    /** Kilograms in a pound. */
    public static final double KG_PER_LB = 0.45359237;
    /** Biogas per kg of fresh food waste, m³: 80–150 m³ a tonne [Reference, food waste digestion]. */
    public static final Range FOOD_WASTE_M3_PER_KG = new Range(0.08, 0.11, 0.15);
    /** Energy in a m³ of biogas, kWh [Reference]. */
    public static final Range KWH_PER_M3 = new Range(5.5, 6, 6.5);

    private Biogas() {
    }

    /** Manure that can be collected each day, kg. */
    public static Range manureKgPerDay(Map<Animal, Integer> animals) {
        Range total = Range.exactly(0);
        for (Map.Entry<Animal, Integer> e : animals.entrySet()) {
            total = total.plus(e.getKey().manureKgPerDay.times(count(e.getValue())));
        }
        return total;
    }

    /** Biogas a day from the animals' manure, m³. */
    public static Range manureM3PerDay(Map<Animal, Integer> animals) {
        Range total = Range.exactly(0);
        for (Map.Entry<Animal, Integer> e : animals.entrySet()) {
            Animal a = e.getKey();
            total = total.plus(a.manureKgPerDay.times(a.m3PerKg).times(count(e.getValue())));
        }
        return total;
    }

    /** Kilograms of food waste for an amount in the chosen unit. */
    public static double kg(double amount, MassUnit unit) {
        if (!(amount >= 0)) throw new IllegalArgumentException("The amount of food waste cannot be negative");
        return unit == MassUnit.KG ? amount : amount * KG_PER_LB;
    }

    /** Biogas a day from food waste given per week, m³. */
    public static Range foodWasteM3PerDay(double amountPerWeek, MassUnit unit) {
        return FOOD_WASTE_M3_PER_KG.times(kg(amountPerWeek, unit) / 7);
    }

    /** Energy in the biogas of a year, kWh. */
    public static Range kwhPerYear(Range m3PerDay) {
        return m3PerDay.times(365).times(KWH_PER_M3);
    }

    /** Whether a digester is worth it: enough manure each day [S22]; food waste alone is not enough. */
    public static boolean worthIt(Map<Animal, Integer> animals) {
        return Eligibility.biogasFits(manureKgPerDay(animals).typical());
    }

    private static int count(Integer n) {
        if (n == null || n < 0) throw new IllegalArgumentException("The number of animals cannot be negative");
        return n;
    }
}
