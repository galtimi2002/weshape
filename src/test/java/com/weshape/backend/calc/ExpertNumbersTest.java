package com.weshape.backend.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every number here is checked against the expert input (docs/expert/2026-11-expert-input-answers-v2.pdf):
 * the worked examples it gives, and the rules in its "Suggested rule for the app" boxes. A test
 * names the section it comes from.
 */
@DisplayName("Calculations against the expert input")
class ExpertNumbersTest {

    @Nested
    @DisplayName("§9 Hydro")
    class HydroTest {
        @Test
        @DisplayName("5 m and 20 L/s give about 500 W and roughly 4,400 kWh a year")
        void workedExample() {
            assertThat(Hydro.powerW(5, 20)).isEqualTo(500.0);
            assertThat(Hydro.annualKwh(500)).isCloseTo(4400, within(50.0));
            assertThat(Hydro.assess(5, 20, 100, 100).shown()).isTrue();
        }

        @Test
        @DisplayName("not shown below 3 m of head, 10 L/s, 300 W, or beyond 500 m / 300 m of pipe")
        void limits() {
            assertThat(Hydro.assess(2.9, 50, 100, 100).reasons()).containsExactly(Hydro.Reason.HEAD_TOO_LOW);
            assertThat(Hydro.assess(10, 9.9, 100, 100).reasons()).containsExactly(Hydro.Reason.FLOW_TOO_LOW);
            // 3 m × 10 L/s = 150 W: both limits met, still too little power.
            assertThat(Hydro.assess(3, 10, 100, 100).reasons()).containsExactly(Hydro.Reason.POWER_TOO_LOW);
            assertThat(Hydro.assess(6, 10, 100, 100).shown()).isTrue(); // exactly 300 W
            // 299 W: head and flow are fine, the power is just under the limit.
            assertThat(Hydro.assess(5.98, 10, 100, 100).reasons()).containsExactly(Hydro.Reason.POWER_TOO_LOW);
            assertThat(Hydro.assess(5, 20, 501, 100).reasons()).containsExactly(Hydro.Reason.STREAM_TOO_FAR);
            assertThat(Hydro.assess(5, 20, 100, 301).reasons()).containsExactly(Hydro.Reason.PIPE_TOO_LONG);
            assertThatThrownBy(() -> Hydro.powerW(-1, 5)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("§9 Wind")
    class WindTest {
        @Test
        @DisplayName("raising the hub from 20 to 30 m in the open adds about 6%")
        void shear() {
            assertThat(Wind.atHeight(5, 20, 30, Wind.SHEAR_OPEN) / 5).isCloseTo(1.06, within(0.005));
        }

        @Test
        @DisplayName("possible from 4.5 m/s, good from 5.5 m/s at hub height; 3 m/s is too low")
        void verdict() {
            assertThat(Wind.verdict(3.0)).isEqualTo(Wind.Verdict.TOO_LOW);
            assertThat(Wind.verdict(4.49)).isEqualTo(Wind.Verdict.TOO_LOW);
            assertThat(Wind.verdict(4.5)).isEqualTo(Wind.Verdict.POSSIBLE);
            assertThat(Wind.verdict(5.5)).isEqualTo(Wind.Verdict.GOOD);
        }

        @Test
        @DisplayName("terrain: a suburb keeps 65% of the speed and 28% of the power; a city almost none")
        void terrain() {
            assertThat(Wind.hubSpeed(6, 20, 20, Wind.Terrain.SUBURB)).isCloseTo(3.9, within(1e-9));
            assertThat(Wind.Terrain.FOREST.powerFactor).isEqualTo(0.37);
            assertThat(Wind.Terrain.CITY.powerFactor).isZero();
            // Power goes with the cube of speed: the table's two columns agree within rounding.
            for (Wind.Terrain t : Wind.Terrain.values()) {
                if (t != Wind.Terrain.CITY) {
                    assertThat(Math.pow(t.speedFactor, 3)).isCloseTo(t.powerFactor, within(0.02));
                }
            }
        }

        @Test
        @DisplayName("17% capacity factor; about 1 acre of land and 1.5× tower height to the boundary")
        void energyAndSpace() {
            assertThat(Wind.annualKwh(1).typical()).isCloseTo(1489.2, within(0.1));
            assertThat(Wind.annualKwh(1).low()).isCloseTo(175.2, within(0.1));
            assertThat(Wind.fits(4000, 20, 30)).isTrue();
            assertThat(Wind.fits(3999, 20, 30)).isFalse();
            assertThat(Wind.fits(5000, 20, 29.9)).isFalse();
        }
    }

    @Nested
    @DisplayName("§5 Solar panels")
    class SolarTest {
        @Test
        @DisplayName("a 60 m² roof plane gives about 42 m² usable and about 9 kWp")
        void workedExample() {
            assertThat(SolarPv.usableRoofM2(60).typical()).isCloseTo(42, within(1e-9));
            assertThat(SolarPv.kWpOnRoof(60).typical()).isCloseTo(9.1, within(0.05));
            // 40–50 m² usable gives 8–10 kWp in the expert's words; the range covers it.
            assertThat(SolarPv.kWpOnRoof(60).low()).isLessThanOrEqualTo(8.2);
            assertThat(SolarPv.kWpOnRoof(60).high()).isGreaterThanOrEqualTo(10);
        }

        @Test
        @DisplayName("5 kWp needs about 20–25 m² of roof")
        void areaPerKwp() {
            assertThat(SolarPv.ROOF_M2_PER_KWP.times(5).low()).isCloseTo(21.5, within(0.01));
            assertThat(SolarPv.ROOF_M2_PER_KWP.times(5).high()).isCloseTo(24, within(0.01));
        }

        @Test
        @DisplayName("orientation: E/W 75–85%, north 40–55%; the southern hemisphere swaps; near the equator it does not count")
        void orientation() {
            assertThat(SolarPv.orientationFactor(SolarPv.Facing.SOUTH, 46.9).typical()).isEqualTo(1.0);
            assertThat(SolarPv.orientationFactor(SolarPv.Facing.EAST_OR_WEST, 46.9)).isEqualTo(Range.of(0.75, 0.85));
            assertThat(SolarPv.orientationFactor(SolarPv.Facing.NORTH, 46.9)).isEqualTo(Range.of(0.40, 0.55));
            // Buenos Aires: a north roof is the best one, a south roof the worst.
            assertThat(SolarPv.orientationFactor(SolarPv.Facing.NORTH, -34.6).typical()).isEqualTo(1.0);
            assertThat(SolarPv.orientationFactor(SolarPv.Facing.SOUTH, -34.6)).isEqualTo(Range.of(0.40, 0.55));
            // Nairobi, Bogotá: within 15° of the equator.
            assertThat(SolarPv.orientationFactor(SolarPv.Facing.NORTH, -1.3).typical()).isEqualTo(1.0);
            assertThat(SolarPv.orientationFactor(SolarPv.Facing.EAST_OR_WEST, 4.7).typical()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("yearly energy: kWp × PVGIS yield × orientation × dust; heat is not counted twice")
        void annual() {
            Range r = SolarPv.annualKwh(Range.exactly(5), 1190, SolarPv.Facing.SOUTH, 46.9, SolarPv.Climate.TEMPERATE);
            assertThat(r.typical()).isCloseTo(5950, within(1e-6));
            Range dusty = SolarPv.annualKwh(Range.exactly(5), 2000, SolarPv.Facing.SOUTH, 24.0, SolarPv.Climate.HOT_DUSTY);
            assertThat(dusty.typical()).isCloseTo(9600, within(1e-6));
            assertThat(SolarPv.climateFactor(SolarPv.Climate.HOT).typical()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("§4 Battery")
    class BatteryTest {
        @Test
        @DisplayName("thresholds: under 60 min none, 60–300 optional, over 300 or monthly backup, over 20 h a month hybrid")
        void thresholds() {
            assertThat(Battery.advise(12, false, false)).isEqualTo(Battery.Advice.NONE); // Germany
            // The edges: 59 min none, 60 optional, 300 still optional, 20 h a month still a backup battery.
            assertThat(Battery.advise(59, false, false)).isEqualTo(Battery.Advice.NONE);
            assertThat(Battery.advise(60, false, false)).isEqualTo(Battery.Advice.OPTIONAL);
            assertThat(Battery.advise(300, false, false)).isEqualTo(Battery.Advice.OPTIONAL);
            assertThat(Battery.advise(20 * 60 * 12, false, false)).isEqualTo(Battery.Advice.BACKUP);
            assertThat(Battery.advise(20 * 60 * 12 + 1, false, false)).isEqualTo(Battery.Advice.HYBRID_MAIN);
            assertThat(Battery.advise(229, false, false)).isEqualTo(Battery.Advice.OPTIONAL); // Romania 2024
            assertThat(Battery.advise(301, false, false)).isEqualTo(Battery.Advice.BACKUP);
            assertThat(Battery.advise(30, true, false)).isEqualTo(Battery.Advice.BACKUP);
            assertThat(Battery.advise(30, false, true)).isEqualTo(Battery.Advice.BACKUP);
            assertThat(Battery.advise(741 * 60, true, false)).isEqualTo(Battery.Advice.HYBRID_MAIN); // Sub-Saharan average
            assertThat(Battery.size(Battery.Advice.HYBRID_MAIN, true)).isEqualTo(Range.of(15, 25));
        }

        @Test
        @DisplayName("size = critical kWh/day × days ÷ (0.9 × 0.9)")
        void size() {
            assertThat(Battery.sizeKwh(10, 3)).isCloseTo(37.04, within(0.01));
            assertThat(Battery.sizeKwh(8.1, 1)).isCloseTo(10, within(1e-9));
        }
    }

    @Nested
    @DisplayName("§2 Hot water and cooking")
    class HouseholdTest {
        @Test
        @DisplayName("800 kWh per person, plus 400 (good tank) or 1,200 (old tank); warmer inlet −10%")
        void hotWater() {
            assertThat(Household.hotWaterKwh(4, false, false).typical()).isEqualTo(3600);
            assertThat(Household.hotWaterKwh(4, true, false).typical()).isEqualTo(4400);
            assertThat(Household.hotWaterKwh(4, false, true).typical()).isCloseTo(3280, within(1e-9));
            assertThatThrownBy(() -> Household.hotWaterKwh(0, false, false)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("cooking: Europe 150 electric / 200 gas, USA 400, Africa and Latin America 300–350 per person")
        void cooking() {
            assertThat(Household.cookingKwhPerPerson(Household.Region.EUROPE, Household.CookingFuel.ELECTRIC).typical()).isEqualTo(150);
            assertThat(Household.cookingKwhPerPerson(Household.Region.EUROPE, Household.CookingFuel.GAS).typical()).isEqualTo(200);
            assertThat(Household.cookingKwhPerPerson(Household.Region.USA, Household.CookingFuel.GAS).typical()).isEqualTo(400);
            assertThat(Household.cookingKwhPerPerson(Household.Region.AFRICA_LATAM, Household.CookingFuel.ELECTRIC))
                    .isEqualTo(Range.of(300, 350));
        }
    }

    @Nested
    @DisplayName("§3 Very high electricity use")
    class UsageTest {
        @Test
        @DisplayName("the expert's table: Romania check 300 / very high 450; USA 1,730 / 2,590; Brazil 300 / 450")
        void table() {
            assertThat(UsageCheck.check("RO", 299, false, false, 0)).isEqualTo(UsageCheck.Level.OK);
            assertThat(UsageCheck.check("RO", 300, false, false, 0)).isEqualTo(UsageCheck.Level.CHECK);
            assertThat(UsageCheck.check("RO", 450, false, false, 0)).isEqualTo(UsageCheck.Level.VERY_HIGH);
            assertThat(UsageCheck.check("US", 1728, false, false, 0)).isEqualTo(UsageCheck.Level.CHECK);
            assertThat(UsageCheck.check("US", 2592, false, false, 0)).isEqualTo(UsageCheck.Level.VERY_HIGH);
            assertThat(UsageCheck.check("BR", 290, false, false, 0)).isEqualTo(UsageCheck.Level.CHECK);
            assertThat(UsageCheck.check("BR", 435, false, false, 0)).isEqualTo(UsageCheck.Level.VERY_HIGH);
            assertThat(UsageCheck.check("de", 600, false, false, 0)).isEqualTo(UsageCheck.Level.CHECK);
        }

        @Test
        @DisplayName("a heat pump (+400), an electric car (+250) and air conditioning raise the limits")
        void extras() {
            // Romania with a heat pump: check from 2 × 150 + 400 = 700, very high from 3 × 150 + 400 = 850.
            assertThat(UsageCheck.check("RO", 450, true, false, 0)).isEqualTo(UsageCheck.Level.OK);
            assertThat(UsageCheck.check("RO", 700, true, false, 0)).isEqualTo(UsageCheck.Level.CHECK);
            assertThat(UsageCheck.check("RO", 850, true, false, 0)).isEqualTo(UsageCheck.Level.VERY_HIGH);
            // And an electric car: 950 and 1,100.
            assertThat(UsageCheck.check("RO", 949, true, true, 0)).isEqualTo(UsageCheck.Level.OK);
            assertThat(UsageCheck.check("RO", 1100, true, true, 0)).isEqualTo(UsageCheck.Level.VERY_HIGH);
            // India with one air conditioner: check from 2 × 84 + 350 = 518.
            assertThat(UsageCheck.check("IN", 518, false, false, 1)).isEqualTo(UsageCheck.Level.CHECK);
            assertThat(UsageCheck.check("IN", 517, false, false, 1)).isEqualTo(UsageCheck.Level.OK);
        }

        @Test
        @DisplayName("a country without an average gets no warning instead of a wrong one")
        void unknown() {
            assertThat(UsageCheck.check("KE", 5000, false, false, 0)).isEqualTo(UsageCheck.Level.UNKNOWN_COUNTRY);
            assertThat(UsageCheck.check(null, 5000, false, false, 0)).isEqualTo(UsageCheck.Level.UNKNOWN_COUNTRY);
        }
    }

    @Nested
    @DisplayName("§1 Heating")
    class HeatTest {
        @Test
        @DisplayName("classes: before 1977 150–250, 1977–2002 90–145, after 2002 30–90 kWh/m²")
        void classes() {
            assertThat(HeatDemand.byEra(HeatDemand.Era.BEFORE_1960)).isEqualTo(new Range(150, 200, 250));
            assertThat(HeatDemand.byEra(HeatDemand.Era.FROM_2010)).isEqualTo(new Range(30, 60, 90));
            // Every era's range stays within the expert's classes.
            for (HeatDemand.Era e : HeatDemand.Era.values()) {
                assertThat(HeatDemand.byEra(e).low()).isGreaterThanOrEqualTo(30);
                assertThat(HeatDemand.byEra(e).high()).isLessThanOrEqualTo(250);
            }
        }

        @Test
        @DisplayName("single to modern double glazing cuts heating by about 20–30%")
        void windows() {
            double cut = 1 - HeatDemand.windowsFactor(HeatDemand.Windows.DOUBLE_NEW) / HeatDemand.windowsFactor(HeatDemand.Windows.SINGLE);
            assertThat(cut).isBetween(0.20, 0.30);
            assertThat(cut).isCloseTo(0.325 * 0.75, within(1e-9));
            assertThat(HeatDemand.windowsFactor(HeatDemand.Windows.DOUBLE_OLD)).isEqualTo(1.0);
            assertThat(HeatDemand.windowsFactor(HeatDemand.Windows.TRIPLE)).isLessThan(HeatDemand.windowsFactor(HeatDemand.Windows.DOUBLE_NEW));
        }

        @Test
        @DisplayName("renovation: 211 → 97 (−54%) and 52 (−75%); real savings are 0.75 of calculated ones")
        void renovation() {
            assertThat(1 - 97.0 / 211).isCloseTo(HeatDemand.STANDARD_PACKAGE_CUT, within(0.005));
            assertThat(1 - 52.0 / 211).isCloseTo(HeatDemand.ADVANCED_PACKAGE_CUT, within(0.005));
            assertThat(HeatDemand.realCut(0.33)).isCloseTo(0.25, within(0.003));
        }

        @Test
        @DisplayName("mild zone about 0.4 of cool-temperate; no heating in hot zones")
        void zones() {
            assertThat(HeatDemand.zoneFactor(HeatDemand.Zone.MILD)).isCloseTo(0.414, within(0.001));
            assertThat(HeatDemand.zoneFactor(HeatDemand.Zone.HOT_HUMID)).isZero();
            Range house = HeatDemand.annualKwh(150, HeatDemand.Era.BEFORE_1960, HeatDemand.Windows.DOUBLE_OLD,
                    HeatDemand.Zone.COOL_TEMPERATE);
            assertThat(house).isEqualTo(new Range(22500, 30000, 37500));
            assertThatThrownBy(() -> HeatDemand.annualKwh(0, HeatDemand.Era.BEFORE_1960, HeatDemand.Windows.SINGLE,
                    HeatDemand.Zone.COLD)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("§6, §7, §9 Which options are shown")
    class EligibilityTest {
        @Test
        @DisplayName("no upkeep: no wind, hydro, wood, pellets or biogas")
        void noUpkeep() {
            var all = EnumSet.allOf(Eligibility.Option.class);
            var left = Eligibility.filter(all, true, false);
            assertThat(left).doesNotContain(Eligibility.Option.SMALL_WIND, Eligibility.Option.MICRO_HYDRO,
                    Eligibility.Option.LOG_BOILER, Eligibility.Option.PELLET_BOILER, Eligibility.Option.BIOGAS);
            assertThat(left).contains(Eligibility.Option.ROOFTOP_PV, Eligibility.Option.AIR_HEAT_PUMP);
        }

        @Test
        @DisplayName("tenants: only what needs no landlord works")
        void tenant() {
            var left = Eligibility.filter(EnumSet.allOf(Eligibility.Option.class), false, true);
            assertThat(left).containsExactlyInAnyOrder(Eligibility.Option.PLUG_IN_PV, Eligibility.Option.CLEAN_COOKING,
                    Eligibility.Option.BATTERY);
        }

        @Test
        @DisplayName("boilers from 15 MWh of heat in the countryside; biogas from 20 kg of manure a day")
        void thresholds() {
            assertThat(Eligibility.boilerFits(15001, true)).isTrue();
            assertThat(Eligibility.boilerFits(15000, true)).isFalse();
            assertThat(Eligibility.boilerFits(30000, false)).isFalse();
            assertThat(Eligibility.biogasFits(20)).isTrue();
            assertThat(Eligibility.biogasFits(19.9)).isFalse();
        }
    }

    @Nested
    @DisplayName("Ranges")
    class RangeTest {
        @Test
        @DisplayName("keep their order through sums and products, and refuse a wrong order")
        void order() {
            Range a = new Range(1, 2, 3), b = new Range(-2, 1, 4);
            assertThat(a.plus(b)).isEqualTo(new Range(-1, 3, 7));
            assertThat(a.times(b)).isEqualTo(new Range(-6, 2, 12));
            assertThat(a.times(-1)).isEqualTo(new Range(-3, -2, -1));
            assertThat(a.contains(3)).isTrue();
            assertThatThrownBy(() -> new Range(3, 2, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
