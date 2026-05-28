# Telemedicine RTC Media Failures Runbook

## Scope

Use this runbook when telemedicine sessions are created, but media (audio/video) fails to connect, reconnect, or sustain quality.

This runbook assumes the doctrine-aligned architecture:

- PCT is the clinical workflow owner.
- RTC Gateway is the media adapter.
- LiveKit is the provider infrastructure.

## Symptoms

- Provider/citizen can join a session but sees "media standby" or repeated reconnecting.
- Session token exists but media transport fails.
- PCT session transitions are normal (`SCHEDULED` -> `IN_PROGRESS`) but no active media tracks.
- Spike in `impilo_rtc_session_provision_failed_total`.

## Immediate Safety Actions

1. Confirm clinical continuity:
   - Keep consultation open using asynchronous notes/chat.
   - Avoid terminating the clinical workflow unless clinically required.
2. Ensure patient safety fallback:
   - Trigger no-show/delay/support signals from provider console.
   - Escalate urgent care to in-person/referral protocols when media is unavailable.
3. Preserve audit trail:
   - Do not bypass governance headers or consent checks.

## Triage Checklist

### 1) RTC Gateway health

- Check `/actuator/health` for `rtc-gateway-service`.
- Verify env values:
  - `RTC_FAIL_CLOSED=true`
  - `RTC_REQUIRE_CONSENT_REFERENCE_FOR_MEDIA=true`
  - `LIVEKIT_URL` (server-to-server)
  - `LIVEKIT_CLIENT_URL` (ws/wss client URL)

### 2) Consent and purpose-of-use

- Confirm request includes supported `purposeOfUse`.
- Confirm `consentReference` is present for VIDEO/AUDIO sessions unless emergency override applies.
- Validate no surge in `RTC_INVALID_REQUEST` responses.

### 3) LiveKit provider status

- Verify room create/delete Twirp endpoints are reachable from RTC Gateway.
- Validate API key/secret rotation status.
- Check provider-side room and participant limits.

### 4) PCT workflow integrity

- Ensure telehealth session outbox events are still emitted:
  - `telemedicine.session.room_provisioned`
  - `telemedicine.session.media_started`
  - `telemedicine.session.media_ended`
- Confirm no fallback to non-governed providers in production.

## Recovery Actions

- If `LIVEKIT_CLIENT_URL` is incorrect:
  - Update to valid `wss://` endpoint and redeploy `rtc-gateway`.
- If consent validation blocks sessions unexpectedly:
  - Confirm consent service flow; do **not** disable consent enforcement in production.
- If provider outage:
  - Keep fail-closed behavior and route care through non-video clinical fallback paths.

## Verification After Recovery

- Create a test telemedicine session with valid `purposeOfUse` and `consentReference`.
- Join from provider + citizen clients and confirm:
  - media connects;
  - reconnect behavior is stable;
  - session closure emits expected lifecycle events.
- Confirm no sustained increase in:
  - `impilo_rtc_session_provision_failed_total`
  - `impilo_rtc_token_issued_total` error-related deltas

## Escalation

- **Ring 0 incident**: platform-wide media outage or governance bypass risk.
- Notify:
  - Clinical operations lead
  - Trust/governance lead
  - Platform on-call (RTC Gateway + LiveKit)
