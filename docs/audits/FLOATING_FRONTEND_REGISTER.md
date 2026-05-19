# Floating Frontend Register

Floating frontend = route/screen/component present but disconnected from navigation, backend, or user action chain.

| ID | Surface | Type | Evidence | Risk | Next action |
|---|---|---|---|---|---|
| FLT-001 | Doctrine journey pages | Web route | Fixture-only pages imply real state | Misleading operational truth | Wire to BFF core transaction endpoints |
| FLT-002 | Provider BillingScreen | Mobile screen | Candidate orphan (no import references found) | Dead code / inconsistent user expectation | Wire into tools or retire/deprecate |
| FLT-003 | Workflow/Dispatch capabilities | Backend surfaced, UI absent | Controllers present, no primary UI call path | Backend-only capability invisible to operators | Add minimal operator views and nav |
| FLT-004 | `/home/referrals` registry drift | Web route metadata | Route existed outside registry map | discoverability/breadcrumb drift | Fixed in this cycle by route registry update |
