# IOPTIME Remaining Intakes — Product Truth Implementation Programme Plan

**Prepared:** 2026-06-17  
**Product Truth branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Product Truth HEAD at plan time:** `291bb03c` — feat(imaging): add native DWV viewer mode  
**Historical source reference (read-only):** `origin/ioptime/dev` (closed as active absorption branch)

---

## 1. Programme decision

`origin/ioptime/dev` is **closed as an active absorption branch**. It may only be consulted as historical source memory — for product archaeology, field-name hints, and workflow ideas — not as a merge, cherry-pick, or wholesale lift target.

Future work must **not** continue as direct absorption from `ioptime/dev`. Any useful remaining idea must become a **named intake branch** from current Product Truth, following the repo-native absorption pipeline:

1. Readiness / design review (scope, system-of-record, migration numbers, stop conditions)
2. Implementation on intake branch only
3. Verification (unit, integration, parity, gates)
4. Browser QA (contract-level minimum; live stack where applicable)
5. Product Owner authorization
6. Controlled landing (cherry-pick or equivalent atomic commits onto Product Truth)

No wholesale source branch merge. No cherry-picking `ioptime/dev` commits. No restoration of `ui/experience/**`.

---

## 2. Current Product Truth baseline

| Item | Value |
|------|-------|
| **Branch** | `claude/staging-ux-orchestration-remediation-Yypyl` |
| **HEAD** | `291bb03c` — feat(imaging): add native DWV viewer mode |
| **Canonical experience layer** | `ui/one-ui-shell` (GAP-010 convergence; no parallel `ui/experience` tree) |
| **Telemedicine RTC** | LiveKit remains canonical |
| **Imaging viewers** | DICOMWEB_STACK default · OHIF canonical for deep review · DWV_NATIVE additive (Phase A landed) |

### Already harvested slices (landed to Product Truth)

| Slice | Landed commit(s) | Scope |
|-------|------------------|-------|
| Home modal work surface launcher | `7918a6b2` | Frontend-only; modal launcher for work surface modules |
| DICOM Phase A — DWV native viewer mode | `1afa2c35`, `291bb03c` | Additive `viewerType=DWV_NATIVE`; governed WADO via existing BFF DICOMweb proxy; no upload/edit/backend |

---

## 3. Remaining workstream overview

| Workstream | Decision | Product value | Main risk | Implementation stance |
| ---------- | -------- | ------------- | --------- | --------------------- |
| **REGISTRY_EXTENDED_DEMOGRAPHICS_PERSISTENCE** | TAKE_NOW_AS_NEXT_INTAKE | Richer client registration/profile data | Fields were previously UI-only; BFF/Vito persistence/read-back unproven | Backend/BFF persistence first, UI second |
| **DICOM_GOVERNED_UPLOAD_WORKFLOW** | FUTURE_INTAKE | Attach/upload DICOM studies from clinical workflow | Upload without role, audit, patient/facility context, PACS ownership | Backend/BFF/PACS governed upload first, UI second |
| **PACS_IMAGING_ANNOTATION_PERSISTENCE** | FUTURE_INTAKE | Preserve clinical annotations/measurements/review notes | Schema/API/audit complexity | PACS adapter owns persistence; BFF audited proxy; UI save only after API |
| **PCT_TRIAGE_IMAGING_LINKS** | FUTURE_INTAKE | Connect triage/encounter workflow to imaging studies | Clinical workflow ownership and migration design | PCT owns relationship; BFF proxy; UI after API |
| **TELEMEDICINE_RTC_STRATEGY_GATE** | STRATEGY_ONLY_NOT_CODE | Review whether custom RTC ideas add anything beyond LiveKit | Replacing canonical LiveKit architecture prematurely | ADR only; LiveKit remains canonical |
| **UI_EXPERIENCE_ARCHAEOLOGY** | REJECT_AS_FILES_ARCHAEOLOGY_ONLY | Possible old product memory | Stale duplicate tree contaminating One UI | No file restoration; only concept extraction through Product Truth design |

---

## 4. Sequencing

### Authorized sequence

1. **Registry extended demographics persistence**
2. **DICOM governed upload workflow**
3. **PACS imaging annotation/edit persistence**
4. **PCT triage-imaging links**
5. **Telemedicine RTC strategy ADR**
6. **ui/experience archaeology closure**

### Rationale

| Order | Why |
|-------|-----|
| **1 — Registry** | Smallest scope; value is already visible in UI concepts but blocked only by persistence/read-back. Proves the intake→land pattern on a low-risk vertical before imaging governance work. |
| **2 — DICOM upload** | Upload must be **governed** (policy, audit, role, patient/facility context) before any drop-zone UI appears. Backend/PACS ownership precedes frontend. |
| **3 — PACS annotation persistence** | Edit/annotation save depends on stable imaging study ownership and upload architecture; PACS adapter is system of record, not browser state. |
| **4 — PCT triage-imaging links** | Clinical workflow links require clear imaging identifiers, governed study IDs, and ownership boundaries established by prior imaging intakes. |
| **5 — Telemedicine RTC ADR** | Strategy-only; can run **in parallel** with code intakes but must not produce RTC code until Product Owner explicitly changes strategy. LiveKit stays canonical. |
| **6 — ui/experience closure** | Files permanently rejected; docs-only archaeology closure records the decision and prevents re-litigation. |

