# CSV templates — Zimbabwe Admin-2 / Admin-3

These files are **column headers plus optional `#` comment lines**. Copy to a working file, fill rows from **COD-AB** or **ZIMSTAT** extracts, then run `scripts/registry/import-zw-admin-csv.mjs`.

| Template | Columns |
|----------|---------|
| `zimbabwe-districts.template.csv` | provinceCode, districtCode, name, sourceRef |
| `zimbabwe-wards.template.csv` | districtCode, wardCode, name, sourceRef |

The import script **ignores** lines that start with `#`.

## DEV-only samples

For local UI demos only (synthetic `DEV-*` codes), see `scripts/registry/fixtures/README-DEV-ONLY.md`.
