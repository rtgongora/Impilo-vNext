# Emergency, Resuscitation and Acute Care Pack — Lease Record

Delivery-boundary record for the **Integrated Emergency, Resuscitation and Acute Care Domain Pack**
(opened 2026-07-26, base anchor `6fd3ca2d0`, branch
`claude/staging-ux-orchestration-remediation-Yypyl`). Companion to
[`iatg-trauma-leases.md`](iatg-trauma-leases.md) and
[`iatg-surgery-procedures-leases.md`](iatg-surgery-procedures-leases.md).

Plan: `~/.claude/plans/coordinate-with-and-learn-lively-hejlsberg.md`.

**Status: awaiting coordinator ratification.** `docs/registry/**` is coordinator-only under the
trauma lease §2, but the surgery/procedures programme has since established the precedent that a
domain pack authors its own `iatg-<pack>-leases.md` (`0ac48f8ce`). This file follows that precedent.
Blocking a live programme on an idle coordinator session would stall it while three other packs
consume migration numbers, so the reservations below are asserted now and are open to coordinator
amendment.

---

## 1. Why this file exists

At the time of writing, **at least five sessions are committing to one working tree on one branch**.
`/home/robert/Impilo-vNext` is a **symlink to `/opt/impilo/repos/Impilo-vNext`** — it is not a second
clone, so those sessions share one git index. During the four commits of this pack's W0, HEAD moved
five times and two ports and one migration number were claimed by peers.

Consequences that are not optional:

- **Commit path-scoped, always.** `git commit -- <explicit paths>`. Never `git add -A`, never a bare
  `git commit` — a bare commit takes the *whole shared index* and has already swallowed another
  session's files once in this repository's history.
- **`git pull --ff-only`**, never `--rebase` on shared history. If ff-only fails, stop.
- **Verify before pushing**: `git show --stat HEAD` must contain only your files.
- **Reserve a migration block before writing a migration**, and re-verify the head first — three
  packs are consuming numbers concurrently.
- **On a shared tree the WORKING TREE is part of the migration namespace, not just history.** An
  in-flight migration from another lane is **untracked**, so `git log` cannot see it and neither can
  any check built on committed history. Run **both**, immediately before you cut a number:

  ```
  ls services/<svc>/src/main/resources/db/migration | sort -V | tail
  git status --porcelain services/*/src/main/resources/db/migration/
  ```

  This is how `pct` **V060** was found (see §3c) — `git log` showed nothing. Contributed by the RMNP
  lane, 2026-07-26.
- **Foreign files are routinely already staged when you go to commit.** The RMNP lane ran `git add`
  on its own paths and found `V060__problem_severity.sql` and a `ProblemServiceTest` sitting in
  `git diff --cached --name-only` from the problem-list lane. `git commit -- <explicit paths>` kept
  them out **and left them staged for their owner**, which is the behaviour you want; a bare
  `git commit` would have swallowed both and produced an entirely plausible-looking commit. Run
  `git diff --cached --name-only` before committing and `git show --stat HEAD` before pushing, and
  compare the **count** against what you intended — a silently-included or silently-skipped file
  still produces a plausible stat block.
- **`git pull` is not reliable here; `git fetch` + explicit ff-only merge is.** Plain
  `git pull --ff-only` fails with `fatal: Cannot fast-forward to multiple branches` when a peer
  fetches concurrently and writes a multi-branch `FETCH_HEAD` under you; `git pull --rebase` fails
  outright because the shared tree almost always contains another session's uncommitted work. Use:

  ```
  git fetch origin && git merge --ff-only origin/$(git branch --show-current)
  ```

  And note the structural shortcut: sessions in this tree **share one `.git`**, so a peer's commits
  are already in your `HEAD` — no fetch is needed at all to push, and `git push` is a fast-forward.
  Both hazards confirmed independently by the RMNP lane.

## 2. Lane ownership

