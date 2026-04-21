# Impilo Facility Operating Model

This document defines the canonical relationship between:

- facility tier
- tenant
- pod
- facility
- workspace

It is intended to make deployment, service continuity, and user experience decisions consistent across both single-facility and national-scale deployments.

## 1. Core Boundary Model

The architecture must keep these boundaries distinct:

| Boundary | Meaning | What it must not be confused with |
|---|---|---|
| `tenant` | Legal, governance, and data isolation boundary | Facility, pod, or provider |
| `pod` | Runtime/deployment boundary | Tenant or individual provider |
| `facility` | Care delivery site | Tenant or deployment unit by default |
| `workspace` | Operational unit inside a facility | Facility itself |
| `provider` | Human actor / professional identity | Infrastructure boundary |

### Non-negotiable rules

1. A provider is never a server or database boundary.
2. A facility is not automatically a tenant.
3. A facility is not automatically a pod.
4. If essential care must continue when the center is unavailable, local execution is required.
5. National authoritative registries and local care execution are separate concerns.
6. Facility tier sets the baseline, but actual behavior is controlled by capability and workflow profiles.

## 2. Deployment Principle

Impilo is designed for **central governance plus local execution**.

In practical terms:

- national or central services remain authoritative for identity, provider registry, facility master, terminology, consent, and policy-governed reporting
- local pods execute time-sensitive operational workflows such as encounters, orders, dispensing, maternity monitoring, and local billing
- edge/mobile operation supports continued care when even pod connectivity is degraded

This means the platform is neither purely central-only nor purely per-facility on-premises by default. It is a hybrid federated model.

## 3. Zimbabwe Facility Tiers

The Zimbabwe tier taxonomy is the canonical deployment and UX planning baseline:

1. Community
2. Health Post
3. Clinic
4. Polyclinic
5. Rural Hospital
6. District Hospital / Mission Hospital
7. General Hospital
8. Provincial / Tertiary Hospital
9. Central Hospital
10. Quinary Hospital
11. Virtual Hospital

## 4. Tier Matrix

| Tier | Typical service shape | Typical UX shape | Continuity requirement | Default deployment mode | Local execution recommendation |
|---|---|---|---|---|---|
| Community | outreach, screening, referrals, public health | mobile-first, task-driven, lightweight launch actions | edge-critical | `EDGE_ASSISTED` | yes, through edge capability |
| Health Post | registration, triage, primary care, immunization | simple service-start dashboard | local execution required | `SHARED_POD` or `EDGE_ASSISTED` | yes |
| Clinic | primary care, maternal-child, pharmacy, referrals | role-aware primary care launch surface | local execution required | `SHARED_POD` | yes |
| Polyclinic | multi-service ambulatory care, pharmacy, lab-light | richer queue and worklist launch paths | local execution required | `SHARED_POD` | yes, sometimes dedicated |
| Rural Hospital | inpatient, maternity, pharmacy, lab, emergency stabilization | hospital workflow landing page | local execution required | `DEDICATED_POD` or strong `SHARED_POD` | strongly recommended |
| District Hospital / Mission Hospital | district anchor, inpatient, maternity, theatre, pharmacy, lab | hospital launch + management surfaces | local execution required | `DEDICATED_POD` | strongly recommended |
| General Hospital | multi-workspace hospital with broader specialties | complex role- and workspace-aware home | local execution required | `DEDICATED_POD` | yes |
| Provincial / Tertiary Hospital | tertiary referral, specialist workspaces | specialty-aware launch experience | local execution required | `DEDICATED_POD` | yes |
| Central Hospital | top-tier referral and national coordination node | advanced specialty and operational dashboards | local execution required | `DEDICATED_POD` | mandatory in practice |
| Quinary Hospital | ultra-specialized care and coordination | highest-complexity launch and orchestration surfaces | local execution required | `DEDICATED_POD` | mandatory |
| Virtual Hospital | telemedicine, remote triage, cross-facility digital coordination | digital-first intake and coordination | connected tolerant or hybrid | `VIRTUAL_ONLY` or central hybrid pod | no physical local pod by default |

## 5. Capability and Workflow Profiles

Facility tier alone is not enough. Every facility must also carry:

- a **capability profile**: what services are genuinely offered
- a **workflow profile**: how patients and staff move through the site
- a **deployment mode**: where runtime execution happens

Two facilities in the same tier may differ materially. For example, two District Hospitals may not have the same maternity, theatre, imaging, or staffing profile.

## 6. Practical Deployment Patterns

### Single independent facility

- usually one tenant
- usually one pod or one shared hosted pod
- one or a few facilities
- simplest governance shape

### District or operator cluster

- one tenant or sub-tenant governance model
- one shared pod serving several smaller facilities
- common for Health Posts, Clinics, and some Polyclinics

### Major hospital deployment

- one facility or hospital group anchored by its own pod
- dedicated runtime and databases for local execution domains
- strongest fit for Rural Hospital and above where service continuity is critical

### National or multi-operator deployment

- national-authoritative spine plus multiple pods
- pods may align to districts, provinces, hospital groups, or operator networks
- local authorities, religious organisations, and ministry-owned networks may either:
  - share one tenant with differentiated governance, or
  - operate as separate tenants under a federated national architecture

## 7. User Experience Consequence

The user interface must adapt by:

- actor role
- facility
- facility tier
- enabled service capabilities
- active workspace

The home page and launch pathways must therefore be:

- role-aware
- facility-aware
- workspace-aware
- service-model-aware

A generic single homepage for every provider, in every facility type, is not architecture-complete.

## 8. Recommended Defaults

### Small-site pattern

For Community, Health Post, Clinic:

- use shared district or operator pods by default
- prioritize strong edge/offline capability
- avoid per-facility full-stack deployments unless justified by connectivity or autonomy requirements

### Medium-complexity pattern

For Polyclinic, Rural Hospital, District Hospital / Mission Hospital:

- use shared pods where practical
- move to dedicated local execution pods where service continuity, scale, or autonomy requires it

### High-complexity pattern

For General Hospital and above:

- dedicated local execution pods are the sensible default
- specialty workflows and richer operational surfaces are expected

### Virtual hospital pattern

For Virtual Hospital:

- central or shared digital delivery infrastructure is the default
- it remains governed by the same tenant/pod model, but is not a normal physical facility

## 9. Canonical Contract

The machine-readable reference for this model is:

- [contracts/facility-operating-model.ts](../../contracts/facility-operating-model.ts)

That contract should be treated as the canonical source when implementing:

- deployment planning
- facility configuration
- route and workspace enablement
- homepage and launch-client experience shaping
- continuity and offline policy
