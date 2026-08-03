# Checkpoint 9 — Final conformance and deployment truth

Branch `claude/tshepo-trust-completion-Yypyl`. Preview namespace `impilo-full-preview`.
All runtime figures measured live on 2026-08-02, not carried forward from earlier checkpoints.

Truth layers are the CP1 four: `SOURCE_IMPLEMENTED`, `TEST_PROVEN`, `PREVIEW_DEPLOYED`,
`PREVIEW_ENFORCED`. **They are declared separately and are never cumulative.** A facet may be
source-complete and test-proven and still change nothing for any user.

---

## Live estate, measured

| Fact | Value |
|---|---|
| Workloads in namespace | 114 |
| **Running with the OAuth bypass ON** | **95** |
| Running with it OFF | 3 — `tshepo-authz-service`, `tshepo-audit-service`, `workforce-governance-service` |
| ServiceAccounts | 108 (was 2 before CP4) |
| NetworkPolicies | 1 (cohort-1 only; enforcement substrate restored in CP4.6) |
| OPA mode | `SHADOW` |
| Envoy ext_authz | **still off** — the two matches in the running config are comments |
| Services open with no bypass flag to retire | 16 |

Of the three enforcing services, two predate this programme. **CP7 retired exactly one bypass.**

---

## Facet truth matrix

| Facet | SOURCE | TEST | DEPLOYED | ENFORCED |
|---|:--:|:--:|:--:|:--:|
| Authentication — a person signs in | ✅ | ✅ | ✅ | ⚠️ 3 of 98 |
| Authentication — the session carries a subject | ✅ | ✅ | ✅ *(fixed 2026-08-03)* | n/a |
| Authentication — AAL2 step-up is reachable | ✅ | ✅ | ✅ *(fixed 2026-08-03)* | n/a |
| Authentication — AAL2 is **required** for clinical work | ❌ | ❌ | ❌ | ❌ **client-elective** |
| Identity assurance / step-up decisioning | ✅ | ✅ | ✅ | ❌ |
| Workload identity — registry (130 rows) | ✅ | ✅ | ✅ | n/a |
| Workload identity — ServiceAccounts | ✅ | ✅ | ✅ 108 | ❌ not yet bound as identity |
| Workload credentials (audience-restricted tokens) | ✅ | ✅ | ⚠️ cohort only | ⚠️ cohort only |
| Transport (strict mTLS) | ❌ | ❌ | ❌ | ❌ |
| Network containment | ✅ | ✅ | ⚠️ 1 policy | ⚠️ cohort 1 only |
| Work context binding | ✅ | ✅ | ✅ | ❌ `SHADOW` |
| Authority resolution (appointment ≠ context) | ✅ | ✅ | ✅ | ❌ |
| Consent — capture and evaluation | ✅ | ✅ | ✅ | ⚠️ PDP off-path |
| Lawful basis beyond consent | ✅ | ✅ | ✅ | ❌ |
| Policy evaluation — PolicyEngine | ✅ | ✅ | ✅ | ⚠️ callers only |
| Policy evaluation — OPA parity | ✅ | ✅ | ✅ | ❌ `SHADOW`; `ENFORCE` structurally unreachable |
| Envoy edge enforcement | ✅ | ✅ | ⚠️ Stage 1 | ❌ ext_authz off |
| Application enforcement (bypass retirement) | ✅ | ✅ | ✅ | ❌ 95 of 98 bypassed |
| Trust challenge experience — BFF | ✅ | ✅ | ✅ | ✅ live over HTTP |
| Trust challenge experience — shell | ✅ | ✅ | ❌ | ❌ |
| Trust challenge experience — mobile | ✅ | ✅ | ❌ | ❌ |
| Constrained recovery | ✅ | ✅ | ✅ | ✅ |
| Audit correlation | ✅ | ✅ | ✅ | ⚠️ not reconstruction-tested |
| Header containment (strip/regenerate) | ✅ | ✅ | ✅ | ⚠️ config-verified only |

---

## What this programme actually changed

Most of CP4–CP9 was not building. It was **finding controls that read as present and did
nothing**, and the same defect recurred at every layer:

1. **Fourteen BFF governance checks had always denied.** The client sent `:method`/`:path` —
   unsendable over HTTP/1.1. Every call threw; the fail-closed catch-all made a thrown check
   indistinguishable from a policy decision. *Only a deployed probe found it.*
