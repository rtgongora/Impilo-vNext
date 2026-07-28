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
- **When ff-only genuinely fails, MERGE — do not rebase, and do not stash.** "If ff-only fails,
  stop" is insufficient guidance and this pack hit the case: a peer committed locally, then
  recommitted the same work and pushed it, stranding their original commit underneath mine. The
  branch was 2 ahead / 1 behind with two commits carrying **identical patch-ids**.

  `git rebase` is the textbook answer and is wrong here: it demands a clean worktree, and on a
  shared tree the worktree always holds someone else's uncommitted work, so `--autostash` would
  capture a peer's edits into your stash. Merge needs no clean tree, rewrites nothing and destroys
  nothing; a merge commit on a six-session branch is honest rather than noisy.

  Before pushing a merge, prove the peer's change was not doubled:

  ```
  git show <local> | git patch-id --stable      # compare against the remote twin
  git diff origin/$(git branch --show-current) -- <their files> --stat   # must be EMPTY
  git diff --name-only origin/$(git branch --show-current)..HEAD         # must list ONLY your files
  ```

  The duplicate commit stays in history with the same message twice. That is cosmetic; the tree is
  what matters, and the third command is the one that proves it.

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
| `libs/emergency-domain/**` and `libs/burn-domain/**` — **NEW libraries** | `burn-domain` is shared with surgery by design (§5b Decision 1). |
| `apps/mobile/provider-app/src/screens/provider/SpecialtyWorkspacePanel.tsx` — burns and resuscitation tools only | Nobody else claims mobile this wave; the trauma lease held it and that programme is closed. |
| `scripts/runtime-proof/emergency-*.sh`, `scripts/guard/check-emergency-*.sh`, `check-no-ts-clinical-logic.sh`, `check-identity-repoint-coverage.sh` | |
| `docs/clinical/emergency-domain-pack/**`, `docs/clinical-governance/emergency/**` | |

**Shared, requires a handoff:** `services/pom.xml`, `docker-compose.runtime.yml`,
`config/full-boot-service-classification.yml`, `deploy/helm/**`, everything else under
`docs/registry/`, `docs/runbooks/port-allocation.md`.

## 3. Reserved migration blocks

### THE RULE: reserve by numeric DISTANCE, not adjacency. This pack owns the **V2xx band**.

Adjacent reservation is fragile on a contested service, and this pack proved it twice in one hour:

- the trauma lease reserved `inpatient` V035–V064 adjacent to its head; only V035/V036 landed and
  theatre then took V065/V066, so **V037–V064 is permanently unusable** (§3a);
- this file originally reserved `pct` V070–V099 just above a head of V060. Within the hour the Adult
  Medicine lane committed **`V100__problem_model_certainty_and_status.sql`** (`b9579561d`), and
  V070–V099 became dead space by the same mechanism — below the head, so Flyway will never apply it
  in any environment that has reached V100. **I wrote the rule in §3a and then broke it.**

Heads move faster than a reservation can track, so the defence is distance. **Every emergency
migration takes a V2xx number**, in every service. Nothing incremental will reach V200 for years, the
band is collision-proof against any lane reserving above a head, and ownership is legible at a glance
from the version alone. Flyway sorts by version and gaps are free, so the cost is cosmetic and the
safety benefit is not.

### 🔴 THE BAND CONVENTION'S TRAP: a cross-lane FK must live in the OWNER's band

Reserving by numeric distance solves collisions and **creates a dependency-ordering trap**. Flyway
applies migrations in version order, so **a lane in a lower band can never reference a table created
in a higher band** — the referencing migration runs first and the table does not exist yet.

Found the hard way, 2026-07-26. The Adult Medicine lane wrote the `pct_medical_episodes` →
`emergency_episode` foreign key at **V108** exactly as specified, dry-ran it against the preview
schema positive *and* negative, and got green. Reproduced on a clean Postgres:

```
=== FLYWAY ORDER: V108 (FK) runs BEFORE V200 (the table) ===
ERROR:  relation "emergency_episode" does not exist
```

Applied in the *discussed* order (V200 then V108) both succeed, which is why preview was green and a
clean boot would not have been. **The trap is invisible in any environment where the referenced table
is already deployed** — which is every environment except a fresh one, and a fresh one is what a
full-boot is.

**THE RULE: a cross-lane migration must not DEPEND ON an object owned by a higher band. Any such
dependency lives in the band of the lane that owns the depended-on object, never the depending one.**

Stated as "depend on" rather than "reference" at the Adult Medicine lane's suggestion, because it is
the same failure in several disguises: a foreign key, an `INSERT` referencing a higher-band table, an
`ALTER` of one, or a seed row whose lookup resolves against one — all fail identically and all for the
same reason. "Reference" reads as being about foreign keys and would have let the next case through.

So an FK onto `pct.emergency_episode` is written in the V2xx block by this pack, even when the
referencing table belongs to another lane. The referencing lane keeps a pointer in its own block
saying where the constraint lives and why, so table history stays readable rather than looking
forgotten.

**Closed, 2026-07-26:** V108 withdrawn (`ac620b355`), rewritten as **`V201__medical_episode_emergency_fk.sql`**
carrying the Adult Medicine lane's rationale verbatim. Verified on a clean Postgres in Flyway order,
with the negative control the original lacked: V200 then V201 both apply; a dangling
`emergency_episode_id` is rejected by name; a real reference is accepted; and `pg_constraint.convalidated`
is `t`, so the constraint is validated rather than left `NOT VALID`.

