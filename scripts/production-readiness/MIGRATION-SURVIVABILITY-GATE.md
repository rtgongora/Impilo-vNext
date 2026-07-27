# Migration survivability gate — spec

Status: **spec + worklist**. The order half is built (runtime); the survivability half below is
specified here for the next migration wave to implement. Owner to be assigned by the coordinator.

## Why this exists, and why it is the only place the class is caught

JVM service test profiles set `spring.flyway.enabled: false` and boot against a Hibernate
`ddl-auto` H2 schema (`MODE=PostgreSQL`). Confirmed on **pct** and **daidzai**. Consequence:
`mvn test` **never applies a migration**. The schema under test comes from entity annotations,
which carry no CHECK constraints, no partial/expression indexes, no `ALTER`/`DROP`, and no data
backfills. A migration's DDL is therefore completely unexercised by the green suite.

Live proof: daidzai's `chk_dai_anchored_once_in_facility` (V200) is violated by the normal trauma
phase-advance, yet the test that advances an episode through ED → RESUS → BLOOD passed **50/50**
with the broken migration present. One statement against real Postgres caught it. See memory
`jvm-tests-do-not-apply-migrations`.

So real-Postgres migration checks are the **only** control for this class. There are two distinct
questions, and passing the first does not imply the second:

1. **Installable** — do the migrations apply, in version order, on a clean database? *(built)*
2. **Survivable** — do the constraints they add tolerate the rows the code that already writes
   those tables actually produces? *(this spec)*

## Part 1 — order gate (BUILT, but currently runtime-only — no committed script)

What ran during the out-of-order sweep (24 services, all PASS): drop/recreate each service's
sovereign DB, run `flyway migrate` (strict order, `out-of-order` OFF) against the empty DB, assert
it reaches the current ceiling with no gaps or version errors. This proves **installable**.

**It lives nowhere as a committed artifact.** It was a pod-side runtime procedure. The reset half
is `scripts/production-readiness/reset-sovereign-flyway-dbs.sh`; the migrate/assert half was
ad-hoc. First task for whoever owns the extension: **commit the order gate as a real harness here**
so Part 2 has something to hang off and the next wave inherits a runnable gate, not a memory.

The fast, database-free sibling — two files sharing one Flyway version — is already a committed,
CI-wired, mutation-proven change-safety gate:
`scripts/guard/check-migration-version-collisions.sh`. It is complementary, not a substitute: it
proves uniqueness of version numbers, not that any migration applies.

## Part 2 — survivability gate (TO BUILD)

Runs against real Postgres, after Part 1's clean apply. Not statically mechanizable — it needs a
live database and knowledge of what each existing writer emits — which is exactly why it cannot be
a `scripts/guard/` static check and must live with the runtime gate.

### 2a. Brownfield constraint exercise (the one that takes a service down)

For each migration that adds a constraint (`CHECK`, `FK`, `UNIQUE`, partial index) to a table that
**already existed before that migration**, exercise it against a row **the existing writer actually
produces** — not only against a synthetic violator.

- Positive probe: insert a row shaped like real live traffic → must be **accepted**.
- Negative probe: insert a row that violates the constraint → must be **rejected**.
- Both rolled back.

The failure mode is a constraint that is correct in the abstract and **rejects live traffic**:
Adult Medicine's V100 CHECK aborted on legacy free-text category values the UI had been sending for
months; daidzai V200 rejected the normal phase-advance. A synthetic-violator-only test passes both.
Sourcing the positive row is the hard part and cannot be fully automated — it must come from the
writer's real payload (the service's own INSERT path or a captured live row), not from an
entity-annotation default.

### 2b. `NOT VALID` never validated

Anything added `... NOT VALID` and intended to be validated later must end up
`pg_constraint.convalidated = t` on the live database:

```sql
SELECT conrelid::regclass AS table, conname
FROM   pg_constraint
WHERE  NOT convalidated;
```

Any row is a constraint-shaped object that was never checked against existing data. Note the
semantics precisely so this is not over-reported: a `NOT VALID` FK/CHECK **still enforces new
writes**; it exempts only pre-existing rows until `VALIDATE CONSTRAINT` runs. So an unvalidated
constraint is a *legacy-row integrity gap*, not a wide-open gate — but if validation was intended
and forgotten, the historical rows were never verified and never will be by boot.

**Concrete starting worklist** (from the merged tree at spec time — a static approximation, to be
confirmed against `convalidated` on a live DB, since validation could also happen out-of-band):
migrations adding `NOT VALID`: indawo `V010`, learning `V027`, pct `V061`, pct `V201`, pct `V045`,
tuso `V030`, vashandi `V008`. Only **pct `V201`** pairs it with a `VALIDATE CONSTRAINT` in a
migration. The remainder are unvalidated *in migrations* and are the first rows to check.

### 2c. Report the risk split explicitly

Every constraint the gate exercises must be classified:

- **Low risk** — constraint on a **new** table with only **new** writers. Nothing legacy to reject.
- **High risk** — constraint on an **existing** table with **live** writers. This is the class that
  takes a running service down. These get the 2a brownfield exercise; the low-risk set need only
  the clean-apply proof from Part 1.

## Verification standard (fleet law)

A migration is **landed** when `flyway_schema_history` says so on the named target in the right
(schema-qualified) schema. A constraint is **correct** when it bites there under a real write —
proven with a positive probe (live-shaped row accepted) **and** a negative probe (violator
rejected), both rolled back. Green `mvn test` proves neither: it never applied the migration.
