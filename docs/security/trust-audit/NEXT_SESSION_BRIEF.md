# Tshepo trust plane — brief for the next session

Written 2026-08-03. Every number below was measured against the live estate on that date, not
recalled. Where I am unsure, it says so.

You are inheriting the whole trust lane. The previous holder (staging/fullboot) has handed it over
and is not touching it. Canonical branch: `claude/staging-ux-orchestration-remediation-Yypyl`.

---

## 1. Read these first, in this order

| Document | Why |
|---|---|
| `docs/security/trust-audit/checkpoint-9/CP9_CONFORMANCE_AND_DEPLOYMENT_TRUTH.md` | Facet truth matrix, terminal status, and **three recorded corrections to my own claims** |
| `docs/security/trust-audit/checkpoint-8/CP8_SENSITIVE_COHORTS.md` | Enforcement posture, cohort selection method |
| `docs/security/trust-audit/checkpoint-6/CP6_TRUST_EXPERIENCE.md` | The challenge experience; what is deployed and what is not |
| `docs/runbooks/shared-tree-concurrency.md` §9, §10, §10a, §10b | How this estate lies to you. Not optional |
| `docs/doctrine/tshepo-trust-plane-doctrine.md` | The doctrine the work serves |

---

## 2. Estate state, measured 2026-08-03

```
deployments            117      all ready
netpol                 1        cohort-1 only
OPA mode               SHADOW
Envoy ext_authz        OFF      (matches in the running config are comments)
Keycloak realm         otp HmacSHA1/6/30; scopes basic + acr present; 51 users, 35 with OTP
master realm users     0        no admin exists; six scripts still password-grant to it
```

### ⚠️ The estate flipped to enforcing, and nobody decided to

CP8/CP9 recorded **95 of 98 workloads running with `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true`**.
That is **no longer true**. The fullboot regenerated the deployments from a values file that no
longer sets the flag; the code default is `false`; so the flag is now simply *absent* almost
everywhere and the enforcing chain applies.

Verified by probe, not by reading config — three services that previously returned `404`
(request reached the application) now return `401`:

```
vito-service     /v1/patients   401
rules-service    /v1/rules      401
guidance-service /v1/guidance   401
```

**This is the CP8 end-state reached by accident rather than through the gated process.** None of
the gates the brief required — caller enumeration, shadow parity, recorded rollback, performance
baseline — were run. The fallout is already in the commit log: `a9bbfd220`, *"28 services expected
the internal issuer; every S2S call 401'd 'iss not valid'"*, is precisely the breakage those gates
existed to prevent, found and repaired reactively.

**Do not treat this as CP8 complete.** Treat it as an unratified change that happened to survive.
The honest first task is to establish what is now genuinely enforced, because the estate's security
posture changed without a decision and CP9's numbers are stale.

---

## 3. The one live hole — start here

**A second factor for clinical work is client-elective.**

`OidcSessionService.validateRequestedAcr` opens `if (requested == null) return;`, and the requested
value originates in the browser at `ui/one-ui-shell/src/components/auth/ProgressiveAuthForm.tsx:48`
(`intent === "personal" ? null : "urn:impilo:aal2"`).

The server verifies **you got what you asked for**. It never verifies **you asked for enough**.

Proven empirically by the previous holder: `dr.mapfumo` signed in requesting no acr, received
`acr=urn:impilo:aal1` as `[CLINICIAN]`, and reached `/work`.

Any caller that omits the acr — a modified shell, a direct API call, any non-browser client —
obtains a session with no second factor, and nothing server-side then refuses it clinical work:
`ext_authz` is off and no BFF endpoint gates on assurance.

**This is the only item on this list that is a security hole rather than an inconvenience.** It is
also the constraint on everything else: moving to progressive step-up before a server-side gate
exists would remove the sole 2FA path on the estate.

---

## 4. Ordered work

