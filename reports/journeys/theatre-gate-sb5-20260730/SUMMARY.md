# Ten theatre rigs — re-run after SB-5 (V304), 2026-07-30

Second full gate run of the day, this time with `inpatient-service` carrying V304 (eleven
operative-record columns and the operative-template reference on `procedure_note`) and the
`ProcedureNoteEntity` / `TheatreService` wiring for them.

## Result: no regression — all ten still at baseline

| Rig | Baseline | Pre-SB-5 run | This run |
|---|---|---|---|
| elective | 36/36 | 36/36 | **36/36** |
| elective-completeness | 14/16 | 14/16 | **14/16** (same two amber) |
| emergency | 26/26 | 26/26 | **26/26** |
| persistence | 5/0 | 5/0 | **5/0** |
| authz | 11/0 | 11/0 | **11/0** |
| clinical-safety | 18/18 | 18/18 | **18/18** |
| commodities | 23/23 | 23/23 | **23/23** |
| alt | 34/34 | 34/34 | **34/34** |
| queue-drainage | 14/14 | 14/14 | **14/14** |
| recovery-reporting | 16/16 | 16/16 | **16/16** |

SB-5's own proof is separate: `procedures-operative-record-journeys.sh`, 20/20 against the full
inpatient migration chain on real Postgres, including negative assertions that the database itself
refuses an invented wound classification and a half-supplied template reference.

## Two rig defects found and fixed while running the gate

Neither is a product defect; both make the gate unreliable, and one is actively dangerous.

**`kill 0` kills the caller.** `theatre-persistence` and `theatre-authz` both had
`cleanup(){ ... kill "${SVC_PID:-0}" ...}`. On any exit *before* the service is launched — an infra
failure, a missing JAR — `SVC_PID` is unset, so this evaluates to `kill 0`, and **`kill 0` signals
the entire process group**, which is whatever invoked the rig. A driver looping over the ten rigs
was killed silently, mid-run, twice, with no error attributable to it. Any future runner scripting
these rigs would have hit the same thing. Now guarded on `SVC_PID` being set.

**`pg_isready -U` races initdb.** `postgres:16-alpine` runs a temporary server during
initialisation that listens on the UNIX socket only. `pg_isready -U impilo` checks that socket, so
it reports ready during initialisation; the `CREATE DATABASE` that follows then fails with
`the database system is shutting down`, which is what took `theatre-persistence` down. Now asks
over TCP, which only the real server listens on, and confirms with an actual query.

Six other rigs share the `pg_isready -U` pattern and are latent for the same race:
`theatre-queue-drainage`, `dags-permit-enforcement`, `emergency-pathway-integrity`,
`hpa-enrichment-journeys`, `tshepo-authz-stepup`, `tshepo-keys-signing`. Left alone here — they
belong to other lanes and did not fail in this run — but recorded so the next person to see the
symptom does not have to rediscover the cause.
