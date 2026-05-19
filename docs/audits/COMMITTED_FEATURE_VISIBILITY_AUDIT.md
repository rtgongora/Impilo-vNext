# Committed Feature Visibility Audit

## Branch

`claude/staging-ux-orchestration-remediation-Yypyl`

## Features Appearing Implemented But Not Fully Surfaced

| ID | Feature | Evidence of implementation | Visibility gap |
|---|---|---|---|
| VIS-001 | Core transaction + journey orchestration | Experience BFF core transaction controller/composition present | Doctrine web pages still fixture-backed and mobile equivalent missing |
| VIS-002 | Nompilo command/handoff endpoints | BFF endpoints implemented | Incomplete web/mobile command parity and grounding |
| VIS-003 | Workflow service orchestration | Workflow controllers/routes in BFF | No primary user-facing web/mobile route surfaced |
| VIS-004 | Dispatch operations | Dispatch controller/routes in BFF | No surfaced web/mobile route |
| VIS-005 | Rich learning/Fundo backend surfaces | learning service + web routes | Mobile citizen parity limited; provider-focused usage |
| VIS-006 | Finance/costing deep backend | Costa/MusheX services and BFF routes | Mobile depth and parity still partial in some flows |

## Hidden/Disconnected UI Candidates

- `apps/mobile/provider-app/src/screens/provider/BillingScreen.tsx` (candidate orphan screen).
- Doctrine pages display realistic transaction UX but run from fixture data.
