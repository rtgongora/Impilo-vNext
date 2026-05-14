# Production Plane Doctrine

This document establishes the canonical seven-plane model for Impilo vNext production architecture.

## Canonical Planes

| Plane ID | Name | Scope |
|---|---|---|
| trust | Trust, Identity Assurance & Governance Plane | TSHEPO policy, authorisation, consent, audit, keys, identity assurance, session and device risk. |
| registry | Registry & Sovereign Identity Spine | VITO, VARAPI, TUSO, ZIBO, UBOMI, INDAWO and authoritative registries. |
| clinical | Clinical Execution & Shared Health Record Plane | BUTANO/FHIR, patient care workflows, encounters, orders, results, pharmacy and inpatient capabilities. |
| data | Data, Intelligence & Public Health Plane | NDR, warehousing, analytics, surveillance, indicators, search and public health intelligence. |
| integration | Integration, Interoperability & Edge Plane | Integration hub, adapters, offline sync, jobs, notifications and channel/edge workflows. |
| experience | Experience, Workflow & Orchestration Plane | one-ui-shell, experience-bff, provider/citizen/admin journeys and orchestration. |
| enterprise | Enterprise Resource & Market Operations Plane | MusheX, COSTA, coverage, claims, billing, marketplace and enterprise operations. |

## Governance Rules

- Do not invent new plane names.
- Every service must declare exactly one `primary_plane` and one `domain`.
- `secondary_planes` denote integration touchpoints only and never transfer ownership.
- Deployment namespaces are infrastructure placement and must not be interpreted as ownership planes.
- Backend capability is incomplete until wired via BFF/API contracts into the applicable experience layer.
- Frontend capability is incomplete until backed by real APIs and production service logic.