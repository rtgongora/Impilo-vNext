# Mobile Experience Reality Check

## Scope

- `apps/mobile/citizen-app`
- `apps/mobile/provider-app`
- `apps/mobile/packages/*`

## Key Findings

| ID | Severity | Mobile area | Current state | Web equivalent | Remediation status |
|---|---|---|---|---|---|
| MOB-001 | HIGH | Citizen Conditions | TODO-only data loading; empty placeholder list | `/ehr/[patientId]/conditions` | Honesty status added; API wiring pending |
| MOB-002 | HIGH | Citizen Allergies | TODO-only data loading and no-op actions | `/ehr/[patientId]/allergies` | Honesty status added; no-op actions disabled |
| MOB-003 | HIGH | Citizen Provider Discovery | TODO-only load with empty results | `/discover/providers` | Honesty status added; API wiring pending |
| MOB-004 | MEDIUM | Mobile Nompilo parity | No complete command parity matrix with web | `/ask`, command surfaces | Pending dedicated parity implementation |
| MOB-005 | MEDIUM | Telemedicine | Present, but parity depth varies by role flow | `/telemedicine*` | Partial parity; continue convergence |

## Current Mobile Maturity Summary

- Provider operational workflows: mostly real/partial.
- Citizen personal clinical sections: mixed, with explicit placeholders.
- Wallet/coverage: real baseline with partial advanced flows.
- Reporting and support: present, but parity completeness varies.

## Immediate Next Steps

1. Add mobile API services for conditions/allergies/discovery.
2. Add shared mobile/web state language for fixture/prototype/not-wired.
3. Add parity tests for mobile actions that are currently placeholder.
