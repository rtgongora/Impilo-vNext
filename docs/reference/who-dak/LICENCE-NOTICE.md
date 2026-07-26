# Licence notice — vendored WHO material and content derived from it

This notice covers `docs/reference/who-dak/**` and the clinical content packs derived from it under
`services/clinical-knowledge-platform-service/src/main/resources/clinical/rmnp-*.json`.

`LEGAL.md` makes this repository proprietary **"except where a specific subdirectory or extracted
artefact explicitly states otherwise."** This is that statement. The material here is not Impilo's
and is not offered under Impilo's terms.

## What is in here and who owns it

The vendored spreadsheets under `smart-anc/`, `smart-fp/`, `dak-pnc-v1.0.0/` and `dak-smbp-v1.0.0/`
are World Health Organization publications, retrieved unmodified and pinned by SHA-256 in
`MANIFEST.json`. WHO publications are typically released under **CC BY-NC-SA 3.0 IGO**. Each entry
in the manifest records the licence as *assumed, not verified*, and carries
`"licenceReviewed": false`.

**Those flags are not decoration and must not be flipped by an engineer.** They are set to false
because nobody with authority to decide has looked, and a `true` there would be a legal assertion
made by whoever happened to be editing JSON.

## Why the material is here at all

WHO SMART Guidelines and Digital Adaptation Kits exist to be implemented in national digital health
systems. That is their stated purpose — a DAK is an implementation artefact, not a reference
document. Implementation by a national health system is the intended use rather than an edge case,
and the material is vendored rather than fetched so that a clinical rule can cite the exact document
it came from, years later, from a checkout, with no network.

## The two questions a lawyer has to answer

Recorded precisely rather than resolved, because neither is an engineering decision.

**1. Non-commercial.** Impilo Technologies is a State Owned Enterprise operating national health
infrastructure, which reads as non-commercial public-health use. But this platform also contains a
marketplace, coverage and claims adjudication, and payment rails. Whether the NC clause is satisfied
across *all* of that, or only the clinical plane, is a question about the platform's commercial
posture and not about the clinical content.

**2. ShareAlike, which is the sharper one.** A transcription of WHO's medical eligibility categories
into JSON is plausibly a derivative work. If it is, SA would require it to carry the same licence —
which is in direct tension with a proprietary repository. This notice puts the derived content under
its own terms rather than inheriting proprietary-by-default, which is the honest position while the
question is open, but it does not answer it.

## What engineering does in the meantime

Three design decisions taken to keep exposure minimal and reversible, all of which are also better
engineering:

- **The engine is separate from the content.** `MedicalEligibilityEngine` contains no WHO material —
  it is arithmetic over a matrix supplied to it. If the content ever has to be withdrawn, relicensed
  or replaced with a ministry-authored equivalent, no code changes.
- **Facts are transcribed; prose is cited, not copied.** A category for a (condition, method) pair
  is a fact and is recorded as one. WHO's explanatory clarifications are referenced by edition,
  section and page rather than reproduced, except where a short verbatim quotation is clinically
  load-bearing — MEC category 3 means "use is not usually recommended unless other more appropriate
  methods are not available or acceptable", and paraphrasing that particular sentence would change
  what a clinician does.
- **Every shipped rule already carries its source.** The `dakRef` and `adaptation` blocks name the
  publication, edition and row, so attribution is structural rather than a footnote someone has to
  remember to add.

## Attribution

World Health Organization. Reproduced and adapted for implementation in the Zimbabwe national
health system. WHO is not responsible for any adaptation made here, and a national adaptation
recorded in an `adaptation` block is Impilo's statement, not WHO's — which is precisely why every
one of them names its own approving authority and carries `PENDING_MOHCC_RATIFICATION` until a
ministry signs it off.

## Status

**Unreviewed by counsel.** This notice records the position and the open questions; it is not legal
advice and does not clear the content for release. The blocking question — whether derived MEC
categories may ship inside this repository — is escalated rather than assumed, and the medical
eligibility content is scoped and paced on the assumption that the answer may be no.
