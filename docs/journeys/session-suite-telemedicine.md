# Session Suite — TELEMEDICINE (Runbook)

Governed clinical consult over the shared LiveKit substrate. PCT owns the
teleconsult business object; rtc-gateway owns the media session, waiting-room
transport truth, tokens, and recording. Proven end-to-end on the live preview
estate (2026-07-05/06).

## What the mode is (doctrine)

Template: [`contracts/schemas/session-templates/telemedicine.json`](../../contracts/schemas/session-templates/telemedicine.json)
(`sessionMode: TELEMEDICINE`, `owningService: PCT`, live mode `CLINICAL_SESSION`).

- **Layout** `consult`, join flow `LOBBY` with a `WAITING_ROOM` lobby — the
  patient never holds a media token until a provider admits.
- **Roles → grants**: PROVIDER (publish + roomAdmin), PATIENT / CAREGIVER /
  SUPERVISOR (publish), INTERPRETER (publish, `audioOnlyDefault`), OBSERVER
  (subscribe-only, hidden). rtc-gateway refuses tokens for any role not in the
  template.
- **Recording**: off by default, PROVIDER-only start, `consentRequired: true`,
  sensitivity `CLINICAL`, artifact owner PCT.
- Token TTL 3600s, max 8 participants, room prefix `impilo-telemedicine`,
  audit depth `CLINICAL`, post-session `BILLING_ENRICH / SUMMARY / FOLLOW_UP`.
- Notification keys: `rtc.telemedicine.{patient-waiting,session-ready,appointment-reminder}`
  (registered by `scripts/operator/register-session-notification-templates.sh`).

## The journey

**Actors**: provider (`dr.mapfumo`), accepting specialist (`nurse.chienda`),
patient (`citizen.moyo` or a registered VITO patient).

**Surfaces**: provider session workspace (waiting-room admit control +
front-and-centre video stage); patient `/my/telehealth/{id}` (device check →
ask-to-join → waiting state → auto-transition into the consult); mobile
waiting-room + call screens on both apps.

**Services**: experience-bff → PCT (VITO-guarded teleconsult intake, specialty
pool routing, consent, submit/accept) → live/rtc chain is direct here:
rtc-gateway provisions the session, mints template-role tokens, and runs the
lobby; khuluma provides waiting-room orchestration/booking/on-call surfaces
(W2); document-service adopts recording artifacts; LiveKit + Egress + MinIO
underneath.

**Events**: LiveKit webhooks → `rtc.session_events`
(`participant_joined/left`, room finished) and the egress webhook completes
`rtc.recordings`; the rtc outbox bridges `impilo.rtc.*` truth onto Kafka.

## Proving it on the preview estate

```bash
# Two-party browser media + patient waiting-room journey (from the preview VM)
(cd ui/one-ui-shell && PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1 \
  PREVIEW_SANDBOX_E2E=1 npx playwright test e2e/session-media-core.spec.ts e2e/telehealth-patient-flow.spec.ts)

# Recording/egress pipeline (policy gate → egress → MinIO → docstore → signed URL)
bash scripts/e2e/session-recording-proof.sh
```

- `session-media-core.spec.ts` — two REAL browser contexts join one governed
  teleconsult room; each must subscribe to the other's video track and sustain.
- `telehealth-patient-flow.spec.ts` — patient waits with **no token**, provider
  admits, patient auto-transitions into the consult, both parties subscribe.
- `session-recording-proof.sh` — PATIENT recording start refused (4xx), PROVIDER
  start → egress ACTIVE → stop → `rtc.recordings` COMPLETE with a storage
  location → document-service `register-external` → signed-URL fetch returns
  real mp4 bytes.

Preconditions are the scenario-A ones (seeded personas, reconciled Keycloak,
full preview estate) — see
[`docs/journeys/scenario-a-clinical-journey.md`](scenario-a-clinical-journey.md).

## Key contracts

- Waiting-room admit/deny is decided against rtc-gateway lobby state first;
  the BFF serves the frozen-contract admit/deny paths.
- Patient tokens are governance-compared in the correct identifier space
  (`5f68ca1e` fixed a CPID-vs-anchor mismatch that denied legitimate patients).
- Recording start is template-gated: `whoCanStart: [PROVIDER]` +
  `consentRequired` — the proof asserts the PATIENT refusal explicitly.

## Failure modes seen and their fixes

| Failure | Fix |
|---|---|
| Every room churned 15s NegotiationError/resume loops (LiveKit server v1.8.4 never acked protocol-17 client offer IDs) | LiveKit server → v1.13.3, egress → v1.13.0 (`eebc7272b`) |
| Client published before `RoomEvent.Connected`, dropping first tracks | publish only after Connected (`fbebafbf0`) |
| Patient token governance compared CPID against person anchor → false denies | compare in the right identifier space (`5f68ca1ee`) |
| LiveKit v1.13 fires `room_started` inside the provisioning tx window → webhook missed the session | bounded retry in webhook session resolution (`19ef4e510`) |
| Concurrent first tokens raced on the session PK | idempotent provisioning (`cb7b7ce70`) |

## Known limits

- **TLS for external browsers**: `getUserMedia` requires a secure context —
  media from arbitrary external browsers needs DNS + certificates on the
  preview ingress (infra item; the proofs run against the VM-local origin).
- Hairpin NAT on the public IP: VM-local runs may need
  `LIVEKIT_MEDIA_HOST_REWRITE` (see `session-media-helpers.ts`).
- SIP/PSTN dial-in fallback is unbuilt (`fallbackRules.sipFallback` is a
  reserved template flag only — no SIP code exists in rtc-gateway).
