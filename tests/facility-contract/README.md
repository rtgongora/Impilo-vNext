# Facility Claim, Completion and MoHCC Verification — conformance pack

`facility-claim-verification-journeys.sh` · exit **0 GREEN** · **2 AMBER** (checks skipped,
nothing failed) · **1 RED** (an invariant is broken).

## Why this pack exists

Every failure mode in this capability is silent. A facility that legitimises itself, an import
that quietly revokes a Ministry verdict, a bulk assertion that operationalises 1,774 facilities,
an expiry date nobody reads — none of them throw, and a green build ships happily on top of any
of them. Each check below is an invariant a wave established, so "the wave is done" is verified
against the tree rather than asserted.

## A skip is never a pass

`FCV-LIVE` needs the preview estate. When it is unreachable the pack reports AMBER and says so;
it never silently counts an unrunnable check as green.

## The checks must bite

Each check is written to fail when its invariant breaks — including when a guard is *deleted but
its method left behind*. The first draft of `FCV-WRITE` grepped for the guard's name and passed
happily after the call was removed, because the method definition still matched. It now asserts
the invocation. **When adding a check, delete the thing it guards and confirm the pack goes RED**;
a check nobody has seen fail is a check nobody should trust.

## Invariants

| ID | What it holds |
|---|---|
| `FCV-SELF` | Issuing a Ministry verdict requires a verifier appointment — a facility cannot legitimise itself |
| `FCV-WRITE` | Only `HPA_LEGAL` is writable through the generic legitimacy endpoint |
| `FCV-CLOBBER` | An import skips decision-bound rows — it cannot silently revoke a verdict |
| `FCV-DENIER` | A Ministry allow may never stand without a platform verdict (DB trigger) |
| `FCV-BULK` | The recognition batch never grants platform operation |
| `FCV-BULKSOD` | The batch demands registry-admin, not verifier, authority |
| `FCV-EXPIRY` | An expired decision loses its platform allow |
| `FCV-REVERIFY` | A material change to the facility demotes it to awaiting re-verification |
| `FCV-JURIS` | Jurisdiction is matched against the facility; an absent district never matches |
| `FCV-HEADER` | Verification authority is an appointment, never a client-supplied header |
| `FCV-PROVIDERID` | A Provider ID is required only where the relationship is regulated |
| `FCV-ADMINCLAIM` | Administrative roles are **not** gated on a licence — that would be exclusion, not rigour |
| `FCV-DRAFT` | Proposals are private, not current, and carry facility provenance |
| `FCV-NOSELFSTATUS` | A facility cannot propose what is decided about it |
| `FCV-CAMPAIGN` | Campaign progress is derived from the rails, not a mirrored status |
| `FCV-TENANT` | Data-gap lookups are tenant-scoped |
| `FCV-LIVE` | The estate answers, and silence and denial both refuse |

## Not yet covered

The §23 runtime journeys (open discovery → invited claim → regulated PIC claim → review →
draft → submission → correction → resubmission → Ministry verification → verdict → revocation
→ campaign monitoring → a facility failing to self-legitimise) need the estate redeployed with
org-registry V012 and tuso V044–V050. Until then this pack proves the invariants hold in the
tree; it does not claim they have been exercised live.
