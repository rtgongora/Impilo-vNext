# Voice dictation — current state audit (Impilo vNext)

**Audit ID**: `voice-dictation-2026-04`  
**Repository**: Impilo vNext (branch per control tower)  
**Scope**: Platform-wide search for voice, speech, dictation, STT, microphone, transcription, and related UX.

---

## Executive summary

| Area | Finding |
|------|---------|
| **Canonical orchestration UI** | `ui/one-ui-shell` includes **Web Speech API–backed** `DictationButton`, `DictatableTextarea`, and `useSpeechToText`, plus route `/clinical/dictation`. |
| **Legacy / parallel workspace** | `ui/experience` **duplicates** the same three modules (copy drift risk). `experience` does **not** declare `shared-ui` as a dependency today. |
| **Shared component library** | `ui/shared-ui` now includes **`dictation/`** as the canonical **type + provider contract** (root-level module next to `components/`). |
| **Backend services (listed scope)** | **No** first-class “dictation service” or persistent audio store found. **VOICE** appears as a **consent channel** enum (telephony/IVR), not clinical STT. |
| **Mobile** | No dedicated dictation package surfaced in `apps/mobile` quick scan; native voice remains a **gap** for parity with web. |
| **Risks** | `useSpeechToText` reports `isSupported` as effectively always true while ElevenLabs path **buffers audio** and POSTs to `/api/v1/speech/transcribe` — **conflicts** with strict “no raw audio unless consented” until gated by config + Mvumo. |

---

## 1. Existing voice / dictation support

### 1.1 Web (one-ui-shell)

| Asset | Path | Behaviour |
|-------|------|-----------|
| `useSpeechToText` | `ui/one-ui-shell/src/hooks/useSpeechToText.ts` | Browser `SpeechRecognition` / `webkitSpeechRecognition`; optional **MediaRecorder** chunks + `fetch("/api/v1/speech/transcribe")` fallback. |
| `DictationButton` | `ui/one-ui-shell/src/components/ui/DictationButton.tsx` | Mic toggle; appends transcript to controlled `value` via `onValueChange`. |
| `DictatableTextarea` | `ui/one-ui-shell/src/components/ui/DictatableTextarea.tsx` | Textarea + `DictationButton`. |
| Route | `ui/one-ui-shell/src/lib/routes.ts` | `/clinical/dictation` — “Voice Dictation” in shell nav. |

### 1.2 Web (experience — legacy packaging)

Same filenames under `ui/experience/src/...` (parallel implementation).

### 1.3 Clinical dictation “studio” page

| Asset | Path | Notes |
|-------|------|-------|
| Dictation page | `ui/experience/src/app/clinical/dictation/page.tsx` | **Honest stub**: local recording UI; copy states **no connected speech-to-text** / no dictation store (Wave 1). **Does not** use `DictatableTextarea` for the main transcript in a unified way with shell. |

### 1.4 Backend (services in audit list)

| Service / area | Voice / dictation? | Notes |
|----------------|-------------------|-------|
| notification-service | No STT | Templates, SMS stubs, delivery — no narrative dictation API. |
| integration-hub | No STT | HTTP/Kafka connectors — no speech pipeline. |
| document-service | Not audited line-by-line | Expect **document bytes** storage; not voice-specific unless upload type added. |
| tshepo-audit-service | No STT | Audit ledger — could log **dictation usage events** in future. |
| rules-service | No STT | Policy rules text is configuration, not live dictation. |
| mvumo-service | Consent orchestration | **Mvumo** relevant for **consent** before optional cloud STT or audio retention. |
| tshepo-consent-service | Channel enum | **VOICE** = telephone/IVR verbal consent channel — **not** the same as browser dictation. |
| pct-service, oros-service, referral-service, pacs-adapter-service, costing-engine-service, mushex-service | No dedicated dictation | Narrative fields live in **UI + FHIR / domain DBs**, not speech engines. |

