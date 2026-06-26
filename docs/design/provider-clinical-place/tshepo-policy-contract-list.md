# Tshepo Policy Contract List (SPEC ONLY — T1/T3/T4)

> **Status:** DESIGN GATE — policy **specification**, not authored policy.
> Branch `intake/provider-clinical-place-design`.
>
> ## ⛔ Single-writer lock (read first)
> `PolicyEngine.java`, `ExtAuthzGrpcService.java`, `AuthorizeController.java` and the OPA rego are
> **single-writer-locked to the live CZO session cluster** (lead `task_430f1240` + WS-OPA `task_c377bcd0` +
> WS-DELEG-BE `task_80565828` + WS-DELEG-UI `task_558c48f9`). **This design gate authors NO policy.** It
> produces the SPEC below. Implementation routes each rule **as rego into the new `impilo.authz` package via
> the WS-OPA workstream**, or **queues it for the CZO lead**. See memory `czo-parallel-coordination` +
> `provider-clinical-place-batch-coordination`.
>
> The 7 orphaned rego modules already in `infra/opa/impilo/` (hsc, marketplace, organisation, registry, tabs,
> vashandi, work) are the modules `impilo.authz` will import; **no `infra/opa/impilo/authz/` exists yet** — it
> is WS-OPA's to create. Every new rule needs a `*_test.rego` and SHADOW divergence ≈ 0 before any ENFORCE.

## How to read this list

Each rule has: **ID · trigger · OPA `input` it reads · decision · obligations · routing**. The `input`/`data`
shape is the **frozen CZO OPA contract** (see `czo-parallel-coordination`): `actor{id,type,providerStatus}`,
`roles`, `loa`, `purpose`, `resource{type,id}`, `action`, `scope{tenant,facility,workspace,subjectId}`,
`consentVerdict`, `delegation`, `riskScore`, `breakGlassActive`, `now`. OPA returns
`{allow, deny_reasons, require_step_up, obligations_hint, policy_version}`.

