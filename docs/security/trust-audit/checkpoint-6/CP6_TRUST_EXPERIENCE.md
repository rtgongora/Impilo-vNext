# Checkpoint 6 — Unified trust experience

Status as at 2026-08-02. Branch `claude/tshepo-trust-completion-Yypyl`.

Truth layers use the CP1 vocabulary: `SOURCE_IMPLEMENTED`, `TEST_PROVEN`, `PREVIEW_DEPLOYED`,
`PREVIEW_ENFORCED`. They are declared separately and are **not** cumulative claims.

---

## What was actually wrong

The checkpoint brief assumed the work was to build a challenge experience. It was not. Every
structural piece existed — the `tshepo.trust.v1` contract with all twelve decisions, the
single-use continuation store, `AuthzResponseChallengeAdapter`, `StepUpPrompt`. Four disconnects
made the whole of it unreachable, and each one read as "implemented" from the outside.

### 1. The BFF collapsed every decision to a boolean

`TshepoAuthzServiceClient.syntheticAuthorizeVerdict` returned `boolean`. `reasonCode`,
`stepUpMethods`, `requiredAssurance` and any continuation reference were discarded before a
single controller saw them. **No BFF endpoint could emit a challenge, because by the time a
decision reached one there was nothing left to emit** — including the `CONSENT_REQUIRED` added
in CP5.

Its catch-all also returned `false`. **A PDP outage and a deliberate refusal were the same
value.** A user told "you do not have access" when the policy service is down goes and asks for
permissions they already hold.

### 2. The legacy wire cannot carry an actionable outcome

`AuthzResponse` has three verdicts. `CONSENT_REQUIRED` can only travel as a `DENY` with that
error code, and the adapter flattened every `DENY` into `TrustChallengeDecision.DENY`.
"You may not" and "nobody has asked you yet" arrived identical.

Fixed with an **allowlist** — an unrecognised refusal reason stays a refusal, so a new deny code
can never silently acquire a call to action.

### 3. Refusals were rendered as bare 403s with log prose

Every governance service threw `ResponseStatusException(FORBIDDEN, "Tshepo PDP denied
telemedicine read")`. The status carried no information; the body carried nothing renderable.

### 4. The shell handled 401 only; 403 appeared nowhere

So a governed refusal reached the user as an opaque failure. Citizen record-sharing read every
403 as `"Create failed"`.

### 5. The BFF could never reach the PDP at all

Found by deploying and probing a live pod. The client sent the action it was asking about as
HTTP/2 pseudo-headers `:method` / `:path`. **A colon-prefixed header name is unsendable over
HTTP/1.1** — the pod logged `invalid header name: ":method"` — so every call threw and the
catch-all returned `false`. **All fourteen BFF governance checks had always denied**, and nothing
distinguished a thrown check from a policy decision.

Doubly broken: had the headers been sendable, the PDP's servlet fallback would have evaluated the
literal `POST /v1/authorize`, matching no rule and denying anyway.

Fixed with `x-original-method` / `x-original-path`, precedence pseudo-header → alias → servlet so
Envoy still wins on the ext_authz path. **These are decision inputs and are stripped in all three
Envoy strip lists** — a client able to set them chooses which resource its own request is
authorized against, which is worse than spoofing `x-actor-id` because it redirects the *question*
rather than the answer.

Every test passed against the broken version. Source and unit truth were both green; only a
deployed probe exposed it.

### 6. A governed refusal came back as a downstream outage

Also live-only. `TeleconsultController` has 53 catch-alls around 39 governance calls, and it
branches on `ResponseStatusException` — which is what the old refusals were. The new exception was
a plain `RuntimeException`, so it fell to `catch (Exception)` and returned `502 PCT_UNAVAILABLE`:
a consent problem reported as a service being down. **This was a regression I introduced**, and it
is exactly what deploying is for.

Two fixes: the exception extends `ResponseStatusException` (so every existing catch produces a
sensible status), and `upstreamFailure` rethrows it — that helper's own javadoc already warned
that "masking them as 502 PCT_UNAVAILABLE turns governed rejections into fake outages".

### 7. Mobile's step-up branch was unreachable — on both wires

`mobile-api-client` checked `body.decision === "STEP_UP_REQUIRED"`. **Neither producer emits
`decision`**: ext_authz serialises `AuthzResponse`, whose field is `verdict`; the BFF nests the
outcome at `error.details.trust_challenge`. The condition could never be true.

Mobile has never been able to prompt for verification. Not a disabled feature — an unreachable
branch that read as implemented.

---

## Facet truth

| Facet | SOURCE | TEST | PREVIEW_DEPLOYED | PREVIEW_ENFORCED |
|---|---|---|---|---|
| Typed PDP decision in BFF (`TrustDecisionResult`) | ✅ | ✅ 9 | ✅ | n/a |
| BFF→PDP authorize wire reaches the PDP at all | ✅ | ✅ 4 | ✅ | n/a |
| Outage distinguished from refusal (503 vs 403) | ✅ | ✅ | ✅ | n/a |
| Actionable deny-code promotion (allowlist) | ✅ | ✅ | ✅ | n/a |
| Canonical challenge envelope over real HTTP | ✅ | ✅ 13 | ✅ | n/a |
| Alias headers stripped at the edge | ✅ | ✅ | ✅ config-verified | ⚠️ not runtime-proven |
| Continuations generalised beyond recovery | ✅ | ✅ 8 | ✅ | n/a |
| Continuation carried through step-up round trip | ✅ | ✅ 3 | ✅ | n/a |
| Shell challenge parsing + presentation | ✅ | ✅ 57 | ❌ | ❌ |
| Mobile challenge recognition | ✅ | ✅ 14 | ❌ | ❌ |
| Governance services emitting challenges | ⚠️ 1 of 9 | ✅ | ✅ | n/a |
| Browser (Playwright) proof of a challenge | ❌ | ❌ | ❌ | ❌ |
| Redroid proof of a mobile challenge | ❌ | ❌ | ❌ | ❌ |

