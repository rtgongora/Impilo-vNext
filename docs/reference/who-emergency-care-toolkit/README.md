# WHO Emergency Care Toolkit — Interagency Integrated Triage Tool (IITT)

Canonical in-repo copies of the triage charts the Emergency, Resuscitation and Acute Care pack
encodes. Follows the pattern set by [`docs/reference/edliz-2025/`](../edliz-2025/README.md), with one
addition: a `pdftotext -layout` companion beside each PDF, because the extracted text is what makes a
content review diffable and what the traceability matrix cites.

**The hash is the version.** None of these charts carries a version number or a publication date —
not on the charts, not in the FAQ. The IITT's own validation literature notes that criteria have been
adjusted since 2019, so "the IITT" is not a stable identifier. The SHA-256 below therefore *is* the
version identity: content that cites one of these documents cites a specific hash, and a re-retrieval
that produces a different hash is a content change requiring re-review, not a silent refresh.

## Files

| File | SHA-256 | Bytes |
|---|---|---|
| `iitt-adult-age-12-and-over.pdf` | `9597ab1f4f28687d9d2e1e28bfac6a84191889c6800198571b6af6a160e5711d` | 53,006 |
| `iitt-paediatric-age-under-12.pdf` | `c1fd4004447c6511a999a96177d887ba3ddfbe7a00e4ff38ba5794dabff515c4` | 42,514 |
| `iitt-high-risk-reference-card.pdf` | `d69bbe391901cbbe14966df93af1f9024d5b9881705589f47dcb4df02a3ca7d1` | 83,950 |

Each has a `.txt` companion extracted with `pdftotext -layout`.

## Source

- Publisher: **World Health Organization**, with the **International Committee of the Red Cross** and
  **Médecins Sans Frontières**, as printed on each chart.
- Landing page: <https://www.who.int/tools/triage>
- Retrieved: **2026-07-26**, from `cdn.who.int`:
  - adult — `…/csy/iitt/iitt_adult.pdf?sfvrsn=b2a91431_1`
  - paediatric — `…/csy/iitt/iitt_pediatric.pdf?sfvrsn=15161bfb_1`
  - reference card — `…/csy/iitt/iitt_reference-card09165e1c-0df5-472d-ab53-45506191a7bd.pdf?sfvrsn=eba17410_1`
- Licence, as printed on the reference card: **CC BY-NC-SA 3.0 IGO**.
- Internal PDF metadata on all three: `60413_OMS-IHS-Poster-Triage-Tool-01-A3-20200326-v6`, which
  suggests a 2020-03-26 artwork revision. That is an *artwork* identifier, not a published version,
  and it is recorded here as an observation rather than relied on.

## Why these are vendored rather than linked

A rule this pack encodes must be reviewable against the exact text it came from, years later, by
someone who cannot assume a CDN URL still resolves to the same bytes. A live link is not a citation.

## Verification note — and why it was not optional

The criteria were first transcribed by an exploratory agent. Before any of it was encoded, the
transcription was checked line by line against these extracted texts. It held — **including on the
detail most likely to be got wrong.**

The adult chart carries **two different heart-rate bands**, and they mean different things:

- **RED, under CIRCULATION:** `HR <50 or >150` — a red criterion in its own right.
- **Step 3, HIGH-RISK VITAL SIGNS:** `HR <60 or >130` — triggers up-triage or immediate review by a
  supervising clinician.

A web search performed during the same check returned `<60 or >130` **as the RED criterion**. Encoding
that would have moved the immediate-resuscitation threshold to a materially less abnormal heart rate
and quietly changed who goes to the resuscitation area first. The two bands must never be merged, and
`IittTriageContentTest` asserts they remain distinct rules for exactly this reason.

The paediatric chart drops the adult numeric HR band from RED entirely and instead carries age-banded
high-risk vitals (RR high 50/40/30 and low 25/20/10; HR high 180/160/140 and low <90/<80/<70, for
<1 year / 1–4 years / 5–12 years). Its altered-mental-status red criterion is a **conjunction** —
altered mental status **with** stiff neck, hypothermia or fever — where the adult chart uses **any two
of** four findings. Those are different logical shapes and the content encodes them differently.

## What is still UNVERIFIED

- **Whether Zimbabwe has adopted the IITT at all**, and with what national time targets, zone map and
  destination policy. The IITT FAQ is explicit that time-to-care per colour "is for facilities to
  determine". **Every Zimbabwe time target in this pack is therefore an `ENGINEERING_SEED`** pending
  MoHCC ratification.
- The remaining Emergency Care Toolkit components — Medical Emergency Checklist, Clinical Registry,
  Acute Transfer Checklist, Acute Referral Form, mass-casualty IITT — are not yet vendored here.
- No Zimbabwe national emergency-care, referral, ambulance/EMS, resuscitation, blood-transfusion or
  disaster/MCI policy could be located. That absence is itself a finding and is recorded in
  `docs/clinical-governance/emergency/standards-baseline.json` rather than left as a silence.

## Re-verifying

```bash
cd docs/reference/who-emergency-care-toolkit && sha256sum -c SHA256SUMS
```