**Routing column:** `WS-OPA` = author as rego in `impilo.authz` now (no PolicyEngine edit). `CZO-LEAD` = queue
for the lead (needs PolicyEngine integration / consent / delegation wiring). `EXISTS` = rule already
represented (verify, don't re-author).

---

## A. Provider login & identity resolution (T1)

| ID | Trigger | Reads (input) | Decision | Obligations | Routing |
|----|---------|---------------|----------|-------------|---------|
| `LOGIN-PERSON-FIRST` | login attempt | actor.type, identifier kind | allow only person-anchored (Health ID / email / phone) authentication | — | WS-OPA |
| `LOGIN-PROVIDERID-DENY` | login with Provider ID / council number as credential | identifier kind = PROVIDER_ID/COUNCIL_NUMBER | **DENY** (Provider ID never authenticates) | audit attempt | WS-OPA |
| `LOGIN-ANTI-ENUM` | failed resolution | input.kind | uniform deny shape; no existence disclosure | constant-time hint | WS-OPA (+ BFF timing) |
| `IDRES-SILENT-CHAIN` | identifier resolution | actor, resource=person | allow silent chain Tshepo→Varapi→Vashandi→VITO→TUSO for resolution purpose only | purpose=RESOLUTION | WS-OPA |
| `IDRES-INVITE` | invite-code resolution | invite token | allow bind invite→person | one-time, expiry | WS-OPA |

## B. Work / My-Professional / My-Life separation (T1 — context isolation, absolute)

| ID | Trigger | Reads | Decision | Obligations | Routing |
|----|---------|-------|----------|-------------|---------|
| `WORK-PRO-LIFE-ISOLATION` | access in a shell mode | actor, purpose, resource owner | work perms NEVER grant access to the actor's **own** citizen record; citizen identity NEVER grants clinical work | — | CZO-LEAD (cross-cutting) |
| `LIFE-SELF-ONLY` | My-Life access | actor.id == resource.subjectId | allow self personal record; deny others | purpose=SELF | WS-OPA |
| `PRO-PROFILE-SELF` | My-Professional access | actor provider profile | allow own professional profile/CPD; deny clinical-on-patients | — | WS-OPA |
| `WORK-REQUIRES-ASSIGNMENT` | Work shell action | scope.assignment active + checkIn | deny work actions without active Vashandi assignment + (where required) check-in | step-up if stale | WS-OPA |
| `SELF-TREATMENT-BLOCK` | provider opens own/family clinical record as clinician | actor.id vs subjectId, relationship | deny / require break-glass + audit | break-glass path | CZO-LEAD |

## C. Facility mode, check-in, context selection (T1 enter / T4 build)

| ID | Trigger | Reads | Decision | Obligations | Routing |
|----|---------|-------|----------|-------------|---------|
| `FACILITY-MODE-ENTER` | flip ShellMode→facility_mode | actor, scope.facility, role | allow if active assignment at facility ∧ facility-admin/PIC role | — | WS-OPA |
| `FACILITY-SETUP` | run setup wizard | role, scope.facility, tenant | allow only facility-admin/org-admin for that tenant | audit each step | WS-OPA |
| `CHECKIN-SCOPE` | provider check-in | assignment, facility, shift | allow check-in only at assigned facility | — | WS-OPA (vashandi.rego exists) |
| `CONTEXT-SELECT` | pick WHERE/WHAT (facility/dept/ward/service-point/virtual-pool/above-site × role/workspace) | activeAssignments, requested scope | allow only scopes ⊆ active assignments | — | WS-OPA |
| `WORKSPACE-ENTER` | activate workspace | scope.workspace, assignment | allow if workspace ∈ facility ∧ role permits | — | WS-OPA (work.rego exists) |
| `ENCOUNTER-ENTER` | open Encounter Cockpit | actor, resource=encounter, scope | allow if assigned + cadre permits context | consent check | CZO-LEAD (consent) |

## D. Cadre-specific clinical actions (T3)

| ID | Trigger | Reads | Decision | Obligations | Routing |
|----|---------|-------|----------|-------------|---------|
| `CADRE-ACTION` | any permitted-workflow action | actor.cadre, visitType, acuity, context, accessState | allow action iff cadre+scope+context permits (Cadre Engine asks Tshepo per gated action) | audit | WS-OPA |
| `ORDER-CREATE` | place OROS order | cadre, order type, facility capability | allow order types within cadre scope; deny out-of-scope | step-up for high-risk | WS-OPA |
| `PRESCRIBE` | medication order | cadre, licence, controlled-flag | allow per licence; MAXIMUM friction for controlled | step-up + co-sign | CZO-LEAD |
| `TRIAGE-RECORD` | record triage/acuity | cadre (nurse+) | allow triage cadres | — | WS-OPA |
| `ADMIT` / `DISCHARGE` | admission / discharge | cadre, role | allow admitting/discharging cadres; billing-block override needs senior | override audit | WS-OPA |
| `CARE-PLAN-WRITE` | care plan / problems | cadre | allow clinical cadres | — | WS-OPA |
| `REFER` / `CONSULT` | referral / teleconsult package | cadre, target | allow; consent-gated for data sharing | consent | CZO-LEAD |
| `HANDOVER` | shift handover | cadre, scope | allow between assigned cadres in unit | — | WS-OPA |

## E. Communication (T3 — Khuluma is consume-only)

| ID | Trigger | Reads | Decision | Obligations | Routing |
|----|---------|-------|----------|-------------|---------|
| `COMMS-PATIENT` | send patient message | purpose, consent, comms-prefs | allow plain-language patient message iff consent + prefs allow | no diagnosis-by-auto-msg | CZO-LEAD (consent) |
| `COMMS-PROVIDER` | provider↔provider secure msg | actor, scope | allow within care relationship | — | WS-OPA |

## F. Facility / Org / Regulator / Indawo modes (T4)

| ID | Trigger | Reads | Decision | Obligations | Routing |
|----|---------|-------|----------|-------------|---------|
| `ORG-ADMIN` | org onboarding/tenancy | role, tenant | allow org-admin within tenant only | — | WS-OPA (organisation.rego exists) |
| `FACILITY-REGISTER` | facility/place registration | role, regulator relationship | allow registrar role; **no hardcoded single regulator** — evaluate council relationship | — | WS-OPA |
| `REGULATOR-MODE` | regulator/oversight access | role, council relationship, tenant | allow regulator role scoped to its regulated entities | aggregate-only off-scope | WS-OPA |
| `INDAWO-MODE` | public-health place mode | role, site scope | allow surveillance/inspection roles; field-team scope | purpose=PUBLIC_HEALTH | WS-OPA |
| `INSPECTION` | inspection/enforcement action | role, site | allow inspector cadre | audit | WS-OPA |

## G. Cross-tenant visibility, break-glass, step-up (cross-cutting)

| ID | Trigger | Reads | Decision | Obligations | Routing |
|----|---------|-------|----------|-------------|---------|
| `XTENANT-ISOLATION` | any read crossing tenant | scope.tenant vs resource.tenant | deny row-level; allow aggregate-only where permitted | `AggregateVisibilityGuard` | WS-OPA |
| `XTENANT-AGGREGATE` | scoped dashboard | role, tenant, visibility | allow aggregate metrics; strip PII | aggregate-only | WS-OPA |
| `BREAK-GLASS` | emergency override of access | breakGlassActive, purpose=EMERGENCY | allow widened access; full audit + later review | mandatory audit + notify | CZO-LEAD |
| `EMERGENCY-CARE-NEVER-BLOCKED` | acuity-1 / life threat | purpose=EMERGENCY, accessState | **never deny on identity/payment**; force EMERGENCY_OVERRIDE | deferred reconcile flag | CZO-LEAD (cross-cutting, Law 1) |
| `STEP-UP` | sensitive action | riskScore, sensitivity, loa | require step-up challenge (StepUp endpoint exists) | challenge id | EXISTS (`StepUpController`) + WS-OPA rule |
| `DELEGATION-ACT-FOR` | acting for another (guardian/proxy) | delegation{} | allow iff active ∧ scope ⊆ ∧ loa ≥ min | acting-for banner | CZO-LEAD (WS-DELEG owns) |

## Routing summary & sequencing

| Routing | Count | What the implementer does |
|---------|-------|---------------------------|
| **WS-OPA** | majority | author as rego in `impilo.authz`, importing existing modules; add `*_test.rego`; `opa test` green; SHADOW only |
| **CZO-LEAD** | consent/delegation/break-glass/self-treatment/emergency cross-cutting | queue for lead; needs PolicyEngine Step 4.5 / consent verdict / delegation resolve |
| **EXISTS** | step-up transport | verify `StepUpController` covers it; add the rego rule only |

**Hard rules for the implementer (round 2):**
1. **Never edit** `PolicyEngine.java` / `ExtAuthzGrpcService.java` / `AuthorizeController.java`.
2. New rego lands in `impilo.authz` (WS-OPA) with tests; **SHADOW** mode first; ENFORCE only after divergence≈0.
3. **OPA-down ⇒ Java DENY; Envoy stays fail-closed.** Never add an auth off-switch.
4. Law 1 (`EMERGENCY-CARE-NEVER-BLOCKED`) and provider/citizen isolation (`WORK-PRO-LIFE-ISOLATION`) are the
   two rules whose failure modes are unacceptable — they get adversarial review before ENFORCE.
5. The identity/login lane **sequences after / coordinates with** the CZO auth work (it shares the
   login/identity contract surface) — do not race it.
