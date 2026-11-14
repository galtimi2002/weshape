# The power cuts question is back, with what it is used for

**Date:** 14 November 2026. **Agreed with:** Timi. Reverses section 3 of
`2026-11-12-question-notes-and-climate-grid.md` (question removed on 12 November).

## Why

From the team's note (14 November 2026):
- To suggest whether someone should **stay on the grid**, we need to know the grid situation: is there
  constant mains electricity, or do they rely on other sources?
- If they stay on the grid: **do they need batteries, how many and how large.** This comes from the
  daily use and the power cuts: someone with a cut once or twice a year needs less than someone with
  cuts every month.
- For someone who wants **full independence** the storage suggestion is different.

## The questions (app, step after "Where does your energy come from?", only with mains electricity)

1. **"Milyen gyakran megy el az áram?"** Soha · Szinte soha (évente egyszer-kétszer) · Havonta
   néhányszor · Szinte minden nap.
2. **"Általában meddig tart?"** (not after "Soha"): Kevesebb mint 1 óra · Néhány óra · Egy napnál
   tovább.
3. **"Szeretnél teljesen független lenni a hálózattól?"** Igen · Nem. A separate question (chosen
   over a priority).
- Note under the title: "Ebből látjuk, érdemes-e a hálózaton maradnod, és kell-e akkumulátor, és ha
  igen, mekkora."

## How it is used (`Battery.plan`, `Battery.kwh`, tests `BatteryPlanTest`)

Row by row from the expert's table (section 4):

| | Under 1 hour | A few hours | Over a day |
|---|---|---|---|
| Never | none | none | none |
| Once or twice a year | none (< 60 min/yr) | optional, 5–10 kWh (60–300 min/yr) | backup (long event) |
| A few times a month | optional ("1–3 short cuts a month") | backup, 10–13 kWh ("cuts at least monthly") | solar + battery as main (> 20 h a month) |
| Most days | backup | solar + battery as main | solar + battery as main |

- **No mains electricity** (only a generator or nothing), or **"Igen" to full independence**:
  off-grid, solar plus battery on its own.
- **Size:**
  - the advice's own band (optional 5–10, backup 10–13, main 10–15 or 15–25 kWh with air
    conditioning) [expert, S33];
  - cuts over a day: essential loads 5–10 kWh/day (at most the home's daily use) × 1–3 days ÷
    (0.9 × 0.9), at least the band [expert, S12: "24–72 h", "10 kWh/day about 30 kWh for 3 days"];
  - off-grid: the home's daily use × 1–3 days ÷ (0.9 × 0.9).

**To confirm with the expert [Judgement, WeShape]:** the cell for "once or twice a year, over a day"
(long event → backup); "most days, under an hour" → backup; using the long-event 1–3 days for
off-grid too.
