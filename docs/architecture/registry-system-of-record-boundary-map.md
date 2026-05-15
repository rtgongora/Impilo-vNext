# Registry System-of-Record Boundary Map

Date: 2026-05-14

## Canonical Registry SoR Boundaries

| Service | Primary SoR Responsibility | Must Not Own |
|---|---|---|
| `vito-service` | person/client sovereign identity, health-id lifecycle, identity alias/recovery workflows | provider credential authority, facility operational SoR, billing/claims ledgers |
| `varapi-service` | provider identity, licensure/professional standing, provider privileges | patient/client SoR, facility registry SoR, financial settlement authority |
| `tuso-service` | facility/workspace registry and operational facility context | patient identity SoR, provider licensure SoR, terminology authority |
| `zibo-service` | terminology/classification authority and versioned terminology governance | patient/provider/facility primary records, financial product operations |
| `ubomi-service` | CRVS-aligned civil status integration boundary (birth/death event registry intake) | general-purpose patient SoR replacement, provider/facility/catalog SoR |
| `indawo-service` | non-facility site/premises registry for place classification and site snapshots | core facility/workspace operational SoR already owned by `tuso-service` |
| `msika-service` | product/catalog registry authority for item master, mappings, and pack composition | marketplace transaction settlement, wallet/billing SoR |
| `product-registry-service` | product/service registry search/snapshot authority for internal consumers | duplicate marketplace operations already owned elsewhere |

## Boundary Notes

- Registry services can consume each other for enrichment/validation, but must not duplicate SoR writes outside ownership.
- Cross-plane services may cache references but cannot become authoritative for registry identities.
- Reconciliation flows must preserve authoritative write ownership in the owning registry service.
