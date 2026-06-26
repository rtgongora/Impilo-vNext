# Recent Claude Branch Closeout — INVENTORY Edition

> **Scope of this document:** discovery + classification + safety branch only.
> **NO merges, NO deletes, NO cherry-picks, NO force-pushes, NO history rewrites were performed.**
> Many candidate branches are under **active development by other live sessions**. This is the
> read-only inventory that feeds the later, separate destructive integration gate — which runs
> **only after all builders quiesce**.

## Run metadata

| Field | Value |
|---|---|
| Report date (repo HEAD `%cd`) | Thu Jun 25 17:34:28 2026 +0200 |
| System calendar date | 2026-06-26 |
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

## Classified branch table

| Branch | Last commit (date · author · SHA · subject) | ahead/behind (L/R) | Tip delta (files / +ins / −del) | Touch areas | Class | Evidence / notes |
|---|---|---|---|---|---|---|
| `intake/citizen-zero-to-one` | 2026-06-26 05:23 · R. Gongora · `941c97f40` · docs(audit): mark TPL-1 fixed | 0 / 126 | 529 / 29270 / 2964 | mobile citizen-app, experience-bff, **mvumo** (legal consent), **tshepo-authz** (JWT-over-headers TPL-1, step-up/OTP/TOTP), libs/tshepo-trust-crypto, OPA, registry, product-truth | **ACTIVE-DO-NOT-TOUCH** | Commit **hours old**. Critical security fix in flight (TPL-1 JWT authoritative over client trust headers). Carries G-CZO consent journey + a large shared substrate (security batch F, CDS Phase 1, OROS Wave 2) that overlaps other active branches. |
| `intake/oros-diagnostics-journey` | 2026-06-25 17:34 · R. Gongora · `3461cb936` · feat(oros,madi): event-driven blood-bank loop (O19) | 0 / 166 | 614 / 37039 / 5014 | oros, madi, channels (notify-only), pacs, mobile provider-app, experience-bff, interop adapters (HL7/DICOM/FHIR), docker-compose.interop, libs/tshepo-trust-crypto | **ACTIVE-DO-NOT-TOUCH** | **Current dirty checkout.** OROS↔MADI loop, O1–O19 wave, FHIR/HL7/DICOM interop adapters, blood-bank SLA timers. Largest in-flight branch. |
| `intake/khuluma-comms-hub` | 2026-06-25 17:29 · R. Gongora · `8ed3fddab` · test(khuluma): real message render mobile | 0 / 122 | 539 / 29150 / 2908 | **khuluma-service** (new), rtc-gateway/LiveKit, pct teleconsult, web `/work/comms`+`/my/comms`, mobile Comms Hub, experience-bff, OPA `khuluma.rego`, compose | **ACTIVE-DO-NOT-TOUCH** | Comms/RTC orchestration ("Impilo Live"), W1.1→U1 + R2/R3 live calls. New service + new OPA policy + compose wiring. |
| `intake/wave-b-tshepo-gdhcn-trust-primitives` | 2026-06-25 14:33 · R. Gongora · `d99378846` · docs(oros-cds): Phase 1 keystone | 0 / 110 | 472 / 25710 / 2917 | **clinical-knowledge-platform** (interpretation engine, CDS Phase 1a–1f), zibo ObservationDefinition, guidance (LLM), experience-bff, vashandi (workforce SoR), libs/tshepo-trust-crypto | **ACTIVE-DO-NOT-TOUCH** | CDS interpretation engine + GDHCN trust primitives + Wave I batches + vashandi workforce consolidation. Heavy overlap with citizen-zero-to-one substrate. |
| `intake/b3-dags-permit-key` | 2026-06-24 04:52 · R. Gongora · `c40153b67` · docs(product-truth): close G003, record G056 | 0 / 2 | 5 / 83 / 13 | **data-access-governance-service** (DAGS `EnforcementService`), product-truth gap register | **clean-merge-candidate** | **Canonical is a direct ancestor → fast-forward-able.** Real unmerged fix: fail-closed permit signing key + strong requester binding (G003); records G056 (permit-signature-never-verified). The one genuinely-unmerged code branch in the closed group. |
| `intake/a2-golden-thread-partials` | 2026-06-24 04:29 · R. Gongora · `def5ae043` · docs(product-truth): record G055 | 3 / 2 | 1 / 1 / 11 | clinical-knowledge-platform (`ClinicalContextEnricher`), product-truth | **already-absorbed** | Feature file `ClinicalContextEnricher.java` **present & identical** on canonical (services delta empty). Only a stale product-truth doc line differs. Nothing to integrate. |
| `intake/clinical-knowledge-placeholder` | 2026-06-23 21:21 · R. Gongora · `7428e31fd` · chore(product-truth): ratchet | 14 / 3 | 21 / 74 / 1048 | clinical-knowledge-platform rules engine, product-truth docs/baseline | **superseded** | Services delta is deletion-dominated (9 files, +9/−737) → branch is behind; specialist-only gating already on canonical. Stale. |
| `intake/community-moderation-authz` | 2026-06-23 21:09 · R. Gongora · `f785c235d` · chore(product-truth): ratchet | 14 / 3 | 23 / 72 / 853 | community-service (pin authz), product-truth | **already-absorbed** | `SocialServicePinAuthzTest.java` present; **services delta empty** → feature identical on canonical. Only product-truth doc noise remains. |
| `intake/vito-demographics-update-parity` | 2026-06-23 20:32 · R. Gongora · `abd3720c3` · test(vito): demographics round-trip | 14 / 2 | 21 / 101 / 834 | vito-service (extended demographics preserve), product-truth | **already-absorbed** | `ClientUpdateServiceTest.java` present; **vito services delta empty** → feature identical on canonical. Doc noise only. |
| `intake/product-truth-scanner-honesty` | 2026-06-23 19:24 · R. Gongora · `aae4494cf` · chore(report): honest gaps | 20 / 6 | 25 / 108 / 1147 | completeness scanner (`generate-product-truth.mjs`, `product-truth-gaps.mjs`), guard scripts, maturity-model doc, baseline.json | **already-absorbed → NEEDS-HUMAN-REVIEW** | Honesty artifacts (`product-truth-maturity-model.md`, `product-truth-baseline.json`, `__tests__/product-truth-truth.test.mjs`) all **present on canonical**, and canonical's scanner has since evolved further (scripts delta would *remove* 77 lines / add 6 → branch is the older, simpler version). **Human check required:** confirm canonical's scanner did not silently re-smooth gaps back toward "0" (per standing guidance the honest gap model must not regress). |
| `claude/product-truth-recovery` | 2026-06-23 03:22 · R. Gongora · `188549aac` · fix(deploy): digest JSON via temp file | 25 / 0 | 51 / 2045 / 21915 | (none unique) | **already-absorbed** | **0 unique commits** — canonical is strictly ahead. Fully contained. |
| `intake/ui-experience-archaeology-closure` | 2026-06-18 04:44 · R. Gongora · `c84f4749a` · docs(absorption): close ui archaeology | 82 / 1 | 507 / 1022 / 90117 | 1 absorption report | **superseded** | Report file present on canonical; branch is ~82 behind. |
| `intake/telemedicine-rtc-strategy-gate` | 2026-06-18 04:44 · R. Gongora · `eb7e14fec` · docs(telemedicine): RTC decision | 83 / 1 | 508 / 1022 / 90203 | 1 absorption report | **superseded** | Report present on canonical; ~83 behind. (RTC strategy now realized in active `khuluma-comms-hub`.) |
| `intake/pct-triage-imaging-links` | 2026-06-18 04:41 · R. Gongora · `0a2ffda09` · feat(triage): linked imaging | 86 / 3 | 509 / 1022 / 90279 | pct-service, experience-bff, one-ui-shell encounter, Flyway `V014` | **superseded** | All feature paths (`ImagingLinkService`, `V014__triage_imaging_links.sql`, BFF `TriageImagingLinkController`, UI panels/hooks) **present on canonical**; branch now far behind. Re-implemented. |
| `intake/pacs-imaging-annotation-persistence` | 2026-06-18 04:33 · R. Gongora · `98ed99ed3` · feat(imaging): save annotations | 89 / 3 | 523 / 1022 / 91000 | pacs-adapter-service, experience-bff, one-ui-shell viewer, Flyway `V005` | **superseded** | Feature paths (`ImagingAnnotationEntity/Repository`, `ImagingAnnotationPanel`) present on canonical. |
| `intake/dicom-governed-upload-workflow` | 2026-06-18 04:27 · R. Gongora · `159f96627` · docs(imaging): defer governed upload | 90 / 1 | 537 / 1023 / 91782 | 1 absorption/deferral report | **superseded** | Deferral report present on canonical; explicitly deferred pending policy; ~90 behind. |
| `intake/registry-extended-demographics-persistence` | 2026-06-18 04:03 · R. Gongora · `b2ce60a42` · feat(registry): extended demographics | 93 / 3 | 538 / 1023 / 91888 | vito-service, experience-bff, one-ui-shell registration wizard | **superseded** | `VitoClientRegistrationWizard.test.tsx` present on canonical; extended-demographics work re-landed (see also active citizen/vito work). |
| `intake/ioptime-dicom-phase-a-dwv` | 2026-06-17 18:18 · R. Gongora · `3ef53d43f` · feat(imaging): native DWV viewer | 96 / 2 | 546 / 1024 / 92909 | one-ui-shell imaging viewer, DWV dep, package-lock | **superseded** | `DwvNativeViewer.tsx` + `resolveViewerEngine.ts` present on canonical. |
| `intake/ioptime-lift-adapt-verify` | 2026-06-17 16:39 · R. Gongora · `87bde97f4` · feat(home): modal work launcher | 97 / 1 | 555 / 1936 / 93575 | one-ui-shell home, `workSurfaceModules.ts` | **superseded** | `workSurfaceModules.ts` + `ExpandableWorkCategoryCard` present on canonical. |
| `staging` | 2026-06-23 11:20 · **tndangana** · `b83ccd6ab` · feat(mobile): SOS button + emergency | 349 / 2 | 96 / 15228 / 7789 | mobile citizen-app (FloatingSOSButton, Toast, Skeleton, **iOS native xcodeproj/Podfile**), design-token overhaul | **NEEDS-HUMAN-REVIEW** (teammate-owned; do not auto-integrate) | **Not a Claude intake branch** — owned by human teammate `tndangana`. Contains substantial unmerged native-iOS scaffolding + SOS/emergency UX **not on canonical**. 349 behind. Coordinate with owner before any integration; conflicts likely with active `citizen-zero-to-one` and `khuluma` mobile work. |
| `ioptime/dev` | 2026-06-18 14:22 · **fwdali1824** (external) · `deb43b746` · added iot based integrations | 350 / 6 | 1667 / 27158 / 1933 | experience-bff telemedicine RTC/WebSocket signaling, pacs imaging-edit, pct vitals/triage-imaging, **IoT integrations**, touches **removed `ui/experience/` fork** | **NEEDS-HUMAN-REVIEW** (external fork; do not auto-integrate) | External contributor fork. The intake/ioptime-*, pct, pacs branches were *lifted & adapted* from this and absorbed, but raw **"iot based integrations"** + some BFF telemedicine signaling may be **unabsorbed**. Edits the deleted `ui/experience/` tree (GAP-010 removed it) → cannot merge cleanly. Human triage required. |

