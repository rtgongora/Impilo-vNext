# RMNP lease — Reproductive, Maternal, Newborn and Perinatal Clinical Domain Pack

Companion to `iatg-trauma-leases.md`, `iatg-emergency-leases.md` and
`iatg-surgery-procedures-leases.md`. Records what this programme writes and which migration numbers
it holds, so a concurrent lane can avoid both without asking.

Programme plan: 11 waves, W0–W10. Sibling of the Paediatric Clinical Domain Pack, covering
everything before the newborn — reproductive intention, contraception, fertility, pregnancy,
antenatal care, labour, delivery, the postnatal dyad, loss, and the transition into paediatrics.

## 1. Files this programme writes

| Owned | Notes |
|---|---|
| `libs/reproductive-domain/**` | New pure-Java module. Spring-free, so the same arithmetic runs in services, batch and offline packages. |
| `services/pct-service/**` — reproductive/maternal records and their services | Shares the module with Paediatrics and Emergency. **Coordinate on `pct_labour_observations`** (Emergency lease §1) and on the `core/forms` resolver. |
| `services/clinical-knowledge-platform-service/**` — RMNP content packs and the engines that read them | **`rules/tabular/**` is hot.** `PredicateEvaluator`, `TabularRule`, `RuleContentLoader` and `DakProvenance` are being changed by this lane. Do not extract them into a library without a handoff. Any change to `PredicateEvaluator` is a retest of the danger-sign engine, both IMNCI packs and the dosing engine. |
| `services/forms-service/**` — ANC/PNC/FP seed forms | |
| `services/experience-bff/**` — maternity and reproductive controllers only | `MaternityPartographController`, `FetalMonitoringController`, `MaternitySummaryController`, and RMNP clinical proxies. |
| `ui/one-ui-shell/src/features/{maternity,reproductive}/**` | Plus `routes.ts` entries. **`routes.ts` is shared** — the facility/HAR lane also edits it. |
| `scripts/clinical/dak/**`, `scripts/guard/check-dak-traceability.sh` | **Shared machinery, deliberately.** The Emergency pack consumes this generator for its own standards baseline rather than forking a rival matrix; rows key on `standardId` + `family`. |
| `docs/reference/who-dak/**`, `docs/clinical-governance/rmnp/**` | Vendored WHO L2 sources and the traceability matrix. |

