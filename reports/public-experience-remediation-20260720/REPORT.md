# Public Experience Remediation — 2026-07-20 (+resume wave 2026-07-23)

> **Resume wave (2026-07-23)** closed report gaps §5.1 and §5.2 and one platform
> incident:
>
> 1. **Anonymous Nompilo Q&A lane** — guidance-service `PublicGuidanceController.ask`
>    (personalization forced off, allow-listed response, honesty-gated
>    llm/retrieval/none, standing disclaimer) behind BFF
>    `POST /internal/v1/public/gateway/guidance/ask` (`PublicGuidanceAskService`:
>    per-IP 10/300s + global 120/60s, 500-char cap; registered in SecurityConfig +
>    public-lane ADR registry, guard green). `NompiloAskInline` on the triage receipt.
>    Retrieval upgraded: natural-language questions fall back to per-keyword search
>    (full-phrase LIKE never matched); live-proven grounded answers with sources for
>    cholera/danger-signs/antibiotics questions.
> 2. **Map-pin location** — `NdilaMapLibre` gained a non-breaking `onMapClick` seam;
>    `EmergencyLocationMapPicker` (public MVT tile lane, dynamic import) wired into
>    the triage location step.
> 3. **Deploy-clobber incident (live-caught, fixed):** the 2026-07-21 integrated
>    deploy branch (`claude/deploy-integration-Yypyl`: ndila basemap fix, EMERGENCY
>    capability search, service-family expansion, find-care filters, welcome
>    nav/colour, coverage appeals) was never merged to canonical; subsequent canonical
>    deploys (incl. this wave's) reverted those fixes live — the find-care basemap was
>    blank again. Fixed by merging the integration branch into canonical
>    (`209b6ad45`, one conflict resolved keeping coloured steps + Impilo ID naming)
>    and redeploying bff+tuso+shell from the merged tree. Live re-proof:
>    EMERGENCY→199, MATERNITY family→904, tile config→public MVT lane, SOS + ask 200.
>    LAW reinforced: shared-image deploys must come from a tree containing every
>    session's landed work — check `git log HEAD..origin/<integration-branch>` before
>    any shell/BFF deploy.

Full design + functionality remediation of the public landing (website) and the
public Emergency journey (one-ui-shell), per PO brief. No mocks were added; every
interactive element is wired to a real backend or explicitly absent.

## 1. Landing page (public-website repo, `zimttech/impilo-website`)

**Hero redesign** — the detached flank-photo cards are gone. The hero is now a
full-bleed, full-viewport photographic slideshow (`src/components/HeroCarousel.jsx`)
behind a readability gradient and a single message layer:

- 6 slides, led by authentic MoHCC deployment photography (Nov 2024 field set):
  nurse reviewing records at Jimila Clinic; fibre-splicing technician
  (engineering/infrastructure — required); paper-register digitisation; telehealth;
  point-of-care data capture; the Impilo app on a tablet.
- Cross-fade (1.4s) + slow pan, 7s auto-advance, prev/next buttons + indicator
  tabs, pause on hover/interaction/hidden tab, `carousel`/`slide` ARIA roles with
  per-slide labels, `prefers-reduced-motion` → static + no auto-advance, absolute
  layers = zero CLS, first slide eager + rest lazy, 1920w/960w WebP srcsets.
- One message, one dominant entry (need-first search → deployed find-care with
  `?q`/`?service`), four quick chips + Ask Nompilo. Account/emergency controls
  live in the header only.
- The six service pathways moved BELOW the hero into their own section
  (progressive disclosure); trust section, partner credibility wall (17 restored
  partner logos), app section and get-involved follow.

**Identity** — the official `impilo.svg` wordmark replaced the text-rendered
brand in the Navbar (crest retained as the Ministry mark); favicon MIME type fixed.

**Assets** — `src/assets/images/hero/` with responsive variants and
`ATTRIBUTION.md` (provenance/licence register). `IMG-20241126-WA0038.jpg`
(identifiable patients) deliberately excluded pending consent review.

## 2. Public navigation continuity (one-ui-shell)

- `PublicHeader` now renders the OFFICIAL `ImpiloBrandLogo` wordmark (it was a
  CSS-drawn green square + letter "i") plus persistent Find care and Emergency
  links; wraps cleanly on small screens.
- New `PublicBackBar` in `PublicShell`: Back (history-aware, falls back to
  /welcome on deep links) + Home on EVERY public sub-page. The stranded
  "Welcome / Emergency" text breadcrumb is gone.
- Floating Emergency Help button hides on the emergency route itself.

## 3. Emergency journey rebuild (one-ui-shell)

`/welcome/emergency` is now an interactive responder:

1. Life-at-risk warning + tap-to-call verified numbers (config-driven:
   `src/config/emergency.ts` is the single source; hard-coded strings removed
   from components).
2. Four-way choice: **Call now** / **Find nearest emergency facility** (GPS →
   real find-care gateway search, TUSO+Ndila) / **Request assistance callback**
   (the existing short form, same real backend) / **Nompilo emergency triage**.
3. **Nompilo triage** (`components/public/emergency/`): DELIBERATELY a
   deterministic danger-sign protocol presented conversationally — one question
   at a time with button answers, editable transcript, immediate escalation on
   any danger sign (call-first banner + safe first-aid actions + shortened flow),
   skip logic, location capture (device GPS / landmark / province / at-facility),
   nearby-care surfacing, structured responder summary, submission to the REAL
   anonymous SOS intake (`POST /internal/v1/public/gateway/sos` → daidzai), and
   sessionStorage persistence across navigation. Explicitly labelled "guided
   help — not a diagnosis".
4. **Honest status**: `SosStatusTimeline` renders only backend-reported stages
   (AWAITING_CALLBACK → RECEIVED → TRIAGED → LINKED → CLOSED/RESOLVED); the UI
   never claims an ambulance was dispatched. The receipt and
   `/welcome/emergency/track` both use it. Failure paths (400/429/network) keep
   state and tell the user to call.
5. Danger-sign cards + expandable public-health guidance replace the flat text
   walls.
6. **Nompilo placement**: on the emergency route the triage panel IS the
   interface — the OS taskbar, Nompilo command layer and proactive assistant are
   suppressed there even for authenticated users (`shell-visibility.ts`).

## 4. Services integrated (all real, no mocks)

| Capability | Interface |
|---|---|
| SOS intake + status | experience-bff `PublicGatewaySosBffController` → daidzai-service `EmergencyController` / `PublicEmergencyStatusController` |
| Nearby emergency care | experience-bff `PublicGatewayFindCareBffController` → TUSO registry + Ndila routing |
| Facility detail links | `/welcome/find-care/{id}` (existing public lane) |
| Ops continuation | daidzai verify-callbacks console (`/work/daidzai/verify-callbacks`), triage + EMS dispatch consoles (unchanged, already real) |

## 5. Backend gaps found (documented, NOT mocked)

1. **No public conversational (LLM) Nompilo lane.** `/internal/v1/assistant/chat`
   (guidance-service + llm-orchestration) requires authenticated actor headers;
   the public gateway exposes read-only guidance/education only. The emergency
   triage therefore uses a deterministic protocol (also the clinically safer
   choice for danger-sign screening). If free-text public Nompilo conversation is
   wanted, a rate-limited anonymous BFF lane needs to be designed (abuse
   controls, PHI guardrails).
2. **Map-pin adjustment** for emergency location is not built (Ndila MapLibre is
   available on find-care; wiring a draggable pin into triage is a follow-up).
   GPS + landmark + province + at-facility capture are in.
3. **Ward/district-level gazetteer selection** — the public search accepts
   `district` but there is no public district list endpoint; province-level
   selection shipped.
4. **Emergency-number verification** — 999/112/2019 carried over from the
   previously approved page and now config-driven with a verification flag;
   Ministry sign-off still required (`src/config/emergency.ts` header).
5. **Shona/Ndebele clinical translations** — the i18n mechanism and locale files
   exist and chrome strings are localized, but the triage protocol's clinical
   strings ship in English pending professional medical translation review
   (machine-translating danger-sign instructions is a patient-safety risk).
6. **Stock-photo licences** — two legacy stock images in the website repo have no
   licence records (see `ATTRIBUTION.md`); the hero avoids them except
   `engage.jpg` (flagged), pending licence confirmation.

## 5b. Live-caught platform bug (fixed): default-tenant split broke SOS status

The end-to-end proof (real triage submission `SOS-20260720-1607C`) exposed that
every public SOS status lookup 404'd. Root cause: the estate runs TWO "default
tenant" UUIDs — anonymous WRITES store under the golden `…-4000-8000-…001`
(browser api-client tenant passes through on POST), while the BFF's
`PublicGatewayAnonymousDefaultsFilter` stamps anonymous GETs with
`…-0000-0000-…001` (which public find-care depends on: the TUSO facility
master's 1,775 rows live under it), and the outbound trust-header interceptor
forwards the inbound tenant over client-set values. Fix (commit `b38bbca56`):
daidzai status lookup = tenant-scoped first + reference-only fallback
(PII-free, uniform 404); constants documented. Verified live: status 200 AND
find-care 86 results in the same deploy. **Platform cleanup flagged: unify the
two default tenants estate-wide.**

## 6. Tests

- `triage-protocol.test.ts` — 7 unit tests: full question order, unconscious
  escalation + shortened flow, cardiac ⇒ chest-pain danger, pregnancy skip rules,
  GPS-only location, RED summary + payload mapping, calm-path no-danger.
- `EmergencyTriagePanel.test.tsx` — 5 journey tests: one-question-at-a-time,
  immediate danger escalation, full calm flow submitting the structured summary
  to the real SOS lane + honest receipt, sessionStorage persistence across
  unmount, honest failure handling.
- Existing suites kept green: EmergencyAssistanceForm (4), EmergencyHelpButton
  (4), emergency track page (3). Emergency scope: **23/23 green**; `tsc` clean.
- Full one-ui-shell suite: 2104 passed / 9 failed — all 9 failures are in files
  owned/modified by other concurrent sessions (login/passkey, discharge board,
  brand registry counts, route registry) and pre-date this work; none touch the
  public/emergency scope.

## 7. Deploys

- public-website → `127.0.0.1:5000/impilo/public-website` digest pinned in
  `deploy/tls/mohcc-gov/public-website.yaml` (zero-downtime rollout, verified live).
- one-ui-shell → built from the committed tree, pushed to the local registry and
  digest-pinned on `impilo-full-preview/one-ui-shell` (2 replicas, RollingUpdate).

## 8. Known constraints of this verification pass

- Mobile visual QA WAS executed with Playwright device viewports (390×844,
  375×812, 1920×1080): landing + emergency + danger-escalation + receipt +
  track captured; a 70px landing overflow (AppShowcase trust panel nowrap
  chips) was caught and fixed live; final overflow = 0 at 375/390/414/desktop.
- Live e2e proof: danger-path triage (unconscious + not breathing + severe
  bleeding) → danger banner + safe actions → callback + location → real
  submission (`SOS-20260720-1607C`, 202 from daidzai) → receipt with honest
  timeline → track page resolves the reference with the real
  AWAITING_CALLBACK status. The request is marked as a test in its description
  for the dispatcher console.
