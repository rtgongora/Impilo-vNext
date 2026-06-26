# Recent Claude Branch Closeout — INVENTORY Edition

> **Scope of this document:** discovery + classification + safety branch only.
> **NO merges, NO deletes, NO cherry-picks, NO force-pushes, NO history rewrites were performed.**
> Many candidate branches are under **active development by other live sessions**. This is the
> read-only inventory that feeds the later, separate destructive integration gate — which runs
> **only after all builders quiesce**.
>
> **Scope filter (per mandate):** branches **we created** (author `Robert Tawanda Gongora` / Claude work)
> **AND** last commit **within the last 72 hours**. The 72h anchor is the newest commit in the repo,
> `2026-06-26 05:23` → boundary `2026-06-23 ~05:23`. Branches owned by others, or whose last commit
> predates the window, are listed under **Explicitly excluded** and are **not** classified candidates.

## Run metadata

| Field | Value |
|---|---|
| Report date (repo HEAD `%cd`) | Thu Jun 25 17:34:28 2026 +0200 |
| System calendar date | 2026-06-26 |
| 72h window | `2026-06-23 05:23` → `2026-06-26 05:23` |
| Target Product Truth branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| Product Truth HEAD | `6d522d291085f5ac002bb127f27a07e61a0e7bfb` — `docs(product-truth): pin G055 to committed feature block H5` (Wed Jun 24 04:36:13 2026 +0200) |
| Safety branch (created **and pushed**) | `safety/product-truth-before-recent-branch-closeout-20260625-1734` → `6d522d291` |
| Working tree at run time | **DIRTY** on `intake/oros-diagnostics-journey` (an ACTIVE branch). Per mandate: **not stashed, not discarded.** All work done read-only + via a detached `git worktree`; the active checkout was never disturbed. |
| Report branch | `intake/branch-closeout-inventory` (off canonical; created in a throwaway worktree) |

### Dirty working-tree files (left untouched — belong to the active OROS session)
`.claude/settings.local.json`, `docs/audits/full-product-truth-recovery-report.md`,
`docs/audits/product-truth-backend-ui-traceability.md`, `docs/audits/product-truth-cross-service-cohesion.md`,
`docs/audits/product-truth-frontend-backend-traceability.md`, `docs/audits/product-truth-gap-register.md`,
`docs/audits/product-truth-service-inventory.md`, `docs/product/service-completion-blueprints.md`,
`reports/full-boot/preview-generation.json`, `reports/full-boot/preview-generation.md`,
`reports/product/product-truth.json`,
`services/dispatch-service/.../config/SecurityConfig.java`,
`services/experience-bff/.../controller/DisplaySettingsControllerTest.java`.

---

## Classification legend

| Class | Meaning |
|---|---|
| **already-absorbed** | Content (feature files) is present and **identical** on canonical; only stale product-truth/doc noise differs. Nothing to integrate. |
| **clean-merge-candidate** | Canonical is an ancestor (or near it); branch fast-forwards or merges trivially with real unmerged value. |
| **cherry-pick-candidate** | Has focused unmerged value but diverged; integrate selected commits, not the whole branch. |
| **superseded** | Branch's intent was re-implemented on canonical via different commits; branch is now far behind and stale. |
| **obsolete** | No remaining value. |
| **NEEDS-HUMAN-REVIEW** | Ambiguity a human must resolve before any integration. |
| **ACTIVE-DO-NOT-TOUCH** | Mid-flight under a live session; the integrator handles it only after it finishes. |

Evidence convention: **ahead/behind** = `git rev-list --left-right --count <canonical>...<branch>`
(`left` = commits in canonical not in branch; `right` = commits in branch not in canonical).
"Tip delta" = two-dot `git diff --stat <canonical> <branch>` (real tip-vs-tip difference; deletion-dominated = branch is simply behind canonical).

---

## Classified branch table (in scope: ours + ≤72h — 10 branches)

