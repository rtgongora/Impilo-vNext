# P1 authorization shadow — reading the evidence

**Status 2026-08-07: observer BUILT and DEPLOYED to `impilo-full-preview`; persona evidence NOT YET
COLLECTED.** No enforcement anywhere. Nothing has gone to production.

---

## What is being measured, and why it cannot be skipped

Measured 2026-08-07: the browser holds an opaque BFF session cookie and sends no `Authorization`
header, so ext_authz validates no session and resolves no roles — while **489 of 525 active ALLOW
rules (93%) require one**. The RBAC layer is inert on the primary human path.

Turning it on blind is not available to us. Rules that have never fired once would begin firing,
DENY rules included, against traffic that works today. This measurement is how the blast radius is
learned before anything changes.

---

## The shape of it

```
Browser ──▶ Envoy ──ext_authz──▶ tshepo-authz  (the REAL decision; already final)
              │
              ▼
        experience-bff
              │  ShadowObservationFilter captures an IMMUTABLE envelope on the request thread
              ▼
        tshepo-authz  POST /v1/authorize/shadow   (a SEPARATE, LATER request)
              │
              ▼
        tshepo_authz.shadow_decision_log
```

**Isolation is physical, not conditional.** The verdict was returned by Envoy before the BFF ran any
code. No ordering exists in which the shadow answer could replace it. The endpoint returns **no
verdict** so nothing can come to depend on it.

**The BFF does not put the bearer on the live path.** It calls domain services by direct cluster
DNS, not through Envoy — commit `78d217bb9` reverted a change that would have sent user bearers to
~100 domain services unstripped. **It must stay reverted.**

---

## Configuration

Both services read `impilo.trust.shadow-bearer.*`.

| Variable | Service | Meaning |
|---|---|---|
| `IMPILO_TRUST_SHADOW_BEARER_ENABLED` | both | master gate; false ⇒ PDP returns **404 before any token processing** and the BFF captures nothing |
| `IMPILO_TRUST_SHADOW_PROBE_KEY` | both | shared secret, from the `impilo-shadow-probe` Kubernetes Secret. **Never committed.** Blank fails closed on both sides |
| `IMPILO_TRUST_SHADOW_ASYNC_SAMPLE_RATE` | BFF | census rate; `1.0` in preview |
| `IMPILO_TRUST_SHADOW_CANARY_SAMPLE_RATE` | BFF | inline-latency canary rate; `0.02` |
| `IMPILO_TRUST_SHADOW_MAX_CONCURRENT` | PDP | shadow evaluations at once per instance; shadow always loses contention to production |

The secret is created out-of-band and referenced by `secretKeyRef` — it is not in the chart, not in
values, and not in git:

```bash
kubectl -n impilo-full-preview create secret generic impilo-shadow-probe \
  --from-literal=probe-key="$(openssl rand -base64 48 | tr -d '\n')"
```

`X-Impilo-Shadow-Probe-Key` answers *may this workload call the temporary measurement API*. It says
nothing about any human. The bearer inside the request body answers *which human is hypothetically
evaluated*. **Using either as the other collapses the trust boundary.**

---

## Proving the pipeline before trusting a dataset

Run all three. A dataset from an unproven pipeline is worse than no dataset.

```bash
# negative control — no key, and a wrong key. Both must be 403.
kubectl -n impilo-full-preview exec deploy/oros-service -- \
  curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://tshepo-authz-service:8081/v1/authorize/shadow \
  -H 'Content-Type: application/json' -d '{"method":"GET","path":"/x","bearer":"x"}'
```

```bash
# positive control — the real key with a deliberately invalid bearer. Must be 202, and must
# write a row with probe_outcome = BEARER_UNRESOLVED. A pipeline that drops a bad bearer
# silently would also drop the requests hardest to evaluate.
KEY=$(kubectl -n impilo-full-preview get secret impilo-shadow-probe -o jsonpath='{.data.probe-key}' | base64 -d)
```

```bash
# the observer's own health, once a minute, from the BFF
kubectl -n impilo-full-preview logs deploy/experience-bff --since=5m | grep shadow_observer | tail -1
```

Read the **negative** counters first. `dropped_queue_full` and `transport_errors` are requests the
dataset does not contain, and they are not randomly distributed — they cluster exactly when the
estate is busiest, which is when the interesting denials happen.

---

## What this observer structurally cannot see

**Only ALLOWED traffic reaches the BFF.** A DENY is returned by Envoy and never arrives. Therefore:

- `PERMIT_TO_DENY` — **observable**, and it is the finding that would break live traffic.
- `ACTOR_TYPE_CHANGE` — observable.
- `DENY_TO_PERMIT` — **structurally invisible from this vantage point.**

