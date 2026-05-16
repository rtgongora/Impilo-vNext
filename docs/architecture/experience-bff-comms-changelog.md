# Experience BFF Comms Contract Changelog

## 2026-05-16

### Added
- `GET /internal/v1/comms/approval-queue` with SLA metadata (`age_hours`, `sla_breach`, `priority`) and source health.
- `GET /internal/v1/comms/approval-queue/history` for governance decision timeline.
- `GET /internal/v1/comms/approval-queue/health` for dependency status checks.
- `POST /internal/v1/comms/approval-queue/templates/{templateId}/approve`
- `POST /internal/v1/comms/approval-queue/templates/{templateId}/reject-with-reason`
- `POST /internal/v1/comms/approval-queue/templates/{templateId}/resubmit`
- `POST /internal/v1/comms/approval-queue/templates/{templateId}/reopen`
- `GET /internal/v1/comms/approval-queue/templates/{templateId}/dry-run`
- `POST /internal/v1/comms/approval-queue/campaigns/{campaignId}/approve`
- `POST /internal/v1/comms/approval-queue/campaigns/{campaignId}/reject-with-reason`
- `POST /internal/v1/comms/approval-queue/campaigns/{campaignId}/resubmit`
- `POST /internal/v1/comms/approval-queue/campaigns/{campaignId}/reopen`
- `GET /internal/v1/comms/approval-queue/campaigns/{campaignId}/dry-run`
- `POST /internal/v1/comms/approval-queue/campaigns/{campaignId}/cancel`
- `GET /internal/v1/communication/dashboard` now returns role-grouped sections (`executive`, `operations`, `clinical`, `communications`, `governance`).
- `GET /internal/v1/communication/health`.
- `GET /internal/v1/omnichannel/dashboard` now returns role-grouped sections and governance confidence metadata.
- `GET /internal/v1/omnichannel/health`.

### Contract Notes
- All new endpoints remain under `/internal/v1/*` to preserve version continuity.
- Existing consumers that expect flat dashboard fields should use compatibility fallbacks while migrating to role-grouped payloads.
- Reject paths now require a `decision_reason` field to enforce governance traceability.

### Consumer Impact
- Web `one-ui-shell` Omnichannel overview updated to read both new role-grouped and legacy flat dashboard metrics.
- Mobile comms dashboard client normalizes role-grouped payloads into existing KPI shape for backward compatibility.