| Branch | Last commit (date · author · SHA · subject) | ahead/behind (L/R) | Tip delta (files / +ins / −del) | Touch areas | Class | Evidence / notes |
|---|---|---|---|---|---|---|
| `intake/citizen-zero-to-one` | 2026-06-26 05:23 · R. Gongora · `941c97f40` · docs(audit): mark TPL-1 fixed | 0 / 126 | 529 / 29270 / 2964 | mobile citizen-app, experience-bff, **mvumo** (legal consent), **tshepo-authz** (JWT-over-headers TPL-1, step-up/OTP/TOTP), libs/tshepo-trust-crypto, OPA, registry, product-truth | **ACTIVE-DO-NOT-TOUCH** | Commit **hours old**. Critical security fix in flight (TPL-1 JWT authoritative over client trust headers). Carries G-CZO consent journey + a large shared substrate (security batch F, CDS Phase 1, OROS Wave 2) that overlaps other active branches. |
| `intake/oros-diagnostics-journey` | 2026-06-25 17:34 · R. Gongora · `3461cb936` · feat(oros,madi): event-driven blood-bank loop (O19) | 0 / 166 | 614 / 37039 / 5014 | oros, madi, channels (notify-only), pacs, mobile provider-app, experience-bff, interop adapters (HL7/DICOM/FHIR), docker-compose.interop, libs/tshepo-trust-crypto | **ACTIVE-DO-NOT-TOUCH** | **Current dirty checkout.** OROS↔MADI loop, O1–O19 wave, FHIR/HL7/DICOM interop adapters, blood-bank SLA timers. Largest in-flight branch. |
| `intake/khuluma-comms-hub` | 2026-06-25 17:29 · R. Gongora · `8ed3fddab` · test(khuluma): real message render mobile | 0 / 122 | 539 / 29150 / 2908 | **khuluma-service** (new), rtc-gateway/LiveKit, pct teleconsult, web `/work/comms`+`/my/comms`, mobile Comms Hub, experience-bff, OPA `khuluma.rego`, compose | **ACTIVE-DO-NOT-TOUCH** | Comms/RTC orchestration ("Impilo Live"), W1.1→U1 + R2/R3 live calls. New service + new OPA policy + compose wiring. |
| `intake/wave-b-tshepo-gdhcn-trust-primitives` | 2026-06-25 14:33 · R. Gongora · `d99378846` · docs(oros-cds): Phase 1 keystone | 0 / 110 | 472 / 25710 / 2917 | **clinical-knowledge-platform** (interpretation engine, CDS Phase 1a–1f), zibo ObservationDefinition, guidance (LLM), experience-bff, vashandi (workforce SoR), libs/tshepo-trust-crypto | **ACTIVE-DO-NOT-TOUCH** | CDS interpretation engine + GDHCN trust primitives + Wave I batches + vashandi workforce consolidation. Heavy overlap with citizen-zero-to-one substrate. |
| `intake/b3-dags-permit-key` | 2026-06-24 04:52 · R. Gongora · `c40153b67` · docs(product-truth): close G003, record G056 | 0 / 2 | 5 / 83 / 13 | **data-access-governance-service** (DAGS `EnforcementService`), product-truth gap register | **clean-merge-candidate** | **Canonical is a direct ancestor → fast-forward-able.** Real unmerged fix: fail-closed permit signing key + strong requester binding (G003); records G056 (permit-signature-never-verified). The one genuinely-unmerged code branch in scope. |
| `intake/a2-golden-thread-partials` | 2026-06-24 04:29 · R. Gongora · `def5ae043` · docs(product-truth): record G055 | 3 / 2 | 1 / 1 / 11 | clinical-knowledge-platform (`ClinicalContextEnricher`), product-truth | **already-absorbed** | Feature file `ClinicalContextEnricher.java` **present & identical** on canonical (services delta empty). Only a stale product-truth doc line differs. Nothing to integrate. |
| `intake/clinical-knowledge-placeholder` | 2026-06-23 21:21 · R. Gongora · `7428e31fd` · chore(product-truth): ratchet | 14 / 3 | 21 / 74 / 1048 | clinical-knowledge-platform rules engine, product-truth docs/baseline | **superseded** | Services delta is deletion-dominated (9 files, +9/−737) → branch is behind; specialist-only gating already on canonical. Stale. |
| `intake/community-moderation-authz` | 2026-06-23 21:09 · R. Gongora · `f785c235d` · chore(product-truth): ratchet | 14 / 3 | 23 / 72 / 853 | community-service (pin authz), product-truth | **already-absorbed** | `SocialServicePinAuthzTest.java` present; **services delta empty** → feature identical on canonical. Only product-truth doc noise remains. |
| `intake/vito-demographics-update-parity` | 2026-06-23 20:32 · R. Gongora · `abd3720c3` · test(vito): demographics round-trip | 14 / 2 | 21 / 101 / 834 | vito-service (extended demographics preserve), product-truth | **already-absorbed** | `ClientUpdateServiceTest.java` present; **vito services delta empty** → feature identical on canonical. Doc noise only. |
| `intake/product-truth-scanner-honesty` | 2026-06-23 19:24 · R. Gongora · `aae4494cf` · chore(report): honest gaps | 20 / 6 | 25 / 108 / 1147 | completeness scanner (`generate-product-truth.mjs`, `product-truth-gaps.mjs`), guard scripts, maturity-model doc, baseline.json | **already-absorbed — RESOLVED** | Honesty artifacts (`product-truth-maturity-model.md`, `product-truth-baseline.json`, `__tests__/product-truth-truth.test.mjs`) all present on canonical. **Re-smoothing concern investigated and CLEARED** — see "Resolution" section below. The `−77` lines were the honesty branch being the older/simpler version; canonical's scanner is **stricter**, not weaker. |

