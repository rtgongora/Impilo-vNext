# Impilo vNext — Hybrid / Federated Target Architecture

**Status:** Controlling architecture · **Version:** 1.1 · **Date:** 2026-08-03
**Factual basis:** [`vnext-current-state-recovery-2026-08-03.md`](vnext-current-state-recovery-2026-08-03.md) (commit `1870cf33d`), plus targeted evidence sweeps for the v1.1 additions. Every current-state statement is a reference, not a re-derivation.
**Scope:** Converts vNext from a single-instance national deployment into a hub-and-spoke federated national platform, consumed through four service profiles. Immediate delivery target is the **large Hospital Node**. This document does not implement; it governs implementation.

## Statement classification

Every substantive statement in this document carries one of four marks. Where a section is uniformly one class, the mark is given once at its head.

| Mark | Meaning | Changes by |
|---|---|---|
| **[D]** Doctrine | A binding rule of the platform | PO ruling recorded as an ADR |
| **[T]** Technical design | An engineering decision that implements doctrine | A new ADR |
| **[O]** Operating-model decision | A commercial, service or governance choice | Service owner + PO |
| **[L]** Awaiting legal/governance determination | Modelled to support the options; not yet decided | The named authority in §26.2 |

## What changed in v1.1

v1.0 settled how the federation works. It did not govern how organisations *consume* it, how people *experience* it, or how the personal, professional, organisational, facility and platform domains stay separate. v1.1 closes those gaps and corrects one conflation.

| Area | v1.0 | v1.1 |
|---|---|---|
| Consumption | National Core / Hospital Node / Facility Edge only | **§2A** three independent dimensions; **§2B** four consumption profiles + Edge, with a full responsibility matrix |
| Hosting vs authority | Prose treated "Hospital Node" as meaning on-premises | **Corrected throughout.** A node profile describes function and authority; a Hospital Node may run on institution premises, MoHCC infrastructure, the NDC, ZCHPC, an approved sovereign cloud, or a DR site |
| Responsibility | Implicit | **§3A** versioned `service_responsibility_profile` + `service_agreement`; controllership never inferred from hosting |
| Rights domains | Institutional boundaries only | **§4A** five rights and business domains with prohibited inheritances |
| Sessions | One work-context token | **§4B** seven session types, audience-bound, non-exchangeable across domains |
| Experience | Stopped at work-context selection | **§11A/11B/11C** national and node login experiences, Professional Status, eight transition sequences |
| Endpoint routing | Node endpoint + scoped failover | **§16A** full experience routing; `clinical_write_authority` as a rule stronger than failover |
| Sizing | One large node | **§17A** five profiles with indicative volumetric bands |
| Commissioning | Node bootstrap only | **§22A** joined to the *shipped* organisational rails; **§22B** Bootstrap Agent |
| Records | Facility record + two Butano projections | **§19A** PHR with provenance classes; **§19B** Shared-Care Cache |
| MoHCC domains | Single `MOHCC-ZW`, flagged unresolved | **§6A** three options evaluated; hierarchical recommended, all three supportable |

Everything else in v1.0 is preserved: hub-and-spoke federation, National Core as governed hub, local-primary Hospital Nodes, no database replication, no raw cross-site Kafka, no uncontrolled peer exchange, one codebase and one signed artefact set, the six-identifier split, Tshepo local enforcement, seven-day autonomy, origin-node clinical authority, National Butano as projection, runtime endpoint discovery, institutional IdP and key custody, non-MoHCC contribution restricted by default, and the Phase 0 safety corrections with their evidence-based gates.

---

# 1. Target-state architecture doctrine

## 1.1 The six doctrine lines

> **Federation line.** One national hub, many governed spokes; nodes federate through signed contracts, never through shared databases or raw brokers.

> **Authority line.** The node that creates a clinical fact is authoritative for that fact; the National Core holds projections, and a projection never overwrites its source.

> **Boundary line.** A trust domain is a data controller, an organisation is an operator, a facility is a place, a node is a machine. Four different things, four different identifiers, never interchangeable.

> **Continuity line.** Local care never waits on the national platform. Seven days of autonomy is a functional requirement of the Hospital Node, not a degraded mode.

> **Enforcement line.** Trust decisions move to the node with the data. A disconnected node enforces the same rules from signed bundles — it never relaxes consent or sensitivity because the hub is unreachable.

> **Single-product line.** One codebase, many profiles. A Hospital Node is a deployment configuration of vNext, never a fork of it.

## 1.2 The eight architectural inversions

The recovery established what vNext is. The target state is defined by eight specific inversions of it. Everything in this document serves one of these.

| # | From (current, per recovery) | To (target) | Why it is the crux |
|---|---|---|---|
| **I1** | Tenancy is a browser-minted `X-Tenant-ID` header with a hardcoded default UUID, and the token's `tenant_id` claim is the hardcoded literal `moh-zw` for every user | `trust_domain_id` is derived server-side from a verified issuer + subject binding, never accepted from a client, and carried in a signed work-context token | Without this, no institutional boundary can be enforced, so non-MoHCC federation is legally impossible |
| **I2** | The PDP is deployed and disengaged: `ext_authz` templated out, work-context/tenancy/OPA/lawful-basis all SHADOW; consent is enforced in exactly one place the edge never reaches | Enforcement is **on** at every node, PDP-local, bundle-fed; consent evaluation sits on the clinical read path itself, not only in the gateway | A federated platform that ships enforcement in shadow mode exports its defects to every institution |
| **I3** | No node concept in the domain model; `pod_id` is the constant `national-spine`; no provenance, no origin, no conflict model | Every clinical fact carries origin node, origin record, version and a signature; amendments supersede, never overwrite | Federation without provenance is data laundering |
| **I4** | Clinical repositories scope by tenant + patient, not facility or organisation; the PDP's `facility_scope` means "a facility id is present" | Facility and organisation scoping are query-level predicates and PDP-level membership assertions | One organisation can currently read another's clinical rows; this is the top pre-federation blocker |
| **I5** | Four FHIR-shaped stores; the BFF and gateway write to a stock ungoverned HAPI while IPS/timeline read the governed one | One governed FHIR implementation (`butano-service`), instantiated as a **local projection** at the node and a **longitudinal projection** nationally | Split-brain cannot be federated; it must be resolved before it is replicated |
| **I6** | Single node, one Postgres (124 DBs), one Kafka (RF=1), Redis with no volume, loopback registry, host-managed TLS, `Recreate` everywhere, 74 services with `@Scheduled` and zero distributed locking | Three-node reference cluster, HA data plane, real registry, cluster-managed TLS, rolling updates, leader-elected schedulers | The current envelope cannot survive a hospital's uptime expectations, let alone seven-day autonomy |
| **I7** | Endpoints baked into images at build time (Next rewrites, `NEXT_PUBLIC_*`, `EXPO_PUBLIC_*`); mobile production builds refuse LAN endpoints | Runtime endpoint discovery from a signed node configuration document, with QR enrolment and a governed failover policy | A hospital cannot be asked to rebuild the national mobile app to reach its own node |
| **I8** | Integration endpoints are global environment variables (one PACS, one SMS sender, one printer URI, no analyser config) | A versioned, audited configuration hierarchy: trust domain → organisation → node → facility → department → service point | A hospital's lab, PACS and till are its own; global env is not a configuration model |

## 1.3 Non-negotiable invariants

These are testable statements. The acceptance plan in §19 proves each one.

1. **No client-supplied authority.** No `X-Tenant-ID`, `X-Facility-ID`, `X-Actor-ID`, `X-Provider-ID`, `X-Purpose-Of-Use` or assurance header originating from a browser or handset is ever load-bearing in a decision. All are derived from a verified token or a signed work-context token, or the request is refused.
2. **No silent overwrite across nodes.** A federated write that would replace a record whose `origin_node_id` differs from the writer is rejected, not merged. Cross-node last-write-wins is prohibited by contract and by database constraint.
3. **Offline never weakens policy.** When a signed bundle expires beyond its permitted staleness, the affected decision class **fails closed**, except for the explicitly enumerated emergency classes in §4.7, which proceed with elevated, non-repudiable audit.
4. **National administration is not clinical access.** A National Core platform administrator role grants no clinical read inside any trust domain. Cross-domain clinical access requires a federation agreement, a consent or legal basis, and produces a disclosure record visible to the institution.
5. **Local care has no synchronous national dependency.** No routine clinical action in the seven-day autonomy set makes a blocking call to the National Core. Verified by the disconnection test, not by inspection.
6. **One artefact set.** A Hospital Node runs the same signed images as the National Core, selected by profile. Any node-only code path is a defect.
7. **Honest failure is preserved.** The recovery's honest degradations (fail-closed consent, `pending_backend` eligibility, the 502 on national KPIs, the "do not treat as absence of allergies" body) are retained as-is. The false-success paths it named are retired, not federated.

## 1.4 The v1.1 doctrine lines **[D]**

> **Dimensions line.** Federation, hybrid operation and service consumption are three independent dimensions. Answering one never answers another.

> **Hosting line.** Deployment location does not determine authority, ownership or access. Whoever runs the servers does not thereby administer the applications or inspect the records.

> **Domain line.** Personal, professional, organisational, facility and national-platform domains remain technically separate in every consumption profile — not separated by menu labels, but by audience-bound sessions, server-derived context and explicit cross-domain APIs.

> **Primacy line.** Hosted facilities are central-primary. Hospital Nodes are local-primary for operational care and continuously federated. A node never writes centrally in normal operation and then switches to local after an outage.

> **Personal-domain line.** My Life is an individual national experience and is never rendered inside an employer-operated node. A node may show work-relevant Professional Status; the full professional domain stays individual-facing.

> **Record line.** Facility Operational Records, the National Shared Health Record, the Personal Health Record and Shared-Care Caches are four distinct products with four distinct authorities. A cached projection is never authoritative.

## 1.5 What is explicitly out of scope

- Peer-to-peer node exchange without a national federation route.
- Database replication of any kind between sites.
- A second vNext codebase, a hospital fork, a hospital "edition", or a node-only service.
- An employer-owned copy of My Life, or the full professional domain under facility administration.
- Facility Edge as a first-wave deliverable — profiled in §17A, sequenced after the first Hospital Node pilot.

---

# 2. The named products

| Product | What it is | Ships as | First delivery |
|---|---|---|---|
| **Impilo National Core** | The federation hub and the national systems of record for identity, provider standing, facility registry, terminology, policy, national longitudinal record and national reporting | The existing estate, re-profiled: `values-national-core.yaml` | Phase 2 (re-profile of the current estate) |
| **Impilo Hospital Node** | A self-sufficient clinical instance for one institution and one or more facilities, authoritative for the care it delivers. **A profile, not a location** (§2A) | `values-hospital-node.yaml` + node config package | **Phase 2 — the immediate target** |
| **Impilo Federation Gateway** | The only sanctioned cross-site path. A Spring Boot service deployed at both ends; signed envelopes over mTLS, durable queues both sides | New service `services/federation-gateway` | Phase 3 |
| **Tshepo Local Enforcement Node** | The node-local trust plane: Envoy PEP + local PDP + local OPA + bundle agent + local audit chain | Composition of existing `tshepo-*` services + new `bundle-agent` sidecar | Phase 2 |
| **Impilo Fleet & Release Service** | National registry of nodes, releases, certificates, capabilities, health and upgrade rings | New service `services/fleet-service` (National Core only) | Phase 2 (registry) → Phase 5 (rings) |
| **Impilo Node Bootstrap Agent** | The signed, transient installer and enrolment agent for on-premises and sovereign deployments (§22B) | Small signed binary + offline bundle variant | Phase 2.5 |
| **Impilo Facility Edge** | A reduced profile for small facilities: local registration, OPD, dispensing, offline capture; no local inpatient/theatre/lab estate | `values-facility-edge.yaml` | Post-pilot |

**Product relationship rule.** National Core and Hospital Node are *profiles of the same chart and the same images*. Federation Gateway, Fleet Service and the Bootstrap Agent are new code. Tshepo Local Enforcement is a *packaging* of existing services plus one new agent. Nothing else is new.

**Consumption is orthogonal to all of the above.** Each product may be consumed under any of the four service profiles in §2B — which is the distinction v1.0 did not draw.

---

# 2A. Three independent dimensions **[D]**

The single most common error in discussing this platform is answering one dimension with another — "we're going on-premises, so we control the data", or "we're hosted, so MoHCC administers us". Both are false.

| Dimension | Question it answers | Values | What it does **not** determine |
|---|---|---|---|
| **Federation** | Whose authority is this, and what may be exchanged with whom? | trust domain, agreements, sharing policy, disclosure basis | Where anything runs |
| **Hybrid operation** | Which capabilities execute locally and which nationally? | central-primary · local-primary · edge-assisted | Who owns the data or operates the platform |
| **Service consumption** | Who supplies and operates the infrastructure, platform and applications? | shared hosted · dedicated hosted · managed on-prem · sovereign on-prem | Data controllership, clinical governance, or access rights |

**The corrected statement, replacing v1.0's prose.** *A Hospital Node is a functional and authority profile, not a location.* The same node profile may be hosted:

- on the institution's own premises;
- inside MoHCC infrastructure;
- at the National Data Centre;
- at ZCHPC;
- in an approved sovereign cloud;
- in a dedicated institutional data centre;
- at a disaster-recovery site operated by a third accredited provider.

In all seven cases it is the same artefact set, the same profile, the same federation obligations — and, critically, **the same data controller**, which is determined by the trust domain and the service agreement, never by the rack.

Three worked consequences:
1. **MoHCC may host a private hospital's dedicated node** and hold no ordinary clinical access to it (§2B, §6).
2. **A hospital may own the hardware while Impilo operates the platform** and still be the data controller and clinical-governance authority (§3A).
3. **A DR node may be operated by a different infrastructure provider from production** without changing the controller — which is why responsibility is versioned per node, not per organisation (§3A).

---

# 2B. Service consumption and responsibility model **[O]**

## 2B.1 The five profiles

### A. National Shared Hosted
Impilo/MoHCC supplies infrastructure, platform and applications, plus operations, updates, monitoring, backup and the security baseline. The organisation runs no Kubernetes, PostgreSQL or Kafka; it configures facilities, workforce, services, workflows and institutional policy through governed application workspaces.

**Isolation must be real, not nominal.** Shared hosting cannot rest on a `facility_id` column and a browser-supplied tenant header — which is precisely what the current estate does. It requires: server-derived trust domain (§4B), enforced organisation and facility predicates (v1.0 inversion I4), per-trust-domain encryption keys, and disclosure records for any cross-organisation access. **Until Phase 0 lands, no second organisation may be onboarded to shared hosting.** That is a gate, not a preference.

### B. Dedicated Hosted
Impilo/MoHCC hosts a logically or physically isolated environment. Isolation is a tier, chosen per agreement:

| Tier | Isolation |
|---|---|
| D1 | Dedicated trust domain + dedicated encryption keys |
| D2 | D1 + dedicated database clusters and storage buckets |
| D3 | D2 + dedicated namespace and dedicated Kafka topics |
| D4 | D3 + dedicated Keycloak realm or registered institutional issuer |
| D5 | D4 + a **dedicated Hospital Node** running on national infrastructure |
| D6 | D5 + a dedicated cluster, where warranted by scale or contract |

### C. Managed On-Premises
The institution supplies servers or VMs, local networking, power and environmental resilience, local storage and local ICT contacts. Impilo/MoHCC or an accredited operator manages Kubernetes, PostgreSQL, Kafka, Redis, storage, the node applications, monitoring, backup, upgrade orchestration, federation connectivity and the security baseline. **The institution owns its operational policy and business configuration throughout.**

### D. Sovereign On-Premises
The institution or its accredited operator runs infrastructure and platform. Impilo supplies signed releases, compatibility contracts, federation certificates, national policy and terminology packages, security and conformance requirements, upgrade support and federation services.

Permitted only where the institution satisfies the operational, security, backup and federation requirements of node accreditation. It is **not an unrestricted fork**: national identity contracts, provider and facility authority, federation contracts, security floors, interoperability contracts, statutory reporting, release compatibility floors and node accreditation all continue to bind.

### E. Facility Edge
A later, reduced profile for small facilities: local gateway, cache, device integration and store-and-forward, without a full node. Constrained continuity — explicitly **not** a seven-day autonomous hospital (§17A).

## 2B.2 Consumption and responsibility matrix

| Axis | National Shared Hosted | Dedicated Hosted | Managed On-Prem | Sovereign On-Prem | Facility Edge |
|---|---|---|---|---|---|
| **Hosting location** | National infrastructure | National infrastructure | Institution premises | Institution premises or its DC | Facility premises |
| **Infrastructure operator** | Impilo/MoHCC | Impilo/MoHCC | Institution | Institution | Managed |
| **Platform operator** | Impilo/MoHCC | Impilo/MoHCC | **Impilo/MoHCC or accredited operator** | **Institution or accredited operator** | Managed |
| **Application operator** | Impilo/MoHCC | Impilo/MoHCC, institution-controlled configuration | Impilo/MoHCC | Institution, on signed Impilo releases | Managed |
| **Data controller** | **The organisation** (never the host) | **The organisation** | **The institution** | **The institution** | The organisation |
| **Data processor** | Impilo/MoHCC | Impilo/MoHCC | Impilo/MoHCC or operator | Institution | Managed operator |
| **Key custodian** | National (per-domain keys) | National or **institutional** (D1+) | National or institutional | **Institutional** | National |
| **Backup operator** | Impilo/MoHCC | Impilo/MoHCC | Operator, institution-verified | Institution | Managed |
| **Release approver** | Impilo/MoHCC | Institution-controlled window | Institution-controlled window | **Institution**, subject to the security floor | Impilo/MoHCC |
| **Security monitoring** | Impilo/MoHCC | Impilo/MoHCC, institution-visible | Operator, institution-visible | Institution, with national conformance reporting | Impilo/MoHCC |
| **Support access** | Standing, audited | **JIT, institution-approved, recorded** | JIT, institution-approved, recorded | Institution-internal | Standing, audited |
| **Clinical governance** | **The organisation** | **The organisation** | **The institution** | **The institution** | The organisation |
| **Upgrade authority** | Platform | Institution window, platform floor | Institution window, platform floor | **Institution**, platform compatibility floor | Platform |
| **Federation obligations** | Full national participation | Per agreement | Per agreement | Per agreement + accreditation | Full (via its parent) |
| **Disconnection capability** | **None** — central-primary | None unless D5 | **Seven days** | **Seven days** | Constrained (hours–days, defined scope) |
| **Suitable for** | Clinics, small hospitals, organisations wanting a complete managed service | Large organisations wanting isolation without hardware | Hospitals providing infrastructure but wanting the platform managed | Highly autonomous non-MoHCC and specialised institutions | Small facilities with intermittent connectivity |

## 2B.3 Hosting confers real capability — say so **[D]**

A platform operator with cluster administration **can** reach the data. Any claim otherwise would be false, and a contract built on a false claim is worse than no contract. The controls are therefore cryptographic, procedural and evidentiary, in that order:

1. **Institution-held key custody.** At D1 and above, the institution holds the key-encryption key under envelope encryption; the platform operator holds ciphertext and cannot decrypt without an institution-held key. This is a real limit on a real capability. It has a real cost: **the institution becomes responsible for key availability**, and a lost KEK is unrecoverable data — which is why D1+ requires a key-management attestation at accreditation.
2. **Just-in-time support access.** No standing administrative access to a dedicated or on-premises environment. Access is requested against a support case, approved by the institution, time-boxed, scoped, and **session-recorded**.
3. **Institution-visible audit.** Every support session and every platform-originated query appears on the institution's disclosure dashboard (§6), itemised with actor, purpose, duration and what was reached.
4. **Separation of duties.** Platform administration grants no application role and no clinical role (§4A); a platform administrator who wants clinical data must obtain it the same way anyone else does, and the attempt is recorded.

---

# 3. Trust-domain and node model

## 3.1 The six identifiers, disambiguated

| Identifier | Answers | Authority | Example | Never |
|---|---|---|---|---|
| `trust_domain_id` | *Under whose legal control does this data sit?* | National Core (issued at accreditation) | `MoHCC-ZW`, `TD-CIMAS`, `TD-MISSION-KAROI` | …a deployment. One trust domain may span many nodes |
| `organisation_id` | *Who operates or governs this?* | org-registry (existing) | Ministry, a mission board, a private group | …a legal controller by itself. Many organisations may sit in one trust domain |
| `facility_id` | *Where does care happen?* | TUSO (existing, unchanged) | Parirenyatwa, a virtual service point | …changed when a facility moves onto a node |
| `node_id` | *Which deployment instance?* | Fleet Service (new) | `NODE-PARI-01`, `NODE-NATIONAL-CORE` | …a tenant, an organisation, or a facility |
| `work_context_id` | *In what authorised capacity is this actor acting, right now?* | BFF mint + tshepo-identity (existing, hardened) | "Registrar, Ward 3B, Parirenyatwa, CLINICAL_CARE" | …browser state |
| `jurisdiction_id` | *Under whose oversight authority?* | org-registry / wgv (existing) | district, province, national, HPA, NCZ, a programme | …conflated with organisation |

**The cardinal rule, stated as a constraint:** `node_id` appears in **no** clinical or registry business predicate. It appears only in provenance, routing, audit and operations. A query that filters clinical data by `node_id` is a design defect; the correct predicate is `trust_domain_id` + `facility_id` + policy.

## 3.2 Canonical schema

New authority: `services/organization-registry-service` gains the trust-domain and agreement tables (it already owns organisations and the closed 17-type vocabulary); a **new** `services/fleet-service` owns node, certificate, capability, release and connection state. Splitting them this way keeps governance data in the registry plane and operational data in the enterprise plane, and prevents the Fleet Service from becoming a second organisation registry.

```sql
-- ── org-registry (governance plane) ─────────────────────────────────────────

trust_domain (
  trust_domain_id        UUID PRIMARY KEY,
  code                   VARCHAR(48) NOT NULL UNIQUE,     -- 'MOHCC-ZW', 'TD-CIMAS'
  display_name           TEXT NOT NULL,
  controller_type        VARCHAR(32) NOT NULL,            -- MINISTRY | PRIVATE_GROUP | MISSION |
                                                          -- LOCAL_AUTHORITY | SECURITY_SECTOR |
                                                          -- UNIVERSITY | REGULATOR | PARTNER
  data_controller_legal_name TEXT NOT NULL,
  data_controller_contact    JSONB NOT NULL,
  jurisdiction_id        UUID NULL REFERENCES jurisdiction,
  accreditation_status   VARCHAR(24) NOT NULL,            -- see lifecycle below
  accredited_at          TIMESTAMPTZ, suspended_at TIMESTAMPTZ, withdrawn_at TIMESTAMPTZ,
  default_data_sharing_policy_id UUID NULL,
  key_custody_mode       VARCHAR(24) NOT NULL DEFAULT 'NATIONAL',  -- NATIONAL | INSTITUTIONAL
  created_at, updated_at, created_by_actor, version INT NOT NULL
);

organisation ADD COLUMN trust_domain_id UUID NOT NULL REFERENCES trust_domain;
-- Backfill: every existing organisation → the MoHCC trust domain (§10.1).

facility  -- stays in TUSO; TUSO gains:
  ALTER TABLE tuso.facility
    ADD COLUMN trust_domain_id UUID NOT NULL,        -- denormalised for query scoping
    ADD COLUMN governing_organisation_id UUID NULL;  -- closes the "facility has no org column" gap

federation_agreement (
  agreement_id           UUID PRIMARY KEY,
  from_trust_domain_id   UUID NOT NULL REFERENCES trust_domain,
  to_trust_domain_id     UUID NOT NULL REFERENCES trust_domain,
  agreement_type         VARCHAR(32) NOT NULL,  -- NATIONAL_CONTRIBUTION | REFERRAL_EXCHANGE |
                                                -- PATIENT_AUTHORISED | STATUTORY_REPORTING |
                                                -- EMERGENCY_ACCESS | RESEARCH_EXTRACT
  data_sharing_policy_id UUID NOT NULL REFERENCES data_sharing_policy,
  legal_basis            VARCHAR(48) NOT NULL,
  signed_by_from         JSONB NOT NULL,   -- signatory identity + signature ref
  signed_by_to           JSONB NOT NULL,
  effective_from         TIMESTAMPTZ NOT NULL,
  effective_to           TIMESTAMPTZ NULL,
  status                 VARCHAR(24) NOT NULL,  -- DRAFT|PENDING_COUNTERSIGNATURE|ACTIVE|
                                                -- SUSPENDED|TERMINATED|EXPIRED
  version                INT NOT NULL,
  supersedes_agreement_id UUID NULL
);

data_sharing_policy (
  policy_id              UUID PRIMARY KEY,
  trust_domain_id        UUID NOT NULL,
  policy_code            VARCHAR(64) NOT NULL,
  domain_rules           JSONB NOT NULL,   -- per data domain (§7): residency class, disclosure
                                           -- class, sensitivity ceiling, purpose allow-list,
                                           -- consent requirement, redaction profile
  sensitivity_ceiling    VARCHAR(32) NOT NULL,  -- highest class disclosable under this policy
  requires_patient_consent BOOLEAN NOT NULL,
  emergency_override_allowed BOOLEAN NOT NULL,
  retention_days         INT NULL,
  status                 VARCHAR(24) NOT NULL,
  version                INT NOT NULL,
  UNIQUE (trust_domain_id, policy_code, version)
);

local_authority (          -- who may act for a trust domain, and over what
  local_authority_id     UUID PRIMARY KEY,
  trust_domain_id        UUID NOT NULL,
  organisation_id        UUID NULL,
  facility_id            UUID NULL,
  authority_type         VARCHAR(40) NOT NULL,  -- TRUST_DOMAIN_ADMIN | NODE_OPERATOR |
                                                -- CLINICAL_GOVERNANCE | PRIVACY_OFFICER |
                                                -- BREAK_GLASS_REVIEWER | DISCLOSURE_REVIEWER
  subject_health_id      UUID NOT NULL,
  granted_by             UUID NOT NULL, granted_at TIMESTAMPTZ NOT NULL,
  expires_at             TIMESTAMPTZ NULL, revoked_at TIMESTAMPTZ NULL,
  UNIQUE (trust_domain_id, subject_health_id, authority_type, facility_id)
      WHERE revoked_at IS NULL
);

-- ── fleet-service (operations plane) ────────────────────────────────────────

deployment_node (
  node_id                UUID PRIMARY KEY,
  node_code              VARCHAR(48) NOT NULL UNIQUE,   -- 'NODE-PARI-01'
  node_type              VARCHAR(24) NOT NULL,          -- NATIONAL_CORE | HOSPITAL_NODE |
                                                        -- FACILITY_EDGE | DR_STANDBY
  trust_domain_id        UUID NOT NULL,                 -- the node belongs to exactly one
  operating_organisation_id UUID NOT NULL,
  profile                VARCHAR(48) NOT NULL,          -- helm profile name
  lifecycle_state        VARCHAR(24) NOT NULL,          -- see §3.3
  contact                JSONB NOT NULL,                -- operator, escalation, site address
  network                JSONB NOT NULL,                -- advertised endpoints, egress posture
  created_at, activated_at, suspended_at, decommissioned_at,
  version                INT NOT NULL
);

node_facility_assignment (
  assignment_id          UUID PRIMARY KEY,
  node_id                UUID NOT NULL REFERENCES deployment_node,
  facility_id            UUID NOT NULL,                 -- TUSO facility_uuid — UNCHANGED
  role                   VARCHAR(16) NOT NULL,          -- PRIMARY | DR | MIGRATION_TARGET
  effective_from         TIMESTAMPTZ NOT NULL,
  effective_to           TIMESTAMPTZ NULL,
  assigned_by            UUID NOT NULL,
  -- exactly one PRIMARY per facility at any instant:
  EXCLUDE USING gist (facility_id WITH =, tstzrange(effective_from, effective_to) WITH &&)
      WHERE (role = 'PRIMARY')
);

node_certificate (
  certificate_id         UUID PRIMARY KEY,
  node_id                UUID NOT NULL,
  purpose                VARCHAR(24) NOT NULL,          -- MTLS_CLIENT | MTLS_SERVER |
                                                        -- ENVELOPE_SIGNING | BUNDLE_VERIFY
  subject_dn             TEXT NOT NULL,
  serial                 TEXT NOT NULL UNIQUE,
  spki_sha256            TEXT NOT NULL,                 -- pinning material
  issued_at, not_before, not_after TIMESTAMPTZ NOT NULL,
  status                 VARCHAR(16) NOT NULL,          -- PENDING|ACTIVE|ROTATING|REVOKED|EXPIRED
  revoked_at             TIMESTAMPTZ NULL, revocation_reason TEXT NULL,
  issued_by_ca           VARCHAR(48) NOT NULL           -- 'impilo-node-ca-v1'
);

node_capability (
  node_id                UUID NOT NULL,
  capability_key         VARCHAR(64) NOT NULL,          -- 'clinical.inpatient', 'lab.lims.hl7v2',
                                                        -- 'imaging.pacs.local', 'pharmacy.dispense'
  enabled                BOOLEAN NOT NULL,
  declared_version       VARCHAR(24) NOT NULL,
  attested_at            TIMESTAMPTZ NOT NULL,          -- self-reported by the node, signed
  PRIMARY KEY (node_id, capability_key)
);

node_release (
  node_id                UUID NOT NULL,
  release_channel        VARCHAR(24) NOT NULL,          -- CANARY | EARLY | STABLE | LTS
  release_version        VARCHAR(48) NOT NULL,
  chart_version          VARCHAR(24) NOT NULL,
  image_digest_manifest_sha TEXT NOT NULL,
  applied_at             TIMESTAMPTZ NULL,
  state                  VARCHAR(24) NOT NULL,          -- OFFERED|ACKNOWLEDGED|APPLYING|
                                                        -- APPLIED|FAILED|ROLLED_BACK
  compatibility_floor    VARCHAR(48) NOT NULL,          -- min federation schema_version it speaks
  PRIMARY KEY (node_id, release_version)
);

node_connection (
  node_id                UUID NOT NULL,
  peer                   VARCHAR(24) NOT NULL,          -- 'NATIONAL_CORE'
  state                  VARCHAR(24) NOT NULL,          -- CONNECTED|DEGRADED|DISCONNECTED|
                                                        -- SUSPENDED|QUARANTINED
  last_heartbeat_at      TIMESTAMPTZ NULL,
  last_successful_exchange_at TIMESTAMPTZ NULL,
  outbound_queue_depth   BIGINT NOT NULL DEFAULT 0,
  inbound_queue_depth    BIGINT NOT NULL DEFAULT 0,
  oldest_unacked_at      TIMESTAMPTZ NULL,
  disconnected_since     TIMESTAMPTZ NULL,
  PRIMARY KEY (node_id, peer)
);
```