This cost is mine: I proposed the band convention (§3) without thinking the dependency direction
through, and a peer lane inherited the consequence. Recorded prominently because it binds every lane
that has adopted a band, not just this pair — Adult Medicine V100s, Emergency V200s, Surgery V300s
means **surgery can reference anything, this pack can reference Adult Medicine's tables, and neither
of the lower bands can reference upward.**

#### The trap got MORE dangerous when out-of-order was enabled (2026-07-28)

`29234046d` set `out-of-order: true` on every band-claimed service — correctly, because a band means
a lane routinely merges a number below one already applied and Flyway's default silently skips those.
But out-of-order **only relaxes ordering on a database where the higher number already applied.** A
fresh database still applies strictly ascending. So after that change:

> A V2xx migration that depends on a V3xx/V4xx/V43x/V5xx object **merges clean, passes CI, and passes
> on every existing environment — and fails only on a fresh boot.** Out-of-order removed the loud
> failure and left the quiet one.

**Concretely, for the waves still ahead of this pack:** `inpatient-service` and `oros-service` now
carry Surgery's **V300**. So a W6 (`inpatient` V200–V229) or W7 (`oros` V200–V219) migration:

- **MAY** add this pack's own columns to `procedure_episode` (e.g. `emergency_episode_id`);
- **MUST NOT** put a CHECK, FK, index or seed lookup on Surgery's `laterality` /
  `site_side_confirmed_by` / `site_side_confirmed_at` / `setting` columns — those are V300 objects and
  a V2xx file referencing them is a fresh-boot bomb;
- **MAY** read and write those same columns freely **from Java**, because application code runs after
  every migration has applied. The distinction is DDL-vs-code, not read-vs-write.

Whenever a constraint genuinely must span the two, it belongs in a migration numbered **above** V300 —
i.e. this pack requests a slot in the owner's band, exactly as Adult Medicine's V108 became V201.

**Verification that catches it:** applying the service's full chain in version order to a throwaway
`postgres:16-alpine` is the *only* check that reproduces a fresh boot. `mvn test` cannot
([[jvm-tests-do-not-apply-migrations]]) and neither can any deployed environment.

**Heads re-verified against disk 2026-07-28** (`ls … | sort -V` + `git status --porcelain`, per §1).
The previous re-verification at `b9579561d` had gone stale in **8 of 12 rows** — `pct` was recorded
as V100 when it is **V500** — and the sub-range map had drifted behind what this pack itself landed.
Sub-ranges marked **✅landed** are consumed; do not re-issue them.

| Service | Head on disk (2026-07-28) | Peer claims | **Emergency block** | Sub-ranges |
|---|---|---|---|---|
| `pct-service` | **V500** | adult medicine V100–V129 · surgery V300–V329 · paeds/IMAM V400–V429 · RMNP V430–V459 · telemedicine V500–V529 · trauma V035–V069 · problem-list V060 (exception, §3c) | **V200–V239** | ✅landed: episode **V200**, medical-episode FK **V201**, triage IITT **V202** · next: alerts **V203** · obs-stay/disposition/handover **V204–06** · diagnostics/order-sets **V207–08** · medicines **V209** · identity ledger **V210** · board **V211–13** · reserve V214–39 |
| `inpatient-service` | **V300** (surgery P4) | surgery V067–V080 **and V300–V329** · adult medicine V111–V130 | **V200–V229** | ✅landed 2026-07-28 (W6a): tenant backfill on `resuscitation_record`/`_phase` (brownfield, backfilled from `emergency_activation`, verified on real Postgres with a pre-seeded legacy row) + `resuscitation_medication` (new, idempotent-retry from creation, correct-never-delete) + `emergency_activation.origin`/`emergency_episode_id` + `protocol_type` CHECK (`NOT VALID` — that column has been fully unconstrained free text since V007) — all in **V200**, one migration. Deliberately did NOT add `subject_cpid` to record/phase: the identity anchor stays ONLY on `emergency_activation` per `InpatientResuscitationRepointHook`'s own design; duplicating it would be a drift bug, not a fix. Next (W6b, not yet built): `resuscitation_event` `client_event_id` + header-only `@Version` on the record's declared scalars + derived (not incremented) summaries + a batch endpoint + the PCT consumer of `EMERGENCY_ACTIVATION_RAISED`. Reserve V201–29. **⚠ Surgery's V300 sits ABOVE this band — see the DDL rule below.** |
| `clinical-knowledge-platform-service` | **V201** | surgery V007–V020 **and V300–V329** · RMNP V007–V009, later V041–V050 · adult medicine V051–V080 | **V200–V229** | ✅landed: void stepless completions **V200**, ED pathway repair **V201** · next: rules framework **V202** · order sets **V203** · tranches **V204–15** |
| `daidzai-service` | **V201** | trauma V010–V049 | **V200–V229** | ✅landed: generalise + PCT back-link **V200**, defer anchor enforcement **V201** · merge/adopt needed no migration (status is free VARCHAR) · next: MCI casualty **V202–04** |
| `madi-service` | V015 | trauma V015–V044 (MTP sub-range **never built**, superseded — §3b) | **V200–V229** | **Contradiction resolved 2026-07-28: use the V200–V229 band, NOT §3b's "V045+".** MHP V200–03 · emergency release V204–05 · ratio content V206 |
| `zibo-service` | **V200** | surgery V008–V014 **and V300–V329** · adult medicine V035–V049 | **V200–V219** | ✅landed: emergency value sets **V200** · reserve V201–19 |
| `tuso-service` | **V052** | surgery **V044–V049** — **overrun on disk**: the facility-readiness lane landed V050/V051/V052 above that ceiling with no lease covering them (flagged to coordinator 2026-07-28) | **V200–V219** | `space_type` widen V200 · ED capacity V201 · trauma-centre capability V202–03 |
| `oros-service` | **V300** (surgery) | trauma V015–V024 · surgery V018–V024 **and V300–V329** | **V200–V219** | `results.acted_at` V200. **⚠ Surgery's V300 sits ABOVE this band — see the DDL rule below.** |
| `inventory-service` (Dura) | V014 | surgery V015–V020 **and V300–V329** | **V200–V219** | emergency kit V200–02 |
| `reporting-service` | V003 | surgery V300–V329 | **V200–V219** | W17 indicators/DSEC mapping V200–02 (band claimed 2026-07-28; §3 previously omitted this service) |
| `rito-quality-safety-service` | V007 | trauma V010–V019 | **V200–V219** | after-action linkage V200 |
| `notification-service` | **V018** | surgery V018–V020 **and V300–V329** | **V200–V219** | emergency templates V200 |
| `vashandi-workforce-service` | V008 | trauma V015–V024 (pre-convention anomaly — migrates to a hundred band at trauma's next wave) · surgery V300–V329 (surgery's own lease is authoritative; the V009–V012 previously recorded here was a stale pre-band draft — corrected by coordinator 2026-07-26) · core workforce keeps the low sequential band V001–V0xx (V009 on-call/swaps landed 2026-07-26) | **V200–V219** | emergency roster view V200 |
| `vito-service` | V048 | trauma V035–V044 | **V200–V219** | provisional-identity hardening V200 |
| `mental-health-service` | — | — | **V001–V030** | new service — no contention, so ordinary numbering |
| `costing-engine-service` (COSTA) | V024 | surgery V025–V028 | **none needed** | emergency override + deferred-charge reconciliation already built (V012/V014) |
| `mvumo-service` | V008 | surgery V009–V014 | **none needed** | `L4_EMERGENCY_OVERRIDE` consent break-glass already built |

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
landed; theatre took V065–V066. The surgery lease reached the same conclusion independently and
leaves them unclaimed to preserve the historical record; this pack does the same. **The trauma lease
§3 row for inpatient is stale and should be annotated.**

