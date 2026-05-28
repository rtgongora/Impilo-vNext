# Doctrine Alignment Checklist (Frontend Surfaces)

Legend:

- **Journey:** Person, Provider, Platform, Cross-cutting
- **Maturity:** Live, Partial, Fixture, Not Wired

## Global checklist gates (must pass for Complete)

- [ ] Person-first identity context declared and rendered
- [ ] Journey and seven-plane mapping declared
- [ ] BFF endpoint(s) and contract references declared
- [ ] Trust/context headers required by flow are available
- [ ] Loading/error/empty/provisional/reconciliation states implemented
- [ ] No fake-success paths (maturity label present when not live)
- [ ] Test/runtime validation coverage updated

## Web surfaces (route families)

| Surface | Journey | Plane mapping | Service/contract anchor | Trust/context anchor | Maturity |
|---|---|---|---|---|---|
| `/home/*`, `/citizen/*`, `/wellness/*`, `/monitoring/*` | Person | Experience, Clinical, Data & Intelligence | Experience BFF citizen routes | v1.2 web headers in `api-client.ts` | Partial |
| `/discover/*`, `/marketplace/*`, `/wallet` | Person | Experience, Registry, Enterprise | Marketplace/discovery/wallet BFF routes | actor + purpose + facility/workspace | Partial |
| `/telemedicine/*`, `/scheduling`, `/queue-status` | Person | Experience, Clinical, Integration | Telemedicine and queue BFF routes | actor + purpose + session context | Partial |
| `/social`, `/communities`, `/groups`, `/pages` | Person | Experience, Data & Intelligence | Social service routes | actor + moderation context | Partial |
| `/queue/*`, `/ehr/[patientId]/*`, `/clinical*` | Provider | Clinical, Trust, Experience | Provider BFF and clinical contracts | provider activation + duty context headers | Partial |
| `/lab/*`, `/pharmacy/*`, `/communication/*` | Provider | Clinical, Integration, Experience | Domain BFF integrations | facility/workspace/shift + purpose | Partial |
| `/operations/*`, `/reports/*`, `/intelligence/*` | Platform | Data & Intelligence, Enterprise, Experience | Ops/reporting BFF routes | role + governance context | Partial (workflow/dispatch telemetry cards standardized; definitions/instances, dispatch backend datasets, workflow instance commands, dispatch task commands, and delivery commands surfaced) |
| `/finance/*`, `/enterprise/*`, `/registry/*` | Platform | Enterprise, Registry, Trust | finance/registry BFF compositions | regulated context + audit headers | Partial (Registry Hub and mobile registry surfaces now expose guided identity commands plus facility lifecycle, locality, intake/import, product, terminology, and trust/consent BFF surfaces; Coverage now exposes guided live eligibility/member/claim/preauth/appeal submit-review-decision commands; payer-ops composes claims/remittance with intent-linked attempts/receipts/settlement/refund state) |
| `/core-transaction`, `/client-journey`, `/provider-workspace`, `/platform-journey` | Cross-cutting | Experience, Trust, Enterprise | Doctrine journey surfaces | trust context banner present, live BFF-only composition with explicit loading/error/empty states, URL-synced ops filters on platform + provider journey routes | Partial |
| `/ask` (Nompilo) | Cross-cutting | Experience, Data & Intelligence | LLM orchestration routes | actor + purpose + policy context | Partial |

## Citizen mobile surfaces

| Surface | Journey | Plane mapping | Service/contract anchor | Trust/context anchor | Maturity |
|---|---|---|---|---|---|
| Tab `home` | Person | Experience, Registry | `/internal/v1/mobile/citizen/*` summary/discovery | mobile trust + tenant/actor/purpose | Partial |
| Tab `personal` | Person | Clinical, Registry, Trust, Experience | citizen personal/records/contracts, `/internal/v1/identity/*` recovery/resolve | mobile trust + facility/workspace when present | Partial (ID Recovery now exposes live identity search, resolve, recovery start, and recovery verify) |
| Tab `social` | Person | Experience, Data & Intelligence | `/internal/v1/social/*`, citizen social routes | actor + purpose + moderation context | Partial |
| Tab `marketplace` | Person | Enterprise, Experience | marketplace and launcher routes | actor + purpose + request context | Partial |
| Tab `messaging` | Person | Experience, Integration | messaging routes | actor + correlation + request headers | Partial |
| Tab `public_health` | Person | Data & Intelligence, Experience | public health citizen routes | actor + purpose (public health/treatment) | Partial |
| Tab `telehealth` (deep-link) | Person | Clinical, Integration, Experience | telehealth citizen routes | actor + purpose + session context | Partial |
| Global `NompiloAssistantScreen` | Cross-cutting | Experience, Data & Intelligence | `/internal/v1/llm/chat` | actor + purpose + policy context | Partial |
| `NhumeTrackingScreen` | Person | Integration & Edge, Experience | nhume delivery/tracking routes | actor + facility/context as available | Partial |
| `ProviderDiscoveryScreen` | Person | Registry, Experience | `/internal/v1/mobile/citizen/services/discover` | mobile trust headers available | Partial (now reachable in personal section) |

