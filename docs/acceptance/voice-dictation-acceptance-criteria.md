# Voice dictation — acceptance criteria (platform)

## Must (M)

- [ ] **M1**: For at least one **clinical** narrative field in the canonical shell, user can dictate, see interim/final text in the control, **edit**, and **save** without auto-submit.
- [ ] **M2**: Dictation **never** submits the parent form without a separate explicit save.
- [ ] **M3**: When Web Speech is unsupported, user sees a **clear** message and can still type (no dead-end).
- [ ] **M4**: Doctrine doc + audit + matrix exist and are **linked** from architecture docs.

## Should (S)

- [ ] **S1**: `shared-ui` dictation types consumed by **one-ui-shell** dictation components (no duplicate type definitions).
- [ ] **S2**: Optional cloud STT path **disabled by default** in national deployments until Mvumo + security review complete.
- [ ] **S3**: Audit metadata event on dictation toggle (no transcript in payload).

## Could (C)

- [ ] **C1**: Mobile native dictation parity.
- [ ] **C2**: Per-field feature flags for high-risk disable list.

## Test evidence

- Unit: dictation hook / provider edge cases (permission denied, abort).
- E2E: one happy path dictation → edit → save on a pilot route.