### Out of scope (older long-lived branches — not part of this recent wave)
Listed for completeness; **excluded** from closeout per mandate (no evidence tying them to the recent wave):
`impilo-fundo-upgrade` (Jun 16, mbaradza), `fix-impilo-fundo` (Jun 9), `fix-migrations` (May 27),
`split/pr3-fundo-ui`, `split/pr4-stabilization` (May), `peter/vnext-2.0`, `peter/vnext-1.0`, `production` (Apr),
`local/one-ui-auth-context-activation`, `local/readme-runtime-bootstrap` (Apr),
and the April `claude/*` branches (`...-fr4iV`, `...-pD8bx`, `...-ugjBG`, `...-jb5O0`, `review-project-manifest-jb5O0`).

---

## ACTIVE-DO-NOT-TOUCH list (explicit)

The destructive integration gate must **not** merge, delete, rebase, or force-touch any of these until the owning session confirms completion.

**On origin, mid-flight (recent commits):**
1. `intake/citizen-zero-to-one` — commit **hours old** (Jun 26 05:23); carries a critical in-flight security fix (TPL-1).
2. `intake/oros-diagnostics-journey` — the **current dirty checkout**; OROS↔MADI O1–O19 wave active.
3. `intake/khuluma-comms-hub` — Comms/RTC orchestration, new service + OPA policy still landing.
4. `intake/wave-b-tshepo-gdhcn-trust-primitives` — CDS interpretation engine + trust primitives active.

