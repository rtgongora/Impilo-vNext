# Telemedicine RTC Strategy Decision

**Prepared:** 2026-06-18  
**Product Truth branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Intake branch:** `intake/telemedicine-rtc-strategy-gate`  
**Status:** `STRATEGY_DECISION_RECORDED`

---

## 1. Decision

**LiveKit remains the canonical RTC stack for Impilo telemedicine.** No custom WebRTC signalling stack from `origin/ioptime/dev` is authorized in Product Truth at this time.

---

## 2. Current Product Truth architecture

| Layer | Canonical implementation |
|-------|-------------------------|
| **RTC gateway** | `services/rtc-gateway-service` — `LiveKitTokenService`, room creation, participant tokens |
| **Web UI** | `ui/one-ui-shell` — `LiveKitConsultRoom.tsx`, `/telemedicine/session/[sessionId]` |
| **Mobile** | `apps/mobile/provider-app` — `LiveKitMobileConsultRoom.tsx` via `@livekit/react-native` |
| **BFF** | `/internal/v1/teleconsult/sessions/**`, `/internal/v1/mobile/provider/telemedicine/**` |
| **Clinical workflow** | PCT telehealth sessions (`pct-service`), Impilo Live event linkage (V013) |

Packages: `@livekit/components-react`, `livekit-client` (web); LiveKit native SDK (mobile). **Not authorized for removal.**

---

## 3. Rejected approaches

| Approach | Reason rejected |
|----------|----------------|
| Replace LiveKit with custom WebRTC from `origin/ioptime/dev` | No architecture/security review; duplicates canonical stack |
| BFF WebSocket signalling stack without review | Bypasses governed RTC gateway; audit/token risk |
| Package removals (LiveKit) | Violates guardrails and programme plan |
| Cherry-pick/merge `origin/ioptime/dev` RTC commits | Source branch closed as active absorption branch |
| Custom RTC backend without Product Owner strategy change | Out of scope for absorption stream |

---

## 4. Salvageable UX ideas (on top of LiveKit)

These concepts from historical `ioptime/dev` archaeology may be revisited **only** as Product Truth-shaped intakes atop LiveKit:

- Call invite UX (incoming session notification)
- Presence indicators (provider availability)
- Incoming-call overlay in encounter/telemedicine shell
- Call state UI (connecting, reconnecting, ended, failed)
- Session handoff between web and mobile with same LiveKit room contract

Each requires a named intake branch, BFF/governed path, audit, and tests — not wholesale file restore.

---

## 5. Relationship to Impilo Live

Future **Impilo Live Events / Impilo Live** experiences should align with the LiveKit/media architecture unless separately decided by Product Owner. PCT telehealth sessions already carry `live_event_id` linkage (V013). Do not fork a parallel RTC stack for live events.

**Future intake (if authorized):** `intake/telemedicine-livekit-call-experience-upgrade`

---

## 6. Guardrails preserved

- No LiveKit removal
- No package removals
- No custom WebRTC implementation in this stream
- No merge/cherry-pick from `origin/ioptime/dev`
- No SecurityConfig narrowing

---

## 7. Recommendation

**Record and close.** Proceed with LiveKit-only RTC doctrine. Any UX upgrade work must use the future intake branch above with explicit Product Owner authorization.
