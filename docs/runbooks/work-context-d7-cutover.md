# Work Context Resolution — D7 staged enforcement cutover

Status: **written, not fired.**
Branch instrument: `claude/workcontext-phase-d` (GatewayProbeController + V060).
Last measured: 2026-07-30 against live `impilo-full-preview`.

> Do not execute any flip in this document until the product owner types the
> authorisation phrase for that flip, **and** every dependency listed under
> [Hard dependencies](#hard-dependencies) is cleared. This runbook is the
> cutover plan, not the cutover.

## What D7 is

Four flips that move the Work Context Resolution programme from "authored and
shadowing" to "enforcing on the request path". Each flip has a soak window, an
observable pass criterion, and a one-command rollback that does not depend on
`helm upgrade`.

| Flip | What changes | Default today | After |
|---|---|---|---|
| **1** | Gateway `ext_authz` → PDP is on the request path | `envoy.extAuthz.enabled: false` | `true` |
| **2** | Duty-token binding | `TSHEPO_WORK_CONTEXT_MODE=SHADOW` | `ENFORCE` |
| **3** | WorkMode / clinical boundary rules | V055 + V058 seeded `active=false` | `active=true` |
| **4** | Signed decision envelope | mint off; verify `OFF` | mint on; verify `SHADOW` then `ENFORCE` |

Ordering is load-bearing:

1. Flip 1 before Flip 2 — enforcing duty-token mismatches at the PDP achieves
   nothing if the gateway is not consulting the PDP.
2. Flip 2 before Flip 3 — boundary rules keyed on `allowed_modes` only fire when
   a usable WORK_CONTEXT token is present; Flip 2 is what makes the token matter.
3. Flip 3 before Flip 4 — the envelope is a second perimeter; turn it on after
   the first perimeter is proven, not instead of it.
4. Inside Flip 4, mint before verify-SHADOW before verify-ENFORCE — verifying
   envelopes that are never minted denies every request.

## Hard dependencies

These block the cutover. They are not soft warnings.

### D-1. Helm repin hold (blocks Flip 1 via values; blocks any values-driven flip)

Release `impilo-full-preview` is at **revision 9** (2026-07-25). Measured on
2026-07-30: **15 deployments** have images that diverge from the release
manifest (including `experience-bff`, `one-ui-shell`, `pct-service`,
`vito-service`, `tshepo-authz-service`'s peers). A `helm upgrade` that only
flips `envoy.extAuthz.enabled` would still re-render every Deployment and
**revert those 15 images**, reporting success.

**Clear by:** regenerating the pin from the live estate and lifting the hold
(PO decision). Until then, Flips 2–4 that can be done with `kubectl set env`
are written that way; Flip 1 still needs either a lifted hold or an explicitly
authorised ConfigMap-only patch that does not run `helm upgrade`.

Verify drift before any values change:

```bash
helm get manifest impilo-full-preview -n impilo-full-preview > /tmp/helm-rel.yaml
# compare live Deployment images to the release; non-zero drift = do not upgrade
```

### D-2. `permitAll` / OAuth-disable interlock (caps Flip 4 evidence)

Measured 2026-07-30: **98 of 121** deployments carry
`IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true`. The refuse-to-start interlock
that rejects that flag outside tests exists in **3 of 96** services
(`ai-model-registry-service`, `llm-orchestration-service`,
`fhir-gateway-service`). Every other service collapses to `permitAll()`.

A decision envelope is a header a bypassing caller omits. Flip 4 on
`experience-bff` alone is real for that service; it is not estate-wide
enforcement while the interlock is unowned elsewhere. This runbook does **not**
adopt that work — it names it. Closing D-2 is a separate programme unless the
PO pulls it in.

### D-3. NetworkPolicy CNI (defence in depth, not a flip blocker)

Host package `ipset` is missing; kube-router cannot program NetworkPolicy.
See `docs/environment/NETWORK_POLICY_ENFORCEMENT_DEPENDENCY.md`. D2/D3 are a
perimeter, not pod isolation. Flip 4 still proceeds; do not claim pod-to-pod
isolation until `ipset` is installed and `scripts/guard/probe-network-policy-enforcement.sh`
exits 0.

### D-4. Images for this programme must be live

Flip 1's instrument (`GatewayProbeController` + V060) and Flip 4's mint/verify
code must be in the running pods. Under the repin hold that means
`kubectl set image` (or equivalent) of at least:

- `tshepo-authz-service` (V055–V060 migrations applied; envelope signer; env wiring)
- `experience-bff` (probe controller; `tshepo-sdk` envelope filter; env wiring)
- Envoy ConfigMap regenerated **only** when Flip 1 is authorised (and only after D-1)

Confirm Flyway has applied through V060 on the authz database before Flip 1's
instrument check.

---

## Pre-flight (every flip)

```bash
# 1. Public stack is the expected one
bash scripts/operator/report-preview-generation.sh
# Expect: SINGLE_PUBLIC_STACK: yes

# 2. Authz and BFF healthy
kubectl -n impilo-full-preview get deploy tshepo-authz-service experience-bff
kubectl -n impilo-full-preview rollout status deploy/tshepo-authz-service --timeout=120s
kubectl -n impilo-full-preview rollout status deploy/experience-bff --timeout=120s

# 3. Probe reachable today (before Flip 1 this proves the service path, not the PDP)
curl -sS -o /tmp/probe.json -w "%{http_code}\n" \
  https://impilo.mohcc.gov.zw/internal/v1/gateway-probe
# Expect: 200 and {"probe":"gateway-probe","reachedService":true,...}
```

Authorisation phrases (product owner types exactly):

| Flip | Phrase |
|---|---|
| 1 | `AUTHORIZE D7 FLIP 1 EXT_AUTHZ` |
| 2 | `AUTHORIZE D7 FLIP 2 WORK_CONTEXT_ENFORCE` |
| 3 | `AUTHORIZE D7 FLIP 3 BOUNDARY_RULES` |
| 4a | `AUTHORIZE D7 FLIP 4 ENVELOPE_SHADOW` |
| 4b | `AUTHORIZE D7 FLIP 4 ENVELOPE_ENFORCE` |

---

## Flip 1 — Put the PDP on the request path

**Goal.** Envoy consults `tshepo-authz-service` `/v1/authorize` for non-bypass
routes. `failure_mode_allow: false` is intentional: a PDP outage denies.

**Instrument.** `GET /internal/v1/gateway-probe` (BFF) + policy rows from V060:

- `d7-gateway-probe-allow` — `ALLOW`, `active=true` (so the route is reachable
  once `ext_authz` is on; without it the PDP would refuse with
  `NO_MATCHING_RULES` and a 403 would be ambiguous)
- `d7-gateway-probe-deny` — `DENY`, `active=false` until the check below

**Execute (only after D-1 is cleared, or with an explicitly authorised
ConfigMap-only patch — never a silent helm upgrade under drift):**

1. Set `envoy.extAuthz.enabled: true` in the values that will be applied.
2. Apply the Envoy config such that the live ConfigMap contains `ext_authz`
   (today's live ConfigMap contains **zero** `ext_authz` references — confirm
   before and after).
3. Roll Envoy; wait for Ready.
4. Baseline: probe returns **200**.
5. Activate the instrument:

```sql
UPDATE tshepo_authz.policy_rule SET active = true
 WHERE name = 'd7-gateway-probe-deny';
-- then invalidate the policy cache (restart authz, or hit the management
-- invalidation path if one is exposed for the tenant)
```

6. Probe returns **403**. If it stays **200**, the gateway is not consulting
   the PDP for this path — stop, roll back Flip 1, do not proceed.
7. Deactivate the instrument immediately:

```sql
UPDATE tshepo_authz.policy_rule SET active = false
 WHERE name = 'd7-gateway-probe-deny';
```

8. Probe returns **200** again.

**Soak.** 24h with the instrument inactive. Watch:

- Envoy `ext_authz` error rate / 5xx from authz
- Authz p99 latency on `/v1/authorize` (envelope minting is still off)
- Bypass routes still work: `/health`, auth initiation/callback, public gateway lane

**Pass criterion.** Instrument check (steps 4–8) succeeded once; soak shows no
sustained authz outage; bypass list still complete for the public surface.

**Rollback.**

```bash
# Prefer: re-apply values with envoy.extAuthz.enabled=false, same pin as live
# Under the hold: restore the previous Envoy ConfigMap and roll Envoy
kubectl -n impilo-full-preview rollout undo deploy/envoy   # if undo is safe
# Confirm: ConfigMap has zero ext_authz; probe still 200; real traffic recovered
```

Also ensure `d7-gateway-probe-deny` is `active=false` after any abort.

---

## Flip 2 — Duty-context binding ENFORCE

**Goal.** A mismatched / revoked / absent WORK_CONTEXT token on clinical writes
is denied, not only audited (`WORK_CONTEXT_MISMATCH` / related signals).

**Precondition.** Flip 1 passed. Mint path
(`POST /internal/v1/work-context/session` with `contextId`) is known healthy.

**Execute (kubectl env — does not require helm):**

```bash
kubectl -n impilo-full-preview set env deploy/tshepo-authz-service \
  TSHEPO_WORK_CONTEXT_MODE=ENFORCE
kubectl -n impilo-full-preview rollout status deploy/tshepo-authz-service --timeout=180s
```

**Soak.** 48h. Watch governance signals and deny rate:

- `WORK_CONTEXT_MATCHED` vs `WORK_CONTEXT_MISMATCH` / revoked
- Clinical write 403 rate (expect a rise only where sessions lack a real token —
  that is the point; investigate mass denial as a rollout bug)

**Pass criterion.** Known good duty sessions still work; forged / missing duty
on a clinical write is denied; no estate-wide clinical outage.

**Rollback.**

```bash
kubectl -n impilo-full-preview set env deploy/tshepo-authz-service \
  TSHEPO_WORK_CONTEXT_MODE=SHADOW
```

---

## Flip 3 — Activate WorkMode / clinical boundary rules

**Goal.** V055 (negative WorkMode boundaries) and V058 (clinical access requires
an IDENTIFIED-capable mode) become live DENY rows. They are already seeded;
this flip only sets `active=true`.

**Precondition.** Flip 2 passed. V059 path-pin correction has been applied
(V055 pins without the trailing-slash defect). Confirm:

```sql
SELECT name, conditions->>'path_contains' AS pin, active
FROM tshepo_authz.policy_rule
WHERE name LIKE 'work-mode-%' OR name LIKE 'clinical-lock-%'
ORDER BY name;
-- pins must NOT end with '/'; clinical-lock-* and work-mode-* start false
```

**Execute:**

```sql
BEGIN;
UPDATE tshepo_authz.policy_rule SET active = true
 WHERE name LIKE 'work-mode-%' OR name LIKE 'clinical-lock-%';
-- Do NOT activate d7-gateway-probe-deny here
COMMIT;
-- Invalidate policy cache for the platform tenant
```

Optional staged sub-order if soak is noisy: activate V055 names first, soak
24h, then V058 `clinical-lock-%`.

**Soak.** 48h after full activation. Watch:

- Denies attributed to those rule names in decision logs
- Support / facility-management / courier sessions cannot read `/patients/{id}`
- `CLINICAL_CARE` / `VIRTUAL_CARE` / `COMMUNITY_OUTREACH` still can
- Token-less sessions: V058 uses `allowed_modes` on DENY rows, so ABSENT mode
  does not fire the lock — Flip 2 already handles duty absence on clinical writes

**Pass criterion.** Matrix in `ClinicalAccessBoundaryMatrixTest` holds in
production traffic samples; no unexpected mass deny on token-less read paths
that Flip 2 still permits.

**Rollback.**

```sql
UPDATE tshepo_authz.policy_rule SET active = false
 WHERE name LIKE 'work-mode-%' OR name LIKE 'clinical-lock-%';
```

---

## Flip 4 — Signed decision envelope

**Goal.** PDP mints `x-decision-envelope` on ALLOW; downstream (`experience-bff`
first) verifies it.

### 4a — Mint + verify SHADOW

```bash
# Mint on (authz)
kubectl -n impilo-full-preview set env deploy/tshepo-authz-service \
  TSHEPO_DECISION_ENVELOPE_ENABLED=true

# Verify in SHADOW on BFF (logs SHADOW_MARKER; never rejects)
kubectl -n impilo-full-preview set env deploy/experience-bff \
  IMPILO_TRUST_DECISION_ENVELOPE_MODE=SHADOW
# Confirm JWKS reaches keys-service from the BFF pod if not using the default
```

**Soak.** 48h. Watch:

- Authz `DecisionEnvelopeSigner` signed vs failed counts / `UNSIGNED_MARKER`
- Signing latency (keys-service hop); abort if p99 `/v1/authorize` regresses
  past the agreed budget
- BFF `SHADOW_MARKER` rate — investigate spikes before ENFORCE

**Pass criterion.** Most ALLOW decisions carry a verifiable envelope; shadow
rejection rate understood and acceptable; probes still Ready.

**Rollback 4a.**

```bash
kubectl -n impilo-full-preview set env deploy/experience-bff \
  IMPILO_TRUST_DECISION_ENVELOPE_MODE=OFF
kubectl -n impilo-full-preview set env deploy/tshepo-authz-service \
  TSHEPO_DECISION_ENVELOPE_ENABLED=false
```

### 4b — Verify ENFORCE (BFF first)

**Precondition.** 4a soak passed. D-2 acknowledged: only services that verify
are protected; `permitAll` services remain a hole.

```bash
kubectl -n impilo-full-preview set env deploy/experience-bff \
  IMPILO_TRUST_DECISION_ENVELOPE_MODE=ENFORCE
```

**Soak.** 72h on BFF alone before expanding to other services.

**Pass criterion.** Missing / forged / wrong-actor envelopes → 403 with
`ENFORCE_MARKER`; exempt prefixes (`/actuator`, `/health`, …) still succeed;
no Ready flap.

**Rollback 4b.**

```bash
kubectl -n impilo-full-preview set env deploy/experience-bff \
  IMPILO_TRUST_DECISION_ENVELOPE_MODE=SHADOW
# or OFF if minting is also being disabled
```

Expanding ENFORCE beyond BFF is a later tranche, service-by-service, same
SHADOW→ENFORCE pattern, gated on D-2 for any service that still
`permitAll`s.

---

## What this wave shipped for the cutover (inert until fired)

| Artifact | Role |
|---|---|
| `GatewayProbeController` | Harmless route that exists to be denied |
| `V060__gateway_probe_deny_rule.sql` | Live ALLOW + inactive DENY for that route |
| `V058` / `V059` | Clinical lock + V055 path-pin correction (already on branch) |
| Authz `decision-envelope-enabled` env | Flip 4a without helm |
| BFF `impilo.trust.decision-envelope.mode` env | Flip 4a/4b without helm |

Nothing in that table changes runtime behaviour at default flag values.

## Explicit non-goals of this document

- Does **not** lift the helm repin hold.
- Does **not** close the OAuth-disable / `permitAll` interlock estate-wide.
- Does **not** install `ipset` or write decorative NetworkPolicies.
- Does **not** fire any flip.