| Owns (may edit) | Notes |
|---|---|
| `services/pct-service/**` — `emergency_episode`, ED lane elevation, triage, alerts, order sets, medicines, observation stay, disposition, handover, identity ledger | ED lane (`ed_*`) was the trauma lane's; that programme is closed. Coordinate with RMNP on `pct_labour_observations` and with Adult Medicine on admission handover. |
| `services/daidzai-service/**` — episode generalisation, PCT back-link, adopt/merge, MCI casualty | The `trauma_episode` spine stays daidzai's delegated capability (CC-4). |
| `services/inpatient-service/**` — `resuscitation_*` **only**, `emergency_activation`, `resuscitation_medication` | **`procedure_episode` and all `procedure_*` tables are theatre/surgery-owned — never migrate them.** This pack adds only a nullable `emergency_episode_id` to `procedure_episode`, and only under a commit-token handoff with the surgery lane. |
| `services/madi-service/**` — massive-haemorrhage protocol, emergency uncrossmatched release | Genuinely absent today. |
| `services/clinical-knowledge-platform-service/**` — emergency rule content and the emergency rules-framework columns | **Do not extract `rules/tabular/**` into a library** — RMNP has those files hot. |
| `services/zibo-service/**` — emergency value sets | |
| `services/tuso-service/**` — ED capacity, resus-bay `space_type`, trauma-centre capability | |
| `services/inventory-service/**` — emergency kit / resus trolley | |
| `services/mental-health-service/**` — **NEW service, port 8397** | See §4. |
| `services/experience-bff/**` — emergency controllers only | |
| `ui/one-ui-shell/src/{app/clinical/emergency,features/emergency,lib/offline}/**` | |
| `libs/emergency-domain/**` — **NEW library** | |
| `scripts/runtime-proof/emergency-*.sh`, `scripts/guard/check-emergency-*.sh`, `check-no-ts-clinical-logic.sh`, `check-identity-repoint-coverage.sh` | |
| `docs/clinical/emergency-domain-pack/**`, `docs/clinical-governance/emergency/**` | |

**Shared, requires a handoff:** `services/pom.xml`, `docker-compose.runtime.yml`,
`config/full-boot-service-classification.yml`, `deploy/helm/**`, everything else under
`docs/registry/`, `docs/runbooks/port-allocation.md`.

## 3. Reserved migration blocks

Heads verified at `6fd3ca2d0`. **Every block below sits above the corresponding claim in the trauma
and surgery/procedures leases**, so a collision is impossible in either direction.

| Service | Head today | Trauma lease | Surgery lease | **Emergency block** | Sub-ranges |
|---|---|---|---|---|---|
| `pct-service` | **V060 (untracked — §3c)** | V035–V069 | — | **V070–V099** | episode V070–72 · triage V073–74 · alerts V075 · diagnostics/order-sets V076–77 · medicines V078 · obs-stay/disposition/handover V079–81 · identity ledger V082 · board V083–85 · reserve V086–99 |
| `inpatient-service` | V066 | V035–V064 (**dead, see §3a**) | V067–V080 | **V081–V110** | resus tenant/anchor V081 · concurrency V082 · `resuscitation_medication` V083 · activation origin/CHECK V084 · reserve V085–110 |
| `daidzai-service` | V016 | V010–V049 | — | **V050–V079** | generalise + PCT back-link V050–52 · merge/adopt V053 · MCI casualty V054–56 |
| `madi-service` | V015 | V015–V044 (MTP sub-range **never built**, superseded — §3b) | — | **V045–V074** | MHP V045–48 · emergency release V049–50 · ratio content V051 |
| `clinical-knowledge-platform-service` | V006 | — | V007–V020 | **V021–V040** | rules framework V021 · IITT V022 · pathway repair V023 · order sets V024 · tranches V025–36 |
| `zibo-service` | V007 | — | V008–V014 | **V015–V034** | emergency value sets V015–18 |
| `tuso-service` | V043 | — | V042–V048 | **V050–V069** | `space_type` widen V050 · ED capacity V051 · trauma-centre capability V052–53 |
| `oros-service` | V017 | V015–V024 | V018–V024 | **V030–V049** | `results.acted_at` V030 |
| `inventory-service` (Dura) | V014 | — | V015–V020 | **V021–V040** | emergency kit V021–23 |
| `rito-quality-safety-service` | V007 | V010–V019 | — | **V030–V039** | after-action linkage V030 |
| `notification-service` | V017 | — | V018–V020 | **V030–V039** | emergency templates V030 |
| `vashandi-workforce-service` | V008 | V015–V024 | V009–V012 | **V030–V039** | emergency roster view V030 |
| `vito-service` | V048 | V035–V044 | — | **V060–V069** | provisional-identity hardening V060 |
| `mental-health-service` | — | — | — | **V001–V030** | new service |
| `costing-engine-service` (COSTA) | V024 | — | V025–V028 | **none needed** | emergency override + deferred-charge reconciliation already built (V012/V014) |
| `mvumo-service` | V008 | — | V009–V014 | **none needed** | `L4_EMERGENCY_OVERRIDE` consent break-glass already built |