1. **Close client-elective AAL2.** Requires a server-side minimum assurance per action, not per
   request. The BFF is the only enforcement point today (ext_authz is off), so it belongs there
   first, with the PDP as the durable home once Stage 2 lands.
2. **Re-establish what is enforced**, now that the flag has vanished estate-wide. Probe, don't read
   config. Then correct CP9's numbers — they are stale in the estate's favour, which is the worst
   direction.
3. **The 16 services with no security chain.** Baseline:
   `docs/security/trust-audit/checkpoint-8/unconditionally-open-services.txt`, frozen by
   `scripts/guard/check-enforcement-posture.sh` in both directions. **See §6 — this list has at
   least one false positive.** They carry no bypass flag, so nothing the fullboot did could have
   closed them; they need chains written.
4. **Six scripts still password-granting to `realms/master`**, which has zero users:
   `bootstrap/bootstrap-auth.sh`, `provision-workload-clients.sh`,
   `provision-preview-test-citizen.sh`, `add-mobile-roles-mapper.sh`,
   `grant-backend-admin-token-scope.sh`, `reconcile-client-secrets.sh`. Ruling stands: **repoint to
   `client_credentials` one at a time with a real run each — never a blanket change.** Each needs
   its own service account checked for the specific rights it uses. `reconcile-keycloak-realm-users.sh`
   is already done (`c8c35ab29`) and is the worked example. **Do not create a master admin** — see §7.
5. **Progressive step-up.** Nothing calls `POST /internal/v1/auth/oidc/step-up`; the BFF endpoint
   and continuation plumbing exist and are tested. Blocked behind item 1.
6. **Provisioning-time enrolment.** 35 personas were enrolled by hand; that sweep expires the
   moment someone is hired. Unowned — flag to the PO.
7. **CP6 browser + Redroid captures.** `one-ui-shell` is built and tested (57 tests) but has never
   been deployed, so no user has seen a trust challenge. Rides the next fullboot.
8. **Smaller:** 3 accounts still pending `CONFIGURE_TOTP` with no seed password; assignment
   `15355e03` sits at `approved` needing an `opaDecisionId` (do **not** synthesise one).

---

## 4a. Authentication on HAPI — the sequence, and why it cannot be one step

**Current state (2026-08-03).** `hapi-fhir` has **no authentication of its own** and cannot easily
be given any: it is stock `hapiproject/hapi:v7.4.0`, and this estate has no Docker Hub egress, so
neither a custom image carrying an `AuthorizationInterceptor` nor a pulled auth-proxy image is a
given. What exists today is *containment only* —
`deploy/networkpolicy/shr-hapi-fhir-ingress.yaml` admits exactly one pod selector,
`fhir-gateway-service`, plus the kubelet probe addresses.

**Containment is not authentication.** The permitted caller reaches HAPI with no credential at all.
If that one policy is deleted, or a workload is relabelled `app: fhir-gateway-service`, the SHR is
open again. The policy is a good control and it is not the control.

### Why enabling auth today would take the SHR down

`GatewayForwardService.forward(...)` is the **only** outbound client of HAPI in the estate, and it
attaches **no `Authorization` header** — there is no `Bearer`, no `setBearerAuth`, no token
plumbing in it at all. Switch HAPI to rejecting unauthenticated requests and the gateway's forwards
begin failing, which is the clinical read/write path.

### The sequence

1. **Make `fhir-gateway-service` present a workload token when forwarding.** `WorkloadTokenProvider`
   in `services/shared-core/.../auth/` exists from CP4.3 for exactly this — cached, audience-restricted,
   fail-closed, with a jittered refresh. This is the prerequisite and it is self-contained: adding a
   header that nothing yet checks changes no behaviour, so it can land and bake on its own.
2. **Put a validating layer in front of HAPI in LOG-ONLY mode**, and confirm from its logs that
   tokens actually arrive on real traffic. Same shadow-then-enforce discipline as the OPA work in
   CP4.5. Do not skip this: step 1 landing green in tests is not evidence that a token reaches HAPI
   on the live path.
