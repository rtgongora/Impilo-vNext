# Ten theatre rigs — regression gate run, 2026-07-30

First run of the full gate by the surgery + clinical-procedures programme since Wave P4.
Base commit `ae6ca5019`, worktree `wt-surgery-completion`, real Postgres/Redis/Kafka in Docker,
services as freshly packaged JARs.

## Result: gate CLEARED — every rig matches its recorded baseline

| Rig | Baseline at programme open | This run | Verdict |
|---|---|---|---|
| elective | 36/36 | **36/36** | matches |
| clinical-safety | 18/18 | **18/18** | matches |
| commodities | 23/23 | **23/23** | matches |
| elective-completeness | 14/16 (2 amber board assertions) | **14/16** (same two) | matches |
| recovery-reporting | 16/16 | **16/16** | matches |
| emergency | 26/26 | **26/26** | matches |
| alt | 34/34 | **34/34** | matches |
| authz | 11/0 | **11/0** | matches |
| persistence | 5/0 | **5/0** | matches |
| queue-drainage | 14/14 | **14/14** | matches |

Baselines are `docs/registry/iatg-surgery-procedures-leases.md` §5 and
`docs/inpatient/theatre-completion-gates-2026-07-15.md`.

The two `elective-completeness` failures are the two already-documented amber assertions, both in
J-TE-8 (`theatre-elective-completeness-journeys.sh:174,176`): `board did not block` and
`resolve-blocker did not route`. They are unchanged in identity and count, and are inherited, not
caused here.

## What the gate caught: a total outage of theatre intake

The very first assertion of the very first rig failed on unmodified canonical:

```
J-TH-1: elective theatre case — intake to COMPLETED
   FAIL: intake HTTP 500
ERROR: null value in column "setting" of relation "procedure_episode" violates not-null constraint
```

`V300__procedure_episode_site_side_and_setting.sql:47` (this programme's own Wave P4) added
`setting VARCHAR(48) NOT NULL DEFAULT 'THEATRE'`. A database default only applies when the INSERT
omits the column. Hibernate names every mapped column on every insert, so it sent an explicit
`null`, and nothing in the codebase ever called `setSetting`. **Every creation of a
`procedure_episode` through JPA failed — elective and emergency intake alike.**

Fixed by mirroring the database default on the entity
(`ProcedureEpisodeEntity.setting = SETTING_THEATRE`, with `nullable = false` and a
null-coalescing setter). `procedure_specimen.adequacy` (V301, one migration later) already had
exactly this shape done correctly, so the fix restores consistency within the programme's own work
rather than introducing a new idiom.

## Why five waves of green module tests never saw it

`services/inpatient-service/src/test/resources/application-test.yml` sets `flyway.enabled: false`
and `ddl-auto: create-drop`. **The module's tests build their schema from the JPA entities, not
from the migrations**, so any constraint that exists only in a Flyway migration is structurally
invisible to every test in the module. A fully green suite and a completely broken production
insert path were never in contradiction.

This is the concrete cost of deferring the gate for five consecutive waves, and the concrete
argument for the rigs existing at all.

Pinned by `ProcedureEpisodeColumnDefaultTest`, which asserts the default on both columns and the
persistence round-trip. Its negative control is this run itself: the defect was observed live,
in the real environment, before the fix and not after.
