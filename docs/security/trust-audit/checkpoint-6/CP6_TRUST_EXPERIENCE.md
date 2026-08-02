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

### 5. Mobile's step-up branch was unreachable — on both wires

`mobile-api-client` checked `body.decision === "STEP_UP_REQUIRED"`. **Neither producer emits
`decision`**: ext_authz serialises `AuthzResponse`, whose field is `verdict`; the BFF nests the
outcome at `error.details.trust_challenge`. The condition could never be true.

Mobile has never been able to prompt for verification. Not a disabled feature — an unreachable
branch that read as implemented.

---

## Facet truth

| Facet | SOURCE | TEST | PREVIEW_DEPLOYED | PREVIEW_ENFORCED |
|---|---|---|---|---|
| Typed PDP decision in BFF (`TrustDecisionResult`) | ✅ | ✅ 9 | ❌ | ❌ |
| Outage distinguished from refusal (503 vs 403) | ✅ | ✅ | ❌ | ❌ |
| Actionable deny-code promotion (allowlist) | ✅ | ✅ | ❌ | ❌ |
| Canonical challenge envelope (`GlobalExceptionHandler`) | ✅ | ✅ 10 | ❌ | ❌ |
| Continuations generalised beyond recovery | ✅ | ✅ 8 | ❌ | ❌ |
| Continuation carried through step-up round trip | ✅ | ✅ 3 | ❌ | ❌ |
| Shell challenge parsing + presentation | ✅ | ✅ 57 | ❌ | ❌ |
| Mobile challenge recognition | ✅ | ✅ 14 | ❌ | ❌ |
| Governance services emitting challenges | ⚠️ 1 of 9 | ✅ | ❌ | ❌ |
| Browser (Playwright) proof of a challenge | ❌ | ❌ | ❌ | ❌ |
| Redroid proof of a mobile challenge | ❌ | ❌ | ❌ | ❌ |

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

The trust experience is implemented and test-proven end to end across contract, BFF, shell and
mobile, and the five defects above are closed. It is **not deployed**, so no user has yet seen a
trust challenge, and per constraint C3 an API proof does not substitute for a browser proof.

`PREVIEW_DEPLOYED` and `PREVIEW_ENFORCED` are `❌` for every facet. Claiming CP6 READY on source
and tests alone would be the exact overclaim this audit exists to prevent.

**To reach READY**: rebuild and deploy `experience-bff` and `one-ui-shell` as a targeted wave,
then capture a Playwright run of a real challenge (consent is the cheapest, since CP5 makes a
subject with no directive produce `CONSENT_REQUIRED` genuinely) and a Redroid run of the mobile
path.
