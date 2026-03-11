# Option B — Documentation Hardening Pass (Scheduled)

> **Status**: Not implemented in this task.
> **Scheduled for**: Future documentation sprint
> **Depends on**: Option A (completed) — canonical spec source identified and index/contract layer established

---

## Objective

Fully populate `docs/prototype/final/*` with detailed content (no index indirection), replacing the current index/contract docs with self-contained specification files. Tighten CI checks to enforce minimum line counts and content completeness.

## Scope

### Files to Expand

Each file below currently acts as an index pointing to canonical spec sources. In Wave B, each becomes a fully self-contained spec document:

| File | Current State | Target State |
|------|--------------|-------------|
| `docs/prototype/final/00_executive_summary.md` | Index (81 lines) | Full executive summary with architecture overview (300+ lines) |
| `docs/prototype/final/01_site_map.md` | Index (81 lines) | Complete 98-route table with all fields (500+ lines) |
| `docs/prototype/final/02_page_by_page_spec.md` | Index (81 lines) | Full page-by-page spec for all 98 routes (2000+ lines) |
| `docs/prototype/final/03_component_inventory.md` | Index (97 lines) | Complete component catalog with props, state, composition (800+ lines) |
| `docs/prototype/final/04_api_surface_map.md` | Index (104 lines) | Full endpoint reference with request/response schemas (1000+ lines) |
| `docs/prototype/final/05_state_and_storage.md` | Index (100 lines) | Full state shape definitions, storage schemas, migration docs (600+ lines) |
| `docs/prototype/final/06_golden_paths.md` | Index (113 lines) | Complete step-by-step scripts with screenshots/expected states (800+ lines) |
| `docs/prototype/final/07_opus_execution_contract.md` | Index (85 lines) | Full 11-phase execution contract with checklists (500+ lines) |

### Process

1. **Extract from implementation**: Generate spec content from actual source code (routes.ts, controllers, components, stores, migrations)
2. **Reconcile with plan docs**: Cross-reference `docs/plan/` and `docs/architecture/v1.1/` to ensure completeness
3. **Resolve open conflicts**: Address the 4 open spec conflicts in `docs/spec-conflicts/v3-align-spec-conflicts.md`
4. **Obtain original spec docs**: If `vNext V3.docx` and `vNext Tech Companion Spec 2.0.docx` become available, reconcile with implementation

### CI Check Tightening

Update `scripts/spec-integrity-check.sh`:

| Current Check | Wave B Check |
|--------------|-------------|
| >= 10 links OR >= 200 lines | >= 200 lines (index mode no longer sufficient) |
| No placeholder phrases | No placeholder phrases + no "Index & Contract" header |
| Canonical spec root exists | Canonical spec root exists + cross-reference validation |

### Tools and Automation

- **Spec generator script**: Auto-generate route table from `routes.ts`, controller table from Java source
- **Cross-reference validator**: Verify every route in `01_site_map.md` has a matching entry in `02_page_by_page_spec.md`
- **Diff reporter**: Compare generated spec with existing content, flag discrepancies

## Success Criteria

- All 8 files pass the tightened spec integrity check (>= 200 lines, no placeholders)
- Route table in `01_site_map.md` matches `routes.ts` exactly (98 entries)
- API surface in `04_api_surface_map.md` covers all BFF controller endpoints
- Golden paths in `06_golden_paths.md` have step-by-step scripts matching `GoldenPathIntegrationTest.java`
- No remaining open spec conflicts

## Not Implemented in This Task

This document records the plan for Wave B. The current task (Option A) established:

1. Canonical spec source identification (`docs/plan/` + `docs/architecture/v1.1/`)
2. Index/contract layer (`docs/prototype/final/` rewritten with canonical links)
3. Spec integrity check (`scripts/spec-integrity-check.sh`) enforcing non-stub status
4. Acceptance pack integration (§ 8 Spec Integrity Gate)

Wave B builds on this foundation by replacing the index layer with fully expanded content.
