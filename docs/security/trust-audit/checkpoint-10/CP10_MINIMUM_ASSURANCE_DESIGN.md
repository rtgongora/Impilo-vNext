# CP10 — Design: closing client-elective AAL2

Written 2026-08-03. **This is a design. Nothing in it has been implemented, and nothing was changed
on the live estate.** The brief asked for the design first, and there is a specific reason to hold:
the current shape is the *only* 2FA path on the estate, and a half-applied change to it removes 2FA
rather than strengthening it.

Facts below were read from source at the stated file:line, or measured by probe where marked. The
enforcement measurement that bounds this design is
[`CP10_MEASURED_ENFORCEMENT_POSTURE.md`](CP10_MEASURED_ENFORCEMENT_POSTURE.md).

---

## 1. What the defect actually is

The brief states it correctly, and reading the code confirms it precisely.

`OidcSessionService.validateRequestedAcr`
(`services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/auth/session/OidcSessionService.java:385-390`):

```java
385	    private static void validateRequestedAcr(String requested, String actual) {
386	        if (requested == null) return;
387	        int requestedRank = aalRank(requested);
388	        int actualRank = aalRank(actual);
389	        if (actualRank < requestedRank) throw new OidcProtocolException("OIDC_AAL_NOT_SATISFIED");
390	    }
```

It has **exactly one caller**, at `OidcSessionService.java:119`, inside `complete(state, code)`.

The requested value originates in the browser at
`ui/one-ui-shell/src/components/auth/ProgressiveAuthForm.tsx:48`:

```tsx
48	      requiredAcr: intent === "personal" ? null : "urn:impilo:aal2",
```

and travels as a **query parameter on a full-page GET navigation** —
`ui/one-ui-shell/src/lib/auth/web-session.ts:82-84` sets `query.set("acr", …)` then
`window.location.assign("/internal/v1/auth/oidc/authorize?…")`, handled by
`OidcSessionController.java:36-44` with `@RequestParam(required = false) String acr`.

### The precise statement of the hole

**`validateRequestedAcr` is not buggy. It does exactly what its name says** — it verifies you got
what you asked for. It is a *replay/downgrade* check on the identity provider, and as such the
`if (requested == null) return;` early return is correct: if you asked for nothing, the IdP cannot
have failed to deliver it.

The hole is that **nothing else establishes a floor**. There is no code path anywhere in the BFF
that asks "does this caller's session meet the minimum assurance this action requires?". Three
consequences follow, and all three matter:

1. It is a **login-time** check. Even when it fires, it constrains the session at the moment of
   minting and never again.
2. The **floor is supplied by the party being constrained**. `normalizeAcr`
   (`OidcSessionService.java:400-404`) validates the requested value is one of
   `ALLOWED_ACR = ["urn:impilo:aal1","urn:impilo:aal2","urn:impilo:aal3"]` — that is a *shape*
   check, not a *sufficiency* check. Nothing ever raises the value.
3. `intent` is a **client-side radio group**. Any caller that omits `acr` — a modified shell, a
   direct call to `/internal/v1/auth/oidc/authorize`, any non-browser client — gets an
   `urn:impilo:aal1` session, and `OidcSessionService.java:320` then silently floors a missing
   claim to `aal1` rather than refusing.

The previous holder proved this empirically: `dr.mapfumo` signed in requesting no acr, received
`acr=urn:impilo:aal1` as `[CLINICIAN]`, and reached `/work`. I did not re-run that probe (it
requires a human credential, which is withheld) and am relying on their record for it. Everything
else in this section I read directly.

### What already exists, and is closer than the brief suggests

Three things are already built, which changes the shape of the fix substantially:

- **A working per-action AAL gate exists** — `SecuritySettingsService.requireFreshAal2`
  (`.../security/SecuritySettingsService.java:103-111`) throws `STEP_UP_AAL2_REQUIRED` when
  `aalRank(acr) < 2` and `FRESH_AUTHENTICATION_REQUIRED` outside a 300-second `auth_time` window. It
  is hand-rolled and wired into **3 methods of 1 service**. It is a proof the pattern works, and a
  template.
- **The PDP already evaluates assurance as policy data.** `PolicyEngine.java:804-819` and
  `meetsAuthenticationRequirement` (`:1041-1052`) evaluate JSONB rule conditions `min_aal`,
  `max_auth_age_seconds`, `phishing_resistant_required` and `accepted_amr` against
  `AuthenticationAssurance`. The durable home is not hypothetical; it is built and running.
