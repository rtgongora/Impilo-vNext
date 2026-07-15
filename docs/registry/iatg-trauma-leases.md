# Trauma-Care Pipeline — Lease Record

Delivery-boundary record for the **Trauma-Care Pipeline Full Build** program (opened 2026-07-15,
base anchor `b343a0919`, branch `claude/staging-ux-orchestration-remediation-Yypyl`). This file is
the single source of truth for what each concurrent session may and may not touch while the trauma
program is in flight on the **shared checkout**.

Plan: `~/.claude/plans/impilo-vnext-trauma-pipeline-snug-shell.md`. Discovery verdict: the trauma
pipeline is largely unbuilt as a coherent journey (real DAIDZAI incident spine + PCT ED + MADI
single-unit blood, but no EMS state machine, no prehospital ePCR, no canonical episode, dead
cross-service Kafka seams). Gate 1 (single-patient trauma spine) **blocks the fullboot preview deploy**.

---

## 1. Program-wide invariant

> **Three writers share ONE working tree on ONE branch** (trauma build session · "Sprint closure
> issues review" session · coordinator). Same-checkout collisions are the top hazard.

- **Commit token (index-race mutex):** only one writer touches the git index at a time. Announce →
  path-scoped `git add <your files>` (**never `git add -A`**) → atomic conventional commit → push →
  release. Serialised through the coordinator.
- **No-rebase-on-merge law:** after a peer merges, `git pull --ff-only` (never `--rebase` on shared
  history). If ff-only fails, **stop and hand off to the coordinator**.
- **Migration numbers:** only ever use numbers inside your reserved block (§3). Never renumber a
  pushed migration.

## 2. Lane ownership (by service / directory)

| Lane | Owns (may edit) | FORBIDDEN |
|---|---|---|
| **Trauma build** | `services/daidzai-service/**`, `services/dispatch-service/**`, `services/nhume-service/**`, `services/pct-service/**`, `services/inpatient-service/**`, `services/oros-service/**`, `services/madi-service/**`, `services/vito-service/**` (merge path only), `services/notification-service/**` (trauma templates only), `services/rito-quality-safety-service/**`, `services/vashandi-workforce-service/**` (roster read only), `services/experience-bff/**` (trauma controllers only: `EdWorkflowController`, `CareEmergencyInpatientController`, `DaidzaiController`, `MadiController`), `apps/mobile/provider-app/**`, `apps/mobile/citizen-app/**` (emergency screens), `apps/mobile/maestro/flows/emergency-*`, `ui/one-ui-shell/src/app/{emergency,clinical/emergency,work/daidzai}/**` + related e2e specs, `scripts/runtime-proof/trauma-*.sh` | Any file outside this list; shared files (§below); vashandi write-side (roster **read** only) |
| **Sprint closure** | Its own disjoint issue set (coordinator to confirm it does not overlap the trauma lane) | Every path in the Trauma-build lane above |
| **Coordinator** | Shared files: `services/pom.xml`, `docker-compose.runtime.yml`, `docs/registry/*` (incl. this file), `services-registry.yaml`, `system-of-record-map.md`; cross-session relay; deploy authorisation | Self-authorised deploy before Gate 1 |

**Shared-file rule:** `services/pom.xml`, `docker-compose.runtime.yml`, and everything under
`docs/registry/` are **coordinator-only** edits. A lane needing one requests it via coordinator handoff.

## 3. Reserved migration blocks (same-branch collision guard)

**Blocker fixed first:** `pct-service` had a live duplicate `V034` (`V034__telemetry_facility_nullable.sql`
+ `V034__virtual_pool_queues.sql`) → Flyway `validate` fails. Rename `virtual_pool_queues` → `V035`
before any new pct migration.

| Service | Current top | Reserved trauma block | Sub-ranges |
|---|---|---|---|
| daidzai | V003 | **V010–V049** | episode V010–14 · EMS V015–29 · ePCR V030–39 · merge/events V040–44 |
| pct | V034 (dup) | **V035 dedupe, then V036–V069** | episode-stamp V036–40 · ED-unify V041–50 · provisional/reconcile V051–56 · merge V057–60 |
| inpatient | V030 | **V035–V064** | episode-stamp V035–39 · resus re-key + `resuscitation_event` V040–49 · ward-namespace V050–54 · merge V055–59 |
| madi | V007 | **V015–V044** | episode-stamp V015–18 · MTP/O-neg/ratio/transport V019–34 · guard V035–38 · merge V039–42 |
| nhume | V006 | **V015–V024** | emergency-priority authz + audit |
| dispatch | V004 | **V010–V019** | emergency-priority authz + audit |
| vito | V032 | **V035–V044** | provisional-emergency + merge-emit hardening |
| vashandi-workforce | V007 | **V015–V024** | EMS crew-assignment + trauma-team roster view |
| oros | V014 | **V015–V024** | critical-result ack |
| rito | V002 | **V010–V019** | after-action → episode linkage |
| referral | V002 | **V010–V019** | inter-facility trauma referral episode-stamp |
| ndila | V002 | **V010–V019** | EMS routing/ETA support |

Gaps below each block (e.g. daidzai V004–V009) are intentionally left for incidental non-trauma
migrations so an unrelated session never collides with the trauma block.

## 4. Gate-1 deploy gate

The fullboot preview deploy is **coordinator-owned** and blocked until, in one run:
all `J-TR-0..9` PASS in `scripts/runtime-proof/trauma-spine-journeys.sh` + `J-TR-M1` (mobile ePCR
Maestro) green + `mvn -pl <touched services> test` green + no new dead-letter rows. See the plan's
Gate-1 checklist (G1.1–G1.16). No self-authorised deploy.

## 5. Wave / journey index

W0 unblock+foundation · W1 episode spine (J-TR-0) · W2 EMS dispatch (J-TR-1) · W3 mobile ePCR
(J-TR-2 + J-TR-M1) · W4 ED unify + roster→ack→escalate (J-TR-3) · W5 ABCDE resus + critical-ack
(J-TR-4) · W6 blood gate + VITO reconcile + drainage + negatives (J-TR-5..9) · **GATE 1** ·
W7–W14 post-gate breadth.
