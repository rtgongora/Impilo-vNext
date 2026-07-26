# IATG lease — Paediatric Clinical Domain Pack

Companion to `iatg-adult-medicine-leases.md`, `iatg-emergency-leases.md`, `iatg-rmnp-leases.md`,
`iatg-surgery-procedures-leases.md` and `iatg-trauma-leases.md`. Records what this programme writes
and which migration numbers it holds.

**Written late, and that is the finding.** This pack shipped growth, immunisations, newborn records,
IMNCI, dosing and IMAM episodes without ever registering a lease. Its migrations were cut by
measuring the head of the branch instead of reading the peers' claims — see §3 for what that cost.

---

## 1. Paths this programme owns

| Owns | Shares with |
|---|---|
| `libs/paediatric-domain/**` — age bands, corrected age, WHO growth engine, nutrition classification | Nobody. Pure library, no Spring, consumed by services and offline packs. |
| `services/pct-service/**` — `pct_growth_measurements`, `pct_immunizations`, `pct_newborn_birth_records`, `pct_imam_episodes`, `pct_imam_visits` and their services/controllers | Module shared with Adult Medicine (problem list, medical episode), Emergency (`emergency_episode`, `ed_*`), RMNP (pregnancy, labour observations) and Surgery. Coordinate on `AdmissionController` and the `core/forms` resolver. |
| `services/clinical-knowledge-platform-service/**` — paediatric rule **content**: danger signs, IMNCI classification tables, EPI schedule, growth intelligence, paediatric dosing, IMAM programme | Content is classpath JSON, **not migrations** (following RMNP and emergency). `rules/tabular/**` is hot for RMNP — extend via content and the `bandKey` / `appliesWhen` hooks, never by editing `PredicateEvaluator`. The young-infant PSBI predicate is shared verbatim between the danger-sign rule and the IMNCI classification row: **editing one means editing the other.** |
| `services/inpatient-service/**` — PEWS thresholds, `NeonatalAdmissionHandler` | Emergency and trauma also write here. |
| `ui/one-ui-shell/src/features/paediatrics/**`, `src/app/ehr/[patientId]/{paediatrics,growth-chart,imam}`, `src/app/clinical/nutrition-tracing` | `routes.ts` and `EXPECTED_ROUTE_COUNT` are hot for every lane — merge additively and re-count. |

---

## 2. Reserved migration blocks

**This programme owns the `V400`–`V429` band in every service it co-edits.** New services of its own
start at `V001`.

| Service | Reserved | Consumed |
|---|---|---|
| `pct-service` | **V400–V429** | IMAM episodes + visits `V400` · IMAM tracing notification `V401` |
| `clinical-knowledge-platform-service` | — | none; paediatric clinical content is classpath JSON |
| `inpatient-service` | — | PEWS thresholds are content; the V066 EWS migration predates this lease |

Growth (`V053`), immunisations (`V054`) and newborn records (`V055`) were cut before the lease
existed and are landed and immutable. They are recorded here for completeness, not as a claim on
the V050s — that range is RMNP's neighbourhood now.

### 2a. Why V400 and not the next free number

The bands in play on `pct-service` are RMNP `V058–V069`, Emergency `V070–V099` and `V200s`, Adult
Medicine `V100–V129`, Surgery/Procedures `V300–V329`. Numeric distance is what makes a band safe:
nothing incremental reaches V400, so the band cannot be consumed by a lane that merely counts
upwards from the head.

---

## 3. What cutting a number without reading this file cost

The IMAM episode migrations were cut as `V058`/`V059` by taking the head of `pct-service` on this
branch. Both numbers were already RMNP's — `V058__reproductive_intention` and
`V059__pregnancy_episode`. The collision has three properties worth writing down, because none of
them is obvious:

1. **Git merges it perfectly cleanly.** Different filenames, no conflict, no warning. It is not a
   merge problem; it is a namespace problem that merge tooling cannot see.
2. **It breaks asymmetrically at runtime.** On a fresh database Flyway refuses to start
   ("more than one migration with version 58"). On an estate where yours is already applied, Flyway
   marks the *other lane's* migration as done and their tables are never created — a silent,
   data-shaped failure in somebody else's programme, caused by your merge.
3. **The second case had already happened.** `pct_reproductive_intentions`,
   `pct_pregnancy_episodes` and `pct_pregnancy_dating_revisions` did not exist on
   `impilo-full-preview` and never would have. Repaired 2026-07-26 by dropping the IMAM tables and
   their two history rows and redeploying, after which 058–061, 100–103 and the IMAM pair applied
   in order.

Then two further numbers were burned before landing here:

- **V102/V103** — claimed off a scan of every remote branch. The merge itself brought
  `V102__clinical_documents` and the re-merge before push brought `V103__problem_links`.
  **A number verified against a pre-merge scan is stale by the time the merge finishes.**
- **V104/V105** — inside Adult Medicine's reserved `V100–V129`, with unpushed work in the range.
  Landing there would have collided with a lane that had done nothing wrong.

**The rule that survives all of this:** reserve a band *above every peer's claimed block*, read from
every `iatg-*-leases.md` on current canonical — not from the highest number visible in `git log`,
and not from `ls` on the migration directory. A head is a measurement; a claim is an agreement.

### 3a. The two checks before cutting any number here

```
ls services/<svc>/src/main/resources/db/migration | sort -V | tail
git status --porcelain services/*/src/main/resources/db/migration/
```

The second is not optional: the dangerous neighbour is the migration nobody has committed yet
(this is how RMNP found Adult Medicine's untracked `pct` V060). Both, plus reading the lease files.

### 3b. And after any rename

`mvn clean package`, then **list the jar**:

```
unzip -l target/<svc>.jar | grep -oE 'V[0-9]+__[a-z_]+\.sql' | sort
```

`mvn package` does not delete removed resources from `target/classes`, so a renamed migration ships
as *both* numbers and Flyway refuses to start. Caught here only by listing the jar.

---

## 4. Clinical content governance

Everything this pack ships as clinical content is `ENGINEERING_SEED` with
`adaptationAuthority: PENDING_MOHCC_RATIFICATION`, carries provenance, and is executed against the
live engine as a build gate (`PaediatricRuleContentTest`, `DoseCalculationServiceTest`,
`ImamProgrammeServiceTest`). A content edit that changes clinical behaviour fails the build before
it can reach a patient. None of it is national protocol and none of it should drive care until
MoHCC and a paediatric specialist have signed it off.
