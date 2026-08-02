# Checkpoint 8 — Sensitive cohorts and bypass retirement

Status as at 2026-08-02. Branch `claude/tshepo-trust-completion-Yypyl`.

---

## The finding that reframes this checkpoint

The brief describes CP8 as retiring 96 bypasses in waves. Measuring the estate first changed what
that means.

`scripts/security/audit-enforcement-posture.py` classifies all 105 services by what their source
**actually does**, not by whether they carry the flag:

| Posture | Count | Meaning |
|---|---:|---|
| `FLAG_IS_SEAM` | 70 | flipping the flag genuinely enforces |
| `ALREADY_ENFORCING` | 18 | `authenticated()` with no bypass — nothing to retire |
| `UNCONDITIONAL_OPEN` | 10 | `permitAll` regardless of the flag |
| `NO_SECURITY_CONFIG` | 6 | no `securityFilterChain` at all |
| `UNKNOWN` | 1 | `experience-bff` — reported, never assumed benign |

**Sixteen services are open and declare no bypass flag at all.** They are invisible to bypass
retirement. Every one of the 95 declared bypasses could be retired and the report would be
*literally true* while these stayed wide open:

```
abis-service            audit-ledger-service       matcher-engine
indawo-service          analytics-pipeline-service connector-fhir-adapter
iot-ingestion-service   nhume-service              ndila-service
jobs-service            offline-edge-service       offline-sync-service
pharmacy-elmis-adapter  support-service            wellness-service
shared-core
```

Biometric identity, the audit ledger, and identity matching are on that list. These need a real
`securityFilterChain` — **no flag flip can close them**, which is precisely why a
retirement-counting approach would never have surfaced them.

This is the same defect class as CP6's finding that fourteen BFF governance checks had always
denied: *a control that reads as present and does nothing.* Counting bypasses retired would have
been a metric that improves while the estate does not.

`scripts/guard/check-enforcement-posture.sh` freezes the set in **both** directions — a new open
service fails, and so does a baseline still naming one since fixed. Proven RED both ways.

---

## Cohort selection, from evidence

Intersecting posture with the source-derived caller graph (116 services, 363 edges):

- **70** `FLAG_IS_SEAM`
- **30** of those have `experience-bff` as their only source-derived caller — and the BFF already
  attaches its own `client_credentials` token when no user token is present
  (`ServiceClientConfig` L524), so they are the lowest-risk cohort
- **38** have multiple or non-BFF callers and need per-caller workload identity first
- **2** have no source-derived caller at all — **not** a safe cohort. Absence of an edge is not
  absence of a caller; Kafka topics, runtime-built URLs and gateway-mediated calls are invisible
  to the graph, and its own caveat says so

Proposed **cohort 2** (bounded registries and orchestration, per the brief's wave order):
`rules-service`, `guidance-service`, with `forms-service` and `reporting-service` to follow.

Pre-flip control captured live: unauthenticated requests to business paths on these services
return **404, not 401** — the request reaches the application, so they are genuinely open. That is
the discriminator the flip must invert. `/actuator/health` is deliberately *not* used: it is
`permitAll` under both configurations and would have proven nothing.

---

## Blocked

**Cohort 2 was not enforced.** `kubectl set env deploy/<svc>
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=false` was refused by the environment's permission
classifier. Nothing partially applied — both services verified still `bypass=true`, `ready=1`.

This is an environment permission boundary, not a technical or evidential gap. Every gate the
brief requires before enforcing this cohort is satisfied and recorded above: posture measured,
callers enumerated, caller authentication verified, pre-flip control captured, rollback known
(`…=true`, one command per service, no image change).

---

## Honest status

**CHECKPOINT 8 PARTIAL.**

Delivered: the evidence base the brief demanded before any cohort could be enforced, a finding
that materially changes what "retire the bypasses" means, and a two-directional guard that stops
the open set growing.

Not delivered: any second enforced cohort. CP7's single cohort
(`workforce-governance-service`) remains the only enforced service.

Unchanged blockers, restated with current numbers:

- **95 live workloads still run with the bypass on** (measured live; CP7 retired exactly one).
- **16 services need a security chain written**, not a flag retired.
- ~90 services still run stale images, so source-level fixes do not become runtime fixes without
  a rebuild and deploy each.
- **Strict mTLS has no substrate in the estate**, so that clause of CP8 cannot begin.
- The FHIR consent PEP bypass, PCT's `ClinicalAccessGuard` break-glass waiver, and work-context
  `SHADOW`→`ENFORCE` all remain open inside this checkpoint.

At planning time I predicted CP8 would land `PARTIAL`. It has, and the reason is not the
permission block — that cost one cohort. The reason is that 95 of 96 bypasses remain, and sixteen
services were never covered by the bypass framing at all.
