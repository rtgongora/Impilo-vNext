# DEV-only Zimbabwe admin CSV fixtures

**These files are not authoritative.** They exist solely so developers can populate a local Tuso database and exercise district/ward pickers without a licensed COD-AB or ZIMSTAT extract.

- Codes and names are **synthetic** (`DEV-*` prefixes, `impilo:dev-fixture:v1` in `sourceRef`).
- **Do not** ship these rows to production or present them as national truth.
- Replace with **COD-AB / ZIMSTAT–aligned** CSVs before any real deployment.

## Files

| File | Purpose |
|------|---------|
| `zimbabwe-districts.DEV-ONLY.sample.csv` | Two demo districts (Harare / Bulawayo province codes from `zimbabwe-provinces.seed.json`). |
| `zimbabwe-wards.DEV-ONLY.sample.csv` | Demo wards for those districts. |

## Example import (Tuso direct)

From repo root (with Tuso running):

```bash
node scripts/registry/import-zw-admin-csv.mjs ^
  --tusoUrl=http://localhost:8084 ^
  --districts=scripts/registry/fixtures/zimbabwe-districts.DEV-ONLY.sample.csv ^
  --wards=scripts/registry/fixtures/zimbabwe-wards.DEV-ONLY.sample.csv
```

Use the same paths on macOS/Linux without `^` line continuations.
