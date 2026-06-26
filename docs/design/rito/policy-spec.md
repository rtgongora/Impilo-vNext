# Rito — Trust Policy Specification (SPEC, queued for the CZO single-writer)

> **Status: SPEC — not yet applied.** `PolicyEngine.java` / OPA rego is single-writer-locked to
> the CZO (Consent/Trust Plane) cluster. This document is the authored `rito.*` role + check
> specification **queued** for that lead to merge into `impilo.authz`. Rito does **not** edit
> `PolicyEngine.java` or the rego bundle. Until merged, the rito-quality-safety-service enforces
> the trust *context* (tenant + actor headers via the companion `TrustContextFilter`, mandatory
> v1.1 trust headers, Idempotency-Key on writes) and the **AI-never-auto-closes** invariant in
> the service layer; fine-grained RBAC/ABAC below is the target end-state once the rego lands.

## 1. Roles (`rito.*`)

| Role | Who | Purpose |
|---|---|---|
| `rito.client.submitter` | citizen / caregiver | open a complaint/compliment/suggestion/safety concern; submit survey responses |
| `rito.client.viewer` | citizen / caregiver | track **their own** case + timeline (own-subject only) |
| `rito.agent.khulumaintake` | Khuluma / call-centre agent | open & annotate cases on a client's behalf from a conversation |
| `rito.provider.reporter` | provider (Work mode) | report a clinical-service safety incident / near-miss / quality finding |
| `rito.provider.action_owner` | provider | own & progress a corrective action / QI task assigned to them |
| `rito.facility.quality_focal` | facility quality focal point | triage/assign/investigate facility cases; run audits; own CAPA/QI |
| `rito.facility.manager` | facility manager | all quality_focal rights + close cases, configure facility SLAs |
| `rito.facility.safety_lead` | facility safety lead | view + manage SAFETY_INCIDENT/ADVERSE_EVENT/SAFEGUARDING cases incl. protected identity |
| `rito.supervisor.district` | district supervisor | above-site district view, escalate, supervisory audits |
| `rito.supervisor.province` | province supervisor | above-site province view |
| `rito.supervisor.national` | national supervisor | national rollups, national sentinel oversight |
| `rito.regulator.viewer` | regulator / council (per policy) | read regulated categories where statute permits |
| `rito.regulator.investigator` | regulator investigator | investigate provider-conduct / statutory categories |
| `rito.admin.config` | quality administrator | manage audit tools, surveys, SLA policies, escalation rules |
| `rito.analytics.viewer` | analyst | read dashboards/aggregates (no client/provider PII) |
| `rito.system.signal_writer` | system / rules / AI-assist | write quality signals only; may suggest severity/routing; **no triage/assign/close** |

## 2. Checks (action → required capability)

| Check key | Action | Allowed roles (summary) |
|---|---|---|
| `rito.case.create` | open a case | client.submitter, agent.khulumaintake, provider.reporter, facility.*, supervisor.*, system.signal_writer |
| `rito.case.create_anonymous` | open anonymous (no reporter identity stored) | client.submitter, agent.khulumaintake |
| `rito.case.link_client` | attach a HEALTH_ID/CLIENT link | agent.khulumaintake, facility.*, supervisor.* |
| `rito.case.view` | read a case | facility.*, supervisor.*, admin.config; client.viewer **own-subject only** |
| `rito.case.view_client_identity` | see client identity on a sensitive case | facility.safety_lead, facility.manager, supervisor.*, regulator.investigator |
| `rito.case.view_provider_identity` | see provider identity on a conduct case | facility.manager, supervisor.*, regulator.investigator |
| `rito.case.triage` | set severity/category, status→TRIAGED | facility.quality_focal, facility.manager, facility.safety_lead, supervisor.* |
| `rito.case.assign` | assign owner | facility.quality_focal, facility.manager, supervisor.* |
| `rito.case.escalate` | escalate level | facility.*, supervisor.* |
| `rito.case.close` | resolve/close | facility.manager, supervisor.*; **never** system.signal_writer |
| `rito.case.reopen` | reopen | facility.manager, supervisor.* |
| `rito.dashboards.view` | dashboards | facility.*, supervisor.*, analytics.viewer |
| `rito.safety.view_incidents` | view SAFETY_INCIDENT/ADVERSE_EVENT | facility.safety_lead, facility.manager, supervisor.* |
| `rito.safety.safeguarding` | view/manage SAFEGUARDING/PRIVACY_CONFIDENTIALITY | facility.safety_lead, supervisor.*, regulator.investigator |
| `rito.config.configure` | tools/surveys/SLA/escalation | admin.config, facility.manager (facility-scoped) |
| `rito.data.export` | export case data | supervisor.national, admin.config (audited) |
| `rito.signal.write` | write quality signal | system.signal_writer, facility.*, supervisor.* |

## 3. Sensitive-category identity protection (ABAC overlay)

For cases whose `case_type ∈ {SAFETY_INCIDENT, ADVERSE_EVENT}` or `category ∈
{SAFEGUARDING, PRIVACY_CONFIDENTIALITY, PROVIDER_CONDUCT, DISCRIMINATION_DIGNITY, BILLING_DISPUTE}`
(`rit_case.sensitive = true`):

1. `subject_health_id`, reporter identity, and any `rit_case_party` where `identity_protected = true`
   are **redacted** from responses unless the caller holds `rito.case.view_client_identity` /
   `rito.case.view_provider_identity` as applicable.
2. PROVIDER_CONDUCT and statutory categories are visible to `rito.regulator.*` **only** where the
   purpose-of-use + legal basis (10-dimension access decision) permits.
3. Anonymous cases never expose reporter identity to anyone, regardless of role.
4. Every privileged identity reveal emits a `rito` audit event (decision + reason) for the Tshepo
   audit chain.

## 4. Hard service-layer invariants (enforced now, independent of rego)

- **AI/system actors (`X-Actor-Type ∈ {SYSTEM, AI, RULES, BOT}`) may suggest severity/routing but
  may NOT resolve or close a CRITICAL or sensitive case** — enforced in `CaseService.guardHumanDecision`.
- All write endpoints require the v1.1 trust headers + `Idempotency-Key` (companion filter).
- Tenant isolation on every query (`findByIdAndTenantId` / `tenantId`-scoped finders).

## 5. Draft rego skeleton (for the CZO lead — NOT applied here)

```rego
package impilo.authz

# rito.* capability mapping — illustrative; CZO lead reconciles with the canonical
# role catalogue + 10-dimension access decision before merge.
rito_allow[decision] {
    input.resource.service == "rito"
    some role in input.subject.roles
    cap := rito_role_caps[role][_]
    cap == input.action            # e.g. "rito.case.triage"
    not rito_sensitive_block       # ABAC overlay (section 3)
    decision := {"allow": true, "reason": sprintf("rito cap %v via %v", [cap, role])}
}

rito_sensitive_block {
    input.resource.sensitive == true
    input.action == "rito.case.view_client_identity"
    not has_cap(input.subject.roles, "rito.case.view_client_identity")
}
```

## 6. Queue / hand-off

- **Owner to action:** CZO/Trust-plane single-writer of `PolicyEngine.java` + `impilo.authz` rego.
- **Coordination memory:** `czo-parallel-coordination`, `rito-patientsafety-coordination`.
- **Boundary:** patient-safety-service queues its own MCAZ/regulator PV roles separately; no overlap
  with `rito.*` role names.
