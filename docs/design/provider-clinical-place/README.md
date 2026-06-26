# Provider · Clinical · Place — Design Gate Package

> **Design-first gate** for three colliding programs: **T1 Provider Experience**, **T3 Core Transaction**,
> **T4 TUSO/Indawo/Facility-Mode/Org/Regulation**. AUDIT + DESIGN ONLY — no implementation code. This package
> is the **spawn spec for round-2 implementation lanes**. Branch `intake/provider-clinical-place-design`.

## The six deliverables

1. **Canonical T3 journey map** → [`docs/journeys/core-transaction-patient-access-encounter-orchestration.md`](../../journeys/core-transaction-patient-access-encounter-orchestration.md)
   — end-to-end + per-context maps, 3-lane swimlanes, state machine, decision-point register, patient message
   catalog, service→value mapping, telemedicine 7-stage. (Mandated FIRST by the T3 Addendum.)
2. **Cross-program audit + Lovable absorption matrix** → [`docs/audits/provider-clinical-place/cross-program-audit.md`](../../audits/provider-clinical-place/cross-program-audit.md)
   — grounded Live/Partial/Fixture/NotWired/Missing across the real repo.
3. **Facility-Mode ownership split** → [`facility-mode-ownership-split.md`](facility-mode-ownership-split.md)
   — **T1 ENTERS · T4 BUILDS**; one Facility Mode, every file assigned.
4. **Shared read-models & API contracts (frozen)** → [`shared-read-models.md`](shared-read-models.md)
   — 9 contracts so lanes compose without colliding.
5. **Tshepo policy contract list (spec only)** → [`tshepo-policy-contract-list.md`](tshepo-policy-contract-list.md)
   — every rule T1/T3/T4 need; **PolicyEngine is CZO-locked — specced, not authored**; routes to WS-OPA / CZO lead.
6. **Lean implementation-lane plan** → [`implementation-lane-plan.md`](implementation-lane-plan.md)
   — 4 lanes by disjoint service ownership, migration assignments, collision notes, merge order. **Round-2 spawn spec.**

## The carve (round-2 lanes)

| Lane | Services owned | Headline net-new |
|------|----------------|------------------|
| **L1 Clinical/Encounter** | PCT + inpatient | Cadre Engine, Sorting Desk, Problems/OPD-care-plan, community context, telemedicine completeness, adaptive cockpit |
| **L2 Facility/Place/Org** | TUSO + Indawo + WGV | Facility-Mode cockpit + setup wizard, Indawo surveillance/field-teams, org/regulator model, scoped dashboards |
| **L3 Provider Experience** | Vashandi + Varapi + VITO/tshepo-identity + BFF session + web shell | person-first hardening, Provider-ID-deny, context picker, life/pro/work separation, bootstrap/self-claim *(coordinates w/ CZO)* |
| **L4 Access/Value/Compensation** | COSTA + Coverage + MUSheX | emergency reconciliation, waiver CRUD, subsidy enrolment, telemed→value, value-event completeness |
| *(P) Policy track* | *WS-OPA `impilo.authz` / CZO lead* | *not a lane — never edit `PolicyEngine.java`* |

## Five laws enforced throughout

1. Emergency care never blocked by identity/payment (override + deferred reconciliation).
2. Provider/citizen context separation absolute.
3. No source-of-record duplication (SoR-first).
4. Patient messages plain-language + multilingual-ready (en/sn/nd).
5. Every meaningful action has state + event + permission + audit meaning.

**Status: design complete — hand back. Implementation begins in round 2.**
