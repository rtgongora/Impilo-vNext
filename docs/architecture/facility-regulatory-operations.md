# Facility Regulatory Operations In TUSO

## Intent
TUSO is the governed digital operations platform for the Health Professions Authority of Zimbabwe (HPA) facility lifecycle. It does not replace HPA's legal authority. Instead, it records and executes the workflow, while preserving decision attribution to authorised HPA actors, committees, councils, and delegated enforcement routes.

## Domain Split
- `FacilityEntity` remains the canonical facility master and national registry anchor.
- Regulatory lifecycle state is layered around the master through:
  - `FacilityApplicationEntity`
  - `FacilityInspectionEntity`
  - `InspectionFindingEntity`
  - `ComplianceActionEntity`
  - `CommitteeReviewEntity`
  - `FacilityCertificateEntity`
  - `FacilityUnitEntity`
  - `PractitionerInChargeAssignmentEntity`
  - `EnforcementCaseEntity`
  - `FacilityStatusHistoryEntity`
  - `FacilityAuditEventEntity`

This preserves the separation between "what the facility is" and "where the facility is in the regulatory workflow."

## Lifecycle Model
Facility status is workflow-derived, not manually improvised.

Implemented regulatory status states include:
- `DRAFT`
- `APPLICATION_IN_PROGRESS`
- `UNDER_INITIAL_REVIEW`
- `PENDING_INSPECTION`
- `INSPECTION_IN_PROGRESS`
- `PENDING_RECTIFICATION`
- `PENDING_COMMITTEE_REVIEW`
- `APPROVED_FOR_REGISTRATION`
- `REGISTERED_ACTIVE`
- `RENEWAL_DUE`
- `RENEWAL_IN_PROGRESS`
- `RESTRICTED`
- `SUSPENDED`
- `PENDING_CLOSURE`
- `CLOSED`
- `VOLUNTARILY_CLOSED`

Implemented application workflow states include:
- `DRAFT`
- `SUBMITTED`
- `UNDER_ADMIN_REVIEW`
- `AWAITING_DOCUMENTS`
- `AWAITING_FEE`
- `READY_FOR_INSPECTION`
- `INSPECTION_SCHEDULED`
- `INSPECTED`
- `AWAITING_RECTIFICATION`
- `READY_FOR_COMMITTEE`
- `DECIDED_APPROVED`
- `DECIDED_REJECTED`
- `DECIDED_DEFERRED`
- `CLOSED_OUT`

## Implemented Workflows
The first production-oriented cut covers:
- New registration application creation and submission
- Readiness for inspection routing
- Inspection scheduling
- Checklist-based inspection capture
- Failure-driven rectification and compliance action creation
- Committee decision capture with approval/defer/reject outcomes
- Certificate issuance tracking on approved decisions
- Renewal and material-change workflow launch from the facility workspace
- Enforcement case creation for investigative or closure/restriction recommendations
- Lifecycle history and audit trail exposure on the facility detail surface

## Checklist Engine
Checklist templates are configuration-backed through `inspection_checklist_template.items_json`.

Current engine rules:
- Templates can be filtered by `inspectionType` and `facilityType`
- Checklist items support section grouping, severity, evidence types, and a `critical` flag
- Findings are linked back to checklist item codes and text
- Failed critical findings automatically escalate the inspection outcome and route the application into rectification or enforcement posture

Seed data currently includes:
- General minimum requirements
- Clinic / consulting room initial inspection
- Laboratory initial inspection
- Maternity initial inspection
- Imaging initial inspection

## Authority Boundaries
Authority remains explicit in state changes and audit records:
- Applicant and facility-side actions are limited to submission, evidence, and correction workflows
- Inspector actions are recorded as `HPA_INSPECTOR` or equivalent authority context
- Committee outcomes are recorded separately from inspection outcomes
- Enforcement cases are recommendations and routes, not silent direct closure by the platform

Audit records and status history capture:
- actor
- actor type
- authority context
- action
- target entity
- before/after state where relevant
- correlation linkage

## API Surfaces
TUSO now exposes the internal regulatory API under:
- `/v1/internal/facility-registry/facilities`
- `/v1/internal/facility-registry/applications`
- `/v1/internal/facility-registry/documents`
- `/v1/internal/facility-registry/checklist-templates`
- `/v1/internal/facility-registry/inspections`
- `/v1/internal/facility-registry/compliance-actions`
- `/v1/internal/facility-registry/committee-reviews`
- `/v1/internal/facility-registry/enforcement-cases`
- `/v1/internal/facility-registry/dashboard/summary`

The Experience BFF mirrors these routes under `/internal/v1/facility-registry/...` for web and mobile use.

## Extension Points
Planned or intentionally preserved extension seams:
- Provider registry lookup for practitioner-in-charge validation through VARAPI
- Payment or fee-state orchestration through a future MusheX integration
- Offline/deferred inspection capture through `captureMode` and `offlineCaptureReference`
- Public legitimacy verification and analytics consumers from canonical facility regulatory status
- Future accreditation, quality scoring, and cross-regulator interoperability through additional workflow layers and event consumers

## Events
Lifecycle changes publish outbox-backed domain events aligned to TUSO topic conventions, including:
- application created
- application submitted
- inspection scheduled
- inspection completed
- committee decision recorded
- certificate issued
- facility status transitions

## Design Constraint To Preserve
Do not collapse facility master data, workflow state, and legal authority attribution into a single mutable record. TUSO should remain the operational system of record, while legal and committee authority stays explicit and attributable in the workflow and audit model.
