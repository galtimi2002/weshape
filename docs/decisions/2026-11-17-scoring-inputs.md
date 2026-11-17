# Scoring inputs from the expert; five priorities; only what the user wants to replace

Date: 2026-11-17. Agreed by Timi with the energy expert.

## What the expert gave

`docs/expert/2026-11-reference-values-and-weights-answers.docx` (our request, filled in).

### Reference values (0–100 points, proportional in between)

| Criterion | Unit | 100 points | 0 points |
|---|---|---|---|
| Payback time | years | 3 | 20 |
| Annual savings | % of the bills the user gave us | 100% | 0% |
| Share of demand covered | % of yearly consumption | 100% | 0% |
| Upfront cost, household | € (log scale) | 450 | 36,000 |
| Maintenance effort | tier 0–100 (0 = yearly check only, 100 = daily labour) | 0 | 100 |
| Environmental benefit | % of current emissions avoided | 100% | 0% |
| Reliability (new) | hours a day of service without backup | 23 | 4 |
| Site fit (new) | tier 0–100 (100 = at least twice the space needed, owned; 0 = does not fit) | 100 | 0 |

Kept for later (not households): upfront cost on a log scale for a micro-enterprise or
smallholder farm (€1,800–135,000), a small or medium business or commercial farm
(€18,000–900,000), and a community or mini-grid (€45,000–1,800,000).

### Weights per priority (each column 100%)

| Criterion | Lower upfront cost | Lower running costs | Lower environmental impact | Reliable supply | Balanced |
|---|---|---|---|---|---|
| Payback time | 12 | 18 | 8 | 8 | 12 |
| Annual savings | 6 | 25 | 7 | 7 | 10 |
| Share of demand covered | 5 | 7 | 6 | 5 | 5 |
| Upfront cost | 35 | 8 | 7 | 7 | 13 |
| Maintenance effort | 10 | 14 | 9 | 22 | 12 |
| Environmental benefit | 12 | 10 | 40 | 10 | 20 |
| Reliability | 10 | 10 | 12 | 30 | 13 |
| Site fit | 10 | 8 | 11 | 11 | 15 |

### Limits that exclude an option on their own

- Payback over 20 years: not recommended.
- Annual savings under 10% of the bills the user gave us: not recommended.
- Under 25% of the stated need: not a main option; may still be shown as an add-on.
- Upfront cost: no limit, unless the user states a budget.
- Maintenance tier 100 when no one could fix it: not recommended.
- Under 4 hours a day without backup: not recommended without storage.
- Does not fit the space or the tenure: excluded, before scoring.

## Decisions

1. **Five priorities again.** The expert filled in "most dependable" as a fifth column, so
   "Reliable supply" is back as an answer in the app (removed on 2026-11-12, see
   `2026-11-14-power-cuts-back.md` for the power cuts question).
2. **Everything the user tells us is what they want to replace** (the expert's rule). We only count
   that: savings, the 10% limit and the share covered are measured against the bills the user gave us.
   Example: gas stove and oven, electric lights and appliances at 200 kWh a month, only electricity
   to be replaced → we count the 200 kWh only.
3. **The energy source question says so.** "Where does the energy for these come from now?"
   with the line "Only choose what you would like to replace. Leave out what can stay as it is."
   The uses chosen earlier are shown above the answers, so "these" is clear.

## Still to do

- Our tiers for maintenance effort and site fit for each option (`[Judgement, WeShape]`, for the
  expert to confirm).
- What "hours a day without backup" means for each option (grid, solar without battery, etc.).
- The scoring code itself.
