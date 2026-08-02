# OPA shadow — Checkpoint 4

**Branch:** `claude/tshepo-trust-completion-Yypyl`
**Namespace:** `impilo-full-preview` · **Captured:** 2026-08-02

`tshepo-authz-service` remains the sole authorization decision API. OPA evaluates the same
request in parallel and its verdict is compared, never applied.

## Deployed state

| Item | Value |
|---|---|
| OPA image | `mirror.gcr.io/openpolicyagent/opa@sha256:dd4cb51598c7…` (digest-pinned) |
| Policy | `infra/opa/authz/authz.rego`, checksum `0fe995afeeb5a746`, 28/28 `opa test` |
| Decision path | `POST /v1/data/impilo/authz/decision` |
| `tshepo-authz-service` | `127.0.0.1:5000/impilo/tshepo-authz-service@sha256:ff74ae2f019d…` (commit `facc96a0d`) |
| Rollback digest | `…@sha256:4da33b6f60ae10e647261fc908b2687c3dfb9c08ef1a4110f2c4b8a41a058e82` |
| `TSHEPO_AUTHZ_OPA_MODE` | `SHADOW` |
| Deployment method | `kubectl apply` of OPA objects only — **no `helm upgrade`** |

## What was broken before this checkpoint

The strangler existed in source and could not have produced a usable signal:

1. **No env binding.** `opa-url`/`opa-mode` appeared in `AuthzProperties` but nowhere in
   `application.yml`, so the mode could not be changed without rebuilding the image.
2. **Wrong input keys.** Java sent `identity_loa` / `authentication_aal`; the policy reads
   `input.loa` / `input.assurance_loa`. Both policy accessors default to `0` on a missing key, so
   `effective_loa` was 0 for every request and `MIN_LOA` would have fired on nearly all traffic —
   the comparison would have measured a typo and reported it as policy disagreement.
3. **ALLOW-only comparison.** `shadowCompareOpa` was called once, on the ALLOW path, with
   `javaAllow` hard-coded `true`. Every Java DENY was invisible.
4. **ENFORCE reachable by a string.** Nothing stopped an env var promoting an unvalidated corpus
   to authoritative.

## Enforcement is unreachable, and that was proven live

`OpaEnforceGate` refuses startup when `opa-mode=ENFORCE` without
`opa-parity-evidence-path` naming a file that exists. Proven against the running pod, not
asserted:

```
kubectl set env deploy/tshepo-authz-service TSHEPO_AUTHZ_OPA_MODE=ENFORCE
→ BeanCreationException: tshepo.authz.opa-mode=ENFORCE requires
  tshepo.authz.opa-parity-evidence-path to name the parity evidence justifying the
  promotion. It is empty. OPA must not become authoritative on the strength of a mode flag.
→ CrashLoopBackOff, restartCount 2, no decision served
```

Reverted to `SHADOW` immediately. An unrecognised mode (`SHADOWW`) is also refused rather than
silently treated as OFF.

## Measured shadow results (44 comparisons)

| Outcome | Count | Meaning |
|---|---|---|
| `agree` | 1 | Both engines denied `INVALID_PURPOSE` |
| `divergence_no_rule_coverage` | 43 | OPA allowed; Java denied on a **DB policy rule** OPA does not implement |
| `divergence` (**REAL**) | **0** | Genuine disagreement on a question both engines answered |
| `divergence_unmappable` | 0 | — |

Latency: 44 comparisons, 0.266 s total ⇒ **~6.1 ms mean**, ~4.2 ms excluding a single 86.8 ms
cold-start outlier. Within the ≤10 ms p95 target, to be re-measured under sustained load.

### Why 43 of 44 are not disagreements

The policy states its own scope: it decides the self-contained gate sub-decision (purpose
validity, `min_loa`, account assurance) and *"the DB-rule RBAC/ABAC … is intentionally NOT decided
here"*. When Java denies with `NO_MATCHING_RULES` / `POLICY_DENY` / `NO_ALLOW_RULE` and OPA allows
with no reasons, OPA was never asked the question. Counting those as divergence would report a
permanent ~100% rate on all rule-governed traffic and bury anything real.

The carve-out is narrow: it applies only when OPA allows **and** the Java code is one of those
three. A test pins that `CONSENT_DENIED` and `DEVICE_BLOCKED` against an OPA allow stay `REAL`.

## Coverage truth — parity may only be claimed over 4 of 11 rules

| Reachable now (4) | Inert, and why |
|---|---|
| `INVALID_PURPOSE` | `PROVIDER_SELF_CLAIM` — needs `action` (fine-grained verb vocabulary) |
| `MIN_LOA` | `PROVIDER_ID_REQUIRED` — needs `regulated_action` |
| `ACCOUNT_NOT_VERIFIED` | `WORK_REQUIRES_ASSIGNMENT` — needs `access_mode` (actor zone) |
| `SELF_TREATMENT` | `LOGIN_PROVIDERID_DENY`, `BADGE_NEVER_AUTHORISES`, `LOGIN_PERSON_FIRST` — need `action` + `identifier_kind` |
| | `WORK_TOKEN_CONTEXT_MISMATCH` — needs `access_mode` |

The unsupplied fields are left **absent**, not approximated. `access_mode` expects an actor zone
(`WORK|PROFESSIONAL|LIFE`) while the nearest local value is a `WorkMode`
(`CLINICAL_CARE`, `VIRTUAL_CARE`, `REGULATORY_OPERATIONS`, …) — a different vocabulary. Filling it
would leave two rules permanently unmatched *while appearing wired*. Every such rule is written to
be inert when its field is missing, so absent is honestly inert.

### Gaps in the opposite direction

Dimensions **Java enforces that the corpus has no rule for** — here OPA will ALLOW where Java
DENIES, and closing the gap means adding rules, not adding input keys:

| Dimension | Status |
|---|---|
| `min_aal` | Java enforces a minimum AAL; no `MIN_AAL` rule exists |
| `max_auth_age_seconds` | Java enforces authentication freshness; no equivalent |
| `phishing_resistant_required` | Java enforces phishing resistance; no equivalent |
| DB `policy_rule` RBAC/ABAC | Not implemented in the corpus at all |

## Gates

Guards, each proven by breaking what it guards and confirming RED:

- `scripts/guard/check-opa-policy-drift.sh` — deployed ConfigMap must match `infra/opa/authz`,
  the corpus must pass `opa test`, `*_test.rego` must never reach the runtime bundle, and a
  source policy missing from the bundle fails (the rule silently stops being evaluated while the
  file still looks authoritative).
- `OpaShadowInputMapperTest` — reads `authz.rego` itself and fails if the mapper emits a key the
  policy does not read, or if a policy rule is neither classified comparable nor inert. A test
  asserting only the Java side would not have caught defect 2.

## Honest status

**`CHECKPOINT 4` OPA facet: SHADOW deployed and producing an attributable signal.**
Not parity. 0 REAL divergences over 44 comparisons is a start, not a cut-over case: the traffic
was synthetic probe traffic, and the corpus does not implement the rule class that governs most
real decisions. ENFORCE remains structurally unreachable.