### 3c. Peer claims recorded here — and `pct` V060 is an EXCEPTION, not part of a range

The RMNP lane asked to record its block here rather than open a third lease document, so:

| Lane | Claim |
|---|---|
| **RMNP (reproductive/maternal/newborn)** | `pct` **V058, V059, and V061–V069** · `clinical-knowledge-platform-service` **V007–V009** |
| **Adult problem-list repair** | `pct` **V060** — `V060__problem_severity.sql` |

> ⚠️ **`pct` V060 IS TAKEN. Do not read V058–V069 as contiguous.**
> `services/pct-service/src/main/resources/db/migration/V060__problem_severity.sql` exists on disk
> and is **untracked** — it is another lane's in-flight work (adding a severity column so that
> repairing the broken `/v1/conditions` write path does not convert a visible outage into a silent
> drop of a clinical attribute). Because it is untracked, `git log` shows nothing and every
> history-based check misses it. See the `git status --porcelain` rule in §1.

RMNP adds no CKP state beyond rule-governance rows and source-document provenance — its clinical
content lives in classpath JSON rather than migrations, deliberately, so a change to a clinical
threshold is a reviewable diff and not a data migration. This pack follows the same rule (R6: content
out of the jar, and out of the schema).

### 3a. `inpatient-service` V037–V064 is dead space

Migration files jump **V036 → V065**. The trauma lease reserved V035–V064 but only V035 and V036
landed; theatre took V065–V066. Flyway `out-of-order` is not enabled anywhere, and `pct` setting
`validate-on-migrate: false` hides a *validate* failure without making a lower-versioned migration
**apply** in an environment already at V066. So V037–V064 can never be used. The surgery lease reached
the same conclusion independently and leaves them unclaimed to preserve the historical record; this
pack does the same. **The trauma lease §3 row for inpatient is stale and should be annotated.**

### 3b. `madi-service` trauma MTP sub-range is superseded

The trauma lease reserved madi V019–V034 for "MTP/O-neg/ratio/transport". None of it was built —
madi's head is V015 and there is no massive-transfusion, uncrossmatched-release or ratio model in the
service. This pack builds it once, at V045+. The trauma sub-range should be marked superseded rather
than left looking like existing work.

## 4. Port allocation

**8397 — `mental-health-service`.** Claimed for the definitive mental-health services this pack's
psychiatric-emergency handover must be accepted by (product-owner ruling, 2026-07-26: build a real
service rather than a typed refusal). Descriptive name follows the `patient-safety-service` /
`telemonitoring-service` / `participation-service` precedent; a vernacular product name may be
assigned in the registry later without a folder move.

The plan originally allocated 8395. The surgery/procedures programme claimed **8395
`procedures-service`** and **8396 `surgery-service`** in `c7ceec827` while this pack's W0 was in
flight — a live illustration of why §1 exists. 8398 remains free; 8399 is `referral-service`.

**Onboarding a new service is not just a port** (and note the recorded hazard: a service absent from
`config/full-boot-service-classification.yml` regenerates to `enabled: false` and silently
undeploys): `services/pom.xml` modules · the seven `docs/registry/` companion files ·
`docs/runbooks/port-allocation.md` · `docker-compose.runtime.yml` · Helm values ·
full-boot classification · envoy route · BFF client and controller · UI route registered in
`ui/one-ui-shell/src/lib/routes.ts`.

## 5. Cross-pack contracts frozen by this pack

Four seams other packs must consume rather than reinvent. Messaged to the Adult Medicine, Surgery and
RMNP sessions on 2026-07-26.

