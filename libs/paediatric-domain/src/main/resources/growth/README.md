# Growth reference tables

These are the only growth reference tables in the repository. The experience BFF used to
carry a second copy of the WHO file alongside a second implementation of the LMS
arithmetic; both are gone, because two copies of a clinical reference is two answers to the
same question and the two had already diverged.

## `who_under5_lms.json` — WHO Child Growth Standards (2006)

Birth to five years (0–1856 days), indexed by age in days, both sexes, four indicators:

- weight-for-age
- length/height-for-age
- BMI-for-age
- head circumference-for-age

Built from the official WHO expanded-table XLSX downloads:

- <https://www.who.int/tools/child-growth-standards/standards/weight-for-age>
- <https://www.who.int/tools/child-growth-standards/standards/length-height-for-age>
- <https://www.who.int/tools/child-growth-standards/standards/body-mass-index-for-age-bmi-for-age>
- <https://www.who.int/tools/child-growth-standards/standards/head-circumference-for-age>

**Known absences, reported rather than substituted.** Weight-for-length/height — the
indicator that defines wasting — is not present, so wasting is assessed from MUAC and
bilateral pitting oedema and the classifier says so on every assessment. The WHO 5–19 year
reference is not present either, so children over five are not scored at all rather than
being read off an extrapolated under-five curve.

## `fenton_2013_preterm_lms.json` — Fenton 2013 Preterm Growth Chart

Completed postmenstrual weeks 22–49, both sexes, three indicators (weight in grams, length
and head circumference in centimetres). Used for infants born before 37 weeks until they
pass the chart's published end, after which they are followed on WHO at corrected age.

Transcribed verbatim from the authors' published bulk calculator,
`Bulk calculator wt hc l - Fenton 2013 growth chart - SD23 - v6`, at
<https://ucalgary.ca/fenton>. The values were independently re-extracted from that
spreadsheet and corroborated against the authors' separate exact-age calculator (v7), which
agrees to within 1.4e-4 relative.

**Do not merge this with the size-at-birth calculator published on the same site.** That
workbook is a deliberately different fit for assigning size at birth (SGA/AGA/LGA) and its
values disagree with these at every overlapping week. Birth classification uses that table;
postnatal growth monitoring uses this one.

**Known absences.** There is no completed-week-50 row: the weekly points sit at the midpoint
of each week, so week 50 would fall past the chart's 50w0d endpoint. Length and head
circumference have no 22-week values — the source states outright that the data does not
exist. Both are absent here rather than filled in, and the engine reports them as named
gaps.

**Licence — CC BY-NC-ND 4.0, with obligations that reach the screen.** Any chart drawn from
this data must display the label "Fenton 2013 Preterm Growth Chart" conspicuously, and the
development paper must be cited: Fenton TR, Kim JH. *A systematic review and meta-analysis
to revise the Fenton growth chart for preterm infants.* BMC Pediatr. 2013;13:59. Both
obligations are carried in the content pack's own metadata and travel through the API to the
UI, so a surface cannot render the chart without them.

Two determinations remain open and belong to MoHCC, not to engineering: whether a national
public health service deployment satisfies the NonCommercial term, and whether embedding the
published weekly values verbatim for lookup is use rather than a derivative work. The
publishers direct data requests to tfenton@ucalgary.ca; a file being publicly served is not
by itself a licence to embed it. Both are recorded in
`docs/registry/iatg-paediatric-leases.md`.

## The rule both files follow

Values are stored exactly as published. No smoothing, no re-fitting, and no interpolation
between published points — an interpolated LMS value is a number the publishing authority
never issued. Where a table stops, scoring stops and says so.
