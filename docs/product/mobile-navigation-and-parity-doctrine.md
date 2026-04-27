# Mobile navigation and parity doctrine

**Status**: Active (2026-04-10)  
**Applies to**: `apps/mobile/citizen-app`, `apps/mobile/provider-app`, shared packages under `apps/mobile/packages/*`

## 1. Principles

1. **No desktop sidebar on phone.** Mobile shells use **Start / launcher**, **bottom tabs** (where appropriate), **horizontal pill or chip navigators** for dense module sets, **workflow steppers**, and **contextual screens**—not a persistent multi-level sidebar mirroring One UI Shell.
2. **Same platform truth as web.** Identity, Tshepo trust headers, facility/workspace/shift context, purpose of use, consent evaluation, and audit obligations must match the Experience BFF and Ring-0 services. Mobile does not invent parallel policy.
3. **Role-appropriate surfaces.** Provider mobile optimises **active work**, **patient safety**, **alerts**, **queue**, and **field capture**. Citizen mobile optimises **personal health**, **consent**, **appointments**, **telehealth**, **reminders**, **support**, and **payments** where product allows—not full EHR chrome.
4. **Critical safety is never buried.** Allergies, urgent flags, Mvumo-derived restrictions (when exposed by API), emergency contacts, and SOS paths must remain reachable within **two intentional gestures** from Start (launcher) in normal configurations.
5. **No production fiction.** Empty states, errors, and “not configured” are preferable to fabricated patients, consents, queues, bills, or clinical results. Development fixtures belong behind explicit environment flags or tests only.

## 2. Navigation model

| Pattern | Provider | Citizen |
|--------|----------|---------|
| Bottom tabs | Launch, Patients, Encounter (when active), Results, Queue, Messages, Tools, Profile | Home, Health (Personal), Feed, Services, Messages |
| Start / launcher | `ProviderDashboardScreen` — worklist, metrics, quick launch | `HomeScreen` — quick actions, upcoming slices |
| Search / command | Patient lookup tab; future unified search | Marketplace / provider discovery; future global search |
| Mode switch | `ModeRouter` + `ModeSwitcher` (Provider / Outreach / Supervisor / Offline) | N/A (single persona) |
| Context chips | Facility, workspace, active encounter on dashboard hero | Health ID, facility picker where used |
| Deep workflows | Clinical tools hub with internal tabs; encounter flow | Personal hub with section pills |
| Help / support / SOS | Launch row + Supervisor escalations; offline break-glass where applicable | Personal → Emergency; Support & Help tab; SOS API-backed |

## 3. Parity with One UI / Experience

- **Comms**: Provider `MessagingScreen`; Citizen messaging inbox. Both use `@impilo/mobile-messaging` and BFF-aligned routes where implemented.
- **Telemedicine**: Provider sessions screen is reachable from **Clinical Tools → Telehealth** (API: `/internal/v1/mobile/provider/telemedicine/*`). Citizen uses `TelehealthListScreen` (tab routing).
- **Mvumo / consent**: Citizen **Consent** screen (granular preferences) plus settings-driven consents; full Mvumo remote session parity depends on `mvumo-service` + BFF routes (see audit).
- **Voice narrative**: Use **device-native** speech where available; design-system **`DictationAssistButton`** guides users to keyboard/OS dictation without silent recording or auto-submit (see security doc).

## 4. Offline and low bandwidth

- Provider **Offline** mode and `mobile-offline` sync engine are authoritative for queued operations.
- Citizen app configures durable SQLite where available; UI must show **offline / sync pending / failed** honestly (`NetworkStatusBar` pattern).
- Do not mark data as synced until the queue reports completion.

## 5. Change control

Any new web-only capability in `ui/one-ui-shell` or `ui/experience` that touches **clinical**, **consent**, **telehealth**, **billing**, or **support** must update:

- `docs/audits/mobile-parity-traceability-matrix.md`
- This doctrine if navigation or safety rules change
