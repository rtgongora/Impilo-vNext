#!/usr/bin/env bash
# RMNP capture coverage guard.
#
# A clinical rule evaluates named facts. A form is what a clinician actually fills in. When a rule
# needs a fact no form can capture, the rule can never fire — it sits in the content looking
# implemented while the finding it depends on is never recorded, and the failure is silent from both
# ends: the form looks complete and the engine looks complete. Nothing errors, nothing warns, and
# the alert simply never comes.
#
# The paediatric equivalent of this guard found two genuinely unfireable rules the first time it
# ran. This one exists before the reproductive rule content does, deliberately, so that the content
# cannot be written without the capture arriving in the same change.
#
# It walks every predicate shape the evaluator understands, including the ones added for this
# domain: bandKey, appliesWhen, atLeast/of, and optionalCriterion.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

CONTENT_DIR=services/clinical-knowledge-platform-service/src/main/resources/clinical
FORMS_DIR=services/forms-service/src/main/resources/seed-forms

echo "=== RMNP capture coverage guard ==="

python3 - "$CONTENT_DIR" "$FORMS_DIR" <<'PY'
import json
import pathlib
import sys

content_dir = pathlib.Path(sys.argv[1])
forms_dir = pathlib.Path(sys.argv[2])

# Which rule packs are checked against which forms. A pack with no paired form would be checked
# against nothing and would pass vacuously, so pairing is declared rather than inferred.
PAIRINGS = {
    # WHO near-miss criteria are genuinely clinician-observed (gasping, cyanosis, shock) and measured
    # (creatinine, platelets, lactate). Declaring this NO_CAPTURE would have been the dishonest option:
    # it reads no derived fact, it reads a bedside assessment somebody has to record.
    "rmnp-maternal-near-miss.json": ["21-maternal-near-miss.json"],
    "rmnp-anc-danger-signs.json": ["07-anc-first-contact.json", "13-anc-contact-followup.json"],
    "rmnp-anc-classification-tables.json": ["07-anc-first-contact.json", "13-anc-contact-followup.json"],
    "rmnp-anc-routine-actions.json": ["07-anc-first-contact.json", "13-anc-contact-followup.json"],
    "rmnp-obstetric-danger-signs.json": ["13-labour-partograph-observation.json",
                                        "15-labour-admission.json"],
    "rmnp-pnc-maternal-danger-signs.json": ["16-pnc-maternal-contact.json"],
    "rmnp-pnc-newborn-danger-signs.json": ["17-pnc-newborn-contact.json",
                                           "12-imci-young-infant-assessment.json"],
    "rmnp-labour-care-guide.json": ["13-labour-partograph-observation.json"],
    "rmnp-smbp-escalation.json": ["20-smbp-home-readings.json"],
    "rmnp-early-pregnancy-danger.json": ["19-early-pregnancy-assessment.json"],
    "rmnp-spr-rules.json": ["18-family-planning-eligibility.json"],
    "rmnp-mec-matrix.json": ["18-family-planning-eligibility.json"],
    "rmnp-reproductive-intention.json": ["18-family-planning-eligibility.json"],
}

# Facts an engine computes and injects rather than a clinician entering. Deliberately tight: parity
# and previousCaesarean ARE captured on a form and must not be waved in here, because "the engine
# knows it" is exactly the excuse that would hide a genuine capture gap.
DERIVED = {
    "ageDays", "ageMonths", "ageYears",
    "gestationalAgeDays", "gestationalAgeWeeks",
    "hoursSinceBirth", "daysSinceBirth", "postpartumDay",
    "minutesSinceOpen", "minutesSinceLastReassessment",
    "stepsCompleted", "stepsOverdue",
    "pregnant", "pluralityDerived",
    # The engine resolves the SPR checklist and injects the verdict; a clinician never types
    # "reasonably certain" as a field. The six criteria that produce it are NOT derived and are
    # checked against the form like everything else.
    "pregnancyCertainty",
    # National-policy parameters, injected as configuration rather than asked of the woman: whether
    # her area is malaria-endemic and whether the population has low dietary calcium are ministry
    # determinations, not per-contact fields. Deliberately NARROW — the per-woman routine-action
    # inputs (tetanus protection, IPTp dose count, pre-eclampsia risk) are NOT here and must be
    # captured on a form, because "the policy knows it" is not true of them.
    "malariaEndemicArea", "lowCalciumIntakePopulation",
}
DERIVED_PREFIXES = ("minutesSinceLastObservationOf.", "smbp.", "robson.")


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"FAIL: could not parse {path}: {exc}")
        raise SystemExit(1)


