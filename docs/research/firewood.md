# Firewood: energy per stacked cubic metre

Our own research (WeShape, November 2026). Used by `src/main/java/com/weshape/backend/calc/Firewood.java`;
checked by `FirewoodTest`.

**Unit.** 1 m³ of stacked split logs, which is about 0.7 m³ of solid wood. Local names:
- Hungarian: "erdei köbméter"
- Romanian: "metru ster"
- German: "Raummeter"

Loose, thrown wood holds less.

## The data [W1]

kWh per stacked m³:

| Species | 15% water | 20% water | 50% water (fresh) |
|---|---|---|---|
| Beech | 1,907 | 1,885 | 1,687 |
| Birch | 1,798 | 1,777 | 1,591 |
| Pine | 1,533 | 1,516 | 1,364 |
| Spruce | 1,348 | 1,333 | 1,199 |

**What follows from the table:**
- **Moisture matters little per volume.** Fresh wood gives only about 11% less than dry wood, because
  wet wood is heavier and a stacked m³ holds more of it.
- **The species matters much more.** Beech gives about 40% more than spruce.
- **What the app asks.** So it asks the kind of wood, not the moisture.
- **Wet wood also burns worse.** It lowers the stove's or boiler's efficiency; the low ends of the
  ranges in `docs/research/boilers.md` cover that.

## Values used

| App answer | kWh per m³ | Basis |
|---|---|---|
| Hardwood (beech, oak, acacia) | **1,800** (1,591–1,907) | Birch fresh to beech dry [W1] |
| Softwood (spruce, pine, poplar) | **1,400** (1,199–1,533) | Spruce fresh to pine dry [W1] |
| Mixed | **1,600** (1,400–1,800) | [Judgement] |
| Don't know | **1,600** (1,199–1,907) | The whole table [W1] |

**Oak and acacia** are not in the table. They are denser than beech, so the hardwood range is, if
anything, cautious.

## Sources

- W1 Bayerische Landesanstalt für Wald und Forstwirtschaft (LWF), *Merkblatt 20: Scheitholz* (2025):
  https://lwf.bayern.de/mam/cms04/service/dateien/mb20_scheitholz_2025_rz_web_bf.pdf
