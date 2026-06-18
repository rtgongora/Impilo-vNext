# IOPTIME-Derived Stream Final Report

**Prepared:** 2026-06-18  
**Product Truth branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Starting HEAD:** `0b34fa3b` — docs(absorption): plan remaining ioptime intakes  
**Final HEAD at report time:** `1a9b17b7` — docs(absorption): close ui experience archaeology  
**Historical source (read-only):** `origin/ioptime/dev` (closed as active absorption branch)

---

## 1. Final Product Truth state

| Item | Value |
|------|-------|
| **Branch** | `claude/staging-ux-orchestration-remediation-Yypyl` |
| **HEAD** | `1a9b17b7` |
| **Pushed** | Yes |
| **Working tree** | Clean (expected after this commit) |
| **Deploy** | Not performed (per stream authorization) |

---

## 2. Completed and landed

| Workstream | Status | Product Truth commits | Notes |
| ---------- | ------ | --------------------- | ----- |
| Home modal work launcher | LANDED (prior) | `7918a6b2` | Frontend-only work surface modal |
| DICOM Phase A DWV viewer | LANDED (prior) | `1afa2c35`, `291bb03c` | Additive `DWV_NATIVE`; view-only |
| Registry extended demographics | LANDED | `a8a4e27c`, `9380545d`, `4d60ba3a` | Vito SoR + BFF + wizard; passport GET read-back |
| DICOM governed upload | DEFERRED | `30b5d883` (ADR only) | `DEFERRED_PENDING_UPLOAD_POLICY` |
| PACS imaging annotation persistence | LANDED | `0e7121f8`, `9f90ce9d`, `c1166cd9` | PACS V005 + BFF audited proxy + viewer panel |
| PCT triage-imaging links | LANDED | `af6fe6d7`, `c65ac6c9`, `d2187f44` | PCT V014 + BFF proxy + encounter panel |
| Telemedicine RTC strategy | CLOSED (ADR) | `3e3e2fc6` | LiveKit remains canonical |
| ui/experience archaeology | CLOSED (ADR) | `1a9b17b7` | Files permanently rejected |

Intake branches pushed for traceability:

- `intake/registry-extended-demographics-persistence`
- `intake/dicom-governed-upload-workflow`
- `intake/pacs-imaging-annotation-persistence`
- `intake/pct-triage-imaging-links`
- `intake/telemedicine-rtc-strategy-gate`
- `intake/ui-experience-archaeology-closure`

---

## 3. Deferred or rejected

| Workstream | Decision | Reason | Future condition to reopen |
| ---------- | -------- | ------ | -------------------------- |
| DICOM governed upload | `DEFERRED_PENDING_UPLOAD_POLICY` | Ungoverned Orthanc proxy exists; no patient/facility binding policy for binary ingest; wrong-patient clinical safety risk | Product Owner approves upload policy; pacs-adapter ingest + BFF audited proxy implemented first |
| Custom WebRTC from ioptime/dev | REJECTED | LiveKit is canonical RTC | Explicit Product Owner strategy change + security review |
| ui/experience/** files | REJECTED | Stale duplicate tree; GAP-010 convergence | Never restore files; concept extraction via named intakes only |
| origin/ioptime/dev wholesale merge | REJECTED | Closed as active absorption branch | Never merge/cherry-pick/restore |

---

## 4. Remaining future backlog

| Item | Recommended intake branch |
| ---- | ------------------------- |
| Governed DICOM upload workflow | `intake/dicom-governed-upload-workflow` (implementation, after policy) |
| LiveKit call experience upgrades (invite/presence/overlay) | `intake/telemedicine-livekit-call-experience-upgrade` |
| Additional registry demographics (if any deferred fields) | Named registry intake from Product Truth |

---

## 5. Guardrails preserved

- No wholesale `origin/ioptime/dev` merge, cherry-pick, or file restore
- No `ui/experience/**` restoration (guard-enforced)
- No vendor/node-cache changes
- No SecurityConfig narrowing
- No package removals (LiveKit, maplibre, guard scripts preserved)
- No ungoverned clinical flows (upload UI not added without backend)
- No custom RTC implementation
- Migrations forward-only, non-colliding: **PACS V005**, **PCT V014** (did not reuse ioptime migration numbers)

---

## 6. Preview QA checklist (post-deploy, when authorized)

| Route / flow | Expected check |
|--------------|----------------|
| `/home` | Work surface modal launcher |
| `/learning`, `/learning/catalog`, `/learning/studio/courses` | Learning surfaces load |
| `/queue/walk-in` | Registry wizard; extended demographics POST + read-back |
| Patient GET/read-back | `passportReference` from profile identifiers |
| `/ehr/{cpid}/imaging` | Governed studies list |
| `/ehr/{cpid}/imaging/viewer` | DICOMWEB_STACK / OHIF / DWV_NATIVE; annotation panel save/read |
| `/ehr/{cpid}/encounter/{id}` | Linked imaging studies panel (PCT links) |
| Telemedicine `/telemedicine/session/*` | LiveKit session (requires rtc-gateway config) |
| Upload route | **Not implemented** — deferral stands |

---

## 7. Stream verdict

**STREAM_COMPLETE_WITH_DEFERRED_ITEMS**

All authorized implementation workstreams landed to Product Truth. DICOM governed upload deferred with ADR. RTC and ui/experience closed with ADRs. No deploy performed in this stream.
