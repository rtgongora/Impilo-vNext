# Voice dictation — security controls

## Threat model (summary)

| Threat | Mitigation |
|--------|------------|
| Covert microphone use | User gesture + visible state; no listen without explicit start. |
| Transcript injection | Treat dictation output like **user typing** — same XSS/output encoding and validation as typed text. |
| Audio exfiltration | Cloud STT only behind **authenticated**, **purpose-bound** BFF routes; **no** third-party keys in browser for national deployments. |
| Impersonation | All STT API calls carry **v1.1 trust headers** (`X-Tenant-ID`, `X-Actor-ID`, `X-Purpose-of-Use`, …). |

## Hardening checklist

- [ ] Remove or gate **`isSupported = true` fallback** that implies capability when browser STT is unavailable.
- [ ] Rate-limit `/api/v1/speech/transcribe` (or successor) at edge.
- [ ] Log **metadata-only** audit events (tenant, actor, field id, duration), not raw audio by default.

See also: `docs/privacy/voice-dictation-privacy.md`.