**Named as active but NOT on origin (local-only in other sessions — do NOT recreate or push over):**
5. `intake/czo-ws-opa` — absent on origin.
6. `intake/czo-ws-deleg-be` — absent on origin.
7. `intake/czo-ws-deleg-ui` — absent on origin.
8. `intake/provider-clinical-place-design` — absent on origin.
9. `intake/fundo-lms` — absent on origin.

**Owner-gated (not Claude-owned; coordinate with the human owner before any action):**
10. `staging` — teammate `tndangana`, unmerged native-iOS + SOS work.
11. `ioptime/dev` — external contributor `fwdali1824`, raw IoT/telemedicine fork on the removed `ui/experience/` tree.

> ⚠️ Cross-branch coupling: the four active intake branches **share a large common substrate**
> (security batch F, `libs/tshepo-trust-crypto`, CDS Phase 0–1, OROS Wave 2, the
> `g046-remove-oauth-offswitch` sweep across ~23 services, runtime-proof scripts). When they
> eventually integrate, expect heavy overlap — integrate the shared substrate **once**, then the
> branch-specific deltas, rather than merging all four blindly.

---

## Recommended integration ORDER (for the later destructive gate ONLY — not executed here)

Run only after **all** ACTIVE branches quiesce. Re-verify each branch at that time (this inventory is a point-in-time snapshot). Suggested layering:

