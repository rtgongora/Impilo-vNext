# Service Definition of Done

A service or component is considered complete for this remediation program only when all items below are satisfied.

- [ ] Registry entry exists in `docs/architecture/services-registry.yaml` with Ring and Primary Plane labels.
- [ ] Activation fields are populated: Activation State, Contract Alignment Status, Surfacing Quality, Integration Status, Remediation Status.
- [ ] Live backend services include `api_surface` and contract evidence (OpenAPI or equivalent) or are explicitly marked as deferred.
- [ ] Frontend surfacing path is explicit (`Complete`, `Partial`, `Placeholder`, or `Missing`) with backlog item where needed.
- [ ] Boundary convergence decisions are encoded for canonical pairs (MSIKA/Product Registry and Simba/Wellness).
- [ ] Validation script runs in advisory mode with zero structural errors.
- [ ] Any unresolved ambiguity is marked `Needs Owner Decision` with an explicit question.
