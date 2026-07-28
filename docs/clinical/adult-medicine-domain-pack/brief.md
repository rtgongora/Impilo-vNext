# Adult Medicine and Medical Specialties Clinical Domain Pack — the brief

> **This is the pack's source of truth.** Everything below the rule is the product owner's brief,
> reproduced verbatim and unedited. Where this file and any other document in the pack disagree,
> this file wins.
>
> **Why it is only being committed now.** The brief was never committed when the pack was started.
> For the first several waves the pack was built against an engineer's paraphrase of it, which is
> how the completion report came to cite "§9", "§19" and "§23" that no reader could check, and how
> two wave labels drifted from what they actually delivered. Three sections — the multimorbidity
> engine (§9), the FHIR resource list (§19) and the ten required demonstrations (§23) — were
> effectively unrecoverable until the product owner supplied the brief again on 2026-07-28.
>
> **Provenance.** Supplied by the product owner on 2026-07-28 and recovered verbatim (21,665
> characters, 25 sections) from the session transcript
> `~/.claude/projects/-opt-impilo-repos-Impilo-vNext--claude-worktrees-gifted-rubin-406cae/a685c2af-4275-4ddb-8252-cc34e0c6178f.jsonl`,
> line 3580, `attachment.prompt`. Only the pasted lead-in `"Here it is: "` was removed. No section
> was summarised, reordered or corrected — including passages the delivered implementation does not
> yet satisfy.
>
> **How to cite it.** Reference sections by number (`brief.md §9`) rather than restating them.
> A paraphrase in a report is not checkable; a section number is.

---

You are working in the Impilo vNext repository.
Your task is to audit, design and implement a complete, production-grade Adult Medicine and Medical Specialties Clinical Domain Pack.
This pack must support the complete medical journey from prevention, screening and first presentation through diagnostic reasoning, specialty assessment, acute and inpatient care, chronic-disease management, multimorbidity, rehabilitation, palliative care and long-term follow-up.
It must integrate deeply with:

* Integrated Emergency Care.
* Paediatrics.
* Reproductive and Maternity care.
* Surgery.
* Critical Care.
* Mental Health.
* Rehabilitation.
* Pharmacy and medicines.
* Laboratory and imaging.
* The Procedures Pipeline.
* Community and virtual care.

This must not become a collection of disconnected disease registers or one generic “medical clerking” form.
1. Standards baseline
Inspect and apply:

* WHO Package of Essential Noncommunicable Disease Interventions for Primary Health Care.
* WHO HEARTS and cardiovascular-risk management resources.
* WHO HIV Digital Adaptation Kit, second edition.
* WHO Tuberculosis Digital Adaptation Kit.
* WHO integrated HIV service-delivery guidance.
* WHO Integrated Care for Older People.
* WHO mental, neurological and substance-use guidance where medicine interfaces with those domains.
* WHO antimicrobial stewardship, essential medicines, palliative-care and patient-safety guidance.
* WHO guidance for diabetes, hypertension, cardiovascular disease, stroke, chronic respiratory disease, cancer, kidney disease, neurological illness and other medical conditions.
* Authorised Zimbabwe national guidelines, including EDLIZ and applicable HIV, TB, malaria, NCD, cancer, mental-health and palliative-care policies.
* Appropriate current international specialty standards where WHO guidance does not provide sufficient specialty depth.

Deeply implement existing WHO DAKs for HIV and TB.
For other medical specialties, use the same DAK decomposition:

1. Recommendations.
2. Personas.
3. User scenarios.
4. Business workflows.
5. Core data elements.
6. Decision support.
7. Indicators.
8. Functional and non-functional requirements.

Maintain explicit source, national adaptation, version and approval traceability.
2. Domain boundary
Medicine owns definitive adult medical care.
Emergency Care owns:

* Initial acuity.
* Stabilisation.
* Emergency diagnostics.
* Immediate treatment.
* Emergency disposition.

Medicine owns:

* Specialist medical assessment.
* Diagnostic refinement.
* Admission.
* Inpatient management.
* Longitudinal care.
* Multimorbidity.
* Specialty follow-up.
* Disease control.
* Rehabilitation.
* Palliative and end-of-life medical care.