---

## 5. Workstream plans

### A. REGISTRY_EXTENDED_DEMOGRAPHICS_PERSISTENCE

**Objective:**  
Land optional client demographic fields **only if** they persist and read back through Product Truth contracts.

**Candidate fields:**

- `middleName`
- `passportReference`
- `email`
- `addressLine`
- `city`
- `preferredLanguage`
- `maritalStatus`
- `emergencyContactName`
- `emergencyContactPhone`

**Required decisions before code:**

- Which fields are official registry fields?
- Which are Vito-owned versus BFF overlay-owned?
- Are any fields sensitive or role-gated?
- Should emergency contact be structured as one object or flat fields?
- Should passport reference be optional alternative identifier?

**Implementation stance:**

1. Inspect current Vito registration and patient profile schemas.
2. Add persistence in the owning service, preferably Vito if these are registry demographics.
3. Add read-back contract.
4. Add BFF pass-through/overlay only if needed.
5. Add tests proving create + retrieve.
6. Only then re-add UI fields to `VitoClientRegistrationWizard`.

**Stop conditions:**

- No clear system of record
- No read-back path
- Backend rejects fields
- Fields only exist in POST payload but disappear afterwards

**Recommended intake branch:**  
`intake/registry-extended-demographics-persistence`

**Recommended commits:**

1. `feat(vito): persist extended client demographics`
2. `feat(bff): expose extended client demographics`
3. `feat(registry): collect extended client demographics in wizard`

---

### B. DICOM_GOVERNED_UPLOAD_WORKFLOW

**Objective:**  
Allow governed DICOM upload only through approved clinical context and audited PACS ownership.

**Required decisions before code:**

- Who may upload?
- Which workspace/facility context is required?
- Is patient context mandatory?
- Does upload create a study or attach to an existing study?
- Does PACS adapter own STOW/upload?
- Which audit event names are required?
- Maximum file size and accepted MIME/extensions?
- Virus/malware scan requirement?
- Consent/privacy requirements?

**Implementation stance:**

1. Define upload policy and audit model.
2. Add PACS adapter upload endpoint or confirm existing STOW path.
3. Add BFF audited proxy.
4. Add UI drop zone only after backend route exists.
5. Browser QA with governed route and sample DICOM.

**Stop conditions:**

- Direct browser-to-PACS upload
- No audit
- No patient/facility context
- No role/policy gate
- Local file UI without backend route

**Recommended intake branch:**  
`intake/dicom-governed-upload-workflow`

**Recommended commits:**

1. `docs(imaging): define governed upload policy`
2. `feat(pacs): add governed DICOM upload endpoint`
3. `feat(bff): add audited imaging upload proxy`
4. `feat(imaging): add governed DICOM upload drop zone`

---

### C. PACS_IMAGING_ANNOTATION_PERSISTENCE

**Objective:**  
Persist imaging annotations, measurements, and review edits safely.

**Required decisions before code:**

- What counts as an annotation versus review note versus measurement?
- Are edits per instance, series, study, or `governedStudyId`?
- Should annotations be versioned?
- Are annotations clinical record content?
- Who can create/edit/delete?
- Required audit events?
- Should annotations write to SHR later?

**Implementation stance:**

1. PACS adapter owns persistence.
2. Use forward-only PACS migration after current Product Truth, **not** ioptime V004.
3. Suggested migration name: `V005__imaging_annotation_persistence.sql` (or next available if V005 is already used).
4. Add PACS API and service tests.
5. Add BFF audited proxy.
6. Add UI save/edit only after API exists.

**Stop conditions:**

- Migration collision
- BFF acting as system of record
- No audit
- No role gates
- Annotations stored only in browser/local state

**Recommended intake branch:**  
`intake/pacs-imaging-annotation-persistence`

**Recommended commits:**

1. `feat(pacs): add imaging annotation persistence`
2. `feat(bff): expose audited imaging annotation proxy`
3. `feat(imaging): save DWV/OHIF annotations through PACS`

---

### D. PCT_TRIAGE_IMAGING_LINKS

**Objective:**  
Link triage/encounter workflow records to imaging studies in a governed clinical workflow.

**Required decisions before code:**

- Does the link belong to triage, encounter, order, or care episode?
- Can one triage link to many studies?
- Can one study link to many triage records?
- Is OROS/orders involved?
- Who can create/remove links?
- What audit event is required?
- How should the link surface in patient timeline?

**Implementation stance:**

1. PCT owns the clinical workflow relationship.
2. Use forward-only PCT migration after current Product Truth, **not** ioptime V007.
3. Suggested migration: `V014__triage_imaging_links.sql` (or next available if V014 is already used).
4. Add PCT API and tests.
5. Add BFF audited proxy.
6. Add minimal UI link only after API exists.

