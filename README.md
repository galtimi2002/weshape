# weshape-backend

REST API for **WeShape Energy**. Spring Boot 3.4, Java 21, PostgreSQL (Neon),
Flyway, Firebase Authentication. Same stack and conventions as Inventica.

## What it stores

One table, `users`:

| Column | Notes |
| --- | --- |
| `user_code` | Personal code like `WSE-K7M2-P9QR`, generated at sign-up, unique, never changes |
| `email` | From the verified Firebase token |
| `first_name`, `last_name` | Set at registration (or the "one more step" screen after Google sign-in) |
| `country` | ISO 3166-1 alpha-2 (`RO`, `HU`, ...) |
| `preferred_language` | One of 25: en hu ro de fr es it pt nl pl hr fi sv da el ru tr zh ja ko hi bn id vi ar |
| `theme_mode` | `SYSTEM`, `LIGHT` or `DARK` |

**Passwords are not in this database.** Firebase Authentication stores them,
salted and hashed; the backend only ever receives signed ID tokens.

## Endpoints

| Method | Path | Auth | |
| --- | --- | --- | --- |
| GET | `/api/v1/ping` | public | liveness |
| GET | `/api/v1/languages?country=HU` | public | supported languages, the country's starting language |
| GET | `/api/v1/users/me` | token | profile; the first call after sign-up creates it |
| PUT | `/api/v1/users/me` | token | complete the profile: `firstName`, `lastName`, `country`, optional `preferredLanguage`, `themeMode` |
| PUT | `/api/v1/users/me/preferences` | token | save `preferredLanguage` and/or `themeMode` |
| GET | `/api/v1/solar/point?lat=46.77&lon=23.59` | token | irradiation (GTI), PV output (PVOUT), optimum tilt (OPTA), monthly figures, from PVGIS; 404 over the sea |
| GET | `/api/v1/solar/layers` | public | map layers: bounds, units, colour scales |
| GET | `/api/v1/solar/layers/{gti,pvout,opta}.png` | public | one layer as a picture to lay over the map |
| GET | `/api/v1/wind/point?lat=46.77&lon=23.59` | token | mean wind speed at 10, 50, 100, 150, 200 m and power density at 100 m (Global Wind Atlas, 250 m); 404 over open sea |
| GET | `/api/v1/wind/layers` | public | wind map layers: units, colour scales, tile addresses, attribution |
| GET | `/api/v1/wind/tiles/{speed,power,height-10…height-200}/{z}/{x}/{y}.png` | public | map tiles (Web Mercator, 256 px) |

Leaving `preferredLanguage` out of the profile call sets it from the country
(HU → hu, AT → de, BR → pt, anything unmapped → en).

## Solar data