2. **A PDP outage and a refusal were the same boolean.** Users would be told they lack
   permissions when the policy service was simply down.
3. **Mobile's step-up branch was unreachable on both wires** — it read `decision`; ext_authz emits
   `verdict` and the BFF nests the outcome. Mobile has never prompted for verification.
4. **Sixteen services are open with no bypass flag**, so retiring all 95 declared bypasses would
   be truthfully reported while biometrics, the audit ledger and identity matching stayed open.
5. **NetworkPolicies could not be enforced at all** for 3+ days — unloaded `ip_set_hash_*` kernel
   modules, not the documented cause. 1270 iptables rules referenced sets that never existed.
6. **The consent wire had five simultaneous mismatches** and its fail-closed catch-all hid a call
   that could never have succeeded.
7. **93 of 100 workloads ran code that was not on the branch** — "merged in all 97 services" was
   running in 9.

Every one was a check that could not fail, or a measurement that lied. None was a wrong opinion
about the code.

---

## Gates from the brief, honestly scored

| Gate | Status |
|---|---|
| Zero unexplained legitimate shadow denials | ✅ 0 REAL divergences over 44 compared |
| Unique workload identity per workload | ⚠️ registry + SAs exist; not bound as identity |
| Stable credential refresh | ✅ cohort |
| Known direct routes | ❌ 38 services have unenumerated non-BFF callers |
| Proven headers and policies | ⚠️ config-verified, not runtime-proven |
| Backups and recorded rollback digests | ✅ every wave |
| Captured performance baseline | ❌ not captured |
| No per-hop synchronous Keycloak dependency | ✅ cached provider |
| Zero fail-open decisions | ❌ `tshepoPdpFallbackAllow` still fails open |

Performance gates (OPA p95 ≤10 ms, trust overhead p95 ≤20 ms) were **not measured**. Reporting
them as met on the basis of a shadow deployment would be a fabrication.

---

## Terminal status

# SERVICE TRUST PARTIALLY ENFORCED

One cohort of one service is genuinely enforced end to end (authentication, audience restriction,
network containment, workload identity). The trust *decision* path is real and live: the BFF now
reaches the PDP for the first time and returns canonical challenges over HTTP. The trust
*experience* is built and tested on every platform and deployed on none of the user-facing ones.

**95 of 98 flag-bearing workloads still run with the bypass on, and 16 more were never covered by
the bypass framing at all.** Envoy ext_authz is off, OPA is in shadow, work-context binding is in
shadow, and strict mTLS has no substrate.

This is the status the evidence supports. It is not "Tshepo complete", and no facet above should
be quoted without its layer.

---

## Correction — the authentication facet was wrong when this report was written

This report originally carried one line, `Authentication (Keycloak, PKCE, browser + mobile)`, marked
`PREVIEW_DEPLOYED ✅`. That was true and misleading, and it is the most important error in the
document. People genuinely signed in. Two things they could not do were invisible behind that tick:

**1. Every session carried a null person anchor.** The `impilo` realm was imported from a pre-25
Keycloak export and had **no `basic` client scope**, which is where Keycloak 25 moved the `sub`
claim. So access tokens carried no subject, `jwt.getSubject()` returned null, and every
`/auth/oidc/session` response returned `"user": {"id": null}`. Authentication succeeded and produced
an anonymous anchor.

**2. AAL2 was unreachable, so no provider could ever reach a work intent.** The same export lacked
the **`acr` client scope**, so tokens carried no `acr` claim at all. `OidcSessionService.aalRank()`
scored the absent claim 0, `0 < 2`, and the BFF refused every work-intent callback with
`OIDC_AAL_NOT_SATISFIED` — *after* Keycloak had correctly performed the OTP step-up. **The
authentication succeeded and was discarded at the last hop.** The realm's `AuthnContextClassRef`
scope looks like the equivalent and is not: it is a `saml-authn-context-class-ref-mapper` and emits
nothing for OIDC.

Both were fixed on 2026-08-03 (`4e18a489f`, `d6405084b`), verified against the live realm:

```
scopes:          basic -> sub (oidc-sub-mapper), auth_time (oidc-usersessionmodel-note-mapper)
                 acr   -> acr loa level (oidc-acr-mapper)
attached:        basic 20 clients, acr 19  (delta = bearer-only realm-management, mints no tokens)
realm defaults:  AuthnContextClassRef acr basic email impilo-tenant openid profile
```

```
citizen.moyo  user.id 4a6c7696-46b2-4f90-8194-f0ee1cc1d4dc  acr urn:impilo:aal1  [CITIZEN]
dr.mapfumo    user.id e80a298e-522e-476b-b379-27e58a85dcc2  acr urn:impilo:aal2  [CLINICIAN]
```

The subject is the Keycloak user UUID — opaque, never a national identifier, which is the direction
the PII work wants.

**What this says about the four-layer vocabulary.** `PREVIEW_DEPLOYED` answers "is it running",
not "does it do what its name implies". A facet can be deployed, exercised daily and still be
missing the thing it exists to provide. Both defects were found by capturing a real token and
reading a real session response — neither was visible in source, in tests, or in any status this
report could have computed. **Facet names must be narrow enough to be falsifiable**: the split
above replaces "Authentication" with three claims that can each be individually disproved by one
observation, which is why the row is now three rows.

The realm-defaults step is the one that stops recurrence — without it, 20 clients would have been
fixed and every client created later would have inherited the same silence, including the
per-workload clients CP4.4 provisions and CP8 multiplies.

## Second correction — "reachable" is not "required", and I made the same error twice

Having split the authentication row because `PREVIEW_DEPLOYED` answered "is it running" rather than
"does it do what its name implies", I then wrote **"AAL2 step-up is reachable ✅"** — which is true
and misleading in precisely the way I had just criticised. Reachable is not required.

`OidcSessionService.assertAcr` compares the acr **requested** against the acr **achieved**:

```java
int requestedRank = aalRank(requested);
int actualRank    = aalRank(actual);
if (actualRank < requestedRank) throw new OidcProtocolException("OIDC_AAL_NOT_SATISFIED");
```

and the requested value originates in the browser — `ProgressiveAuthForm.tsx:48`,
`intent === "personal" ? null : "urn:impilo:aal2"`. So the server verifies *you got what you asked
for*; it never verifies *you asked for enough*.

**Consequently a second factor for clinical work is client-elective.** A caller that requests no
acr — a modified shell, a direct API call, any non-browser client — obtains a session with no
second factor, and nothing server-side then refuses it clinical work: Envoy `extAuthz` is off, and
no BFF endpoint gates on assurance. Door-level AAL2 is not merely the *only* 2FA enforcement on the
estate; it is **enforcement the client opts into**.

This is not a regression. It is the honest description of a control that has always worked this way
and that the previous row's wording concealed. It also constrains the enrolment fix below: moving
to progressive step-up before a server-side gate exists would remove the sole 2FA path entirely.

**The pattern, for the eighth time and this one mine:** I corrected an over-claim and introduced a
subtler version of it in the correction. A facet name that cannot be disproved by a single
observation will keep flattering whatever is underneath it, and "reachable" was still such a name.

## Third correction — the bypass counts in this report are stale, and in the estate's favour

Measured 2026-08-03, after the fullboot: **`IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` is no longer
set on any workload.** The boot regenerated the deployments from a values file that does not set
it; the code default is `false`; so the enforcing chain now applies almost everywhere.

Confirmed by probe rather than by reading config — three services that previously returned `404`
(request reached the application) now return `401`:

```
vito-service /v1/patients 401 · rules-service /v1/rules 401 · guidance-service /v1/guidance 401
```

So "95 of 98 workloads bypassed" above is **wrong as of 2026-08-03**. It is left in place rather
than edited, because the number was true when written and the correction is more informative than a
silent overwrite.

**This is the CP8 end-state reached by accident, not by the gated process.** None of the gates this
report scores — caller enumeration, shadow parity, recorded rollback digests, performance baseline —
were run for it. The fallout is already in the log: `a9bbfd220`, *"28 services expected the internal
issuer; every S2S call 401'd 'iss not valid'"*, is exactly the breakage those gates exist to
prevent, found and repaired reactively rather than predicted.

It should not be read as CP8 complete. An unratified posture change that happened to survive is not
the same as a cohort enforced against evidence, and the difference is invisible in any metric this
report computes — which is the report's own recurring lesson, now applied to itself for the third
time.