The dataset answers *what would break*. It cannot answer *what would newly be permitted*. That is a
property of where the observer sits, not an omission, and any report built from this table must say
so.

Three route families bypass ext_authz in Envoy and are deliberately not observed: `/actuator`,
`/internal/v1/auth/`, `/internal/v1/public/`. If that list in
`deploy/helm/impilo-vnext/templates/envoy.yaml` changes, `ShadowObservationFilter` must change with
it.

---

## Reading the evidence

```sql
-- 1. Coverage. Anything other than OK is a request the dataset does not describe.
SELECT probe_outcome, count(*)
FROM tshepo_authz.shadow_decision_log GROUP BY 1 ORDER BY 2 DESC;

-- 2. The headline: what would break.
SELECT delta_kind, count(*)
FROM tshepo_authz.shadow_decision_log WHERE probe_outcome='OK' GROUP BY 1 ORDER BY 2 DESC;

-- 3. Per persona — the answer the principal model is here to justify.
SELECT active_persona, legacy_actor_type, delta_kind, count(*)
FROM tshepo_authz.shadow_decision_log WHERE probe_outcome='OK'
GROUP BY 1,2,3 ORDER BY 1,4 DESC;

-- 4. Which surfaces would break, most-affected first.
SELECT route_class, count(*) FILTER (WHERE delta_kind='PERMIT_TO_DENY') AS would_break,
       count(*) AS observed
FROM tshepo_authz.shadow_decision_log WHERE probe_outcome='OK'
GROUP BY 1 ORDER BY 2 DESC;

-- 5. The exact routes to fix, with the reason the shadow evaluation gave.
SELECT method, path, resource_type, action, prod_actor_type, legacy_actor_type,
       shadow_deny_reason, shadow_roles, count(*)
FROM tshepo_authz.shadow_decision_log
WHERE probe_outcome='OK' AND delta_kind='PERMIT_TO_DENY'
GROUP BY 1,2,3,4,5,6,7,8 ORDER BY 9 DESC LIMIT 100;

-- 6. Latency. pdp_millis is PDP-side evaluation; the round trip is BFF-side, in the
--    shadow_observer log line, because a call cannot report its own duration in its own body.
--    total_added_millis is QUEUE-TO-DISPATCH delay, not the round trip. Do not confuse them.
SELECT probe_mode, count(*),
       percentile_disc(0.5)  WITHIN GROUP (ORDER BY pdp_millis) AS p50,
       percentile_disc(0.95) WITHIN GROUP (ORDER BY pdp_millis) AS p95,
       percentile_disc(0.99) WITHIN GROUP (ORDER BY pdp_millis) AS p99
FROM tshepo_authz.shadow_decision_log WHERE probe_outcome='OK' GROUP BY 1;
```

---

## Personas to drive

Sign in, exercise the landing surface and one representative action, then read sections 3–5 above
scoped to that actor.

| # | Persona | Preview principal | Realm role |
|---|---|---|---|
| 1 | Citizen in My Life | `citizen.moyo` | `CITIZEN` |
| 2 | Clinician in Work | `nurse.chienda` (PROV-ZW-00007) | `NURSE` |
| 3 | Provider in My Professional | `dr.gwena` (PROV-ZW-00009) | `CLINICIAN` |
| 4 | Facility administrator | `admin.harare` | `FACILITY_ADMIN` |
| 5 | Provider-registry manager | `hpa.registrar` | `HPA_REGISTRAR` |
| 6 | National administrator | `national.admin.one` | `NATIONAL_ADMINISTRATOR` |
| 7 | Regulator / council | `regulator.hpcz` | `HIE_ADMIN` |
| — | **Caregiver / proxy** | **none exists** | **NOT COVERED** |

**Caregiver/proxy is reported as not covered, not simulated.** Delegation machinery exists in Mvumo,
but no end-to-end proxy persona exists in this estate. `PrincipalProjection.PERSONA_PROXY` is
therefore unreachable today, and manufacturing a delegation to fill the row would put a fabricated
persona in an evidence table.

### A caveat that must appear in the report

`active_persona` is derived **only from an introspected duty token**. `x-provider-id` and any
client-supplied work-mode header are refused as persona inputs, because PolicyEngine echoes the
inbound `x-provider-id` back and the server-side resolver that would populate it is citizen-only —
letting it select `MY_PROFESSIONAL` would reproduce, with a header, the exact defect the projection
refuses with a role.

Consequence: **a persona that never mints a duty token projects as `MY_LIFE`/`CITIZEN` regardless of
its Keycloak roles.** That is correct behaviour (possession of a role never selects a persona), but
it means personas 2–7 only produce work-persona rows if the session actually enters a work context.
A run where every row says `MY_LIFE` has measured the absence of duty tokens, not the absence of
deltas — say which.
