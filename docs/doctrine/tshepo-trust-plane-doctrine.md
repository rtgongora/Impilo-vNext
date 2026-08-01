# Tshepo Trust-Plane Doctrine

> **Short label**: Trust-Plane Doctrine.

> **Canonical summary**: Tshepo is Impilo's **complete national trust plane** — the whole of
> authentication, identity assurance, authentication assurance, workload identity, active
> context, authority, consent and lawful basis, policy decision, enforcement, recovery, and
> audit and governance, expressed through one unified trust experience. It is **not** a single
> microservice. Keycloak is the *authenticator inside* Tshepo; `tshepo-authz-service` is the
> *decision and orchestration boundary*; OPA is the *target policy evaluator after proven
> parity*; Envoy, gateways, the BFF and applications are *complementary enforcement points*;
> Mvumo is the *governed consent and lawful-basis experience*; the audit ledger *records
> evidence and can never grant authority*.

> **Short doctrine line**: One trust plane, many components; authenticate the human and the
> workload separately; a role is not authority; context is not authority; the network is not
> identity; trust rises with the action and is enforced at every real boundary; audit records,
> it never grants.

This doctrine governs the **runtime trust and enforcement plane**. It is complementary to,
and does not replace, the
[Identity, Access and Trust Governance Doctrine](identity-access-trust-governance.md)
(the *identity / registry / activation* doctrine — who exists, who may act, and how access is
*activated*). Where that doctrine decides **whether** an actor may act, this doctrine decides
**how that decision is authenticated, propagated, evaluated, enforced, recovered and audited
at runtime** across humans, browsers, mobile clients, workloads, gateways, events and
external integrations. It also extends the foundational
[Health OS Doctrine](health-os-doctrine.md) (Access Control §20, Audit §22) and the
[Health Services Gateway Doctrine](health-services-gateway-doctrine.md) (public-first,
progressive trust).

---

## 1. Tshepo Is the Trust Plane, Not a Service

Tshepo is the name of Impilo's **entire trust plane**, spanning eleven capability areas that
together answer *"may this specific request proceed, right now, with this evidence?"*:

1. **Authentication** — proving a human or workload is who it claims to be.
2. **Identity assurance** — how strongly the *identity* has been established (registration,
   proofing, binding). Governed by the Access Governance Doctrine §9 (Identity Trust) and the
   `identity-assurance-service`.
3. **Authentication assurance** — how strongly *this session's authentication event* was
   performed (AAL1/AAL2/AAL3, factor types, recency). Distinct from identity assurance.
4. **Workload identity** — proving which *service* is calling, independently of any human.
5. **Active context** — the operational setting of the action (facility, workspace, ward,
   department, programme, shift). Context is a *precondition*, never a grant.
6. **Authority** — the activated right to perform the action (appointment, licence, mandate,
   delegation), evaluated per the Access Governance Doctrine §10.
7. **Consent / lawful basis** — the lawful reason the data or action is permitted.
8. **Policy decision** — the PDP verdict combining all evidence.
9. **Enforcement** — the PEPs that carry out the verdict at each boundary.
10. **Recovery** — governed re-establishment of trust after loss/compromise, as a *restricted*
    state, never a bypass.
11. **Audit and governance** — tamper-evident evidence of every decision and enforcement.

**No single component is "Tshepo."** Documentation, UI language, and code comments that call
`tshepo-service`, `tshepo-authz-service`, Keycloak, or OPA "Tshepo" (as the whole) are
**incorrect** and must be corrected to name the specific component and its role (§4).

## 2. Foundational Separations (the things that are *not* the same)

These separations are the spine of the trust plane. Collapsing any of them is a defect.

