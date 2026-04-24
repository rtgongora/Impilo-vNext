## Indawo Site Registry (Public Health Premises Ops)

Indawo governs **non-facility sites of public health concern** (premises) and their regulatory lifecycle.
This is a sibling domain to TUSO facility-registry (health facilities), not a duplicate.

### What this subsystem adds
- **Registry + operations** for public health premises: applications, inspections, findings, compliance actions, licensing, renewals, enforcement (foundation).
- **Checklist engine**: templates + items selected by `site_category` + `inspection_type`, with critical item support.
- **Audit + status history**: privileged lifecycle actions are recorded for traceability.
- **Outbox events**: key lifecycle changes emit events using existing `ind_event_outbox`.

### Persistence
Flyway migration: `db/migration/V003__site_registry_regulatory.sql`

Key tables:
- `ind_sites` (extended): `site_code`, `site_category`, `risk_class`, `province/district/ward`, `lifecycle_status`, `regulatory_status`, `active_flag`, `metadata`
- `ind_site_applications`
- `ind_site_operators`
- `ind_site_inspections`
- `ind_inspection_checklist_templates`, `ind_inspection_checklist_items`
- `ind_site_inspection_findings`
- `ind_site_compliance_actions`
- `ind_site_licences`
- `ind_site_enforcement_cases` (foundation)
- `ind_site_status_history`
- `ind_audit_events`

### APIs
Internal endpoints (Indawo):
- Base: `/internal/v1/site-registry`
- `GET /sites`: search/filter
- `GET /sites/{siteId}`: full regulatory profile
- `POST /applications`: create application (supports `siteDraft` for new registration)
- `POST /applications/{applicationId}/submit`
- `POST /inspections`: schedule
- `POST /inspections/{inspectionId}/record`: record outcome + findings; can create compliance actions
- `POST /compliance-actions/{actionId}`: update action status
- `POST /licences`: issue licence
- `POST /sites/{siteId}/renewals`: create renewal application
- `POST /enforcement-cases`: open case (foundation)
- `GET /checklist-templates`, `GET /checklist-templates/{templateId}`
- `GET /dashboard/summary`

Experience BFF proxies these under:
- `/internal/v1/public-health/site-registry/**`

### Events (outbox)
Emitted by `SiteRegulatoryService` (topic naming follows existing Indawo conventions):
- `impilo.indawo.site.registry.created.v1`
- `impilo.indawo.site.application.created.v1`
- `impilo.indawo.site.application.submitted.v1`
- `impilo.indawo.site.inspection.scheduled.v1`
- `impilo.indawo.site.inspection.completed.v1`
- `impilo.indawo.site.compliance.action.created.v1`
- `impilo.indawo.site.licence.issued.v1`
- `impilo.indawo.site.renewal.created.v1`
- `impilo.indawo.site.status.changed.v1`
- `impilo.indawo.site.enforcement.case.created.v1`

### Local testing
Backend (module tests):

```bash
mvn -pl services/indawo-service test
```

UI:
- Visit `GET /public-health/site-registry` for list
- Visit `GET /public-health/site-registry/{siteId}` for profile + minimal actions

### Known gaps (intentionally left for next waves)
- Full RBAC enforcement inside Indawo beyond the `PUBLIC_HEALTH` guard at the Experience UI/BFF layer.
- Document upload (file store integration) and verification workflow.
- Committee review/decision formalization (beyond issuing licence).
- Rich inspection workspace (offline/mobile capture, evidence attachments, scoring, multi-inspector).
- Jurisdiction assignment and workload routing.
- Public verification endpoint for licence validity.

