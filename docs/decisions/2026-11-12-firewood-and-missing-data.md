# Decision: firewood questions and missing data (12 November 2026)

Agreed with Timi.

## Questions

All in the **"A few more numbers"** step (fuels), under **"How much firewood do you burn in a heating
season?"**. They are shown only when an amount of firewood is entered.

1. **Amount, m³.** Already asked. Add a note under the field: "1 m³ = one cubic metre of stacked split
   logs (erdei köbméter / metru ster)".
2. **"What wood do you burn?"** Hardwood (beech, oak, acacia) · Softwood (spruce, pine, poplar) · Mixed
   · Don't know.
3. **"How much does one m³ of firewood cost?"** Number plus the same currency picker as for gas and
   electricity.
4. **Why we ask**, a small note under the questions: "From this we work out how much heat your home
   uses now, and how much you could save with the other options."

## If the person does not know

Every field here may stay empty or be answered "Don't know". The other fields are still asked.

**On the spot:** a soft yellow note appears: "If you don't know, the result will be less precise. A
rough number helps a lot, e.g. 'about 8 m³' or 'about 350 lei'."

**How we calculate (`Firewood`, `Boiler`, `HeatDemand`):**

| Missing | Calculation | Text to the person |
|---|---|---|
| Kind of wood | 1,199–1,907 kWh/m³, 1,600 used | "We count with average wood; the result is a little less precise." |
| Price | Saving shown in wood and kWh ("about 4 m³ less a year"), not in money; no payback | "Without a price we can't tell the saving in money, only in wood. A rough price helps a lot." |
| Everything | Heat demand from the house (era × area × windows × climate), wide range; saving only as a range, without money | "We estimate from the house how much heat it uses. This is less precise than real use, so the result is a wider range." |

## On the result page

List exactly what was missing, so the person sees whether it was one thing or many. For example:

> **These data were missing, so the result is less precise:**
> • price of firewood
> • kind of firewood
> • window type ("Don't know")
> [Add the missing data]

**What the list includes:**
- empty fields in the fuels step;
- every "Don't know" answer elsewhere: era, renovation, wall/roof/floor insulation, windows, "not sure"
  places, the stream, who fixes things, "could something go on it".

**The button:** it leads straight to the question where the data is missing.

## Status

Agreed; not built yet. To build:
- app: the questions, the notes, the warning, the result list (25 languages);
- backend: done for the energy (`Firewood`).