| Not this | … is not the same as … | … this |
|---|---|---|
| Authentication | ≠ | Authorization |
| Identity assurance (how well we know *who you are*) | ≠ | Authentication assurance (how strongly *this login* was performed) |
| A role | ≠ | Authority to perform an action |
| Active work context (where you are) | ≠ | Authority (what you may do there) |
| Selecting/entering a workplace | ≠ | Being granted authority at that workplace |
| Consent (a person's permission) | ≠ | Lawful basis (the legal ground, which may exist without consent) |
| Network position / namespace / internal header | ≠ | Identity |
| The calling **workload** | ≠ | The **human** on whose behalf it may be acting |
| Audit record of an action | ≠ | Authority to perform it |
| Envoy/edge approval | ≠ | Application resource-level authorization |
| Recovery / break-glass / offline | ≠ | A bypass (they are governed trust *states*) |

**The workload and the human actor must never be collapsed into one ambiguous subject.** Every
trust decision carries both, or explicitly records the absence of one.

## 3. Public-First, Progressive Trust

Consistent with the Gateway Doctrine: **help before identity, care before coverage.** Public
information, search, maps, facility and credential verification, emergency help, basic Nompilo
guidance, and feedback remain **public-first** and must not be gated behind authentication.
Trust requirements rise **only at real boundaries** — where an action touches a person's
record, a regulated capability, money, or governed data. Progressive trust **never restarts
the journey**: an interrupted action is preserved and resumed after the required trust step
(§7 of the combined programme; unified trust experience).

## 4. Component Responsibility Matrix

Each component owns a **bounded** role inside the trust plane. None is the whole.

| Component | Owns (role in the trust plane) | Must NOT |
|---|---|---|
| **Keycloak** | The **authenticator**: credential storage, OIDC/OAuth flows, MFA factors (TOTP, passkeys, hardware AAL3), recovery-code issuance, login/admin **event emission**. Issues tokens carrying authentication assurance (ACR/AAL). | Be called "Tshepo"; be the authorization decision point; be exposed to end users as a raw admin surface; hold clinical/registry authority. |
| **`tshepo-authz-service`** | The **decision & orchestration boundary** (PDP front door): assembles `TrustDecisionInput` from validated evidence, returns a standardized `TrustChallenge`, is the single ext_authz decision API, and orchestrates OPA. | Store sovereign domain truth; trust client-supplied identity headers; be bypassed by direct-backend paths. |
| **OPA** | The **target policy evaluator**, run behind `tshepo-authz-service` — first in **shadow**, then enforcing after proven parity. | Be enabled for enforcement before parity is proven; be a second, divergent decision API; be exposed to users. |
| **`tshepo-consent-service`** | System-of-record for **consent and lawful-basis grant/revocation state**. | Own the consent *experience*; be the only lawful-basis authority without a single designated source. |
| **Mvumo** | The **governed consent & lawful-basis experience** (user-facing orchestration), rendered inside the Impilo shell. | Become a second source of truth for grant state; diverge from the canonical consent contract. |
| **Envoy / gateways** | **Edge & east-west PEP**: strip client-supplied trust headers, call `tshepo-authz` ext_authz, regenerate trust headers only from validated evidence, enforce the coarse allow/deny. | Be the *only* authorization control; be bypassed by alternate ingress paths; pass client-supplied identity headers through. |
| **Experience BFF** | **Composition + browser session PEP**: BFF-managed OIDC (authorization-code + PKCE), encrypted server-side sessions, CSRF binding, and per-request trust-context assembly for composed calls. | Become a source of truth for clinical/registry/trust/finance; mint authority; persist browser tokens. |
| **Applications / services** | **Definitive resource-level authorization**: patient-, record-, case-, licence-, facility-, council-level checks; emit audit + decision pairs. | Rely on edge approval alone; trust internal headers without validation; run permanently with auth disabled. |
| **Mobile clients** | Native PKCE with transaction persistence and replay protection; secure token storage; step-up/recovery UX. | Use password (ROPC) grant; store long-lived tokens insecurely; assume authority from context. |
| **Kubernetes** | **Workload identity substrate**: per-service ServiceAccounts, least-privilege, NetworkPolicy **containment** (not authorization). | Be treated as an authorization control; share one `default` SA across the estate; be the reason a backend is reachable without auth. |
| **Audit ledger (`tshepo-audit-service`)** | **Tamper-evident evidence**: hash-chained decision/enforcement records, Keycloak event ingestion, end-to-end correlation. | Grant, imply, or substitute for authority; store raw tokens/secrets/OTPs/clinical payloads it does not need. |

## 5. The Trust Decision Contract (target)

Every protected decision is evaluated from a **versioned `TrustDecisionInput` assembled only
from server-validated evidence** — never from client-supplied identity, service, role,
context, assurance, purpose, consent, or authority headers. Inputs include: human subject
(where present); calling workload and original client; destination service; active session;
identity assurance; authentication assurance; workload assurance; active context; current
authority/appointment/licence/mandate; resource, operation and sensitivity; tenant, facility
and council; consent or lawful basis; purpose of use; delegation chain; device/session risk;
emergency/break-glass state; operating mode; policy version and correlation IDs.

Every decision returns a standardized **`TrustChallenge`** — one of `ALLOW`, `DENY`,
`AUTHENTICATION_REQUIRED`, `STEP_UP_REQUIRED`, `CONTEXT_REQUIRED`, `AUTHORITY_REQUIRED`,
`CONSENT_REQUIRED`, `APPROVAL_REQUIRED`, `BREAK_GLASS_AVAILABLE`, `REAUTHENTICATION_REQUIRED`,
`RECOVERY_REQUIRED`, or `TEMPORARILY_UNAVAILABLE` — carrying **only safe fields** (decision,
reason_code, user_message_key, required_action, required_assurance,
allowed_authentication_methods, context_options, consent_request_reference,
approval_reference, continuation_reference, decision_id, policy_version, expires_at,
support_reference). A denial, an authentication failure, and a temporary infrastructure
failure are **distinct outcomes** and must never be conflated.

Shared, versioned contract types: `AuthenticationAssurance`, `IdentityAssurance`,
`WorkloadSubject`, `DelegatedHumanContext`, `ServiceRequestContext`, `ConsentDecision`,
`LawfulBasisDecision`, `AsyncExecutionReference`, `TrustAuditEvent`. Compatibility adapters for
existing callers are permitted **only with explicit retirement criteria**.

## 6. Enforcement Doctrine (PEPs are layered, never singular)

- **Every request crosses a PEP appropriate to its boundary.** The edge PEP (Envoy/gateway)
  is necessary but **never sufficient**: applications retain **definitive** resource-level
  authorization.
- **Identity is never inferred from the network.** Namespace membership, pod-to-pod
  reachability, and internal headers confer nothing. Header ownership is registered; all
  client-supplied trust headers are stripped and regenerated only from validated evidence;
  JWTs are validated for signature, issuer, audience, expiry, not-before, type, authorized
  party and scopes; wildcard audiences and cross-service tokens are rejected.
- **Workloads authenticate as themselves.** Each service-owned action uses a unique workload
  identity (dedicated K8s ServiceAccount and, where client-credentials remain appropriate, a
  dedicated Keycloak client) with short-lived, audience-restricted, least-privilege
  credentials — **no shared estate-wide service token**.
- **Human delegation is bounded.** Broad human tokens terminate at the authorized boundary;
  onward hops use bounded token exchange or an opaque Tshepo delegation reference that retains
  human identity, workload identity, context, authority, purpose, AAL, audience, expiry and
  delegation depth. **No hop may increase authority.**
- **Asynchronous work carries a reference, not a token.** Workers authenticate with their own
  workload identity and **revalidate** scope, authority, expiry and revocation at execution.
- **Fail closed.** Identity or PDP outages yield `TEMPORARILY_UNAVAILABLE`, never silent
  allow. Recovery, degraded, offline and break-glass are **governed states** with their own
  auditable behavior — none may silently fail open.

## 7. Authority, Context and Consent Convergence

- **Selecting a workplace cannot create authority.** Uploads, imports and administrative
  actions cannot implicitly grant login or business authority.
- **Every protected action revalidates**: selected work context; appointment/employment;
  professional standing/licence; facility/council/tenant scope; delegated authority; and
  suspension/expiry/revocation — per the Access Governance Doctrine §10.
- **One canonical consent/lawful-basis contract** is used by Mvumo, `tshepo-consent-service`,
  `tshepo-authz`, the FHIR gateway, the experience BFF and protected applications. It
  distinguishes explicit consent, direct-care relationship, statutory/regulatory authority,
  public-health authority, emergency/break-glass, and other approved lawful bases. Mvumo owns
  the experience; **one designated source** owns authoritative grant/revocation state.

## 8. Recovery and Operating Modes

Recovery authentication yields a **restricted recovery state**, not ordinary workforce AAL2
authority: sensitive, administrative, regulatory, financial and clinical work stays blocked;
the user must enroll a new approved factor; exposed sessions are terminated or rotated; the
recovery code is single-use and fully audited. Normal, degraded, offline, recovery,
break-glass and read-only-emergency are **explicit governed modes**, each with defined,
audited behavior and none silently failing open.

## 9. Audit Doctrine

Audit **records evidence; it cannot grant authority.** Every protected interaction is
correlated end-to-end (ingress → workload authentication → token exchange/delegation →
context/authority resolution → consent/lawful-basis → PDP decision → Envoy enforcement →
application resource enforcement → event/worker processing → result), recording request_id,
trace_id, decision_id, calling/destination workload, human actor, active context, delegation
reference, purpose, action, resource reference, credential type, assurance, policy version,
enforcement point, result and timestamp. It **never** records raw tokens, passwords, OTPs,
recovery codes, private keys, or unnecessary clinical payloads. Alerts fire on missing
decision/enforcement pairs, repeated spoofing, lateral-movement attempts, failed credential
renewal, and audit-chain discontinuity.

## 10. Truth Discipline

Deployed behavior is **never** inferred from a merged branch. Every running digest is mapped
to its source commit and configuration, and each capability is classified against the
**intended/standard production design** as one of: `ENFORCED`, `ACTIVE_NOT_ENFORCED`,
`SHADOW`, `PARTIAL`, `BYPASSABLE`, `DISCONNECTED`, `DOCUMENTED_ONLY`, `ABSENT`, or
`INSUFFICIENT_EVIDENCE`. The merged MFA implementation
(`f190318e1f6be79208cd2e80de9b5c4cc8f5a7ea`) is the **foundation for authentication
assurance** and **must not be represented as completing Tshepo by itself.** No capability
report may use one broad "Tshepo complete" claim to hide partially enforced capabilities; the
final report declares truth separately for authentication, workload trust, context, authority,
consent, policy evaluation, edge enforcement, application enforcement, recovery, audit and
user experience.

The current source-vs-runtime truth for this doctrine is recorded under
[`docs/security/trust-audit/checkpoint-1/`](../security/trust-audit/checkpoint-1/). Any claim
in this doctrine that a capability is enforced must be backed by that evidence set, refreshed
per checkpoint.

---

## Closing doctrine paragraph

> Tshepo is Impilo's trust plane in full: it authenticates humans and workloads as distinct
> subjects; it keeps identity assurance and authentication assurance apart; it treats roles
> and contexts as inputs to authority, never as authority itself; it refuses to read identity
> from the network; it decides once at a governed boundary and enforces at every real one; it
> makes recovery, offline and break-glass into governed, audited states rather than holes; and
> it records everything without ever letting the record become the grant. Keycloak
> authenticates within it, `tshepo-authz` decides within it, OPA will evaluate within it,
> Mvumo obtains lawful basis within it, Envoy and the applications enforce within it, and the
> audit ledger remembers within it — but none of them, alone, is Tshepo.
