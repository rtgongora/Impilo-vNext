# Surgical CDS rule fragments — authored, NOT yet integrated

**147 clinical decision-support rules** across all fifteen surgical specialties, authored during
Wave SB-4 (2026-07-28) and preserved here **unintegrated**. Fifteen `*-rules.jsonl` files, one per
specialty; each file's first line is a `{"_meta": true, ...}` header, so the raw line count is 162
and the rule count is 147.

## Status: content exists, integration does not

These rules are **not loaded into any service** and are **not executable**. Nothing reads this
directory at runtime. They are checked in so that authored clinical content is not lost, not
because they are wired up.

They were deliberately not integrated in SB-4: mapping rule content into
`clinical-knowledge-platform-service` (a co-edited service) needs its real schema reviewed first
rather than a guessed column mapping. That review has since been done — see the constraints
below, which are the actual blocking work.

Every fragment carries `"status": "ENGINEERING_SEED"` and a
`PENDING_MOHCC_RATIFICATION` note. They are engineering-authored, **not** Ministry-ratified, and
must not be presented as clinical guidance in that state.

## Shape

Each rule line carries: `layer`, `rule_key`, `title`, `specialty`, `logic` (prose), `action`
(prose). Six layers, per `dak-baseline.md` §6:

`DANGER_SIGN` · `THERAPY` · `MONITORING` · `FOLLOW_UP` · `CLASSIFICATION` · `DATA_VALIDATION`

## What integration actually requires (verified against the real schema)

Target is `clinical.rule_definitions`
(`services/clinical-knowledge-platform-service/src/main/resources/db/migration/V001__clinical_platform_schema.sql:118`).
Four real obstacles, none of them mechanical:

1. **No `approval_status` column exists.** The programme plan assumed one. Content-maturity
   honesty has to ride on `source_refs_json` or `explanation_template` instead — decide
   deliberately, do not drop the flag.
2. **No `tenant_id` column**, unlike every table this programme has built. Rules are global.
3. **`code` is globally `UNIQUE`.** The fragments' `rule_key` values (`SURG-BE-01`, …) are only
   unique within this pack and **will collide** with another lane's rules unless namespaced
   (e.g. `SURG-BE-01` → a prefixed form) before insert.
4. **Three required NOT NULL fields the fragments do not carry**: `severity`,
   `message_template`, `effective_start`. `severity` in particular is a clinical judgement per
   rule — deriving it mechanically from `layer` would fabricate precision the authors did not
   supply. `logic` and `action` are prose, not a `logic_expression` the engine can evaluate.

Migration band for this programme in CKP is **V300–V329** (V200–V202 belong to the emergency
lane), so the target file is `V300__surgical_cds_rules.sql`.

## Honest assessment

Integrating these is not a data-loading task. Items 3 and 4 are content decisions that change
what the rules mean at the point of use, and item 4 means the rules are **advisory prose today,
not executable logic** — a rule whose `logic` is a sentence cannot fire. Treat this as its own
wave with clinical input, not a chore appended to another.