def captured_link_ids(form_doc):
    """Every linkId a clinician can fill in on this form."""
    out = set()
    definition = form_doc.get("definition", form_doc)
    for section in definition.get("sections", []):
        for field in section.get("fields", []):
            link = field.get("linkId")
            if link:
                out.add(link)
                # A nested fact path is satisfied by its parent field: a form capturing "vitals"
                # as a group satisfies "vitals.systolicBp".
                out.add(link.split(".")[0])
    return out


def walk_inputs(node, found):
    """Every fact an engine would look up, across all predicate shapes."""
    if isinstance(node, dict):
        for key in ("input", "finding"):
            value = node.get(key)
            if isinstance(value, str):
                found.add(value)
        # bands may score against a scale other than ageDays
        band_key = node.get("bandKey")
        if isinstance(band_key, str):
            found.add(band_key)
        for value in node.values():
            walk_inputs(value, found)
    elif isinstance(node, list):
        for item in node:
            walk_inputs(item, found)


def rule_inputs(pack):
    """Declared requiredInputs plus everything the logic actually reads.

    Both, because they fail differently: a requiredInput nobody reads is dead weight, and a fact the
    logic reads without declaring it is invisible to the "what could not be assessed" report.
    """
    found = set()
    # "criteria" is here because the SPR reasonable-certainty checklist is not a rule list: its six
    # criteria are combined by the engine rather than fired independently, so they live in their own
    # collection. Left out, the single most consequential question in family planning — is she
    # pregnant — would have been checked against nothing and passed.
    for collection in ("rules", "tables", "conditions", "criteria",
                       "signals", "services", "bundles"):
        for item in pack.get(collection, []) or []:
            for declared in item.get("requiredInputs", []) or []:
                found.add(declared)
            for key in ("logic", "appliesWhen", "trigger", "openWhile", "closeWhen", "abandonWhen"):
                if key in item:
                    walk_inputs(item[key], found)
            for tier in item.get("tiers", []) or []:
                walk_inputs(tier.get("logic"), found)
    return found


def is_derived(fact):
    return fact in DERIVED or any(fact.startswith(p) for p in DERIVED_PREFIXES)


rmnp_packs = sorted(content_dir.glob("rmnp-*.json"))
if not rmnp_packs:
    # This exit used to be SystemExit(0), because the guard was deliberately wired before the content
    # existed. The content has since landed, so "no packs" no longer means "not written yet" — it
    # means this glob stopped reaching the pack directory, and every rule below went unchecked while
    # the guard reported success. Self-reach is the whole point: matching nothing is a broken guard,
    # not a clean tree.
    print(f"FAIL: no RMNP rule packs found under {content_dir}/rmnp-*.json.")
    print("      This guard scanned nothing. The RMNP content has landed, so a zero count is a")
    print("      broken scan — check the content directory path above, not the rule packs.")
    raise SystemExit(1)