**⚠ CORRECTION 2026-07-28 — this section's original reasoning is now false.** It argued the range was
unusable because "Flyway `out-of-order` is not enabled anywhere". That premise was overturned by
`29234046d`, which set `out-of-order: true` on **every** band-claimed service (26 estate-wide,
including all 12 this pack touches). A lower-versioned migration merged today **does** apply on an
environment already past it. So V037–V064 is *mechanically usable* now — the range stays unclaimed by
choice, to preserve the historical record and avoid re-litigating a settled boundary, **not** because
Flyway would refuse it. Anyone reasoning from the old sentence would draw the wrong conclusion about
their own band, which is why this is corrected in place rather than deleted. The dependency-direction
trap above is unaffected and, if anything, sharper — see "The trap got MORE dangerous" in §3.

### 3b. `madi-service` trauma MTP sub-range is superseded

The trauma lease reserved madi V019–V034 for "MTP/O-neg/ratio/transport". None of it was built —
madi's head is V015 and there is no massive-transfusion, uncrossmatched-release or ratio model in the
service. This pack builds it once. The trauma sub-range should be marked superseded rather than left
looking like existing work.

**⚠ CONTRADICTION RESOLVED 2026-07-28.** This section previously said "at V045+" while the §3 table
assigned madi **V200–V229** — two different answers in one document, undecided until a migration was
actually written. **The band wins: W8 writes madi at V200–V229**, consistent with every other service
in §3 and with the reserve-by-distance rule. "V045+" was pre-band drafting and is withdrawn. madi's
head is still V015, so nothing has been written either way and the correction costs nothing.

## 4. Port allocation

**8397 — `mental-health-service`.** Claimed for the definitive mental-health services this pack's
psychiatric-emergency handover must be accepted by (product-owner ruling, 2026-07-26: build a real
service rather than a typed refusal). Descriptive name follows the `patient-safety-service` /
`telemonitoring-service` / `participation-service` precedent; a vernacular product name may be
assigned in the registry later without a folder move.

The plan originally allocated 8395. The surgery/procedures programme claimed **8395
`procedures-service`** and **8396 `surgery-service`** in `c7ceec827` while this pack's W0 was in
flight — a live illustration of why §1 exists. 8398 remains free; 8399 is `referral-service`.

**Onboarding a new service is not just a port**: `services/pom.xml` modules · the seven
`docs/registry/` companion files · `docs/runbooks/port-allocation.md` (**done 2026-07-28** — 8397 row
landed early, deliberately, because that file is what other lanes read and this pack already lost
8395/8396 to a peer mid-flight) · `docker-compose.runtime.yml` · Helm values · full-boot
classification · envoy route (`infra/envoy/envoy.yaml` **and** `envoy-runtime.yaml`, two edit sites
each: a route match and a cluster) · BFF client and controller · UI route registered in
`ui/one-ui-shell/src/lib/routes.ts`.

