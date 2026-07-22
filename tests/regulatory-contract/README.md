# Regulatory Contract Pack (Regulatory Operating Model — ROM-W11)

Conformance pack for the **Regulatory Operating Model** — HPA + the eight professional councils as
nine regulatory organisations on one governed substrate. Proves the load-bearing invariants of
[`regulatory-operating-model-doctrine.md`](../../docs/doctrine/regulatory-operating-model-doctrine.md),
mirroring `tests/reputation-contract/`.

```
bash tests/regulatory-contract/regulatory-journeys.sh
```

Exit / verdict: **0 GREEN** (all proven) · **2 AMBER** (software contract green; some live checks
await estate deploy + the SHADOW→ENFORCE flip) · **1 RED** (an invariant violated). A check that
cannot reach its service reports **SKIP — never a false PASS**.

## Invariants

| ID | Invariant | Where enforced |
|----|-----------|----------------|
| ROM-OWN | org identity in org-registry; `varapi.councils` FK-bound; no duplicate SoR | registry tokens + varapi V028 FK |
| ROM-APPT | access flows only from a verified regulatory appointment | org-registry V006/V007 |
| ROM-CTX | org-scoped session token carries org + role (no facility/provider) | tshepo-identity `orgScoped` + BFF `startRegulatorySession` |
| ROM-ISO | strict cross-council isolation | tshepo-authz V045 (SHADOW→ENFORCE at W11) |
| ROM-APPL | two-sided application; INTERNAL notes never in the applicant view | `RegulatoryApplicationCorrespondenceService` (W4) |
| ROM-CPD | renewal consumes VARAPI-adjudicated CPD status only | `RenewalEligibilityService` (W5) |
| ROM-FIREWALL | a rito complaint never auto-opens/transitions a proceeding | `DisciplinaryProceedingService.openFromReferral` (W7) |
| ROM-COMMITTEE | a committee member sees only docketed cases | `HearingDocketService` + authz V046 (W8) |
| ROM-OVERSIGHT | HPA sees aggregates + granted cases only, never council workspaces | `OversightService` + authz V047 (W10) |
| ROM-RECUSAL | a person cannot act as regulator on their own record | recusal firewall across W4/W7/W8 (tie on person Health-ID) |

## Status & the ENFORCE flip

Green here = `SOFTWARE_CONTRACT_GREEN`. The three isolation policy families (V045 cross-council,
V046 committee-docket, V047 HPA-oversight) are seeded **SHADOW** (`active=false`) with rego doing
the match; they flip to ENFORCE at ROM-W11 **after** the identity program's WORK_CONTEXT flip and
through the CZO single-writer channel — never simultaneously, never by editing the frozen
`tshepo-service`. Until deploy + flip, the live cross-council/committee/oversight *deny* checks
SKIP; the service layer enforces the same scoping in-band meanwhile (defense in depth).

Real council/HPA statutory actions on a governed referral remain `EXTERNAL_INTEGRATION_GREEN`
(out of scope) — the recusal + firewall invariants ensure nothing auto-fires them.
