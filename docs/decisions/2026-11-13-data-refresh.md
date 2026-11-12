# Data refresh: calculation data kept current without a new release

**Date:** 12–13 November 2026. **Agreed with:** Timi.

## Decision

- **Numbers live in data files, not in the code.** The code holds the formulas; the tables can be
  replaced while the app runs.
- **R2:** each table has one current file under `data/`. A refresh **replaces** it; the one before is
  kept **once** as `data/<name>.prev.csv` (one step to undo; the next refresh overwrites it). No
  yearly copies pile up.
- **The backend** (`CalcData`) reads the tables at start and every 6 hours, checks them, and puts them
  in use. A missing, unreadable or failing file changes nothing. Without R2 it uses the copy shipped
  with the app (`src/main/resources/calc/`).
- **Public sources are fetched once a year** by the "Refresh calculation data" workflow, checked
  before upload. When a check fails nothing is uploaded, the run turns red and GitHub e-mails us.
- **Our own research and the expert's values** (heat pumps, boilers, firewood, self-consumption,
  weights) change rarely and need a person: they are changed by hand, with the tests.
- **Each result keeps the numbers it used** (e.g. "Romania 251 g/kWh, Ember 2025"), so an old
  assessment shows the same later without keeping old tables. To build with the results page.

## Size

All the data planned to be refreshed (electricity CO₂, prices by country, country table, building
types, degree-days) is under about 5 MB; with the one backup copy under about 10 MB. The R2 free
tier is 10 GB.

## Tables

| Table | R2 key | Source | Refresh | Checks |
|---|---|---|---|---|
| Electricity CO₂ by country | `data/electricity-co2.csv` | Ember via Our World in Data | yearly, 15 June | ≥ 200 countries; 10–1400 g; a country changing by > 50% and > 50 g stops the upload until "force" |

To come, in the same way: price ranges by country (yearly, see `2026-11-12-prices-as-ranges.md`),
degree-days (`2026-11-12-question-notes-and-climate-grid.md`), the country table.

## How to

- **Run now:** Actions → "Refresh calculation data" → Run workflow.
- **A real big change** (a dry year for a hydro country): run it with "force".
- **Undo the last upload:** run it with "restore previous".