- **The challenge contract carries the answer.** `TrustChallengeOutcome`
  (`libs/tshepo-contracts/.../v1/TrustChallengeOutcome.java:146-161`) has
  `@JsonProperty("required_assurance") Integer requiredAssurance` and a `STEP_UP_REQUIRED` decision.

So the gate is not missing because it is hard. It is missing because **the BFF never lets the
answer reach a controller** — `TshepoAuthzServiceClient.syntheticAuthorizeVerdict` (`:247-250`,
`@Deprecated`) collapses the outcome to a boolean and discards `requiredAssurance`, and the client
POSTs `/v1/authorize` with an **empty body** (`:266-302`), carrying only `x-original-method` and
`x-original-path`.

---

## 2. Where the gate belongs

### The constraint that decides it

Envoy `ext_authz` is **off** — I confirmed the running config: probes to `envoy:10000` are proxied
and refused by the upstream service, not by an authorization filter, and the brief records that the
`ext_authz` matches in the running config are comments. So today the BFF is the only enforcement
point for browser and mobile traffic.

But my enforcement measurement bounds how much that is worth, and the bound must be stated plainly:

> **A BFF-side assurance gate constrains only callers that go through the BFF.** 11 services serve
> unauthenticated business requests directly, and `hapi-fhir` serves `GET /fhir/Patient`
> unauthenticated. A caller inside the namespace does not need a session at all, let alone an AAL2
> one. The AAL2 gate is necessary and is *not* the estate's largest exposure.

That is not an argument against building it — the BFF is where clinical work is actually driven from
— but a design that is quoted as "clinical work now requires a second factor" without that sentence
attached would be the exact failure mode §8 of the brief describes: a facet name broad enough to
stay true.

### Rejected: per-endpoint annotations

The BFF has **≈3,270 handler methods across ~409 `@RestController` classes**. A
`@RequiresAssurance(2)` annotation is the obvious design and is wrong as a first move: it is a
3,000-site change whose default is "absent", so the security property is "whatever nobody annotated
is ungated" — invisible, and it silently un-gates every new endpoint. It is a reasonable *second*
step for narrowing, once a central gate makes the default safe.

### Chosen: a central, registry-driven filter — modelled on a control that already works

`RecoverySessionFilter` (`.../auth/session/RecoverySessionFilter.java`) is the structural precedent
and should be copied deliberately:

- a static registry mapping `(HTTP method, path Pattern) → canonical ACTION:RESOURCE_TYPE` (`:50-70`)
- checked against a permitted set (`:76-83`)
- **fail-closed when nothing matches** (`:130 return false;`)
- registered in the Spring Security chain (`SecurityConfig.java:607`)

The new control is `MinimumAssuranceFilter`, an `OncePerRequestFilter` registered
**`addFilterAfter(..., BearerTokenAuthenticationFilter.class)`** — after bearer authentication, like
`ActorContextFilter` (`SecurityConfig.java:612`), because it needs an authenticated principal to
read `acr` from.

### The single most important design decision: read the token, not the cookie

`requireFreshAal2` reads the **JWT `acr` claim**. `RecoverySessionFilter` reads **`SessionData`**
from Redis. The new gate must read the **JWT claim**, with `SessionData` used only to enrich
(`stepUpTime`, `recovery`).

This is decisive, not stylistic. The attack in the brief is *"any non-browser client"*. A gate that
resolves assurance from the `__Host-impilo_session` cookie is bypassed completely by a caller that
presents a bearer token and no cookie — which is precisely the caller the gate exists to stop, and
is how mobile talks to the BFF. **A cookie-sourced assurance gate would be a control that reads as
present and does nothing** — instance nine of the pattern.

`SessionData.acr` (`WebAuthSessionStore.java:258`) stays as the read model for
`GET /internal/v1/auth/oidc/session`; it is not the enforcement input.

### Scope: fail-closed inside a declared boundary

The honest tension: the brief forbids fail-open behaviour, and a registry defaulting to "no minimum"
for 3,270 endpoints is fail-open by another name. But a registry defaulting to "AAL2 required" for
3,270 endpoints breaks every citizen wellness journey on the estate, and would be reverted within a
day — which is worse, because a reverted control protects nothing.

The resolution is a **declared governed scope**:

```
governedPrefixes:                       # inside these, an unmatched route is DENIED
  - /internal/v1/pct/**
  - /internal/v1/prescriptions/**
  - /internal/v1/imaging/**
  - ...
rules:                                  # explicit minima inside the scope
  - { methods: [POST,PUT,PATCH,DELETE], path: /internal/v1/prescriptions/**, minAal: 2, maxAuthAgeSeconds: 900 }
  - { methods: [GET],                   path: /internal/v1/pct/**,           minAal: 2 }
```

