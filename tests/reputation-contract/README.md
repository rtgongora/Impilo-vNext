# Reputation Contract Pack (Rito Experience & Reputation — RW11)

Conformance pack for the provider/facility **reputation lane**. Proves the
load-bearing invariants of
[`provider-reputation-doctrine.md`](../../docs/doctrine/provider-reputation-doctrine.md)
and [`service-relationship-doctrine.md`](../../docs/doctrine/service-relationship-doctrine.md)
end-to-end, mirroring the discipline of `tests/identity-contract/provider-journeys.sh`.

```
bash tests/reputation-contract/reputation-journeys.sh
```

Exit code / verdict:

- **0 GREEN** — every invariant proven, no skips.
- **2 AMBER** — no failures, but some *live* checks could not run because the
  reputation lane is not yet deployed on the estate. The **software contract is
  green** (doctrine present, ownership boundary codified, migration seeds valid,
  JVM acceptance tests pass); only the runtime-through-gateway proofs await a
  `rito` / `varapi` / `khuluma` / `tuso` redeploy.
- **1 RED** — an invariant was violated.

A check that cannot reach its service reports **SKIP — never a false PASS**.

## Invariants

| ID | Invariant | Static proof | Live proof |
|----|-----------|--------------|------------|
| RR-OWN | Rito (not Varapi) is SoR for ratings/reputation | registry forbidden token + doctrine docs | `rito.rit_provider_rating` exists, varapi has none |
| RR-VERIFY | rating VERIFIED only via a PCT interaction; one per encounter | `RatingServiceTest` + `uq_rit_rating_verified_encounter` | submit w/o interaction ⇒ `verified=false` |
| RR-DISCLOSE | public summary = verified experience domains only | `ReputationReadServiceTest` (PUBLIC_DOMAINS) | public read carries no quality/safety domain |
| RR-FIREWALL | a rating rewrites no licence/scope/employment/TSHEPO/registration | `RatingGovernanceAcceptanceTest` | — (no authority client on the write seam) |
| RR-COMPOSE | Varapi displays a Rito-sourced summary, stores nothing | `PublicPractitionerVerificationResponse` + `RitoClient` | verify carries source-tagged `experienceSummary` |
| RR-VISIBILITY | management view gated to regulators + quality teams | `V044` seed | rules live+active on the PDP |
| RR-FEEDBACK | PCT completion → Rito verified interaction → Khuluma request | `VerifiedInteractionServiceTest` + `RitoFeedbackRequestConsumerTest` | `rit_verified_interaction` pipeline present |

Green here = `SOFTWARE_CONTRACT_GREEN`. Real council/HPA authority actions on a
governed referral remain `EXTERNAL_INTEGRATION_GREEN` (out of scope, declared
honestly — the firewall means a rating never triggers them automatically).
