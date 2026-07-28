# RMNP W10 — report to the coordinator

`docs/registry/**` is coordinator-only, so this **reports** drift rather than editing the leases.
Four items need a coordinator decision or a lease amendment; two are not RMNP's.

---

## 1. Migration numbers RMNP consumed this wave

| Service | Number | What |
|---|---|---|
| `pct-service` | **V437** | Confidentiality stamp reconciliation + capacity determinations |
| `clinical-knowledge-platform-service` | **V041** | `national_policy_parameters` |

Both verified free against **both** committed history and the working tree immediately before cutting,
per the standing rule. Next free: pct **V438**, CKP **V042**.

## 2. RMNP's `tshepo-authz` lease is consumed — amendment needed

`iatg-rmnp-leases.md:38` reserves **V048–V052** for "SRH sensitivity assignment + adolescent consent
policy seeds". **That entire block is gone.** On disk: V048 is the confidential-lane policy migration,
V049 purposes, V050 khuluma, V051 ward manager, V052 regulator roles. Head is **V055**.

RMNP needed no authz migration this wave, so nothing is blocked. But the lease row is now false, and a
future lane reading it would cut a colliding number. **Next free is V056**, which is also where flip
step 5 (`UPDATE policy_rule SET active = true` for the V048 rows) would land.

## 3. `tshepo-authz` V053–V055 were cut against no lease band — not RMNP's

`V053__rom_shadow_rules_use_implemented_primitives.sql`, `V054__work_mode_catalog_and_role_template_modes.sql`
and `V055__work_mode_boundary_policy_rules.sql` belong to the WorkMode lane and sit in no registered
band — they were cut by measuring head rather than reading claims. That is the exact failure mode the
paediatrics lease post-mortems in its §2b. Reporting for lease hygiene, not as a complaint: nothing
collided.

## 4. The confidentiality stack landed without a lease row — worth recording

The whole seam (PDP Step 4.7, `ResourceSensitivityClassifier`, the `VisibilityProfile` category
obligation, V048's rules, zibo V008, the rego mirror) landed **2026-07-26** under a lane that
registered no lease row. Three lease files still describe `SPECIALLY_PROTECTED` as "decorative" and
two record work as *blocked* on it:

- `iatg-adult-medicine-leases.md:343-351` — "Confidentiality blocks W3… the paediatric pack's Wave 5
  is blocked on the same seam"
- `iatg-surgery-procedures-leases.md:253-256` — "P5 blocked; either it is fixed first or P5 ships an
  explicit PARTIAL"

**Both are now obsolete**, and both lanes may be holding work they no longer need to hold. Worth
telling them directly rather than waiting for them to re-read a lease.

This also produced a reusable lesson: on an estate this busy, **a "blocked by another lane" note can
go stale without anyone telling you**. RMNP carried "confidentiality is a separate lane, blocked" for
two days after it had in fact landed, and only found out by re-verifying rather than trusting the note.

## 5. Uncommitted foreign work in the most contended service

`services/experience-bff/.../worklist/ClinicalWorklistComposer.java` and `WorklistRanking.java` have
been sitting **untracked** in `experience-bff` — a service leased per-controller by at least five
lanes. Not mine, not touched. Flagged because untracked files in a shared checkout are invisible to
`git log` and to most guards, and because that is precisely how a migration-number collision or a lost
edit happens.

## 6. Two guards added, both wired into `run-change-safety-gates.sh`

- **`check-confidential-lane-routing.sh`** — fails the build if a controller exposes a
  confidentiality-stamped record on a path lacking a `/confidential/` lane marker. Estate-relevant
  beyond RMNP: any service stamping records for the tshepo-authz seam has the same trap.
- **`check-top-no-record-level-emit.sh`** — fixed (see below).

**A defect worth propagating: `git ls-files` blinds a pre-commit guard.** It lists only *tracked*
files, so a guard cannot see the newly-added file it exists to catch. The routing guard **passed**
against a deliberately-planted offending controller until this was fixed, and the TOP guard had the
same blindness. Both now use `--cached --others --exclude-standard`. **Any guard in the estate that
scans `git ls-files` for new offending files has this bug.** Worth a sweep.

## 7. Working arrangement

RMNP W10 is being built in an isolated worktree — `/opt/impilo/repos/impilo-rmnp-w10`, branch
`claude/rmnp-w10-completion` — merging into the integration branch **per wave** rather than at the
end, so migration numbers are claimed immediately. Reason: this checkout had `ui/one-ui-shell` vanish
three times in 30 hours, and `check-dangerous-deletions.sh` diffs `BASE...HEAD` commit-to-commit, so
it printed PASS while 2,859 files showed deleted in the working tree. The guard suite is structurally
blind to that class of failure.
