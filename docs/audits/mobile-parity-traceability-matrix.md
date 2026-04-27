# Mobile parity traceability matrix

**Date**: 2026-04-10  
**Legend — classification (Req column)**  
`P` = Required Provider mobile · `C` = Required Citizen · `B` = Both · `O` = Optional mobile · `W` = Web/admin only · `N` = Not applicable · `F` = Future phase

**Legend — status**  
`OK` implemented · `PART` partial / behind flag · `GAP` missing · `INT` integration debt (API/UI stub)

Columns per block: **Capability | Req | Web surface | Prov status | Cit status | Backend/API | Offline? | Push? | UX pattern | Security / consent | Audit | Tests | Gap / fix**

---

## A. Shell / navigation

| Capability | Req | Web | Prov | Cit | API | Off | Push | UX | Sec | Audit | Tests | Gap / fix |
|------------|-----|-----|------|-----|-----|-----|------|----|----|-------|-------|-----------|
| Start / launcher | B | One UI home | OK `ProviderDashboardScreen` | OK `HomeScreen` | BFF | No | No | Cards + CTA | Context headers | Access logs | Dashboard tests | Global search `F` |
| Bottom navigation | B | Zones | OK `ProviderTabs` | OK `CitizenTabs` | — | No | No | Bottom tabs | — | — | ModeRouter test | — |
| Facility / workspace context | P | Header chips | OK dashboard chips | N/A | Session/BFF | Cache | No | Chips | Trust headers | Yes | Manual | — |
| Comms hub | B | Messaging | OK tab | OK inbox | `mobile-messaging` | Queue | Yes generic | Inbox | Authz | Yes | Messaging tests | Deep link parity `F` |
| Help / Nompilo | B | Help routes | PART | PART | KB BFF `F` | Cached articles `F` | Generic | Sheet / webview | No PHI | Yes | — | Wire KB when API ready |
| System support / tickets | B | Support | OK Supervisor `EscalationsScreen` | OK `SupportScreen` in Personal | support APIs | Queue | Generic | List | Authz | Yes | Support test stub | Was missing citizen hub — **fixed** |
| SOS | B | SOS | PART (supervisor/offline) | OK `EmergencySOSSection` | `sosService` | Offline queue `F` | Generic | Prominent CTA | Safeguarding | Yes | — | Provider launch SOS row **added** |
| Notifications | B | Bell | Badge | Badge | BFF | No | **Privacy-safe** | Badge | `pushNotificationPrivacy` | Yes | — | Use trust helpers for local notifs |
| Profile / settings | B | Account | Professional tab | Profile/settings | Profile APIs | No | Generic | Forms | Consent | Yes | Personal flow tests | — |
| Telemedicine entry | B | Telehealth routes | **GAP→OK** Clinical Tools tab | Tab + home quick | `/telemedicine/*` | Pre-pack `F` | Generic | List + session | Consent | Yes | Telemedicine test | Was orphan — **fixed** |

---

## B. Clinical / EHR (provider-heavy)

| Capability | Req | Web | Prov | Cit | API | Off | Push | UX | Sec | Audit | Tests | Gap / fix |
|------------|-----|-----|------|-----|-----|-----|------|----|----|-------|-------|-----------|
| Patient lookup | P | EHR search | OK | N/A | BFF mobile provider | Cache `PART` | No | Search | Authz | Yes | — | — |
| Queue / my queue | P | PCT queues | OK | N/A | `queueService` | Yes `PART` | Abnormal alerts `F` | List | Authz | Yes | queueService test | — |
| Encounter / vitals | P | Encounter | OK | N/A | Encounter APIs | Vitals offline `PART` | No | Tabs | Consent | Yes | — | Persist SOAP `INT` |
| Orders / results | P | OROS | OK results | PART results | oros / BFF | View cache `F` | Generic | Cards | Masking | Yes | Vitest screens | PACS viewer `F` |
| Referrals | P | Referrals | OK screens | OK `ReferralsSection` | referral-service | No | Generic | List | Authz | Yes | — | — |
| Patient summary / flags | P | Patient header | PART | Own summary `PART` | butano/BFF | Pack `F` | No | Banner | Mvumo | Yes | — | **Contract** `PatientSummary` `F` |

---

## C. Mvumo consent (adaptive)

| Capability | Req | Web | Prov | Cit | API | Off | Push | UX | Sec | Audit | Tests | Gap / fix |
|------------|-----|-----|------|-----|-----|-----|------|----|----|-------|-------|-----------|
| View / toggle sharing prefs | C | Settings | PART | OK `ConsentScreen` | citizen profile `/consents` | No | Generic | Toggles | Tshepo | Yes | Consent tests | Mvumo proof `F` |
| Remote consent session | B | Mvumo UI | `F` | `F` | mvumo-service | Offline token `F` | “Action needed” | Wizard | Step-up | Yes | — | **Backend** |
| Provider check / capture | P | Mvumo | `F` | N/A | mvumo + tshepo-consent | Offline `F` | No | Banner | Witness / proxy `F` | Yes | — | **Backend** |

---

## D. Voice dictation

| Capability | Req | Web | Prov | Cit | API | Off | Push | UX | Sec | Audit | Tests | Gap / fix |
|------------|-----|-----|------|-----|-----|-----|------|----|----|-------|-------|-----------|
| Narrative assist | P | `DictationButton` | PART `DictationAssistButton` | PART same | Device STT | No | No | Mic hint | No silent capture | `F` | — | Optional `@react-native-voice/voice` `F` |

---

## E. Offline / sync

| Capability | Req | Web | Prov | Cit | API | Off | Push | UX | Sec | Audit | Tests | Gap / fix |
|------------|-----|-----|------|-----|-----|-----|------|----|----|-------|-------|-----------|
| Queue + conflict UI | P | Limited | OK packages | **PART→improved** | offline store | Yes | Failed generic | Banner | Encrypted `PART` | Yes | offline tests | Citizen `NetworkStatusBar` enhanced |

---

## F. Shared contracts (target)

| Contract | Web / shared | Mobile today | Action |
|----------|--------------|--------------|--------|
| `PatientSummary` / `CriticalFlags` | BFF DTOs | Partial types in `provider-app/src/types` | Align field names with BFF OpenAPI when published |
| `ConsentSummary` | tshepo + mvumo | Citizen `Consent` + profile | Add mvumo-specific DTO when service live |
| `TelemedicineSession` | Experience | `TelemedicineSession` type | **Keep** in sync with `/internal/v1/mobile/provider/telemedicine/sessions` |
| `Notification` | channels | Badges only | Apply `pushNotificationPrivacy` helpers |

---

## G. Classification index (quick lookup)

| ID | Capability | Req |
|----|--------------|-----|
| NAV-01 | Start / launcher | B |
| CLN-01 | Patient lookup | P |
| CLN-10 | Telemedicine session list/join | B |
| CON-01 | Preference consent | C |
| CON-10 | Mvumo remote adaptive | F |
| OFF-01 | Offline queue + banner | P (C partial) |
| SEC-01 | Privacy-safe push | B |

**Maintainers**: Update this file when adding a mobile screen that represents a new vNext capability.
