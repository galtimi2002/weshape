# Prices: partner companies, otherwise a range

**Date:** 12 November 2026. **Agreed with:** Timi.

## Decision

- **No price research now:** equipment costs, electricity prices, feed-in tariffs and exchange rates
  are not researched or collected country by country.
- **In the final calculation** prices come from one of two places, in the user's currency:
  1. **Partner companies:** if WeShape has partner companies in the user's country, connected to the
     app, the prices are calculated from their prices, and the companies are shown to the user.
  2. **No partner data:** an approximate range (low–high), from the expert's global cost bands
     (section 11).
- The result says which of the two was used.

## What this changes

- Removed from the "still missing" list: exchange rates, electricity prices and feed-in tariffs by
  country, and the missing equipment costs (solar water heater, small wind, hydro, biogas).
- `SelfConsumption` gives kWh used at home and kWh exported; the money value of each comes from the
  prices above.

## Also decided

- **Power cuts are not calculated for now.** The mapping of the "power cuts" answer onto minutes
  per year is not done.
