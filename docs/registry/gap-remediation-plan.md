# Gap Remediation Plan

## Priority 0 - Production Blockers

| Gap | Impact | Action | Owner |
|---|---|---|---|
| Stub providers active by default (`notification-service`) | silent message delivery failure | switch defaults to explicit real providers; fail closed when provider unset | integration platform |
| Sandbox adapter defaults in `mushex-service` | payment integrity risk | require production profile hard-disable for sandbox adapter and add startup guard | enterprise finance |
| Placeholder BFF responses in production controllers | false-success behavior | replace placeholders with real integrations or typed error responses + audit events | experience platform |
| Mvumo consent evaluate path (previously stubbed) | trust/compliance gap | **completed in Trust audit pass:** delegated `/internal/v1/mvumo/evaluate` to live `tshepo-consent-service` decision endpoint; retain regression tests | trust plane |
| MVUMO remote-session/template trust workflows | trust orchestration false-positive/incomplete behavior | **completed in this pass:** verify/grant/refuse/withdraw + template create/update implemented with persistence, transitions, and audit events | trust plane |
| MVUMO + TSHEPO runtime full-stack cutover evidence | residual integration risk | **partially completed in cutover pass:** runtime harness reliability hardened (preflight daemon/compose checks, deterministic project naming, health-gated startup, retries/timeouts, failure diagnostics artifact capture, clean teardown) and CI job hardened to fail explicitly; blocker remains open until first green `trust-fullstack-runtime` CI execution and subsequent audit-ledger depth expansion | trust plane |
| Legacy TSHEPO retirement execution gates | compatibility drift risk | **advanced in cutover pass:** added machine-readable checklist (`docs/architecture/tshepo-legacy-retirement-checklist.md`), compatibility deprecation metadata in OpenAPI, consumer default-URL guard tests, and removed `TSHEPO_POLICY_BASE_URL` fallback from active runtime policy consumers; complete zero-usage window and compatibility proxy decommission gate before route removal | trust plane |

## Priority 1 - Architecture and Ownership

| Gap | Impact | Action |
|---|---|---|
| Potential SoR overlap (`ndr-service` vs `national-data-repository-service`) | duplicate ownership and drift | run service merge ADR and pick one SoR owner |
| `mushe-wallet-service` ownership/build drift | enterprise capability fragmentation | align reactor/build/deploy ownership with `mushex-service` strategy |
| Parallel experience shells drift | route mismatch and duplicated behavior | consolidate canonical route ownership to `one-ui-shell` and compatibility policy for `ui/experience` |
| TSHEPO decomposition overlap (`tshepo-service` vs decomposed TSHEPO sub-services) | duplicate trust ownership and policy drift | ADR published; enforce migration wave plan and consumer cutover gating to canonical TSHEPO services |

## Priority 2 - Backend/Frontend Wiring

| Gap | Action |
|---|---|
| Citizen conditions and provider discovery TODO paths | wire to live APIs via BFF and remove empty local state placeholders |
| SOAP save local-only behavior | add persisted backend endpoint + audit |
| Public-health mixed fixture/live rendering | move fixtures to test/demo-only sources and enforce production API data only |
| Sparse wiring for workflow/dispatch routes | add explicit UI orchestration surfaces or register as non-user-facing APIs |

## Priority 3 - Contracts, Tests, and Operations

| Gap | Action |
|---|---|
| Partial API contract readiness on multiple services | enforce OpenAPI parity with implementation and add contract tests |
| Partial authz/audit and observability status | add mandatory route-level checks and dashboards/alerts runbook references |
| Incomplete readiness signal taxonomy | tighten per-service readiness statuses from inferred to evidenced values |

## Exit Criteria

- No known production-path mock/stub remains unclassified.
- Every service has one primary plane, one domain, explicit SoR, and explicit forbidden responsibilities.
- Critical user-facing capabilities have UI -> BFF -> backend wiring evidence with authz/audit controls.
- Production readiness register transitions from baseline-assessed to evidence-backed status.
