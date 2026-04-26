# Zimbabwe administrative seeding

## Doctrine

- **Admin 1–3** (province, district, ward): seed from **COD-AB** and/or **ZIMSTAT** census boundary products — **never** hand-invent ward lists.
- **Locality / village / suburb / growth point**: governed by **Tuso Locality Gazetteer** APIs — UI loads suggestions from Tuso, not static JSON in the web bundle.

## Files

| File | Status |
|------|--------|
| `zimbabwe-provinces.seed.json` | Ten provinces — **verify PCODEs** against COD-AB before production. |
| `data/zimbabwe-districts.seed.json` | Empty until import pipeline runs. |
| `data/zimbabwe-wards.seed.json` | Empty until import pipeline runs. |

## Zimbabwe ISO country row

Canonical: **Zimbabwe** — alpha-2 `ZW`, alpha-3 `ZWE`, numeric **716** (see `countries.iso3166-1.json`).
