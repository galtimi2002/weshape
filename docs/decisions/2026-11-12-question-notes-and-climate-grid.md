# Short "what we take into account" notes; climate grid finer in the mountains

**Date:** 12 November 2026. **Agreed with:** Timi.

## 1. A short note under the questions

- **What:** under the questions, one short line on what we take into account with the answer, so the
  user sees how carefully we calculate. Example under the place question: "We also take into
  account how cold it is where you live: how much heating it needs."
- **Why:** it makes the user curious, shows how precise the calculation is, and explains why we ask.
- **Same idea as** the why-note agreed for firewood (`2026-11-12-firewood-and-missing-data.md`).
- **Rules:** one sentence, plain words, no numbers or sources; in all 25 languages, Hungarian as the
  reference meaning.
- **Status:** built in the app (13 November 2026): a sparkle and one line under the title of 19
  steps, in all 25 languages (keys `note…`). No note on the bills paid and the "keep free" steps.
  The Hungarian texts (reference meaning):

| Step | Note |
|---|---|
| Kinek készül a felmérés? | Ehhez igazítjuk, milyen megoldásokat mutatunk és mekkora méretben. |
| Hol van? | Figyelembe vesszük, mennyire hideg vagy meleg a környék, mennyi ott a napsütés és a szél. |
| Mire szeretnéd használni az energiát? | Csak azokat a megoldásokat számoljuk, amelyek erre valóban jók. |
| Épület (típus, méret, hányan laktok ott) | A ház méretéből és abból, hányan laktok ott, számoljuk ki, mennyi energia kell fűtésre, melegvízre és főzésre. |
| Mikor épült? | Az építés éve sokat elárul arról, mennyi hőt veszít az épület. |
| Felújították azóta? | A felújítás sokat csökkenthet a fűtési igényen, ezt beszámoljuk. |
| Mennyire szigetelt? | Minél jobb a szigetelés, annál kevesebb fűtés kell, és annál jobban működik egy hőszivattyú. |
| Milyen ablakai vannak? | Az ablakokon át megy el a hő jelentős része, ezt is beszámoljuk. |
| Honnan kapsz most energiát? | Ehhez hasonlítjuk, mennyit spórolhatsz, és mennyivel kevesebb lesz a kibocsátás. |
| Mennyi áramot használtál az elmúlt 3 hónapban? | Az évszakot is figyelembe véve ebből becsüljük meg az egész éves fogyasztást. |
| Mennyi gázt használtál, és mennyibe került? | Ebből tudjuk, mennyit fűtesz most, és mennyibe kerül neked. |
| Tűzifa, pellet, palack, távfűtés | Ebből számoljuk ki, mennyi hőt kapsz most, és mennyibe kerül neked. |
| Hol van szabad helyed? | Csak oda tervezünk, ahol valóban elfér, és ahol a szabályok is engedik. |
| Mekkorák ezek a helyek? Merre néz? | A méretből és az irányból számoljuk ki, mennyi napelem fér el, és mennyi napot kapnak. |
| A tiéd ez a hely? | Ha bérelsz, csak olyat javaslunk, amihez nem kell a tulajdonos engedélye. |
| Ki javítaná meg? Mennyi kétkezi munka? | Ha nem szeretnél vele foglalkozni, kihagyjuk a sok gondozást igénylő megoldásokat. |
| Mi a legfontosabb neked? | Ez alapján rangsoroljuk a megoldásokat: kevés kezdő pénz, alacsony számlák, a környezet kímélése, vagy mindenből egy kicsit. |
| Még néhány kérdés a helyedről (folyó, szél, állatok) | A közeli folyó, a szél és az állatok alapján azt is megnézzük, megéri-e a víz, a szél vagy a biogáz. |
| Van a helyeden valami új, ami még nincs a térképen? | Egy új épület, egy fészer vagy egy kinőtt fa is megváltoztathatja, mennyi energiát nyerhetsz. Jelöld be, és a valódi helyzeteddel számolunk. |

## 2. Climate data (degree-days): finer where the land is uneven

- **What:** degree-days (how cold a place is over a year, i.e. how much heating it needs) are stored
  on a grid over the map; the app uses the cell the home is in.
- **Decision:** the precision depends on the land:
  - **plains:** a medium grid is enough (about 28–55 km cells); cold changes little there;
  - **mountains:** more precise, because it is about 0.65 °C colder every 100 m up. Done in two ways:
    the value is corrected by the home's height above sea level, and, if checks show that this is not
    enough, smaller cells are used where the land is uneven.
- **Size:** a few MB in total (see the storage estimate given to Timi: all refreshed data under
  about 10 MB with one backup copy).
- **Status:** not built; part of the data refresh work.

## 3. The power cuts question is removed

- **Decision:** the question "Milyen gyakran megy el az áram?" is taken out of the app completely
  (step, answer field, texts in all 25 languages). Power cuts are not part of the calculation.
- **Backend:** `Battery` (expert section 4) stays in the code, unused by the assessment, in case it
  is needed later.
- **The "reliable supply" priority is removed too** ("Megbízható ellátás"). Four answers remain:
  lower upfront cost, lower running cost, lower environmental impact, balanced.