---

## ACTIVE-DO-NOT-TOUCH list (explicit)

The destructive integration gate must **not** merge, delete, rebase, or force-touch any of these until the owning session confirms completion.

**On origin, mid-flight (recent commits):**
1. `intake/citizen-zero-to-one` — commit **hours old** (Jun 26 05:23); carries a critical in-flight security fix (TPL-1).
2. `intake/oros-diagnostics-journey` — the **current dirty checkout**; OROS↔MADI O1–O19 wave active.
3. `intake/khuluma-comms-hub` — Comms/RTC orchestration, new service + OPA policy still landing.
4. `intake/wave-b-tshepo-gdhcn-trust-primitives` — CDS interpretation engine + trust primitives active.

**Named as active but NOT on origin (local-only worktrees in other sessions — confirmed via `git worktree list`; do NOT recreate or push over):**
5. `intake/fundo-lms` — live worktree `/opt/impilo/repos/impilo-fundo`.
6. `intake/provider-clinical-place-design` — live worktree `/opt/impilo/repos/impilo-pcp`.
7. `intake/czo-ws-opa`, `intake/czo-ws-deleg-be`, `intake/czo-ws-deleg-ui` — absent on origin (local-only / not yet pushed).

> ⚠️ Cross-branch coupling: the four active **on-origin** intake branches **share a large common substrate**
> (security batch F, `libs/tshepo-trust-crypto`, CDS Phase 0–1, OROS Wave 2, the
> `g046-remove-oauth-offswitch` sweep across ~23 services, runtime-proof scripts). When they
> eventually integrate, expect heavy overlap — integrate the shared substrate **once**, then the
> branch-specific deltas, rather than merging all four blindly.

---

## Resolution — product-truth scanner "re-smoothing" concern (read-only probe, 2026-06-26)

**Question:** did canonical's product-truth scanner silently re-smooth gaps back toward "0" relative to the honest-scanner branch?

**Verdict: NO. Concern cleared.** Canonical's scanner is **strictly stricter** than the original honesty branch, and the falling gap count traces to **genuine fixes**, not suppressed detection.

Evidence (all read-only, via `git show` / `git diff` on origin refs):

1. **Detectors strengthened, not weakened** (`git diff <honesty> <canonical> -- scripts/completeness/generate-product-truth.mjs`):
   - `MOCK_STUB_PATTERNS` widened (added `mock-data`, `mockedData`, `fakeData`, `demoData`).
   - `scanInMemoryStore` widened — original Rule 1 (`*Store.java` + concurrent field) **kept**, plus new Rule 2 catching Controller/Service static mutable backing collections (e.g. a seeded `CopyOnWriteArrayList`).
   - New detector `scanStubMarkers` (stub-placeholder + `TODO: wire/implement`).
   - `scanSecurityPlaceholders` retained and wired into `scanServiceModule`.
   - (The `−77` lines flagged earlier were simply the honesty branch being the older, smaller version; canonical has *more* detector code.)
2. **Lock-test intact:** `scripts/completeness/__tests__/product-truth-truth.test.mjs` is present and **identical (181 lines)** on both canonical and `intake/oros-diagnostics-journey`. The test that locks honest maturity + detector behaviour was never removed.
3. **Detectors still fire and are honestly reported on canonical** — its actual gap list still names a real **S/blocker** (`mushe-wallet-service` security placeholder) and **S/high** (`experience-bff` security placeholder). Not suppressed; canonical is not at 0.
4. **Gap-count trajectory = real closures** (`summary.gapCounts.total`): honesty **7** → canonical **6** → oros **4**. Between canonical and oros the two **S-category security placeholders closed** (security batch F: `g046` oauth-off-switch removal across ~23 services, vito SMART-card fail-closed key, DAGS permit key; plus citizen TPL-1 JWT-over-headers), and mock/stub hit counts fell with real de-fabrication (experience-bff 10→7, mushe-wallet 3→2). The remaining 4 F-gaps (experience-bff + mushe-wallet mock/stub, `/wellness/commodities`, `/operations/facility-operations`) are still **openly flagged** on oros — i.e. honestly retained, not zeroed.

