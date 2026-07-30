# Surgical CDS rule fragments — authored, and now loaded as inert content

**147 clinical decision-support rules** across all fifteen surgical specialties, authored during
Wave SB-4 (2026-07-28). Fifteen `*-rules.jsonl` files, one per specialty; each file's first line is
a `{"_meta": true, ...}` header, so the raw line count is 162 and the rule count is 147.

These fragments remain the authored artifact and the source of truth for the content. They are
loaded into `clinical.rule_definitions` by
`services/clinical-knowledge-platform-service/src/main/resources/db/migration/V300__surgical_cds_rules.sql`,
which adds no clinical content of its own.

Every fragment is engineering-authored and **not** Ministry-ratified, and must not be presented as
clinical guidance in that state.

## Status: loaded, inspectable, and unable to fire

Loading them made them queryable and reviewable. It did not make them live, and four independent
mechanisms keep it that way:

1. `logic_json` and `logic_expression` are NULL on all 147 rows. The authored `logic` is English
   prose describing a condition, not a predicate tree; it is loaded into `explanation_template`,
   where it is a description, and is never presented as logic.
2. `effective_start` is set beyond any operational horizon, so `RuleDefinitionEntity.activeOn()`
   is false for every row. Ratification replaces it with a real date.
3. `approval_status = ENGINEERING_SEED`, `adaptation_authority = PENDING_MOHCC_RATIFICATION`.
4. `RuleGovernanceService.apply()` only adjusts alerts the deterministic engine has already
   emitted, matched by `code`. No engine emits a `SURG_*` code, so these rows change no behaviour.

Every row is additionally non-interruptive and overridable.

`SurgicalSeedRuleContentTest` asserts all of the above against these fragments and the migration
together, so a later change that made one of these rules live has to break a test to do it.

## Shape

Each rule line carries: `layer`, `rule_key`, `title`, `specialty`, `logic` (prose), `action`
(prose). Six layers, per `dak-baseline.md` §6:

`DANGER_SIGN` · `THERAPY` · `MONITORING` · `FOLLOW_UP` · `CLASSIFICATION` · `DATA_VALIDATION`

## The integration constraints, corrected

An earlier version of this file listed four blocking obstacles. **Two of them were wrong**, having
been checked against `V001__clinical_platform_schema.sql` alone without reading the later
migrations that extend the same table. Recorded here rather than quietly deleted, because the
error is the instructive part: a schema claim is only as current as the last migration you read.

1. ~~No `approval_status` column exists.~~ **Wrong.** `V006__paediatric_rules_framework.sql:30`
   adds `approval_status VARCHAR(32) NOT NULL DEFAULT 'ENGINEERING_SEED'`, along with `layer`
   (whose six values match these fragments exactly), `adaptation_authority`, `required_action`,
   `logic_json` and `test_cases_json`. Content-maturity honesty has a first-class home; no
   workaround via `source_refs_json` was needed.
2. **No `tenant_id` column**, unlike every table this programme has built. Rules are global.
   Still true.
3. **`code` is globally `UNIQUE`.** Still true, and it is the real constraint. The fragments'
   `rule_key` values are unique only within this pack, so V300 namespaces them
   (`SURG-BE-01` → `SURG_BE_01`) before insert. Verified against a real Postgres with the whole
   CKP chain applied: 151 rows, 151 distinct codes, no collision.
4. ~~`severity`, `message_template` and `effective_start` are required, and `logic` is prose the
   engine can never evaluate.~~ **Half wrong.** The three NOT NULL fields are real and V300
   supplies them: `message_template` takes the title, `effective_start` is the
   beyond-horizon date described above, and `severity` is derived from `layer` (`DANGER_SIGN` →
   `HIGH`, else `MEDIUM`). That derivation is an engineering default and not a clinical judgement
   the authors supplied, so every row records `"severity_origin": "LAYER_DEFAULT_NOT_AUTHORED"` in
   `source_refs_json`, letting ratification see exactly which fields still need a clinician.

   The claim that the prose could never be executable was too strong: `logic_json` (also from
   V006) is exactly the column for a structured predicate. What is true is that **nobody has
   written those predicates**, so today the rules genuinely cannot fire — a fact, not a
   permanent property.

## What ratification actually needs

Loading was the mechanical part and it is done. What remains is clinical, per rule: confirm or
replace the derived `severity`, decide whether the rule should be interruptive, author a
`logic_json` predicate for those that should execute, and set a real `effective_start`. That is
work for a clinical authority with MoHCC standing, not for engineering.