**⚠ THE `enabled: false` HAZARD — corrected 2026-07-28, this section previously named the wrong
file.** It said a service absent from `config/full-boot-service-classification.yml` regenerates to
`enabled: false`. That is misleading: the classification file is itself **generated from
`docs/registry/services-registry.yaml`**, so a registered service always gets a classification entry
and cannot be "absent" from it independently. Traced to source:

- `scripts/full-boot/generate-full-preview-runtime-values.mjs:285` — `const enabled = waveEnabled.has(entry.id);`
- `waveEnabled` is built (line 249) from **`config/full-boot-waves.yml`**.

**So the real rule is: missing from `config/full-boot-waves.yml` ⇒ `enabled: false` in
`values-full-preview-runtime.generated.yaml` ⇒ silently undeployed.** The umbrella generator does
fail loudly (`validateFullBootWaves` throws before the runtime regen), but two paths bypass it:
running `generate-full-preview-runtime-values.mjs` standalone, and `check-full-boot-waves.sh`, which
invokes the umbrella as `… >/dev/null 2>&1 || true` and swallows the throw.

**Second hazard, previously unrecorded** (same file, lines 276–281): if no port resolves from the
registry or the service's `application.yml`, it falls back to `port = 8310 + hash(id)` with only a
`console.warn`. A service with incomplete port plumbing gets a silent garbage port instead of a
failure. **W13 must put 8397 in BOTH the registry entry (`default_http_port`) and
`services/mental-health-service/src/main/resources/application.yml`.**

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

## 5c. INHERITED SAFETY GAP: nothing verifies site and side

Reported by the surgery/procedures lane, 2026-07-26. **No structured site/side field exists on
`inpatient.procedure_episode`, and no gate refuses to start a procedure whose site and side were not
confirmed.** They are recorded on `referral.surgical_referral` and rendered on the theatre case banner,
but as display text — the WHO Surgical Safety Checklist Time Out asks the question and there is
nowhere to put the answer.

**This lands hardest on emergency care, not on theatre.** Elective surgery has a consent form, a
marked limb and a scheduled Time Out. The lateralised procedures this pack owns are done at the
bedside, at speed, often on an unidentified patient: **chest drain, needle and tube thoracostomy,
central line, arterial line, thoracotomy, joint reduction, escharotomy, burr hole**. Wrong-side chest
decompression is a fatal, fast and entirely preventable error, and it is precisely the procedure most
likely to be performed by a lone clinician under time pressure without a second checker.

The surgery lane fixes it in their **P4** with a structured field plus a start gate on the shared
`procedure_episode` aggregate, declared as standard `PS.WRONG_SITE.PREVENTION` in
`docs/clinical-governance/procedures/standards-baseline.json` so their guard holds them to it. This
pack inherits the fix.

**Binding consequence for this pack:** if an emergency lateralised bedside procedure would ship before
P4 lands, it must either wait for P4 or carry its own laterality confirmation — and it may **not**
ship with laterality as free text. W7 (procedures invocation) is sequenced after their P4 for this
reason; if that ordering changes, this constraint is what has to be re-decided. Recorded in the
honest-gap register either way.

## 5b. Burns — split at the stabilisation boundary

Burns is **in scope for this pack's owned responsibilities and out of scope for definitive
management**, the same boundary the pack applies to trauma and surgery generally. The brief lists
Burns under "invoke the Trauma and Surgery Packs", and that invocation is correct — but three burns
capabilities are squarely inside the list of things Emergency Care owns (immediate stabilisation,
resuscitation, time-critical treatment, transfer), so they cannot be deferred to a downstream pack
that only sees the patient later.

| Capability | Owner | Why |
|---|---|---|
| %TBSA estimation (Lund–Browder, age-adjusted; rule of nines) | **Emergency** — `libs/emergency-domain` arithmetic | An emergency assessment measurement, and the input to fluid resuscitation. Age-adjusted body proportions mean this must route through `paediatric-domain` age banding, not a fixed adult chart. |
| Fluid resuscitation calculation (Parkland: %TBSA × weight × rate, with the first-half/second-half clock) | **Emergency** — `libs/emergency-domain` arithmetic | Time-critical and clock-anchored **from the time of injury, not from arrival** — which is exactly why `time_target_basis` exists as its own column (R9/C.3). Wrong clock, wrong volume. |
| Inhalational-injury / airway assessment | **Emergency** — CKP content, `DANGER_SIGN` + `ESCALATION` layers | An emergency airway decision. Pairs with the environmental tranche's smoke-inhalation rules. |
| Burn-centre referral and transfer criteria | **Emergency** — `emergency_handover(TO_FACILITY)` + disposition CHECKs | A disposition decision, and the acceptance handshake applies unchanged. |
| Definitive burn management — excision, grafting, dressings, nutrition, rehabilitation, scar care | **Surgery / plastics** | Not emergency care. Invoked, not rebuilt. Surgery has not claimed burns in its ADR, lease or standards baseline, so this needs confirming with that lane. |

`episode_class` already carries `BURNS`. Burns **rules** land in tranche 10 (environmental — shared
with smoke inhalation, electrical and lightning injury) and tranche 12 (the trauma/surgical invocation
set). Weight-banded and TBSA-banded thresholds must use RMNP's `bandKey` rather than overloading
`ageDays` (§5a).

### Decision 1 — the arithmetic lives in `libs/burn-domain`, not `libs/emergency-domain`

Accepting the surgery lane's refinement, because the argument is right: %TBSA is not only a
resuscitation input. It drives excision timing, graft planning, nutrition requirement and mortality
prediction for months after the emergency episode closes, so putting it in an emergency library would
make surgery depend on an emergency lib for the whole course of care.

