# Runbook — Session Suite Notification Templates (national pod)

The comms hub renders strictly from **registered** notification templates, and
template governance is a national-pod authority. Session-suite services emit
against the template keys declared in
`contracts/schemas/session-templates/*.json` (`khulumaNotificationKeys`); a
key that is not registered on the pod renders nothing.

## How to register

```bash
FULL_BOOT_NAMESPACE=impilo-full-preview \
  bash scripts/operator/register-session-notification-templates.sh
```

The script is idempotent (GET `/internal/v1/templates/{key}` → 404 → POST),
mirrors the fundo-learner-journey step-0 pattern, and execs curl from the
`experience-bff` pod against `http://notification-service:8200` with
national-pod headers (`X-Pod-ID: national`). Env overrides: `TENANT_ID`,
`FULL_BOOT_NAMESPACE`, `NOTIFY_BASE_URL`, `EXEC_DEPLOY`.

## Keys it registers

All are `IN_APP` channel with subject `{{title}}` / body `{{message}}`:

| Key | Mode |
|---|---|
| `rtc.telemedicine.patient-waiting` | TELEMEDICINE |
| `rtc.telemedicine.session-ready` | TELEMEDICINE |
| `rtc.telemedicine.appointment-reminder` | TELEMEDICINE |
| `khuluma.meeting.invite` | MEETING |
| `khuluma.meeting.admission-requested` | MEETING |
| `learning.session.reminder` | LEARNING_LIVE |
| `live.event.starting-soon` | LIVE_EVENT |

## Seams / honest notes

- **The templates declare more keys than this batch registers.** The batch
  covers the keys with dispatch paths built in W2–W6. Remaining declared keys
  (e.g. `khuluma.meeting.{starting-soon,action-item-assigned,recording-ready}`,
  `learning.{session.attendance-recorded,completion.achieved,recording.published}`,
  `live.event.{registration-confirmed,replay-published,speaker-approved}`)
  should be added to `TEMPLATE_KEYS` as their dispatchers land.
- **`live.event.announcement` is declared but unwired**: live-service
  publishes `impilo.live.announcement.published.v1`, but notification-service
  has **no consumer** for that key — registering the template alone would not
  make announcements deliver. Wire the consumer first, then add the key.
- `learning.certificate.issued` is self-provisioned by the scenario-C proof
  script today; production template governance is a national-admin workflow
  (see `docs/product-truth/journey-closure-scenarios-a-d.md`).