Outside `governedPrefixes` the filter does not apply and existing authn/authz is unchanged. Inside
it, an unmatched route is refused. This is not fail-open: it is a boundary that is **fail-closed
within itself** and **explicitly, reviewably narrow** — and a guard freezes the prefix list so
widening or narrowing it is a reviewed act, exactly as `check-enforcement-posture.sh` freezes the
open-services baseline. The scope grows over time; the security property at every point is stated
by the prefix list rather than by what somebody remembered to annotate.

Endpoints that must be excluded or the gate deadlocks: `/internal/v1/auth/oidc/**` (the step-up path
itself), `/internal/v1/auth/contact/otp/**`, and `/actuator/**`.

### Where the minimum comes from — and how it stops being code

The filter must not hard-code minima, or the PDP migration becomes a rewrite. Interpose a resolver:

```java
interface MinimumAssuranceResolver {
    OptionalInt minimumFor(String method, String path);
}
```

- **Stage 1 (now):** `StaticMinimumAssuranceResolver` — the registry above, config-bound.
- **Stage 2 (durable):** `PdpMinimumAssuranceResolver` — reads `required_assurance` from
  `TrustChallengeOutcome`, which `PolicyEngine` already computes from JSONB `min_aal` conditions
  (`PolicyEngine.java:804-819`). Assurance minima become *policy data in the tshepo DB*, which is
  the doctrinal position: the PDP decides, the BFF enforces.

Stage 2 is blocked on repairs that are separately worth doing and are **not** in this design's
scope: `syntheticAuthorizeVerdict` discarding `requiredAssurance` (`TshepoAuthzServiceClient.java:247-250`),
the empty-body `/v1/authorize` call (`:266-302`), and
`AuthzResponseChallengeAdapter.java:47` defaulting `requiredAssurance` to `2` when the legacy wire
carries no `stepUpRequirement` — a default that is safe today only by coincidence.

### What the gate returns

Not a bare 403. `STEP_UP_REQUIRED` through the existing, tested challenge machinery:
`TrustChallengeResponder`, `TrustChallengeOutcome`, and the single-use continuation store — all
built and test-proven under CP6, and currently reachable from **one** call site
(`TelemedicineGovernanceService.java:130`). This gate becomes its second real consumer, and
unblocks brief item §4.5 (progressive step-up), where `POST /internal/v1/auth/oidc/step-up` exists,
is tested, and is called by nothing but `settings/security/page.tsx:50`.

---

## 3. The login-time half — and why it is a redirect, not a refusal

The per-action gate alone leaves a gap worth closing at the same time: a `[CLINICIAN]` can still
*obtain* an `aal1` session. Nothing then breaks until they touch a governed route, but the session
exists, and every ungated BFF route and all 11 open services accept it.

At `OidcSessionService.complete()`, after token exchange, the realm roles are already in hand —
`profile.put("roles", realmRoles(jwt))` at `OidcSessionService.java:315`. So a server-side floor is
derivable from the *authenticated identity* rather than the client's claimed intent:

> If the subject holds a workforce/clinical realm role and the minted `acr` ranks below 2, the login
> is **not** completed at `aal1`.

**It must redirect, not throw.** Throwing `OidcProtocolException` here would mean a clinician who
authenticated correctly is told their login failed, with no route to satisfy the requirement — the
same shape as the §7 hazard about clearing `CONFIGURE_TOTP`, where removing the prompt leaves the
requirement. The correct behaviour reuses machinery that already exists: re-drive to
`sessions.begin(returnTo, "urn:impilo:aal2", null, sessionId, …)`, the exact call the step-up
endpoint already makes (`OidcSessionController.java:87-89`), which sets `previousSessionId` so
`OidcSessionService.java:322` stamps `stepUpTime` correctly.

**`validateRequestedAcr` itself should not be modified.** It is correct for its job. Changing
`if (requested == null) return;` to impose a default would conflate two different questions — "did
the IdP honour the request" and "was the request sufficient" — and would make the downgrade check
harder to reason about. The floor belongs beside it, not inside it.

Two cleanups that fall out and should ride along: `aalRank` is duplicated verbatim at
`OidcSessionService.java:392-398` and `SecuritySettingsService.java:157-163`, and the estate carries
both a URN spelling (`urn:impilo:aal2`) and the canonical numeric `int aal` in
`libs/tshepo-contracts/.../v1/AuthenticationAssurance.java:18`. One conversion, in one place, onto
the contract type.

---

## 4. Tradeoffs, stated plainly