The 16 services with no security chain are **unaffected**: they carry no flag, so nothing the boot
did could have closed them. They remain the dominant gap.

## Fourth correction — the replacement numbers, and one claim above that is now falsified

Superseded 2026-08-03 by
[`checkpoint-10/CP10_MEASURED_ENFORCEMENT_POSTURE.md`](../checkpoint-10/CP10_MEASURED_ENFORCEMENT_POSTURE.md),
which probed all 102 running Spring services from inside the namespace rather than sampling three:

```
enforcing  91      open  11      (9 of the 11 proven with a 200 and a response body)
```

Two corrections to the paragraph immediately above this one:

- **"The 16 services … remain the dominant gap" is wrong twice.** The set is **11**, not 16 — four
  of the baseline's names (`abis-service`, `indawo-service`, `ndila-service`, `wellness-service`)
  return `401` to unauthenticated business requests and a fifth, `shared-core`, is a library that
  cannot be probed. And they are not the dominant gap: **`hapi-fhir` serves `GET /fhir/Patient`
  unauthenticated**, while its governed front doors `butano-fhir` and `fhir-gateway-service` both
  correctly `401`. `orthanc` answers too. Neither is in `services/`, so no source-level screen
  scoped to that tree could ever have seen them.
- `abis-service` is the one that stings: CP8 headlines it as the *biometric identity* case, and it
  is enforcing. The baseline's argument now rests on `audit-ledger-service` and `matcher-engine`,
  both of which were confirmed open by probe.

The recurring lesson applies to the correction itself: this report's numbers were never measured
per-service, only sampled and generalised. The count was the artefact of the method.

## Recorded doctrine decisions

### TOTP algorithm: HmacSHA256 → HmacSHA1 (PO decision, 2026-08-03, `ccd2ed326`)

The `impilo` realm minted TOTP under `HmacSHA256`. **Google Authenticator ignores the `algorithm`
parameter in the `otpauth://` URI and always computes SHA1**, so against a SHA256 realm it produces
well-formed codes that are *always wrong*, with no error message anyone can act on. A nurse
enrolling with the most common authenticator would be permanently unable to sign in, and the
failure is indistinguishable from mistyping.

I initially ruled **against** the flip, on the grounds that lowering a realm-wide crypto policy so
one app works is the same move as clearing `CONFIGURE_TOTP` — fixing a demo by weakening a control.
That was right about a demo justification and wrong about this: the PO reframed it as an onboarding
capability, not a convenience, and "the most common authenticator silently cannot enrol" is a
different problem from "one app is inconvenient".

**The security cost is small and worth stating precisely, because it is easy to overstate.**
HMAC-SHA1 is not weakened by SHA1's broken collision resistance — HMAC's security rests on PRF
properties, not collision resistance, and no practical attack exists against HMAC-SHA1. It is also
RFC 6238's default and the interoperability baseline. This is a compatibility decision with a
marginal cryptographic cost, not a meaningful downgrade.

**Operational consequence, which recurs every time this is touched:** flipping `otpPolicyAlgorithm`
**invalidates every enrolled factor** — same stored secret, different HMAC, every authenticator
silently wrong. Two personas had to re-enrol. The cost scales with the number of enrolled users, so
it was correct to settle this at 2 enrolments rather than at 38. The warning lives in
`scripts/operator/reconcile-keycloak-realm-users.sh:126` so the next person meets it before acting.

Applied **by the reconciler, not by hand**, so a realm reset replays it — the condition this
programme keeps rediscovering is that a manual fix dies at the next reset.

## Ranked next actions

1. **Write security chains for the 16 open services.** No flag closes them. `abis-service`,
   `audit-ledger-service` and `matcher-engine` first.
2. **Enforce CP8 cohort 2** — 30 candidates are gate-passed and ready; the flip was blocked by an
   environment permission, not by evidence.
3. **Deploy `one-ui-shell`** and capture the Playwright and Redroid proofs (queued for the next
   fullboot).
4. **Rebuild the ~90 stale services**, without which source fixes never become runtime fixes.
5. **Retire `tshepoPdpFallbackAllow`** — the last known fail-open.
6. **Capture the performance baseline** before ext_authz Stage 2.
