# Voice dictation — privacy & data handling

## Principles

1. **Data minimisation**: Prefer **transcript text** in application state; avoid retaining **raw audio** unless a documented use case requires it.
2. **Purpose limitation**: Audio or transcript sent to processors must match **declared purpose of use** for the workflow.
3. **Third-party STT**: If vendor processes audio, DPIA / cross-border and **Mvumo** consent flows apply.
4. **Retention**: If audio is stored, define **TTL**, **encryption at rest**, and **access control** in the service owning the blob (e.g. document-service only if explicitly designed).

## User-facing disclosure

Copy should state clearly:

- Whether recognition runs **on-device** (browser) or **server/vendor**.
- That the user must **review** text before save.
- That **saving** the form persists according to the **same policy** as typed content.

## Relation to consent channel “VOICE”

`tshepo-consent-service` **VOICE** channel refers to **telephony / IVR verbal consent**, not browser dictation. Do not conflate in privacy notices.
