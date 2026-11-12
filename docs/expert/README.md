# Expert input for the assessment calculations

What the energy expert gave us, how the app uses it, and what is still missing. The code that
uses it is in `src/main/java/com/weshape/backend/calc/`; every number there names its section and
source, and `ExpertNumbersTest` checks the code against the expert's own worked examples.

## Files

| File | What it is |
|---|---|
| `2026-11-expert-input-answers-v2.pdf` | The expert's answers to our 12 questions (November 2026), with sources S1–S37. Values are tagged [Sourced], [Derived] or [Judgement]. |
| `2026-11-reference-values-and-weights-request.docx` | Our request for the scoring numbers (reference values, weights, exclusion limits). **Not answered yet.** |

## The expert's own remarks (sent with the answers)

1. None of this can be guaranteed for every country: much varies by area, weather and economy.
   Information on Latin America and Africa is much harder to find.
2. Question 3 (how high is too high): national averages exist, but it is not linear; households
   have very different needs. → The warning is only a "please check", never a block.
3. Question 5 (wind): "1 acre" is a common minimum, but it really depends on the turbine size,
   tower height, local rules, noise limits and the manufacturer's instructions. → The app says
   "check" rather than "not possible" when the land is close to the limit.
4. Question 10 (areas to keep free): commercial and larger buildings can fall under other
   provisions of the International Fire Code (IFC); requirements depend on roof size, building
   layout, occupancy and the code edition adopted. → Homes only; larger buildings get "confirm
   with the fire service".
5. Question 11 (costs): prices vary a lot by country; import taxes and national commercial law
   can change equipment prices a lot. → Global bands plus the user's own quote, in local currency.

## How each section is used

| Expert section | App question(s) | Used for | Code |
|---|---|---|---|
| 1a Heating by year built | Building, era, area | Heat demand (kWh/m² × area) | `HeatDemand.byEra`, `annualKwh` |
| 1b Renovation | Renovation, when | Cut of 54% / 75%, × 0.75 realism | `HeatDemand` constants, `realCut` |
| 1c Windows | Windows | Heat demand factor (single → modern double ≈ −24%) | `HeatDemand.windowsFactor` |
| Climate zones | (from the place) | Heating × zone factor; none in hot zones | `HeatDemand.zoneFactor` |
| 2 Hot water, cooking | People, uses | Demand when bills are missing | `Household` |
| 3 Bills, 3 months | Electricity, gas | "Check" / "very high" warning by country | `UsageCheck` |
| 4 Power cuts | Power cuts | Battery advice and size | `Battery` |
| 5 Space | Spaces, sizes, roof facing | kWp on the roof, orientation, dust | `SolarPv` |
| 6 Ownership | Ownership | Tenants: only options without landlord works | `Eligibility.filter` |
| 7 Upkeep | Hands-on | "No upkeep": no wind, hydro, wood, biogas | `Eligibility.filter` |
| 8 Profile weights | Priority | **Not answered**: the expert wants to see all five sets first | waiting |
| 9 River, wind, animals, biomass | Follow-ups, maps | Hydro, wind, biogas, boiler rules | `Hydro`, `Wind`, `Eligibility` |
| 10 Areas to keep free | Exclude question | Roof edge strips (0.5 m, 1 m around windows) | to do |
| 11 Costs, lifetimes | Currency, bills | Payback | to do (needs the reference values) |
| 12 Permits | (country) | "Typical requirements, confirm locally" | to do (country table) |

## Our own choices that an expert must confirm

Marked `[Judgement, WeShape]` in the code:

- **App eras onto the expert's building classes.** 1960–1989 spans "before 1977" and "1977–2002";
  1990–2009 spans "1977–2002" and "after 2002".
- **Window type in the class values.** These are taken to include older double glazing; a mix or
  "don't know" counts as older double glazing.
- **Air conditioning.** 350 kWh a month per unit for the usage warning (the expert gives 200–500).
- **Heat of hot panels.** Not counted on top of PVGIS, which already models it; only extra dust
  (2–6%) is counted in dusty places.
- **Tenants.** Kept: plug-in panels, clean cooking and a portable battery.
- **Mapping the app's "power cuts" answer onto minutes per year.** To do.
- **Mapping the "wind exposure" answer onto the terrain table.** To do.

## Still missing

- **From the expert:** the filled-in reference values and weights
  (`2026-11-reference-values-and-weights-request.docx`).
- **Data:**
  - degree-days by place (climate zone; turning 3 months of bills into a year);
  - national building typologies (TABULA);
  - a country table for permits, plug-in solar and incentives.
