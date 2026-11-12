# Heat pumps: seasonal efficiency by climate zone

Our own research (WeShape, November 2026), because the expert input has no efficiency values. Used by
`src/main/java/com/weshape/backend/calc/HeatPump.java`; checked by `HeatPumpTest`. **To confirm with the
expert:** the values for the cold and mild zones are our own derivation (see "How the zones are made").

## Heating

| Climate zone | Air-source | Ground-source |
|---|---|---|
| Cold | **2.9** (2.2–4.2) [Derived] | **4.3** (3.6–5.4) |
| Cool-temperate (e.g. Romania, Hungary, Germany) | **3.4** (2.6–4.9) [R1] | **4.3** (3.6–5.4) [R1] |
| Mild / Mediterranean | **4.2** (3.2–6.1) [Derived] | **4.3** (3.6–5.4) |
| Hot-humid, hot-arid | no heating season | – |

Seasonal performance factor: heat delivered per kWh of electricity over the heating season.
Electricity = heat demand ÷ factor.

### Measured in real homes [R1]

Fraunhofer ISE field trial in existing (not new) German single-family homes, 4 years:

- **Air/water, 61 systems:** average 3.4, range 2.6–4.9 (3.1 in the earlier WPsmart trial).
- **Ground-source (brine/water), 16 systems:** average 4.3, range 3.6–5.4 (4.1 earlier).
- **Radiators:** adequately sized radiators can run at similarly low temperatures as underfloor
  heating.
- **Building age:** no correlation with efficiency.
- **Electric heating rod:** 1.3% of the electricity in air systems, close to 0% in ground systems.

### How much the climate matters, in the lab [R2][R3]

EU reference climates (Regulation 811/2013, EN 14825):

| Climate | Design temperature | Heating hours a year |
|---|---|---|
| Colder (Helsinki) | −22 °C | 6,446 |
| Average (Strasbourg) | −10 °C | 4,910 |
| Warmer (Athens) | +2 °C | 3,590 |

SCOP of an 8 kW air/water heat pump (HP Keymark data sheet) [R3]:

| Climate | 35 °C (floor heating) | 55 °C (radiators) |
|---|---|---|
| Colder | 4.44 | 3.12 |
| Average | 5.15 | 3.63 |
| Warmer | 6.51 | 4.47 |

### How the zones are made [Derived]

- **Lab values are higher than real ones.** So we start from the measured field values and only
  take the climate ratio from the lab.
- **Colder against average:** (4.44 ÷ 5.15 + 3.12 ÷ 3.63) ÷ 2 = **0.861**.
- **Warmer against average:** (6.51 ÷ 5.15 + 4.47 ÷ 3.63) ÷ 2 = **1.248**.
- **Air-source, cold zone:** field values × 0.861. **Mild zone:** field values × 1.248.
- **Ground-source:** the same in every heating zone. The ground keeps its temperature through the
  year [Judgement].

## Hot zones

There is no space heating in hot zones (expert input). Two other uses matter there.

### Cooling: split air conditioners [R4][R5]

Seasonal efficiency (ISEER, kWh of cooling per kWh of electricity), Indian energy labels:

| Unit | ISEER | Used |
|---|---|---|
| Older, 1-star | 3.1–3.3 [R4] | 3.2 |
| 3-star inverter | 3.7–4.38 [R5] | 4.0 |
| 5-star inverter | 5.0–5.85 [R5] | 5.4 |

**Check [R5]:** 1.5-ton units use about 1,008 kWh a year at 3 stars, 903 at 4 stars and 740 at 5
stars. Our values give −26% from 3 to 5 stars, against −27% in the labels.

**Limits:** the label is a guideline. It assumes 1,600 hours a year, most of them at or below 30 °C,
and the same rating applies everywhere in India. Hotter cities (Chennai) will see different
performance than milder ones (Bengaluru) [R5].

### Hot water: heat pump water heaters [R6]

Florida Solar Energy Center, hot-humid climate (Cocoa, Florida), 2013–2014:

- **Heat pump water heater:** COP 2.2 (60-gallon) and 2.5 (80-gallon).
- **Standard electric tank:** 0.82, standby losses included.
- **Saving:** 61.5% measured for the 60-gallon unit, 62.5% on average at 60 gallons a day.
- **Our formula:** 1 − 0.82 ÷ COP = 63–67%, close to the measured savings.

## Sources

- R1 Fraunhofer ISE, *Heat pumps provide climate-friendly heating in existing buildings* (press
  release 2025):
  https://www.ise.fraunhofer.de/content/dam/ise/en/documents/press-releases/2025/3025_ISE_PR_Heat-Pumps-Provide-Climate-Friendly-Heating-in-Existing-Buildings.pdf
  ; project page:
  https://www.ise.fraunhofer.de/en/research-projects/wpsmart-im-bestand-heat-pump-field-trial-focus-existing-buildings-and-smart-control.html
- R2 Commission Delegated Regulation (EU) 811/2013, Annex VII (reference climates):
  https://www.legislation.gov.uk/eur/2013/811/annex/VII/adopted/data.pdf
- R3 HP Keymark data, Saunier Duval GeniaSet Split 8:
  https://outils-avv.saunierduval.fr/outils_synergies/Outils_SD/Donnees_HP_KEYMARK/Saunier%20Duval%20-%20HP%20Keymark%20-%20GeniaSet%20Split%208%20-%201C%20et%202C.pdf
- R4 CAG, *Indian Seasonal Energy Efficiency Ratio (ISEER) overview*:
  https://www.cag.org.in/newsletters/public-newsense/indian-seasonal-energy-efficiency-ratio-iseer-overview
- R5 Down To Earth, *What is the star rating label of an air-conditioner telling you?* (CSE analysis):
  https://www.downtoearth.org.in/energy/what-is-the-star-rating-label-of-an-air-conditioner-telling-you
- R6 NREL / Building America, heat pump water heaters in a hot-humid climate (FSEC):
  https://nrel.gov/docs/fy17osti/65404.pdf