Surgery owns surgical disease and operative decisions.
The Procedures Pipeline owns safe execution of procedures.
Paediatrics owns children routed by the configured age and transition rules.
The Reproductive Pack remains active whenever pregnancy, postpartum state or reproductive care materially changes management.
3. Principal care settings
Support:

* Primary care.
* General medical clinic.
* Specialist outpatient clinic.
* Emergency consultation.
* Inpatient ward.
* High-dependency care.
* Day-treatment unit.
* Infusion unit.
* Dialysis unit.
* Anticoagulation clinic.
* Diagnostic unit.
* Rehabilitation.
* Palliative care.
* Home care.
* Community care.
* Telemedicine.
* Provider-to-provider consultation.
* Multidisciplinary team.
* Outreach.
* Mobile services.

The same clinical record must continue across settings.
4. Canonical medical episode and problem model
Implement:

* Medical episode.
* Active problem.
* Historical problem.
* Suspected diagnosis.
* Differential diagnosis.
* Confirmed diagnosis.
* Resolved condition.
* Complication.
* Comorbidity.
* Risk factor.
* Functional consequence.
* Treatment goal.

Do not collapse:

* Symptom.
* Syndrome.
* Guideline classification.
* Working diagnosis.
* Confirmed diagnosis.

Create a longitudinal problem list that supports:

* Onset.
* Status.
* Severity.
* Evidence.
* Certainty.
* Responsible service.
* Current plan.
* Related medicines.
* Related investigations.
* Complications.
* Goals.
* Review date.
* Resolution.
* Recurrence.

Prevent duplicate diagnoses created independently by multiple clinics.
5. General adult medical journey
Implement:

1. Entry and reason for care.
2. Acuity check.
3. Presenting concern.
4. Structured history.
5. Medication reconciliation.
6. Allergy and adverse-reaction review.
7. Relevant examination.
8. Diagnostic reasoning.
9. Investigations.
10. Problem list.
11. Risk stratification.
12. Treatment.
13. Procedures.
14. Education and self-management.
15. Referral or consultation.
16. Follow-up.
17. Outcomes.

The workflow must adapt to:

* New undifferentiated presentation.
* Known chronic disease.
* Acute exacerbation.
* Post-discharge review.
* Abnormal result.
* Medication review.
* Multimorbidity.
* Frailty.
* Palliative needs.
* Procedure follow-up.

6. Full medical clerking
Capture structured data for:

* Presenting concern.
* Symptom timeline.
* Previous episodes.
* Relevant systems review.
* Past medical history.
* Previous admissions.
* Previous intensive care.
* Surgical history.
* Reproductive and pregnancy context.
* Medicines.
* Adherence.
* Allergies.
* Adverse reactions.
* Immunisation.
* Infection exposure.
* Occupational history.
* Environmental exposure.
* Tobacco.
* Alcohol.
* Substance use.
* Diet.
* Physical activity.
* Sleep.
* Family history.
* Genetic risk.
* Social support.
* Housing.
* Food security.
* Function.
* Cognition.
* Mood.
* Safeguarding.
* Goals and preferences.

Use conditional structured forms.
Do not require the clinician to re-document unchanged history at every visit.
7. Examination framework
Implement reusable structured examinations for:

* General condition.
* Vital signs.
* Anthropometry.
* Hydration.
* Pallor.
* Jaundice.
* Cyanosis.
* Oedema.
* Lymph nodes.
* Cardiovascular system.
* Respiratory system.
* Abdomen.
* Neurology.
* Musculoskeletal system.
* Skin.
* Endocrine features.
* Peripheral vascular system.
* Feet.
* Eyes.
* Functional status.
* Cognition.
* Frailty.

Support:

* Normal.
* Abnormal.
* Not examined.
* Unable to examine.
* Deferred.
* Uncertain.

Implement graphics for:

* Chest auscultation.
* Cardiac findings.
* Abdominal regions.
* Neurological deficits.
* Dermatomes.
* Peripheral pulses.
* Oedema.
* Joints.
* Skin lesions.
* Diabetic foot.
* Pressure injuries.

8. Specialty workspaces
8.1 Cardiology
Support:

* Hypertension.
* Cardiovascular-risk assessment.
* Ischaemic heart disease.
* Acute coronary syndrome handoff from Emergency.
* Heart failure.
* Valvular disease.
* Cardiomyopathy.
* Arrhythmia.
* Syncope.
* Pericardial disease.
* Congenital heart disease in adult care.
* Anticoagulation.
* Cardiac rehabilitation.
* Device follow-up.
* Pregnancy-related cardiac coordination.

Include:

* ECG workflow.
* Echocardiography.
* Ambulatory monitoring.
* Risk scores.
* Functional classification.
* Volume-status tracking.
* Medicine titration.
* Device and procedure referrals.
* Longitudinal outcomes.

8.2 Respiratory and pulmonology
Support:

* Asthma.
* COPD.
* Bronchiectasis.
* Pneumonia follow-up.
* Tuberculosis integration.
* Interstitial lung disease.
* Pleural disease.
* Pulmonary hypertension.
* Sleep-related breathing disorders.
* Occupational lung disease.
* Post-infectious lung disease.
* Oxygen therapy.
* Pulmonary rehabilitation.

Include:

* Spirometry.
* Peak flow.
* Inhaler assessment.
* Symptom-control scores.
* Exacerbation history.
* Tobacco cessation.
* Oxygen eligibility.
* Respiratory procedures.

8.3 Gastroenterology and hepatology
Support:

* Dyspepsia and ulcer disease.
* Gastrointestinal bleeding follow-up.
* Chronic diarrhoea.
* Malabsorption.
* Inflammatory bowel disease.
* Chronic liver disease.
* Viral hepatitis.
* Cirrhosis.
* Ascites.
* Hepatic encephalopathy.
* Pancreatic disease.
* Biliary disease.
* Nutrition.
* Cancer referral.
* Endoscopy pathways.

8.4 Nephrology
Support:

* Acute kidney injury follow-up.
* Chronic kidney disease.
* Proteinuria.
* Haematuria.
* Electrolyte disorders.
* Hypertension.
* Glomerular disease.
* Nephrotic syndrome.
* Dialysis preparation.
* Haemodialysis.
* Peritoneal dialysis.
* Vascular access.
* Transplant referral and follow-up where applicable.
* Renal anaemia.
* Mineral and bone disorder.
* Fluid management.

8.5 Endocrinology and metabolic medicine
Support:

* Type 1 diabetes.
* Type 2 diabetes.
* Secondary diabetes.
* Thyroid disease.
* Adrenal disease.
* Pituitary disease.
* Calcium and bone metabolism.
* Osteoporosis.
* Obesity.
* Dyslipidaemia.
* Metabolic syndrome.
* Endocrine hypertension.
* Reproductive-endocrine interfaces.

Include:

* Glucose monitoring.
* HbA1c.
* Hypoglycaemia.
* Complication screening.
* Eye, renal, foot and cardiovascular checks.
* Insulin and medicine titration.
* Device data.
* Nutrition and lifestyle support.

8.6 Neurology
Support:

* Epilepsy.
* Stroke follow-up and prevention.
* Headache.
* Neuropathy.
* Movement disorders.
* Dementia.
* Cognitive impairment.
* Multiple sclerosis and demyelinating disease where applicable.
* Neuromuscular disease.
* Spinal disease.
* CNS infection follow-up.
* Neurodisability.
* Rehabilitation.

Include:

* Structured neurological examination.
* Seizure classification.
* Functional scores.
* Cognitive assessments.
* Imaging.
* EEG.
* Lumbar puncture.
* Rehabilitation goals.
* Driving and occupational advice according to policy.

8.7 Infectious diseases
Deeply apply the HIV and TB DAKs.
Also support:

* Malaria.
* Sepsis follow-up.
* Chronic or recurrent infection.
* Opportunistic infections.
* Viral hepatitis.
* Sexually transmitted infections.
* Antimicrobial-resistant infection.
* Infection in immunocompromised patients.
* Imported and travel-related infection.
* Outbreak-linked care.
* Long-term antimicrobial therapy.

Avoid separate duplicated HIV and TB records.
Use one person record with appropriate programme views and confidentiality.
8.8 Rheumatology and clinical immunology
Support:

* Inflammatory arthritis.
* Connective-tissue disease.
* Vasculitis.
* Gout.
* Autoimmune disease.
* Immunosuppression.
* Biologic or disease-modifying therapy.
* Infection screening.
* Disease-activity scores.
* Functional status.
* Pregnancy coordination.
* Infusion monitoring.

8.9 Haematology
Support:

* Anaemia.
* Haemoglobinopathies.
* Sickle-cell disease.
* Bleeding disorders.
* Thrombotic disease.
* Cytopenias.
* Haematological malignancy interface.
* Transfusion planning.
* Anticoagulation.
* Bone-marrow and other diagnostic procedures.
* Longitudinal laboratory trends.

8.10 Medical oncology
Support:

* Suspicion and referral.
* Diagnostic confirmation.
* Staging.
* MDT.
* Treatment intent.
* Systemic anticancer therapy.
* Cycle planning.
* Regimen verification.
* Toxicity.
* Response.
* Survivorship.
* Recurrence.
* Palliative care.
* Financial and access navigation.

Do not duplicate surgical oncology or radiotherapy workflows.
Create coordinated plans across specialties.
8.11 Dermatology
Support:

* Structured morphology.
* Body distribution.
* Photography with consent.
* Infectious dermatoses.
* Inflammatory disease.
* Drug reactions.
* Autoimmune skin disease.
* Ulcers.
* Skin cancer referral.
* Procedure and biopsy pathways.

8.12 Geriatric medicine
Implement WHO ICOPE principles.
Support:

* Intrinsic capacity.
* Cognition.
* Mobility.
* Nutrition.
* Vision.
* Hearing.
* Psychological wellbeing.
* Continence.
* Frailty.
* Falls.
* Polypharmacy.
* Social support.
* Carer needs.
* Advance-care planning.
* Long-term care.

8.13 Palliative medicine
Support:

* Palliative-needs identification.
* Symptom assessment.
* Pain.
* Breathlessness.
* Nausea.
* Delirium.
* Anxiety.
* Spiritual and social concerns.
* Function.
* Care preferences.
* Family and caregiver support.
* Controlled medicines.
* Home care.
* End-of-life care.
* Bereavement.
* Advance-care planning according to law and policy.

9. Multimorbidity engine
Do not force clinicians to manage each disease in isolation.
Create a multimorbidity view showing:

* Active conditions.
* Shared risk factors.
* Conflicting recommendations.
* Medicine burden.
* Interaction risks.
* Renal and hepatic constraints.
* Functional impact.
* Appointment burden.
* Patient priorities.
* Care-team responsibilities.
* Consolidated monitoring plan.

The system must detect:

* Duplicate medicines.
* Contradictory targets.
* Unsafe combinations.
* Excessive visit schedules.
* Repeated investigations.
* Competing dietary advice.
* High treatment burden.

Allow a person-centred prioritised care plan.
10. Medicines and clinical pharmacology
Implement comprehensive medication reconciliation:

* Current medicines.
* Source.
* Dose.
* Route.
* Frequency.
* Indication.
* Start date.
* Intended duration.
* Adherence.
* Last dose.
* Patient-reported use.
* Traditional and complementary remedies.
* Over-the-counter medicines.
* Adverse reactions.
* Allergies.

Support:

* Renal dosing.
* Hepatic dosing.
* Pregnancy and breastfeeding safety.
* Interaction checking.
* Duplication.
* Therapeutic monitoring.
* Controlled medicines.
* Antimicrobial stewardship.
* Deprescribing.
* Formulary.
* Dura stock.
* Refill.
* Dispensing.
* Medicine possession and adherence.

11. Diagnostic orchestration
Support:

* Indication-based order sets.
* Appropriate-use checks.
* Duplicate-test detection.
* Serial trends.
* Critical results.
* Incidental findings.
* Result acknowledgement.
* Action tracking.
* Diagnostic uncertainty.
* Referral for unavailable tests.

Include:

* Laboratory.
* Pathology.
* Radiology.
* Ultrasound.
* CT.
* MRI.
* ECG.
* Echocardiography.
* Spirometry.
* Endoscopy.
* Neurophysiology.
* Ambulatory monitoring.
* Point-of-care tests.