| Decision | Cost |
|---|---|
| Central filter, not per-endpoint annotations | Path patterns are a weaker expression of intent than an annotation on the method, and they drift when routes are renamed. Mitigated by a guard asserting every governed prefix matches ≥1 live mapping — a rule matching nothing must go red, or it is instance ten of the pattern. |
| Fail-closed inside a declared scope | The estate is not fully gated on day one, and the honest facet name is *"AAL2 is enforced on the declared governed routes"*, never *"AAL2 is enforced"*. |
| JWT claim as the assurance source | A revoked/downgraded session stays valid until token expiry. Acceptable at current TTLs; `stepUpTime` freshness from `SessionData` narrows it for the highest-risk actions. |
| Static resolver first, PDP second | Two homes for minima temporarily. The interface keeps it to one swap, but a Stage-1 registry left in place after Stage 2 lands would be a second source of truth — it must be deleted, not disabled. |
| `STEP_UP_REQUIRED` over 403 | More moving parts (continuation store, shell handling). But the shell's challenge handling is **built, 57 tests, never deployed** — so the first deploy of this gate needs the shell deployed too, or a clinician meets a challenge the UI cannot render. |
| BFF-only enforcement | Bypassable by anything that reaches services directly. See §2. |

### Prerequisites that are genuinely blocking

1. **The shell has never been deployed** (brief §4.7). Shipping a gate that emits `STEP_UP_REQUIRED`
   to a UI that cannot render it converts a governed refusal into a broken screen.
2. **35 of 51 users hold an OTP factor.** Enforcing AAL2 on clinical routes locks out every
   clinician without one. The enrolment sweep (brief §4.6) is a prerequisite, not a follow-up, and
   §7's warning applies: after enrolment, reconcile enrolled-in-Keycloak against secrets-held.
3. **`tshepoPdpFallbackAllow` is still fail-open**, in five services —
   `ShellWorkspaceAuthorizationService:46`, `ImagingGovernanceService:58,74,90`,
   `PublicHealthGovernanceService:61`, `RegistryIntakeAuthorizationService:109`,
   `TelemedicineGovernanceService:127`. CP6 deferred it to CP8; CP8 did not do it. A new gate must
   not acquire this flag, and the existing five should be retired in the same programme.

---

## 5. How to prove it, in both directions

Per the brief's law, and stated before implementation so the proof cannot be fitted to the result.

**The gate fires (RED):** an `aal1` session, a governed clinical route → `STEP_UP_REQUIRED` with a
continuation, and the audit record shows the refusal.

**A success reaches the gate (GREEN) — the half that matters.** An `aal2` session doing the *same*
action on the *same* route must return **200**. A gate proven only by a denial is indistinguishable
from a gate that denies everything, which is precisely the defect in the fourteen BFF governance
checks (brief §8.1): the fail-closed catch-all made a thrown check identical to a refusal. **The
green case must be observed on a deployed pod with a real token**, not asserted from a unit test.

**Negative control on the boundary:** a route just *outside* `governedPrefixes` with the same `aal1`
session must return 200 — otherwise the filter is matching everything and the scope declaration is
decorative.

**The bypass control:** the same request with a **bearer token and no session cookie** must be
refused identically. This is the control that distinguishes this design from a cookie-sourced one,
and it is the specific attack in the brief. If it passes only with a cookie, the gate is theatre.

**Guard:** freeze `governedPrefixes` in both directions, as `check-enforcement-posture.sh` does for
the open-services baseline — and prove the guard on a clean tree *before* proving it red, per
runbook §10. Do not break a file in the shared tree to prove it; use a private worktree (§10a).

---

## 6. Recommended order

1. Enrolment sweep + reconcile (prerequisite; brief §4.6).
2. Deploy `one-ui-shell` so a challenge can be rendered (prerequisite; brief §4.7).
3. `MinimumAssuranceFilter` + `StaticMinimumAssuranceResolver`, scope = **one** governed prefix,
   behind a default-off flag. Prove all four controls in §5 on a deployed pod.
4. Login-time floor in `OidcSessionService.complete()` as a redirect (§3).
5. Widen `governedPrefixes` one prefix at a time, each with the §5 controls re-run.
6. `PdpMinimumAssuranceResolver`; delete the static registry.
7. Retire `tshepoPdpFallbackAllow`.

Steps 3–5 are the CP8-style gated process the estate skipped when the bypass flag vanished. The
point of writing them down before building is that the previous change of this size was ratified by
surviving, and `a9bbfd220` — *"28 services expected the internal issuer; every S2S call 401'd"* — is
what that costs.
