# DEPRECATED — ui/ehr

**Status**: DEPRECATED as of 2026-03-16 (Integration Closure Wave)

**Reason**: This EHR UI has been superseded by `ui/one-ui-shell`, the canonical Impilo web experience shell with full clinical coverage, React Query hooks, and real BFF integration.

**Replacement**: `ui/one-ui-shell` (port 3000)

**Action**: Do not develop new features in this directory. All clinical UI work should target `ui/one-ui-shell`.

Port 3002 (previously assigned to this app) is now reserved/unassigned.

Retirement of this folder is tracked as **RR-05** in [`docs/retirement/retirement-readiness-ledger.md`](../../docs/retirement/retirement-readiness-ledger.md); the telemetry signals required to satisfy the retirement criteria are defined in [`docs/retirement/telemetry-signals.md`](../../docs/retirement/telemetry-signals.md).
