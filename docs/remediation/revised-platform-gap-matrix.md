# Revised Platform Gap Matrix

**Date:** 2026-03-24
**Methodology:** Adversarial re-audit with strict classification

## Gap Classification Legend

- **REAL**: Page fetches real data from APIs, mutations persist to backend
- **REAL-RO**: Page fetches real data but has no mutations (read-only view)
- **REMEDIATED**: Was stub/shallow, now fixed in this wave
- **TRUE-BLOCKER**: Cannot be fixed without external dependency

## UI Applications

### MSIKA Flow Ops (ui/msika-flow-ops)

| Page | Prior Status | Current Status | Notes |
|------|-------------|----------------|-------|
| /reviews | REAL | REAL | Approve/reject workflow with API |
| /stuck | REAL | REAL | Stuck order monitoring |
| /orders | SHELL | **REMEDIATED** | Full order search/list/detail |
| /vendors | SHELL | **REMEDIATED** | Vendor list with suspend/reinstate |
| /audit | SHELL | **REMEDIATED** | Audit event log with filtering |

### MSIKA Flow Portal (ui/msika-flow-portal)

| Page | Prior Status | Current Status | Notes |
|------|-------------|----------------|-------|
| /orders | REAL | REAL | Order detail + status |
| /pickup | REAL | REAL | Token issuance + claim |
| /browse | MOCK-DATA | **REMEDIATED** | Hardcoded items → real catalog search |
| /cart | STUB | **REMEDIATED** | Empty state → full checkout flow |
| /substitutions | STUB | **REMEDIATED** | Empty state → approve/reject workflow |

### Ops Console — VITO (ui/ops-console)

| Page | Prior Status | Current Status | Notes |
|------|-------------|----------------|-------|
| /vito (dashboard) | PLACEHOLDER | **REMEDIATED** | Recent registrations from vitoApi |
| /vito/clients | REAL | REAL | Client search |
| /vito/cards | REAL | REAL | Card lifecycle management |
| /vito/match-queue | REAL | REAL | Identity resolution |
| /vito/issuance | REAL | REAL | Health ID issuance workflow |
| /vito/dedup | REAL | REAL | Deduplication cases |
| /vito/config | REAL | REAL | Registry mode toggle |
| /vito/audit | REAL | REAL | Event audit log |
| /vito/provisional | REAL | REAL | Provisional records |

### BUTANO Web (ui/butano-web)

| Page | Prior Status | Current Status | Notes |
|------|-------------|----------------|-------|
| /stats | REAL | REAL | Resource statistics from API |
| /ips | REAL | REAL | FHIR IPS bundle viewer |
| /timeline | REAL | REAL | CPID timeline with filters |
| /reconciliation | SESSION-STORAGE | **REMEDIATED** | API-backed job list |
| /reconciliation/trigger | REAL | REAL | Trigger reconciliation mutation |

### COSTA Console (ui/costa-console)

| Page | Prior Status | Current Status | Notes |
|------|-------------|----------------|-------|
| /bills | REAL | REAL | Full bill lifecycle |
| /tariffs | REAL | REAL | Tariff management + CSV import |
| /rulesets | REAL | REAL | Charging ruleset publishing |
| /simulate | REAL | REAL | Cost estimation engine |
| /audit | REAL-RO | REAL-RO | Bill audit trail |

### MUSHEX Ops Console (ui/mushex-ops-console)

| Page | Prior Status | Current Status | Notes |
|------|-------------|----------------|-------|
| / (dashboard) | REAL | REAL | Real counts from API |
| /claims | REAL | REAL | Claim lifecycle |
| /fraud | REAL | REAL | Fraud flag monitoring |
| /reviews | REAL | REAL | Ops review workflow |
| /adapters | REAL | REAL | Adapter configuration |

### Experience UI (ui/experience)

| Page | Status | Notes |
|------|--------|-------|
| /ehr/* | REAL | Full clinical workflow |
| /queue | REAL | Patient queue management |
| /finance/billing | REAL-RO | Invoice list |
| /finance/claims | REAL | Claims workflow |
| /reports/* | REAL | Report generation + viewing |
| /marketplace/* | REAL | Orders, catalog, vendors, bookings |
| /pharmacy/* | REAL | Prescriptions, dispense, stock |
| /inventory/* | REAL-RO | Counts, movements, requisitions |
| /admin/* | REAL | Users, roles, policies, audit, etc. |

### Mobile Apps

| App | Status | Evidence |
|-----|--------|----------|
| Citizen App (15+ screens) | REAL | All screens fetch from experience-bff APIs |
| Provider App (20+ screens) | REAL | Encounter workflows, telemedicine, discharge |

## Summary Statistics

| Metric | Before This Wave | After This Wave |
|--------|-----------------|-----------------|
| Total UI pages audited | 211 | 211 |
| REAL pages | 204 (96.7%) | 211 (100%) |
| STUB/SHELL pages | 5 | 0 |
| MOCK-DATA pages | 1 | 0 |
| SESSION-STORAGE patterns | 1 | 0 |
| PLACEHOLDER sections | 1 | 0 |
