# Adult Medicine — the specialty map (§8, §24)

**Source: [`brief.md`](brief.md) §8.** This is one of the seventeen outputs §24 asks for.

## What a specialty is, here

A specialty is **a view onto the shared record**, not a record of its own. It declares which
problems it cares about, which governed CDS packs apply, which chronic registers it runs, and which
of §7's twenty-two examination regions its assessment centres on. It owns none of them.

That is the whole architectural bet, and it is the one the brief demands in its opening line:
*"This must not become a collection of disconnected disease registers or one generic medical
clerking form."* Thirteen hand-built screens would each invent their own problem list, their own
examination and their own decision support — the folder of specialist forms. Thirteen configurations
over one spine cannot.

Implementation: `ui/one-ui-shell/src/features/medicine/specialties/specialty-config.ts`, surfaced at
`/ehr/[patientId]/medicine/specialty/[specialty]`.

## The thirteen

| § | Specialty | Governed CDS | Registers | Examination focus |
|---|---|---|---|---|
| 8.1 | Cardiology | CV risk, deprescribing | Hypertension | Cardiovascular, vitals, oedema, peripheral vascular |
| 8.2 | Respiratory and pulmonology | Procedure indication, AMS | Asthma/COPD | Respiratory, vitals, cyanosis |
| 8.3 | Gastroenterology and hepatology | Procedure indication, oncology | — | Abdomen, jaundice, hydration, anthropometry |
| 8.4 | Nephrology | Deprescribing, procedure indication | CKD, hypertension | Oedema, hydration, vitals, pallor |
| 8.5 | Endocrinology and metabolic medicine | CV risk, deprescribing | Diabetes | Feet, eyes, endocrine, anthropometry |
| 8.6 | Neurology | Procedure indication, mhGAP | — | Neurology, cognition, functional status |
| 8.7 | Infectious diseases | Antimicrobial stewardship | — | General, vitals, lymph nodes, skin, abdomen |
| 8.8 | Rheumatology and clinical immunology | Deprescribing, AMS | — | Musculoskeletal, skin, functional status |
| 8.9 | Haematology | Procedure indication, oncology | — | Pallor, lymph nodes, abdomen, skin |
| 8.10 | Medical oncology | Oncology, palliative | — | General, lymph nodes, anthropometry, function |
| 8.11 | Dermatology | Oncology, procedure indication | — | Skin, feet |
| 8.12 | Geriatric medicine | ICOPE, deprescribing, palliative | — | Cognition, frailty, function, anthropometry, eyes |
| 8.13 | Palliative medicine | Palliative, deprescribing | — | General, function, hydration, skin |

The HIV and TB Digital Adaptation Kits sit under §8.7 and are the deepest content in the pack —
thirteen governed standards, eight rule packs. §8.7's instruction *"avoid separate duplicated HIV and
TB records; use one person record with appropriate programme views and confidentiality"* is honoured
structurally: HIV and TB are programme enrolments anchored to the one problem list, not parallel
records.

## What is not built, and why that is on screen

§8 pairs each specialty's disease list with a tooling list — ECG workflow, spirometry, dialysis
access planning, EEG, staging, cycle planning. **Most of that tooling does not exist.** Every
specialty workspace therefore renders a **"Not built here"** section naming exactly what §8 asked
for that this pack has not built.

This is deliberate and it is enforced by test: a specialty whose `notBuilt` list is empty fails the
build, because an empty list is a claim to have built everything §8 named. An earlier wave in this
estate had to strip out order-set buttons that had no click handler anywhere on the page; a tile that
looks available and does nothing is worse than an absence, because a clinician plans around an
absence and trusts a tile.

The largest gaps, by specialty:

- **Cardiology** — ECG, echocardiography, ambulatory monitoring, volume-status tracking, titration.
- **Respiratory** — spirometry, peak flow, inhaler assessment, symptom-control scores, oxygen eligibility.
- **Nephrology** — dialysis preparation, vascular access, transplant referral. Also **eGFR is consumed
  but never computed** — it arrives as a supplied fact, so a CKD stage can be recorded that no
  measurement supports.
- **Endocrinology** — glucose/device data, HbA1c trend, complication-screening schedule, insulin titration.
- **Oncology** — staging, MDT (§14 is not built at all), systemic therapy, cycle planning, toxicity,
  survivorship.
- **Hepatology** — Child-Pugh/MELD staging. No governed hepatic instrument exists, so
  `hepaticImpairment` is null for every patient in production.
- **Palliative and geriatrics** — symptom-score instruments, advance-care planning record, carer
  support, long-term care.

## Relationship to the other specialty route

`/ehr/[patientId]/workspace/[specialty]` is a **different thing owned by another lane**: a
cross-discipline template covering surgery, obstetrics, paediatrics, emergency and orthopaedics.
Only *cardiology* appears in both. Converging the two is a follow-up; naming the overlap here is
better than a silent fork nobody notices.
