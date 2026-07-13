# System of Record Map

| Service ID | Primary plane | System-of-record responsibilities |
|---|---|---|
| `ai-model-registry-service` | data | Ai Model Registry canonical records |
| `analytics-pipeline-service` | integration | Analytics Pipeline canonical records |
| `asset-registry-service` | integration | Asset Registry canonical records |
| `audit-ledger-service` | integration | Audit Ledger canonical records |
| `booking-service` | experience | booking transaction container, appointment scheduled events, booking-to-appointment conversion lifecycle, Mvumo-gated booking orchestration links |
| `butano-fhir` | clinical | Butano Fhir canonical records |
| `butano-service` | clinical | Butano canonical records |
| `campaigns-service` | data | public-health campaign definitions, campaign outreach plans and schedules, campaign execution state and coverage metrics |
| `card-print-agent` | integration | Card Print Agent canonical records |
| `channels-service` | integration | Channels canonical records |
| `clinical-knowledge-platform-service` | clinical | Clinical Knowledge Platform canonical records |
| `community-service` | experience | Community canonical records |
| `connector-fhir-adapter` | integration | Connector Fhir Adapter canonical records |
| `costing-engine-service` | enterprise | Costing Engine canonical records |
| `coverage-service` | enterprise | Coverage canonical records. **Subsidy dual-lane (G3):** two distinct, both-live subsidy enrolment models share `cv_subsidy_programs`. **Model X** — `cv_subsidy_enrolments` + `cv_subsidy_balances` + `cv_subsidy_drawdowns` (V010/V011): the *value / annual-cap money lane*, keyed by `member_cpid`, with atomic cap-enforced drawdown; SoR for subsidy value consumption. **Model Y** — `cv_subsidy_enrollments` (V012, note the double-L): the *exemption-category / billing-classification lane*, keyed by `client_id`, carries `exemption_category`; SoR for costing waivers (consumed by `CoveragePlanController` patient-category). No data bridge exists between them. A physical merge would repoint a live consumer and cross an identifier boundary (`member_cpid` ≠ `client_id` space) — deferred to an architecture decision; do NOT wire the wrong lane (the `enrolment`/`enrollment` near-homonym is the trap). |
| `credential-verification-service` | enterprise | Credential Verification canonical records |
| `daidzai-service` | experience | Daidzai canonical records — emergency_request / emergency_incident / mission status timeline / resource_request / affected_site. Orchestrates (does NOT own) dispatch (Nhume), maps/routing (Ndila), clinical encounter/record (PCT/Butano), comms (Khuluma), after-action (Rito). |
| `data-access-governance-service` | data | Data Access Governance canonical records |
| `data-governance-service` | data | Data Governance canonical records |
| `data-ingestion-service` | data | Data Ingestion canonical records |
| `data-pipeline-service` | data | Data Pipeline canonical records |
| `data-warehouse-service` | data | Data Warehouse canonical records |
| `developer-portal-service` | integration | Developer Portal canonical records |
| `dispatch-service` | integration | Dispatch canonical records |
| `document-service` | clinical | Document canonical records |
| `experience-bff` | experience | Experience Bff canonical records |
| `fhir-gateway-service` | clinical | Fhir Gateway canonical records |
| `forms-service` | clinical | Forms canonical records; **encounter form definitions + immutable versions** (clinical DAK metadata). Must not own form responses or clinical encounters — those are PCT. |
| `general-ledger-service` | enterprise | General Ledger canonical records |
| `guidance-service` | clinical | Guidance canonical records |
| `hr-payroll-service` | enterprise | Payroll-financial: employees, contracts, deductions, payroll runs, payslips, earnings. The `hr.employees` row (incl. `employment_status`) is **payroll-financial only**; **employment trust is `workforce-governance-service`'s** (`wgv_hsc_employment.employment_status`) — hr-payroll's copy is unsynced and must never be read as a trust/eligibility signal. **Workforce attendance + leave are Vashandi's** — payroll derives worked-hours from Vashandi (no separately-entered attendance). |
| `identity-assurance-service` | trust | Identity Assurance canonical records |
| `indawo-service` | registry | Indawo canonical records |
| `inpatient-service` | clinical | Inpatient canonical records |
| `integration-hub` | integration | Integration Hub canonical records |
| `inventory-elmis-adapter` | clinical | Inventory Elmis Adapter canonical records |
| `inventory-service` | clinical | Inventory canonical records — **this is "Dura"** (the doctrine name for stock / commodity / storehouse / supply truth). There is no separate `dura-service`; do not create one. Marketplace/wellness/care flows that reference "Dura" reserve/issue through `inventory-service` (8098) + `msika-flow-service` reservations. |
| `iot-ingestion-service` | integration | Iot Ingestion canonical records |
| `jobs-service` | integration | Jobs canonical records |
| `khuluma-service` | experience | Khuluma Comms Hub: unified conversation index, participants, messages + read receipts, presence, conversation↔canonical-object links, escalation/SLA, and the realtime push gateway (SSE + WebSocket). **Reuses** channels-service (channel sessions/messages), notification-service (notification inbox/templates/delivery/providers), live-service (meetings/webinars), rtc-gateway-service + LiveKit (call/meeting media), pct-service (teleconsult) — must not duplicate any of these. **G31 resolved:** khuluma's `DeliveryService` now delegates external channels (SMS/WhatsApp/EMAIL/USSD) to notification-service via `POST /internal/v1/notify` (`NotificationDeliveryClient`); its `ChannelAdapterEntity` provider config is retained as legacy/advisory only, not a delivery gate. IN_APP stays khuluma-native. Follow-up (operator/config): register the canonical khuluma delivery templateKeys in notification-service — until then a no-templateKey external dispatch records `SKIPPED_NO_TEMPLATE` (honest, no fabricated send). |
| `landela-adapter-service` | integration | Landela Adapter canonical records |
| `learning-service` | experience | Learning canonical records |
| `live-service` | experience | live events and webinars, live event registrations, live event attendance, live event interactions, live event certificates, live event analytics snapshots |
| `llm-orchestration-service` | integration | Llm Orchestration canonical records |
| `madi-service` | clinical | blood donor registry, donation drives, blood units, crossmatch, transfusion episodes, haemovigilance |
| `msika-flow-service` | enterprise | Msika Flow canonical records |
| `msika-service` | enterprise | Msika canonical records |
| `mushe-wallet-service` | enterprise | Mushe Wallet canonical records |
| `mushex-service` | enterprise | Mushex canonical records |
| `mvumo-service` | trust | Mvumo canonical records |
| `national-data-repository-service` | data | National Data Repository canonical records |
| `ndila-service` | integration | canonical geospatial location registry, routing, ETA, and distance matrix orchestration, geofencing and catchment boundary operations, tracking asset telemetry normalization, spatial search and geospatial intelligence context |
| `ndr-service` | data | Ndr canonical records |
| `nhume-service` | integration | dispatch request and assignment lifecycle, courier and fleet operational registry, last-mile tracking and proof-of-delivery telemetry, delivery chain-of-custody and exception workflow |
| `notification-service` | integration | Notification canonical records |
| `observability-service` | integration | Observability canonical records |
| `offline-edge-service` | integration | Offline Edge canonical records |
| `offline-sync-service` | integration | Offline Sync canonical records |
| `organization-registry-service` | registry | Organization registry (NEW organizations, `source=NATIVE`), authorized representatives, Channel-C delegated onboarding claims. Wave-1 dual-SoR: `wgv_organisation` (workforce-governance) remains SoR for existing governance links; org-registry holds a one-way `WGV_MIRROR` copy with `source_ref` back-pointer — see `docs/architecture/organization-registry-adoption.md`. Must not own facility (tuso), provider professional (varapi), or HSC employment (workforce-governance) truth. |
| `oros-service` | clinical | Oros canonical records |
| `pacs-adapter-service` | clinical | Pacs Adapter canonical records |
| `pct-service` | clinical | Pct canonical records; **encounter form responses** (structured data-entry responses, resolver decisions, extraction provenance). Must not own form definitions — those are forms-service. |
| `pharmacy-elmis-adapter` | clinical | Pharmacy Elmis Adapter canonical records |
| `pharmacy-service` | clinical | Pharmacy canonical records |
| `procurement-service` | enterprise | Procurement canonical records |
| `product-registry-service` | registry | Product Registry canonical records |
| `referral-service` | integration | Referral canonical records |
| `reporting-service` | data | Reporting canonical records |
| `rules-service` | clinical | Rules canonical records |
| `scheduling-service` | clinical | Scheduling canonical records |
| `schema-registry-service` | integration | Schema Registry canonical records |
| `search-service` | data | Search canonical records |
| `security-hardening-service` | integration | Security Hardening canonical records |
| `share-slip-service` | enterprise | Share Slip canonical records |
| `simba-service` | enterprise | wellness journeys, lifestyle plans, self-care plans, preventive care workflows, wellness goals, habit tracking workflows, coaching and nudge workflows, wellness programme participation, longitudinal wellness progress, connected source registry and permissions, personal wellness readings and manual entries, wellness remote monitoring alerts |
| `support-service` | integration | Support canonical records |
| `surveillance-service` | data | public-health surveillance signals and case aggregates, surveillance alert definitions and epidemiological counters, notifiable event monitoring telemetry |
| `tshepo-audit-service` | trust | Tshepo Audit canonical records |
| `tshepo-authz-service` | trust | Tshepo Authz canonical records |
| `tshepo-consent-service` | trust | Tshepo Consent canonical records |
| `tshepo-identity-service` | trust | Tshepo Identity canonical records |
| `tshepo-keys-service` | trust | Tshepo Keys canonical records |
| `tshepo-offline-service` | trust | Tshepo Offline canonical records |
| `tshepo-service` | trust | Tshepo canonical records |
| `tuso-service` | registry | Tuso canonical records; **facility-effective Practitioner-In-Charge (PIC) assignment** via the HPA-2017 nomination lifecycle (`PicNominationService`). SoR split (G30): TUSO owns the assignment; VARAPI owns the PIC eligibility-assessment snapshot each nomination captures verbatim. VARAPI's `pic-assignments` write endpoints are deprecated legacy parallel writers — do not re-introduce a second PIC assignment writer. |
| `ubomi-service` | registry | Ubomi canonical records |
| `varapi-service` | registry | Varapi canonical records; **provider PIC eligibility-assessment snapshot** (`TusoInteropController /v1/internal/interop/eligibility/assessments`). SoR split (G30): VARAPI is SoR for the point-in-time eligibility assessment only, NOT the facility-effective PIC assignment (that is tuso-service). VARAPI's own `pic-assignments` write path is `@Deprecated`. |
| `vito-service` | registry | Vito canonical records |
| `wellness-service` | enterprise | — |
| `workflow-service` | integration | Workflow canonical records |
| `workforce-governance-service` | enterprise | Workforce Governance canonical records |
| `vashandi-workforce-service` | enterprise | Operational workforce profile, assignment, roster, shift, attendance, leave/availability, access risk |
| `zibo-service` | registry | Zibo canonical records |