12. Procedures
Invoke the common Procedures Pipeline for:

* Lumbar puncture.
* Pleural aspiration.
* Chest drainage.
* Paracentesis.
* Liver biopsy.
* Bone-marrow procedures.
* Central venous access.
* Dialysis access.
* Haemodialysis.
* Peritoneal-dialysis procedures.
* Endoscopy.
* Bronchoscopy.
* Cardioversion.
* Cardiac procedures.
* Joint aspiration.
* Skin biopsy.
* Infusion and day-treatment procedures.
* Other specialty interventions.

Medicine owns:

* Indication.
* Appropriateness.
* Specialty plan.
* Interpretation.
* Long-term follow-up.

The Procedures Pipeline owns execution safety.
13. Inpatient medicine
Create a medical ward workspace showing:

* Active problems.
* Severity.
* Allergies.
* Current medicines.
* Oxygen.
* Devices.
* Fluid balance.
* Nutrition.
* Mobility.
* VTE risk.
* Infection status.
* Pending results.
* Required observations.
* Consultations.
* Goals for the day.
* Discharge barriers.
* Escalation plan.
* Ceiling-of-care decisions where lawfully documented.

Create structured:

* Admission.
* Post-take review.
* Daily ward round.
* Consultant review.
* Handover.
* Deterioration.
* Transfer.
* Discharge.

14. Consultation and MDT
Support:

* Advice-only consultation.
* Shared-care consultation.
* Transfer of care.
* Remote specialist review.
* MDT.
* Case conference.
* Tumour board.
* Renal meeting.
* Cardiac meeting.
* Complex-care meeting.
* Palliative conference.

Track:

* Question asked.
* Records reviewed.
* Recommendation.
* Responsible service.
* Accepted actions.
* Rejected actions and rationale.
* Follow-up.

15. Longitudinal monitoring and home care
Support:

* Blood-pressure monitoring.
* Glucose monitoring.
* Weight.
* Peak flow.
* Oxygen saturation.
* Symptoms.
* Medication adherence.
* Fluid status.
* Dialysis data.
* Patient-reported outcomes.
* Functional status.

For each remote measurement capture:

* Device.
* Validation status.
* Timestamp.
* User.
* Context.
* Data quality.
* Threshold.
* Escalation.
* Review.

Remote monitoring must not create false reassurance when data are absent, stale or unreliable.
16. Clinical decision support
Implement transparent CDS for:

* Screening.
* Diagnosis.
* Risk.
* Treatment.
* Monitoring.
* Referral.
* Prevention.
* Complications.
* Multimorbidity.
* Deprescribing.
* Palliative needs.

Each rule must include:

* Source.
* Version.
* National adaptation.
* Inputs.
* Exclusions.
* Output.
* Explanation.
* Override.
* Test cases.

Do not autonomously prescribe or diagnose.
17. African and Zimbabwean context
Account for:

* HIV and TB.
* Malaria.
* Sickle-cell disease.
* Rheumatic heart disease.
* Hypertension.
* Diabetes.
* Chronic kidney disease.
* Chronic respiratory disease.
* Late cancer presentation.
* Infection-related chronic disease.
* Food insecurity.
* Medicine availability.
* Long referral distances.
* Limited diagnostics.
* Specialist scarcity.
* Traditional medicines.
* Occupational exposure.
* Mining and agricultural risks.
* Intermittent connectivity.
* Cross-border care.
* Public, mission and private sectors.

Use TUSO capability and Dura availability rather than assuming universal access.
18. Integration
Use:

* VITO for identity and household relationships.
* TSHEPO for context, consent and sensitive information.
* BUTANO for longitudinal medical records.
* PCT for encounters, wards and telemedicine.
* OROS for diagnostics.
* ZIBO for terminology, DAKs and rules.
* Dura for medicines.
* Madi for blood.
* TUSO for capability.
* VARAPI and Vashandi for specialists, competence and teams.
* Khuluma for follow-up.
* Nompilo for person guidance.
* Ndila and Nhume for referral.
* Rito for quality and safety.
* Ruvimbo and COSTA for coverage and billing.
* Fundo for education.
* Simba for wellness and self-management where appropriate.

