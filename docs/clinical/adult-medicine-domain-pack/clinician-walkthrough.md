# Adult Medicine — §23 clinician walkthrough checklist

**Layer:** clinician (product UI). Complements the record and service proofs.

| Layer | Script / artefact | Status |
|---|---|---|
| Record | `scripts/runtime-proof/medicine-demonstrations.sh` | Proven |
| Service | `scripts/runtime-proof/medicine-demonstrations-service.sh` | Proven |
| Clinician | this checklist against `https://impilo.mohcc.gov.zw` (or local one-ui-shell) | **Manual — not automated** |

This is not a claim that the ten journeys pass end to end in the product. It is the honest
protocol for proving (or failing) them with a clinician in front of the screen.

## Preconditions

- Preview (or local) one-ui-shell at current branch HEAD
- A test patient with CPID / Health ID (no real patient data)
- An open journey or encounter id for MDT / consultation anchors (care continuum)
- Provider actor with EMERGENCY or TREATMENT purpose as required by the surface

## The ten

For each journey: perform the steps, record **PASS / FAIL / BLOCKED**, and name the first blocker
clinically (not as an HTTP status).

### 1. Newly detected HTN + DM → integrated care plan
1. Open problem list; add hypertension and diabetes (or confirm both present).
2. Enrol both on chronic registers.
3. Open / confirm a MULTIMORBIDITY episode spanning both.
4. **Expose:** two parallel plans / schedules.

### 2. HIV + TB + diabetes without disconnected records
1. Confirm all three on one person.
2. Open HIV register attempt — expect honest refusal / confidential lane statement, not a silent empty list.
3. **Expose:** duplicated HIV/TB records or fake confidentiality.

### 3. Heart failure across settings
1. Open cardiology specialty → spine links (worklist / examination / volume).
2. Attempt to find titration history across settings.
3. **Expect BLOCKED** on titration workspace until built — do not invent one.

### 4. CKD → dialysis prep
1. Nephrology specialty → CKD register + HD/PD/AVF catalogue links.
2. Confirm dialysis *adequacy / modality review* remains on notBuilt.
3. **Expose:** private nephrology workflow bypassing procedures pipeline.

### 5. Stroke → exam + rehab / secondary prevention
1. Record neurological examination (with graphics if applicable).
2. Create / open care plan with rehab + secondary-prevention goals.
3. **Expose:** plan never created or never followed up.

### 6. Decompensated liver → result actioned
1. Record abdomen exam findings.
2. Confirm result-action path is reachable for an investigation result (OROS acknowledgement + V115).
3. **Expose:** procedure result never returns to the problem list.

### 7. Suspected cancer → MDT
1. Record suspected malignancy; promote certainty when confirmed.
2. On Consultations and MDT: **Record an MDT decision** (chair, participants, decision, journey/encounter anchor).
3. Confirm treatment intent null is stated, not blank.
4. **Expect BLOCKED** on staging / cycles / CTCAE — not inventable.

### 8. Older person / multimorbidity
1. Open multimorbidity view; confirm priorities panel stays unknown without a SoR.
2. Functional status: record a Barthel/Katz/Lawton assessment (score XOR absent reason).
3. **Expose:** ICOPE as a sixth parallel assessment.

### 9. Pregnancy coordination
1. Confirm pregnancy-aware CDS fires only when pregnancy status is supplied.
2. Confirm medical view does not silently invent pregnancy from V111.
3. **Expose:** teratogen continued because pregnancy was invisible.

### 10. Surgical consult without loss of ownership
1. Ask SURGERY a question; confirm owning-service note stays medicine.
2. Answer with takeover recommended — confirm ownership unchanged.
3. Request transfer; confirm still medicine; accept with `accepting_ref`; confirm ownership moves only then.
4. **Expose:** silent handover or duplicated clerking.

## Reporting

Copy results into `reports/journeys/medicine-clinician-walkthrough-<date>/summary.txt`:

```
journey=N result=PASS|FAIL|BLOCKED blocker=<clinical sentence>
…
clinician_walkthrough=INCOMPLETE|COMPLETE
```

Do not mark pack §25 done until this walkthrough is COMPLETE with only intentional BLOCKED rows
matching the stated CANNOT register.