`work_context` and `jurisdiction` already exist in substance — `work_context` as the minted duty token plus `tshepo_identity.scoped_token`, `jurisdiction` as `jurisdiction_code` on regulatory appointments and `wgv_jurisdiction`. Both are **promoted to first-class rows** (§10.3) so they can be referenced by federation metadata rather than carried as loose strings.

## 3.3 Lifecycles

```mermaid
stateDiagram-v2
  direction LR
  [*] --> REGISTERED : fleet-service registration
  REGISTERED --> ENROLLING : CSR submitted, operator identity proven
  ENROLLING --> PROVISIONED : node cert issued + bundles seeded
  PROVISIONED --> ACTIVE : first successful federation handshake
  ACTIVE --> DEGRADED : heartbeat lost < staleness ceiling
  DEGRADED --> ACTIVE : reconnect
  DEGRADED --> DISCONNECTED : beyond staleness ceiling
  DISCONNECTED --> RECONCILING : reconnect with backlog
  RECONCILING --> ACTIVE : backlog drained, no quarantine
  RECONCILING --> QUARANTINED : integrity or schema failure
  ACTIVE --> SUSPENDED : governance action (agreement lapse, breach)
  QUARANTINED --> ACTIVE : operator clears after review
  SUSPENDED --> ACTIVE : reinstated
  SUSPENDED --> DECOMMISSIONING : withdrawal
  DECOMMISSIONING --> [*] : facilities reassigned, data disposition executed
```

**Trust domain:** `PROVISIONAL → ACCREDITED → (SUSPENDED ⇄ ACCREDITED) → WITHDRAWN`. A domain in `PROVISIONAL` may run a node but may not receive cross-domain disclosures. `SUSPENDED` halts all outbound disclosure to that domain and all inbound contribution from it; local care continues (a governance suspension must never stop a hospital treating patients).

**Federation agreement:** `DRAFT → PENDING_COUNTERSIGNATURE → ACTIVE → (SUSPENDED ⇄ ACTIVE) → TERMINATED|EXPIRED`, with `supersedes_agreement_id` for renegotiation. Expiry of an agreement stops *new* disclosure; it does not retro-delete what was lawfully disclosed, and it does not stop referral **replies** on in-flight cases (an in-flight clinical episode is completed under the agreement in force when it started, recorded on the referral).

## 3.4 How an existing TUSO facility joins a node — without changing its Facility ID

This is the single most important continuity requirement in the model, and it is satisfied by construction: **`node_facility_assignment` is a separate table keyed by the existing `tuso.facility.facility_uuid`.** No facility identifier changes, ever.

```mermaid
sequenceDiagram
  autonumber
  participant FA as Facility administrator
  participant NC as National Core (org-registry + fleet)
  participant TU as TUSO
  participant HN as Hospital Node
  participant FG as Federation Gateway

  FA->>NC: Request node enrolment for facility_uuid F (existing MFL/FCV facility)
  NC->>TU: Verify F: exists, legitimacy allows platform operation, governing org known
  Note over NC,TU: Uses the existing FCV legitimacy lattice — a facility with no allowing<br/>row cannot be assigned to a node. Today that is 5 of 7,285.
  NC->>NC: Create deployment_node (REGISTERED) in the facility's trust domain
  NC->>NC: node_facility_assignment(node, F, role=PRIMARY, effective_from)
  FA->>HN: Install node, run enrolment (QR + operator identity)
  HN->>NC: CSR + capability attestation + release manifest
  NC->>HN: node certificate + signed node configuration document + initial bundles
  HN->>FG: First handshake → node_connection ACTIVE
  NC->>NC: Publish facility→node routing (federation directory)
  Note over NC,HN: F's facility_uuid is unchanged. Only its ROUTING changed:<br/>the directory now says "F's primary node is NODE-X".
```

**One node, several facilities.** A hospital node commonly serves a main campus, satellite clinics and a virtual service point. Each is a separate `node_facility_assignment` row with `role=PRIMARY`. All share the node's databases; separation is by `facility_id` predicates and PDP membership, exactly as it must already be *within* a facility today (inversion **I4**).

**One facility, several nodes.** Permitted only as `PRIMARY` + `DR` (+ `MIGRATION_TARGET` during a move). The `EXCLUDE` constraint guarantees at most one `PRIMARY` at any instant — this is the mechanism that prevents split-brain writes for a facility. DR promotion is a governed act recorded in the fleet service, and the federation directory refuses to route to a non-primary node except for read-only DR verification traffic.

---

# 3A. Service responsibility schema **[T]**

## 3A.1 Where responsibility lives, and why not on the node

Three candidate homes were considered. **Responsibility belongs on a versioned profile referenced by a service agreement, and overridable per node** — not on `deployment_node` alone, for two decisive reasons:

- **A hosted organisation has no node at all.** A National Shared Hosted customer needs a data controller, a clinical-governance authority, a support-access policy and a release approver — with nothing to hang them on if responsibility lives only on nodes.
- **Production, DR and backup can have different operators.** A DR node run by a second accredited provider, or a backup operator distinct from the platform operator, cannot be expressed by a single per-organisation assignment.

```sql
-- ── org-registry (governance plane) ──────────────────────────────────────────

service_agreement (                 -- the commercial/service relationship
  service_agreement_id   UUID PRIMARY KEY,
  trust_domain_id        UUID NOT NULL REFERENCES trust_domain,
  organisation_id        UUID NOT NULL,
  consumption_profile    VARCHAR(32) NOT NULL,   -- NATIONAL_SHARED_HOSTED | DEDICATED_HOSTED |
                                                 -- MANAGED_ON_PREMISES | SOVEREIGN_ON_PREMISES |
                                                 -- FACILITY_EDGE
  isolation_tier         VARCHAR(8) NULL,        -- D1..D6, dedicated hosted only
  commercial_service_tier VARCHAR(32) NULL,
  responsibility_profile_id UUID NOT NULL REFERENCES service_responsibility_profile,
  effective_from         TIMESTAMPTZ NOT NULL, effective_to TIMESTAMPTZ NULL,
  status                 VARCHAR(24) NOT NULL,   -- DRAFT|PENDING_SIGNATURE|ACTIVE|
                                                 -- SUSPENDED|TERMINATED|EXPIRED
  signed_by_platform JSONB, signed_by_organisation JSONB,
  version                INT NOT NULL, supersedes_agreement_id UUID NULL
);

service_responsibility_profile (    -- versioned, immutable once ACTIVE
  responsibility_profile_id UUID PRIMARY KEY,
  profile_code           VARCHAR(64) NOT NULL,
  hosting_model          VARCHAR(32) NOT NULL,   -- NATIONAL_INFRASTRUCTURE | NATIONAL_DATA_CENTRE |
                                                 -- ZCHPC | INSTITUTION_PREMISES |
                                                 -- INSTITUTION_DATA_CENTRE | APPROVED_SOVEREIGN_CLOUD |
                                                 -- DISASTER_RECOVERY_SITE
  infrastructure_operator_id     UUID NOT NULL,
  platform_operator_id           UUID NOT NULL,
  application_operator_id        UUID NOT NULL,
  data_controller_id             UUID NOT NULL,   -- NEVER inferred from any operator above
  data_processor_id              UUID NOT NULL,
  identity_operator_id           UUID NOT NULL,
  key_custodian_id               UUID NOT NULL,
  backup_operator_id             UUID NOT NULL,
  release_approver_id            UUID NOT NULL,
  security_monitoring_operator_id UUID NOT NULL,
  clinical_governance_authority_id UUID NOT NULL,
  support_access_policy_id       UUID NOT NULL REFERENCES support_access_policy,
  maintenance_window_policy_id   UUID NOT NULL,
  version                INT NOT NULL,
  status                 VARCHAR(16) NOT NULL,   -- DRAFT|ACTIVE|SUPERSEDED
  approved_by, approved_at,
  UNIQUE (profile_code, version)
);

support_access_policy (
  support_access_policy_id UUID PRIMARY KEY,
  mode                   VARCHAR(24) NOT NULL,   -- STANDING_AUDITED | JIT_INSTITUTION_APPROVED |
                                                 -- INSTITUTION_INTERNAL_ONLY
  max_session_minutes    INT NOT NULL,
  requires_institution_approval BOOLEAN NOT NULL,
  session_recording_required    BOOLEAN NOT NULL,
  approver_role          VARCHAR(48) NOT NULL,
  break_glass_permitted  BOOLEAN NOT NULL,
  disclosure_visible_to_institution BOOLEAN NOT NULL DEFAULT true
);

-- ── fleet-service ────────────────────────────────────────────────────────────
deployment_node
  ADD COLUMN hosting_model  VARCHAR(32) NOT NULL,
  ADD COLUMN service_agreement_id UUID NOT NULL,
  ADD COLUMN responsibility_profile_id UUID NULL;   -- per-node override; NULL = agreement default
```

## 3A.2 The rule that makes this load-bearing **[D]**

> **No authority may be inferred from any operator field.** `data_controller_id`, `clinical_governance_authority_id` and the institutional administration roles in §4A are the *only* sources of authority. `infrastructure_operator_id`, `platform_operator_id` and `application_operator_id` describe who does work, never who decides.

Enforcement: the PDP receives the responsibility profile as policy input; **no rule in the closed condition vocabulary may key on an operator field**, and the vocabulary lockstep test (which already exists for `RECOGNISED_CONDITION_KEYS`) is extended to reject any attempt to add one.

## 3A.3 The five cases the model must express

| Case | Encoding |
|---|---|
| MoHCC hosts a private hospital's dedicated node, no ordinary clinical access | `hosting_model=NATIONAL_INFRASTRUCTURE`, infra/platform operator = MoHCC, `data_controller_id` = the hospital, `clinical_governance_authority_id` = the hospital, `support_access_policy.mode=JIT_INSTITUTION_APPROVED`, key custodian = the hospital (D1+) |
| Hospital owns infrastructure, Impilo operates the platform | `hosting_model=INSTITUTION_PREMISES`, infrastructure operator = hospital, platform operator = Impilo, controller = hospital |
| Hospital group runs its own platform, consumes national applications and federation | `SOVEREIGN_ON_PREMISES`, platform + identity operator = group, application operator = group on signed releases, release approver = group subject to the security floor |
| DR node operated by a different accredited provider | production node uses the agreement default; DR node carries a **per-node `responsibility_profile_id` override** naming the other infrastructure and backup operator |
| Different assignments for production, DR and backup | three profiles, one agreement, two node overrides |

---

# 4. Data-residency and disclosure matrix

## 4.1 Classes

**Residency class** — where the authoritative copy lives: `NATIONAL_AUTHORITATIVE` · `LOCALLY_AUTHORITATIVE` · `DUAL` (distinct facets authoritative in each place).
**Disclosure class** — what crosses the federation boundary: `FULL` · `SUMMARY` · `INDEX_ONLY` (existence + pointer, fetch on demand) · `ON_DEMAND` (nothing pushed; pull under a live authorisation) · `STATUTORY_ONLY` (de-identified or aggregate for a legal report) · `NEVER_WITHOUT_SPECIFIC_AUTHORITY`.

## 4.2 The matrix

Columns are defaults per context; a `data_sharing_policy` may tighten them and may only loosen them where the column marked *(negotiable)* says so.

| Data domain | Authority | MoHCC Hospital Node → National | Non-MoHCC node → National | Patient-authorised cross-institution | Statutory reporting | Emergency access |
|---|---|---|---|---|---|---|
| Patient identity (Health ID, CPID map) | **National** | N/A — national issues | N/A — national issues, node caches | Resolved nationally | — | Provisional O-CPID locally, reconciled |
| Demographics (PII) | National, node-cached | SUMMARY (updates as amendments) | INDEX_ONLY *(negotiable → SUMMARY)* | SUMMARY under consent | Aggregate only | SUMMARY with elevated audit |
| Provider standing / licensure | **National** | N/A — bundle-cached at node | N/A — bundle-cached | — | — | Cached bundle within staleness ceiling |
| Facility configuration | **DUAL** — TUSO owns registry identity; node owns operational shape | Registry facts SUMMARY up; operational config stays local | Same | — | Aggregate | Local |
| Workforce assignments | **DUAL** — national employment truth (wgv/HSC), local rostering | SUMMARY (assignment state) | INDEX_ONLY | — | Aggregate | Local roster |
| Encounters | **Local** | SUMMARY (encounter header: type, facility, dates, provider, disposition) | INDEX_ONLY *(negotiable → SUMMARY)* | SUMMARY under consent | Aggregate counts | SUMMARY |
| Clinical notes (free text) | **Local** | **NEVER without specific authority** | NEVER | ON_DEMAND under explicit consent | Never | ON_DEMAND, elevated audit |
| Problems / diagnoses | **Local** | FULL (coded) | SUMMARY *(negotiable)* | FULL under consent | Aggregate + notifiable | FULL |
| Allergies / intolerances | **Local** | **FULL** — safety-critical, always contributed | **FULL** — the one clinical domain non-negotiable for patient safety | FULL | — | FULL |
| Medications | **Local** | FULL (coded, dispensed + active) | SUMMARY *(negotiable → FULL)* | FULL under consent | Aggregate | FULL |
| Orders | **Local** | SUMMARY (order header + status) | INDEX_ONLY | SUMMARY | Aggregate | SUMMARY |
| Results | **Local** | FULL (coded results + abnormal flags) | SUMMARY *(negotiable)* | FULL under consent | Notifiable + aggregate | FULL |
| Imaging metadata | **Local** | INDEX_ONLY (study exists, modality, date, accession) | INDEX_ONLY | INDEX_ONLY + ON_DEMAND fetch | — | INDEX_ONLY + ON_DEMAND |
| **Images (pixel data)** | **Local** | **ON_DEMAND only** — never bulk-pushed | ON_DEMAND | ON_DEMAND under consent | Never | ON_DEMAND, bandwidth-negotiated |
| Documents | **Local** | INDEX_ONLY + ON_DEMAND | INDEX_ONLY | ON_DEMAND under consent | Never | ON_DEMAND |
| Theatre records | **Local** | SUMMARY (procedure coded, date, outcome) | INDEX_ONLY | SUMMARY under consent | Aggregate | SUMMARY |
| Mental-health records | **Local** | **NEVER without specific authority** | NEVER | ON_DEMAND under *explicit, category-specific* consent | Aggregate only, k-anonymised | **Break-glass only, dual-authorised** |
| Sexual & reproductive health | **Local** | **NEVER without specific authority** | NEVER | ON_DEMAND under explicit, category-specific consent | Aggregate only, k-anonymised | Break-glass only, dual-authorised |
| Other SPECIALLY_PROTECTED | **Local** | NEVER without specific authority | NEVER | Category-specific consent | Aggregate only | Break-glass only, dual-authorised |
| Consent directives | **DUAL** — captured either side, both authoritative for their own captures | FULL (directives are federation control data) | FULL | FULL | — | Cached bundle |
| Referrals | **Local at origin; routed** | FULL to the routed counterpart + SUMMARY nationally | FULL to counterpart, SUMMARY national *(negotiable)* | N/A — referral *is* the authorised exchange | Aggregate | FULL, emergency basis recorded |
| Billing | **Local** | SUMMARY (charge classes, totals) | **NEVER** — commercially sensitive | — | Aggregate | — |
| Insurance / coverage | **National** (scheme + benefit truth) | Claims FULL | Claims FULL (that is the point of the payer rail) | — | Aggregate | Offline eligibility token |
| Payments | **DUAL** | SUMMARY (settlement state) | SUMMARY | — | Aggregate | — |
| Public-health / notifiable | **National** | **FULL, mandatory, no consent gate** (legal basis) | **FULL, mandatory** | — | This *is* the statutory channel | FULL |
| Audit | **Local, chained per node** | SUMMARY (chain heads + disclosure records) | SUMMARY (chain heads only) | Disclosure records visible to both | Chain attestation | FULL local record |
| Research datasets | **Local** | NEVER without specific authority | NEVER | Under a separate research agreement + ethics | Never | — |

## 4.3 Five rules that make the matrix enforceable

1. **The matrix is data, not code.** It is expressed as `data_sharing_policy.domain_rules` rows and evaluated by the Federation Gateway's disclosure engine before an envelope is emitted, and again by the receiver before it is accepted. Two independent evaluations, both auditable.
2. **Sensitivity ceiling wins over domain default.** A record classified SPECIALLY_PROTECTED (existing `zibo` V008 confidentiality vocabulary, existing `ResourceSensitivityClassifier`) is never disclosed above its category ceiling, regardless of what the domain row says.
3. **Statutory beats consent; consent beats convenience.** Notifiable-disease reporting proceeds under legal basis with no consent gate and is recorded as such (`consent_basis = LEGAL_OBLIGATION`). Everything else that lacks a legal basis requires consent, and "the receiving clinician would find it useful" is not a basis.
4. **Emergency is a disclosure *mode*, not a policy bypass.** It raises what may cross for a bounded window under break-glass, records a disclosure record on both sides, and triggers post-hoc review in the *disclosing* institution.
5. **Non-MoHCC defaults are tighter and negotiable upward only.** A non-MoHCC institution starts at INDEX_ONLY for most clinical domains and negotiates outward through a `federation_agreement`. It cannot be defaulted into contribution.

## 4.4 Extended domains (v1.1) — personal, professional and operational

These domains were absent from v1.0. They need different columns from the clinical matrix, because the decisive questions are *may the employer see it* and *can consent disclose it*.

| Domain | Authority | Hosting options | At a Hospital Node? | Cached? | Employer may view | Nationally viewable | Consent can disclose | Statutory override | Retention |
|---|---|---|---|---|---|---|---|---|---|
| **Personal Health Record** (My Life) | **The individual** (vito + wellness/simba) | National only | **No — never rendered** | No | **Never** | By the person; platform only for support with consent | Yes, item-scoped, as a labelled disclosure | No | Person-controlled + statutory floor |
| **Patient-reported data** | The individual | National | Only as a labelled disclosure attached to an episode | Only within that disclosure | Only what was disclosed | Only if contributed | Yes | No | Follows the disclosure |
| **Device / wellness data** | The individual | National | No | No | **Never** (default `provider_access_allowed=false`) | Aggregate only with consent | Yes, per source and category | No | Person-controlled |
| **Shared-Care Cache** (§19B) | The **origin** node retains authority; the cache is a projection | Node only | **Yes — the point of it** | Yes, by definition | Yes, within the treating cohort and purpose | Cache contents are not re-contributed | The cache exists *because* of consent or legal basis | Emergency may widen it | Cohort exit + TTL |
| **Professional portfolio** | The individual + VARAPI for verified elements | National | **No** | No | **No** | Yes, to the person and their regulator | Yes | Regulator may compel | Career-long |
| **Regulatory correspondence** | Regulator ↔ individual | National | **No** | No | **Never** | Regulator and person only | Person may share specific items | Yes, by the regulator | Statutory |
| **CPD records** | VARAPI / councils | National | **No** — only the compliance *verdict* reaches the node | Verdict only, in the standing bundle | **Only the verdict** (compliant / not / expiring) | Yes | — | Regulator may compel | Statutory |
| **Local professional-status projection** | Derived from the standing bundle | Node | **Yes** | Yes, freshness-labelled | Yes — it is what authorises work | Not contributed | — | — | Bundle TTL |
| **Organisational HR record** | The organisation (wgv + hr-payroll) | National or node per profile | Employment facts only | Assignment bundle | Yes — it is the employer's own record | Employment truth only | — | Yes (labour, tax) | Statutory |
| **Local work-context history** | The node | Node | **Yes** | — | Yes, as operational audit | Chain-head attestation only | — | Yes, for investigation | Audit retention |
| **Node support telemetry** | Platform operator | Node → national | Yes | — | Yes, institution-visible | Yes | — | — | Operational |
| **Platform support sessions** | Platform operator, institution-approved | Both | Yes, recorded | — | **Yes — mandatory visibility** | Yes | — | — | Contractual, ≥ audit floor |
| **Disclosure-dashboard records** | Both parties jointly | Both | Yes | — | **Yes — the institution's evidence** | Yes | — | — | ≥ the disclosure it records |

**Three rules govern this table.** First, *the employer column is the sharpest one*: everything a hospital genuinely needs to roster and supervise safely is in the standing bundle and the assignment bundle, and nothing else about a person's professional or personal life is available to it. Second, *device and wellness data default to unshared* — the existing `wellness_connected_sources` row ships `provider_access_allowed=false` and `clinical_writeback_allowed=false`, and that default is now doctrine rather than an implementation choice. Third, *support sessions are visible to the institution by contract*, which is what makes §2B.3's honesty about hosting capability enforceable rather than rhetorical.

---

# 4A. The five rights and business domains **[D]**

A person is one identity operating in several capacities. The platform must keep those capacities apart — technically, not by menu labels.

## 4A.1 Domain definitions

### A. Individual personal domain
Health identity, My Life, the Personal Health Record, citizen authentication, personal consent, guardianship and delegation, wellness tracking, personal device data, personal uploads, Marketplace, personal coverage and payment views, personal preferences.

**Authoritative services:** vito (person identity), wellness-service / simba-service (`wellness_connected_sources`, personal data), mvumo (consent and delegation), mushe-wallet + coverage (personal money views), msika (Marketplace), community-service (social).
**Data authority:** the individual. **User rights:** access, explanation, correction pathway, consent management, delegation, export.
**Permitted disclosures:** item-scoped, person-authorised, provenance-labelled, to a named recipient for a stated purpose.
**Administrative roles:** none institutional. Platform support only under an approved support case with the person's knowledge.

### B. Individual professional domain
Provider ID, qualifications, registration, licence, scope and restrictions, CPD, professional portfolio, regulatory applications and correspondence, career history, personal professional learning.

