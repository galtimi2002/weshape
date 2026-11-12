# Boilers and stoves: seasonal efficiency

Our own research (WeShape, November 2026), because the expert input has no efficiency values for
boilers. Used by `src/main/java/com/weshape/backend/calc/Boiler.java`; checked by `BoilerTest`.

**Efficiency** here means: heat delivered to the home per kWh of fuel (net calorific value), over a
season. Fuel needed = heat demand ÷ efficiency. **To confirm with the expert:** the values marked
[Judgement].

| Appliance | Used | Range | Basis |
|---|---|---|---|
| Pellet boiler | **78%** | 65–85% | Field: UK 78% net average [B1]; Austria 65–85% [B2] |
| Log (gasification) boiler | **81%** | 70–86% | Field: UK 81% net average [B1]; low end [Judgement], high end the lab top [B1] |
| Modern closed wood stove (since 2022) | **70%** | 65–80% | EU minimum 65% [B5]; typical and high [Judgement] |
| Older, uncertified wood stove | **43%** | 35–55% | 65% ÷ 1.5 [Derived from B6]; range [Judgement] |
| Pellet stove (since 2022) | **82%** | 79–88% | EU minimum 79% [B5]; typical and high [Judgement] |
| Open fireplace | **20%** | 10–30% | EU minimum only 30% [B5]; typical and low [Judgement] |
| Condensing gas boiler | **83%** | 80–90% | Field: UK 82.9% and 83.2% on average, most 80–90% [B3] |

## What the field trials found

**UK, 67 biomass boilers, 10 kW to 800 kW [B1]**
- Average in-situ efficiency: pellet 78% net (72% gross), chip 75% (67%), log 81% (74%).
- Log boilers varied least.
- Boilers under 100 kW: about 81% net.
- Laboratory steady-state: 77–86% gross. Over three-quarters of the field boilers were below it, on
  average by about 15 percentage points.
- Main causes:
  - oversizing (average load factor 14%, against an ideal of about 20%);
  - on/off cycling at low demand;
  - buffer tanks that are too small;
  - wet or contaminated fuel;
  - poor settings and maintenance.

**Austria, 5 pellet boilers in homes [B2]**
- Annual efficiency 65–85%, against 90–94% certified in the laboratory.
- Efficiency fell with low load and many ignitions.

**UK, condensing gas boilers in homes [B3]**
- 82.86% and 83.21% average in two groups (about 48 and 28 boilers); most sites 80–90%.
- A few were lower at low heat loads.

## Minimum efficiencies for new appliances (EU)

- **Solid-fuel boilers** (Regulation 2015/1189, from 2020) [B4]: at least 75% up to 20 kW, 77% above.
- **Solid-fuel local space heaters** (Regulation 2015/1185, from 2022) [B5]:
  - open-fronted: 30%;
  - closed-fronted: 65%;
  - closed-fronted pellet: 79%;
  - cookers: 65%.

## Older stoves [B6]

- **US EPA:** certified stoves can be up to 50% more efficient and use about a third less wood for
  the same heat than older stoves.
- **Rated efficiencies:** advertised figures are often combustion efficiency; the overall efficiency
  can be 10–20% lower.
- **Our derivation:** a third less wood means the old stove gives 1 ÷ 1.5 of the heat per kWh. With a
  modern stove at the EU minimum of 65%, an old stove is about 43%.

## Still missing

- **Firewood energy:** kWh per stacked m³ by species and moisture. The app asks firewood in m³.
- **Old non-condensing gas boiler:** no field value found yet.

## Sources

- B1 UK Government (BEIS), *Biomass boilers: measurement of in-situ performance*, summary of findings:
  https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/830905/Summary_of_findings.pdf
  ; https://www.gov.uk/government/publications/biomass-boilers-measurement-of-in-situ-performance
- B2 *Efficiency and operational behaviour of small-scale pellet boilers installed in residential
  buildings* (Austria): https://agris.fao.org/search/en/records/65df6bb76eef00c2cea2c360
- B3 Energy Saving Trust / DECC, *In-situ monitoring of efficiencies of condensing boilers*:
  https://assets.publishing.service.gov.uk/media/5a78c2b0ed915d042206520a/1149-condensing-boilers.pdf
- B4 Commission Regulation (EU) 2015/1189, Annex II:
  https://legislation.gov.uk/eur/2015/1189/annexes/data.xht
- B5 Commission Regulation (EU) 2015/1185, Annex II, Division 1:
  https://www.legislation.gov.uk/eur/2015/1185/annex/II/division/1
- B6 US EPA, *Burn Wise: energy efficiency*: https://www.epa.gov/burnwise/burn-wise-energy-efficiency
