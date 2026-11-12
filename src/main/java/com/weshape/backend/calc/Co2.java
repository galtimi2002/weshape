package com.weshape.backend.calc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Greenhouse gases per kWh of electricity (by country) and of fuel, in g CO₂e. Our own research
 * (docs/research/co2.md).
 *
 * <p>Electricity: lifecycle g CO₂e per kWh generated, Ember via Our World in Data [C1], in
 * {@code calc/electricity-co2.csv}. Fuels: burning, IPCC 2006 defaults for homes [C2] with the
 * IPCC AR6 warming potentials [C3], plus the supply chain (extraction, processing, transport) from the
 * UK government factors [C4]. CO₂ from wood counts as zero, as in national inventories [C2]; it is
 * given separately by {@link #biogenicCo2PerKwh}.
 */
public final class Co2 {

    /** kg per TJ → g per kWh (1 TJ = 277,777.8 kWh). */
    public static final double KG_PER_TJ_TO_G_PER_KWH = 0.0036;
    /** IPCC AR6, 100 years [C3]. */
    public static final double GWP_CH4_FOSSIL = 29.8;
    public static final double GWP_CH4_BIOGENIC = 27.0;
    public static final double GWP_N2O = 273;
    /** Supply chain outside the UK: half to double the UK value [Judgement, WeShape]. */
    public static final Range SUPPLY_CHAIN_SPREAD = new Range(0.5, 1, 2);
    /** World average, 2023–2025 [C1]: used when the country is not in the table. */
    public static final Range WORLD_ELECTRICITY = new Range(458, 458, 483);

    /** Fuels burnt at home. kg per TJ (net calorific value) [C2]; supply chain g per kWh, net [C4]. */
    public enum Fuel {
        NATURAL_GAS(new Range(54300, 56100, 58300), new Range(1.5, 5, 15), new Range(0.03, 0.1, 0.3), false, 33.47),
        LPG(new Range(61600, 63100, 65600), new Range(1.5, 5, 15), new Range(0.03, 0.1, 0.3), false, 27.36),
        /** Gas/diesel oil [C2]; gas oil [C4]. */
        HEATING_OIL(new Range(72600, 74100, 74800), new Range(3, 10, 30), new Range(0.2, 0.6, 2), false, 62.91),
        /** Other kerosene [C2]; burning oil [C4]. */
        KEROSENE(new Range(70800, 71900, 73700), new Range(3, 10, 30), new Range(0.2, 0.6, 2), false, 54.24),
        /** Other bituminous coal [C2]; coal (domestic) [C4]. */
        COAL(new Range(89500, 94600, 99700), new Range(100, 300, 900), new Range(0.5, 1.5, 5), false, 59.25),
        /** Lignite [C2]; supply chain as coal [Judgement, WeShape]. */
        LIGNITE(new Range(90900, 101000, 115000), new Range(100, 300, 900), new Range(0.5, 1.5, 5), false, 59.25),
        /** Wood/wood waste [C2]; wood logs [C4]. */
        FIREWOOD(new Range(95000, 112000, 132000), new Range(100, 300, 900), new Range(1.5, 4, 15), true, 12.77),
        /** Wood/wood waste [C2]; wood pellets [C4]. */
        PELLETS(new Range(95000, 112000, 132000), new Range(100, 300, 900), new Range(1.5, 4, 15), true, 37.44);

        public final Range co2KgPerTj;
        public final Range ch4KgPerTj;
        public final Range n2oKgPerTj;
        public final boolean biogenic;
        public final double supplyChainGPerKwh;

        Fuel(Range co2, Range ch4, Range n2o, boolean biogenic, double supplyChain) {
            this.co2KgPerTj = co2;
            this.ch4KgPerTj = ch4;
            this.n2oKgPerTj = n2o;
            this.biogenic = biogenic;
            this.supplyChainGPerKwh = supplyChain;
        }
    }

    /** Electricity for a country: g CO₂e per kWh, the data year, and whether the country was found. */
    public record Electricity(Range gPerKwh, int year, boolean known) {
    }

    private static final Map<String, Electricity> TABLE = load("calc/electricity-co2.csv");

    private Co2() {
    }

    /** g CO₂e per kWh of fuel burnt at home (net calorific value), supply chain included. */
    public static Range perKwh(Fuel fuel) {
        double gwpCh4 = fuel.biogenic ? GWP_CH4_BIOGENIC : GWP_CH4_FOSSIL;
        Range co2 = fuel.biogenic ? Range.exactly(0) : fuel.co2KgPerTj;
        Range kgPerTj = co2.plus(fuel.ch4KgPerTj.times(gwpCh4)).plus(fuel.n2oKgPerTj.times(GWP_N2O));
        return kgPerTj.times(KG_PER_TJ_TO_G_PER_KWH).plus(SUPPLY_CHAIN_SPREAD.times(fuel.supplyChainGPerKwh));
    }

    /** CO₂ from burning wood, g per kWh: not counted in {@link #perKwh}, shown for information. */
    public static Range biogenicCo2PerKwh(Fuel fuel) {
        return fuel.biogenic ? fuel.co2KgPerTj.times(KG_PER_TJ_TO_G_PER_KWH) : Range.exactly(0);
    }

    /** Electricity in a country (ISO 3166 two letters); the world average when it is not in the table. */
    public static Electricity electricity(String country) {
        Electricity e = country == null ? null : TABLE.get(country.toUpperCase(Locale.ROOT));
        return e != null ? e : new Electricity(WORLD_ELECTRICITY, 2025, false);
    }

    /** Number of countries in the table. */
    public static int countries() {
        return TABLE.size();
    }

    /** kg CO₂e for an amount of energy. */
    public static Range kg(Range kwh, Range gPerKwh) {
        return kwh.times(gPerKwh).times(0.001);
    }

    private static Map<String, Electricity> load(String resource) {
        Map<String, Electricity> map = new HashMap<>();
        try (InputStream in = Co2.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing " + resource);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("country") || line.isBlank()) continue;
                String[] p = line.split(",");
                map.put(p[0], new Electricity(
                        new Range(Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4])),
                        Integer.parseInt(p[1]), true));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(map);
    }
}