Not written by this programme: `services/tshepo-service/**` (NO-TOUCH, frozen), `apps/mobile/**`
(the mobile-recovery lane's — this pack delivers APIs and a written contract instead),
`services/tuso-service/**`, `services/varapi-service/**`, `services/ndila-service/**` (read-only
consumption of the facility/provider lane's surfaces).

## 2. Reserved migration blocks

Heads verified on disk immediately before publishing this file.

| Service | Head today | **RMNP block** | Planned use |
|---|---|---|---|
| `pct-service` | V401 | **V058, V059, V061 (landed) + V430–V459 (everything from here)** — see §2c | intention V058 · pregnancy episode + dating revisions V059 · fetuses V061 · **contraception V430** · ANC contacts V431 · delivery records V432 · postnatal + lactation V433 · loss + termination V434 · obstetric emergencies V435 · confidentiality V436 · constraint validation V437 · reserve V438–V459 |
| `clinical-knowledge-platform-service` | V006 | **V041–V050** (see §2b) | rule-definition rows + source-document provenance V041 · national policy parameters V042 · reserve V043–50 |
| `forms-service` | V002 | **V003–V006** | form applicability columns if the ANC/PNC/FP seeds need them |
| `tshepo-authz-service` | V047 | **V048–V052** | SRH sensitivity assignment + adolescent consent policy seeds |
| `vito-service` | V048 | **V055–V059** | mother↔child relationship types, the missing read, idempotency (below the Emergency lane's V060) |
| `ubomi-service` | V005 | **V006–V008** | stillbirth / fetal-death civil notification |

### 2c. Why RMNP abandoned V062–V069 and moved to V430–V459

**The remaining low numbers in this lease are dead space. Do not use them, and check whether your own
lease has the same problem.**

pct's highest applied version on the preview estate is **401**. `application.yml` sets
`validate-on-migrate: false` and does **not** set `out-of-order`, and Flyway's default is to
*silently ignore* a pending migration whose version is below the highest applied one. Ignored means
exactly that: it does not apply, it does not fail, the service starts, the health check is green, and
the table is absent. That is not theoretical — V058 and V059 of this pack were skipped that way on
2026-07-26 and needed a cross-lane repair to recover.

An honest complication, recorded rather than tidied away: the estate **does** show
`V106__structured_history` applying at 18:43 after `V401` applied at 18:19, on this same database.
Out-of-order application is therefore happening somewhere, and the mechanism is not in
`values-full-preview.yaml` (the only `SPRING_FLYWAY_OUT_OF_ORDER: "true"` there is scoped to
`tshepo-authz-service`), not in the pct pod's environment, and not in the service configuration.
**Nobody has explained it, so nobody should build on it.**

The asymmetry settles it without needing the explanation. Numbering above the current maximum costs
nothing if the concern turns out to be unfounded; numbering below it costs a silently absent schema
if it does not, and that failure is invisible at every layer that would normally report it.

Two consequences for other lanes:

1. **If you hold a lease below 401 and have not yet applied it, treat it as unsafe** and move above
   the current maximum. IMAM has V400–V429 (paediatrics); RMNP takes V430–V459.
2. `V107__medication_reconciliation.sql` and `V108__medical_episode_emergency_fk.sql` are on disk,
   unapplied, and below 401. They may be fine — they may simply not have been deployed yet — but
   whoever owns them should confirm against `flyway_schema_history` on the target rather than against
   a green service.

### 2a. pct V060 is not ours, and it is not committed

`V060__problem_severity.sql` exists on disk as an **untracked** file belonging to the adult
problem-list lane. `git log` shows nothing for it. The Emergency lease's table records pct head as
V060, which is correct, but a lane checking only committed history would not have seen it.

**Law for this repository: the working tree is part of the migration namespace.** Before cutting a
number, run both:

```
ls services/<svc>/src/main/resources/db/migration/ | grep -oE '^V[0-9]+' | sort -V | tail
git status --porcelain services/*/src/main/resources/db/migration/
```

The RMNP block is therefore **discontinuous by design**. Do not read V058–V069 as a contiguous range
and re-take V060.

### 2b. Why CKP jumps to V041 rather than V007

The surgery/procedures lease claims CKP V007–V020 and the emergency lease claims V021–V040. This
programme originally asked the Emergency lane for V007–V009, before the surgery lease landed. Rather
than negotiate a three-way split of a low block, RMNP takes **V041–V050**, above both. Migration
numbers are cheap; a collision discovered after a push is not, because `pct` sets
`validate-on-migrate: false` and Flyway `out-of-order` is off everywhere — so a lower-numbered
migration arriving late does not apply *and* does not fail loudly. The schema simply diverges from
what the code expects, silently.

Note that this pack deliberately needs very few CKP migrations: clinical content lives in classpath
JSON, not in the database, so changing a clinical threshold is a reviewable diff rather than a data
migration.

## 3. Coordination already agreed

- **Emergency pack** — will not extract `rules/tabular/**`; consumes this lane's traceability
  generator as a second dataset. It found and fixed a real gap in this lane's event-inventory test:
  `pct.ed.critical_result` is emitted via `setEventType()` rather than `emit(`, so the guard's
  documented grep recipe missed it and the estate's only ED safety event rode the catch-all.
- **Facility / Provider ID lane** — owns TUSO capability and EmONC readiness. This pack consumes
  `emonc-readiness` and `readiness-programme/status` read-only, gates referral on `operational`
  rather than `facility.status`, requires `councilVerified` for assisted birth, caesarean,
  anaesthesia and countersignature, and **never writes a readiness assessment as a side effect of
  clinical activity**.
- **MPDSR firewall** — a maternal death review MAY PROMPT a readiness assessment; it MUST NOT BE
  one. The review raises a task; no case content, narrative or identifying date crosses into the
  facility register.
- **Mobile lane** — owns `apps/mobile/**`. This pack delivers endpoint shapes and a written contract
  for the citizen pregnancy/SMBP surface and the CHW community-postnatal surface.
