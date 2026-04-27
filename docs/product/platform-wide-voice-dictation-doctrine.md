# Platform-wide voice dictation — product doctrine

**Status**: Active doctrine  
**Applies to**: All Impilo surfaces (clinical, operational, support, citizen, mobile, enterprise) where **meaningful narrative text** is entered.

---

## 1. Positioning

1. **Voice dictation is a universal input aid**, not a replacement for structured data capture.
2. **If a user can type meaningful narrative text**, they should **usually** be able to **dictate** it, **review** it, **correct** it, and **then save** it — same bar as typing.
3. Dictation is **not** a notes-only novelty: it applies to **any appropriate narrative field** (clinical narrative, operational comments, support descriptions, citizen free text, finance narrative where allowed).

---

## 2. Safety and control

4. Dictation must **never auto-submit** the parent form or persist without an explicit user save action.
5. Dictation must **always** allow **review and correction** before final submission.
6. **No silent recording** — activation must be obvious (e.g. visible listening state, accessible label).
7. **No background listening** — capture stops when the user ends the session or navigates away (implementation must abort recognition/recording).
8. **No raw audio storage** unless **explicitly configured**, **consented** (Mvumo / national policy), and **documented** in privacy & security packs.
9. **Clinical users must verify** dictated clinical content before final submission (same standard as pasted or imported text).

---

## 3. Structured vs narrative

10. **Structured coded fields remain structured** (codes, quantities, dates, enums). Dictation does not replace pickers or coded entries.
11. Voice may assist **searching or suggesting** coded concepts only where **safe** and **regulation-aligned**; **user confirmation** is mandatory before a code is applied.
12. Dictation must support **low-bandwidth** and **unavailable-service** fallbacks (typing, offline queue, graceful “dictation unavailable” with reason).

---

## 4. Context and fairness

13. Dictation must be **role-aware**, **patient-aware** (when applicable), and **context-aware** (purpose of use, facility, programme).
14. **Mobile**: use **native/OS** speech capabilities where feasible, still subject to review-before-save and consent rules.
15. Dictation must be **accessible** to users with typing difficulties, disability, or low keyboard confidence — WCAG-compatible controls and clear errors.

---

## 5. Engineering alignment

16. Shared contracts live in **`ui/shared-ui/dictation/`** (types + `DictationProvider` abstraction); shells consume them to avoid drift between workspaces.
17. **Telemetry / audit** of dictation usage should be **minimal**, **purpose-bound**, and **governed** (see `docs/security/voice-dictation-security.md`).

---

## 6. Related documents

- Current state: `docs/audits/voice-dictation-current-state-audit.md`
- Field matrix: `docs/audits/voice-dictation-field-coverage-matrix.md`
- Architecture: `docs/architecture/voice-dictation-platform-integration.md`
- Security: `docs/security/voice-dictation-security.md`
- Privacy: `docs/privacy/voice-dictation-privacy.md`
- Clinical: `docs/clinical/voice-dictation-clinical-controls.md`
- Acceptance: `docs/acceptance/voice-dictation-acceptance-criteria.md`
