# COSTA — tariff library UI coverage

## Required groups (product)

| Group | Seeded? | Shown in UI? | Notes |
|-------|----------|--------------|--------|
| Default / demo (Zimbabwe PoC v0.1) | V007 | Y — **Default / demo** section | Disclaimer inline |
| WHO references (CHOICE, ICHI) | V007 | Y — **WHO references** | Reference-only banner context |
| International refs (NHS, MBS, CMS) | V007 | Y — **International reference tariffs** | Reference-only |
| Country / provider / payer / donor / programme / org uploads | V010 (demo channel metadata) | Y — under **Uploaded / operational-style** by `upload_channel` | Replace with real tenant uploads in ops |
| Retired / historical | V010 | Y — **Retired / historical** | `effective_to` + `retired` metadata |

## Fields per row

- Name, code, tariff type & family, operational vs reference, billing approval, currency, effective from/to, validation / official status, provenance (metadata).

## Warnings

- **Global:** `REFERENCE_TARIFF_WARNING` (see `shared-ui` export).
- **Zimbabwe PoC:** `ZIMBABWE_POC_DISCLAIMER` for external code `ZW-PLACEHOLDER-POC-V0.1`.

## Behaviours

- **Reference-only** lists: displayed with amber **Reference only** chip; `approved_for_billing: false` shown.
- **Operational billing** is **blocked** server-side for reference lists when posting invoice/claim (`assertCanBill`).

## Tests

- `ui/experience/src/lib/finance/tariff-library-groups.test.ts` — classification rules.
- `ui/experience` + `ui/one-ui-shell` `finance/tariffs/page.test.tsx` — renders COSTA list payload.

## Legacy

- `GET /internal/v1/finance/tariffs` remains as **secondary** table for `TariffEntity` imports.