**Authoritative services:** varapi (registration, licence, scope — the regulator's truth), the nine council organisations via org-registry, learning-service (CPD evidence), and the existing self-service lane `/internal/v1/me/regulatory` (whose controller javadoc already states the caller's Health ID resolves *their own* records and never another person's — the correct doctrine, already shipped).
**Data authority:** the regulator for registration and standing; the individual for portfolio, planning and correspondence.
**Institutional rights:** **only the work-relevant subset** — active registration, permitted scope, restrictions relevant to work, expiry dates, required organisational credentials, mandatory local learning status. Nothing else.

### C. Organisation domain
Governance, participation, contracts and subscriptions, institutional policy, employment, appointments, tariffs, financial operations, institutional analytics, integrations, release approvals, data-sharing agreements, organisation-wide configuration.

**Authoritative services:** org-registry, workforce-governance (employment), costa/coverage/mushex (finance), the new `service_agreement` (§3A).

### D. Facility / practice domain
Departments, wards, service points, queues, appointments, local workforce assignments, encounters, orders and results, admissions and discharge, theatre, local pharmacy and stock, local billing, devices, printers, LIMS and PACS connections, downtime procedures, facility reporting.

**Authoritative services:** tuso (operational shape), vashandi (local assignment), pct, oros, inpatient, pharmacy, costa, document-service.
**Data authority:** **the facility is the operational source authority for the events it creates** — the v1.0 origin-authority rule, restated in domain terms.

### E. National health OS / platform domain
National identity frameworks, trust anchors, national registries, federation, interoperability contracts, national terminology, national clinical-content packages, national longitudinal projections, cross-institution referrals, statutory and public-health coordination, fleet and release governance, national audit and conformance.

**Data authority:** national for the registries it owns; **projection only** for everything a node created.

## 4A.2 Prohibited inheritances **[D]**

Each line is an access-control invariant with an acceptance test in §23.

| # | Prohibited | Why it is tempting | Test |
|---|---|---|---|
| P1 | An **employer** accessing My Life because the person works there | Employer holds the person's identity for rostering | A25 |
| P2 | A **facility administrator** inheriting professional regulatory correspondence | The administrator already sees licence status | A26 |
| P3 | A **platform administrator** inheriting clinical access | Cluster admin can reach the database | A23 |
| P4 | A **national administrator** inheriting local institutional administration | National role sounds superior | A24 |
| P5 | A **local identity provider** asserting licensure or professional scope | The IdP already asserts who logged in | A27 |
| P6 | A hospital treating **patient-reported** data as clinician-verified | It appears in the same timeline | A44 |
| P7 | A **node administrator** inheriting clinical access | They installed the system | A24 |
| P8 | An **organisation officer** inheriting cluster administration | They approved the node | A40 |

## 4A.3 The domains as they exist today — and the three leaks to close

Measured for this version:

- **Work is the only domain with a real credential.** The WORK_CONTEXT duty token is server-proven, audience-scoped, revocable by `jti`, and re-proved at mint. Personal and professional have **no token, no scope, no audience and no session field** — they are a server-computed response payload (`SessionExperienceService`'s three tabs: `personal` / `professional` / `work`), plus unsigned browser state (`OperationalMode` in sessionStorage), plus UI route guards (`navZone`).
- **There is no audience mapper anywhere in Keycloak.** Every client carries identical default scopes; the only per-client differences are that `experience-ui` gets `impilo-trust-headers` and `impilo-mobile-citizen` gets a `cpid` mapper the provider client lacks. Every login requests the same hardcoded scope string.
- **One OIDC session per person.** The session record carries `subject`, tokens, `acr`, `amr`, `authTime`, `stepUpTime`, `flowId`, `profile` and a `recovery` flag — **no domain, audience, mode or context field**.

Three concrete leaks to close in Phase 2:

| Leak | Evidence | Fix |
|---|---|---|
| Personal documents served from the clinical lane | the personal document vault calls `/internal/v1/clinical-tools/documents` | Move to a personal-audience endpoint family |
| Domain-misfiled routes | `/home/credentials` is `navZone: professional` inside the personal landing; `/citizen/provider-claim` is `professional` under a citizen prefix; most of `/marketplace` is `work`, not `life` | Refile against §4A; make navZone derive from the session audience rather than a hand-maintained table |
| Two independent hand-maintained domain enumerations that disagree | the server tab model and the client `navZone` table are separate lists of the same three domains; unregistered routes (`/professional` itself, `/professional/request-access`, `/my-life/fundraisers`, `/my/telehealth`) have **no navZone at all and are therefore not citizen-blocked** | One generated enumeration, server-derived, with an unregistered route defaulting to **deny** |

---

# 4B. Session and token separation **[T]**

## 4B.1 The model

```
Person identity  (one Health ID, one human)
  ├── Personal session        aud: impilo-personal
  ├── Professional session    aud: impilo-professional
  ├── Work session            aud: impilo-work:<node_id>      ← the existing duty token
  ├── Regulatory session      aud: impilo-regulatory:<org_id>
  ├── Organisation-admin      aud: impilo-org-admin:<org_id>
  ├── Platform-admin          aud: impilo-platform
  └── Break-glass             a work session with an elevated, bounded grant
```

**No single token carries every privilege.** A token whose audience does not match the API's expected audience is rejected — not downgraded, not partially honoured.

## 4B.2 Session matrix

| Session | Issuer | Audience | Key claims | Permitted APIs | Max TTL | Step-up | Usable locally | Usable nationally | Exchangeable | Basis | Never inherits |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Personal** | National Keycloak only | `impilo-personal` | `sub`, `health_id`, `acr` | `/internal/v1/citizen/**`, `/wellness/**`, `/marketplace/**`, PHR | 12 h idle / 24 h absolute | AAL2 for sensitive views, payments, sharing | **No** | Yes | **No** | The person | any clinical, work, admin authority |
| **Professional** | National Keycloak only | `impilo-professional` | `sub`, `health_id`, `provider_public_id` | `/internal/v1/me/regulatory/**`, portfolio, CPD, licensure self-service | 8 h | AAL2 | **No** | Yes | **No** | The person as professional | employer visibility, clinical write |
| **Work** | **Node** tshepo-identity (or national for hosted) | `impilo-work:<node_id>` | `tdid`, `nid`, `fid`, `oid`, `jid`, `wcid`, `work_mode`, `role`, `purpose_of_use`, `clinical_data_access` | node clinical + operational APIs | **900 s** (existing) | AAL2 to mint; per-action step-up per policy | **Yes** | Only at its own node's national twin | **No** | Assignment + standing + consent | personal or professional data |
| **Regulatory** | National Keycloak | `impilo-regulatory:<org_id>` | appointment, `jurisdiction_code`, `work_mode=REGULATORY_OPERATIONS` | regulatory console, registers | 900 s | AAL2 | Only if the node hosts a regulatory workspace | Yes | No | Appointment | clinical care access |
| **Organisation admin** | National or node IdP | `impilo-org-admin:<org_id>` | `oid`, admin role | org configuration, agreements, tariffs, workforce admin | 30 min | AAL2 + re-auth for destructive acts | Yes | Yes | No | Officer appointment | clinical read, cluster admin |
| **Platform admin** | National Keycloak only | `impilo-platform` | platform role, no `fid` | fleet, releases, certificates, node lifecycle | 30 min | **AAL2 + two-person for destructive acts** | No | Yes | No | Platform role | **any clinical data, any institutional admin** |
| **Break-glass** | Node | `impilo-work:<node_id>` + grant | `break_glass_grant_id`, patient scope, expiry | the named patient's record only | **≤ 60 min**, resource-scoped (ADR-10) | Fresh AAL2 required | Yes | No | No | Vital interest | anything beyond the named patient |

## 4B.3 The transition rule **[D]**

> **A session is never exchanged across a domain boundary.** Moving from Work to My Life is a *new authentication or a brokered handoff*, never a token upgrade. A node-issued work token presented to a personal API is rejected on audience, and the rejection is audited.

The brokered handoff is gated by the `trusted_issuer.identity_binding_mode` control from §11.1:

- `identity_binding_mode = LOCAL_ONLY` → the node's issuer **cannot** produce a national personal session at any assurance. The user re-authenticates nationally. This is the default for an institution-operated IdP.
- `identity_binding_mode = NATIONAL_BOUND` → a handoff is permitted: the national broker verifies the issuer, the person binding and the assurance, then mints a **fresh** personal session. The institution may still disable handoff entirely (`node_config.transitions.sso_handoff = false`), in which case re-authentication is always required.

In both cases the resulting personal session is **never returned to the node**, never stored in node state, and never visible to the institution.

## 4B.4 What must be built

`SessionExperienceService`'s three-tab model is the right seam and is kept — but it is promoted from a *response payload* to an *audience-bound session decision*. Concretely: audience mappers per client scope in Keycloak (none exist today), per-domain client registration, audience validation in every resource server, and the client-side `OperationalMode`/`navZone` tables replaced by a server-derived domain claim with unregistered routes defaulting to deny.

---

# 5. Seven-day outage behaviour

## 5.1 What must continue, and what makes it possible

| Capability | Made possible by | Bundle / local artefact it depends on |
|---|---|---|
| Local user authentication | Node Keycloak (MoHCC-managed) or institution IdP; both node-local | Local IdP; national issuer not required |
| Work-context entry | Local PDP + local work-context mint signed by the node's key | `workforce-standing` bundle (assignments), node signing key |
| Patient registration | Local VITO instance with a pre-allocated identifier block | Identifier allocation grant (§6.5) |
| Casualty, triage, OPD | PCT local, unchanged code | policy + consent + terminology bundles |
| Inpatient, ward, theatre | inpatient, surgery, procedures local | policy bundle; local device config |
| Orders and results | OROS local + local LIMS/PACS adapters | node integration config |
| Pharmacy | pharmacy + inventory local | product/terminology bundle |
| Billing | costa local; coverage eligibility from offline tokens | offline eligibility JWS (already exists) |
| Discharge | PCT + inpatient local | — |
| Local clinical record access | Local Butano projection + PCT + OROS | consent + sensitivity bundles |
| Consent & SPECIALLY_PROTECTED | Local tshepo-consent + local PDP with ENFORCE confidentiality | `consent` + `relationship` bundles, fail-closed on expiry |
| Local audit | Local tshepo-audit chain, per-node chain | — |
| Devices and integrations | Node-scoped configuration | node integration config |
| **Professional Status** (§11B) | Rendered from the standing bundle that already authorises work | provider-standing bundle, freshness shown |
| **National continuity for patients in the building** | Shared-Care Cache (§19B), serving with every item's age displayed | cohort cache, never claiming currency |

**What is explicitly unavailable, and must be shown as such:** My Life, the Personal Health Record, Marketplace, personal wellness, personal payments, full My Professional, and regulatory self-service. These are national individual-facing domains (§11B.3). **The node must not build local substitutes for them** — an employer-operated stand-in for a person's private health record is the one failure mode this architecture exists to prevent.

## 5.2 What queues, and what stops

**Queues (durable, replayed on reconnect):** national record contribution, outbound referrals to other nodes, statutory and public-health reports, national audit chain-head attestations, disclosure records, fleet health telemetry, claims submission to national payers.

**Degrades honestly (returns a stated, non-fabricated limitation):** remote provider verification beyond the standing bundle's staleness ceiling → `pending_backend` (existing behaviour, retained); cross-institution record lookup → "unavailable while disconnected", never an empty result presented as "no records"; national KPI views → the existing 502 rather than invented numbers.

**Stops (and must be visible in the UI):** inbound referrals from other nodes (they queue at the sender), new national identity minting beyond the local allocation block, cross-institution emergency access *initiated from elsewhere*, telemedicine with remote participants, national payment rails.

## 5.3 The staleness ladder

Each bundle class has a **refresh interval**, a **soft ceiling** (warn, continue) and a **hard ceiling** (fail closed or degrade to a stated fallback). These are the numbers the seven-day test proves.

| Bundle | Refresh | Soft ceiling | Hard ceiling | At hard ceiling |
|---|---|---|---|---|
| Policy rules | 15 min | 24 h | **30 days** | Fail closed on new policy classes; continue on cached rules for already-permitted routine care classes, with elevated audit |
| Provider standing | 1 h | 72 h | **14 days** | New clinical authority denied; providers already in an active work context continue for the episode; all writes flagged `standing_stale=true` |
| Consent directives | 15 min | 24 h | **7 days** | **Fail closed** for non-emergency access to consent-gated records; emergency path only |
| Relationship / delegation | 1 h | 72 h | 14 days | Delegated (guardian/proxy) access denied |
| Revocation list | 5 min | 1 h | **24 h** | Any actor or credential not affirmatively present in the last good list is treated as **potentially revoked** for high-authority actions (prescribing controlled drugs, break-glass approval, disclosure approval) |
| Terminology / clinical content | daily | 30 days | 90 days | Continue; new codes rejected with a stated reason |
| Facility / workforce registry | 1 h | 72 h | 14 days | Continue on cache; new staff cannot be activated |
| Trust anchors (issuer keys, node CA) | 6 h | 7 days | **30 days** | Fail closed on federation; local operation continues |

**Design note.** The consent hard ceiling (7 days) is deliberately equal to the autonomy requirement, not longer. A node that has been dark for more than seven days has, by doctrine, exceeded its designed autonomy and must not keep making consent-gated disclosures on a week-old picture — it drops to emergency-only for those classes and says so on screen.

---

# 6. MoHCC versus non-MoHCC operating model

| Dimension | MoHCC Hospital Node | Non-MoHCC institutional node |
|---|---|---|
| Trust domain | Inside `MOHCC-ZW` | Its own (`TD-<institution>`); a group may hold one domain over many organisations, facilities and nodes |
| Identity | **Managed node Keycloak** — realm provisioned and rotated by the National Core, credentials local | **Institution-owned** Keycloak / AD / LDAP / approved OIDC or SAML IdP, registered as a trusted issuer |
| Signing keys | National `tshepo-keys` custody | **Institutional custody permitted** (`key_custody_mode=INSTITUTIONAL`): institution holds its node signing key; the National Core holds only the public half and the CA trust |
| Clinical data default | Contribution ON by default (national longitudinal record is the ministry's purpose) | Contribution **OFF** by default; INDEX_ONLY, negotiated upward by agreement |
| National platform admin | May administer the node's platform (releases, infrastructure) — **still no clinical read** | **No platform administration at all** beyond release publication and certificate lifecycle; the institution operates its own node |
| Facility/org boundary inside the domain | **Still technically enforced** (inversion I4) — a MoHCC hospital may not read another MoHCC hospital's records without a basis | Enforced identically |
| Statutory reporting | Mandatory, automatic | **Mandatory, automatic — the one channel that is never negotiable** |
| Upgrade control | Ring-managed by the National Core, with a node-declared maintenance window | **Institution-controlled window**; the Core may only *offer* a release, plus a security floor with a contractual deadline |
| Disclosure visibility | Disclosure dashboard available | **Disclosure dashboard mandatory** — every national access to institutional data is visible to the institution, itemised, with actor and basis |
| Break-glass review | Reviewed by MoHCC clinical governance | Reviewed by the **institution's** governance; the National Core is notified, not the approver |
| Data on withdrawal | Facilities reassigned within the domain | Governed disposition: institution retains local records; national projections are frozen and marked `source_withdrawn`, not deleted (they are part of others' clinical history) |

**The administration separation, stated precisely.** Three distinct role spaces, in three distinct places, and no inheritance between them:
- **Platform administration** (Fleet Service, National Core realm) — releases, certificates, node lifecycle. Grants zero data access.
- **Trust-domain administration** (`local_authority` rows) — agreements, sharing policy, disclosure review, break-glass review. Grants governance rights over one domain, not clinical read.
- **Clinical access** (work context + PDP) — requires an active assignment at a facility, a purpose, a subject relationship, and consent or legal basis, as today but actually enforced.

---

# 6A. MoHCC trust-domain structure **[L — recommendation given, determination pending]**

v1.0 placed MoHCC hospitals inside a single `MOHCC-ZW` domain and flagged the controllership question as unresolved. This section evaluates the three options and recommends one, while keeping the implementation able to support all three until the legal determination lands.

## 6A.1 The options

**Option A — one MoHCC trust domain.** All MoHCC institutions inside `MOHCC-ZW`, with organisation and facility boundaries enforced within it.
**Option B — separate hospital trust domains.** Each large MoHCC hospital is its own trust domain, federated under MoHCC.
**Option C — hierarchical.** A broad `MOHCC-ZW` federation family containing institution-level control subdomains for hospitals that warrant them.

## 6A.2 Evaluation

| Criterion | A — single | B — separate | **C — hierarchical** |
|---|---|---|---|
| Legal controllership | Clean if the Ministry is controller; wrong if the hospital is | Clean if each hospital is controller; fragments ministerial oversight | **Expresses either** — the subdomain carries the controller field |
| Operational autonomy | Low — a central hospital has no distinct boundary | High | **High where granted, default low** |
| Incident response | Single national response | Fragmented across many domains | **National coordination, institutional execution** |
| Disclosure approval | One national approver | Each hospital approves its own | **Delegated by subdomain, escalable to the family** |
| Break-glass review | National reviewers | Institutional reviewers | **Institutional, with national visibility** |
| Retention | One policy | Many policies | **Family default, subdomain override** |
| Platform operations | Simplest | Most complex | Moderate |
| National continuity | Strongest | Weakest — no natural cross-hospital route | **Strong — the family is the route** |
| Cross-hospital access | Trivial, and *too* trivial: no boundary to cross | Requires an agreement per pair — heavy for one ministry | **Family agreement covers intra-family; still auditable** |
| Administrative complexity | Lowest | Highest | Moderate |
| Central-hospital expectations | Will be contested — a teaching hospital expects its own boundary | Met | **Met** |

## 6A.3 Recommendation **[O]**

**Adopt Option C.** A broad `MOHCC-ZW` family with institution-level control subdomains for hospitals that warrant them — initially the central and teaching hospitals, extended on request.

Three reasons it is also the *safe* choice under legal uncertainty. It is the only option that can express either controllership answer without re-modelling. It does not depend on the determination to start work, because **organisation and facility enforcement is required in every option** (v1.0 inversion I4) and that is the actual Phase 0 work. And it degenerates gracefully: with no subdomains created it behaves exactly as Option A, and with every hospital a subdomain it behaves as Option B.

```sql
trust_domain
  ADD COLUMN parent_trust_domain_id UUID NULL REFERENCES trust_domain,
  ADD COLUMN domain_role VARCHAR(24) NOT NULL DEFAULT 'STANDALONE';
      -- FAMILY | CONTROL_SUBDOMAIN | STANDALONE
```

Rules: a subdomain inherits the family's default sharing policy and may only **tighten** it; disclosure and break-glass review delegate to the subdomain where one exists; a family-level federation agreement covers intra-family exchange, so a MoHCC hospital referring to another MoHCC hospital needs no per-pair agreement — but the transfer still produces a disclosure record on both sides. **Depth is limited to two levels**; a subdomain may not itself have subdomains.

**Not deferrable:** whichever option is chosen, facility and organisation boundaries must be technically enforced *inside* the domain. Option A without I4 is a single undifferentiated administrative pool of every MoHCC hospital's records — which is what exists today, and is precisely what the recovery identified as the top pre-federation blocker.

---

# 7. National Core — target diagram

```mermaid
flowchart TB
  subgraph edge["Public edge"]
    DNS["DNS + cert-manager/ACME<br/>(cluster-managed — replaces host certbot)"]
    ING["Ingress → Envoy"]
  end

  subgraph pep["Tshepo enforcement (NATIONAL)"]
    ENV["Envoy — ext_authz ENABLED<br/>strips + regenerates all identity headers"]
    PDP["tshepo-authz — PDP<br/>policy_rule + OPA (parity→enforce)"]
    OPA["OPA"]
  end

  subgraph fed["Federation plane (NEW)"]
    FGW["Federation Gateway (hub side)<br/>mTLS · envelopes · queues · DLQ · quarantine"]
    FLEET["Fleet & Release Service<br/>nodes · certs · capabilities · releases · rings"]
    BUNDLE["Bundle Publisher<br/>signs policy/standing/consent/revocation/terminology"]
    DIR["Federation Directory<br/>facility→node routing · issuer registry"]
  end

  subgraph natsor["National systems of record"]
    KC["Keycloak — national realm<br/>+ trusted-issuer registry"]
    VITO["VITO — person identity + Health ID"]
    TID["tshepo-identity — CPID map, token authority"]
    KEYS["tshepo-keys — national CA + signing"]
    VAR["VARAPI — provider standing"]
    TUSO["TUSO — facility registry + FCV legitimacy"]
    ORG["org-registry — trust domains, orgs, agreements, sharing policy"]
    WGV["workforce-governance — employment truth"]
    ZIBO["ZIBO — terminology + confidentiality vocabulary"]
    CKP["Clinical Knowledge Platform"]
    CONS["tshepo-consent — national consent registry"]
    MV["Mvumo — consent journey"]
  end

  subgraph natproj["National projections (NOT source of truth)"]
    NBUT["Butano NATIONAL — longitudinal FHIR projection<br/>Provenance-stamped by origin node"]
    NAUD["tshepo-audit — national chain + node chain-head attestations"]
    DWH["Data warehouse / reporting / NDR"]
    SURV["Surveillance (notifiable)"]
  end

  subgraph natops["National operational services"]
    COV["Coverage — scheme + benefit truth"]
    MSX["MusheX — payer rails"]
    BFF["experience-bff (national)"]
    SHELL["one-ui-shell (national)"]
  end

  NODES["Hospital Nodes (spokes)"]

  DNS --> ING --> ENV --> BFF
  ENV --> PDP --> OPA
  BFF --> natsor & natops
  NODES <-->|"mTLS + signed envelopes<br/>ONLY sanctioned cross-site path"| FGW
  FGW --> DIR & FLEET
  FGW --> NBUT & NAUD & SURV & DWH & COV
  BUNDLE -->|signed bundles| FGW
  natsor --> BUNDLE
  KEYS -.->|signs| BUNDLE
  PDP --> CONS
```

**Three things this diagram asserts.** First, the National Core keeps every registry that must be nationally unique (identity, provider standing, facility, terminology, policy) — these are the bundle sources, not per-node data. Second, the national clinical store is explicitly a **projection**, drawn separately from the systems of record, because it is not authoritative for anything a node created. Third, `Hospital Nodes` touch exactly one box: the Federation Gateway. There is no other line into the National Core, and drawing one is a design violation.

# 8. Hospital Node — target diagram

> **Hosting-independent.** This is a *functional* topology. The cluster below may sit on the institution's premises, in MoHCC infrastructure, at the NDC, at ZCHPC, in an approved sovereign cloud or at a DR site — without changing a single box, and without changing who controls the data (§2A, §3A).

```mermaid
flowchart TB
  subgraph clients["Local clients"]
    WEB["Browser → one-ui-shell (node)<br/>WORK | PROFESSIONAL STATUS | OPEN FULL IMPILO"]
    MOB["Mobile (same national app)<br/>node-enrolled via signed QR config"]
    DEV["Modalities · analysers · printers · card agents"]
  end

  subgraph k8s["Hospital Kubernetes cluster — 3+ nodes"]
    subgraph tshepo["Tshepo Local Enforcement Node"]
      LENV["Envoy — ext_authz ENABLED, unconditional header strip"]
      LPDP["tshepo-authz (local PDP)"]
      LOPA["OPA (local)"]
      BAG["Bundle Agent (NEW)<br/>fetch · verify signature · install · expiry alarm"]
      LAUD["tshepo-audit (local chain)"]
    end

    subgraph identity["Local identity"]
      LKC["Node Keycloak (MoHCC-managed)<br/>— or — institution IdP / AD / LDAP"]
      LTID["tshepo-identity (local)<br/>work-context + O-CPID + local ID block"]
      LKEYS["tshepo-keys (local)<br/>node signing key, bundle verification"]
      LIA["identity-assurance (local)"]
      LCONS["tshepo-consent (local) + Mvumo"]
    end

    subgraph clinical["Clinical estate — authoritative locally"]
      LBFF["experience-bff (node profile)"]
      PCT["PCT"]; OROS["OROS"]; INP["Inpatient"]; PHA["Pharmacy"]
      SUR["Surgery"]; PRO["Procedures"]; REF["Referral"]; FRM["Forms"]
      LBUT["Butano LOCAL projection<br/>the governed FHIR store for this node"]
      SCC[("Shared-Care Cache<br/>SEPARATE store · cohort-scoped<br/>provenance + freshness labelled")]
      PST["Professional Status<br/>rendered from the standing bundle"]
    end

    subgraph registrycache["Locally cached national authority (read-only)"]
      LVITO["VITO local instance<br/>+ allocated identifier block"]
      LVAR["VARAPI standing cache (bundle)"]
      LTUSO["TUSO facility cache (bundle) + local operational config"]
      LVASH["Vashandi (local rostering) + assignment bundle"]
      LZIBO["ZIBO terminology (bundle)"]; LCKP["CKP content (bundle)"]
    end

    subgraph money["Money (local capture)"]
      COSTA["Costa"]; LCOV["Coverage — offline eligibility tokens"]
    end

    subgraph data["Node data plane — HA"]
      PG[("PostgreSQL HA<br/>primary + replica + PITR")]
      KF[("Kafka — 3 brokers, RF=3")]
      RD[("Redis — persistent + replica")]
      MI[("MinIO — distributed")]
      OR[("Orthanc — local PACS")]
    end

    LFGW["Federation Gateway (node side)<br/>outbound + inbound durable queues"]
    OBS["Observability + capacity/health reporting"]
  end

  NC["National Core"]

  WEB & MOB --> LENV --> LBFF
  DEV --> clinical
  LENV --> LPDP --> LOPA
  BAG -.->|installs signed bundles| LPDP & LCONS & LVAR & LZIBO & LTUSO
  BAG -.->|standing bundle| PST
  LBFF --> clinical & identity & registrycache & money
  clinical --> PG & KF
  clinical --> LBUT
  LBUT --> PG
  SCC --> PG
  clinical --> MI & OR
  LFGW -->|consumes local outbox/Kafka| KF
  LFGW -->|"cohort request / revocation"| SCC
  LFGW <-->|"mTLS + signed envelopes"| NC
  BAG <-->|bundle fetch| NC
  OBS -->|"health · capacity · queue depth"| NC
  WEB -.->|"Open Full Impilo → new national session<br/>the node never receives it"| NC
```

**What the node does not have.** No national reporting stack, no NDR, no national surveillance authority, no MusheX national rails, no national Keycloak realm, no LiveKit SFU (unless the institution licenses one), no research extract capability — and, by doctrine rather than footprint, **no My Life, no Personal Health Record, no personal wellness and no Marketplace** (§11B.3). These are National Core responsibilities; the first group because centralising them is efficient, the second because rendering them inside an employer's installation would be wrong at any scale.

# 9. Tshepo distributed trust — target diagram

```mermaid
flowchart LR
  subgraph national["National Core"]
    SRC["Sources: policy_rule · VARAPI standing ·<br/>tshepo-consent · revocation · ZIBO · TUSO/Vashandi"]
    BP["Bundle Publisher"]
    CA["tshepo-keys — node CA + bundle signing (Ed25519)"]
    NPDP["National PDP (national-scope decisions)"]
  end

  subgraph node["Hospital Node — Tshepo Local Enforcement Node"]
    BA["Bundle Agent"]
    ST[("Bundle store<br/>version · issued_at · expires_at · signature")]
    LP["Local PDP (tshepo-authz)"]
    LO["Local OPA"]
    PEP["Envoy PEP + service-level PEPs"]
    WC["Work-context verifier"]
    BG["Local break-glass"]
    AUD["Local audit chain"]
  end

  SRC --> BP --> CA
  CA -->|"signed bundle + manifest"| BA
  BA -->|verify sig, check freshness| ST
  ST --> LP & WC
  PEP --> LP --> LO
  LP --> BG --> AUD
  LP --> AUD
  AUD -->|"chain-head attestation + disclosure records"| national
  BG -->|"break-glass record for review"| national
  WC -->|"locally minted, node-signed"| PEP
```

**The rule this diagram encodes:** the local PDP makes decisions from **local state only** — bundles, local consent captures, local work contexts. It never makes a synchronous call to the National Core to decide. Everything the current PolicyEngine reaches for over HTTP mid-decision (consent service, delegation, identity introspection, envelope signing) becomes either a node-local service or a signed bundle. That is the single change that makes seven-day autonomy possible.

---

# 10. Component-placement matrix

Legend for **Placement**: `NAT` national only · `LOC` hospital local · `BOTH` deployed both places · `CACHE` locally cached national authority · `PACK` optional hospital pack · `RETIRE` retired/consolidated · `REDESIGN` not suitable for federation without redesign.

| Component | Placement | Target role | Data authority | Local dependency behaviour | Federation behaviour | Code changes | Deployment changes | Reuse verdict |
|---|---|---|---|---|---|---|---|---|
| **Envoy** | BOTH | PEP at both edges; **ext_authz unconditionally on** | none | Node Envoy calls the local PDP only | none | Render ext_authz + header-strip outside the `if` guard; add node-identity header injection | New `envoy-node.yaml` template; `extAuthz.enabled` removed as a switch (becomes always-true) | **Repair** |
| **experience-bff** | BOTH | Composition layer; node profile has a reduced downstream set | none (no datasource) | Node BFF composes only node services + caches | Never calls the peer directly | Remove client-header trust (I1); derive context server-side; profile-driven client set; delete the 45 dead migrations | `values-hospital-node` env subset; Redis becomes persistent | **Repair** |
| **one-ui-shell** | BOTH | Same app, runtime-discovered endpoint | none | Node shell works fully offline-of-national | none | Replace build-time `NEXT_PUBLIC_*`/rewrite baking with runtime config (§16) | Node ingress; signed node config document | **Repair** |
| **Keycloak** | BOTH (separate realms, **never synchronised**) | National realm for national actors; node realm or institution IdP for local actors | Each realm authoritative for its own users | Node login works with national dark | Trusted-issuer registry + claim binding, not DB sync | Multi-issuer resource-server config; issuer registry client | Node Keycloak in the node profile; realm provisioning job | **Reuse, re-profile** |
| **tshepo-authz (PDP)** | BOTH | Local PDP is the decision point for local data | policy from bundles | **Must decide with zero national calls** | Publishes decision/audit upward | Replace mid-decision HTTP calls with bundle lookups; add bundle freshness gates; add trust-domain + node dimensions to the condition vocabulary | Node deployment + bundle volume | **Repair — the largest single work item** |
| **OPA** | BOTH | Parity today, enforcement later, at both tiers | policy bundles | Local sidecar | Bundles distributed with policy bundles | Keep the existing parity gate; add node-scope inputs | Node sidecar | **Reuse** |
| **tshepo-identity** | BOTH | National: CPID authority + Health-ID map. Node: work-context minting, O-CPID, local ID block | National for CPID map; node for its own tokens and O-CPIDs | Mints work-context tokens locally, signed by the node key | O-CPID reconciliation upward | Node mode: local signing key, allocation-block awareness; VITO URL must be configured (recovery defect) | Node deployment; fix the missing `VITO_SERVICE_URL` | **Split** |
| **tshepo-keys** | BOTH | National: node CA + bundle signing. Node: local signing + bundle verification | Each holds its own private material | Node signs work contexts, envelopes, local audit | Publishes JWKS; verifies national bundle signatures | Add node-CA issuance (CSR → cert); add bundle sign/verify API | Node deployment; KEK per node; institutional custody option | **Repair + extend** |
| **tshepo-consent** | BOTH | Node is authoritative for consent captured locally; national registry aggregates | **DUAL** | Local evaluate on the clinical read path | Consent directives federate FULL both ways | Add origin/version fields; add bundle export/import; fix the Redis catch-scope defect (a Redis blip currently denies estate-wide) | Node deployment | **Repair** |
| **Mvumo** | BOTH | Consent journey UX; materialises into tshepo-consent | none (journey state) | Works locally | Journeys stay local; directives federate | Retire the fail-safe-to-yes path (recovery §M21) | Node deployment | **Repair** |
| **tshepo-audit** | BOTH | Per-node hash chain; national holds chain-head attestations + disclosure records | Node authoritative for its chain | Local append always works | Chain heads + disclosure records upward; national never rewrites a node chain | Add `node_id` to the chain hash input; add attestation export; **add the six unhashed columns to the hash** (recovery §F.5) | Node deployment | **Repair** |
| **tshepo-offline** | REDESIGN → folded into Tshepo Local Enforcement | Its bundle/capability-token ideas become the Bundle Agent contract | — | — | — | Reuse `CapabilityTokenJwsVerifier` and the last-known-good JWKS cache; **delete the empty consent snapshot** (recovery §M10) | Not deployed as a standalone service | **Split then retire** |
| **identity-assurance** | BOTH | LOA ledger per domain | Node for local assurance events | ABIS binding degrades closed | Assurance summaries upward | Fix the missing `ABIS_BASE_URL` (recovery §M20) | Node deployment | **Reuse** |
| **VITO** | BOTH (**CACHE + local authority for local registrations**) | Person identity; node registers locally from an allocated identifier block | National for the Health-ID space; node for its own registrations until reconciled | Registration works fully offline | New persons contribute upward; national dedupe runs at the hub | **In-line dedup at registration** (recovery §H4); identifier-block allocation; origin fields | Node deployment | **Repair + extend** |
| **VARAPI** | NAT + CACHE | National provider/licence truth; node holds a signed standing bundle | **National only** | Standing bundle within staleness ceiling; then denies new authority | Standing bundles down; local privilege events up | Bundle export; standing-summary contract | No node deployment — bundle only | **Reuse (national)** |
| **TUSO** | NAT + CACHE + LOC(config) | National registry identity; node holds facility cache **plus** its own operational configuration | **DUAL** | Facility identity from cache; operational config local | Registry facts down; operational changes up as amendments | Split registry facts from operational config; add `trust_domain_id` + `governing_organisation_id`; fix the org-registry URL (recovery §M20) | Node deployment for the config half | **Split** |
| **Vashandi** | BOTH | Local rostering/attendance authority; assignments from bundle | Node for rosters; national for employment | Eligibility → `pending_backend` (existing honest behaviour retained) | Assignment state upward | Enable its Kafka events in Helm (currently unset) | Node deployment | **Reuse** |
| **workforce-governance** | NAT | Employment and governance truth | National | Bundle-cached at node | Employment bundles down | Bundle export | — | **Reuse (national)** |
| **PCT** | LOC (+ NAT for the national referral spine) | Care-continuum spine, authoritative locally | **Local** | Fully local | Encounter summaries + referrals upward | Federation metadata on all clinical writes; **referral state machine extended cross-node** (§15); enable listeners | Node deployment; listeners ON | **Reuse + extend** |
| **OROS** | LOC | Orders/results, authoritative locally | **Local** | Fully local; local LIMS/PACS adapters | Result summaries upward | Fix the two payload-contract breaks that silently drop review tasks and critical alerts (recovery §H14); node-scoped adapter config | Node deployment; set the integration base URLs | **Repair** |
| **Butano (butano-service)** | BOTH — **the single governed FHIR implementation** | Node: local projection of local clinical facts. National: longitudinal projection across nodes | Neither is a source of truth; both are projections with provenance | Local IPS/timeline from the local projection | Node → national via federation, Provenance-stamped | Node/national mode flag; provenance carries `origin_node_id`; ingestion from federation envelopes | Deployed both places | **Reuse — promote to the only FHIR store** |
| **butano-fhir** | **RETIRE** | — | — | — | — | Migrate inpatient's writes to the governed store; delete the service | Removed from all profiles | **Retire** (recovery §M6: it has no PII guard and receives personal names) |
| **stock hapi-fhir** | **RETIRE** | — | — | — | — | Repoint `FhirPublisher` and the gateway default target at the governed store; migrate the `hapi` database | Removed | **Retire** |
| **fhir-gateway** | BOTH | FHIR ingress PEP: consent + policy before any FHIR write; the federation receiver's FHIR arm | none | Local ingress for local systems | Validates federated FHIR on arrival | Fix the misplaced env (its config lives in the BFF's block); add federation-envelope awareness | Node + national | **Repair** |
| **Inpatient** | LOC | Ward/bed/theatre operations | **Local** | Fully local | Discharge summaries upward | Discharge summary must reach the governed store (currently emitted to a topic nobody consumes) | Node deployment | **Repair** |
| **Pharmacy** | LOC | Dispensing + stock | **Local** | Fully local | Dispense summaries upward | Node-scoped eLMIS config; retire the adapter shell or mark it absent | Node deployment | **Reuse** |
| **Surgery / Procedures** | LOC (PACK) | Theatre packs | **Local** | Fully local | Procedure summaries upward | Surgery must actually emit events (it emits none today) | Optional pack in the node profile | **Repair** |
| **Referral** | RETIRE-INTO-PCT (surgical slice retained) | Cross-node referral is PCT's, transported by the Federation Gateway | Local at origin | Queues when disconnected | Signed referral packages | Consolidate: PCT owns clinical referral; keep the surgical funnel | Node deployment | **Consolidate** |
| **ZIBO** | NAT + CACHE | Terminology + confidentiality vocabulary | **National** | Terminology bundle; LENIENT locally is acceptable, STRICT for federated writes | Bundles down | Bundle export | — | **Reuse (national)** |
| **Clinical Knowledge Platform** | NAT + CACHE | Clinical content and deterministic engines | **National** | Content bundle; engines run locally | Bundles down | Bundle export; the relay flag must be set | Node runs the engines from cached content | **Reuse** |
| **Forms** | NAT + CACHE (definitions) / LOC (responses in PCT) | Definitions national, responses local | National for definitions | Definition bundle | Definitions down | Bundle export; retire the phantom submissions endpoints (recovery §M) | Node deployment | **Repair** |
| **Costa** | LOC | Billing capture, authoritative locally | **Local** | Fully local | Charge summaries upward | Node-scoped tariff packs | Node deployment | **Reuse** |
| **Coverage** | NAT (+ CACHE) | Scheme/benefit truth national; **offline eligibility tokens** already exist | National | Offline eligibility token — a genuine existing asset | Claims upward | Reuse the token model as a template for other bundles | Node caches; claims queue | **Reuse — exemplar** |
| **MusheX** | NAT | Payer rails and settlement | National | Node queues claims | Claims/remittance | — | Not deployed at nodes | **Reuse (national)** |
| **Document Service** | BOTH | Local documents authoritative locally | **Local** | Fully local | INDEX_ONLY + on-demand fetch | **Encryption at rest; tenant-scope the delete; enforce scan status** (recovery §G.5) | Node deployment | **Repair** |
| **MinIO** | BOTH | Object storage per node | Local | Fully local | Never bulk-replicated | — | Distributed mode; backup | **Reuse** |
| **Orthanc** | LOC | Local PACS | **Local** | Fully local | Imaging metadata index up; pixels on demand | Node-scoped DICOM config | Node deployment | **Reuse** |
| **Kafka** | BOTH — **intra-node only** | Local event bus | — | Local | **Never a cross-site protocol** | — | 3 brokers RF=3 at the node | **Reuse, re-scope** |
| **Redis** | BOTH | Sessions, caches, idempotency | — | Local | none | — | **Persistent + replicated** (today it has no volume) | **Repair** |
| **Khuluma** | BOTH | Internal comms; local conversations local | Local | Fully local | Cross-node comms only via federation | Retire the never-published outbox or wire it | Node deployment | **Repair** |
| **Notification** | BOTH | Local sends via node-scoped providers | Local | **Node holds its own SMS/SMTP credentials** | none | **Add retry (FAILED is terminal today); retire `MockProvider` for unsupported channels** | Node deployment; per-node provider config | **Repair** |
| **LiveKit** | NAT (+ optional PACK) | National SFU; a node may license its own | — | Telemedicine degrades when national is dark | none | — | Optional node pack | **Reuse** |
| **Reporting** | NAT | National reporting | National | Node reports queue | Statutory + aggregate upward | **Fix the cross-schema report definitions that cannot execute** | Not at nodes | **Repair (national)** |
| **Surveillance** | NAT (+ local capture) | National notifiable authority | National | Local capture queues | Mandatory statutory channel | **PCT must actually set the `notifiable` marker** (recovery §H19) | National | **Repair** |
| **Data warehouse / NDR** | NAT | National analytics | National | — | Fed by federation, not by cross-DB reads | **Fix the topic-pattern mismatch that starves bronze**; remove the peer-DB JDBC pull | National | **Repair** |
| **offline-edge** | REDESIGN → absorbed | Its entitlement + replay model informs the Bundle Agent and the mobile sync contract | — | — | — | Harvest the working parts; retire the service | Removed | **Split then retire** |
| **offline-sync** | **RETIRE** | — | — | — | — | Delete — its replay is a simulation that marks work `SYNCED` without moving data | Removed | **Retire** |
| **jobs-service** | **RETIRE** | — | — | — | — | Delete — no scheduler, no executor, no callers | Removed | **Retire** |
| **channels-service** | **RETIRE** | — | — | — | — | Delete — marks messages `SENT` with no transport, and nothing calls it | Removed | **Retire** |
| **connector-fhir-adapter** | **REDESIGN** | Legacy-EHR relay, if still needed, becomes a node integration adapter | — | — | — | It records `RELAYED` with no HTTP client in the module — either implement or retire | — | **Repair or retire** |
| **Fleet & Release Service** | NAT (**new**) | Node registry, certificates, capabilities, releases, rings, health | National | — | The control plane for federation membership | New service | National only | **New** |
| **Federation Gateway** | BOTH (**new**) | The only cross-site path | — | Node side queues durably | The federation protocol | New service | Both | **New** |
| **Bundle Agent** | LOC (**new**) | Fetch, verify, install, alarm on staleness | — | The node's lifeline to policy truth | Pull-based, resumable | New sidecar/daemon | Node only | **New** |
| **wellness / simba** (personal health, device, wellness data) | **NAT only — never node-rendered** | The individual's personal-domain data | The individual | n/a at a node | Not federated to nodes; item-scoped disclosure only | Promote `provider_access_allowed=false` / `clinical_writeback_allowed=false` to enforced doctrine; add provenance classes (§19A.3) | National only | **Reuse, re-scope** |
| **msika / Marketplace, community, participation** | **NAT only — never node-rendered** | Personal marketplace and social | The individual | n/a | none | Refile the mis-zoned routes (§4A.3) | National only | **Reuse, re-scope** |
| **mushe-wallet / personal coverage views** | NAT (personal views) + LOC (facility billing capture) | Split by domain: the person's wallet is personal; the facility's charge capture is operational | Person / facility respectively | Facility capture works offline | Settlement summaries | Separate the personal audience from the operational one | Both, different audiences | **Split** |
| **learning-service (Fundo)** | NAT + CACHE (compliance verdict only) | National: full CPD and portfolio. Node: **only the mandatory-learning verdict** in the standing bundle | National | Verdict cached | Verdict down; completion up | Restrict the node-visible projection to the verdict | Bundle only | **Split** |
| **Shared-Care Cache** | LOC (**new store**) | Cohort-scoped national continuity for patients under care here | **Origin retains authority**; the cache is a projection | The reason it exists | Pull on cohort entry; suppress on revocation | New service/schema, separate from Node Butano (ADR-30) | Node only | **New** |
| **Node Bootstrap Agent** | LOC (**new**, transient) | Install, enrol, verify, hand over — then expire | — | Runs once per node lifecycle | Enrolment handshake only | New signed agent (§22B) | Node only, ephemeral | **New** |

**Four services are retired outright** (`offline-sync`, `jobs-service`, `channels-service`, `butano-fhir`) and **two are retired into others** (`tshepo-offline` into the Bundle Agent, `referral-service`'s clinical half into PCT). Every one of these is retired because the recovery proved it either fabricates success or has no callers — not because federation is inconvenient for it. Retiring them *before* federation prevents exporting those defects to every institution.

---

# 11. Identity and authentication architecture

## 11.1 The rule that replaces database synchronisation

**Keycloak databases are never synchronised.** Instead: *many trusted issuers, one claim-binding contract, one national identifier space.*

A node accepts tokens from a small set of registered issuers. Each issuer is registered nationally with its JWKS URI, its trust domain, and — critically — **what it is allowed to assert**. An institution's Active Directory can assert *who logged in*; it can never assert *that this person is a licensed prescriber*. That claim comes only from VARAPI, through a signed standing bundle.

```sql
trusted_issuer (
  issuer_id             UUID PRIMARY KEY,
  issuer_uri            TEXT NOT NULL UNIQUE,      -- the `iss` value
  trust_domain_id       UUID NOT NULL,
  issuer_type           VARCHAR(24) NOT NULL,      -- NATIONAL_KEYCLOAK | NODE_KEYCLOAK |
                                                    -- INSTITUTION_OIDC | INSTITUTION_SAML |
                                                    -- INSTITUTION_AD_LDAP_BRIDGE
  jwks_uri              TEXT NOT NULL,
  jwks_cache            JSONB NOT NULL,            -- last-known-good, for offline validation
  assertable_claims     TEXT[] NOT NULL,           -- e.g. {sub, email, groups, employee_id}
  identity_binding_mode VARCHAR(24) NOT NULL,      -- NATIONAL_BOUND | LOCAL_ONLY
  max_assurance_assertable VARCHAR(8) NOT NULL,    -- e.g. 'AAL2'
  status                VARCHAR(16) NOT NULL,      -- PENDING|ACTIVE|SUSPENDED|REVOKED
  registered_at, last_verified_at TIMESTAMPTZ
);

identity_binding (   -- the join between a local login and a national identity
  binding_id            UUID PRIMARY KEY,
  issuer_id             UUID NOT NULL,
  issuer_subject        TEXT NOT NULL,             -- the IdP's `sub`
  health_id             UUID NULL,                 -- VITO person — the national anchor
  provider_public_id    VARCHAR(26) NULL,          -- VARAPI provider
  trust_domain_id       UUID NOT NULL,
  binding_method        VARCHAR(32) NOT NULL,      -- IN_PERSON_PROOFED | BIOMETRIC |
                                                    -- NATIONAL_ID_VERIFIED | ADMIN_ASSERTED
  bound_at, bound_by, revoked_at,
  UNIQUE (issuer_id, issuer_subject) WHERE revoked_at IS NULL
);
```

## 11.2 Authoritative / cached / locally issued — stated exactly

| Credential or claim | Authoritative source | At the node it is… | Offline behaviour |
|---|---|---|---|
| Login credential (password, passkey, OTP) | The issuer that holds it (national Keycloak, node Keycloak, or institution IdP) | **Locally issued** when the node realm or institution IdP holds it | Works — this is why a node realm exists |
| `sub`, session, `acr`/`amr` | The authenticating issuer | Locally validated against cached JWKS | Works within the trust-anchor staleness ceiling |
| **Health ID** | **VITO (national)** | Cached in the identity binding; new persons minted from the node's allocation block | Registration works; national reconciliation queues |
| **CPID** | **tshepo-identity (national)** | Local O-CPID minted offline, reconciled on reconnect (existing mechanism) | Works |
| **Provider ID + licensure/standing** | **VARAPI (national) — never assertable by an IdP** | Signed standing bundle | Works to the standing ceiling; then no *new* clinical authority |
| Employment / post | workforce-governance (national) | Signed workforce bundle | As above |
| Facility assignment (roster) | **Vashandi at the node** | Locally authoritative | Works |
| **Work context** | **Minted at the node**, signed by the node key, proven against local assignment + standing bundle | Locally issued | **Works — this is the key change from today** |
| Assurance level (LOA) | identity-assurance in the acting trust domain | Local ledger | Works; ABIS-dependent elevation degrades closed |
| Consent directive | Whichever node captured it (DUAL) | Local capture + national bundle | Works; fail-closed past the 7-day ceiling |
| Revocation | National revocation feed | Signed revocation list | Works to 24 h, then high-authority actions deny |
| Node identity (mTLS + envelope signing) | Node CA in national tshepo-keys | Node holds the private key | Certificate valid until `not_after`; rotation queues |

## 11.3 Provider sign-in at a Hospital Node, with the national platform dark

```mermaid
sequenceDiagram
  autonumber
  actor P as Provider
  participant SH as one-ui-shell (node)
  participant BFF as experience-bff (node)
  participant IDP as Node Keycloak / institution IdP
  participant PDP as Local PDP
  participant BND as Bundle store
  participant TID as tshepo-identity (node)
  participant KEY as tshepo-keys (node)

  P->>SH: Open node URL (runtime-discovered endpoint)
  SH->>BFF: GET /internal/v1/auth/oidc/authorize (acr=aal2)
  BFF->>IDP: Authorization code + PKCE, acr_values=aal2
  IDP-->>BFF: code → tokens (validated against LOCAL JWKS)
  Note over BFF,IDP: No national call. The node realm or institution IdP is the issuer.
  BFF->>BFF: identity_binding: issuer sub → health_id / provider_public_id
  P->>SH: Choose work context (facility, ward, role)
  SH->>BFF: POST /internal/v1/work-context/session
  BFF->>BND: provider standing? (VARAPI bundle) · assignment? (Vashandi local) · revoked? (revocation list)
  alt Standing bundle fresh and provider not revoked
    BFF->>TID: mint work-context token
    TID->>KEY: sign (node key)
    KEY-->>TID: Ed25519 JWS
    TID-->>BFF: work_context token (jti, TTL, facility/ward/role/trust_domain/node)
    BFF-->>SH: session + work context
  else Standing bundle beyond hard ceiling
    BFF-->>SH: DENY — "provider standing cannot be verified (bundle stale N days)"
    Note over BFF,SH: Honest refusal. Not a silent allow, not a fabricated success.
  end
  P->>SH: Open patient record
  SH->>BFF: GET /internal/v1/summary/patient/{id} + work-context token
  BFF->>PDP: authorize (token-derived context ONLY — no client headers)
  PDP->>BND: policy bundle · consent bundle · sensitivity classification
  PDP-->>BFF: PERMIT + obligations (visibility tier, suppressed categories)
  BFF-->>SH: record, masked per obligations
```

**What changed from today, precisely.** Today the loose `X-Facility-ID` header travels unchallenged to every backend and the PDP is not on the path at all. Here, the work-context token is the *only* source of facility, ward, role and trust domain; Envoy strips the client's versions unconditionally; and the PDP is consulted on the clinical read itself.

## 11.4 Break-glass, offline

Break-glass remains the existing `tshepo-authz` lifecycle (reason mandatory, TTL, post-hoc review) with three federation changes: the request is **minted and stored locally**; the grant is bounded by the local consent bundle's emergency provisions rather than a national call; and the record is queued for review by the **trust domain's** reviewers (§6), with a copy to the National Core as a disclosure record when it reaches SPECIALLY_PROTECTED categories. The mobile fail-open (break-glass proceeding when its audit call fails) is **retired**: offline break-glass writes to the local audit chain first, and a break-glass that cannot be audited does not proceed.

## 11.5 Key and certificate rotation

| Material | Issuer | Rotation | Offline tolerance |
|---|---|---|---|
| Node mTLS client/server cert | National node CA (tshepo-keys) | 90 days, auto-renewed from 30 days before expiry | Federation stops at expiry; **local care unaffected** |
| Node envelope-signing key | Node-generated, CSR to national CA | 180 days, overlapping validity | Signed backlog remains verifiable via the overlap window |
| Bundle-signing key (national) | tshepo-keys | 180 days, published in JWKS with the previous key retained | Node keeps last-known-good JWKS; new bundles rejected if the key is unknown and the anchor is stale |
| Institution-custody signing key | Institution HSM/KMS | Institution's schedule, public half registered | Institution controls its own continuity |
| Realm client secrets | Per realm | 90 days | Local |

---

# 11A. National login experience **[T]**

## 11A.1 What the person sees

Entering through the national platform, a person authenticates **as a person** — never as an employee, never as a provider. Their available domains are then resolved:

```
MY LIFE  |  MY PROFESSIONAL  |  WORK
```

Each tab appears only if the person is eligible for it. The existing `SessionExperienceService` already computes exactly this three-tab model with a `defaultTab` rule (work if work-visible, else professional when the login method was `provider_id`, else personal). v1.1 keeps the model and the rule, and changes what backs it: each tab becomes an **audience-bound session** (§4B) rather than a visibility flag on one session.

## 11A.2 Resolution and selection

- **Health ID binding** is established at authentication from the `health_id` claim; **Provider ID** is resolved from VARAPI by Health ID, never asserted by the IdP (§4A P5).
- **Work contexts** are resolved by the existing six-source union and re-proved at mint. A person with several employers sees them grouped by organisation:

```
Parirenyatwa Group of Hospitals
  ├── Casualty — Medical Officer
  ├── Ward B — Consultant
  └── Clinical Governance — Reviewer
Sally Mugabe Central Hospital
  └── Virtual Specialist Service — Consultant
```

- **Selecting one mints a work session** and does not alter the personal or professional identity beneath it. Switching revokes the previous `jti` — two work contexts are never live at once.
- **Every context switch is audited** with the previous and new context, the proof source and the mode.
- **A person with no active workplace still has My Life and My Professional.** Work is absent, not broken — and it must not present as an error, since most citizens will never have it.

---

# 11B. Hospital Node login experience **[T]**

## 11B.1 What the node presents

The node identifies itself as what it is:

> **Impilo at Parirenyatwa Group of Hospitals**

After authentication the default experience is the **local Work context**, and the top-level navigation is deliberately narrower than the national one:

```
WORK  |  PROFESSIONAL STATUS  |  OPEN FULL IMPILO
```

### Work
The complete facility-scoped operational environment: patients, queues, wards, orders, results, theatre, pharmacy, local management, communications, facility workflows. Composed by the existing work-home family model — eight families, each with one adapter, the section list decided server-side.

### Professional Status — rendered from the standing bundle
The node shows only what it needs to establish safe authority to work: Provider ID, active registration, licence status, scope, restrictions, expiry, local assignment, required supervision, facility credentialing, mandatory local learning, **and the standing bundle's freshness**.

**This surface introduces no new data flow.** It renders the same signed provider-standing bundle that already authorises the work session (§12.1). Three properties follow for free: it works offline for as long as the bundle is valid; it is freshness-labelled by construction; and it cannot show more than the employer is entitled to, because the bundle is scoped to providers with assignments at this node and to the fields work authorisation needs.

The existing `ProfessionalAlertsComposer` is the model for its honesty: it emits licence, CPD and assignment alerts and **omits** the alert classes that have no wired source rather than approximating them. Professional Status inherits that rule.

### Open Full Impilo
A secure transition to the national platform for the full professional domain, My Life, Marketplace, personal wellness, personal payments and coverage, broader learning and regulatory self-service.

## 11B.2 Does full My Professional ever appear locally? **No. [D]**

Confirmed as doctrine, with one refinement to the brief's wording: the node may hold a **local professional-status projection**, scoped to institutional necessity and derived solely from the standing bundle. It may not hold, cache, clone or display the person's portfolio, CPD detail, regulatory correspondence, applications or career history — and a sovereign institution may not locally reconstruct the national professional domain from its own records. The distinction is *what authorises this person to work here* (node) versus *this person's professional life* (national, individual-facing).

## 11B.3 Does My Life ever appear locally? **No. [D]**

Not as a tab, not as an embedded view, not as a cached summary, and **not as a locally-built substitute during disconnection**. The node offers `Open Personal Impilo`, which opens a new audience-bound national session the hospital cannot inspect.

During a national outage: Work continues locally; Professional Status continues from the standing bundle with its freshness shown; **My Life and Marketplace are unavailable and are shown as unavailable.** Fabricating a local personal experience would be exactly the false-success pattern the recovery catalogued and Phase 0 removes.

## 11B.4 Why this matters beyond privacy

A provider is simultaneously an employee, a professional and a private individual who is also a patient. Collapsing those into one employer-operated session does not merely risk a data leak — it makes it impossible for a clinician to seek care, manage a condition, or correspond with their regulator without their employer being architecturally capable of watching. The separation is a condition of the platform being usable by the people who staff it.

---

# 11C. Context-transition sequences **[T]**

**1 — National login → Work context**

```mermaid
sequenceDiagram
  autonumber
  actor P as Person
  participant SH as Shell (national)
  participant BFF as experience-bff
  participant KC as National Keycloak
  participant WC as WorkContextResolution
  participant TID as tshepo-identity
  P->>SH: national URL
  SH->>KC: OIDC + PKCE (acr=aal2 if work intent)
  KC-->>BFF: tokens → personal session (aud impilo-personal)
  BFF->>BFF: SessionExperienceService → tabs {life, professional, work}
  P->>SH: choose WORK → organisation → facility/ward/role
  SH->>BFF: POST /internal/v1/work-context/session
  BFF->>WC: re-prove contextId against source (never cached)
  BFF->>TID: mint work token (aud impilo-work:<node>, 900s)
  TID-->>SH: work session; previous jti revoked
```

**2 — Hospital Node login → local Work**

```mermaid
sequenceDiagram
  autonumber
  actor P as Provider
  participant NS as Node shell
  participant NB as Node BFF
  participant IDP as Node IdP / institution IdP
  participant BND as Standing + assignment bundles
  participant NID as Node tshepo-identity
  P->>NS: node URL → "Impilo at <Institution>"
  NS->>IDP: OIDC + PKCE (local JWKS)
  IDP-->>NB: tokens (issuer registered, binding checked)
  NB->>BND: standing fresh? assignment active? revoked?
  alt authorised
    NB->>NID: mint work session (node-signed)
    NB-->>NS: WORK (default) | PROFESSIONAL STATUS | OPEN FULL IMPILO
  else standing bundle past hard ceiling
    NB-->>NS: refuse, stating the bundle age — no silent allow
  end
```

**3 — Node → Full My Professional**

```mermaid
sequenceDiagram
  autonumber
  actor P as Provider
  participant NS as Node shell
  participant NB as Node BFF
  participant BR as National identity broker
  participant KC as National Keycloak
  P->>NS: Open Full Impilo → My Professional
  NS->>NB: request transition
  NB->>BR: handoff intent {issuer, subject, node_id}
  BR->>BR: issuer registered? identity_binding_mode? institution allows handoff?
  alt NATIONAL_BOUND and handoff enabled
    BR->>KC: brokered authentication (fresh, assurance re-evaluated)
  else LOCAL_ONLY or handoff disabled
    BR->>KC: full re-authentication required
  end
  KC-->>P: professional session (aud impilo-professional)
  Note over NB,P: The node never receives this session. The work token was<br/>not exchanged — it was left behind.
```

**4 — Node → My Life** — identical to 3, terminating in `aud impilo-personal`. The node is told only "transition completed"; never the session, never its contents.

**5 — National → Hospital Node work context**

```mermaid
sequenceDiagram
  autonumber
  actor P as Provider
  participant NAT as National shell
  participant DIR as Federation directory
  participant NODE as Node
  P->>NAT: choose a facility whose primary node is NODE-X
  NAT->>DIR: resolve facility → node endpoint + signed node config
  NAT-->>P: hand off to the node's own endpoint
  P->>NODE: authenticate at the node (its issuer)
  NODE->>NODE: mint local work session
  Note over NAT,NODE: The national session is NOT carried into the node.<br/>Clinical work happens under node-issued authority, always.
```

**6 — Returning from personal/professional to local Work** — the node work session is resumed if still within its 900-second TTL, otherwise re-minted from the bundles. The personal session persists independently in its own tab and is not revoked by returning.

**7 — One provider, two nodes**

```mermaid
sequenceDiagram
  autonumber
  actor P as Provider
  participant A as Node A
  participant B as Node B
  P->>A: work session (aud impilo-work:NODE-A)
  P->>B: work session (aud impilo-work:NODE-B)
  Note over A,B: Distinct audiences, distinct issuers, distinct jti.<br/>A's token presented to B is rejected on audience — not downgraded.
  Note over P: Both may be live at once (different institutions,<br/>different sessions). Within ONE node, switching context still<br/>revokes the previous jti.
```

The one-live-context rule is **per node**, not per person: a consultant may hold a session at each of two hospitals simultaneously, because they are two employments in two institutions. What is forbidden is two contexts at the same node, because that is one duty with two answers.

**8 — National disconnection during an active local work session**

```mermaid
sequenceDiagram
  autonumber
  participant P as Provider (mid-consultation)
  participant NODE as Node
  participant NAT as National Core
  NODE--xNAT: connectivity lost
  Note over NODE: Work session continues — it was node-issued and node-verified.
  P->>NODE: continue clinical work → writes locally, queues federation
  NODE-->>P: banner: national services unavailable since T; bundle ages shown
  P->>NODE: Open Full Impilo
  NODE-->>P: unavailable — stated plainly, no local substitute
  Note over NODE: At each bundle's hard ceiling the affected decision class<br/>fails closed (§5.3). Work does not silently degrade.
```

---

# 12. Tshepo distributed enforcement

## 12.1 The bundle set

Five signed bundles plus one manifest. All are **Ed25519-signed by the national bundle-signing key** (an extension of the existing `tshepo-keys` purpose-scoped signing), delivered as content-addressed, resumable, compressed artefacts over the federation transport.

```jsonc
// bundle-manifest.json — fetched first, tiny, tells the agent what to pull
{
  "manifest_version": "1",
  "issued_at": "2026-08-03T04:00:00Z",
  "issuer": "impilo-national-core",
  "node_id": "…",                       // bundles are node-scoped: a node gets only its own
  "trust_domain_id": "…",
  "bundles": [
    { "type": "policy",        "version": "2026.08.03-1", "sha256": "…", "size": 184320,
      "expires_at": "2026-09-02T04:00:00Z", "hard_ceiling_days": 30 },
    { "type": "standing",      "version": "…", "sha256": "…", "expires_at": "…", "hard_ceiling_days": 14 },
    { "type": "consent",       "version": "…", "sha256": "…", "expires_at": "…", "hard_ceiling_days": 7 },
    { "type": "relationship",  "version": "…", "hard_ceiling_days": 14 },
    { "type": "revocation",    "version": "…", "hard_ceiling_days": 1 },
    { "type": "terminology",   "version": "…", "hard_ceiling_days": 90 }
  ],
  "signature": { "alg": "Ed25519", "kid": "impilo-bundle-2026-Q3", "value": "…" }
}
```

| Bundle | Contents | Scope | Source |
|---|---|---|---|
| **policy** | `policy_rule` rows for the node's trust domain and facility set; the closed condition vocabulary version; obligation templates; the OPA rego bundle | Node's trust domain + assigned facilities | `tshepo_authz.policy_rule` (existing table, existing 68 migrations of seeded packs) |
| **standing** | For each provider with an assignment at the node's facilities: provider public id, registration status, licence validity window, scope-of-practice codes, supervision requirement, suspension flags | Only providers relevant to this node | VARAPI |
| **consent** | Directives for patients with a local record or an open episode: subject CPID, scope, permit/deny, period, category restrictions, version | Node's patient cohort | tshepo-consent (national registry) |
| **relationship** | Delegations, guardianships, proxies, care-team relationships that bear on access | Node's cohort | Mvumo delegations + PCT care teams |
| **revocation** | Revoked providers, revoked credentials, revoked work contexts (jti list), revoked node certs, suspended trust domains | Global, small, frequent | Aggregated nationally |
| **terminology** | ZIBO artefacts, confidentiality category vocabulary, CKP content packs, forms definitions, order sets | National, versioned | ZIBO / CKP / forms |

**Cohort scoping is a privacy control, not an optimisation.** A node receives consent and relationship data only for patients it has a lawful relationship with (an open episode, a local record, or an inbound referral). This is enforced at the publisher, and the bundle's cohort definition is itself recorded as a disclosure.

## 12.2 What is decidable offline

| Decision class | Offline? | Basis |
|---|---|---|
| Local clinician reads a local record for a local patient | **Yes** | policy + consent + relationship bundles + local work context |
| Local clinician writes a clinical fact | **Yes** | policy + standing bundle |
| Prescribing (ordinary) | **Yes** | policy + standing |
| Prescribing controlled drugs / high-authority actions | **Yes, if revocation list < 24 h**; otherwise **deny** | revocation freshness gate |
| Access to SPECIALLY_PROTECTED categories | **Yes, with a fresh consent bundle**; otherwise **emergency path only** | consent bundle, confidentiality ENFORCE |
| Delegated (guardian/proxy) access | **Yes** within the relationship ceiling; then **deny** | relationship bundle |
| Break-glass | **Yes**, with local audit written first and post-hoc review queued | local |
| Cross-institution record access | **No — fail closed** | requires a live agreement evaluation |
| National record contribution | **No — queue** | federation |
| New provider activation | **No — deny with a stated reason** (`pending_backend`) | standing is national |
| Consent capture | **Yes** — captured locally, authoritative locally, federated on reconnect | DUAL authority |
| Consent *revocation* honoured | **Yes locally, immediately**; nationally on reconnect | local capture wins locally |

## 12.3 Behaviour at each ceiling

```mermaid
flowchart LR
  F["Fresh<br/>(within refresh)"] -->|"refresh missed"| S["Soft ceiling<br/>WARN"]
  S -->|"continues"| H["Hard ceiling"]
  H --> D1["Deny new authority classes"]
  H --> D2["Emergency-only for consent-gated"]
  H --> D3["Banner in every affected UI surface"]
  H --> D4["Every write flagged bundle_stale=true"]
  D4 --> R["On reconnect: flagged writes are<br/>listed for clinical governance review"]
```

The UI banner is not decoration. A clinician must be able to see, without asking anyone, that the node is operating on a policy picture of a known age — and the record must carry that fact forever, because a decision made on stale authority is a different clinical artefact from one made on fresh authority.

## 12.4 Eliminating browser-authoritative headers

| Header today | Target |
|---|---|
| `X-Tenant-ID` | **Deleted from the client contract.** `trust_domain_id` is a work-context token claim, derived server-side |
| `X-Facility-ID`, `X-Department-ID`, `X-Ward-ID`, `X-Workspace-ID`, `X-Programme-ID`, `X-Shift-ID` | **Deleted from the client contract.** All become work-context token claims; Envoy strips any inbound copy unconditionally; the PDP regenerates them downstream (the existing `ContextHeaderAuthority`, promoted from `PASSTHROUGH` to `REGENERATE`) |
| `X-Actor-ID`, `X-Actor-Type`, `X-Provider-ID` | Derived from the verified token (the existing `ActorContextFilter` override becomes the only source; the client no longer sends a "hint") |
| `X-Purpose-Of-Use` | Retained as a client *declaration* — but it selects among purposes the work context already permits, and an unpermitted purpose is refused, not trusted |
| `X-Subject-ID` (delegation) | Retained as a declaration, validated against the relationship bundle |
| `X-Work-Context-Token` | **The only client-supplied authority artefact** — signed, short-TTL, revocable, node-issued |
| Visibility obligation headers (11) | **Never client-supplied.** Minted by the PDP, stripped unconditionally at the edge — which finally makes it safe to turn on obligation propagation (today it is off precisely because the edge does not strip) |

`services/shared-core/.../auth/TrustContext.java` is the seam: today it is a 10-field record read from headers (`tenantId`, `actorId`, `actorType`, `purposeOfUse`, `deviceFingerprint`, `correlationId`, `facilityId`, `workspaceId`, `shiftId`, `mode`). It becomes a record built from **verified token claims**, extended with `trustDomainId`, `nodeId`, `organisationId`, `jurisdictionId`, `workContextId`, `assuranceLevel` and `consentBasis`, with a construction path that **cannot** be reached from raw headers. Because every service uses this one type, changing it changes the whole estate at once — that is the point.

---

# 13. Federation metadata, provenance and identifiers

## 13.1 The common metadata block

```jsonc
"federation": {
  "trust_domain_id":   "uuid",
  "organisation_id":   "uuid",
  "facility_id":       "uuid",      // TUSO facility_uuid — unchanged from today
  "node_id":           "uuid",      // the node processing
  "origin_node_id":    "uuid",      // the node that CREATED the fact — never rewritten
  "origin_record_id":  "uuid",      // the record's identity in its origin node
  "global_event_id":   "uuid",      // v7 UUID — globally unique, time-ordered
  "origin_sequence":   9007,        // monotonic per (origin_node_id, stream)
  "record_version":    3,           // monotonic per origin_record_id
  "occurred_at":       "2026-08-03T09:14:00Z",   // clinical time
  "recorded_at":       "2026-08-03T09:14:07Z",   // system time
  "author_provider_id":"VARAPI public id",
  "subject_cpid":      "uuid",
  "sensitivity":       "ROUTINE|RESTRICTED|SPECIALLY_PROTECTED:<category>",
  "purpose_of_use":    "TREATMENT|...",
  "consent_basis":     "CONSENT|LEGAL_OBLIGATION|VITAL_INTEREST|BREAK_GLASS|CARE_TEAM",
  "schema_version":    "1.2.0",
  "integrity_signature": { "alg": "Ed25519", "kid": "node-PARI-01-2026Q3", "value": "…" }
}
```

**Three fields do the heavy lifting.** `origin_node_id` + `origin_record_id` + `record_version` together make cross-node last-write-wins *impossible to express*: a write whose `origin_node_id` is not the receiver's own is only ever an append of a new version, and a version that is not `current + 1` for that origin record is a conflict, not a winner (§18).

## 13.2 Where it lands

| Surface | Mapping |
|---|---|
| **Request context** | `TrustContext` gains `trustDomainId`, `nodeId`, `organisationId`, `jurisdictionId`, `workContextId` (§12.4) |
| **JWT / work-context token** | Claims `tdid`, `nid`, `fid`, `oid`, `jid`, `wcid`, `aal`, plus the existing role/mode claims |
| **Kafka events** | The existing `EventEnvelope` already mandates `pod_id` — it is **renamed/extended to the full block**, which is the cheapest possible landing because the envelope type already exists and is shared |
| **Outbox tables** | Add `federation_meta JSONB NOT NULL` + generated columns for `origin_node_id`, `global_event_id`, `origin_sequence` (indexed). One migration shape, applied per service |
| **Audit events** | `tshepo_audit.audit_event` gains the block; the chain hash input is extended to cover it **and the six currently-unhashed columns** |
| **FHIR `Provenance`** | One `Provenance` per contributed resource: `agent.who` → author provider, `agent.onBehalfOf` → organisation, `entity.what` → origin record, `recorded` → `recorded_at`, extension `origin-node` → `origin_node_id` |
| **FHIR `meta.source`** | `urn:impilo:node:{origin_node_id}:{origin_record_id}` — replacing today's tenant-scoped `urn:impilo:butano:{tenantId}` |
| **FHIR identifiers/tags** | `identifier` gains system `https://impilo.gov.zw/origin-record`; `meta.tag` gains trust-domain, facility and sensitivity tags (extending the existing tenant-tag mechanism, which already works) |
| **Documents** | `document.federation_meta`; the object key becomes `{trust_domain}/{node}/{object_id}/{filename}` |
| **Images** | DICOM: `IssuerOfAccessionNumberSequence` + private tags for origin node; the index record carries the block |
| **Referrals** | The signed referral package **is** an envelope with this block as its header (§15) |
| **Orders/results** | Order and result identifiers become `(origin_node_id, origin_record_id)` pairs in federated references |

## 13.3 Identifier migration — what changes and what does not

The recovery established that ~90 services use `GenerationType.IDENTITY` bigserial keys, and that four things are hard merge blockers. The migration is deliberately **additive**, not a re-keying.

| Identifier | Verdict | Action |
|---|---|---|
| **Local database primary keys** (bigserial, ~90 services) | **Stay local, forever** | No change. They are never exposed in a federated reference |
| Health ID (UUID v4) | Already federation-safe | Add allocation-block awareness for offline minting |
| CPID (UUID v4) | Safe for uniqueness; the *mapping* is central | Keep central minting; O-CPID for offline; reconcile (existing rail) |
| **Public Impilo ID** (9 random digits + check digit) | **BLOCKER — collides across issuers** | **Add a node/issuer discriminator to the issuance scheme** (an allocation-block prefix), and issue only from a nationally allocated block per node. Existing IDs remain valid |
| Provider ID (26 hex chars) | Safe | Keep |
| Facility ID | `facility_uuid` safe; bigserial local | Use `facility_uuid` in all federated references — never the serial |
| Journey / order / result (ULID) | Safe | Promote to the federated reference; strengthen the RNG (currently `java.util.Random`) |
| Encounter | Row PK bigserial, external `encounter_ref` UUID exists | Use `encounter_ref` |
| Referral, consent (UUID) | Safe | Keep |
| **Audit event** (bigserial + per-tenant chain) | **BLOCKER for merge — by design** | Do not merge. **Per-node chains stay per-node**; the National Core stores signed chain-head attestations, not interleaved events (§18.4) |

**The migration path that avoids a big-bang re-key**: every federated entity gains a nullable `origin_record_id UUID` populated on write and backfilled once; a unique index on `(origin_node_id, origin_record_id)`; federated references use that pair exclusively. Local joins keep using the serial. A service is "federation-ready" when its outbound contract contains no serial — provable by a contract test, not by inspection.

**Measured identifier state (verified for this design).** Seven domains are *already* federation-safe and need no new column: PCT journey and OROS order (ULID primary keys), OROS result, referral and consent (UUID primary keys), VITO client (`health_id` unique UUID), TUSO facility (`facility_uuid` unique, immutable, and whose javadoc already states its purpose is exactly this), VARAPI provider (two candidates — `provider_ref` UUID and `provider_public_id` ULID; §20 records the open ruling on which is canonical). **Exactly one high-value gap exists: the PCT encounter.** Its primary key is a bigserial and its `encounter_ref UUID` column is nullable and carries no unique constraint — yet it is the anchor that OROS orders reference and that the care-continuum admission handshake depends on. Making `encounter_ref` `UNIQUE NOT NULL` is therefore the single highest-leverage identifier change in the programme.

## 13.4 The three request contexts — the real seam, and a trap

The estate does not have one request context. It has **three**, and a federation field added to only one is invisible to two-thirds of the estate:

| Context object | Used by | Fields today | Verdict |
|---|---|---|---|
| `services/shared-core/.../auth/TrustContext.java` | **70 services** construct its filter | the 10-field record above; header constants inlined | **The primary landing site.** Its filter is the *sole* caller of the canonical constructor, so adding record components is source-compatible for all 70 consumers |
| `libs/tech-companion/.../context/RequestContext.java` | **83 services** read its holder | `tenantId, podId, requestId, correlationId, authToken, principal, clientTimeoutMs` — **it already has `podId`** and an `isNationalPod()` helper | **The second landing site.** `node_id` is a refinement of the existing `podId`; `trust_domain_id` is new |
| `libs/tshepo-sdk/.../TrustContext.java` | **zero consumers** | field-identical to shared-core's, fail-closed filter | **Retire.** Its fail-closed filter is the better design and should be harvested into shared-core, whose filter is fail-open (`parseUuid` returns null on garbage) |

There are likewise **three** header-constant classes (`libs/tshepo-contracts/.../TrustHeaders.java` ~45 constants, `libs/tech-companion/.../CompanionHeaders.java` ~40, `services/shared-core/.../TrustHeaders.java` 19). The contracts one *declares* itself the single source of truth and lists four mirrors — but omits shared-core's, and shared-core's `TrustContext` inlines a fourth copy anyway. **Consolidating to one generated constant set is a Phase 1 prerequisite**, not a tidy-up: a federation whose header vocabulary has four definitions cannot have one contract test.

> **⚠ The `pod_id` landmine.** `pod_id` already exists in `EventEnvelope`, in four of six sampled outbox tables, and as part of the `idempotency_keys` primary key in both FHIR services. But the SQL default written in ≥12 migrations is the literal **`'national-spine'`**, while `FederationAuthority.NATIONAL_POD_ID` in Java is **`"national"`**. *These two strings do not match*, so `requireNational()` evaluated against a database-defaulted `pod_id` denies. Any node work that leans on the existing `pod_id` plumbing must reconcile these two literals in the same change — and `OutboxEventBuilder` fills the string `"unknown"` when the value is absent, which is precisely the fabrication pattern §1.3(7) forbids for `origin_node_id`.

## 13.5 Why the event contract cannot be changed in one place

`EventEnvelope` is a shared record with a **hand-written `LinkedHashMap` serializer** in `CompanionOutboxPublisher.serializeEnvelope()` — a new field added to the record but not to that method silently never reaches Kafka. More consequentially:

- **32 services extend `CompanionOutboxPublisher`** and therefore emit real envelopes.
- **33 services hand-roll their own publisher** — and they include **pct, oros, pharmacy, referral, tshepo-consent and tshepo-identity**, six of the highest-value clinical and trust services in the estate. PCT's publisher sends `event.getPayload()` **raw**, with no envelope at all, routed by a 128-line string `switch` with a `pct.events` catch-all.

**Therefore:** adding federation metadata to `EventEnvelope` reaches *none* of the six services whose events matter most for federation. The migration plan (§17, Phase 1) makes converting those six publishers to `CompanionOutboxPublisher` a **hard prerequisite** for Phase 3, and treats it as a correctness fix rather than a refactor — it also closes PCT's unrouted-event catch-all in the same pass.

The outbox tables are equally non-uniform — six services, six shapes: the payload column is `payload` in five and `payload_json` in coverage; `tenant_id` is `UUID` in four and `TEXT` in two; `schema_version` is `INT` in three and `VARCHAR(16)` in coverage. Coverage is the only service with a natural `event_id UUID` (the obvious `global_event_id` carrier) and the only one enforcing `UNIQUE (idempotency_key)`. **Coverage's outbox is therefore adopted as the canonical target shape**, and the federation migration normalises the others toward it rather than inventing a new one.

---

# 14. Impilo Federation Gateway

## 14.1 What it is, and what it is explicitly not

A Spring Boot service deployed at both ends, speaking **one governed protocol over mTLS HTTPS**. Local Kafka and local outbox tables feed the node-side gateway; **they never cross the site boundary**. Calling a Kafka topic a federation protocol is prohibited by §1.4 — the topic is the local plumbing behind the gateway, not the contract between institutions.

## 14.2 The envelope

```jsonc
{
  "envelope_id": "uuid-v7",
  "envelope_type": "CLINICAL_CONTRIBUTION | REFERRAL | REFERRAL_RESPONSE | CONSENT_DIRECTIVE |
                    DISCLOSURE_RECORD | AUDIT_ATTESTATION | STATUTORY_REPORT |
                    ON_DEMAND_REQUEST | ON_DEMAND_RESPONSE | BUNDLE_ACK | NODE_HEARTBEAT |
                    SHARED_CARE_COHORT_REQUEST | SHARED_CARE_COHORT_RESPONSE |
                    SHARED_CARE_REVOCATION | PHR_DISCLOSURE | SUPPORT_SESSION_RECORD",
  "schema_version": "1.2.0",
  "federation": { /* the §13.1 block */ },
  "routing": {
    "from_node_id": "…", "from_trust_domain_id": "…",
    "to_node_id": "…|NATIONAL_CORE", "to_trust_domain_id": "…",
    "agreement_id": "…",            // the federation_agreement authorising this transfer
    "policy_version": "…",          // the data_sharing_policy version evaluated
    "priority": "EMERGENCY | URGENT | ROUTINE | BULK"
  },
  "disclosure": {
    "basis": "CONSENT | LEGAL_OBLIGATION | VITAL_INTEREST | BREAK_GLASS | CARE_TEAM",
    "consent_ref": "…|null",
    "redaction_profile": "…",       // what was withheld, by category
    "withheld_categories": ["MENTAL_HEALTH"]   // declared, so the receiver knows the view is partial
  },
  "payload": { "content_type": "application/fhir+json | application/json",
               "inline": { }, "attachments": [ { "attachment_id":"…","sha256":"…","size":123,
                                                  "fetch_url":"…","encryption":"…" } ] },
  "signature": { "alg": "Ed25519", "kid": "node-PARI-01-2026Q3", "value": "…" }
}
```

**`withheld_categories` is a deliberate design choice.** A receiving clinician who sees a partial record must be told it is partial. Silent redaction is a patient-safety hazard: a clinician who believes they are looking at a complete medication list will prescribe on it.

## 14.3 Transport and queues

```mermaid
sequenceDiagram
  autonumber
  participant SVC as Node service (PCT/OROS/…)
  participant OBX as Local outbox / Kafka
  participant NFG as Node Federation Gateway
  participant CFG as Core Federation Gateway
  participant DIS as Disclosure engine
  participant PRJ as National projection (Butano/DWH)

  SVC->>OBX: write record + outbox row (one transaction)
  OBX-->>NFG: relay reads local outbox (in-node only)
  NFG->>DIS: evaluate data_sharing_policy + agreement + consent
  alt Disclosure permitted
    NFG->>NFG: build envelope, redact, sign (node key), enqueue outbound (durable)
    NFG->>CFG: POST /federation/v1/envelopes (mTLS, batched, bandwidth-aware)
    CFG->>CFG: verify cert + signature + schema + agreement + sequence
    alt Accepted
      CFG->>PRJ: apply as a NEW VERSION (never an overwrite)
      CFG-->>NFG: ACK {envelope_id, applied_sequence}
      NFG->>NFG: mark sent; advance the per-stream watermark
    else Rejected (schema/policy/integrity)
      CFG-->>NFG: NACK {reason_code, retryable}
      NFG->>NFG: retryable → backoff; terminal → DLQ + operator surface
    end
  else Disclosure denied
    NFG->>NFG: record a non-disclosure decision (auditable, with the reason)
    Note over NFG: The record stays local. The refusal is visible, not silent.
  end
```

| Property | Mechanism |
|---|---|
| **Durability** | Both ends persist to PostgreSQL before acknowledging anything (`fed_outbound_envelope`, `fed_inbound_envelope`). Nothing lives only in memory or only in Kafka |
| **Idempotency** | `envelope_id` (UUID v7) unique-constrained at the receiver; a replay is acknowledged, not re-applied |
| **Deduplication** | `(origin_node_id, origin_record_id, record_version)` unique at the receiver |
| **Sequencing** | `origin_sequence` monotonic per `(origin_node_id, stream)`; the receiver tracks a watermark and **detects gaps**, requesting a replay range rather than silently accepting out-of-order clinical history |
| **Retry** | Exponential backoff with jitter, priority-aware: EMERGENCY retries aggressively, BULK yields |
| **DLQ** | `fed_dead_letter` with the reason code, the full envelope and an operator replay action — modelled on Costa's money DLQ, the recovery's best-in-estate failure handling |
| **Quarantine** | Signature failure, unknown certificate, schema violation or an agreement that is not ACTIVE → the envelope is quarantined and the node's `node_connection` state moves to `QUARANTINED`. **Quarantine never stops local care** |
| **Bandwidth awareness** | Priority queues, a configurable per-node rate ceiling, compression, delta-only contribution, and attachments transferred **out-of-band by reference** (§14.4) |
| **Replay after ≥7 days** | The watermark is per stream, so reconnection is a resumable range request, not a full resend. §18 covers the ordering and conflict rules |
| **Selective disclosure** | Two independent evaluations — sender-side before signing, receiver-side before applying. Divergence between them is itself an alert |

## 14.4 Large objects: documents and images

Pixel data and documents are **never** inlined. The envelope carries an attachment descriptor; the receiver fetches on demand through an authenticated, time-boxed, resumable channel (HTTP range requests; DICOMweb WADO-RS for imaging), subject to a fresh policy evaluation *at fetch time*. This is what makes `INDEX_ONLY` and `ON_DEMAND` in §4.2 implementable, and it keeps a single CT study from blocking a referral queue on a constrained link.

## 14.5 Gateway schema (both ends)

```sql
fed_outbound_envelope (envelope_id UUID PK, envelope_type, to_node_id, agreement_id,
  priority, federation_meta JSONB, payload JSONB, attachments JSONB,
  origin_sequence BIGINT NOT NULL, state VARCHAR(24) NOT NULL,  -- PENDING|SENDING|SENT|ACKED|
                                                                 -- NACKED|DLQ|SUPPRESSED
  attempts INT, next_attempt_at, last_error TEXT, created_at, sent_at, acked_at,
  UNIQUE (to_node_id, origin_sequence, envelope_type));

fed_inbound_envelope (envelope_id UUID PK, from_node_id, received_at, verified_at,
  state VARCHAR(24),                                            -- RECEIVED|VERIFIED|APPLIED|
                                                                 -- REJECTED|QUARANTINED|CONFLICT
  origin_sequence BIGINT, applied_at, reject_reason, raw JSONB,
  UNIQUE (from_node_id, envelope_id));

fed_stream_watermark (peer_node_id, stream VARCHAR(64), last_applied_sequence BIGINT,
  gap_detected_at, PRIMARY KEY (peer_node_id, stream));

fed_disclosure_record (disclosure_id UUID PK, direction, envelope_id, subject_cpid,
  data_domain, basis, agreement_id, policy_version, withheld_categories TEXT[],
  actor_provider_id, occurred_at);   -- the institution's disclosure dashboard reads this

fed_dead_letter (…, reason_code, envelope JSONB, replayable BOOLEAN, resolved_at, resolved_by);
```

`fed_disclosure_record` is written on **both** sides of every transfer. It is the evidentiary backbone of §6's mandatory disclosure dashboard: an institution can prove what left, and the National Core can prove what it received and why it was entitled to it.

---

# 15. Referral federation

The current model — a receiving facility discovers a referral only by polling the same PCT database, with no events emitted and no notification — is replaced. PCT keeps the state machine and gains a transport.

```mermaid
sequenceDiagram
  autonumber
  participant Ref as Referring clinician (Node A)
  participant PA as PCT (Node A)
  participant GA as Gateway A
  participant CORE as National Core (directory + routing)
  participant GB as Gateway B
  participant PB as PCT (Node B)
  participant Rec as Receiving clinician (Node B)

  Ref->>PA: Create referral (subject, urgency, clinical question, attachments)
  PA->>PA: State SUBMITTED · consent/legal basis recorded · package assembled + signed
  PA->>GA: outbox → REFERRAL envelope
  GA->>CORE: resolve destination facility → node (federation directory)
  CORE-->>GA: to_node_id (+ agreement_id for cross-domain)
  GA->>GB: signed REFERRAL envelope (priority = urgency)
  GB->>PB: apply → inbound referral, state RECEIVED
  GB-->>GA: DELIVERY ACK
  GA->>PA: state DELIVERED  ← visible to the referrer
  Note over Ref,PA: "Delivered to Node B at 09:14" — a human-visible delivery status,<br/>which today does not exist in any form.
  Rec->>PB: Accept / Reject / Request information
  PB->>GB: REFERRAL_RESPONSE envelope
  GB->>GA: response
  GA->>PA: state ACCEPTED | REJECTED | INFO_REQUESTED
  opt Images or documents needed
    Rec->>PB: request attachment
    PB->>GB: ON_DEMAND_REQUEST (attachment_id)
    GB->>GA: fetch with a fresh policy evaluation at fetch time
    GA-->>GB: streamed attachment
  end
  opt Node B offline
    Note over GA: Envelope stays queued at A with visible status "queued — receiving node offline since T".<br/>Nothing is lost and nothing is falsely reported as delivered.
  end
```

**Design commitments.** Urgency maps to queue priority end-to-end. Transfer summaries are a referral subtype carrying the discharge/transfer document set. Amendments supersede by `record_version` and never mutate the delivered package. Patient consent or emergency legal basis is recorded *on the referral itself*, so the receiving institution can see the basis on which it holds the data. Every state transition is visible to a human on both sides — including failure states, which today have no representation at all.

---

# 16. Mobile and browser routing

## 16.1 Signed node configuration document

```jsonc
{
  "config_version": "2026.08.03-1",
  "node_id": "…", "node_code": "NODE-PARI-01",
  "trust_domain_id": "…", "display_name": "Parirenyatwa Group of Hospitals",
  "endpoints": {
    "api":      "https://impilo.parirenyatwa.health.zw",
    "auth":     "https://id.parirenyatwa.health.zw/realms/impilo-node",
    "realtime": "wss://impilo.parirenyatwa.health.zw/internal/v1/khuluma/stream/ws"
  },
  "failover": {
    "policy": "LOCAL_FIRST",          // LOCAL_FIRST | LOCAL_ONLY | NATIONAL_ONLY
    "national_fallback": "https://impilo.mohcc.gov.zw",
    "fallback_allowed_scopes": ["citizen.self", "public.gateway"],
    "fallback_prohibited_scopes": ["clinical.write", "clinical.read"]
  },
  "tls": { "spki_pins": ["sha256/…"] },
  "facilities": ["facility_uuid …"],
  "issued_at": "…", "expires_at": "…",
  "signature": { "alg": "Ed25519", "kid": "impilo-node-config-2026Q3", "value": "…" }
}
```

**Failover is scoped, not blanket.** A handset that cannot reach its node must not silently start writing clinical data to the National Core — that would create records with the wrong origin node and the wrong authority. `fallback_prohibited_scopes` makes that structurally impossible; citizen self-service may fall back, clinical work may not, and the app says which mode it is in.

## 16.2 Enrolment and discovery

```mermaid
sequenceDiagram
  autonumber
  actor U as Clinician
  participant APP as Mobile app (national build)
  participant QR as Node enrolment QR
  participant N as Hospital Node
  participant CORE as National Core

  U->>APP: Scan node enrolment QR (displayed by node IT)
  QR-->>APP: {node_code, config_url, config_signing_kid, spki_pin}
  APP->>CORE: fetch node config signing key (pinned, cached)
  APP->>N: GET /node-config (TLS pinned to spki_pin)
  N-->>APP: signed node configuration document
  APP->>APP: verify signature + expiry + node_code match → persist as active node
  APP->>N: OIDC PKCE against the node's auth endpoint
  Note over APP,N: The app is now a node client. No rebuild. No LAN prohibition —<br/>the node presents a valid, pinned TLS certificate, which is the actual requirement.
```

**Two current constraints are removed by this design.** The build-time endpoint baking (Next rewrites resolved into `routes-manifest.json`, `NEXT_PUBLIC_*` and `EXPO_PUBLIC_*` inlined by Metro) is replaced by runtime configuration fetched at startup and cached. The mobile production guard that rejects LAN and `http://` endpoints is replaced by the correct rule: **reject any endpoint that does not present a valid, pinned TLS certificate for a signed, enrolled node** — which permits a hospital's own domain on its own network, and still rejects an attacker's plaintext host.

---

# 16A. Experience routing and write authority **[T]**

## 16A.1 The node configuration document, extended

v1.0's document carried API, auth and realtime endpoints plus a failover policy. v1.1 extends it to describe the whole experience surface and, crucially, to separate **where requests go** from **who may write clinical facts**.

```jsonc
{
  "config_version": "2026.08.03-2",
  "node_id": "…", "node_code": "NODE-PARI-01", "trust_domain_id": "…",
  "display_name": "Impilo at Parirenyatwa Group of Hospitals",

  "endpoints": {
    "node_work":            "https://impilo.parirenyatwa.health.zw",
    "node_auth":            "https://id.parirenyatwa.health.zw/realms/impilo-node",
    "node_realtime":        "wss://impilo.parirenyatwa.health.zw/internal/v1/khuluma/stream/ws",
    "national_my_life":     "https://impilo.mohcc.gov.zw/home",
    "national_professional":"https://impilo.mohcc.gov.zw/professional",
    "national_marketplace": "https://impilo.mohcc.gov.zw/marketplace",
    "national_public":      "https://impilo.mohcc.gov.zw/welcome",
    "national_citizen":     "https://impilo.mohcc.gov.zw/citizen",
    "federation_issuer":    "https://impilo.mohcc.gov.zw/realms/impilo"
  },

  "transitions": {
    "sso_handoff_permitted": false,        // institution choice; false ⇒ always re-authenticate
    "reauthentication_required": true,
    "permitted": ["WORK_TO_PROFESSIONAL", "WORK_TO_PERSONAL", "WORK_TO_PUBLIC"],
    "managed_devices_only": true,          // institution may restrict handoff to managed devices
    "identity_binding_mode": "LOCAL_ONLY"  // mirrors trusted_issuer; LOCAL_ONLY ⇒ no brokered handoff
  },

  "scopes": {
    "strictly_local":   ["clinical.read", "clinical.write", "operational.write",
                         "pharmacy.dispense", "orders.write", "admission.write"],
    "may_fall_back":    ["citizen.self", "public.gateway", "marketplace.browse"],
    "prohibited_fallback": ["clinical.read", "clinical.write"]
  },

  "clinical_write_authority": "LOCAL",     // LOCAL | NATIONAL — see 16A.2
  "tls": { "spki_pins": ["sha256/…"] },
  "facilities": ["facility_uuid …"],
  "issued_at": "…", "expires_at": "…",
  "signature": { "alg": "Ed25519", "kid": "impilo-node-config-2026Q3", "value": "…" }
}
```

## 16A.2 `clinical_write_authority` is a stronger rule than failover **[D]**

Endpoint failover answers *which host do I call*. Write authority answers *whose record is this*. Conflating them is how split-brain happens.

> **A facility is either central-primary or local-primary, and does not switch.** `clinical_write_authority` is a property of the facility's node assignment, not a runtime reaction to connectivity. A local-primary facility writes locally whether or not the National Core is reachable; a central-primary facility writes centrally and, when the Core is unreachable, **stops** rather than silently starting a local record with no lineage.

Consequently: a client that cannot reach its node **does not** re-route clinical traffic to the National Core. It queues (mobile), degrades to read-only from cache, or refuses — and says which. `prohibited_fallback` makes this structural rather than advisory.

## 16A.3 Central-primary versus local-primary — the operating matrix

| | **Hosted facility (central-primary)** | **Hospital Node (local-primary)** |
|---|---|---|
| Encounters | Written nationally | **Locally**, always |
| Orders and results | Nationally | **Locally** |
| Inpatient, theatre | Nationally (or unavailable if unsupported) | **Locally** |
| Pharmacy, stock | Nationally | **Locally** |
| Billing capture | Nationally | **Locally** |
| Documents and images | National storage | **Local by default**; index up, fetch on demand |
| System of record | National | **The node** |
| While connected | All operations national | Local writes + **continuous** federation |
| On connectivity loss | Clinical operations **stop**; Edge (if present) provides constrained, explicitly reconciled continuity | **Nothing changes about the source of truth** — only the synchronisation state changes |
| National authority regardless | Identity, provider standing, facility identity, terminology, trust anchors, statutory reporting, cross-institution routing | Same |
| Reconnection | Edge backlog reconciled | Control information first (revocation, consent), then clinical backlog (§20.1) |

**Why central-first-with-local-failover is rejected [D].** A node that ordinarily writes centrally holds no complete local record; activating a cold local system at the moment of failure produces partial encounters, orders whose results will land elsewhere, and admissions with no local history. It also makes correctness depend on detecting the exact instant of failure — the least reliable moment in any distributed system. Local-primary avoids all of it: the local record is always complete because it is always the record, and disconnection changes only whether the projection is current.

---

## 16.3 Offline and synchronisation posture

The provider app's existing SQLite queue is retained (it works). Three additions: queued operations are tagged with the enrolled `node_id` so they can never replay against a different node; the dead pull-sync (`downloadEdgeSnapshot`, which targets an endpoint no service serves and has no callers) is **replaced** by a real node-side snapshot endpoint scoped to the clinician's work context and cohort; and the web shell's emergency-only service-worker lane is extended to the node's core clinical read set, since a node-local browser is on the same LAN as its server and its outage modes are different from a national outage.

---

# 17. Hospital Node deployment profile

## 17.1 What the current chart already gives us, and the eight things it does not

The chart is closer than expected: `templates/microservice.yaml` already reads `replicaCount`, `resources`, `env`, `secretEnv`, per-service probes and digest-pinned images through the `impilo.image` helper, and the estate is expressed as data in `fullBootServices`. A node profile is therefore **another values file plus eight capabilities the chart has never needed**:

| # | Missing primitive | Evidence | Node requirement |
|---|---|---|---|
| 1 | `storageClassName` — **the field does not exist in any template**; all seven PVCs take the cluster default and are RWO | grep of `templates/` + all values files: zero hits | Parameterise per PVC; the node uses replicated block storage, not `local-path` |
| 2 | `StatefulSet` — **no template renders one** | zero hits | Postgres, Kafka, MinIO and Orthanc all need stable ordinals |
| 3 | `RollingUpdate` — `microservice.yaml` emits only `strategy.type`, no `rollingUpdate:` block; `global.deploymentStrategy` is `Recreate` | `values-full-preview.yaml:11` | Node runs rolling updates with surge/unavailable per service |
| 4 | `PodDisruptionBudget`, `HorizontalPodAutoscaler`, `affinity`, `topologySpreadConstraints`, `nodeSelector`, `tolerations`, `priorityClassName` | zero hits, all seven | Spread replicas across three cluster nodes; protect quorum during drain |
| 5 | `imagePullSecrets` | zero hits | Node pulls from a real registry with credentials |
| 6 | Namespace and public host are **hardcoded** — `impilo-full-preview` in ingress routes, the secrets script and the TLS sync script; `impilo.mohcc.gov.zw` written literally into ~40 env entries and into every generated service block | `deploy/tls/mohcc-gov/ingressroutes.yaml:15,31`; `scripts/secrets/bootstrap-secrets.sh:28` | Both become values |
| 7 | Distributed scheduler locking — **139 `@Scheduled` annotations across 74 services and zero locks anywhere** (ShedLock, advisory locks, `SKIP LOCKED`, leader election: all zero hits) | measured | Required before any service runs `replicas > 1` |
| 8 | Node identity — the only per-cluster identifier the chart mints is `impilo.workloadId` (`urn:impilo:workload:<env>:<cluster>:<ns>:<sa>:<workload>`) | `_helpers.tpl:31-34` | `node_id` becomes a first-class chart value threaded into every pod |

> **⚠ Double-firing is not hypothetical — it is happening now.** `experience-bff` already runs `replicaCount: 2` and carries three `@Scheduled` beans (`BookingOutboxCommsPoller`, `AppointmentReminderScheduler`, `WorkContextRevalidationJob`). Appointment reminders are being scheduled twice in the current estate. Fixing scheduler locking is therefore a **current-estate defect fix (Phase 0)**, not node-enablement work.

## 17.2 Deployment-profile matrix

| Concern | Current single-node preview | **National Core** target | **Hospital Node** target | Facility Edge (later) |
|---|---|---|---|---|
| Cluster | 1 k3s node | ≥3 control + workers | **≥3 nodes** | 1–2 nodes |
| Namespaces | one (`impilo-full-preview`) | `impilo-core`, `impilo-fed`, `impilo-data`, `impilo-obs` | `impilo-clinical`, `impilo-trust`, `impilo-data`, `impilo-fed`, `impilo-obs` | one |
| Storage | `local-path`, RWO, node-local | replicated CSI + snapshots | **replicated CSI, per-PVC class**, snapshot-capable | local + nightly off-box |
| PostgreSQL | 1 Deployment, 124 DBs | HA operator, per-plane clusters | **primary + ≥1 sync replica + PITR (CloudNativePG or Patroni)** | single + PITR |
| Kafka | 1 broker, RF=1, `NODE_ID` hardcoded in the pod env | 3 brokers RF=3 | **3 brokers, RF=3, min.isr=2, TLS+SASL** | single broker or none |
| Redis | **no volume at all** — 23-line template | persistent + replica | **AOF persistence + replica/Sentinel, auth on** | persistent |
| MinIO | standalone single drive | distributed | **distributed (≥4 drives)** | standalone + off-box copy |
| Orthanc | 1 replica, index and storage on the same RWO path, **auth disabled** | n/a | **auth on, index separated, replicated storage** | n/a |
| Keycloak | 1 replica, `Recreate` hardcoded, realm import forbidden | HA, Infinispan | **2+ replicas, Infinispan; or institution IdP instead** | national only |
| Update strategy | `Recreate` estate-wide | RollingUpdate | **RollingUpdate + PDB + anti-affinity** | Recreate acceptable |
| Registry | `127.0.0.1:5000` loopback, plus four other hardcoded copies | real registry | **real registry + pull secrets + digest pinning retained** | mirror/pull-through |
| TLS | host certbot + host nginx + manual Endpoints | cert-manager | **cert-manager (institutional CA or ACME)** | cert-manager |
| Secrets | `impilo-app-secrets` created by an imperative script; Postgres password committed in values | External Secrets / Vault | **External Secrets or Vault; no committed credentials** | sealed secrets |
| Deploy | GitHub Action → SSH → shell script on one known host | GitOps | **GitOps (Argo or Flux — neither exists today)**; digest pins committed, not generated on the box | GitOps |
| Backup | nightly `pg_dumpall` to the same VM | off-site | **PITR + nightly logical + MinIO replication + Orthanc archive, all off-node and off-site, with a proven restore** | nightly off-box |
| Observability | none deployed; tracing force-disabled estate-wide | full stack | **Prometheus + Loki/ELK + OTel + alerting; capacity and queue-depth reporting to the Fleet Service** | health only |
| Health→national | none | n/a | **`observability-service` `/ops/heartbeat` + `/health/summary` + `/metrics/lag` extended with node capacity and federation queue depth** | heartbeat only |
| Upgrade rings | n/a | publisher | **CANARY → EARLY → STABLE → LTS**, with an institution-controlled window for non-MoHCC | STABLE only |

## 17.3 Service dependency closure for the node

Derived from the BFF's `impilo.services.*` block and each clinical service's own configuration, expressed as the chart's existing deployment tiers.

**Tier DATA (must be first):** postgres · redis · kafka · minio · orthanc *(if imaging)* — plus the node's IdP if MoHCC-managed.
**Tier TRUST (all seven):** tshepo-authz · tshepo-identity · tshepo-consent · tshepo-audit · tshepo-keys · bundle-agent *(new, replaces tshepo-offline)* · envoy.
**Tier REGISTRY (cache + local config):** vito · varapi *(read cache)* · tuso · organization-registry *(read cache)* · vashandi · indawo.
**Tier CLINICAL (the reason the node exists):** pct · oros · butano *(local projection)* · fhir-gateway · inpatient · pharmacy · inventory · product-registry · forms · zibo *(cache)* · clinical-knowledge-platform *(cache)* · document-service · pacs-adapter · identity-assurance · surgery + procedures *(theatre pack)* · mental-health *(pack)*.
**Tier MONEY (local capture):** costa · coverage *(offline tokens)* · mushe-wallet.
**Tier EXPERIENCE:** experience-bff · one-ui-shell.
**Tier FEDERATION:** federation-gateway · observability-service.

**Explicitly not on the node:** reporting · ndr · national-data-repository · surveillance *(national authority; local capture only)* · data-warehouse · data-pipeline · data-ingestion · analytics-pipeline · campaigns · data-governance · developer-portal · schema-registry · mushex *(national rails)* · livekit *(optional pack)*.

> **⚠ A closure blocker inherited from the current estate.** Most service-to-service base URLs **are not set in any values file** — `OROS_BUTANO_BASE_URL`, `OROS_VARAPI_BASE_URL`, `OROS_TUSO_BASE_URL`, `INVENTORY_BASE_URL`, `PCT_BASE_URL`, `MADI_BASE_URL`, `COSTA_BASE_URL` and `ZIBO_BASE_URL` among them. Unset, they fall back to `localhost:PORT`, which inside a pod is the pod itself. **The node profile cannot be built by copying the current values files** — the closure must be generated from each service's declared dependencies and verified by a startup assertion that every configured peer resolves. Newer services (surgery, procedures) already default to `http://<service>:<port>` and show the correct pattern.

## 17.4 Node bootstrap sequence

```mermaid
sequenceDiagram
  autonumber
  participant OP as Node operator
  participant GIT as GitOps repo (node overlay)
  participant K8S as Hospital cluster
  participant CORE as National Core (Fleet + CA)

  OP->>CORE: Register node → node_id, node_code, enrolment token
  OP->>GIT: Commit node overlay (node_id, hosts, storage classes, facility set)
  GIT->>K8S: Reconcile — namespaces, secrets refs, storage, infra tier
  K8S->>K8S: Postgres HA + Kafka quorum + Redis + MinIO healthy
  K8S->>CORE: CSR (node signing + mTLS) with the enrolment token
  CORE-->>K8S: node certificates (node CA)
  K8S->>CORE: Fetch bundle manifest → policy, standing, consent, relationship, revocation, terminology
  K8S->>K8S: Bundle Agent verifies signatures, installs, arms staleness alarms
  K8S->>K8S: Trust tier → registry tier → clinical tier → experience tier
  K8S->>CORE: First federation handshake → node_connection ACTIVE
  K8S->>K8S: Publish signed node configuration document + enrolment QR
  OP->>OP: Run the acceptance gate (§19) — including a disconnection rehearsal BEFORE go-live
```

---

# 17A. Facility and node sizing profiles **[T/O]**

## 17A.1 One codebase, five profiles

Facility size never produces different source code. It determines: whether a local node exists at all, which capability packs are active, resource sizing, HA topology, storage capacity, integration requirements and continuity expectations. A "hospital edition" is a defect (§1.5).

```
HOSTED_FACILITY  ·  FACILITY_EDGE  ·  HOSPITAL_COMPACT  ·  HOSPITAL_STANDARD  ·  HOSPITAL_ENTERPRISE
```

## 17A.2 Volumetric bands — **planning estimates, not measurements**

> ⚠️ **Read this caption before using any number below.** The recovery established that the repository contains **no sizing or capacity guidance and no load baselines beyond a narrow read/write ring**. The bands below are *derived from Zimbabwean facility-type norms* — bed complements, typical OPD attendance and staffing patterns by facility tier — and are published so procurement and infrastructure conversations can start. **They are estimates. No figure here has been measured against a running Impilo deployment.** Each is replaced by measurement per §17A.5.

| | HOSTED_FACILITY | FACILITY_EDGE | HOSPITAL_COMPACT | HOSPITAL_STANDARD | HOSPITAL_ENTERPRISE |
|---|---|---|---|---|---|
| **Typical facility** | Clinic, RHC, small private practice | Clinic/RHC with intermittent connectivity | District, mission, small private hospital | Provincial, large private hospital, hospital group | Central, teaching, specialist, multi-campus |
| Indicative beds | 0–20 | 0–20 | 60–200 | 200–500 | 500–2,000 |
| Concurrent users (peak) | 2–15 | 2–15 | 20–80 | 80–250 | 250–1,500 |
| Daily encounters | 20–80 | 20–80 | 150–500 | 400–1,200 | 1,000–3,000 |
| Daily orders + results | 10–60 | 10–60 | 150–600 | 500–2,000 | 2,000–8,000 |
| Daily imaging studies | 0–5 (referred out) | 0–5 | 10–60 | 60–250 | 250–1,000+ |
| **Continuity requirement** | None (central-primary) | Hours–days, defined scope | **7 days** | **7 days** | **7 days, plus DR** |
| Minimum cluster nodes | n/a | 1 (appliance) | 3 | 3 | **3+, multi-failure-domain** |
| PostgreSQL | National | Local cache only | Primary + sync replica, PITR | Primary + replica(s), PITR | HA cluster, separate failure domains, PITR |
| Kafka | National | **None** (store-and-forward queue instead) | 3 brokers RF=3 | 3 brokers RF=3 | 3+ brokers RF=3, tiered storage |
| Redis | National | Local, persistent | Persistent + replica | Persistent + replica/Sentinel | HA, separate domain |
| Object storage | National | Local disk + off-box copy | MinIO, replicated | MinIO distributed | MinIO distributed, separate domain |
| PACS | National / none | None | Optional local Orthanc | **Local Orthanc** | **Enterprise PACS**, local archive |
| Backup | National | Nightly off-box | PITR + nightly, **off-node and off-site** | + tested restore drills | + DR node, RPO/RTO contractual |
| DR expectation | National | Rebuild from bundle | Restore within contracted RTO | Restore + optional DR node | **DR node, exercised** |
| Local Keycloak | No | No | **Yes** or institution IdP | Yes or institution IdP | Yes or institution IdP |
| Local VITO registration | No | Provisional capture only | **Yes** (allocation block) | Yes | Yes |
| Local Butano projection | No | No | **Yes** | Yes | Yes |
| Local analytics | No | No | No | Institutional reporting | **Local warehouse permitted** |
| Local ICT capacity | None | Site champion | 1–2 ICT staff | Small ICT team | **24/7 ICT + on-call** |

## 17A.3 Capability packs

Core (every node profile): trust tier, registry caches, PCT, OROS, local Butano, FHIR gateway, pharmacy, inventory, forms, documents, identity assurance, Costa, BFF, shell, Federation Gateway, observability, Bundle Agent.

| Pack | COMPACT | STANDARD | ENTERPRISE |
|---|---|---|---|
| Inpatient / ward | ✔ | ✔ | ✔ |
| Theatre (surgery + procedures) | optional | ✔ | ✔ |
| Local PACS + imaging | optional | ✔ | ✔ (enterprise) |
| Laboratory / analyser integration | optional | ✔ | ✔ |
| Mental health | optional | optional | ✔ |
| Maternity / RMNP | ✔ | ✔ | ✔ |
| Telemedicine (local LiveKit) | — | optional | optional |
| Teaching / Fundo integration | — | optional | ✔ |
| Research governance | — | — | ✔ |
| Local analytics / warehouse | — | — | ✔ |
| Multi-campus topology | — | optional | ✔ |

**Always national, never a node pack:** national reporting authority, NDR, national data warehouse, national surveillance authority, national payment rails, campaigns, national governance services, developer portal, schema registry — and every personal-domain service (My Life, PHR, wellness, Marketplace), which is national by doctrine, not by sizing (§11B.3).

## 17A.4 Hosted facility and Facility Edge, precisely

**HOSTED_FACILITY** is central-primary and has **no independent local clinical runtime**. Local components are limited to browser and mobile clients, an optional printer/device agent, an optional read cache and a connectivity monitor. Its continuity story is honest: when the link is down, clinical work stops.

**FACILITY_EDGE** adds a secure edge gateway, runtime endpoint discovery, device and printer adapters, an encrypted local patient-worklist cache, a store-and-forward queue, local emergency workflow, local policy and identity cache, and a sync agent. It supports **constrained continuity** — emergency workflow, OPD capture and dispensing for a bounded window — and **does not pretend to be a seven-day autonomous hospital**. Everything it captures is explicitly reconciled, and its cohort and cache rules are those of §19B at a smaller scale.

## 17A.5 How every band above gets replaced by a measurement

Each figure is a hypothesis with a named replacement method: node instrumentation reports concurrent sessions, encounters, orders, results, imaging studies, storage growth, federation queue depth and p95 latency to the Fleet Service (§17.2); the Phase 5 pilot runs at a central hospital under real load; and the bands are re-issued from observed percentiles per facility tier. **Until that happens, no procurement decision should treat a number in §17A.2 as more than a starting point**, and the acceptance plan (A45) requires the pilot to publish measured figures against these estimates.

---

# 18. Facility-specific configuration

## 18.1 The hierarchy

Resolution is **most-specific-wins**, with each level able to *narrow* but never *widen* what the level above permits:

```
trust_domain  →  organisation  →  node  →  facility  →  department  →  service_point
```

| Setting class | Owned at | Rationale |
|---|---|---|
| Data-sharing policy, retention, sensitivity ceiling, key custody | **trust_domain** | Legal controller decisions |
| Branding, clinical governance defaults, formulary policy, tariff schedule | **organisation** | Operator decisions |
| Infrastructure endpoints, storage, registry, TLS, backup targets, federation queues | **node** | Deployment decisions |
| LIMS, PACS/DICOM AE titles, eLMIS endpoint, SMS sender ID, payment till, printers, opening hours, capability set | **facility** | Where care happens; a node serving three facilities has three of these |
| Modality worklist filters, analyser mappings, department printers, order sets | **department** | Departmental workflow |
| Queue behaviour, kiosk/label printer, card reader | **service_point** | Point-of-service devices |

## 18.2 Replacing global environment variables

| Integration | Today | Target level | Configuration object |
|---|---|---|---|
| LIMS | one HL7 MLLP listener bound to a single tenant+facility from static env, disabled everywhere | **facility** (+ department for analyser routing) | `lims_endpoint` — protocol, host/port or URL, credentials ref, AE/sender identifiers, result-mapping profile |
| Laboratory analysers | **no analyser configuration exists at all** | **department** | `analyser_device` — driver, connection, test-code map, QC profile |
| PACS | one global `ORTHANC_BASE_URL`, one external VNA URL | **facility** | `pacs_endpoint` — DICOMweb/DIMSE, AE title, TLS, storage-commit policy |
| DICOM modalities | MWL publishers exist, default OFF | **department** | `modality` — AE title, IP, worklist filter, procedure-code map |
| eLMIS | one global URL pointed at `localhost:9080` | **organisation** (+ facility overrides) | `elmis_endpoint` |
| Printers | one IPP URI per print agent | **service_point** | `printer` — URI, media, purpose (card, label, document) |
| SMS / email | one gateway URL and one sender ID estate-wide; providers pinned to `log` | **node** (credentials) + **facility** (sender identity) | `messaging_provider` |
| LiveKit | one national SFU | **node** (optional) | `rtc_provider` |
| Payment rails | deployment-level env, no live rail enabled | **organisation** (+ facility till) | `payment_rail` |
| Payers | national schemes | **organisation** | `payer_contract` |
| Existing hospital systems | none | **facility** | `external_system` — adapter type, endpoint, credentials, mapping profile |
| External FHIR/HL7 | `fhir_route` keyed `(tenant, source_system, resource_type)` with **no uniqueness constraint and no node dimension** | **node + facility** | extend `fhir_route` with `node_id`, `facility_id` and a real unique key |

## 18.3 Configuration object contract

Every configuration object is a versioned, audited row — never an environment variable:

```sql
config_binding (
  binding_id      UUID PRIMARY KEY,
  scope_level     VARCHAR(16) NOT NULL,   -- TRUST_DOMAIN|ORGANISATION|NODE|FACILITY|
                                          -- DEPARTMENT|SERVICE_POINT
  scope_id        UUID NOT NULL,
  config_key      VARCHAR(96) NOT NULL,   -- 'lims_endpoint', 'pacs_endpoint', …
  config_value    JSONB NOT NULL,         -- secrets by REFERENCE only, never inline
  secret_refs     JSONB NOT NULL DEFAULT '{}',
  version         INT NOT NULL,
  effective_from  TIMESTAMPTZ NOT NULL, effective_to TIMESTAMPTZ NULL,
  status          VARCHAR(16) NOT NULL,   -- DRAFT|ACTIVE|SUPERSEDED|WITHDRAWN
  created_by, created_at, approved_by, approved_at,
  UNIQUE (scope_level, scope_id, config_key, version)
);
```

**Three guarantees.** Configuration is *data*, so it survives upgrades — a Helm release never overwrites it, which is the specific failure the current model has (a hand edit to a generated values file survives only until the next generator run, and the recovery records `KEYCLOAK_BACKEND_SECRET` being lost exactly that way). It is *versioned and approved*, so a change to a lab endpoint is an auditable clinical-safety event. And it holds *secret references, never secrets*, so it can be replicated to a node without moving credentials.

TUSO already owns the facility operational shape (departments, service points, clinical spaces, capabilities, operating hours, versioned facility config with a tenant→facility→workspace override cascade). `config_binding` extends that existing cascade upward to trust domain and node and downward to department and service point — it does not replace it.

---

# 19. Resolving the FHIR split-brain

## 19.1 The single governed implementation

**`butano-service` is the only FHIR store.** It is the one with the interceptor chain that actually enforces doctrine: PII prevention (CPID-only identifiers, rejecting names/telecom/address), tenant tag stamping and cross-tenant 403, provenance stamping, terminology validation, header validation.

- **`butano-fhir` is retired.** It has no PII guard and demonstrably receives free text and collector names from inpatient. Its data migrates into the governed store; the service is deleted.
- **Stock `hapi-fhir` is retired.** The BFF's `FhirPublisher` and the FHIR gateway's default target are repointed at the governed store, and the `hapi` database is migrated then dropped.

## 19.2 Local projection and national projection

Both run `butano-service` in different modes.

| | **Node Butano** | **National Butano** |
|---|---|---|
| Contains | FHIR facts created at this node | Longitudinal projection across all contributing nodes |
| Authority | Projection of the node's own clinical records | Projection of everyone's — authoritative for nothing |
| Writes | From local PCT/OROS/inpatient/pharmacy/theatre via the local FHIR gateway | **Only** from verified federation envelopes |
| IPS / timeline | Local record, complete for this node | Cross-node, each row labelled with its origin node |
| Provenance | `meta.source = urn:impilo:node:{node_id}:{origin_record_id}` | Preserves the origin node's provenance unchanged |

**Two concrete blockers, both identified and both cheap to fix.** First, `butano-fhir`'s unique index `(tenant_id, resource_type, resource_id)` would collide across projections — moot once it is retired, but the governed store's tenant-tag read filter has the same shape and gains a node/domain tag. Second, `IpsBundleGenerator` and `TimelineService` filter solely by tenant across 8 and 11 resource types respectively, and `TimelineItem` carries no origin field — so a merged view could not tell a clinician which node a row came from. Adding an origin label to the timeline item is a patient-safety requirement, not a nicety.

## 19.3 How clinical facts reach the governed store

| Source | Today | Target |
|---|---|---|
| PCT observations, problems, examinations | Kafka → butano consumer (**works in code**) | Unchanged locally; contributed nationally by federation |
| PCT encounters | **No listener exists; `ButanoIntegration` is a dead class with zero callers** | Encounter projection added to the local FHIR write path |
| OROS results | **Butano has no OROS listener**; the REST writeback is dead because its base URL is unset | Local FHIR write path + result contribution |
| Inpatient discharge summaries | Emitted to a topic **nobody consumes** | Consumed by the local projection, contributed nationally |
| Theatre procedures | Written to `butano-fhir` (ungoverned) | Written to the governed store |
| Documents | Index only | `DocumentReference` in the governed store, binary in MinIO |
| Allergies, immunisations, prescriptions | **Read by the IPS generator, written by nothing** | Local write path — this closes the readable-never-written gap |

## 19.4 Provenance representation

```jsonc
{ "resourceType": "Provenance",
  "target": [{ "reference": "Condition/…" }],
  "occurredDateTime": "2026-08-03T09:14:00Z",
  "recorded": "2026-08-03T09:14:07Z",
  "agent": [
    { "type": {"coding":[{"code":"author"}]},
      "who":        {"identifier": {"system":"https://impilo.gov.zw/provider","value":"…"}},
      "onBehalfOf": {"identifier": {"system":"https://impilo.gov.zw/organisation","value":"…"}} },
    { "type": {"coding":[{"code":"custodian"}]},
      "who": {"identifier": {"system":"https://impilo.gov.zw/node","value":"NODE-PARI-01"}} }
  ],
  "entity": [{ "role":"source",
               "what": {"identifier": {"system":"https://impilo.gov.zw/origin-record","value":"…"}} }],
  "signature": [{ "type":[{"code":"1.2.840.10065.1.12.1.1"}], "when":"…",
                  "who":{"identifier":{"value":"NODE-PARI-01"}}, "data":"<Ed25519 JWS>" }]
}
```

The existing `ProvenanceStampingInterceptor` already stamps eight tag codes and sets `meta.source` — this extends that mechanism rather than replacing it, and the `PiiPreventionInterceptor` allow-list must admit the federation extension or the stamped resource is rejected by its own guard.

---

# 19A. Personal Health Record architecture **[D/T]**

## 19A.1 Four record concepts, four authorities

| Record | What it is | Authority | Lives |
|---|---|---|---|
| **A. Facility Operational Health Record** | The detailed legal and operational record created by a facility's clinical systems — notes, orders, results, medication administration, ward and theatre records, local documents and images, billing events | **The originating facility** (PCT, OROS, inpatient, pharmacy, theatre are the systems of record) | Node |
| **B. Node Butano projection** | The governed FHIR projection of facts produced at that node | A projection — authoritative for nothing | Node |
| **C. National Butano longitudinal projection** | The cross-node, provenance-preserving national longitudinal record: summaries, problems, allergies, active medications, significant results, discharge summaries, procedures, referrals, selected documents, links to externally held imaging | A projection — **never overwrites its source** | National |
| **D. Personal Health Record** | The individual-facing record inside My Life | **The individual** | National only |

Neither Butano instance is the source of truth for a clinical event; PCT, OROS, inpatient and their peers remain authoritative. The PHR is not the legal record merely because it displays one.

## 19A.2 What the PHR contains

Citizen-readable national SHR content; patient-entered history; personally uploaded documents; device data; wellness data; personal observations; personal care plans; preferences; and consent and sharing settings.

## 19A.3 Provenance classes **[D]**

```
CLINICIAN_VERIFIED · FACILITY_REPORTED · PATIENT_REPORTED
DEVICE_REPORTED · CAREGIVER_REPORTED · IMPORTED · FEDERATED_PROJECTION
```

Every item rendered anywhere in the platform carries exactly one class, and **a hospital must not treat `PATIENT_REPORTED` or `DEVICE_REPORTED` content as clinician-verified without review.** Review is an explicit act that produces a new `CLINICIAN_VERIFIED` item citing the original — never an in-place upgrade, so the patient's assertion and the clinician's verification remain separately attributable.

**Current state, and why this is new work.** No `PATIENT_REPORTED` constant exists anywhere in the estate. What exists is *source-level consent* rather than *item-level provenance*: `wellness_connected_sources` carries `source_type`, `provider_access_allowed` and `clinical_writeback_allowed` — **both defaulting to false** — plus `sharing_scope` toggling between `PERSONAL_ONLY` and `SHARED_WITH_PROVIDER`. Those defaults are promoted to doctrine here. Clinical-side provenance today is actor- and context-based (`ProvenanceStampingInterceptor` stamps tenant, facility, workspace, actor, purpose, correlation, break-glass and mode), where `X-Actor-Type` is the only place a citizen author is distinguishable — a tag, not a typed class.

Two existing patterns are the model to follow, both already shipping:
- `ubomi`'s `cause_of_death_basis` — a six-value certainty vocabulary (`MEDICALLY_CERTIFIED`, `POST_MORTEM_CERTIFIED`, `VERBAL_AUTOPSY_PROBABLE`, `FIELD_INVESTIGATION_PROBABLE`, `MEDICO_LEGAL_PENDING`, `UNKNOWN_UNCERTIFIED`) built explicitly so that a probable cause is never counted as a certified one.
- The immunisation forecaster's `GIVEN_UNVERIFIED` status, surfaced to clinicians as "Given — unverified" with the note that it is counted so a child is not needlessly re-vaccinated, but must be verified before being relied upon.

That is exactly the register the PHR provenance classes must hit: **useful enough to act on, honest enough not to be mistaken for verification.**

## 19A.4 Disclosure to a facility

A person may authorise specific PHR content to a named facility for a stated purpose and period. The facility receives it as a **labelled disclosure attached to an episode** — provenance class intact, expiry attached, listed on the person's own consent centre and on the facility's disclosure record. It never confers general access to My Life, and it is never merged silently into the facility's operational record.

## 19A.5 Boundary defects to close

- **Personal documents are currently served from the clinical lane** — the personal document vault calls `/internal/v1/clinical-tools/documents`. A personal-audience endpoint family replaces it (§4A.3).
- **Device and wellness data must remain unshared by default**, per the shipped `provider_access_allowed=false` — now doctrine, not an implementation choice.
- **The PHR is never rendered by a node** (§11B.3), so no node holds PHR content except as an explicit, labelled, time-boxed disclosure.

---

# 19B. Shared-Care Cache (Care Continuity Projection) **[T]**

## 19B.1 The problem v1.0 left open

v1.0 held that Node Butano contains *facts created at that node*, and cross-institution reads require live federation. That is correct as a default and wrong as a totality: **a disconnected central hospital treating a patient with existing national records loses the continuity information it had already lawfully retrieved.** A clinician who saw the patient's allergy list on Monday should not lose it on Wednesday because a fibre was cut.

The answer is a governed cache — with enough constraints that it cannot become bulk replication wearing a cache's clothes.

## 19B.2 It is a separate store, not merged into Node Butano **[D — ADR-30]**

The Shared-Care Cache is stored **separately** from the node's own Butano projection, for four reasons that all reduce to one: a clinician must never be unable to tell *we recorded this* from *somewhere else recorded this, some days ago*.

1. **Provenance clarity.** Merged, a cached national fact and a node-authored fact become one row in one timeline.
2. **Revocation.** A cached item must be suppressible without touching the node's own authored record.
3. **Retention.** Cache entries expire on cohort exit and TTL; authored records follow clinical retention.
4. **Contribution safety.** A node must never re-contribute a cached item as though it authored it — a separate store makes that structurally impossible rather than a rule someone must remember.

## 19B.3 Cohort — lawful relationship only **[D]**

Entry requires a **triggering event**, cited by id on the cache entry:

`ACTIVE_ADMISSION` · `ACTIVE_REFERRAL` · `SCHEDULED_ATTENDANCE` · `OPEN_EPISODE` · `CONTINUING_CARE_PLAN` · `EMERGENCY_PRESENTATION` · `PATIENT_AUTHORISED_PRE_POSITIONING`

**Catchment-based pre-positioning is not permitted.** A node may not cache for a population it might one day treat — only for people it is treating, is about to treat, or has been asked to treat.

## 19B.4 Contents, and what is excluded

Permitted: allergies · active medications · significant problems · recent discharge summaries · the relevant referral package · significant recent results · care plans · emergency information · **known sensitivities and consent restrictions** (so the node can enforce them offline).

**Excluded unless specifically authorised:** SPECIALLY_PROTECTED categories. Mental-health, sexual and reproductive-health and other protected content are **never pre-positioned** on a category-general basis. They enter only under category-specific consent or an audited emergency basis, and the node must be able to enforce the same protections offline — if it cannot, the content does not go.

## 19B.5 Properties, all mandatory

Patient-cohort scoped · purpose scoped · **read-only relative to origin** · provenance-labelled · freshness-labelled · consent- and policy-governed · encrypted at rest · audited on write and on read · time-limited · revocable · suppressed when the node lacks authority · visually distinct from node-authored records · **explicit about what is unavailable**.

That last property is the one clinicians will feel. A cache that silently omits a medication is more dangerous than no cache: the UI must show *"national record unavailable — last synchronised 3 days ago"* rather than an apparently complete list.

## 19B.6 Lifecycle

```mermaid
sequenceDiagram
  autonumber
  participant EV as Triggering event (admission/referral/…)
  participant NODE as Node
  participant FG as Federation Gateway
  participant CORE as National Core
  participant CACHE as Shared-Care Cache
  EV->>NODE: patient enters the lawful cohort
  NODE->>FG: SHARED_CARE_COHORT_REQUEST {cpid, trigger_event_id, purpose}
  FG->>CORE: signed envelope
  CORE->>CORE: evaluate agreement + consent + sensitivity ceiling
  CORE-->>FG: permitted subset + withheld_categories declared
  FG->>CACHE: install, labelled with origin, freshness, expiry
  CORE->>CORE: write disclosure records (both sides)
  loop while in cohort and connected
    CORE-->>CACHE: deltas; consent revocations applied immediately
  end
  Note over CACHE: On cohort exit → expire per policy.<br/>On revocation → suppress immediately, even offline.<br/>On 7-day outage → serve with age shown, never claim currency.
```

**During a seven-day outage** the cache continues to serve, with every item's age displayed. Emergency-minimum content (allergies, active medications, critical alerts) may carry a longer TTL than the general cohort content, because the harm of a stale allergy list is asymmetric — but it is still labelled, and still expires.

**On cohort exit** entries expire per policy rather than being retained "in case they come back". On explicit revocation they are suppressed immediately, including offline, because revocation is carried in the revocation bundle whose hard ceiling is 24 hours.

## 19B.7 How this avoids being bulk replication in disguise

| Control | Mechanism |
|---|---|
| Every entry cites a trigger | `trigger_event_id NOT NULL`, verified against a real admission/referral/appointment at the Core |
| Every entry is a disclosure | A `fed_disclosure_record` on both sides; the institution and the Core both see what was pre-positioned |
| Volume ceilings per node | Cohort size and fetch rate capped by agreement, alerted on breach |
| Cohort audit | Periodic reconciliation: cohort membership versus live triggering events; orphans expire |
| No re-contribution | Separate store; the contribution path reads only node-authored records |
| Sensitivity floor | SPECIALLY_PROTECTED excluded by default, category-specific authority required |

## 19B.8 What the clinician sees

Three visually distinct states, never blended:

| State | Presentation |
|---|---|
| **Node-authored** | Normal. This facility recorded it. |
| **Cached national** | Marked with origin facility and *"as at <timestamp>"*. Actionable, clearly not local. |
| **Unavailable** | Explicitly stated: *"National record unavailable since <time>"* — never an empty section that reads as "nothing to report". |

---

# 20. Reconnection and conflict-resolution model

## 20.1 The four reconnection phases

```mermaid
sequenceDiagram
  autonumber
  participant N as Hospital Node
  participant C as National Core
  N->>C: HANDSHAKE — node_id, cert, release version, schema_version, disconnected_since
  C-->>N: capability + schema negotiation; suspension/agreement status
  Note over N,C: Phase 1 — CONTROL. Revocation list, then policy, standing, consent,<br/>relationship bundles. Policy truth is restored BEFORE any data moves.
  C-->>N: bundle manifest → agent installs → staleness alarms clear
  Note over N,C: Phase 2 — INBOUND. Referrals, consent revocations and directives<br/>captured elsewhere that bear on patients currently in the building.
  C->>N: queued inbound envelopes (priority order)
  Note over N,C: Phase 3 — OUTBOUND. The node drains its backlog in origin_sequence order<br/>per stream, rate-limited, EMERGENCY first.
  N->>C: envelopes … ACK/NACK … watermark advances
  Note over N,C: Phase 4 — RECONCILE. Divergence report: stale-flagged writes,<br/>break-glass records, provisional identities, conflicts.
  N->>C: reconciliation report
  C-->>N: conflict decisions + O-CPID resolutions
```

**Ordering is a safety property, not an optimisation.** Revocation and consent must land before the node's backlog is accepted — otherwise the Core ingests a week of records made under authority the node did not know had been withdrawn. And inbound consent revocations precede outbound contribution so that a patient who withdrew consent on day 2 does not have day 3–7 records contributed on reconnect.

## 20.2 Conflict taxonomy

| Class | How it arises | Resolution | Automated? |
|---|---|---|---|
| **Duplicate delivery** | Envelope replayed | Idempotent — acknowledge, do not re-apply | Yes |
| **Sequence gap** | Envelopes lost or out of order | Receiver requests a replay range; does **not** apply past the gap | Yes |
| **Concurrent amendment** | The same origin record amended at two nodes | **Impossible by construction** — only the origin node may amend. A non-origin amendment is rejected with `NOT_ORIGIN_AUTHORITY` | Yes (prevented) |
| **Duplicate patient** | The same person registered at two nodes offline | Both records stand; the national matcher proposes a link; **a clinician confirms the merge**; both CPIDs redirect via the existing repoint rail | No — human confirmation required |
| **Provisional identity** | O-CPID minted offline | Reconciled to the canonical CPID via the existing mechanism; local references repointed | Yes |
| **Stale-authority write** | Written past a bundle's hard ceiling | Applied and **flagged** `bundle_stale`; listed for clinical governance review | Applied, reviewed |
| **Retrospective consent withdrawal** | Consent withdrawn nationally during the outage | The record stays (it lawfully existed); **national disclosure is suppressed**; a disclosure record notes the suppression | Yes |
| **Policy divergence** | The node decided under a policy version the Core has since replaced | Never re-litigated retrospectively; the decision is recorded with its policy version and audited | Yes |
| **Schema mismatch** | The node runs an older release | Negotiated at handshake; below `compatibility_floor` the node is quarantined and told to upgrade | Yes |
| **Integrity failure** | Bad signature or unknown certificate | Quarantine the envelope, alert both sides, **never apply** | Yes |

## 20.3 Amendment, not overwrite — stated as a database rule

```sql
-- On any federated projection table:
UNIQUE (origin_node_id, origin_record_id, record_version)
CHECK  (record_version >= 1)
-- Applying a version requires: version = current_max + 1 for that origin record,
-- and the applying party's node_id = origin_node_id  OR  the envelope is a
-- projection write from the authoritative origin. Anything else raises a conflict.
```

There is no code path that updates a projection row in place. Corrections are new versions; retractions are a version with `status = ENTERED_IN_ERROR` (FHIR-native); superseding is explicit. **Last-write-wins cannot be expressed in this schema**, which is the point — invariants that depend on developer discipline do not survive contact with 100 services.

## 20.4 Per-node audit chains

Node chains are never interleaved into a national chain. Each node's chain stays whole and local; the node periodically signs its chain head and sends an attestation, which the Core stores as an anchor. A national investigator can prove a node's chain was intact at time T without holding its events.

This requires four changes to the current chain: scope it `(tenant_id, node_id)` instead of `tenant_id` alone — today there is effectively **one global chain serialised by a single row lock**, which is both a throughput ceiling and an absolute barrier to disconnected operation; add `node_id` to the hash input; **add the six columns the hash currently omits** (`subject_ref`, `resource_type`, `resource_id`, `purpose_of_use`, `facility_id`, `detail` are persisted but unhashed, so precisely the fields a hostile node would alter are the ones not protected); and delimit the concatenation, which is currently an undelimited string join and therefore collision-constructible across field boundaries. All four are one breaking change, gated by a `hash_algorithm_version` column so existing rows verify under the old formula — the existing legacy-timestamp recovery shim is the precedent for exactly this.

---

# 21. Trust and threat model

## 21.1 Trust boundaries

```
┌ National Core ────────────────────────────────────────────┐
│ trusted: its own services, its CA, its issuers            │
│ semi-trusted: enrolled nodes (authenticated, not obeyed)  │
│ untrusted: everything else                                │
└───────────────────────────────────────────────────────────┘
        ▲  mTLS + signed envelopes + agreement evaluation
        ▼
┌ Hospital Node ────────────────────────────────────────────┐
│ trusted: its own services, its local IdP, its bundles     │
│ semi-trusted: the National Core (authenticated; may NOT   │
│               read clinical data without an agreement)    │
│ untrusted: browsers, handsets, devices, LAN               │
└───────────────────────────────────────────────────────────┘
```

**The Core is semi-trusted from the node's perspective.** This is the architectural expression of §1.3(4): a hospital's node authenticates the Core, accepts bundles from it, and still refuses it clinical data absent an agreement and a basis.

## 21.2 Threats and controls

| # | Threat | Control | Residual |
|---|---|---|---|
| T1 | Compromised node exfiltrates other institutions' data | A node receives only its own cohort's bundles; no cross-node read path exists; every disclosure is recorded on both sides | A node sees its own patients — inherent |
| T2 | Compromised node injects false clinical facts | Envelopes signed by the node key; `origin_node_id` is immutable; a node can only ever assert *its own* authorship; suspension revokes acceptance | A node can lie about its own care — same as any EHR |
| T3 | National administrator reads institutional clinical data | Platform administration carries no data grant; PDP requires work context + basis; disclosure dashboard is mandatory for non-MoHCC | Insider with clinical credentials — mitigated by audit |
| T4 | Stolen node certificate | Short lifetime, revocation list every 5 minutes, SPKI pinning, mTLS both directions | Window up to the revocation ceiling |
| T5 | Bundle tampering | Ed25519 signature verified at install; unknown `kid` rejected; expiry enforced | Compromise of the national signing key — mitigated by rotation and institutional custody |
| T6 | **Rollback attack** — replaying an old bundle to restore revoked authority | Monotonic bundle version enforced at the agent; a version lower than the installed one is refused | — |
| T7 | Offline consent bypass | Consent hard ceiling of 7 days; emergency-only past it; every emergency access audited and reviewed | Genuine emergencies proceed — by design |
| T8 | Browser or handset forges context | All context derives from the signed work-context token; Envoy strips client copies unconditionally | Compromised endpoint with a valid session — mitigated by TTL and revocation |
| T9 | Replay of federation envelopes | `envelope_id` uniqueness + sequence watermarks + signature freshness | — |
| T10 | Node runs modified code | Digest-pinned signed images; release attestation in the fleet registry; capability attestation signed by the node | An operator with cluster admin — inherent; detected by attestation drift |
| T11 | Denial of service on the Core by a chatty node | Per-node rate ceilings, priority queues, backpressure, suspension | — |
| T12 | Local PDP unavailability | `failure_mode_allow: false` means the node **denies** rather than opens — correct, and it makes local PDP availability the node's hard SLO | Clinical stoppage — mitigated by PDP HA and a documented break-glass procedure |
| T13 | Data remanence on decommission | Governed disposition workflow; national projections frozen and marked, never deleted | Physical media handling — operational |
| **T14** | **Hosted-tenant isolation failure** — one hosted organisation reaches another's data | Server-derived trust domain (§4B), enforced organisation and facility predicates, per-domain keys, disclosure records on any cross-organisation access. **Gate: no second organisation onboards to shared hosting until Phase 0 lands** (§2B.1) | Shared infrastructure remains shared — mitigated cryptographically at D1+ |
| **T15** | **Support-access abuse** — a platform operator uses legitimate cluster access to read institutional data | JIT institution-approved, time-boxed, session-recorded access; institution-visible disclosure dashboard; separation of duties (§2B.3) | Real capability remains; the control is detection and contract, and this is stated rather than hidden |
| **T16** | **Cache as covert bulk replication** — a node grows its cohort until it holds the national record | Trigger-event citation on every entry, per-node volume ceilings, cohort reconciliation against live triggers, disclosure record per entry (§19B.7) | A determined operator can generate spurious triggers — detected by cohort-versus-trigger reconciliation |
| **T17** | **Local IdP asserts professional standing** — an institution's AD claims a licence its holder does not have | `trusted_issuer.assertable_claims` allow-list; standing comes only from the VARAPI bundle; a standing claim from an IdP is dropped, not merged (§4A P5) | — |
| **T18** | **Session-domain confusion** — a work token reaches a personal API, or a personal session reaches clinical APIs | Audience validation at every resource server; rejection on audience mismatch is never a downgrade; unregistered routes default to deny (§4A.3) | — |
| **T19** | **Handoff abuse** — a node uses a brokered handoff to obtain a person's national personal session | The resulting session is never returned to the node; `identity_binding_mode=LOCAL_ONLY` blocks brokering entirely; institutions may disable handoff (§4B.3) | — |

## 21.3 Two current-estate hazards this design must not inherit

**The `x-confidential-categories` asymmetry.** The header is stripped at every Envoy route and **regenerated nowhere** — it travels only inside the `x-obligations` JSON. The parser reads both, so the BFF path works and a downstream reading flat headers gets nothing. On a node with unconditional stripping this becomes a silent loss of the confidentiality obligation. It must be emitted as a flat header by the PDP in the same change that turns stripping on.

**The strip-order trap.** The current catch-all route deliberately does *not* strip `x-facility-id`, `x-workspace-id`, `x-department-id`, `x-ward-id`, `x-programme-id`, `x-shift-id`, `x-work-context-token`, `x-purpose-of-use` or `x-workflow-state`, because "the browser genuinely supplies them and the PDP does not regenerate them" — and those names are also absent from `allowed_upstream_headers`. **Turning on unconditional stripping before flipping `context-header-mode` to authoritative and adding those nine names to the upstream allow-list would delete the operating context the entire estate runs on.** The regenerator already exists and is switched off; the sequencing in §22 Phase 0 reflects this exactly.

---

# 22. Migration roadmap

Six phases. **Phase 0 is not preparation for federation — it is the correction of defects that would otherwise be replicated into every institution.** No phase begins before its predecessor's acceptance gate is green.

## Phase 0 — Current-estate safety

*Goal: make the existing estate honest and enforcing, so federation replicates something worth replicating.*

**Repository changes**
- **Consolidate the three request contexts and four header-constant classes.** Retire `libs/tshepo-sdk`'s zero-consumer `TrustContext` after harvesting its fail-closed filter into `shared-core` (whose filter is fail-open). Generate one constant set consumed by Java, TypeScript and Envoy config.
- **Turn on enforcement, in this exact order** (the strip-order trap, §21.3): (1) flip `tshepo.authz.context-header-mode` to authoritative so the PDP regenerates context headers — *the regenerator already exists and is switched off*; (2) add the nine operating-context header names to Envoy's `allowed_upstream_headers`; (3) emit `x-confidential-categories` as a flat header from `buildHeaderMutations`; (4) render ext_authz and the unconditional strip list; (5) enable `experience.trust.propagate-obligations`.
- **Wire consent to real clinical reads** — the PDP's `CLINICAL_RESOURCE_TYPES` gate must match the paths the BFF actually calls, so consent evaluation stops being unreachable.
- **Facility and organisation scoping** — add facility/organisation predicates to clinical repositories (PCT, OROS, inpatient, pharmacy, coverage) and change the PDP's `facility_scope` from "a facility id is present" to a membership assertion.
- **Resolve the FHIR split** — repoint `FhirPublisher` and the gateway default target at `butano-service`; migrate and retire `butano-fhir` and stock `hapi-fhir`.
- **Repair event contracts** — convert the six hand-rolled publishers (pct, oros, pharmacy, referral, tshepo-consent, tshepo-identity) to `CompanionOutboxPublisher`; fix PCT's unrouted-event catch-all; fix the two OROS result payload contracts that silently drop review tasks and critical alerts; set the `notifiable` marker so surveillance stops skipping every encounter; fix the warehouse topic-pattern mismatch.
- **Retire false-success paths** — delete `offline-sync-service`, `jobs-service`, `channels-service`; fix or retire `connector-fhir-adapter`; add retry to notification and remove `MockProvider` for unsupported channels; remove the mobile break-glass fail-open, the consent fail-safe-to-yes, the seeded facility-name fallback and the walk-in registration fallthrough.
- **Scheduler locking** — introduce leader election or advisory locks across all 139 `@Scheduled` beans. **`experience-bff` already runs two replicas with three schedulers, so this is a live defect.**
- **Set the missing service base URLs** and add a startup assertion that every configured peer resolves to a non-loopback address in a cluster profile.

**Schema changes:** `encounter_ref UNIQUE NOT NULL` on PCT encounters; audit hash extended to the six unhashed columns with `hash_algorithm_version`; outbox tables normalised toward the coverage shape.
**Deployment changes:** durable Redis; proven restore from the nightly dump; NetworkPolicies beyond the single existing one; `extAuthz.enabled: true`.
**Tests:** a negative control proving each newly-enabled gate denies when it should; a consent-revocation test proving a clinical read changes; a restore drill with a verification report.
**Gate:** ext_authz on in the live estate; unauthenticated probes 401 across the estate; a revoked consent demonstrably blocks a read; restore proven; zero known false-success paths.
**Rollback:** every enforcement flag is independently reversible; the strip list and ext_authz revert together.

## Phase 1 — Node-aware domain foundation

*Goal: the domain model can express a node, a trust domain and an origin — with nothing yet deployed remotely.*

**Repository:** `trust_domain`, `federation_agreement`, `data_sharing_policy`, `local_authority` in org-registry; `deployment_node` and the fleet tables in a new `services/fleet-service`; federation metadata in `EventEnvelope`, `CompanionOutboxPublisher.serializeEnvelope`, `OutboxRow`, `TrustContext`, `RequestContext`; **reconcile the `pod_id` literal mismatch** (`'national-spine'` in SQL defaults versus `"national"` in `FederationAuthority`) and replace `OutboxEventBuilder`'s `"unknown"` fallback with a hard failure.
**Schema:** federation band **`V600–V629`** in every co-edited service — it clears PCT's `V500` head and the telemedicine `V500–V529` lease, satisfying the estate's "reserve by numeric distance" rule. **`out-of-order: true` must land in the same wave** for org-registry, pharmacy, tshepo-consent, tshepo-audit and tshepo-identity, which do not set it — otherwise a `V600` migration applies now and silently never runs on a peer that later adds a lower number.
**Deployment:** `node_id` threaded into every pod; the National Core self-registers as `NODE-NATIONAL-CORE`.
**Dependencies:** Phase 0's publisher conversion — the six hand-rolled publishers must be converted first or federation metadata reaches none of them.
**Tests:** contract test that every outbound event carries a complete federation block; a test that no federated reference contains a bigserial.
**Gate:** every clinical write in the estate carries `origin_node_id` and `origin_record_id`; the audit chain is node-scoped.
**Rollback:** additive columns, nullable until backfilled; dual-emit policy already exists as the phased-rollout precedent.

## Phase 2 — Deployable Hospital Node

*Goal: a hospital runs vNext standalone and passes a disconnection rehearsal. **This is the delivery target.***

**Repository:** `values-hospital-node.yaml` and the eight missing chart primitives (§17.1); **Bundle Agent** (harvesting `tshepo-offline-service`'s working signed-pack, capability-token and JWKS-cache machinery); **bundle publishers** — hang signing and versioning on `PolicyRuleBundleBuilder`, *which already exists with a passing test and no production caller*; node CSR and X.509 issuance in `tshepo-keys` (**the one genuinely absent primitive** — there is no CA anywhere today); multi-issuer session validation with a real issuer allowlist reading the existing `trust_issuer_system` table (**which exists and nothing reads**); **fix `KeycloakAdapter`'s permanent `jwtProcessor = null` cold-start failure** — a node that boots while the IdP is briefly unreachable is otherwise dead until restarted; local work-context minting proven against the standing bundle rather than six live upstreams; runtime endpoint discovery in shell and mobile.
**Schema:** `config_binding`; node-scoped audit chains; local identifier allocation blocks.
**Deployment:** HA Postgres/Kafka/Redis/MinIO; GitOps; cert-manager; External Secrets; real registry; observability; `observability-service`'s existing `/ops/heartbeat`, `/health/summary` and `/metrics/lag` extended with node capacity and federation queue depth.
**Also in Phase 2 (v1.1 additions):** the **session-domain split** — Keycloak audience mappers per domain (none exist today), per-domain client registration, audience validation in every resource server, and replacement of the client-side `OperationalMode`/`navZone` tables with a server-derived domain claim defaulting to deny; the **Professional Status** surface rendered from the standing bundle; the three domain leaks closed (§4A.3); and the node configuration document extended with the experience-routing block and `clinical_write_authority` (§16A).
**Tests:** the **seven-day disconnection rehearsal** (§23) in a lab; bundle expiry behaviour at every ceiling; local login and work-context entry with the Core unreachable; the session-separation tests A28–A32.
**Gate:** all twelve autonomy capabilities (§5.1) pass with the Core network-partitioned for seven days; every ceiling behaves as specified; no fabricated success anywhere in the disconnection log; **no cross-domain token is accepted anywhere**.
**Rollback:** the node profile is additive; the National Core is unaffected. Audience validation is enabled per resource server, so it can be rolled back service by service.

## Phase 2.5 — Commissioning and consumption

*Goal: an organisation can be onboarded through one governed journey, into any consumption profile.*

**Repository:** join the two tracks (§22A) — extend the **shipped** organisational rails (Bootstrap Mode, platform-origin two-person approval, authorised representatives, facility claim, PIC, invitations, regulator bootstrap) with the node track that does not yet exist; add the **Node Administrator** role and the node commissioning states; build the **Bootstrap Agent** (§22B); add `service_agreement`, `service_responsibility_profile` and `support_access_policy` (§3A); write the retrospective design record for Bootstrap Mode, which ships today with none.
**Deployment:** hosted provisioning automation for profiles A and B; the signed offline bundle for air-gapped installs.
**Tests:** A38–A43 (authority expiry, role separation, hosted isolation, MoHCC-hosted private node control).
**Gate:** an organisation can be commissioned end-to-end into each of the four profiles; commissioning authority demonstrably expires; **no role grants both cluster administration and clinical access**.
**Rollback:** commissioning is additive to the existing rails, which continue to work unchanged.

## Phase 3 — Federation Gateway

**Repository:** `services/federation-gateway` (both profiles); disclosure engine; reconciliation reporter; the cross-node referral extension of PCT's state machine; national Butano ingestion from envelopes only. **v1.1 additions:** the **Shared-Care Cache** (§19B) as a separate store with its cohort, revocation and reconciliation machinery; **PHR disclosure** as a labelled, time-boxed envelope type; provenance classes (§19A.3) applied across the personal and clinical lanes.
**Schema:** the `fed_*` tables both ends; `fed_disclosure_record`; the Shared-Care Cache schema.
**Deployment:** mTLS between node and Core; the federation directory.
**Tests:** replay after seven days; sequence-gap detection; conflict taxonomy end-to-end; a referral round trip with attachments; quarantine on a bad signature; A33–A37 and A44–A46 (cache cohort, sensitivity exclusion, freshness labelling, provenance).
**Gate:** a node contributes and receives without a single overwrite; every transfer produces a disclosure record on both sides; the reconciliation report is complete and human-readable; **the cache contains only the lawful cohort, and every cached item shows origin and freshness**.
**Rollback:** contribution and caching are independent per-node feature flags; local care is unaffected by disabling either.

## Phase 4 — Non-MoHCC sovereignty and hosted isolation

**Repository:** institutional IdP federation; institutional key custody (`key_custody_mode = INSTITUTIONAL`); the disclosure dashboard; per-institution sharing policy authoring; institution-controlled upgrade windows. **v1.1 additions:** the **Dedicated Hosted isolation tiers D1–D6** (§2B.1) with institution-held KEK under envelope encryption; JIT institution-approved support access with session recording; and the hierarchical trust-domain structure (`parent_trust_domain_id`, §6A.3).
**Gate:** an institution can prove what left its walls, can refuse a national request it has not agreed to, **and can see every support session that touched its environment**.

## Phase 5 — Production pilot

One large central hospital. Seven-day disconnection under real clinical load; reconnection and conflict tests; failover; restore; independent security assessment; load and capacity testing against measured volumes — **which also replaces the indicative sizing bands of §17A.2 with measured figures (A45)**.
**Gate:** clinical sign-off, security sign-off, and a data-protection sign-off from the institution's own controller.

---

# 22A. Commissioning — joining institutional authority to the node track **[O/T]**

## 22A.1 A correction, and why it improves the design

The brief treats an "Impilo Commissioning and Bootstrap Package" as an existing artefact to preserve. **Measured for this version: no document of that name exists, and the terms `Deployment Commissioner`, `Bootstrap Manifest`, `Node Administrator` and `AWAITING_COMMISSIONING` appear nowhere in the repository.**

That is good news, not bad. Most of the *substance* exists — and not merely as doctrine, but **already shipping in code**. v1.1 therefore does not specify a parallel commissioning system. It maps the brief's vocabulary onto the shipped rails and adds only what is genuinely missing.

| Brief's concept | What exists today | Status |
|---|---|---|
| Deployment Commissioner — time-limited, no permanent superuser | Bootstrap Mode: `wgv_bootstrap_state` (`bootstrap_open`, `bootstrap_closed_at`, `recovery_mode`), `BootstrapService.activate/close`, `/internal/v1/bootstrap`, a `/bootstrap` UI wizard, **a 24-hour bootstrap-role TTL**, MFA required, one-time-token or signed-authorisation entry only | **Shipping.** And there is **no design document for it** — v1.1 supplies one retrospectively |
| Two-person approval, initiator may not approve | `TwoPersonApprovalService`, `wgv_platform_action_approval` with `UNIQUE(access_request_id, approver_user_id)` | **Shipping** |
| Origin authority that hands over | Platform Origin Administrator + country operation, with ratified doctrine stating it is "an origin key, not a daily operator" and that continued origin governance is "an exceptional state that must be flagged" | **Shipping + ratified** |
| Organisation Authorised Officer | `wgv_authorised_representative`, `AuthorisedRepresentativeService`, org-registry claim and invitation rails | **Shipping** |
| Facility Administrator | `tuso.facility_admin_appointment` — **already that exact role name**, closed role vocabulary, per-role active uniqueness, gated on FCV `allowed_on_platform` | **Shipping** |
| Practitioner-in-Charge | varapi PIC assignment + eligibility assessment; tuso PIC nomination | **Shipping** |
| Signed single-use invitations | `org_registry_invitation` (hashed token), `varapi.provider_claim_token` | **Shipping** |
| Regulator cold start | `wgv_regulator_bootstrap_request`, role locked to `FOUNDING_REGULATOR_ADMINISTRATOR`, one live request per organisation | **Shipping** |
| "Bring your organisation or facility to Impilo" | Live routes: `/facility/claim`, `/facility/register`, `/site/register`, `/citizen/provider-claim`, `/organization-admin/onboarding`, `/registry/intake`, and 13 delegated `/work/administration-governance/onboard/*` lanes | **Shipping** |
| Registry steward | Steward routing exists in indawo, tuso and the BFF; **no first-class steward role table** | **Partial** |
| **Node Administrator, node commissioning states, Bootstrap Manifest, installer** | nothing | **Absent — the genuine gap** |

## 22A.2 The two tracks

**Track A — institutional authority** (exists; extended with the consumption choice):

```
Participation request → Authority review → Trust domain established
→ Organisation established or claimed → Facilities claimed → PIC confirmed
→ Officers and stewards appointed → Consumption model selected
→ Agreements approved (service + federation + data-sharing) → Ready for provisioning
```

**Track B — technical node** (new):

```
Node registered → Node ID + enrolment token issued → Infrastructure validated
→ GitOps overlay generated → Platform dependencies installed → Certificates issued
→ Bundles installed → Services started → AWAITING_COMMISSIONING
→ Node configuration published → Federation handshake → Disconnection test → Go-live approval
```

## 22A.3 Synchronisation points **[D]**

```mermaid
sequenceDiagram
  autonumber
  participant AO as Authorised Officer (Track A)
  participant CORE as National Core
  participant NA as Node Administrator (Track B)
  participant NODE as Node
  AO->>CORE: participation request + authority evidence
  CORE->>CORE: trust domain · organisation · facilities (FCV legitimacy) · PIC · officers
  Note over CORE: S1 — a node cannot be registered for a facility that is not<br/>claimed and legitimacy-allowed. Today that is 5 of 7,285 facilities.
  AO->>CORE: select consumption profile; approve service + federation agreements
  CORE->>CORE: service_agreement + responsibility profile ACTIVE
  Note over CORE: S2 — no node registration without an ACTIVE service agreement.
  CORE->>NA: Node ID, enrolment token, pinned release, approved facility scope, manifest
  NA->>NODE: run the Bootstrap Agent (§22B)
  NODE->>CORE: CSR + capability attestation
  CORE-->>NODE: certificates + bundles
  NODE->>NODE: AWAITING_COMMISSIONING · readiness + disconnection tests
  NODE->>CORE: signed commissioning report
  AO->>CORE: countersign go-live (Organisation Authorised Officer)
  Note over CORE: S3 — the node reaches ACTIVE only on the Officer's countersignature,<br/>never on the Node Administrator's own authority.
  CORE->>CORE: destroy enrolment credential; close commissioning authority
  Note over CORE: S4 — commissioning authority expires. Bootstrap Mode's<br/>existing 24h role TTL and close-out is the mechanism.
```

**The five separation rules, each with an acceptance test:**

| Rule | Test |
|---|---|
| A Node Administrator cannot register or activate an unapproved facility | A38 |
| A node cannot reach `ACTIVE` before organisational and facility authority exist | A38 |
| The Organisation Authorised Officer gains **no** cluster administration | A40 |
| The Node Administrator gains **no** clinical access and no application role by default | A39 |
| Commissioning authority **expires**; no permanent bootstrap superuser survives | A41 |

## 22A.4 Provisioning by consumption profile

| Profile | Track B |
|---|---|
| **National Shared Hosted** | No node. The platform provisions the organisation logically within the National Core: trust domain, organisation, facilities, keys, storage prefixes, application configuration, initial appointments and invitations. **No installer, no download.** |
| **Dedicated Hosted** | The Fleet Service provisions a dedicated environment (isolation tier D1–D6) on national infrastructure. **No customer-side installer.** |
| **Managed On-Premises** | The approved Node Administrator receives the Bootstrap Manifest and runs the agent; the accredited operator manages the platform thereafter. |
| **Sovereign On-Premises** | As above, with the institution operating the platform and, where approved, holding its own signing keys and IdP. Advanced operators may bypass the agent and consume the GitOps overlay directly. |

---

# 22B. Node Bootstrap Agent specification **[T]**

## 22B.1 What it is — and what it is not

A **small, signed agent**, not a monolithic vNext executable. It carries no application code and no clinical logic. It validates, enrols, provisions and hands over — then its credential is destroyed.

## 22B.2 The Bootstrap Manifest

Issued by the Fleet Service, signed by the national bundle key, and verified before the agent does anything:

```jsonc
{
  "node_id": "…", "node_code": "NODE-PARI-01",
  "trust_domain_id": "…", "organisation_id": "…",
  "approved_facility_scope": ["facility_uuid …"],
  "deployment_profile": "HOSPITAL_STANDARD",
  "consumption_profile": "MANAGED_ON_PREMISES",
  "pinned_release": { "version": "…", "chart_version": "…", "image_digest_manifest_sha": "…" },
  "sbom_ref": "…", "checksums": { … },
  "national_trust_anchors": { "node_ca": "…", "bundle_signing_jwks": "…" },
  "required_bundles": ["policy","standing","consent","relationship","revocation","terminology"],
  "enrolment": { "one_time_credential_ref": "…", "expires_at": "…" },
  "approvers": { "organisation_authorised_officer": "…", "node_administrator": "…" },
  "disconnected_operation": { "permitted": true, "max_days": 7 },
  "expires_at": "…",
  "signature": { "alg": "Ed25519", "kid": "impilo-manifest-2026Q3", "value": "…" }
}
```

## 22B.3 The fifteen steps

| # | Step | Automated | Human approval |
|---|---|---|---|
| 1 | Validate host: CPU, RAM, storage, networking, DNS, TLS reachability, **time synchronisation** | ✔ | |
| 2 | Verify the manifest signature and expiry | ✔ | |
| 3 | Validate the approved trust domain and facility scope against the manifest | ✔ | |
| 4 | Install or validate the approved Kubernetes distribution | ✔ | |
| 5 | Configure the image registry, or mount the signed offline bundle | ✔ | |
| 6 | Generate the GitOps overlay | ✔ | |
| 7 | Configure namespaces, storage classes and secrets integration | ✔ | **Secrets backend choice** |
| 8 | Retrieve pinned OCI images and Helm packages; verify digests against the SBOM | ✔ | |
| 9 | Request node certificates (CSR with the one-time credential) | ✔ | |
| 10 | Retrieve and verify all six bundles | ✔ | |
| 11 | Start tiers in order: infrastructure → trust → registry → clinical → experience → federation | ✔ | |
| 12 | Enter **`AWAITING_COMMISSIONING`** | ✔ | |
| 13 | Run conformance, backup, **restore** and **disconnection** tests | ✔ | |
| 14 | Destroy the one-time enrolment credential | ✔ | |
| 15 | Produce a **signed commissioning report** | ✔ | **Officer countersignature to reach ACTIVE** |

Time synchronisation is step 1 for a reason: every signature, TTL, staleness ceiling and audit chain entry in this architecture depends on the node's clock being right, and a node with a wrong clock fails in ways that look like security incidents.

## 22B.4 Install modes

| Mode | Mechanism |
|---|---|
| **Connected** | Agent pulls images, charts and bundles directly |
| **Partially connected** | Manifest and certificates online; images and charts from a local mirror |
| **Air-gapped** | A signed offline bundle carrying OCI images, charts, policy and terminology packs and trust anchors; enrolment completed by an out-of-band signed exchange. The estate already uses `docker save`/`load` transfer as a build technique — this formalises it as a distribution channel |
| **Existing cluster** | Skips steps 4–5; validates the cluster meets the profile's requirements and refuses if not |
| **Managed appliance** | Pre-built image with the agent embedded; steps 1–11 on first boot |
| **Sovereign advanced** | The operator takes the GitOps overlay and runs their own pipeline; the agent is used only for enrolment and attestation |

## 22B.5 What the agent never does

It never creates clinical users, never grants clinical access, never mints a work context, never becomes a standing administrator, and never survives commissioning. **The Node Administrator who runs it holds no application role by default** — installing the system and using it are different authorities held by different people (§22A.3).

---

# 23. Proof and acceptance test plan

**Standing rule, inherited from the recovery:** a capability is proven by a *positive* control that exercises the real path, never by the absence of an error and never by a mocked test. A fail-closed check is proven by watching a success reach it.

| # | Test | Method | Pass condition |
|---|---|---|---|
| **A1** | No client-supplied authority | Replay every clinical request with forged `X-Tenant-ID`, `X-Facility-ID`, `X-Provider-ID` and the eleven visibility headers | Forged values have **zero** effect on any response; each is stripped or refused |
| **A2** | Consent actually gates a read | Read a record; revoke consent; read again | Second read is refused with a consent reason code — the exact test that fails in the current estate |
| **A3** | Facility scoping | Facility A credentials request facility B's clinical rows | Refused, and audited |
| **A4** | National admin ≠ clinical access | A platform administrator attempts a clinical read in an institutional trust domain | Refused; the attempt appears on the institution's disclosure dashboard |
| **A5** | **Seven-day disconnection** | Partition the node for 7×24 h under simulated clinical load; exercise all twelve autonomy capabilities daily | All pass; every ceiling behaves as specified; **no fabricated success in the log** |
| **A6** | Ceiling behaviour | Age each bundle past soft and hard ceilings | Warn then fail-closed per §5.3; UI banner present; writes flagged |
| **A7** | Offline consent integrity | Withdraw consent nationally on day 2 of a disconnection | Node honours it on reconnect; the day 3–7 records are **not** contributed; suppression is recorded |
| **A8** | Break-glass offline | Emergency access with the Core dark | Proceeds, audits locally **first**, queues for review; a break-glass that cannot be audited does not proceed |
| **A9** | Reconnection ordering | Reconnect with a 7-day backlog | Revocation and consent land before outbound contribution is accepted |
| **A10** | No overwrite | Attempt a federated write against a record whose `origin_node_id` differs | Rejected `NOT_ORIGIN_AUTHORITY`; the database constraint blocks it independently |
| **A11** | Duplicate patient | Register the same person at two nodes offline | Both stand; a link is proposed; **a clinician confirms**; both CPIDs resolve after repoint |
| **A12** | Referral round trip | Cross-node referral with images while the receiver is offline | Queued with a visible status; delivered on reconnect; delivery and acceptance visible to both humans |
| **A13** | Disclosure completeness | Compare every transfer against `fed_disclosure_record` on both sides | Exact match; a divergence raises an alert |
| **A14** | Rollback attack | Replay an old bundle | Refused on monotonic version |
| **A15** | Quarantine | Send an envelope with a bad signature | Quarantined; node marked; **local care unaffected** |
| **A16** | Scheduler safety | Run every service at `replicas: 2` for 24 h | No duplicated outbox publish, sweep, reminder or print job |
| **A17** | HA failover | Kill the Postgres primary, a Kafka broker, and a cluster node in turn | Clinical operations continue within the stated RTO |
| **A18** | Restore | Restore from off-site backup into a clean cluster | Verified clinical dataset; audit chains verify |
| **A19** | Endpoint discovery | Enrol a stock national mobile build to a node by QR; then fail over | Reaches the node with no rebuild; clinical write scopes **refuse** national fallback |
| **A20** | Upgrade and rollback | Upgrade a node one ring; roll back | No configuration loss; `config_binding` survives; federation resumes |
| **A21** | Sensitivity offline | Access a SPECIALLY_PROTECTED record offline with and without a fresh consent bundle | Permitted only with a fresh bundle or an audited emergency path |
| **A22** | Audit chain integrity | Verify each node chain; verify national anchors | Chains verify; anchors match; a tampered row is detected — including in the six columns the current hash omits |

## 23.1 v1.1 additions — domains, sessions, consumption, records

| # | Test | Method | Pass condition |
|---|---|---|---|
| **A23** | Platform admin ≠ clinical data | A platform administrator attempts clinical reads at a node and nationally | Refused everywhere; attempts appear on the institution's disclosure dashboard |
| **A24** | Node admin ≠ clinical data; national admin ≠ local administration | Node Administrator attempts a clinical read; national administrator attempts institutional administration | Both refused; **neither role carries an application role by default** |
| **A25** | Facility administrator ≠ My Life | A facility administrator attempts to read a staff member's PHR, wellness and device data by every route | Refused on every route, including unregistered ones (which default to deny) |
| **A26** | Employer ≠ regulatory correspondence | An employer attempts to read a provider's applications, correspondence, CPD detail and portfolio | Refused; only the standing-bundle subset is visible |
| **A27** | Local IdP cannot assert standing | Institution IdP issues a token claiming a licence and scope | Claims dropped, not merged; standing resolves only from the VARAPI bundle; the attempt is audited |
| **A28** | Work token ≠ personal APIs | Present a node work token to My Life, PHR, wellness and Marketplace APIs | Rejected on audience; never partially honoured |
| **A29** | Personal token ≠ clinical APIs | Present a personal session to node clinical read and write APIs | Rejected on audience |
| **A30** | Node → My Life creates a separate session | Open My Life from a node | A new national session is created; **the node never receives it**; with `LOCAL_ONLY` binding, full re-authentication is required |
| **A31** | Clinical work never falls back | Partition a client from its node | Clinical scopes refuse or queue; **no clinical request reaches the National Core**; the mode is shown to the user |
| **A32** | Personal/public may transition | Same partition, personal scopes | Transition to national succeeds where the node configuration permits it |
| **A33** | Two nodes, no mixing | One provider works at Node A and Node B | Distinct audiences and sessions; A's token is rejected at B; per-node one-live-context holds |
| **A34** | Hosted and on-prem are contract-identical | Run the full application contract suite against a hosted node and an on-premises node | Identical results; no node-only or host-only code path |
| **A35** | Hosted organisations are isolated | Organisation X attempts to reach organisation Y's data in shared hosting, by every route | Refused; **this test gates onboarding the second hosted organisation** |
| **A36** | MoHCC-hosted private node stays institutionally controlled | MoHCC platform operators attempt ordinary clinical access to a hosted private node | Refused; any support access is JIT, institution-approved, recorded and visible on the institution's dashboard |
| **A37** | Support session visibility | Perform an approved support session | Fully itemised on the institution's disclosure dashboard with actor, purpose, duration and scope reached |
| **A38** | Commissioning order | Attempt to register and activate a node for an unclaimed facility, and to activate before officer countersignature | Both refused |
| **A39** | Node Administrator has no application role | Enumerate the Node Administrator's effective permissions after go-live | No clinical, no application, no work-context minting capability |
| **A40** | Authorised Officer has no cluster role | Enumerate the Officer's effective permissions | No Kubernetes or platform capability |
| **A41** | Bootstrap authority expires | Complete commissioning, then attempt to use bootstrap and enrolment credentials | Both refused; the one-time credential is destroyed; the 24-hour role TTL has elapsed |
| **A42** | Sizing profile fidelity | Deploy each profile and exercise its capability packs | Each starts, passes its dependency closure check, and refuses packs it does not carry |
| **A43** | Air-gapped install | Install from the signed offline bundle with no network | Node reaches `AWAITING_COMMISSIONING` and passes readiness tests |
| **A44** | Patient-reported labelling | Submit PHR content, then view it as a clinician | Labelled `PATIENT_REPORTED` everywhere; verification produces a **new** `CLINICIAN_VERIFIED` item citing the original, never an in-place upgrade |
| **A45** | Sizing bands replaced by measurement | Pilot instrumentation reports concurrent sessions, encounters, orders, imaging, storage growth and queue depth | Measured percentiles published against §17A.2; the estimates are re-issued or corrected |
| **A46** | Cache cohort integrity | Reconcile cache membership against live triggering events; attempt entry without a trigger; exit the cohort | Only the lawful cohort is present; entry without a trigger is refused; exit expires or withdraws content per policy |
| **A47** | Cache sensitivity floor | Attempt to pre-position SPECIALLY_PROTECTED content without category-specific authority | Refused; excluded content is **declared** as withheld, not silently omitted |
| **A48** | Cache freshness honesty | View cached national items during a seven-day outage | Every item shows origin facility and age; unavailable sections say so; **no empty section reads as "nothing to report"** |
| **A49** | Reconnection ordering with cache | Revoke consent nationally on day 2, reconnect on day 7 | Revocation applied to the cache **before** any queued clinical record is contributed |

---

# 24. Prioritised engineering backlog

Priority reflects *what unblocks the most* and *what is riskiest to defer*. P0 items are current-estate defects that federation would otherwise multiply.

| # | Item | Pri | Phase | Why here |
|---|---|---|---|---|
| 1 | Enforcement sequencing: context-header authority → upstream allow-list → flat `x-confidential-categories` → ext_authz + strip → obligation propagation | **P0** | 0 | Every other control is downstream of this, and the wrong order deletes the estate's operating context |
| 2 | Consent on the clinical read path | **P0** | 0 | Today a revoked consent changes nothing |
| 3 | Facility/organisation scoping in clinical repositories + PDP membership | **P0** | 0 | One organisation can read another's records |
| 4 | Scheduler locking across 139 `@Scheduled` beans | **P0** | 0 | Already double-firing at `replicas: 2` in the live BFF |
| 5 | Convert the six hand-rolled publishers to `CompanionOutboxPublisher` | **P0** | 0→1 | Federation metadata otherwise reaches none of PCT, OROS, pharmacy, referral, consent, identity |
| 6 | Retire `offline-sync`, `jobs`, `channels`, `butano-fhir`, stock `hapi-fhir` | **P0** | 0 | Fabricated success must not be federated |
| 7 | Missing service base URLs + startup peer-resolution assertion | **P0** | 0 | The node closure cannot be built on the current values files |
| 8 | Durable Redis, proven restore, NetworkPolicies | **P0** | 0 | Data-loss exposure |
| 9 | `trust_domain` / `deployment_node` / agreement / policy schema | P1 | 1 | The vocabulary everything else references |
| 10 | Federation metadata into envelope, outbox, contexts, audit | P1 | 1 | The provenance substrate |
| 11 | `encounter_ref UNIQUE NOT NULL` | P1 | 1 | The one identifier gap on the critical path |
| 12 | Node CA: CSR intake and X.509 issuance in `tshepo-keys` | P1 | 2 | The single genuinely absent trust primitive |
| 13 | Bundle publishers on `PolicyRuleBundleBuilder` + Bundle Agent from `tshepo-offline` | P1 | 2 | Both halves largely exist; this joins them |
| 14 | Fix `KeycloakAdapter` cold-start (`jwtProcessor = null` is permanent) | P1 | 2 | A node that boots during an IdP blip is dead until restarted |
| 15 | Multi-issuer validation reading `trust_issuer_system` | P1 | 2 | The table exists and nothing reads it |
| 16 | Local work-context mint from the standing bundle | P1 | 2 | Today it needs six live upstreams |
| 17 | Hospital Node Helm profile + the eight missing chart primitives | P1 | 2 | The deployment unit itself |
| 18 | HA data plane (Postgres, Kafka, Redis, MinIO) | P1 | 2 | Hospital uptime |
| 19 | Runtime endpoint discovery (web + mobile) | P1 | 2 | Removes build-time coupling and the LAN prohibition |
| 20 | Local Butano projection + retire the split-brain | P1 | 2 | Clinical record access offline |
| 21 | Federation Gateway both ends | P2 | 3 | The protocol |
| 22 | Disclosure engine + `fed_disclosure_record` | P2 | 3 | Legal defensibility |
| 23 | Cross-node referral | P2 | 3 | The highest-value federated journey |
| 24 | National Butano ingestion from envelopes | P2 | 3 | The longitudinal record |
| 25 | Reconciliation reporter + conflict surfaces | P2 | 3 | Reconnection safety |
| 26 | Fleet & Release Service + upgrade rings | P2 | 2→5 | Fleet operations |
| 27 | `config_binding` hierarchy + per-facility integrations | P2 | 2→3 | Node integrations |
| 28 | Institutional IdP + institutional key custody | P3 | 4 | Non-MoHCC sovereignty |
| 29 | Disclosure dashboard | P3 | 4 | Institutional trust |
| 30 | Facility Edge profile | P4 | post-pilot | Explicitly not first |
| **31** | **Keycloak audience mappers + per-domain clients + audience validation** | **P1** | 2 | **None exist today.** Every session-separation invariant (P1–P8) depends on this one change |
| **32** | Server-derived domain claim replacing `OperationalMode`/`navZone`; unregistered routes default to **deny** | **P1** | 2 | Today two hand-maintained tables disagree, and unregistered routes are not citizen-blocked at all |
| 33 | Professional Status surface from the standing bundle | P1 | 2 | No new data flow; unblocks the node login experience |
| 34 | Close the three domain leaks (personal documents on the clinical lane; mis-zoned routes; duplicate enumerations) | P1 | 2 | Each is a live boundary violation |
| 35 | `service_agreement` + `service_responsibility_profile` + `support_access_policy` | P1 | 2.5 | Nothing can be commissioned into a consumption profile without them |
| 36 | Node Administrator role + node commissioning states + Bootstrap Manifest | P1 | 2.5 | The only genuinely absent part of commissioning |
| 37 | Node Bootstrap Agent (four install modes) | P1 | 2.5 | The on-premises delivery vehicle |
| 38 | Retrospective design record for Bootstrap Mode | P2 | 2.5 | It ships today with **no design document** — a governance gap, not a code gap |
| 39 | `clinical_write_authority` + extended node configuration document | P1 | 2 | Makes central-vs-local primacy structural rather than advisory |
| 40 | Shared-Care Cache: separate store, cohort machinery, revocation, freshness UI | P2 | 3 | Closes the disconnected-continuity gap without becoming replication |
| 41 | PHR provenance classes + review-produces-new-item rule | P2 | 3 | Prevents patient-reported data masquerading as verified |
| 42 | PHR disclosure envelope + consent-centre surfacing | P2 | 3 | Person-authorised sharing without general My Life access |
| 43 | Hierarchical trust domains (`parent_trust_domain_id`) | P2 | 4 | Lets the MoHCC determination land later without re-modelling |
| 44 | Dedicated Hosted isolation tiers D1–D6 + institution-held KEK | P2 | 4 | The commercial offer for institutions without hardware |
| 45 | JIT support access with session recording + disclosure dashboard entries | P2 | 4 | Makes §2B.3's honesty enforceable |
| 46 | Sizing profiles + pilot instrumentation that replaces the estimates | P3 | 2→5 | Bands are unvalidated until A45 |

---

# 25. Architecture decision records

| ADR | Decision | Alternatives rejected | Consequence |
|---|---|---|---|
| **ADR-01** | Hub-and-spoke federation; all cross-site exchange traverses the Federation Gateway | Peer-to-peer mesh; database replication; cross-site Kafka | The Core is a governance chokepoint by design; peer-to-peer needs a national route |
| **ADR-02** | The node that creates a clinical fact is authoritative for it; the Core holds projections | Central authority with local caches | The Core cannot correct a hospital's record; corrections are amendments at origin |
| **ADR-03** | `trust_domain_id` is the data-controller boundary; `node_id` never appears in a business predicate | Reusing `tenant_id`; treating a node as a tenant | Migrating `tenant_id` is unavoidable work |
| **ADR-04** | Signed, versioned, node-scoped bundles carry policy, standing, consent, relationship, revocation and terminology | Synchronous PDP calls to the Core; Keycloak database replication | Bundle freshness becomes a first-class clinical-safety property |
| **ADR-05** | Many trusted issuers, one claim-binding contract; **an IdP may assert who logged in, never professional standing** | Federating Keycloak realms; syncing user databases | Institutions keep their IdP; standing stays national |
| **ADR-06** | `butano-service` is the only FHIR store, instantiated as a local projection and a national projection | Keeping `butano-fhir`; keeping stock HAPI; a single central store | Two retirements and a data migration |
| **ADR-07** | Local database primary keys stay bigserial; federated references use UUID/ULID pairs | Re-keying ~90 services | Additive migration; a contract test enforces the boundary |
| **ADR-08** | Audit chains are per-node and never interleaved; the Core stores signed chain-head attestations | One national chain; merging events | A breaking hash change, versioned per row |
| **ADR-09** | Consent bundles carry a **negative assertion** ("N active directives as of T") | Positive directives only | Without it a node cannot distinguish "no consent exists" from "the bundle does not cover this subject" — two outcomes the engine already treats differently |
| **ADR-10** | Break-glass grants are scoped to a resource or patient, not to an actor for a TTL | Retaining the current actor-wide grant | Fixes a live over-grant: today one active request covers *any* resource for that actor until expiry |
| **ADR-11** | Runtime endpoint discovery via a signed node configuration document; failover is scope-limited | Build-time endpoints; blanket national fallback | Clinical writes may never silently fall back to the Core |
| **ADR-12** | Images and documents are never bulk-replicated; index up, fetch on demand under a fresh policy evaluation | Full replication | Cross-node image viewing needs connectivity — accepted |
| **ADR-13** | Statutory public-health reporting is mandatory and never negotiable, for every trust domain | Making it agreement-dependent | Institutions cannot contract out of notifiable-disease reporting |
| **ADR-14** | Non-MoHCC defaults are INDEX_ONLY and negotiate upward only | Contribution-by-default | Slower onboarding; defensible data protection |
| **ADR-15** | Local enforcement fails closed (`failure_mode_allow: false`) at the node | Fail-open for availability | Local PDP availability becomes the node's hard SLO |
| **ADR-16** | One product, profile-selected; a node-only code path is a defect | A hospital edition | Profiles and feature flags carry all variation |
| **ADR-17** | Service consumption is a dimension independent of deployment profile and federation | Treating "on-premises" as a single bundled choice | Four consumption profiles crossed with node profiles; more combinations, but each is expressible |
| **ADR-18** | A Hospital Node may be hosted nationally or institutionally; the profile describes function and authority, not location | Equating node with on-premises (v1.0's prose) | MoHCC can host a node it does not control; `hosting_model` is data |
| **ADR-19** | Infrastructure or platform operation grants **no** application-data authority | Inferring controllership from hosting | Controls must be cryptographic, procedural and evidentiary — and the real capability is stated, not denied |
| **ADR-20** | Personal, professional and work sessions are separate, audience-bound and **non-exchangeable** | One session with a domain flag; token exchange between domains | Keycloak audience mappers become mandatory; three logins where a person holds three capacities |
| **ADR-21** | My Life is never rendered as an institution-owned local domain, and is never locally substituted during disconnection | A cached personal view for provider convenience | Providers lose personal services when the link is down — accepted, and stated on screen |
| **ADR-22** | Full My Professional stays individual-facing; a node shows **Professional Status** derived from the standing bundle | Local professional workspaces owned by the employer | No new data flow; offline-correct by construction |
| **ADR-23** | Clinical scopes never fall back from node to National Core | Blanket failover for availability | Clinical work stops rather than writing to the wrong authority |
| **ADR-24** | Hosted facilities are central-primary; Hospital Nodes are local-primary and never switch | Central-primary with local failover | No split-brain; no dependence on detecting the moment of failure |
| **ADR-25** | Commissioning authority and node operation are separate roles that never merge | One installer-administrator | The Officer countersigns; the Node Administrator installs; neither inherits the other |
| **ADR-26** | Bootstrap is a small signed agent plus GitOps, not a monolithic installer | A large privileged executable | The agent carries no application code and does not survive commissioning |
| **ADR-27** | PHR and SHR are different products with different authorities | One record with a citizen view | The PHR is never the legal clinical record; the SHR is never the person's own record |
| **ADR-28** | Patient-entered data is provenance-labelled, and verification produces a **new** item rather than upgrading the original | In-place promotion to verified | Both the assertion and the verification stay separately attributable |
| **ADR-29** | The Shared-Care Cache is cohort-scoped by lawful relationship, never catchment-based | Pre-positioning for a catchment population | Better emergency readiness forgone in exchange for a defensible disclosure boundary |
| **ADR-30** | The Shared-Care Cache is a **separate store**, not merged into Node Butano | Merging for a single unified timeline | A clinician can always tell local from cached; revocation and retention stay independent; re-contribution is structurally impossible |
| **ADR-31** | Node sizing is expressed through profiles, capability packs and resource classes — never a separate edition or codebase | Compact/Standard/Enterprise as products | One artefact set; differences are configuration and topology |
| **ADR-32** | MoHCC adopts hierarchical trust domains; the model supports all three options until the legal determination | Committing to a single national domain now | Implementation proceeds; the determination changes configuration, not architecture |

---

# 26. Risks and unresolved policy decisions

## 26.1 Engineering risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | Phase 0 is larger than the federation work itself and could consume the programme | **High** | Sequence it by inversion; ship enforcement flags independently; treat P0 items 1–8 as the funded critical path |
| R2 | Turning on enforcement breaks working journeys, because much of the estate has never run with the PDP on the path | **High** | Shadow-then-enforce per route family, with divergence measurement before each flip — the OPA parity gate is the existing pattern |
| R3 | The audit hash change invalidates existing chains | Medium | `hash_algorithm_version` per row; the legacy-timestamp shim is the precedent |
| R4 | Node PDP outage stops clinical care (fail-closed) | **High** | PDP HA at the node; documented degraded procedure; local PDP as a hard SLO with alerting |
| R5 | Bundle cohort scoping leaks or under-covers | Medium | Publisher-side cohort tests; the negative assertion (ADR-09); disclosure records for bundles |
| R6 | Institutional key custody defeats national incident response | Medium | Contractual escrow and dual-control break-glass agreed at accreditation, not at incident time |
| R7 | Seven-day autonomy proves untestable at real load | Medium | Rehearse in a lab in Phase 2 before the pilot; instrument the node so the test is measurable |
| R8 | Hospital IT cannot operate a three-node Kubernetes cluster | **High** | Managed-node option for MoHCC hospitals; capacity and skills assessment before enrolment; Facility Edge for smaller sites |
| R9 | Backlog replay overwhelms the Core when several nodes reconnect together | Medium | Per-node rate ceilings, priority queues, staggered reconnection |
| R10 | Identifier collision from the current 9-digit public Impilo ID | Medium | Allocation blocks before any second issuer exists — this must precede the first node going live |

## 26.2 Policy decisions required, with decision owners

**Not** engineering questions. Each blocks a specific design element. Owners are given **by role** — the PO must attach the named individual and a date before the blocked phase begins.

| # | Decision | Blocks | Owner (role) | Phase |
|---|---|---|---|---|
| 1 | **Who is the data controller for a MoHCC hospital's records** — the Ministry or the hospital? | Trust-domain assignment (§6A); mitigated by ADR-32 so work can start | MoHCC Permanent Secretary + national Data Protection Authority | 3 |
| 2 | Minimum mandatory national contribution set for a non-MoHCC institution | Non-MoHCC onboarding (§4.2) | MoHCC Chief Health Informatics Officer + regulator | 4 |
| 3 | May a patient compel disclosure an institution's policy would refuse? | Patient-authorised exchange (§4.2, §19A.4) | Data Protection Authority + national clinical governance | 3 |
| 4 | Who reviews break-glass in a non-MoHCC institution, and what does the Ministry see? | Disclosure dashboard scope (§6) | Institutional clinical governance + MoHCC | 4 |
| 5 | How long may a node operate disconnected before it must stop accepting new patients? | The staleness ladder (§5.3) | National Clinical Governance Committee | 2 |
| 6 | Legal status of a record created under stale authority | Governance review workflow (§12.3) | Data Protection Authority + clinical governance | 2 |
| 7 | On withdrawal, what happens to contributed data? Design proposes freeze-and-mark | Decommissioning workflow (§3.3) | Data Protection Authority | 4 |
| 8 | Who pays for node infrastructure, and who is accountable when a node fails? | Operating model and pilot contract (§2B) | MoHCC Finance + service owner | 2.5 |
| 9 | Which regulator supervises cross-domain exchange, under what instrument? | Federation agreement template (§3.2) | MoHCC Legal + regulator | 3 |
| 10 | Is emergency cross-institution access permitted without a pre-existing agreement? Design assumes yes, vital interest, dual audit | Emergency access path (§4.3) | National Clinical Governance Committee + Legal | 3 |
| **11** | **May a platform operator hold cluster access to an institution's environment at all, and on what contractual terms?** §2B.3 states the capability plainly; the terms are a contract decision | Dedicated Hosted and Managed On-Premises offers | MoHCC Legal + institutional counsel | 2.5 |
| **12** | **Is institution-held key custody mandatory or optional at D1+?** Mandatory is stronger but excludes institutions without key-management capability, and a lost KEK is unrecoverable data | Isolation tiers (§2B.1) | Service owner + Data Protection Authority | 4 |
| **13** | **Who may authorise Shared-Care Cache pre-positioning, and for how long may emergency-minimum content persist?** | Cache TTL and cohort rules (§19B) | National Clinical Governance Committee | 3 |
| **14** | **Does an employer ever see mandatory-learning detail, or only the compliance verdict?** Design says verdict only | Professional Status scope (§11B.1, §4.4) | Regulator + MoHCC HR | 2 |
| **15** | **Can an institution refuse a national release that carries a security fix?** Design says no — a security floor overrides the institution's window | Upgrade authority (§2B.2) | MoHCC + institutional counsel | 4 |

## 26.3 Questions this architecture deliberately leaves open

- The canonical VARAPI federated provider reference — `provider_ref` UUID or `provider_public_id` ULID. Both exist and are unique; the ruling is a doctrine call, not a technical one.
- Whether OPA eventually replaces the Java `PolicyEngine` or remains a parity check. The current parity gate is sound; the decision can wait until parity evidence exists.
- Whether the Facility Edge profile shares the Hospital Node chart or becomes a distinct profile. Deferred until after the first pilot, deliberately.
- Whether a person may hold a personal and a work session simultaneously in one browser, or must switch. The architecture permits both; the choice is a usability decision with a modest security trade-off, and should be made with clinicians rather than for them.
- Whether Marketplace is a personal domain, an organisational one, or both. Today most of it is work-zoned while presenting as personal (§4A.3); the refile in Phase 2 forces the question.

---

## Document control

**Supersedes:** v1.0 of this document (same path, commit `97eab3ac8`), which remains valid for every decision v1.1 does not amend. **Depends on:** the current-state recovery of 2026-08-03, which remains the factual baseline; where this document states a current-state fact, that document is the citation.

**Change discipline:** an architecture decision recorded in §25 changes only by a new ADR. The data-residency matrices (§4.2, §4.4), the staleness ladder (§5.3), the prohibited inheritances (§4A.2) and the Shared-Care Cache rules (§19B) are clinical-safety and data-protection artefacts and change only with clinical-governance and data-protection sign-off. The sizing bands (§17A.2) are estimates and are expected to change — that is their purpose.

**Implementation status:** nothing in this document is implemented. §22 defines the sequence; §23 defines the gates; no phase begins before its predecessor's gate is green. Two things in v1.1 are exceptions worth naming: the organisational commissioning rails of §22A **already ship** and are being extended rather than built, and Bootstrap Mode ships **without a design document**, which §22A supplies retrospectively.
