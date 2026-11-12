# Prices: partner companies, otherwise a range

**Date:** 12 November 2026. **Agreed with:** Timi.

## Decision

- **No price research now:** equipment costs, electricity prices, feed-in tariffs and exchange rates
  are not researched or collected country by country.
- **In the final calculation** prices come from one of two places, in the user's currency:
  1. **Partner companies:** if WeShape has partner companies in the user's country, connected to the
     app, the prices are calculated from their prices, and the companies are shown to the user.
  2. **No partner data:** an approximate range (low–high) per country, collected automatically
     from the internet **once a year** and stored with the other data (R2). It goes through
     automatic checks before upload, like every data table.
  3. **Not enough found for a country:** the expert's global cost bands (section 11).
- The result says which one was used, with the year of the data.
- **Not live:** prices are not fetched while the user waits; that would be slow, can fail, and
  would give a different result for the same home from one day to the next.

## How prices are shown (13 November 2026)

- **Calculated in euros, shown in the user's currency.** Always a range; average exchange rates are
  enough, no exact currency arithmetic is needed.
- **Labour at the country's average level.**
- **Split in two:** the result shows separately
  - **equipment:** what the system itself costs;
  - **installation (labour):** what the work costs;
  - and the total.
  Someone who can do the work can see what it costs them without paying for the labour.
- **When the work must be done by a professional** (for example, connecting rooftop solar to the grid
  needs a licensed electrician in many countries; heat pump refrigerant work needs a certified
  installer in the EU), the result says so next to the labour part. To be filled in per option and
  country with the country table.
- **To ask the expert:** the share of labour in the price, per option.

## What this changes

- Removed from the "still missing" list: exchange rates, electricity prices and feed-in tariffs by
  country, and the missing equipment costs (solar water heater, small wind, hydro, biogas).
- `SelfConsumption` gives kWh used at home and kWh exported; the money value of each comes from the
  prices above.

## Also decided

- **Power cuts:** the question is removed from the app; see
  `2026-11-12-question-notes-and-climate-grid.md`, section 3.
