# ui/experience Archaeology Closure

**Prepared:** 2026-06-18  
**Product Truth branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Intake branch:** `intake/ui-experience-archaeology-closure`  
**Status:** `PERMANENTLY_CLOSED_AS_FILES`

---

## 1. Decision

**`ui/experience/**` is permanently rejected as files.** It must not be restored, merged, cherry-picked, or wholesale-lifted into Product Truth.

The directory **does not exist** in current Product Truth (removed via GAP-010 convergence, 2026-05-28). Guard scripts enforce this:

- `scripts/guard/check-doctrine-compliance.sh` — fails if `ui/experience/` reappears
- `scripts/guard/check-deprecated-surfaces.sh` — blocks new files under `ui/experience/`

---

## 2. Why it is closed

| Reason | Detail |
|--------|--------|
| **Stale duplicate tree** | Parallel fork of orchestration layer after One UI consolidation |
| **Canonical layer** | `ui/one-ui-shell` is the single experience orchestration workspace (GAP-010) |
| **Build wiring** | `ui/package.json` workspaces list `one-ui-shell` only — no `ui/experience` |
| **Doctrine** | One Health OS, one experience shell — no parallel default web entry |

---

## 3. Permitted use: archaeology only

`origin/ioptime/dev` and any archived copies of `ui/experience/**` may be consulted as **historical product memory** for:

- Field naming hints
- Workflow ideas
- UX patterns worth re-implementing

They must **not** be restored as files. Useful concepts become **new Product Truth-shaped designs** under `ui/one-ui-shell` via named intake branches.

---

## 4. Concepts already harvested to Product Truth

| Concept | Product Truth landing |
|---------|----------------------|
| Home modal work launcher | `7918a6b2` — work surface modal in `one-ui-shell` |
| DICOM Phase A DWV viewer | `1afa2c35`, `291bb03c` — additive `DWV_NATIVE` viewer mode |
| Registry extended demographics | `a8a4e27c`–`4d60ba3a` — Vito persistence + wizard |
| Imaging annotation persistence | `0e7121f8`–`c1166cd9` — PACS adapter SoR |
| PCT triage-imaging links | `af6fe6d7`–`d2187f44` — workflow link layer |

---

## 5. Do-not-restore list

The following must **never** be wholesale restored from `ui/experience/**` or `origin/ioptime/dev`:

- Home page wholesale
- `AppLayout` duplicate
- `ExperienceSidebar` duplicate
- Stale registry wizard (`VitoClientRegistrationWizard` ioptime fork)
- Pending-toast downgrades (fake success without persistence)
- Duplicated route tree parallel to `one-ui-shell`
- Custom WebRTC / telemedicine stack bypassing LiveKit
- Ungoverned DICOM upload drop zones

---

## 6. Future concept extraction process

1. Identify concept in archaeology (read-only)
2. Map to Product Truth architecture (SoR, BFF, audit, tests)
3. Create named intake branch from current Product Truth HEAD
4. Implement Product Truth-shaped design in `ui/one-ui-shell` + backend
5. Verify gates; land via cherry-pick; **never** restore `ui/experience/**` files

---

## 7. Guardrails preserved

- No `ui/experience/**` restoration
- No merge/cherry-pick from `origin/ioptime/dev`
- No parallel experience orchestration layer
- Guard scripts remain in place and must not be removed
