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
| `DonorScreeningScreen` | Hub → Wellness Check | `POST …/donors/{id}/pre-screening`, Nompilo assist |

Service module: `citizen-app/src/services/madiService.ts`

Base: `/internal/v1/mobile/citizen/madi`

Session-scoped mobile paths (`/register`, `/profile`, `/history`, etc.) resolve donor context from `X-Actor-ID`.

## Mobile — Provider (`apps/mobile/provider-app`)

| Screen | Path in app | BFF endpoints |
|--------|-------------|---------------|
| `MadiOrdersScreen` | Clinical Tools → **Blood Orders** | `GET/POST …/orders`, `POST …/orders/{id}/submit` |
| `MadiTransfusionScreen` | Clinical Tools → **Transfusion** | `GET/POST …/transfusions`, observations, complete |
| `MadiDriveCaptureScreen` | Clinical Tools → **Blood Drives** | `GET …/drives`, screen, donations |
| `MadiReactionReportScreen` | Clinical Tools → **Haemovig.** | `POST …/haemovigilance/reactions` |

Service module: `provider-app/src/services/madiService.ts`

Base: `/internal/v1/mobile/provider/madi`

## Web — Live (`ui/one-ui-shell`)

| Route | Capability |
|-------|------------|
| `/madi/donor/*` | Donor self-service + Nompilo pre-screening |
| `/madi/drives/*` | Drive planning and field ops |
| `/madi/orders/*` | Blood order workbench + OROS deep-link |
| `/madi/transfusion/*` | Ward transfusion + VITO bedside verify |
| `/madi/blood-bank/*` | Stock, issue, crossmatch |
| `/madi/blood-bank/fridges` | IoT cold-chain monitoring |
| `/madi/haemovigilance` | Facility reaction reporting |
| `/madi/haemovigilance/national` | National supervisory roll-up |
| `/madi/dashboard` | Programme KPIs |

Web client: `useMadi.ts` + `madi.ts`

## Parity matrix reference

Regenerate: `node scripts/frontend/generate-parity-docs.mjs`

MADI capabilities are registered in `scripts/frontend/generate-parity-docs.mjs` with mobile parity marked **partial** for donor engagement, drives, orders, transfusion, and haemovigilance.
