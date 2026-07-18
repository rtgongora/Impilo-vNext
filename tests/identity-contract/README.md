# Identity Contract — 12-journey conformance pack

This pack is the **AMBER→GREEN bar** for the identity-plane rearchitecture
([`docs/architecture/identity-trust-contract.md`](../../docs/architecture/identity-trust-contract.md) §16).
The implementation verdict flips to GREEN only when all 12 journeys run green
against the **live estate** (post CPID-cutover full-boot).

## Run

```bash
# against a deployed preview estate (services up)
bash tests/identity-contract/identity-contract-journeys.sh

# wired into the deploy smoke test (opt-in after cutover)
RUN_IDENTITY_PACK=1 bash scripts/deploy/preview-smoke-test.sh
```

Service base URLs default to the standard ports and are overridable via env
(`TSHEPO_IDENTITY`, `VITO`, `PCT`, `BUTANO`, `EXPERIENCE_BFF`, `KEYS`). DB
assertions use `PSQL` (default `kubectl exec` into the preview postgres).

## Exit codes / verdict

| Exit | Verdict | Meaning |
|------|---------|---------|
| 0 | GREEN | all 12 journeys passed — contract proven end-to-end |
| 2 | AMBER | no failures, but ≥1 journey could not reach its service (estate not fully up) |
| 1 | RED | ≥1 journey failed — a contract invariant is violated |

A journey **never false-PASSes**: an unreachable service is reported SKIP.

## The 12 journeys

1. New registration → proofing → issuance (Impilo ID v2 only after approval)
2. Returning client by Impilo ID → CPID (hash chain; no plaintext in transit or at rest)
3. Returning client by legacy PHID (alias status honoured)
4. Recovery without the card (step-up gated, anti-enumeration)
5. Self-service record claiming (record-link confidence recorded)
6. Lost-card replacement (card token revoked, HID untouched, same Impilo ID)
7. Biometric enrol + 1:1 verify (fail-closed engine; care not blocked)
8. Duplicate → merge → unmerge (relay `subject.merged`, mapping RETIRED redirect)
9. Offline O-CPID issue → reconcile → clinical repoint
10. Split retrieval: banner via HID→VITO, history via CPID→PCT/BUTANO
11. Assurance-vector escalation (step-up names the deficient dimension)
12. No-PII-reaches-SHR + estate audit (no `health_id` value in any clinical DB)

Evidence lands in `reports/identity-contract/<timestamp>/` (summary + audit log).
