# AI Governance Unlock — Comparison, Boundary, and Closure

## Phase 1 — Comparison and Boundary Definition

| Lovable Tile / Module | Intended Purpose | Runtime Capability Source | Classification | First Coherent Downstream Flow |
|---|---|---|---|---|
| `AIGovernance.tsx` module home (`/ai-governance`) | Entry point for governance controls and operational AI oversight | Experience UI route + new Experience-BFF AI governance controller + data-governance-service | FIXED_NOW | Home tile → AI Governance module home overview cards → navigate to datasets/rules/policy/audit |
| Governance Datasets | Register and classify data assets used in AI or analytics workflows | `data-governance-service` `POST/GET /internal/v1/governance/datasets` | FIXED_NOW | AI Governance → Datasets tab → create dataset → list refresh |
| Governance Rules | Manage allow/deny governance rules and required purpose binding | `data-governance-service` `POST/GET/DELETE /internal/v1/governance/rules` | FIXED_NOW | AI Governance → Rules tab → create rule → deactivate rule |
| Access Decisioning | Evaluate if a principal can access a governed dataset for a given purpose | `data-governance-service` `POST /internal/v1/governance/decide` | FIXED_NOW | AI Governance → Decisions tab → submit principal/dataset/purpose → view decision |
| Policy Publication | Publish governance policy versions for enforcement | `data-governance-service` `POST /internal/v1/governance/policies` | FIXED_NOW | AI Governance → Policy tab → publish policy → success confirmation |
| Governance Audit | Track governance-relevant audit signals from runtime activity | Experience-BFF audit repository `admin_audit_log` surfaced under AI governance BFF | SUPPORTABLE_NOW | AI Governance → Audit tab → inspect recent audit entries |
| Model Registry / Prompt Registry / Runtime Model Controls | Control model versions, prompt templates, release approvals | No dedicated model registry or prompt-control domain surfaced in runtime services | BLOCKED_BY_MISSING_DOMAIN_MODEL | Deferred to `ai-governance-model-registry-slice` |
| AI Safety Incident Workflow | Structured AI incident intake, triage, and remediation tracking | No AI-specific incident domain model in experience-bff or domain services | BLOCKED_BY_MISSING_DOMAIN_MODEL | Deferred to `ai-governance-incident-response-slice` |

## Phase 2 — Unlock Executed

The supportable-now subset is fully surfaced as a coherent slice:

1. New AI Governance module home and tabs in experience UI.
2. New Experience-BFF AI Governance controller bridging to data-governance-service.
3. Homepage and nav reintegration so AI Governance is discoverable as a first-class module.
4. Role-aware visibility maintained through existing admin role guards.

## Closure Pass

All reachable items inside this bounded slice were implemented now:

- Dataset registry flow (create + list).
- Rule lifecycle flow (create + list + deactivate).
- Runtime decision flow.
- Policy publication flow.
- Governance audit visibility flow.
- Homepage and sidebar entry points.

No reachable adjacent item in this slice is left PARTIAL.

## Deferment Pass

### Deferred item 1
- **Capability:** Model registry and prompt-control governance.
- **Reason:** Runtime lacks a dedicated model/prompt governance domain service and schema.
- **Blocker:** Missing domain model + API surface for model artifacts and approval lifecycle.
- **Why not completable in this wave:** Would require new domain service design beyond bounded bridge work.
- **Future slice:** `ai-governance-model-registry-slice`.
- **Smallest future prompt:** "Add model registry CRUD + approval workflow service, then expose it through experience-bff `/internal/v1/ai-governance/models` and connect it to `/ai-governance`."
- **Preparatory work completed now:** AI Governance module shell + BFF controller extension point + navigation integration.

### Deferred item 2
- **Capability:** AI safety incident workflow.
- **Reason:** No AI incident entity/workflow in existing services; only generic audit logs exist.
- **Blocker:** Missing domain model for AI incident records and state transitions.
- **Why not completable in this wave:** Would require cross-service workflow modeling and persistence schema.
- **Future slice:** `ai-governance-incident-response-slice`.
- **Smallest future prompt:** "Add AI incident intake/triage domain model and API, then wire incident queue and detail views into AI Governance module."
- **Preparatory work completed now:** Audit tab and governance entrypoint established for future incident integration.