### Citizen personal sections

All personal sections inherit person-first and trust requirements from the `personal` tab shell and must declare live status per endpoint:

profile, health-id, allergies, conditions, immunizations, referrals, care-plans, appointments, prescriptions, results, records, reminders, timeline, wellness, finance, challenges, programs, wallet, monitoring, queue, sos, coverage (now includes payer reconciliation and durable provisional command queue), consent, comms-prefs, support, settings, assessments, care-team, id-recovery, record-sharing, claim, verify, delegated-pickup, nhume-track, privacy, terms.

## Provider mobile surfaces

| Surface | Journey | Plane mapping | Service/contract anchor | Trust/context anchor | Maturity |
|---|---|---|---|---|---|
| Mode `provider` | Provider | Clinical, Experience, Trust | `/internal/v1/mobile/provider/*` | provider/facility/workspace/shift | Partial |
| Mode `outreach` | Provider | Public Health, Experience, Integration | outreach/household routes | provider + purpose + location context | Partial |
| Mode `supervisor` | Platform/Provider | Enterprise, Experience | supervisor ops routes | duty context + governance headers | Partial |
| Mode `offline` | Provider | Integration & Edge, Trust, Experience | offline queue/conflict flows | trust on replay + correlation integrity | Partial |
| Mode `courier` | Provider | Integration & Edge, Enterprise, Experience | nhume delivery routes | actor + facility + delivery context | Partial |
| Tab `dashboard` | Provider | Experience | provider dashboard APIs | provider-activated context | Partial |
| Tab `patients` | Provider | Registry, Clinical | lookup/patient routes | provider + purpose + facility | Partial |
| Tab `encounter` | Provider | Clinical, Trust | encounter workflows | provider + shift + purpose | Partial |
| Tab `results` | Provider | Clinical, Data & Intelligence | results/lab APIs | provider + patient context | Partial |
| Tab `queue` | Provider | Clinical, Experience | queue and triage endpoints | provider + facility/workspace | Partial |
| Tab `messaging` | Provider | Experience, Integration | provider messaging routes | actor + request/correlation | Partial |
| Tab `social` | Provider | Experience, Data & Intelligence | provider social routes | role-aware social context | Partial |
| Tab `tools` | Provider | Multi-plane | clinical tools orchestration, `/internal/v1/identity/*`, `/internal/v1/facility-registry/*`, `/internal/v1/registry-intake/*`, `/internal/v1/registry/localities/*`, `/internal/v1/product-registry/*`, `/internal/v1/registry/zibo/*`, `/internal/v1/admin/trust/*` | full duty context | Partial (Flow/Ops now exposes workflow/dispatch read + command parity controls; Admin & Registry now exposes live registry/admin operations across identity, facility lifecycle, locality, intake/import, products, terminology, and trust/consent reads) |
| Tab `apps` | Provider | Enterprise, Experience | Health OS apps launcher | role and maturity aware | Partial |
| Tab `professional` | Provider | Trust, Registry, Experience | profile/settings routes | actor/provider context | Partial |

### Provider tools sub-surfaces

soap, telemedicine, drugs, orders, care, mar, cds, paging, barcode, workspaces, inpatient, facility, reports, finance, schedule, pharmacy, lab, marketplace, admin, ops_reports, developer_hub, prof_settings, prof_channels, learning, core_transaction, workflow_dispatch, ph_field_tasks.

Each sub-surface is currently `Partial` unless explicitly marked live by service-level runtime evidence.

## Cross-surface doctrine exceptions requiring closure

- Doctrine route families are now live-BFF only, but still need deeper journey-specific action coverage before complete.
- Remaining implemented-but-not-primary surfaces must be either linked, deprecated, or documented with explicit ownership.
- Offline reconciliation and event timeline visibility are improving (platform now surfaces workflow/dispatch timelines; coverage commands now have citizen/provider mobile durable queues, retry history, and payer reconciliation panels) but not yet uniform across all surfaces.
