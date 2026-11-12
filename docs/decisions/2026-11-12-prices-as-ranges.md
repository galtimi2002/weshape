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

## What this changes

- Removed from the "still missing" list: exchange rates, electricity prices and feed-in tariffs by
  country, and the missing equipment costs (solar water heater, small wind, hydro, biogas).
- `SelfConsumption` gives kWh used at home and kWh exported; the money value of each comes from the
  prices above.

## Also decided

- **Power cuts are not calculated for now.** The mapping of the "power cuts" answer onto minutes
  per year is not done.