3. **Flip to enforcing**, with the prior state recorded for rollback, and verify BOTH directions —
   the gateway still forwards successfully, and a direct unauthenticated request is refused.

Compressed into one move, step 3 fails because step 1 has not happened, and the failure lands on
clinical data.

### Two traps specific to this work

- **NetworkPolicy ports are POD ports, never Service ports.** `hapi-fhir` maps Service `8090` →
  container `8080`. A policy written against 8090 partitions every legitimate caller — and the
  *negative* control still passes, because the non-caller is blocked exactly as intended. **Always
  run positive controls.** This already happened once; it was caught and reverted inside a minute.
- **An env var naming a host is not evidence of a call.** `butano-service` was in the allow-list on
  a `RUNTIME_ENV` name match. Its `HAPI_FHIR_URL` binds to `hapi.fhir.server-address`, used only in
  `HardcodedServerAddressStrategy` — the address HAPI advertises for *itself*. Outbound
  advertisement, not a client target. **Check which direction the URL points before counting a
  caller.**

### Also open, adjacent

- `experience-bff`'s `FHIR_BASE_URL` still points at HAPI. The route is dead — `FhirPublisher` is
  `@Component` but never injected, write-only, and `ServiceEndpoints.fhirBaseUrl()` is never read —
  and it is now network-denied, so it fails closed if anyone wires it up. `FhirPublisher` was left
  in place deliberately; deleting it is a separate decision.
- `butano-service` advertises `hapi-fhir`'s address as its own server address, though they are
  different FHIR servers with different databases. Very likely a config error. Not investigated.
- `orthanc` answers `/patients` unauthenticated (empty at the time of measurement) and has **no**
  NetworkPolicy. Same class as the HAPI finding, unaddressed.

## 5. Constraints still in force

Withheld: production deployment · destructive fullboot · namespace/PVC/database/queue/user/audit
deletion · modifying existing human credentials · global strict-mTLS · broad emergency allowlists ·
fail-open behaviour · unrelated refactoring.

Granted: repository work, tests, builds, commits and pushes, immutable image builds, additive and
targeted preview deployments, staged cohort enforcement after gates pass, bypass retirement only
after every legitimate consumer is migrated and rollback is proven.

Ask only for: production deployment, final merge, destructive action, or a genuinely missing
external credential or governance decision. Do not ask routine permission.

**Never work inside `/opt/impilo/repos/wt-ncz-materialiser`** — it holds preserved untracked
journey reports.

---

## 6. Do not trust these, including mine

- **`audit-enforcement-posture.py` has at least one false positive.** It classifies
  `wellness-service` as `UNCONDITIONAL_OPEN`; a live probe returns `401`. It is a **source-level
  screen, not a runtime measurement** — treat every row as a hypothesis and confirm by probe before
  acting. The 16-service baseline in §4.3 inherits this caveat.
- **CP9's bypass counts are stale** (see §2). CP8's cohort-2 selection is largely moot.
- **The Helm digest pin file was generated 2026-07-27.** A helm-only deploy path, or
  `IMPILO_DEPLOY_NO_DIGEST_PIN=1`, reverts **14 services** including `tshepo-authz-service` and
  `tshepo-consent-service`. A true fullboot regenerates it and is safe.
- **`check-trust-header-strip-pairing.sh` is blind** to the `x-original-*` regression. The real
  guard is `check-visibility-header-strip.sh` (blocking, per-block, all three ext_authz configs);
  `AuthorizeWireTest` is a weaker co-located canary — it counts per file, so a strip *moved*
  between blocks passes while one route leaks.

---

## 7. Estate hazards, each learned the hard way

- **`/home/robert/Impilo-vNext` is a SYMLINK to `/opt/impilo/repos/Impilo-vNext`** — same inode.
  Sessions with different-looking cwds share one tree. **Announce any deliberate breakage, or use a
  private worktree.** A negative control run by you appears to every other session as a real defect;
  this cost two sessions several hours.