- **One place** (`/api/v1/solar/point`): asked live from
  [PVGIS](https://joint-research-centre.ec.europa.eu/photovoltaic-geographical-information-system-pvgis_en)
  (European Commission JRC): a 1 kWp system, 14 % losses, modules at the
  optimum tilt facing the equator. Yearly and monthly PV output (kWh/kWp),
  irradiation on the modules (kWh/m², i.e. GTI) and the optimum tilt. Answers
  are cached; over the sea the endpoint answers 404.
- **The map layers** (`/api/v1/solar/layers/*.png`): drawn from
  `src/main/resources/solar/pvgis-grid.csv.gz`, PVGIS values every 0.5° in
  Europe and every 2° elsewhere, interpolated in between.

The grid file is made once by `solar/SolarGridBuilder` (in IntelliJ:
right-click it, Run 'SolarGridBuilder.main()'). It needs the internet, takes
roughly 20 to 40 minutes and can be stopped and resumed. Commit the file it
writes. Until it exists the map falls back to invented example values
(`ExampleSolarResource`, marked `"example": true`).

**Keeping it current.** `.github/workflows/pvgis-refresh.yml` runs on the
3rd of every month (or from the Actions tab, "Run workflow"). It checks
whether PVGIS has a new API version or now serves newer data than the grid
was built from (the grid file's first line records both). If so it rebuilds
the grid on GitHub's machines and opens a pull request assigned to
galtimi2002 for review; nothing changes until it is merged. To rebuild
locally from scratch, delete `target/pvgis-grid.partial.csv` first.

If PVGIS cannot be reached, a place gets its values from the grid instead.
Set `PVGIS_ENABLED=false` to never call PVGIS (the tests do this).

## Wind data

From the [Global Wind Atlas](https://globalwindatlas.info) 4.0 (DTU and the
World Bank Group, CC BY 4.0), at 250 m for the whole world: mean wind speed at
10, 50, 100 and 200 m and mean power density at 100 m. 150 m is worked out
from 100 and 200 m (power law, 0.6 % mean error against the atlas).

The world files are 13–15 GB each, too big for the repository or the server.
The **"Build wind map data"** workflow (`.github/workflows/gwa-wind-build.yml`,
run by hand from the Actions tab) reads them from the atlas, stores each value
in one byte (speed in 0.1 m/s, power density on a log scale) as a
cloud-optimised GeoTIFF with overviews (about 1 GB per layer), and uploads them
to the private Cloudflare R2 bucket `weshape-wind` under `wind/gwa/`.
It needs the repository secrets `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`,
`R2_SECRET_ACCESS_KEY` and `R2_BUCKET`. One layer takes about 40 minutes;
pass a subset (for example `ws100`) to rebuild only that.

Power density's world file sits on a slow server (one connection, about
0.5 MB/s), so that layer is put together from the atlas' per-country files
(266 countries, same 250 m grid) instead.

**Map tiles** are drawn in advance by the **"Build wind map tiles"** workflow
(`.github/workflows/gwa-wind-tiles.yml`, run after the data build): zoom 0 to 8
for the seven map layers (speed, power, height-10 … height-200), about 35,000
tiles and 330–390 MB per layer. Each layer is two objects in R2,
`wind/tiles/<layer>.tiles` (all tiles one after another) and
`wind/tiles/<layer>.index` (where each one is), because uploading tens of
thousands of small objects took longer than drawing them. The map enlarges
zoom 8 beyond that. A layer without stored tiles is drawn on the server
instead (slowly, a few seconds a tile on Render's free plan).

The backend reads only what it needs with signed range requests: the file
headers once (read at startup), then the 512 × 512 blocks around a point, from
all five files at once (`wind` package, no AWS SDK). Tiles are cached in memory
and by browsers for a week.

In R2 this takes about 5.9 GB for the data and 2.7 GB for the tiles, under
the 10 GB free tier.
Without bucket settings `/api/v1/wind/layers` reports `available: false` and
the other wind endpoints answer 503. For development, `WIND_LOCAL_DIR` can
point to a folder holding the same files (`wind/gwa/ws100.tif` ...).

## Run locally

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# fill in the Neon connection string, keep auth mode "dev" for now
./mvnw spring-boot:run
```

With no profile given, the `local` profile is used, so the green Run button
in IntelliJ works the same way.

In dev mode any `Authorization: Bearer dev:<uid>:<email>` token is accepted:

```powershell
curl.exe http://localhost:8080/api/v1/users/me -H "Authorization: Bearer dev:demo:demo@weshape.energy"
```

Swagger UI: http://localhost:8080/docs

## Tests

```bash
./mvnw verify
```

Integration tests run on in-memory H2 with dev tokens: account creation and
the unique code, language from country, preferences persisting, validation,
and account linking by verified email.

## Deploy (Render)

`render.yaml` describes the service. Environment variables to fill in:

- `DATABASE_URL`: Neon connection string (`postgres://...` is fine)
- `FIREBASE_PROJECT_ID`
- `FIREBASE_CREDENTIALS_BASE64`: the service-account JSON, base64 encoded:
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("serviceAccount.json"))`
- `CORS_ALLOWED_ORIGINS`: the web app's address, e.g. `https://weshape-app.onrender.com`
- `WIND_R2_ACCOUNT_ID`, `WIND_R2_ACCESS_KEY_ID`, `WIND_R2_SECRET_ACCESS_KEY`
  (and `WIND_R2_BUCKET` if not `weshape-wind`): a **read-only** R2 token for
  the wind map, separate from the upload token GitHub uses.

## Before launch

- **Data disclaimer and attribution**: see "Before launch" in the
  weshape-app README (PVGIS credit, terms of service, legal check).
- **Wind attribution**: the Global Wind Atlas licence (CC BY 4.0) needs the
  credit shown wherever the wind map or its numbers appear (the app shows it
  in the map attribution).
- **PVGIS is a free public service** with no guaranteed capacity (at most 30
  requests a second per address). Answers are cached in memory; if usage
  grows, keep a persistent cache or ask the PVGIS team (JRC-PVGIS@ec.europa.eu).
- **Commit `src/main/resources/solar/pvgis-grid.csv.gz`** with the code, or
  the map shows example values.
- **GitHub settings** for `.github/workflows/pvgis-refresh.yml`:
  Settings → Actions → General → "Allow GitHub Actions to create and approve
  pull requests".
