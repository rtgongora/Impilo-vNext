# MADI Frontend Routes

## Mobile — Citizen (`apps/mobile/citizen-app`)

| Screen | Path in app | BFF endpoints |
|--------|-------------|---------------|
| `MadiDonorHubScreen` | Personal → **Blood Donor** | Hub navigation |
| `BecomeDonorScreen` | Hub → Become a Donor | `POST …/register` |
| `DonorProfileScreen` | Hub → My Donor Profile | `GET …/profile`, `GET …/next-eligibility`, `PUT …/preferences` |
| `DonationDrivesScreen` | Hub → Donation Drives | `GET …/drives/near-me`, `POST …/drives/{id}/register` |
| `DonorHistoryScreen` | Hub → Donation History | `GET …/history` |
| `DonorFeedbackScreen` | Hub → Give Feedback | `POST …/feedback` |

Service module: `citizen-app/src/services/madiService.ts`

Base: `/internal/v1/mobile/citizen/madi`

## Mobile — Provider (`apps/mobile/provider-app`)

| Screen | Path in app | BFF endpoints |
|--------|-------------|---------------|
| `MadiOrdersScreen` | Clinical Tools → **Blood Orders** | `GET/POST …/orders`, `POST …/orders/{id}/submit` |
| `MadiTransfusionScreen` | Clinical Tools → **Transfusion** | `GET/POST …/transfusions`, observations, complete |
| `MadiDriveCaptureScreen` | Clinical Tools → **Blood Drives** | `GET …/drives`, screen, donations |
| `MadiReactionReportScreen` | Clinical Tools → **Haemovig.** | `POST …/haemovigilance/reactions` |

Service module: `provider-app/src/services/madiService.ts`

Base: `/internal/v1/mobile/provider/madi`

## Web — Planned (`ui/one-ui-shell`)

| Route (planned) | Capability |
|-----------------|------------|
| `/madi/donors` | Donor registry search (operator) |
| `/madi/drives` | Drive planning and publish |
| `/madi/orders` | Blood order workbench |
| `/madi/transfusions` | Ward transfusion console |
| `/madi/stock` | Blood bank inventory |
| `/madi/haemovigilance` | Reaction queue and cases |
| `/madi/dashboards` | Programme KPIs |

Web clients (planned): `useMadiDonor.ts`, `useMadiOrders.ts`, etc.

## Parity matrix reference

Regenerate: `node scripts/frontend/generate-parity-docs.mjs`

MADI capabilities are registered in `scripts/frontend/generate-parity-docs.mjs` with mobile parity marked **partial** for donor engagement, drives, orders, transfusion, and haemovigilance.
