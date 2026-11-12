# CO₂ per kWh: electricity by country, and fuels

Our own research (WeShape, November 2026), because the expert input has no emission values. Used by
`src/main/java/com/weshape/backend/calc/Co2.java` and `src/main/resources/calc/electricity-co2.csv`;
checked by `Co2Test`. All values are in **g CO₂e per kWh** (CO₂ plus methane and nitrous oxide,
counted as CO₂ by their warming effect over 100 years).

## Electricity by country [C1]

- **What:** lifecycle emissions per kWh generated in the country (power plants, plus building them
  and the fuel chain), Ember's yearly electricity data, via Our World in Data.
- **Countries:** 212, data from 2022–2025 (most 2024 or 2025). File: `electricity-co2.csv`
  (`github.com/owid/energy-data`, commit f8de88a4, 1 October 2026).
- **Range:** typical = the latest year; low and high = the lowest and highest of the last three years
  with data. Countries with much hydro power vary most from year to year (Colombia 187–298,
  Venezuela 86–180, Montenegro 264–422).
- **Left out:** Central African Republic (0 g in the data, which cannot be right: even hydro power
  has lifecycle emissions) and Western Sahara (last data 2009).
- **Country not in the table:** the world average, 458 (458–483, 2023–2025) [C1]; the result is
  marked as "country not known".

Examples (2025):

| Country | g/kWh | | Country | g/kWh |
|---|---|---|---|---|
| Norway | 28 | | Germany | 330 |
| France | 41 | | United States | 384 |
| Kenya | 95 | | Nigeria | 456 |
| Brazil | 110 | | Mexico | 474 |
| Austria | 117 | | China, Australia | 525 |
| Spain | 154 | | Poland | 589 |
| Hungary | 163 | | India | 670 |
| United Kingdom | 217 | | South Africa | 699 |
| Romania | 251 | | Kosovo | 901 |
| Italy | 285 | | | |

## Fuels burnt at home

Per kWh of fuel (net calorific value). Burning [C2] with warming potentials [C3], plus the supply
chain [C4].

| Fuel | Burning | Supply chain | **Total used** | Range |
|---|---|---|---|---|
| Natural gas | 202.6 | 33.5 | **236** | 212–279 |
| LPG | 227.8 | 27.4 | **255** | |
| Kerosene | 260.5 | 54.2 | **315** | |
| Heating oil | 268.4 | 62.9 | **331** | |
| Coal | 374.2 | 59.3 | **433** | |
| Lignite | 397.3 | 59.3 (as coal) | **457** | |
| Firewood | 33.1 | 12.8 | **46** | 18–128 |
| Wood pellets | 33.1 | 37.4 | **71** | |

- **Burning:** IPCC 2006 defaults for homes (Table 2.5), kg per TJ: natural gas CO₂ 56,100, CH₄ 5,
  N₂O 0.1; wood CO₂ 112,000, CH₄ 300, N₂O 4; the others in the code. 1 kg/TJ = 0.0036 g/kWh.
- **Warming potentials** [C3]: methane 29.8 (fossil) or 27.0 (from wood), nitrous oxide 273.
- **Supply chain** [C4]: extraction, processing and transport ("well-to-tank"), UK government
  factors, net calorific value. Outside the UK it can be quite different: we use **half to double**
  as the range [Judgement, WeShape].
- **CO₂ from wood counts as zero**, as in every national inventory [C2]: the tree took it from the air.
  This holds only if the forest grows back. For information, the code also gives it:
  **403 g/kWh** (342–475). Wood is therefore low only for sustainably harvested wood.
- **Old stoves** emit more methane than modern boilers; the IPCC range (100–900 kg/TJ) covers this,
  so firewood's range is wide.

## Heat delivered

The values above are per kWh of fuel. Per kWh of heat, divide by the efficiency (`Boiler`) or by the
heat pump's factor (`HeatPump`).

**Example, Romania, 10,000 kWh of heat a year:**
- air heat pump (3.4): 2,941 kWh × 251 g = **about 740 kg**;
- condensing gas boiler (83%): 12,048 kWh × 236 g = **about 2,840 kg**.

## Limits

- **Electricity is the average of the country's generation.** Imports and grid losses (often 5–10%)
  are not counted; neither is the time of day (solar noon is cleaner than winter evenings).
- **Lifecycle (electricity) against supply chain plus burning (fuels):** close, but not the same
  boundary; plant construction is counted for electricity, not for boilers.
- **The supply chain values were read from a republication** of the 2026 UK factors [C4]; to check
  against the official file.

## Not yet covered

- **Charcoal** (important in Africa): making it in kilns emits much methane; no reliable value yet.
- **Biogas** from a home digester (methane leaks).
- **Solar panels, batteries:** lifecycle emissions of the equipment itself.

## Sources

- C1 Ember, *Yearly Electricity Data* (2026), via Our World in Data, energy-data
  (`carbon_intensity_elec`), CC BY 4.0: https://ember-energy.org/data/yearly-electricity-data/ ;
  https://github.com/owid/energy-data
- C2 IPCC, *2006 Guidelines for National Greenhouse Gas Inventories*, Volume 2, Chapter 2
  (Stationary combustion), Table 2.5 and section 2.3.3.4:
  https://www.ipcc-nggip.iges.or.jp/public/2006gl/pdf/2_Volume2/V2_2_Ch2_Stationary_Combustion.pdf
- C3 IPCC, *AR6 Working Group I*, Chapter 7, Table 7.15 (GWP-100).
- C4 UK DESNZ, *Greenhouse gas reporting: conversion factors 2026*, well-to-tank, as republished by
  GreenCalculus: https://greencalculus.com/data/defra-well-to-tank-factors/