1. **Product-Truth / guard / docs first** — reconcile the scanner + gap register. Resolve
   `intake/product-truth-scanner-honesty` (verify no gap re-smoothing) **before** anything ratchets the baseline, so honest debt is preserved.
2. **Backend services** — `intake/b3-dags-permit-key` (clean FF; the only genuinely-unmerged code in the closed group). Then the active branches' backend layers in dependency order: shared `libs/tshepo-trust-crypto` + tshepo-authz/security substrate → vito/registry → clinical-knowledge-platform/zibo (CDS) → oros/madi/channels → khuluma-service → mvumo consent.
3. **BFF** — experience-bff endpoints behind each backend (interpretation proxy, comms controllers, OROS observation/specimen, consent/legal-agreement, triage/imaging proxies).
4. **Web UI** — one-ui-shell routes (comms, CDS interpreted flags, OROS worklists/admin catalogue, registry demographics).
5. **Mobile** — citizen-app + provider-app parity (Comms Hub, trust banner, diagnostics, clinical records). **Then** reconcile teammate `staging` (SOS/native-iOS) with owner sign-off.
6. **Tests / regeneration last** — run the full suite, regenerate product-truth + full-boot preview, re-assert guards, confirm honest gap count did not regress.

**Do not auto-integrate:** `staging` and `ioptime/dev` require human owners and (for `ioptime/dev`) conflict structurally with the removed `ui/experience/` tree.

---

## What this run did and did NOT do

**Did (safe, non-destructive):**
- `git fetch --all --prune`; verified (dirty) tree state without modifying it.
- Created **and pushed** safety branch `safety/product-truth-before-recent-branch-closeout-20260625-1734` @ `6d522d291`.
- Discovered + classified every recent-wave candidate branch with full ahead/behind, unique-commit, changed-file, and tip-delta evidence.
- Probed canonical for feature-file presence to distinguish absorbed vs unmerged.
- Wrote + pushed this inventory on `intake/branch-closeout-inventory` (via an isolated worktree).

**Did NOT (per hard limits):** no `git merge`, no `git cherry-pick`, no branch deletes, no force-push, no history rewrite, no stash/discard of the active dirty working tree. All integration items above are **recommendations only**.