**`libs/burn-domain`** — framework-free (Jackson only, no Spring, no I/O), depending on
`libs/paediatric-domain` for age banding, following the `paediatric-domain` / `reproductive-domain`
pattern: age-adjusted **Lund–Browder** and rule-of-nines TBSA, depth classification, **injury-clocked
Parkland** (total, first-half and second-half windows measured from time of burn), and mortality
scores. Emergency builds it in W1 because emergency needs it now; surgery consumes it rather than
reimplementing. **One Parkland in the estate** matters more than which directory holds it.

Registered in `services/pom.xml` and in the `libraries:` block of `services-registry.yaml` — that
block currently omits `paediatric-domain` and `reproductive-domain` too, and all three are backfilled
in one write (cleared with the RMNP lane).

### Decision 2 — one serial `pct.burn_assessment`, owned by PCT

The surgery lane proposed one series owned by `surgery-service`, first entry written during
resuscitation, and asked this pack to decide. **Ruling: exactly one series, and PCT owns it.**

Their requirement — one series, no parallel acute copy — is the right requirement, and burn depth
declaring over 48–72 hours with %TBSA revised long after the emergency episode closes is exactly why.
But the owner cannot be `surgery-service`:

- **The first entry is written during emergency resuscitation, routinely at a facility with no
  surgical service deployed.** If the series lived in `surgery-service`, recording a TBSA at a
  district hospital would depend on a service that may not be there — violating §20's rule that
  immediate care is never suppressed because an external service is unavailable.
- **CC-2 forbids a component owning person-level longitudinal clinical truth.** A measurement series
  spanning months is precisely that. The established precedent is PCT's own serial-measurement tables:
  `pct.growth_measurements` (V053) and `pct.pct_labour_observations` (V056). A burn assessment is
  structurally identical — a serial structured clinical measurement, written by whichever service is
  in front of the patient at the time.

So: **`pct.burn_assessment`** (emergency block, pct V2xx), a serial time series carrying the region
map, depth per region, computed %TBSA, the assessment clock and a revision reason. Emergency writes
the first entry; surgery writes subsequent entries and its management records reference them. Neither
pack holds a parallel copy.