1. **`pct.emergency_episode` is the canonical emergency episode**, facility-scoped and journey-anchored,
   a *sibling* of `ed_visit` under the journey rather than a parent that mints one — `ed_visit.journey_id`
   is already `NOT NULL UNIQUE`, so an episode-parent would fork a second journey for one facility
   visit. Cross-facility continuity stays `dai_trauma_episode` alone. No pack may create a rival
   episode or acuity concept; link on `emergency_episode_id`.
2. **Handover acceptance transfers responsibility, and nothing else does.** Raising a referral,
   admission request or theatre request leaves the episode `OPEN_AWAITING_ACCEPTANCE`. Only a write by
   the accepting party, carrying the accepting party's own record id, permits closure. `DECLINED` and
   `EXPIRED` return the patient to emergency care; expiry raises a Rito case. **There is deliberately
   no timeout that discharges responsibility.** For theatre, creating the `procedure_episode` *is* the
   acceptance; for admission, the existing `pct_admission_id` handshake echo is reused rather than a
   new mechanism invented.
3. **Acuity has exactly one authority: WHO IITT** (product-owner ruling). ESI and MTS become advisory
   scores that can never write `ed_triage_assessment.acuity`; `IMPILO_5` is retired as a selectable
   system. A pack needing a severity concept derives from IITT priority rather than adding a fifth
   scale.
