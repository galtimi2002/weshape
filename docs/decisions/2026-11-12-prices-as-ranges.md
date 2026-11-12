# Prices: given as ranges in the final calculation

**Date:** 12 November 2026. **Agreed with:** Timi.

## Decision

- **No price research now:** equipment costs, electricity prices, feed-in tariffs and exchange rates
  are not researched or collected country by country.
- **In the final calculation** prices are given as **ranges** (low–high), in the user's currency.

## What this changes

- Removed from the "still missing" list: exchange rates, electricity prices and feed-in tariffs by
  country, and the missing equipment costs (solar water heater, small wind, hydro, biogas).
- `SelfConsumption` gives kWh used at home and kWh exported; the money value of each comes from the
  price ranges.

## Also decided

- **Power cuts are not calculated for now.** The mapping of the "power cuts" answer onto minutes
  per year is not done.