19. Data model and interoperability
Map to appropriate FHIR resources including:

* Patient.
* Encounter.
* EpisodeOfCare.
* Observation.
* Condition.
* ClinicalImpression.
* RiskAssessment.
* CarePlan.
* Goal.
* ServiceRequest.
* DiagnosticReport.
* ImagingStudy.
* MedicationRequest.
* MedicationAdministration.
* MedicationStatement.
* Procedure.
* Device.
* Questionnaire.
* QuestionnaireResponse.
* Task.
* Appointment.
* Consent.
* Flag.
* DetectedIssue.
* GuidanceResponse.
* Provenance.
* AuditEvent.

Do not use one untyped medical-record blob.
20. Offline operation
Support offline:

* Clerking.
* Problem lists.
* Medicine reconciliation.
* CDS.
* Orders queued for submission.
* Chronic-care follow-up.
* Inpatient rounds.
* Procedure requests.
* Referral.

Show stale:

* Laboratory availability.
* Stock.
* Specialist availability.
* Facility capability.
* Guidelines.
* Remote measurements.

21. Analytics
Implement:

* Disease detection.
* Control and target attainment.
* Complications.
* Hospitalisation.
* Readmission.
* Mortality.
* Follow-up completion.
* Missed appointments.
* Medicine adherence.
* Polypharmacy.
* Antimicrobial use.
* Diagnostic delays.
* Referral delays.
* Procedure completion.
* Functional outcomes.
* Palliative access.
* Patient-reported outcomes.
* Equity.
* Stockouts.
* Programme indicators from HIV and TB DAKs.
* NCD indicators based on WHO PEN and national requirements.

22. Testing
Test:

* New medical presentation.
* Chronic follow-up.
* Multimorbidity.
* Medication reconciliation.
* Renal dosing.
* Pregnancy-sensitive prescribing.
* HIV/TB integration.
* Hypertension.
* Diabetes.
* Heart failure.
* Asthma and COPD.
* CKD.
* Stroke follow-up.
* Epilepsy.
* Cirrhosis.
* Sickle-cell disease.
* Oncology.
* Geriatrics.
* Palliative care.
* Procedure request.
* Emergency handoff.
* Surgical consultation.
* Offline care.
* Critical-result closure.
* Cross-facility referral.

23. Required demonstrations
Demonstrate:

1. A person with newly detected hypertension and diabetes moving from screening into an integrated care plan.
2. A person with HIV, TB and diabetes managed without three disconnected records.
3. A heart-failure admission from Emergency through inpatient care, medicine titration and follow-up.
4. A patient with CKD progressing into dialysis preparation and the Procedures Pipeline.
5. A patient with stroke moving from Emergency into medical admission, rehabilitation and secondary prevention.
6. A patient with decompensated liver disease undergoing paracentesis through the Procedures Pipeline.
7. A person with suspected cancer moving through diagnosis, MDT, oncology care and palliative support.
8. An older person receiving ICOPE-based assessment and a consolidated multimorbidity plan.
9. A pregnant patient whose medical treatment is coordinated with the Maternity Pack.
10. A complex medical patient requiring surgical consultation without loss of ownership or duplicated clerking.

24. Expected outputs
Produce:

* Repository audit.
* Medicine architecture.
* Specialty map.
* DAK traceability for HIV and TB.
* DAK-style traceability for other specialties.
* Canonical problem model.
* Full medical clerking.
* Specialty workspaces.
* Multimorbidity engine.
* Inpatient medicine.
* Medication reconciliation.
* Decision-support catalogue.
* Procedures integration.
* Analytics.
* Tests.
* Demonstration data.
* Completion report.

25. Definition of done
The pack is complete only when:

* A person has one longitudinal medical record.
* Specialty views use shared clinical truth.
* Problems, medicines, tests and procedures are reconciled.
* HIV and TB DAK requirements are traceable.
* Multimorbidity is managed coherently.
* Emergency and surgical handoffs work.
* Procedures execute through the common pipeline.
* Results are reviewed and actioned.
* Offline operation works.
* End-to-end journeys pass.

The target is a national adult-medicine operating pathway—not a folder of specialist forms.
