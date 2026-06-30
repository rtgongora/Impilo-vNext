# Feature Sprint Ledger — shared cross-session memory

This ledger is the shared memory + reuse-first contract for the pop-out feature workstreams.
Each workstream does read-only discovery + an anti-duplication audit first, extends existing
system-of-record services rather than duplicating them, builds full vertical slices, and appends
a completion row here (append only — do not rewrite other rows).

## Reuse-first rules

- **Extend before creating.** Do not create a new service, table family, registry entry, API
  contract, route, or experience if an existing service already owns that capability. Prove no
  existing owner before introducing anything new.
- **Respect system-of-record ownership.** Resolve identity via Vito + Tshepo; clinical via
  Butano/SHR/PCT; consent via tshepo-consent + mvumo; messaging/notifications via Khuluma +
  notification-service; payments via Costa + MusheX; guidance via Nompilo/guidance-service.
- **No mocks in production paths.** Every card/action connects to a real backend/BFF capability or
  is explicitly documented as deferred.
- **BFF is stateless.** experience-bff is composition/orchestration only — persist in a sovereign
  service, never in the BFF.

## Coordination-owned files (single-writer rule)

The following are coordination-owned. Record needed changes under "coordination items" in your
report and in the table below rather than editing them unilaterally:

- `docs/registry/services-registry.yaml`
- `docs/registry/system-of-record-map.md`
- `docs/runbooks/port-allocation.md`
- `contracts/**` shared contracts

## SoR quick map (as discovered)

| Domain | Owner |
|---|---|
| Person/client identity, Health-ID, profile, relationships, corrections | `vito-service` |
| Trust / assurance / LoA | `identity-assurance-service` (+ Tshepo gate) |
| Consent | `tshepo-consent-service` |
| Delegated access / proxy / comms-preference orchestration | `mvumo-service` |
| Clinical summary / SHR / FHIR | `butano-service` (+ `pct-service`) |
| Care journey / encounters / referrals | `pct-service` |
| Bills / costing | `costing-engine-service` (Costa) |
| Payments / rails | `mushex-service` |
| Messaging / notifications / delivery | `khuluma-service` (+ `notification-service`) |
| Guidance (Nompilo) | `guidance-service` + UI intelligent components |

## Completed-workstream status

| # | Workstream | Branch | New service? | New tables? | Services extended | Tests | Coordination items |
|---|------------|--------|--------------|-------------|-------------------|-------|--------------------|
| 1 | Unified Person Health Wallet | `feature/person-health-wallet` | No | No | experience-bff (CitizenWalletController + WalletOverviewService + 2 Mvumo client reads), one-ui-shell (8 `/citizen/wallet/**` pages + hook), citizen-app mobile (WalletOverviewSection), OPA `impilo.wallet` policy | BFF 10/10 green, OPA 11/11 green, web 3/3 green (+tsc clean), mobile section wired (runner blocked by `workspace:*` install) | None — no SoR/registry/port/contract changes. Wallet is a pure experience/orchestration slice over existing owners. |