4. **Traceability is shared machinery.** Emergency standards are declared in
   `docs/clinical-governance/emergency/standards-baseline.json` and run through
   `scripts/clinical/dak/build-traceability-matrix.py` + `scripts/guard/check-dak-traceability.sh`,
   generalised for exactly this purpose by RMNP in `0619341d7`. This pack builds no rival matrix or
   guard. The input contract, verified on disk:

   ```json
   { "domain": "emergency",
     "standards": [ { "standardId": "EMS-IITT-014", "family": "IITT",
                      "title": "…", "sourceCitation": "…", "kind": "STANDARD" } ] }
   ```

   `standardId`, `family` and a **non-blank `sourceCitation`** are mandatory — the guard fails
   without a citation, because "a declared standard with no citation is an assertion wearing the
   costume of a standard". Families: `IITT` · `BEC` · `DSEC` · `ECT_CHECKLIST` · `SSC26` · `EDLIZ` ·
   `ZW_POLICY` (`WHO_DAK` is RMNP's). Non-coverage goes in a per-domain exclusions register and every
   deferral needs a revisit condition. Each domain's standards stay in its own file; only the
   machinery is common.

## 5a. Inherited engineering constraints (from the RMNP lane, 2026-07-26)

Two constraints on CKP internals this pack must respect rather than rediscover.

**`PredicateEvaluator` is shared by four engines — any change is a retest-all-four event.** It backs
the danger-sign engine, *both* IMNCI classification packs and the dosing engine. Touching it means
running `PredicateEvaluatorTest`, `PaediatricRuleContentTest`, `DangerSignEvaluationServiceTest`,
`ImnciClassificationServiceTest`, `YoungInfantClassificationTest`, `DoseCalculationServiceTest`,
`PaediatricUnsafeDoseTest`, `PaediatricVitalsRulesTest` **and** re-proving the live estate cases. The
emergency IITT engine should extend via content and the two hooks below, not by editing the evaluator.

**Two recent hooks to use instead of inventing equivalents:**
- **`bandKey` on `bands`** — lets a threshold be scored against something other than `ageDays`.
  **Never overload `ageDays`**: facts are one flat map shared by every rule in a request, so
  overloading it corrupts every other rule in the same evaluation. The emergency pack needs this for
  gestation-scoped obstetric rules and for weight-banded dosing.
- **`appliesWhen` on `TabularRule`** — "is this rule about this patient at all", three-valued, where
  UNKNOWN reports the inputs as unassessed rather than silently dropping the rule. This is the
  mechanism for age/pregnancy/context-routed triage variants; it is *not* the same question as "does
  it fire".

Also confirmed by RMNP and worth restating: `pct` sets `validate-on-migrate: false`
(`services/pct-service/src/main/resources/application.yml:31`), which hides a Flyway *validate*
failure **without** making a lower-versioned migration apply — so the schema diverges from what the
code expects and nothing says so.

## 6. Defects fixed in W0 that other lanes depend on

All three were verified in code, not inferred, and are pushed.

| Fix | Commit | Why other lanes care |
|---|---|---|
| BFF forwards `X-Trauma-Episode-ID` | `22ead58ff` | Every resus, ED and blood write reached its service **unstamped** — any lane reading a cross-service episode timeline was reading nothing. The surviving test asserted only that the *shell* set the header. |
| `pct.ed.critical_result` routed to `pct.emergency.critical_result` | `fae3270d8` | The estate's only ED safety event rode the `pct.events` catch-all, so rito and notification never heard it. Also absent from **both** maps in `ClinicalEventTopicInventoryTest`, because the guard's maintenance recipe greps `emit("pct.` and this event uses `setEventType()` — recipe corrected. |
| `criticalPayload` projects `encounterRef` | `6fd3ca2d0` | A consumer could not tell which encounter a critical result belonged to; `pct.ed_diagnostic_order` existed partly to work around it. |

## 7. Known defects this pack will fix later, recorded so nobody builds on them

- **`V005__ed_emergency_pathways.sql` is broken.** 8 of its 11 seeded `ED_*` pathways have **zero**
  `pathway_steps` (only `ED_SEPSIS`, `ED_ANAPHYLAXIS`, `ED_TRAUMA` have any), and four cite an
  unrelated source section: `ED_RESP_FAILURE` → "Asthma — adult primary care"; `ED_ECTOPIC` and
  `ED_OBSTETRIC` → "Gonorrhoea — urogenital"; `ED_STATUS_EPILEPTICUS` → "Fever — risk
  stratification". `EdProtocolCatalog` recommends all eleven and a clinician can start any of them.
  **Do not build on those pathway UUIDs until W4 repairs them.**
- **`EdTriageDiscriminatorEngine` scores an unmeasured patient as not-in-danger** — `intVal()` returns
  `0` for an absent key while `vitalsInDangerZone` guards every comparison with `hr > 0 &&`. And
  `EdVisitService:356` does `if (acuity == null) acuity = 3`. Both fixed in W4.
- **`scoreBoth` returns `Math.min(esi, mts)` and overwrites `triage_system`** with whichever system
  scored lower, so a row can be stamped `MTS` when the clinician selected `ESI`.
- **`resuscitation_record` and `resuscitation_phase` carry no `tenant_id` and no `subject_cpid`** —
  reachable only via `activation_id`. A cross-tenant read hazard and a CC-5 anchoring hole before that
  table becomes the resus record for every entry route. Fixed in W6.
- **`ED_SEPSIS` screens on qSOFA**, which Surviving Sepsis Campaign 2026 recommends *against* as a
  single screening tool in favour of NEWS/NEWS2/MEWS/SIRS — and this platform already computes NEWS2
  server-side from versioned content in `EwsCalculatorEngine`.
- **`tuso.clinical_space.space_type` CHECK excludes emergency**
  (`OPERATING_THEATRE|PACU|ICU|HDU|WARD`), so an ED resus bay needs a CHECK widening, not a new table.
- **`inpatient.emergency_activation.protocol_type`** has no default and no CHECK; `"CODE_BLUE"` is a
  Java string literal in `InpatientClinicalService`.
- **`libs/paediatric-domain` is not in the registry** — `services/pom.xml` only. To be backfilled into
  the `libraries:` block when `libs/emergency-domain` is registered.

## 8. Wave index

W0 truth + guardrails (**done**) · W1 `libs/emergency-domain` + standards baseline · W2 episode spine ·
W3 daidzai generalisation (**closes CC-5 violation V-3**) · W4 triage + pathway repair · W5 alerts ·
W6 resus hardening · W7 diagnostics + order sets · W8 medicines + blood · W9 observation +
disposition + acceptance handshake · W10 command view + capacity · W11 MCI · W12 identity proof ·
W13 `mental-health-service` · W14 content tranches 4–12 · W15 experience · W16a TeaVM spike / W16b
offline · W17 indicators · W18 journeys + report · W19 realtime phase 2.
