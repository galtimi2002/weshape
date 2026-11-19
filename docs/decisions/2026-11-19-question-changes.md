# Question changes: power cuts, gas bottles, animals and crops, parts kept free, people, building material

Date: 2026-11-19. Decided by Timi.

## Removed

- **"If something broke, who would fix it?"** Not needed. The expert's limit "maintenance tier 100 when
  no one could fix it" (`scoring.xlsx`, Exclusions, `maintenanceNoRepair`) has no answer to work from
  any more; it stays in the file but is not applied. `Eligibility.filter(..., noUpkeep, ...)` is called
  with `false`.
- **"Is there a small brook on your land that we can't see on the map?"** Not needed.

## Power cuts and full independence

Full independence of the grid (off-grid) is possible for every answer except **most days, for a few
hours or longer**. "Most days, under an hour" is the worst case still allowed; "hardly ever" and
"a few times a month" allow it whatever the length. Where it is not possible the app does not ask
"Would you like to be fully independent of the grid?", and the table's own advice applies
(`Battery.offGridPossible`, `Battery.plan`). With no grid at all the home is off-grid anyway.

## Gas bottles

When "gas bottles" is chosen as a source, the app asks how many bottles a month and **how big**, in the
unit the person chooses: **litres, kilograms or US gallons** (`GasBottle`). Piped gas keeps its own
question in m³ or kWh; both are counted when both are used.

- kg: the gas in the bottle, as written on it (11 kg).
- litres: the bottle's volume, as bottles are labelled in Europe (26 L); bottles are filled to 80–85%.
- US gallons: gallons of liquid gas, as propane is sold in the US (a 20 lb tank holds 4.7 gal).
- 0.51–0.58 kg a litre, 12.7–12.9 kWh a kg. A 26 L bottle ≈ 11 kg ≈ 140 kWh.

## Animals, food waste and crop residue

"Do you keep animals or have farm or food waste?" — several answers can be chosen:

- **A few animals:** which (cattle, pigs, poultry, horses, sheep or goats) and how many. The manure and
  the biogas are worked out from that (`Biogas`); the person is not asked for the manure.
- **Food waste:** how much a week, in kg or lb (`Biogas.foodWasteM3PerDay`).
- **Crop residue** (what stays on the field after the harvest: maize stalks and cobs, straw, sunflower
  stalks, prunings): which crop (maize, wheat or barley, sunflower, rapeseed, vineyard or orchard) and
  on how many hectares or acres (`CropResidue`). 1 ha of maize leaves 5–8 t; 30–50% may be taken.
- **None:** nothing more is asked.

"A herd or a farm" is no longer an answer.

## Parts of the roof or yard kept free

Instead of "Why?" with five reasons (`KeptFree`):

- **Something is there** — a chimney, a window or skylight, something else. For a chimney no size is
  asked (about 1–3.2 m² with its clearance); for a window or anything else roughly how wide and how
  high, plus 0.3 m around it. Only that is left out, and the app says: *the rest of this part can still
  be used.*
- **I don't want anything there**, **hard to reach for upkeep**, **the rules don't allow it**: nothing
  goes on that part.

## People living there

Asked only when hot water is one of the uses: it is what the hot water need is worked out from
(`Household.hotWaterKwh`).

## What the building is made of

Next to "When was the building built?": brick, concrete or panel, wood, stone or adobe, don't know.
With the age and whether the roof was renovated it says whether the roof can carry panels or a solar
water heater (`RoofSafety`, [Judgement, WeShape], for the expert to confirm):

| Material | Before 1960 | 1960–1989 | From 1990 | Unknown age |
|---|---|---|---|---|
| Wood | unsafe | check | safe | check |
| Stone or adobe, or unknown | check | safe | safe | check |
| Brick, concrete | safe | safe | safe | safe |

A renovated roof is safe in every case. Unsafe: no roof options, only ground or yard ones. Check: roof
options with the note to have the structure checked first.

## Firewood

Already asked: stacked m³ a heating season, the kind of wood and the price (`Firewood`).

## Tests

`BatteryPlanTest`, `GasBottleTest`, `BiogasTest`, `CropResidueTest`, `KeptFreeTest`, `RoofSafetyTest`.