### Live evidence

Probed inside a running `experience-bff` pod after deploying both services:

```
STATUS=403
{"error":{"code":"MISSING_HEADERS","message":"trust.deny.generic",
  "details":{"trust_challenge":{"decision":"DENY",
    "contract_version":"tshepo.trust.v1","reason_code":"MISSING_HEADERS",
    "user_message_key":"trust.deny.generic",
    "allowed_authentication_methods":[],"context_options":[]}},
  "request_id":"req-cp6-5","correlation_id":"corr-cp6-5"}}
```

403 rather than 502; a real PDP reason code; a message key rather than "Tshepo PDP denied
telemedicine read". Before this wave the same probe returned `502 PCT_UNAVAILABLE`, and before
that the call never reached the PDP at all.

**Deployed digests** (rollback targets recorded):

| Workload | Rollback (prior) | Deployed |
|---|---|---|
| `experience-bff` | `sha256:553598c7…` | `sha256:3bac520a…` |
| `tshepo-authz-service` | `sha256:183ab8ef…` | `sha256:b231a0af…` |

Java: `experience-bff` 1814 tests, 0 failures. Shell: 988 passing in `src/lib`.
Guard `check-trust-decision-contracts.sh` green, proven RED first.

---

## Deliberately not done

- **`tshepoPdpFallbackAllow` keeps its fail-open behaviour.** Changing a fail-open default while
  restructuring how refusals are *reported* would mix a behavioural change into a presentation
  change. It belongs to CP8.
- **Eight of nine governance services still use the deprecated boolean wrapper.** Telemedicine is
  converted as the first consumer and the pattern is proven. Converting the rest is mechanical
  but each one needs its synthetic paths checked against the PDP's rules — the first draft of the
  telemedicine conversion got all three paths wrong by spelling them at the call site, which is
  why paths are now declared only on the client.
- **Policy prose is never carried toward a user.** The canonical contract has no free-text field
  and `errorMessage` has not been through the sensitive-value fence. Copy resolves
  `user_message_key`, then decision, and never the reason code.
- **No new backend endpoints were invented for the UI.** Where no handler is supplied, permitted
  context options are listed rather than rendered as controls that would go nowhere.

## Known environmental limits

- **jest-dom matchers are broken in this worktree.** `ui/node_modules` and
  `ui/one-ui-shell/node_modules` symlink to two different checkouts, so two module trees are live
  and `jest-dom/vitest` extends an `expect` the tests do not use. Untouched files fail
  identically (`TrustProfilePanel.test.tsx`, four invitation components). Component tests here
  use plain DOM reads instead. **Pre-existing; not introduced by this checkpoint.**
- **Workspace packages resolve to the canonical checkout.** `@impilo/mobile-trust` resolved
  through `node_modules` into `/opt/impilo/repos/Impilo-vNext`, so mobile tests exercised a
  different tree's copy of the package — a new export appeared not to exist. Fixed with a vitest
  alias onto the workspace source, as Metro resolves it.

---

## Terminal status

**CHECKPOINT 6 PARTIAL.**

The service half is real: the BFF↔PDP wire works for the first time, and the canonical challenge
envelope was observed over real HTTP on a deployed pod. Seven defects are closed, three of which
were invisible to every test and to source review, and one of which I introduced and then caught
by deploying.

It is **PARTIAL, not READY**, on three specific counts:

1. **No browser proof.** `one-ui-shell` is not rebuilt or deployed, so no user has yet seen a
   trust challenge. Per constraint C3 the API proof above does not substitute.
2. **No Redroid proof** of the mobile path.
3. **The alias-header strip is deployed but not runtime-proven.** `x-original-method` /
   `x-original-path` are now in the running Envoy ConfigMap (verified by reading
   `/etc/envoy/envoy.yaml` inside the live pod: 2 entries, at the same two routes as
   `x-actor-id`). The in-flight negative control — send a spoofed header through the edge and
   confirm the upstream never sees it — was **not run**, because the Envoy image has no HTTP
   client. Config parity is good evidence; it is not the same as observing the strip happen.

   Worth recording how this nearly went wrong: the first `kubectl apply` reported
   `configmap/envoy-config created` and I nearly accepted it. The rendered manifest carried
   `namespace: default`, so it had created a stray ConfigMap in the wrong namespace while the real
   one was untouched. **"created" rather than "configured" for an object 16 days old was the
   tell.** The stray object was removed (it had not existed before, so removal restored the prior
   state exactly) and the apply repeated with an explicit namespace.

Claiming READY on a service-level probe would be the exact overclaim this audit exists to prevent.
