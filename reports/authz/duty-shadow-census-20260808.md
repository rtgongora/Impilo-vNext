# Duty shadow — costa and general-ledger money decisions

**Date:** 2026-08-08 · **Namespace:** `impilo-full-preview` · **Mode:** `SHADOW` (no response changed)

## What it answers

`ActorTypeGuard` can only ask *what kind of principal* is calling, so today **any `OPERATOR` can
approve any waiver or close any accounting period**. The shadow evaluates the question the
actor-type gate cannot — *which duty does this caller hold?* — using the **signed realm roles in the
JWT** rather than a header, and logs what it *would* refuse.

## Result

**7 observations across both services.**

| Lane | Verdict | Count |
|---|---|---|
| `MACHINE` | would **PERMIT** | 4 |
| `HUMAN` | would **DENY** | **3** |

Every human-lane observation:

```
lane=HUMAN would=DENY actor_type=OPERATOR subject=service-account-impilo-bff roles_presented=[]
  costing-engine-service  /costa/v1/waivers
  general-ledger-service  /internal/v1/gl/periods/year-end
  general-ledger-service  /internal/v1/gl/accounts
```

**A workload token, carrying no duty role whatsoever, asserted `X-Actor-Type: OPERATOR` and cleared
the actor-type gate on a fee waiver, the chart of accounts, and year-end close.** That is the
forgeable-header hole stated as a measurement rather than an argument: the gate that stands in for
duty today is satisfied by a header any in-cluster caller can set.

## ⚠️ What this census does **not** establish

**All seven observations are my own probe traffic.** The preview estate had no organic money
traffic in the window, and no preview user credential could be obtained — realm users' passwords do
not match the committed fixture, `admin-cli` rejects them, and the `impilo-user-admin` service
account returns 403 on the admin API. I did not write to Keycloak's database to manufacture one.

So:

- ✅ The mechanism is **proven live** — both lanes classify correctly against real tokens.
- ✅ The forgeable-header finding is **confirmed live**, not inferred.
- ❌ **Zero role-bearing user traffic was observed.** The census cannot yet say whether enforcing is
  safe for real operators, because it has not seen one.

**Enforcement remains blocked**, on exactly the evidence P1 already demanded ("15% census, no duty
token — do NOT enforce on it"). Flipping `impilo.security.duty.mode=ENFORCE` needs either working
preview user credentials or an observation window containing real logins. The shadow is deployed and
will keep accumulating.

## Design decisions the numbers depend on

**The lane split.** A `client_credentials` token has no user and therefore **no roles by
construction**. Counting schedulers and service-to-service calls as would-deny would drown the
census in false positives and make the rate meaningless — those 4 `MACHINE` permits are exactly the
traffic that must not be counted. `MACHINE` requires **both** a machine actor type **and**
corroboration from the token (a service account, or no roles); trusting the header alone would let
the forgeable `X-Actor-Type` move a caller into the lane that is never questioned, which is the hole
being measured. Proven by mutation.

**It runs after the actor-type gate**, so the census counts only callers that already cleared it.
Otherwise every citizen refusal would also register as a duty would-deny and the rate would say
nothing about duty. This is why `PROVIDER` and `CITIZEN` probes produce no shadow lines — they are
refused earlier, correctly.

**Charge capture is excluded.** `POST /costa/v1/bills/draft` produced **no** shadow line, and the
same request returned 500 rather than 403 — so it passed the actor gate and the absence is
*exclusion*, not refusal. Raising a bill at the bedside is not a money decision and should never
require a finance duty.

**`FINANCE_CAPABLE` starts deliberately wide** — `FINANCE`, `SYSTEM_ADMIN`, `ADMIN`,
`NATIONAL_ADMINISTRATOR`, `FACILITY_ADMIN` — taken from the live realm (holder counts 2/3/4/1/3), not
invented. The census exists to tell us whether it can be **narrowed**; starting narrow would report a
would-deny rate reflecting the guess rather than the estate.

**An unreadable mode string defaults to `SHADOW`, never `OFF`** — a setting nobody can parse must not
silently disable a control.

## Next step

1. Obtain a working preview user credential (or wait for real logins) so the human lane sees
   role-bearing traffic.
2. Read the census: if would-deny is ~0 for real operators, narrow `FINANCE_CAPABLE` and enforce.
3. Enforce per path-class, money decisions first — `waivers`, `periods/year-end`, `receivables/write-off`.