Note this does **not** contradict HP4 ("findings are coded observations, no syndrome-specific
columns"). HP4 forbids syndrome-specific columns *on the emergency episode*, to stop the pack becoming
a mega-form. A shared serial measurement table owned by PCT and written by two packs across months is
the V053/V056 pattern, not an episode field.

### Lund–Browder chart

The surgery lane's body-map inventory builds "burn and scar map" in their **S16**, which is late in
their programme; this pack needs it now. So emergency builds the chart, they register it in their map
inventory and extend rather than replace. The shell already has a `src/features/body-map` module to
build on.

### Verified state today: burns had shipped, and it was wrong

My first grep was scoped to `services/ ui/ libs/` and missed `apps/` — the surgery lane caught it.
There were **two live calculators**, both defective, now withdrawn in `697a7924b`:

- `ParklandForm` computed `4 × kg × %TBSA` and rendered a single 24-hour volume — **no injury clock,
  no first-half/second-half split**. The `time_target_basis` concern was not a future risk, it was a
  shipped defect that under-resuscitates the late-presenting patient.
- `RuleOf9Form` used fixed **adult** proportions in an app that treats children, under-estimating a
  paediatric burn and therefore the fluid volume derived from it.
- Neither persisted. `RuleOf9Form` raised `Alert("Saved", "TBSA X% recorded locally")` and wrote
  nothing — a clinician told the assessment was recorded had no reason to write it down.

The arithmetic and `RULE9_REGIONS` are **deleted**, not disabled, so a future edit cannot re-route to
them. Had this not been caught, the estate would have had **three** Parkland implementations.

Two menus also advertise burns tools and disagree with each other: the BFF offers 10 specialties × 3
tools, and `apps/mobile/provider-app/src/data/specialtyWorkspaces.ts` offers **18 specialties × 6 =
108 tool labels**, almost none implemented. Tracked as `task_40846f47` and `task_4d5f394f`.

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

## 5d. Estate band convention, and what peer lanes committed

**One band per lane, adopted estate-wide 2026-07-26:** Adult Medicine **V100s** · Emergency
**V200s** · Surgery/Procedures **V300s**. New services keep V001+. The surgery lane adopted the
band after verifying the reasoning and **released its earlier adjacent claims** — `inpatient`
V067–V080, `oros` V018–V024, `ckp` V007–V020, `tuso` V044–V049 and the rest are free for any lane.

The surgery lane's sharper statement of why adjacency fails, worth keeping: *a reservation is a
claim about the future written in a namespace anyone may extend. Adjacency puts the claim exactly
where the next incremental writer will land, so the claim and the collision occupy the same address
by construction.* Three deaths in an hour reads like bad luck; it is the design.

| Lane | Committed blocks |
|---|---|
| **Adult Medicine** | `pct` V100–V129 · `ckp` V051–V080 · `inpatient` V111–V130 · `zibo` V035–V049 · `oros` V050–V069 · `butano` V010–V029 · `telemonitoring` V010–V029 |
| **Surgery / Procedures** | V300–V329 in every co-edited service; `procedures-service` and `surgery-service` V001+ |
| **Emergency (this pack)** | V2xx per §3 |

**STANDING RULE: read the lease files, not the announcement messages.** My own messages to peers
carried block numbers that were stale against this file, and two of them (`inpatient` V067–V094,
`ckp` V010–V029) would have landed straight on top of surgery had anyone acted on them. The
committed lease is the only source of truth; a message is a draft. Adopted as a rule by the Adult
Medicine lane too.

## 5e. The emergency↔medicine handover contract (frozen with Adult Medicine)

Their medical episode links to `pct.emergency_episode` via `emergency_episode_id` and is a
different object: an emergency episode is **one presentation**, a medical episode is the arc of a
problem across contacts, facilities and years (FHIR `EpisodeOfCare`, not `Encounter`). No rival
episode, no rival acuity.

**Acuity and severity are different axes and neither derives from the other.**
`pct_problems.severity` (their V060) is a property of the *disease* — moderate persistent asthma,
Child-Pugh B cirrhosis. IITT priority is a property of the *arrival*. **IITT stops at the door.**
Carrying an acuity forward as a severity is precisely how a triage score becomes a permanent
clinical label, so this pack never writes one from the other.

Their four commitments, which this pack builds against:
1. **Accept is idempotent on `pct_admission_id`** — a retried acceptance creates neither a second
   admission nor a second acceptance.
2. **The clinical record does not restart at the door** — problems raised in emergency stay the
   *same* `pct_problems` rows; they take ownership via `responsible_service`, never by re-recording
   the diagnosis. An internal handover is exactly where re-clerking would fork one disease in two.
3. **Certainty travels, and is usually not CONFIRMED** — a problem raised in emergency is typically
   `SUSPECTED` or `WORKING`. A `WORKING` diagnosis silently promoted to `CONFIRMED` by an admission
   handshake is a diagnosis nobody made.
4. **No timeout discharges responsibility** — a patient nobody has accepted is emergency's patient.

**The link column is fact, not assumption — `pct` V101 has landed.**
`pct_medical_episodes.emergency_episode_id UUID NULL`, with a composite partial index
`idx_pct_medical_episodes_emergency (tenant_id, emergency_episode_id) WHERE emergency_episode_id IS
NOT NULL`. Verified on disk. **No foreign key, deliberately** — `pct.emergency_episode` does not
exist yet and a hard constraint would couple the two lanes' deploy order in both directions. It is a
documented soft reference, validated in application code until V200 lands.

**⚠ V200 IS NOW PUSHED (`dd540d56d`), so the FK is due — and its owner may have gone.** The Adult
Medicine lane reserved the work and then posted a closing note, so this is recorded as an **open
cross-pack item with no confirmed owner**. Everything needed to write it:

| What | Value |
|---|---|
| Target | `pct.emergency_episode(episode_id)` |
| PK shape | single-column `UUID` — their column list needs no change |
| Deletion | **`ON DELETE RESTRICT`**, never CASCADE |
| Rollout | `ADD CONSTRAINT ... NOT VALID`, then `VALIDATE CONSTRAINT` as a separate statement |
| Their block | consumed through **V107**; next free is **V108** |
| Safe because | no `emergency_episode` row is ever hard-deleted — a merge sets `MERGED` + `merged_into_id`, so RESTRICT is a backstop and not a workflow |

**If the Adult Medicine lane does not resume, this pack writes it** under a coordinator handoff
rather than leaving a soft reference indefinitely. A nullable cross-table reference with no owner and
no constraint is exactly the orphan CC-5 exists to reject, and "someone was going to add the FK" is
not a state anyone can audit.

**Also enforced on their side, and it constrains this pack's ED write path:** their V107 models
medication reconciliation as the comparison *event* rather than another medicine list, and
**completion is refused while any discrepancy has no decided action.** An unfinished reconciliation
must never read as a clean one, because "no discrepancies outstanding" is the claim completion makes.
So an emergency admission that triggers an ADMISSION-context reconciliation cannot report it
complete until every discrepancy is dispositioned.

**Decision when V200 lands: promote it to a real FK with `ON DELETE RESTRICT`.** Both tables live in
the `pct` schema and the same service, so there is no cross-service coupling to trade away, and a
dangling `emergency_episode_id` is precisely the orphan this pack's CC-5 discipline exists to reject.
`RESTRICT` not `CASCADE`, consistent with the standing rule that nothing cascades into or out of the
emergency episode. Merging is safe under an FK because a merged episode is marked `MERGED` and never
deleted. The constraint is contingent on this pack's table, so this pack writes it — but
`pct_medical_episodes` belongs to the Adult Medicine lane, so it goes in under a handoff rather than
unilaterally.

Two enforced behaviours on their side that this pack's handover must respect:
- **Closing a medical episode requires an explicit reason and will not default one.** `COMPLETED`,
  `DIED`, `TRANSFERRED`, `LOST_TO_FOLLOW_UP`, `PATIENT_DECLINED` all leave status `FINISHED`;
  defaulting to `COMPLETED` would record a good outcome for a patient who was lost and every outcome
  indicator would inherit it. If an emergency disposition ever closes a medical episode it must say
  which — and the same discipline applies in reverse to this pack's own disposition outcomes.
- **Attaching a problem requires one that already exists and belongs to the same patient.** The
  service refuses to create a problem implicitly, because that would be a second write path into the
  problem list bypassing the duplicate guard. This is the mechanical reason behind
  "references, not content", and it is now enforced rather than agreed — a handover carrying free
  text would be rejected.

**What the handover row must carry, and nothing more:** `emergency_episode_id`, `subject_cpid`,
`journey_id`, the problems raised **as `pct_problems` ids rather than free text**, the disposition,
and the requesting clinician.

**⚠ Will break ED writes if unheeded — their V100 closed the `pct_problems` vocabularies and
RENAMED two values:** `RISK` → `RISK_FACTOR`, `SOCIAL` → `SOCIAL_CIRCUMSTANCE`. Closed sets:
`category` (DIAGNOSIS · SYMPTOM · SYNDROME · GUIDELINE_CLASSIFICATION · RISK_FACTOR ·
FUNCTIONAL_CONSEQUENCE · SOCIAL_CIRCUMSTANCE), `diagnostic_certainty` (SUSPECTED · DIFFERENTIAL ·
WORKING · CONFIRMED · REFUTED, or null), `clinical_status` (ACTIVE · RECURRENCE · RELAPSE ·
REMISSION · INACTIVE · RESOLVED). An out-of-set value returns 400 naming the allowed set. **And
adding a problem already open on the patient now returns 409** carrying the existing problem and two
resolutions — a caller treating 409 as failure will drop the write.

## 5f. STANDING RULE: a hardened server does not survive a careless client

Adult Medicine's most valuable finding, and it hit this pack twice. Their `PatientBanner` read "no
conditions" for every patient in the estate **even though the BFF had already been hardened to
502** — the client destructured only `data` and fell through to `?? []`, so the server-side honesty
fix bought nothing.

The same pattern was live on two emergency surfaces, fixed in `d4b37c810`:
- **the ED trackboard** rendered "No active ED visits." on a failed read — telling a coordinator the
  department is empty, which is the one claim on that screen that stops someone looking;
- **the emergency patient view's medication list** rendered "None active." on a failed read, one
  line below an allergy query that already captured `isError` correctly.

So: **every `useQuery` on an emergency surface must destructure `isError`, and every empty state
must distinguish "none recorded" from "could not be read".** Guard candidates for W15. And the
regression test must be **mutation-proved** — revert the guard, watch it fail — because this is the
layer where the previous fix was silently undone. Still unaudited and owned by this pack: vitals and
the ED-specific queries on `ui/one-ui-shell/src/app/ehr/[patientId]/emergency/page.tsx`.

The `intVal()`-returns-0 defect in `EdTriageDiscriminatorEngine` (§7) is the same family one layer
down: **absence rendering as a reassuring value.** The reassuring default is the one that stops
someone looking.

## 5g. Mobile findings inherited from the burns withdrawal

From the session that landed `19429a2a7` (adopted onto canonical in `3cb08e4b9`):
- **The two specialty menus were never connected.** `fetchSpecialtyWorkspaces()` returns
  `unknown[]` and is not wired to the panel, which renders only the local list. So the BFF's
  10 × 3 and mobile's 18 × 6 = 108 labels have always been independent, and both are wrong.
- **`formKindForTool` routes index 3 of *every* workspace to a generic two-number adder** — so
  "Heart Failure Assessment" and "Sickle Cell Crisis Protocol" labelled an arbitrary sum as a
  clinical result. The burns fix stopped it for burns only.
- **`NotesForm` fakes its save** the same way `RuleOf9Form` did.
- Generalised lesson: **a grep for clinical formula names under-reports**, because implementations
  are named after the component (`ParklandForm`), not the formula. Grep the arithmetic too
  (`4 \* kg`, `* weight *`). This is why §5b's original "burns is absent" claim was wrong.

## 5h. Coordinator rulings taken into scope

**Definition of done, all remaining waves.** A slice is done only when **UI + BFF + contract/API +
backend are all wired** — no orphan endpoints, no UI over demo fixtures. And **every new
service→service call must carry its own `client_credentials` token** (the mvumo
`ClientCredentialsTokenProvider` / pct `ServiceTokenProvider` pattern). **Trust headers are not
authentication**, and a unit test that mocks the client mocks away the 401 — so an S2S call proven
only by a mocked-client test is not proven. This binds every cross-service call this pack adds:
PCT→daidzai continuum-link and adopt, PCT→CKP triage and pathway evaluation, PCT→madi MHP
activation, PCT→inpatient activation, and the emergency→mental-health handover. A coordinated S2S
token wave is building the BFF-side minting seam; this pack consumes it rather than minting its own.

**S2S tokens — the concrete plan, verified.** The BFF-side seam landed on
`origin/worktree-agent-ad437b26ae1ec58a7` (head `5a34930e3`): `ServiceTokenProvider` mints
client_credentials, caches to expiry−30s, and on failure logs `SERVICE_TOKEN_MINT_FAILED` and
returns empty rather than throwing. Interceptor precedence in the shared `serviceRestTemplate`:
(1) an explicitly pre-set `Authorization` wins; (2) an inbound user token is forwarded unchanged and
the provider is never consulted; (3) with no inbound token — schedulers, Kafka consumers, public-lane
composition — the service token is attached.

**But that seam covers only BFF-originated calls.** Every cross-service call this pack adds is
**service-side**: PCT→daidzai (continuum-link, adopt), PCT→CKP (triage, pathways), PCT→madi (MHP),
PCT→inpatient (activation), and emergency→mental-health. Each needs the per-service provider —
copy `services/pct-service/.../integration/ServiceTokenProvider.java`
(`origin/claude/trusting-greider-6464d6` @ `bef6af9f3`, verified reachable) plus helm `secretEnv` per
service. `mvumo` has the sibling `ClientCredentialsTokenProvider` if a second reference is wanted.

**The role caveat does NOT block this pack — checked rather than assumed.** The coordinator flagged
that `service-account-impilo-backend` currently holds no downstream roles beyond realm-management, so
role-guarded endpoints (ndila) will 403 until a realm-role vocabulary is decided. **All four of this
pack's targets are `authenticated()` only, not role-guarded:**

| Service | Posture | Blocked by the role gap? |
|---|---|---|
| clinical-knowledge-platform | `.anyRequest().authenticated()` | No |
| daidzai | `/internal/v1/daidzai/**` `.authenticated()` | No |
| madi | `/internal/v1/madi/**` `.authenticated()` | No |
| inpatient | `.anyRequest().authenticated()` | No |

So W2 and W3 can proceed on the token seam without waiting for the realm-role decision. (Noted in
passing: daidzai and inpatient carry an `else` branch of `anyRequest().permitAll()` behind the
preview/test flag — relevant to the deferred preview→prod auth hardening wave, not to this one.)

**Rig proof pattern, binding on W2:** a positive assertion on authenticated content **plus a negative
control — a tokenless call must 401**. Never absence-of-errors. This is the concrete form of the DoD
clause that a unit test mocking the client mocks away the 401.

**Mobile specialty-workspace slice.** The coordinator's sweep found 66 of 108 labels attach clinical
instrument names to a fake adder or a non-persisting notes box; the burns session does the mechanical
withdrawal and per-lane replacements go through **forms-service governed definitions**. This pack's
slice: **GCS, NIHSS, RASS/ICU sedation**, plus **PHQ-9, GAD-7 and Safety Plan as forms-service
questionnaires now, with interpretation deferred to `mental-health-service` (8397)**. The split
matters — capturing a PHQ-9 score is a form; acting on item 9 is a clinical decision that needs the
service, so shipping the questionnaire without the interpretation is honest only while the deferral
is stated on the surface.

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

W0 truth + guardrails (**done**) · W1 `libs/emergency-domain` (**done**) + standards baseline (**done**:
`docs/clinical-governance/emergency/standards-baseline.json`, 7 EMS standards, guard green, all
DEFERRED with revisit conditions) · W2 episode spine (**done**: pct V200/V201, FSM, service, events,
identity hook) · W3 daidzai generalisation + **CC-5 V-3 CLOSED backend-to-UI** (**done**:
continuum-link + adopt + unanchored sweep, pct wiring on arrival, and the timeline UI showing
linked/unanchored/prehospital; enforcement is a sweep not a CHECK — V200's CHECK broke the live
phase-advance and was withdrawn in V201) ·
**W4a triage demotion (backend done; live rig deploy-gated)** — pct **V202** adds `triage_tool`,
`iitt_priority`, `emergency_signs_json`, `requires_clinician_review`, `review_reason`,
`advisory_scores_json` to `ed_triage_assessment` (additive/nullable, brownfield-safe: the one CHECK
only bites a `triage_tool='WHO_IITT'` row, verified on real Postgres over a pre-seeded legacy row);
`EmergencyTriageDecider` (pure, 13 unit tests) makes WHO IITT the acuity of record — RED→1/YELLOW→3/
GREEN→5, ESI/MTS demoted to advisory that can never set acuity or overwrite the tool, the `Math.min`
reconciliation and the `acuity=3` default both removed, `NOT_TRIAGEABLE`→422 refusal, cross-system
discrepancy raises `requires_clinician_review` not a silent up-triage; `EdVisitService.recordStructuredTriage`
rewritten to call it and persist the new columns, `triageRow` surfaces them. **Contract change:** the
old `autoAcuity`/`scoreBoth`→acuity path is retired — a request with neither runnable IITT (signs/vitals
+ age) nor an explicit clinician acuity now returns 422 instead of acuity 3; an explicit acuity still
stands as a manual entry (and, when IITT can also run, becomes a flagged override with IITT retained as
the tier of record), so legacy explicit-acuity callers keep working. ·
**W4b pathway repair (done, `38c1cca80`)** — ckp V201: 8 stepless `ED_*` pathways given real 2-3 step
content, 4 miscited repointed (ectopic/obstetric→UTI, status→Fever, resp→asthma → correct
ENGINEERING_SEED sections), qSOFA sepsis screen retired for an EWS (SSC26), ED_ECTOPIC gated on
reproductive-age-until-pregnancy-excluded; proven by `scripts/runtime-proof/emergency-pathway-integrity.sh`
(5/5, both-ways). ·
**W4c zibo value sets (done, `e5024acae`)** — zibo V200: 5 CodeSystem+ValueSet pairs backing the real
pct enums (priority/tool/entry-route/class/state), real-DB verified, state codes drift-checked identical
to pct chk_ee_state, EmergencyValueSetValidationTest 2/2. ·
**Still W4d:** BFF/UI triage capture surface (surface iitt_priority + requires_clinician_review; map the
pct 422 instead of swallowing it to 502) — live proving waits for the pct redeploy + experience-bff
rebuild. · W5 alerts ·
W6 resus hardening · W7 diagnostics + order sets · W8 medicines + blood · W9 observation +
disposition + acceptance handshake · W10 command view + capacity · W11 MCI · W12 identity proof ·
W13 `mental-health-service` · W14 content tranches 4–12 · W15 experience · W16a TeaVM spike / W16b
offline · W17 indicators · W18 journeys + report · W19 realtime phase 2.