# Packs that capture nothing a clinician types, and so pair with no form — declared here with the
# reason, NOT left to fall through the "unpaired" branch. The distinction the guard defends is
# between "captures nothing because it was checked against nothing" (a vacuous pass, forbidden) and
# "captures nothing because it genuinely reads no clinician-entered fact" (legitimate, and stated).
# A schedule is driven by gestational age, which is derived, and by the record of which contacts
# happened, which is a record of care rather than a form field. Adding a form to satisfy the guard
# would invent a capture surface that does not and should not exist.
NO_CAPTURE_PACKS = {
    "rmnp-respectful-maternity-care.json":
        "a measure SET, not a rule pack — it computes no verdict and evaluates nothing. It is a "
        "citizen-facing instrument submitted through Rito's EXISTING rating and anonymous public-case "
        "lanes (rit_rating_domain_score carries a free-form measure), so it pairs with no "
        "forms-service form by design, not by omission",
    "rmnp-anc-schedule.json": "a contact schedule — driven by gestational age (derived) and the "
                              "record of completed contacts, neither of which is a form field",
    "rmnp-eclampsia-bundle.json": "an emergency treatment bundle — recorded step completions + injected temporal facts, no form findings",
    "rmnp-maternal-sepsis-bundle.json": "an emergency treatment bundle — recorded step completions + injected temporal facts, no form findings",
    "rmnp-robson-classification.json": "the Robson caesarean classification — computed from the delivery record and pregnancy episode (robson.* injected), a surveillance calculation with no clinician-entered form",
    "rmnp-pph-bundle.json": "an emergency treatment bundle — its inputs are recorded step "
                            "completions (an act log, not a form) and injected temporal facts, so "
                            "there is no clinician-entered finding for a form to capture",
}

failed = False
for pack_path in rmnp_packs:
    pack = load(pack_path)
    if pack_path.name in NO_CAPTURE_PACKS:
        # Still not a free pass: prove it reads no capturable fact, so a schedule that later grows a
        # clinician-entered input cannot hide behind this exemption.
        needed = {f for f in rule_inputs(pack) if not is_derived(f)}
        if needed:
            print(f"FAIL: {pack_path.name} is exempted as capturing nothing, but it reads "
                  f"{len(needed)} non-derived fact(s): {sorted(needed)}. Remove the exemption and "
                  f"pair it with a form.")
            failed = True
        else:
            print(f"  OK  {pack_path.name}: no-capture pack ({NO_CAPTURE_PACKS[pack_path.name]})")
        continue
    forms = PAIRINGS.get(pack_path.name)
    if forms is None:
        print(f"FAIL: {pack_path.name} is not paired with any form in this guard.")
        print("      An unpaired pack is checked against nothing and passes vacuously, which is")
        print("      indistinguishable from being correct. Add it to PAIRINGS.")
        failed = True
        continue

    captured = set()
    missing_forms = []
    for form_name in forms:
        form_path = forms_dir / form_name
        if not form_path.exists():
            missing_forms.append(form_name)
            continue
        captured |= captured_link_ids(load(form_path))

    if missing_forms:
        print(f"FAIL: {pack_path.name} is paired with form(s) that do not exist: "
              f"{', '.join(missing_forms)}")
        failed = True

    needed = {f for f in rule_inputs(pack) if not is_derived(f)}
    uncapturable = sorted(f for f in needed
                          if f not in captured and f.split(".")[0] not in captured)

    if uncapturable:
        print(f"FAIL: {pack_path.name} reads {len(uncapturable)} fact(s) no paired form captures.")
        print("      These rules cannot fire in practice — the finding is never recorded, and")
        print("      nothing about the form or the engine says so.")
        for fact in uncapturable[:20]:
            print(f"        - {fact}")
        if len(uncapturable) > 20:
            print(f"        ... and {len(uncapturable) - 20} more")
        print(f"      Paired forms: {', '.join(forms)}")
        failed = True
    else:
        print(f"  OK  {pack_path.name}: {len(needed)} fact(s), all capturable")

raise SystemExit(1 if failed else 0)
PY
STATUS=$?

if [[ $STATUS -eq 0 ]]; then
  echo "RMNP capture coverage guard: OK"
else
  echo "RMNP capture coverage guard: FAILED"
fi
exit $STATUS
