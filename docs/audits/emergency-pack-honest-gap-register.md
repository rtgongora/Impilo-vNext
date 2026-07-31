# Emergency pack — honest gap register

**Purpose:** name every known gap so a summary cannot be read as completion. PARTIAL and
NOT BUILT are never counted as delivered. Updated for W18 + W19 (2026-07-30).

## Must-carry gaps (plan-mandated)

### 1. W14 sourcing blocker (~140 syndromes)

**Status:** SKIPPED (PO decision 2026-07-28) — blocked on **sourcing**, not on scope.

Content tranches 4–12 require transcribing real external clinical sources (WHO Emergency Care
Toolkit, IITT charts, BEC 2018, EDLIZ 2025, DSEC, specialty societies, toxicology, mental-health
safeguarding) into `docs/reference/who-emergency-care-toolkit/` with retrieval date + SHA-256.
Those sources were not available to hash and cite. Fabricating ~140 syndromes was **refused**.

Engines, CKP schema hooks, and guard/traceability machinery remain ready. Only the content is
missing. This is an honest gap, not a silent drop.

### 2. W15a disposition-reconciliation — silent-open episode failure mode

**Status:** INTENTIONAL new failure mode — must stay visible.

`EdVisitService.recordEpisodeDispositionBestEffort` maps ED visit dispositions onto the 15-type
episode disposition. When the map cannot satisfy R12 mandatory content, the write is **logged and
skipped** rather than fabricating fields. The ed_visit disposition still succeeds; the
**episode stays open**.

That is the honest state, but it is a failure mode clinicians have not seen in production yet:
episodes can remain open past what a clinician might expect after an ED disposition. Do not
"fix" this by inventing destination/last-seen values.

### 3. `mental-health-service` built but never deployed

**Status:** IMAGE BUILDABLE LOCALLY — still UNREACHABLE on public preview.

W13 landed service, V001, registry, BFF client/controller, and UI. A local runtime image now
builds (`services/mental-health-service/Dockerfile` +
`reports/journeys/emergency-pack-w19/mental-health-image-build.md`, digest
`sha256:b77d2efb2962…`). That digest is **not** in
`values-full-preview-digests.generated.yaml` until push + `resolve-image-digests.sh` +
authorized deploy. Every mental-health surface remains unreachable on the public preview today.

### 4. Deliberately absent Helm digest

**Status:** STILL OMITTED — local image exists; registry digest not resolved.

Do not invent a digest. After image import, run `scripts/full-boot/resolve-image-digests.sh`.

### 5. Envoy public path vs compose/runtime MH

**Status:** PARTIAL — compose wired; Envoy public cluster still omitted (in-cluster only).

`docker-compose.runtime.yml` now includes `mental-health` (port 8397) + BFF
`MENTAL_HEALTH_BASE_URL`, and `scripts/seed/01-init-databases.sql` creates `mental_health`.
Envoy still has no public MH cluster — BFF→MH is in-cluster DNS only (same as other
non-public domain services). Helm digest + authorize deploy remain the preview blocker (§3–4).

### 6. Inter-facility ambulance transport — scoped OUT

**Status:** OUT OF SCOPE.

Nhume `DeliveryType.PATIENT` is intra-facility porter/trolley only. Inter-facility transport is
modelled as `emergency_handover(TO_FACILITY)` + PCT referral + a Daidzai EMS mission where one
exists — not as a Nhume delivery type. Nobody owns a full ambulance transport product in this
pack.

### 7. W16a TeaVM spike + Tier A UI binding

**Status:** **GO** (spike) + **Tier A preview wired** (UI)

- 23/23 corpus scenarios in-browser
- Bundle ~149 KiB; cold-start ~49 ms
- Resource embedding via TeaVM `ResourceSupplier` plugin (not path-literal hardcoding)
- Evaluate export: `IittBrowserEvaluateMain` → `/emergency/iitt-engine.js` (~100 KiB)
- UI: `OfflineIittPreviewPanel` on ED visit when offline; of-record write still
  `NOT_TRIAGEABLE_OFFLINE`

Evidence: `docs/clinical/emergency-domain-pack/teavm-w16a-go-nogo.md`,
`ui/one-ui-shell/public/emergency/iitt-engine.js`.

---

## Additional named gaps

| Gap | Notes |
|-----|-------|
| 31 ED routes still nested honesty envelope | Flat `EmergencyHonesty` landed; ED lane migration open |
| DSEC value lists UNVERIFIED | ENGINEERING_SEED; zibo dependency |
| Acuity indicator PARTIAL | No triage_acuity on episode events yet |
| Resus / observation / critical-result indicators | NOT_COMPUTABLE until projected |
| `test:query-honesty` peer failure | Regulatory student-applications page — other lane |
| Costa / rito identity-repoint coverage | Pre-existing debt outside pack remit (guard scoped) |
| Realtime Helm keys unset until deploy | `values-full-preview.yaml` now sets `NEXT_PUBLIC_REALTIME_WS` / `NEXT_PUBLIC_KHULUMA_WS` + `PCT_EMERGENCY_REALTIME_ENABLED`; apply still needs authorize |

## Thin-UI / product-completeness closure (2026-07-31)

Web journey breaks closed:

- BFF auto-intake on `MENTAL_HEALTH` handover (`mh_intake_status` + spine retry)
- Durable ED diagnostics via visit `diagnostic_orders` + GET list
- Canonical `/internal/v1/ed/resuscitation/**` (phases/CPR/meds) for web + mobile
- Silent-open disposition reconcile CTA on ED visit
- Pathway answers UI; blood readiness bound when order context exists; `emergency-cases` deprecated

Mobile parity landed (Emergency tab + hub): episode spine, ED depth, resus, MH queue/clinical,
guest SOS callback → public gateway, emergency outbox + offline triage honesty.

Remaining must-carry gaps above (W14 sourcing, MH Helm digest + authorize deploy) are **not**
code thin-UI.

## How to use this register

- A demo that skips these names is incomplete.
- Closing a gap requires evidence (deploy, source hash, projection, or explicit scope change) —
  not a README edit alone.
