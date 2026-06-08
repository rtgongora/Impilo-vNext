# Preview Validation — Production Readiness

| Field | Value |
|-------|-------|
| Preview URL | `http://41.57.127.235` |
| Namespace | `impilo-preview` (fallback slice) / `impilo-full-preview` (full boot) |
| Deploy script | `scripts/deploy/manual-authorized-preview-deploy.sh` |
| Post-deploy smoke | `scripts/deploy/preview-smoke-test.sh` + `/health/version` SHA match |

**Authorization:** Deploy only after `run-local-quality-gates.sh` PASS and explicit user phrase. VM gates are canonical when GitHub Actions unavailable.

---

## Absorption completeness gate (pre-deploy)

- [ ] `sidecar-retirement-ledger-v2.ts` statuses match [`full-experience-production-readiness-audit.md`](full-experience-production-readiness-audit.md)
- [ ] No in-scope production route uses static KPI shells or stub-only panels
- [ ] 47/47 transaction-complete (`node scripts/product/generate-core-transaction-maps.mjs --check-only`)
- [ ] 21/21 VM local quality gates PASS

---

## Shell / global (acceptance 1–6)

| # | Check | Steps |
|---|-------|-------|
| 1 | Prominent logo | Open `/auth/login` at 375px, 768px, 1280px — logo clearly visible (hero scale) |
| 2 | Nompilo not page chrome | Authenticated `/provider-workspace` — no full-width Nompilo strip above content |
| 3 | Nompilo taskbar | Press `Ctrl+K` or taskbar Nompilo — palette/slide-over opens |
| 4 | Provider landing | Provider ID login → `/provider-workspace` (or `/facility` if no facility) |
| 5 | My Life minimized | Provider sees Work context; My Life tab accessible but not default |
| 6 | Client limited access | Self-register → limited My Life; verification elevation path visible |

---

## Critical services (acceptance 7–19)

### Identity (7–8)

- [ ] **Facility client intake:** `/registry/intake` — search, register, status badges
- [ ] **Client self-registration:** `/auth/register` → limited home
- [ ] **Provider ID application:** `/registry/providers` — apply, verification queue, approve flow
- [ ] **Provider login:** Full funnel through `/auth/resolving`

### Clinical (9–13)

- [ ] **PCT queue:** `/queue` — waiting list, call next
- [ ] **Telehealth:** `/telemedicine` → create session → join (RTC token if runtime up)
- [ ] **Inpatient:** `/clinical/inpatient` — ward board
- [ ] **OROS lab:** `/lab/worklist` — live items, accept/reject (not zeros)
- [ ] **PACS:** `/ehr/{patientId}/imaging` — study list, honest viewer boundary

### Domain services (10–18)

- [ ] **Wellness:** `/wellness` — goals, screenings (BFF reminders)
- [ ] **MADI:** `/madi` — donor pathway, blood bank, transfusion
- [ ] **MusheX:** `/wallet`, `/finance/mushex-platform` — payment/wallet credit
- [ ] **Costa:** `/finance/costa/encounter/{id}` — billing trigger
- [ ] **Fundo:** `/learning/catalog` — enrol, progress, assessment, certificate (`e2e/fundo-learning-flow.spec.ts`)
- [ ] **Coverage:** `/coverage/enroll`, `/coverage/member`
- [ ] **Public health:** `/public-health/site-registry` — Ndila map markers
- [ ] **Enterprise:** `/enterprise` drill-down; `/enterprise/warehousing` inventory KPIs
- [ ] **Live events:** `/live/discover` — register, join, replays

---

## Quality gates (acceptance 20–25)

- [ ] `npm run test:no-stubs` PASS in `ui/one-ui-shell`
- [ ] `check-frontend-mocks-and-stubs.sh` PASS
- [ ] `check-backend-frontend-parity.sh` PASS
- [ ] Key journey e2e: provider login, live events, wallet, coverage
- [ ] Mobile-responsive spot check: auth, provider workspace, wellness (375px)
- [ ] No regression: existing MADI, Fundo, registry routes still load

---

## Sidecar retirement verification (Wave 6)

- [ ] MusheX sidecars not in preview helm (only `one-ui-shell` + BFF public)
- [ ] `check-retired-sidecars-full-boot.sh` PASS
- [ ] Record: [`wave-6-sidecar-retirement-record.md`](wave-6-sidecar-retirement-record.md)

---

## Companion checklists

- [`docs/environment/OWNER_PREVIEW_TEST_CHECKLIST.md`](../environment/OWNER_PREVIEW_TEST_CHECKLIST.md)
- [`docs/product/PRODUCT_OWNER_TEST_SCRIPTS.md`](../product/PRODUCT_OWNER_TEST_SCRIPTS.md)

---

## Risks before production (not preview blockers)

1. RTC/LiveKit — k8s service DNS required; local port collision on mvumo/rtc-gateway
2. OROS catalog/reconciliation — maturity-labelled until BFF proxies ship
3. Marketplace MusheX 501 — honest blocked UX required
4. ops-docs partial absorption — document print/issue deferred (Tier D)
