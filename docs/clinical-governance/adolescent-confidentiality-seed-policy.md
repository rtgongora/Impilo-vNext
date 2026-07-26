# Adolescent confidentiality engineering seed — clinical governance note

## Status

The `SPECIALLY_PROTECTED` **enforcement mechanism** is built, tested and shipped. The **content that
decides what it means** is an engineering seed and is **inert**.

| Artefact | State |
|---|---|
| `services/tshepo-authz-service/src/main/resources/policy/adolescent-confidentiality-pack.json` | `ENGINEERING_SEED`, `effective: false` |
| `tshepo.authz.confidentiality-mode` | `SHADOW` |
| zibo `V008` code→category map (`ConfidentialCategoryService.CATEGORY_MAP_RATIFIED`) | `false` |
| tshepo-authz `V048` lane policy rules | `active = false` |

While inert, the PDP evaluates and audits what it *would* have done, grants nothing and denies
nothing; zibo reports which categories a record's codes matched but hands back **no sensitivity
class to stamp**.

## Why the content cannot be an engineering decision

Two questions govern behaviour, and neither is ours to answer:

1. **At what age does a young person's record become confidential from their parent?** This is
   Zimbabwean law and MoHCC policy, and it is **not uniform** — independent consent for HIV testing,
   for contraception and for mental health care sit under different instruments, amended at different
   times. A single "adolescent age" would be wrong for most services.
2. **Which clinical codes are confidential by nature?** Governed terminology, seeded in zibo `V008`
   as ICD-10 prefix→category mappings.

Every age threshold in the pack therefore ships as `null` with `verificationStatus: UNVERIFIED` and a
`legalBasisToVerify` naming the instrument that would settle it. **A guessed age that looks
authoritative is worse than an obviously missing one** — the missing one prompts the question, the
guess ends it.

## The rule that governs this whole area

> **No record may ever carry a protection label that does not protect it.**

A record marked `SPECIALLY_PROTECTED` while enforcement is inert would look protected in the schema
and in any UI showing the label, while being readable by exactly the same people. That manufactures a
false assurance for the clinician deciding whether it is safe to write something down, and for the
adolescent being told their record is confidential. It is worse than leaving the record unmarked.

This is why the switches are gated together and why zibo withholds the stampable class rather than
just the enforcement.

## Requirements before production use

1. **Legal review** of each `guardianConfidentialityRules` entry: set
   `confidentialFromGuardianAgeYears`, set `verificationStatus: VERIFIED`, and record the citation in
   `sourceRefs`. Resolve the `openQuestions` on each entry, in particular:
   - Does independent consent to HIV testing imply confidentiality of the result from a guardian, or
     are those separate determinations?
   - How does the mandatory-reporting duty for a sexual offence against a minor interact with
     confidentiality from the guardian? **This is the sharpest conflict in the pack** and must be
     resolved before any SRH threshold is set.
   - `SAFEGUARDING` and `GENDER_BASED_VIOLENCE` are not age-derived at all — the person the record
     must be confidential *from* is frequently the guardian. These likely need a relational rule
     shape (confidential from a **named** person regardless of age) that the current pack does not
     have. Flagged rather than guessed.
   - Where a young person discloses suicidal intent, confidentiality from the guardian may be
     actively unsafe. Decide whether a per-record safety override is needed.
2. **Clinical governance sign-off** on the six categories and on all 64 ICD-10 prefix mappings in
   zibo `V008`. Under-classifying leaks a confidence; over-classifying hides records from the
   clinicians treating the person. Both harm.
3. **MoHCC ratification of the cadre list** in `V048`. Activating a rule there gives that role access
   to adolescent sexual-health or safeguarding records. The seed is minimal — the person themselves,
   clinicians and nurses staffing the service, and the safety focal for safeguarding. Nurses are
   included because they staff most adolescent and sexual-health care in Zimbabwe; excluding them
   would push the work outside the record entirely.
4. **Flip the switches together.** Pack `approvalStatus`/`effective`, `CATEGORY_MAP_RATIFIED`, the
   `V048` `active` flags, and `confidentiality-mode: ENFORCE`. They are one governance act, not four.
   `ENFORCE` with an unratified pack deliberately refuses to enforce and emits
   `CONFIDENTIALITY_ENFORCE_UNAVAILABLE` — it will not silently half-work.
5. **Review the shadow audit stream first.** Run in `SHADOW` against real traffic and read the
   `CONFIDENTIAL_ACCESS_REFUSED` events with `enforced: false`. They are the list of accesses that
   would break on the day you enforce. Enforcing without reading them is how a confidentiality
   control takes a service down.

## Emergency access is a hard requirement, not a nice-to-have

`EMERGENCY` and `BREAK_GLASS` purpose-of-use **waive** the category requirement entirely, at both the
PDP (Step 4.7) and the PEP (`SpeciallyProtectedVisibilityGuard`), mirroring `ClinicalAccessGuard` in
pct-service. Over-restricting kills people too: a teenager arriving unconscious whose HIV status or
medication explains the presentation must not be invisible to the clinician treating them. The waiver
is checked *before* the delegate exclusion, so an accompanying adult handing over a collapsed
teenager is not the reason the clinical picture stays hidden.

**The waiver is detection, not prevention.** `EMERGENCY` is a self-asserted header; the control over
its misuse is that every waiver is logged at WARN naming the actor and emits a
`CONFIDENTIAL_ACCESS_GRANTED` governance event. That only protects anyone **if the stream is actually
reviewed.** Assign an owner for that review before enforcing, or the waiver is an unmonitored bypass.

## Assistant / service behaviour while inert

- zibo `classify` returns `confidential: false` and `sensitivityClass: null` even when categories
  match. Callers must not stamp a record on the strength of `categories` alone.
- An uncovered code system, and an entirely absent map, are reported in `unmatchedCodeSystems` rather
  than answered "not confidential". "No list loaded" and "nothing is confidential" must never look
  the same to a caller deciding whether to stamp a record.
- The PDP's whole-set grant (`*`) for self-access and the emergency waiver is a property of the
  *mechanism*, not the content, so it survives an inert pack. If the inert pack revoked it, emergency
  care would break before the content was ever ratified.

## Related

- `docs/authorization-visibility-model.md` — the confidentiality control in the wider visibility model
- `docs/clinical/paediatric-domain-pack/implementation-report.md` §5 — Wave 5, journey 5
- `docs/clinical-governance/edliz-engineering-seed-policy.md` — the same seed-then-ratify pattern