- **Never run `kc.sh` inside the live Keycloak pod** — OOM, exit 137. Use an isolated Job with its
  own memory limit.
- **`wget` prints `Username/Password Authentication Failed.` on any 401.** That is *wget's* text,
  not Keycloak's. It was read as a server response and produced a fully fictitious client-secret
  drift investigation. **Use `curl`.** The BFF pod has no curl; the host does.
- **Keycloak theme assets are served under `/resources/<KEYCLOAK-BUILD-hash>/`, and the hash does
  not change when theme files change.** `curl` shows new CSS while the browser renders the old.
  Indistinguishable from a failed deploy.
- **A retry loop against a one-time code locks the account** (5 attempts). Clear via
  attack-detection. Expect to lock whatever you are testing.
- **After any TOTP enrolment, reconcile enrolled-in-Keycloak against secrets-held.** A timed-out run
  left 4 accounts enrolled with lost secrets — locked out of AAL2 with no way back. Nothing else
  catches it.
- **Flipping `otpPolicyAlgorithm` invalidates every enrolled factor** — same secret, different HMAC,
  every authenticator silently wrong. Cost scales with enrolments. Currently `HmacSHA1` by PO
  decision (`ccd2ed326`); the rationale is in CP9 and includes why that is *not* a meaningful
  cryptographic downgrade.
- **Do not clear `CONFIGURE_TOTP` to unblock anyone.** AAL2 genuinely requires a factor; clearing it
  removes the enrolment prompt while the requirement stays, leaving the user with no route to
  satisfy it.
- **`dr.mapfumo` holds both `otp` and recovery codes**, so Keycloak offers the *recovery* challenge
  first. Looks like a defect; is not.
- **Rendered Helm manifests carry `namespace: default`.** `kubectl apply -f` on one creates a stray
  object in the wrong namespace while the real one stays untouched. **The tell is `created` rather
  than `configured`** on an object you know already exists.
- **`~/.m2` holds a stale `tshepo-contracts`.** Always build via the reactor (`-pl X -am`), never a
  module alone, or you compile against a jar with no `contracts/v1/` classes.
- **`mvn -am` prints `No tests to run.` for every dependency module** before the real module runs.
  Reading the first one as the result concluded a working guard was dead.

---

## 8. The pattern — eight instances, and it will find you too

Almost nothing this programme found was a wrong opinion about the code. It was **controls that read
as present and did nothing**:

1. Fourteen BFF governance checks that had always denied — pseudo-headers unsendable over HTTP/1.1
2. Mobile's step-up branch, unreachable on both wires, never once fired
3. A consent client with five simultaneous wire mismatches, hidden by fail-closed
4. NetworkPolicies unenforceable for three days — unloaded kernel modules
5. A change-safety gate that was permanently red, so its RED proved nothing
6. A persona reseeder, written after an incident to prevent recurrence, that could never authenticate
7. No `basic` client scope → **every session carried `user.id: null`**
8. No `acr` client scope → **AAL2 unreachable; every provider work-intent refused after a successful
   step-up**

**Prove every guard in BOTH directions.** A guard proven only RED is indistinguishable from one that
is permanently red. A guard proven only GREEN may never fire.

**Prove a fail-closed check by observing a SUCCESS reach it**, never a denial — a thrown check and a
refusal are the same value.

**Facet names must be narrow enough to be falsifiable by one observation.** I wrote "Authentication
✅ PREVIEW_DEPLOYED" while the anchor was null and AAL2 was unreachable, then corrected it and
immediately wrote "AAL2 is reachable ✅" while it was client-elective. A name broad enough to stay
true will keep flattering whatever sits under it.

**Source and tests were green against every defect on that list.** Each needed a deployed probe, a
real token, or a real response body. Budget for that.
