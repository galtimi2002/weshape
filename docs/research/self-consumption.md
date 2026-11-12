# Solar self-consumption: how much the home uses itself

Our own research (WeShape, November 2026), because the expert input has no values for it. Used by
`src/main/java/com/weshape/backend/calc/SelfConsumption.java`; checked by `SelfConsumptionTest`.

**Why it matters:** electricity used at home saves the full price; electricity sent to the grid is
usually paid much less (or nothing). So the saving depends strongly on this share.

**Ratio** below means: solar kWh a year ÷ the home's kWh a year.

## Values used

| Case (ratio 1) | Used | Range | Basis |
|---|---|---|---|
| No battery | **30%** | 20–41% | 28% [P1]; just under 30% [P2]; 20–33%, 30–41% with smart control [P3] |
| Battery of at least 0.5 kWh per kWp | **+20 points** | +13 to +30 | +13–24 at 0.5–1 kWh/kW [P4]; +19 at 0.6 [P1]; about +30 at 0.8 [P2] |
| Smaller battery | part of it | | gain × (kWh per kWp ÷ 0.5) [Judgement, WeShape] |

## Other sizes [Judgement, WeShape]

- **Larger system (ratio above 1):** the kWh used at home stay the same, so the share falls as
  1 ÷ ratio. In reality the kWh used at home still grow a little [P2]; we are on the safe side.
- **Smaller system (ratio below 1):** the share of ratio 1 is kept. In reality it is higher [P2];
  again on the safe side.
- **Never above 71%** (41% + 30%).

## Checks against the sources

- **[P1] example:** 4 people, 140 m², heat pump, 10 kWp south 45°, 6 kWh usable battery. About
  8,800 kWh used, about 10,000 made (ratio ≈ 1.1). Measured 47%; ours 44% (29–62%).
- **[P2] example:** 4,700 kWh a year, 5 kWp (about 958 kWh per kWp), 4 kWh battery. About 60%;
  ours 49% (32–70%).

## Not yet covered

- **Hot zones:** daytime air conditioning raises the share; no source found yet, so the same values
  are used.
- **Smart use** (washing in the day, a heat pump or car charged at noon): +2 to +15 points [P4]. Not
  counted.
- **Prices:** what a used and an exported kWh are worth, by country (to do, with the costs).

## Sources

- P1 Verbraucherzentrale Rheinland-Pfalz, PV with heat pump and battery (example, 2022):
  https://verbraucherzentrale-rlp.de/node/71757
- P2 Weniger, Quaschning, Tjaden (HTW Berlin), *Solare Unabhängigkeitserklärung* (2012):
  https://solar.htw-berlin.de/wp-content/uploads/WENIGER-2012-Solare-Unabhaengigkeitserklaerung.pdf
- P3 Tjaden (HTW Berlin, 2015), as cited in P1.
- P4 Luthander et al., *Photovoltaic self-consumption in buildings: A review*, Applied Energy 142
  (2015) 80–94: https://ideas.repec.org/a/eee/appene/v142y2015icp80-94.html
