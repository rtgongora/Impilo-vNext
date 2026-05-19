# Service Boundary Violations Register

This register tracks ownership conflicts, documentation drift, unresolved responsibilities, and architecture boundary risk identified during the audit.

## Boundary Violations
| Violation Type | Service Or Component | Details |
|---|---|---|
| Skeleton Contract Verification Pending | Referral Service | OpenAPI exists but runtime-backed contract verification evidence is still required before `Aligned`. |
| Skeleton Contract Verification Pending | Analytics Pipeline Service | OpenAPI exists but runtime-backed contract verification evidence is still required before `Aligned`. |

## Additional Governance Findings
- Inconsistent naming remains between legacy and decomposed TSHEPO services.
- Previously Missing live backend contract evidence has been remediated and linked in the canonical registry.
- Previously documentation-only services now have approved implementation scaffolds in repository.
- Some frontend surfaces still require explicit backend ownership traceability.
- Product registry authority has been converged to MSIKA; Product Registry Service is now alias/deprecated.
- Wellness authority has been converged to Simba; Wellness Service is now alias/deprecated.
- Approved closure milestones for alias runtimes are now codified: freeze `2026-05-15`, cutover `2026-09-30`, hard sunset `2026-12-31`.

## Required Boundary Review Areas
- Duplicated service responsibilities: mitigated (authority convergence applied for Product Registry and Wellness domains).
- Services owning the same truth: mitigated (Simba and MSIKA designated canonical owners).
- Frontend apps containing backend/domain logic: no hard proof in this pass; owner review still required for critical paths.
- Backend services with no frontend surfacing where surfacing is expected: present for selected backend modules with no explicit frontend traceability.
- Documented services not implemented: mitigated for approved hybrid scope; runtime and library scaffolds now exist.
- Implemented services not documented: partially present in newer modules not reflected in legacy planning artifacts.
- Ring and Plane classification conflicts: present in legacy documents that still show earlier models.
- Unclear services requiring owner decision: present for low-confidence/documentation-only entries.
- Inconsistent naming: present across legacy and decomposed TSHEPO naming.
- Stale documentation: present in historical catalogs now superseded by canonical register.
- Service folder names mismatching canonical names: present in selected display aliases versus module names.
- Services with ports but no clear deployment unit: present for selected entries with incomplete deployment metadata.
- Services with database schemas but unclear ownership: present where schema evidence is not linked in contracts.
- Services with overlapping API responsibilities: residual transition risk remains until deprecated alias endpoints are sunset.

## Implemented Owner Decisions
- Product registry authority is merged into MSIKA with Product Registry Service retained only as a deprecated alias during transition.
- Wellness authority is merged into Simba with Wellness Service retained only as a deprecated alias during transition.
- Documentation-only services are approved for hybrid implementation (runtime services plus library scaffolds).
- OpenAPI closure for all previously Missing contract-alignment services is required before soft-gate promotion.
- Contract handling split is approved: alias-deprecated entries are `Not Applicable`; skeleton entries require runtime-backed contract verification evidence before `Aligned`.
