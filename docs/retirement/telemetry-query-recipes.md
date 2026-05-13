# Retirement telemetry query recipes (Phase 7B/7C support)

> Purpose: provide copy-ready query templates for the `SIDECAR_UI` and `LEGACY_WEB_SHELL` signals so operations can wire dashboards without rediscovering filters.

## 1. SIDECAR_UI (RR-01/02/03)

Use ingress/access logs and filter to browser-originated traffic only.

Template fields:
- `${HOST}`: deployed host
- `${PATH_PREFIX}`: `/mushex-finance-console`, `/mushex-ops-console`, `/mushex-payer-portal`
- `${PROBE_IP_REGEX}`: healthcheck/probe source CIDRs

Pseudo-query:

```text
count_over_time(
  http_requests{
    host="${HOST}",
    path=~"${PATH_PREFIX}(/.*)?",
    user_agent!~"(?i)(kube-probe|prometheus|uptime|healthcheck)",
    source_ip!~"${PROBE_IP_REGEX}"
  }[1d]
)
```

Dashboard rule: 30-day rolling daily series must remain `0`.

## 2. LEGACY_WEB_SHELL (RR-04/05)

Template fields:
- `${PATH_PREFIX}`: `/experience` or `/ehr` deploy path (environment-specific)

Pseudo-query:

```text
count_over_time(
  http_requests{
    path=~"${PATH_PREFIX}(/.*)?",
    user_agent!~"(?i)(kube-probe|prometheus|uptime|healthcheck)"
  }[1d]
)
```

Dashboard rule: 30-day rolling daily series must remain `0`.

## 3. Evidence capture checklist

- Save a screenshot/export for each dashboard panel showing the full rolling window.
- Link each screenshot/artifact from the corresponding RR row in `retirement-readiness-ledger.md`.
- Record panel query text used for the snapshot to keep evidence reproducible.
