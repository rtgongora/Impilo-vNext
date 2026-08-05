# ADR-0054 — Architecture freeze: Hybrid / Federated Target Architecture v1.3.8

**Status:** Accepted · **Date:** 2026-08-05 · **Decision by:** Product Owner

> Numbering note: the repository's numbered ADR sequence lives in `docs/adr/` (highest prior: `ADR-0053`). This ADR is filed under `docs/architecture/adr/`, which is the architecture-scoped ADR location and already holds two ADRs. The identifier **ADR-0054** is the next valid one in the shared sequence and is not reused in either directory.

## Decision

**Impilo vNext Hybrid / Federated Target Architecture v1.3.8 is APPROVED and ARCHITECTURE-FROZEN**, effective 2026-08-05. It is the governing baseline for federation, trust domains, node profiles, authority, record topology and the Experience Plane.

The frozen artefact is `docs/architecture/hybrid-federated-target-architecture-v1.3.8.md`. The unversioned `vnext-hybrid-federation-target-architecture.md` remains a pointer to it and is never a second copy.

## What was frozen, and what was not

**v1.3.1 through v1.3.7 were never frozen.** Each corrected its predecessor and was then refused freeze at its own review — eight refusals in total, on defects including a deleted section, an invariant citing a section that never existed, a `NOT NULL` column that pre-empted an open legal question, a safety default the deciding function never read, and repeated acceptance criteria that proved a different claim from the one citing them. Every one of those versions is archived under `docs/architecture/archive/working-drafts/` with a banner recording that it was never frozen and why. **None of them may be cited as authority.**

v1.3.8 was approved after a freeze review that found no stop condition, subject to one pre-freeze erratum: two active references still used the superseded acceptance range `A87–A108` after §23.7 added A109–A117. Both were corrected before this decision was recorded.

## Effect of freeze by statement class

| Class | Effect from 2026-08-05 | Changed by |
|---|---|---|
| **`[D]` Doctrine** | Binding | A Product Owner ruling recorded as an ADR |
| **`[T]` Technical design** | Governs implementation | A new ADR **and** a new architecture version |
| **`[O]` Operating model** | Binds within the decision authority the architecture states | The named service owner together with the Product Owner |
| **`[L]` Legal / governance** | **Unresolved. Freeze decides none of them.** The eighteen decisions in §26.2 remain open with their named role owners and blocked phases | Only the named authority in §26.2 |

Modelling an `[L]` matter to support the available options is not deciding it. No schema, default or convenience may stand in for a determination that has not been made — the nullable `data_controller_id` and its refusal rule exist precisely so the undetermined case stays representable.

## What freeze does not authorise

**Architecture freeze establishes the governing baseline. It does not constitute implementation, runtime acceptance, production readiness or deployment authorisation.**

Specifically, this decision authorises **no** implementation wave by itself, and authorises **no** deployment, migration, federation activation, node pilot, organisation onboarding or production release. It makes no legal determination, retires no website, and permits deletion of no repository or branch.

Eligibility is not authorisation. Every implementation wave additionally requires: phase eligibility under §22, a defined scope, tests, evidence, pull-request review, and explicit Product Owner authorisation. The §Post-freeze implementation control section of the frozen document states which areas are eligible, which remain blocked behind phase and dependency gates, and which are never authorised by freeze alone.

**Freeze does not turn a specified acceptance criterion into a passing test.** A87–A117 are governed architecture acceptance criteria; several are explicitly `Executable test: no`. §38A is authoritative for what has been proved, and **no journey currently holds `PASSING`** — zero of twenty-four. That is the honest position on the day of freeze and no wave may cite a specified-only criterion as evidence.

## Change control

**Substantive change requires a new architecture version** — v1.3.9, v1.4 or another explicitly versioned successor. Substantive means any change to doctrine, technical design, operating model, schemas, contracts, gates, journey behaviour or acceptance meaning.

**Non-substantive errata** may be applied to frozen v1.3.8 under the governed errata process: the change must alter none of the above, must be marked at its site as an erratum with its date, and must leave the governance verifier green. The stale-range correction recorded above is the reference example — it corrected a superseded range in two active statements and changed no meaning.

The governance verifier (`scripts/architecture/verify-governance-pack.sh`) enforces this ADR mechanically: it fails if v1.3.8 loses its frozen status, if this ADR reference or the approval date disappears, if any earlier version is described as frozen, if the freeze-versus-implementation distinction is removed, if an `[L]` matter is globally described as settled, if a second active architecture copy appears, if the pointer stops resolving to the frozen baseline, if the frozen content changes without a version change, or if any of its twenty declared checks fails to run.

## Consequences

Document-status uncertainty is removed; teams may plan against a stable baseline. The cost is deliberate friction: a substantive correction now costs a version, which is the point — eight refused freezes established that this document's failure mode is a correction applied to the instance in front of the reviewer rather than to the class, and versioning makes that visible.