**Stop conditions:**

- Migration collision
- Link stored in PACS only
- No clinical owner
- No audit
- No encounter/triage identity clarity

**Recommended intake branch:**  
`intake/pct-triage-imaging-links`

**Recommended commits:**

1. `feat(pct): add triage imaging link model`
2. `feat(bff): expose triage imaging link proxy`
3. `feat(triage): surface linked imaging studies`

---

### E. TELEMEDICINE_RTC_STRATEGY_GATE

**Objective:**  
Decide whether any custom RTC ideas from `ioptime/dev` should influence the canonical LiveKit telemedicine architecture.

**Current decision:**  
Do **not** implement custom RTC now. **LiveKit remains canonical.**

**Required strategy questions:**

- What LiveKit gap is custom RTC solving?
- Are call invite/presence ideas reusable on top of LiveKit?
- Does custom WebRTC increase security, TURN/STUN, recording, moderation, audit, and support burden?
- What is the provider/citizen telemedicine doctrine?
- Does Live Events share the same stack?

**Implementation stance:**

1. Produce ADR only.
2. Compare LiveKit canonical model versus custom WebRTC.
3. Salvage UI ideas only if they can sit on top of LiveKit.
4. No RTC code until Product Owner explicitly changes strategy.

**Stop conditions:**

- Replacing LiveKit without ADR
- Package removals
- Backend signalling stack without policy/security review
- Telemedicine drift from Impilo Live/Fundo/Nompilo integration doctrine

**Recommended intake branch:**  
`intake/telemedicine-rtc-strategy-gate`

**Recommended commit:**  
`docs(telemedicine): record RTC strategy decision`

---

### F. UI_EXPERIENCE_ARCHAEOLOGY

**Objective:**  
Close `ui/experience` as a file source while preserving any useful product memory through Product Truth design.

**Decision:**  
Reject `ui/experience/**` as files **permanently**.

**Allowed:**

- Read old files for product archaeology.
- Extract concepts into new Product Truth-shaped designs.
- Create named intakes if a concept has value.

**Not allowed:**

- Restore `ui/experience/**`
- Duplicate One UI shell
- Copy stale pages wholesale
- Use `ui/experience` as canonical app tree

**Recommended commit:**  
`docs(absorption): close ui experience archaeology`

---

## 6. Cross-cutting rules

All remaining intakes must obey:

- **Start from Product Truth** — branch from current HEAD; never from `ioptime/dev`.
- **No wholesale source branch merge** — selective lift-adapt only.
- **No cherry-picking ioptime commits** — re-implement against Product Truth contracts.
- **No stale migration numbers** — always inspect Flyway state on Product Truth before naming migrations.
- **No package removals** — especially LiveKit, maplibre, guard scripts.
- **No `ui/experience` restoration** — archaeology only.
- **No BFF as system-of-record** unless explicitly designed and documented.
- **No UI-only field collection** without persistence and read-back proof.
- **No clinical action without audit** — create/update/delete must emit auditable events.
- **No imaging upload/edit/link** without patient, facility, role, and audit context.

---

## 7. Master backlog table

| Priority | Intake | Branch | Type | Starts now? |
| -------- | ------ | ------ | ---- | ----------- |
| 1 | Registry extended demographics persistence | `intake/registry-extended-demographics-persistence` | Backend + BFF + UI | **Yes** |
| 2 | DICOM governed upload workflow | `intake/dicom-governed-upload-workflow` | Backend + BFF + UI | After registry |
| 3 | PACS imaging annotation persistence | `intake/pacs-imaging-annotation-persistence` | Backend + BFF + UI | After upload architecture |
| 4 | PCT triage-imaging links | `intake/pct-triage-imaging-links` | Backend + BFF + UI | After PACS/link model clarity |
| 5 | Telemedicine RTC strategy ADR | `intake/telemedicine-rtc-strategy-gate` | Docs / ADR only | Can run in parallel |
| 6 | ui/experience archaeology closure | (docs on Product Truth) | Docs only | Can run in parallel |

---

## 8. Recommended immediate next action

**Immediate next action is `REGISTRY_EXTENDED_DEMOGRAPHICS_PERSISTENCE`.**

Do **not** start DICOM upload, PACS annotation persistence, or PCT triage-imaging link code until the registry intake is closed — or until the Product Owner explicitly reprioritizes.

**Suggested first steps for registry intake:**

1. Open readiness review: map candidate fields to Vito schema and `docs/registry/system-of-record-map.md`.
2. Create branch `intake/registry-extended-demographics-persistence` from Product Truth `@291bb03c` (or later HEAD at intake start).
3. Implement Vito persistence + read-back + tests before any wizard UI changes.
4. Run local quality gates and browser QA proving create → retrieve round-trip.
5. Product Owner authorization → controlled landing to Product Truth.

---

*This document is planning only. No application, backend, BFF, package, or migration changes are authorized by this plan alone.*
