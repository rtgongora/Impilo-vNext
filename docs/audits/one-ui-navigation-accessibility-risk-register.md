# One UI Shell — Navigation & Accessibility Risk Register

| ID | Risk | Affected users | Mitigation | Test evidence | Residual |
|----|------|----------------|------------|---------------|----------|
| R1 | Users cannot find modules without permanent sidebar | Nurses, clerks, low digital literacy | Start launcher + Command + **Navigation drawer** (full legacy map) + Home tiles | `ExperienceSidebar.test.tsx`, manual smoke | Medium until training materials ship |
| R2 | Icon-only taskbar on narrow screens | Mobile web clinicians | Tooltips + `aria-label` + visible Start | Component a11y pass | Low |
| R3 | Keyboard users lose muscle memory | Power users | `Ctrl+K` preserved; `Escape` closes overlays | ShellChrome keyboard test | Low |
| R4 | Context loss (facility/workspace) | Multi-facility staff | Breadcrumbs + Context button + drawer footer | `ModuleBreadcrumb` render | Medium — needs E2E |
| R5 | SOS misuse / alarm fatigue | All | SOS dialog confirms type; audit events | `ShellSosDialog` unit test (pending) | Medium — needs policy text |
| R6 | Duplicate discovery (drawer + Start) | — | Documented: drawer = browse, Start = launch | Doctrine doc | Low |
| R7 | Unauthorised command results | Mixed roles | Role gates on `SHELL_APPS` / `SHELL_COMMANDS`; BFF enforces data | `app-registry` + API tests | Low (server truth) |
| R8 | Fake dashboard metrics erode trust | Clinical leads | Doctrine + incremental removal of demo numbers | Home page audit backlog | High until data wired |

## Review cadence

- Update this register **each release** that touches `ShellTaskbar`, `ShellSearchPalette`, or `ExperienceSidebar`.