**Residual caveat (low risk, optional human spot-check):** detector firing and S-gap closure were confirmed via named architectural commits, but a line-by-line check that the mushe-wallet S/blocker fix is substantive (real fail-closed change) rather than a reworded string was **not** performed. Given the commit content, confidence is high; flagged only for completeness.

---

## Recommended integration ORDER (for the later destructive gate ONLY — not executed here)

Run only after **all** ACTIVE branches quiesce. Re-verify each branch at that time (this inventory is a point-in-time snapshot). Suggested layering:

1. **Product-Truth / guard / docs first** — reconcile the scanner + gap register.
   `intake/product-truth-scanner-honesty` is **already absorbed and cleared** (see Resolution above — no re-smoothing; canonical's scanner is stricter). No action needed beyond keeping the honest detectors + the 181-line lock-test in place when later branches regenerate the dataset.
2. **Backend services** — `intake/b3-dags-permit-key` (clean FF; the only genuinely-unmerged code in scope). Then the active branches' backend layers in dependency order: shared `libs/tshepo-trust-crypto` + tshepo-authz/security substrate → vito/registry → clinical-knowledge-platform/zibo (CDS) → oros/madi/channels → khuluma-service → mvumo consent.
3. **BFF** — experience-bff endpoints behind each backend (interpretation proxy, comms controllers, OROS observation/specimen, consent/legal-agreement).
4. **Web UI** — one-ui-shell routes (comms, CDS interpreted flags, OROS worklists/admin catalogue, registry demographics).
5. **Mobile** — citizen-app + provider-app parity (Comms Hub, trust banner, diagnostics, clinical records).
6. **Tests / regeneration last** — run the full suite, regenerate product-truth + full-boot preview, re-assert guards, confirm honest gap count did not regress.

---

## Explicitly excluded (out of scope under the ours + ≤72h filter)

Recorded for transparency; **not** classified candidates this run.

**Not ours (different author) — leave to owners:**
- `staging` — author `tndangana` (Jun 23 11:20). Within the time window but **not our branch**; unmerged native-iOS + SOS/emergency mobile work. Coordinate with owner separately.
- `ioptime/dev` — external contributor `fwdali1824` (Jun 18). Not ours **and** outside window; raw IoT/telemedicine fork on the removed `ui/experience/` tree.

**Ours but outside the 72h window (last commit before 2026-06-23 05:23):**
- `claude/product-truth-recovery` — Jun 23 03:22 (just outside). Note: already fully absorbed (0 unique commits vs canonical) — no value lost by exclusion.
- Jun 17–18 intake set: `ui-experience-archaeology-closure`, `telemedicine-rtc-strategy-gate`, `pct-triage-imaging-links`, `pacs-imaging-annotation-persistence`, `dicom-governed-upload-workflow`, `registry-extended-demographics-persistence`, `ioptime-dicom-phase-a-dwv`, `ioptime-lift-adapt-verify`. Spot-checked earlier: their feature paths are already present on canonical (re-implemented / superseded), so exclusion loses no unmerged value — but they were **not** re-classified under this run's narrowed scope.

**Older long-lived branches** (well outside window, not part of this wave): `impilo-fundo-upgrade`, `fix-impilo-fundo`, `fix-migrations`, `split/pr3-fundo-ui`, `split/pr4-stabilization`, `peter/vnext-2.0`, `peter/vnext-1.0`, `production`, `local/*`, and the April `claude/*` branches.

---

## What this run did and did NOT do

**Did (safe, non-destructive):**
- `git fetch --all --prune`; verified (dirty) tree state without modifying it.
- Created **and pushed** safety branch `safety/product-truth-before-recent-branch-closeout-20260625-1734` @ `6d522d291`.
- Discovered + classified the in-scope recent-wave branches (ours, ≤72h) with full ahead/behind, unique-commit, changed-file, and tip-delta evidence.
- Probed canonical for feature-file presence to distinguish absorbed vs unmerged.
- Wrote + pushed this inventory on `intake/branch-closeout-inventory` (via an isolated worktree).

**Did NOT (per hard limits):** no `git merge`, no `git cherry-pick`, no branch deletes, no force-push, no history rewrite, no stash/discard of the active dirty working tree. All integration items above are **recommendations only**.