**Conclusion**: Dictation is **predominantly a client-side capability** today, with **no** platform-wide BFF contract for “dictation sessions” or transcript storage.

---

## 2. Microphone / audio components

| Component | Location | Purpose |
|-----------|----------|---------|
| `MediaRecorder` | `useSpeechToText` (ElevenLabs path) | Captures **raw audio** into blobs for chunked upload. |
| `getUserMedia({ audio: true })` | Same | Mic permission gate. |
| Telemedicine (external tree) | `impilo-structure` (if present in other repos) | **Out of Impilo-vNext** workspace scope for this audit file — do not treat as shipped in vNext unless merged. |

---

## 3. Narrative & notes-style fields (representative)

| Domain | Example surface | Dictation today |
|--------|-----------------|-----------------|
| Experience EHR vitals | `textarea` for observations, labour notes, etc. | **Plain `<textarea>`** — **no** `DictatableTextarea` wired in vitals wave slices. |
| EHR stub | `ui/ehr` `EncounterPanel` notes | **Plain `<textarea>`** — no dictation. |
| Partograph / CTG | Structured + narrative notes on Experience | Narrative fragments **typed**; **no** dictation affordance on those forms in current slices. |
| Shell clinical dictation | `/clinical/dictation` | Manual type/paste + recording placeholder. |

Full field inventory belongs in **`voice-dictation-field-coverage-matrix.md`** (living document).

---

## 4. Privacy / security controls (current)

| Control | Status |
|---------|--------|
| Explicit user action (mic click) | **Yes** for `DictationButton` — not silent. |
| No background listen | **Browser API** only active while recognition/recording session — **ElevenLabs interval path** is closer to “chunked capture”; must be **disclosed** and **consent-gated**. |
| Raw audio storage | **Not** in platform dictation table; **risk** if `/api/v1/speech/transcribe` persists server-side — **verify** BFF / edge implementation and **Mvumo** policy. |
| Clinical verification | **Process** only (user edits textarea); **not** enforced in code. |
| Audit events | **Not** standardized for “dictation_started / dictation_stopped / transcript_committed”. |

---

## 5. Gaps, mocks, and recommendations

### Gaps

1. ~~**No shared dictation contract** in `shared-ui`~~ **Addressed**: `ui/shared-ui/dictation/` (types + provider + noop factory).
2. **Duplicate** shell vs experience implementations — **converge** on `one-ui-shell` + `shared-ui` types; deprecate experience copy when routing fully migrates.
3. **Field coverage**: Most narrative `<textarea>` fields **lack** dictation affordance.
4. **Mobile**: No shared React hook; use **OS speech APIs** (iOS/Android) behind same **doctrine** (review before save).
5. **`isSupported` truthfulness**: `browserSupported \|\| true` is misleading — fix to align with doctrine + fallbacks.

### Mocks / stubs

- `/clinical/dictation` “Recent dictations” empty — **documented** stub until backend notes/dictation API exists.

### Recommendations (prioritised)

1. Adopt **`shared-ui` dictation types + `DictationProvider`** in `one-ui-shell` first; add **`shared-ui`** dependency to `experience` only if that workspace remains in use.
2. Roll **`DictatableTextarea`** onto **high-volume narrative fields** (clinical notes, referral narrative, support ticket body, free-text order instructions) without touching coded pickers.
3. Add **audit metadata** emission (client → BFF → `tshepo-audit-service` or experience audit topic) when dictation toggles and when user saves after dictation (configurable).
4. Gate **ElevenLabs / any cloud STT** behind **Mvumo + purpose-of-use** and **explicit** “send audio” consent copy.
5. Update **architecture / security / privacy / acceptance** docs (see companion files in this change).

---

## 6. References

- Doctrine: `docs/product/platform-wide-voice-dictation-doctrine.md`
- Coverage matrix: `docs/audits/voice-dictation-field-coverage-matrix.md`
- Architecture note: `docs/architecture/voice-dictation-platform-integration.md`
- Types & provider: `ui/shared-ui/dictation/`
