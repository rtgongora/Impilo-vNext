# BFF Decomposition — Part 2: Complete Table → Sovereign Owner Map

**Version**: 1.0.0
**Date**: 2026-04-12
**Status**: PROPOSED

This document maps every table in the experience-bff database to its correct sovereign owner,
the BFF controller that currently writes to it, and the migration priority.

Priority key: **CRITICAL** = safety-critical clinical data, **HIGH** = clinical/operational,
**MEDIUM** = operational/financial, **LOW** = social/UI-only

---

## 1. Tables → PCT (Clinical Execution Store)

### 1.1 pct_core schema (Journey/Encounter Orchestration)

| BFF Table | Priority | BFF Controller | PCT Schema | Notes |
|-----------|----------|---------------|------------|-------|
| encounters | CRITICAL | QueueController, multiple | pct_core.encounters | Bridge columns: pct_journey_id, pct_encounter_ref already exist |
| queue_entries | CRITICAL | QueueController | pct_queue.queue_items | Bridge: pct_journey_id exists |
| queue_definitions | HIGH | QueueController | pct_queue.queue_definitions | Facility-specific queue config |
| triage_records | CRITICAL | QueueController, MobileProviderExtended | pct_queue.triage_records | Triage acuity scoring |
| referrals | HIGH | (embedded in BFF domain) | pct_core.referrals | Referral.java has accept/respond/complete state machine |
| clinical_timeline | HIGH | ClinicalTimelineController | pct_clinical.timeline | Or derived view from encounter events |

### 1.2 pct_clinical schema (Clinical Content)

| BFF Table | Priority | BFF Controller | PCT Schema | Notes |
|-----------|----------|---------------|------------|-------|
| allergies | CRITICAL | AllergiesController | pct_clinical.allergies | Safety-critical (drug interactions) |
| conditions | CRITICAL | (implied by EHR) | pct_clinical.conditions | Problem list / diagnoses |
| vitals_records | CRITICAL | (CareEmergencyInpatient) | pct_clinical.vitals | Observation chart entries |
| immunizations | CRITICAL | (implied by EHR) | pct_clinical.immunizations | Vaccination records |
| clinical_notes | CRITICAL | ClinicalNotesController | pct_clinical.clinical_notes | SOAP / clinical documentation |
| observation_entries | HIGH | CareEmergencyInpatientController | pct_clinical.observations | Generic observation entries |
| care_plans | HIGH | CareEmergencyInpatientController | pct_clinical.care_plans | Nursing/care plans |
| care_plan_goals | HIGH | CareEmergencyInpatient, ClinicalDepth | pct_clinical.care_plan_goals | Care plan targets |
| care_plan_interventions | HIGH | CareEmergencyInpatient, ClinicalDepth | pct_clinical.care_plan_interventions | Care plan actions |
| growth_measurements | HIGH | GrowthController | pct_clinical.growth_measurements | Pediatric anthropometrics |
| early_warning_scores | CRITICAL | ClinicalDepthController | pct_clinical.early_warning_scores | NEWS2 triggers escalation |
| apgar_scores | CRITICAL | (maternity flow) | pct_clinical.apgar_scores | Newborn assessment |
| clinical_documents | HIGH | ClinicalDocumentsController | pct_clinical.document_refs | Metadata only; content in Document Service/MinIO |

### 1.3 pct_orders schema (Order/Result/Prescription Tracking)

| BFF Table | Priority | BFF Controller | PCT Schema | Notes |
|-----------|----------|---------------|------------|-------|
| lab_orders | CRITICAL | (bridged to OROS) | pct_orders.order_tracking | Bridge: oros_order_id exists |
| lab_order_results | CRITICAL | (bridged to OROS) | pct_orders.order_results | Result values from OROS |
| lab_results | HIGH | (citizen-facing) | pct_orders.order_results | Citizen view of results |
| prescriptions | CRITICAL | (bridged to Pharmacy) | pct_orders.prescription_tracking | Bridge: pharmacy_dispense_order_id exists |
| service_requests | MEDIUM | (marketplace flow) | pct_orders.service_requests | Service request tracking |
| encounter_order_carts | MEDIUM | (order staging) | pct_orders.order_carts | Provisional order staging per encounter |

### 1.4 pct_inpatient schema (Admission/Emergency/Maternity)

| BFF Table | Priority | BFF Controller | PCT Schema | Notes |
|-----------|----------|---------------|------------|-------|
| admissions | CRITICAL | (inpatient flow) | pct_inpatient.admissions | Inpatient census |
| wards | HIGH | (seeded) | pct_inpatient.wards | Ward definitions |
| beds | HIGH | (seeded) | pct_inpatient.beds | Bed inventory and status |
| patient_transfers | HIGH | (inpatient flow) | pct_inpatient.transfers | Ward-to-ward transfers |
| ward_rounds | HIGH | (inpatient flow) | pct_inpatient.ward_rounds | Ward round documentation |
| ward_round_entries | HIGH | (inpatient flow) | pct_inpatient.ward_round_entries | Per-patient entries |
| ward_charts | HIGH | (charting flow) | pct_inpatient.ward_charts | Inpatient charting system |
| ward_chart_entries | HIGH | (charting flow) | pct_inpatient.ward_chart_entries | Chart data points |
| fluid_balance_records | HIGH | CareEmergencyInpatientController | pct_inpatient.fluid_balance | I&O tracking |
| discharge_clearances | HIGH | ClinicalDepthController | pct_inpatient.discharge_clearances | Discharge workflow gates |
| emergency_activations | CRITICAL | CareEmergencyInpatientController | pct_inpatient.emergency_activations | Emergency protocol activation |
| emergency_actions | CRITICAL | CareEmergencyInpatientController | pct_inpatient.emergency_actions | Actions during emergency |
| resuscitation_records | CRITICAL | CareEmergencyInpatientController | pct_inpatient.resuscitation_records | CPR/resuscitation outcomes |
| resuscitation_phases | CRITICAL | ClinicalDepthController | pct_inpatient.resuscitation_phases | Resuscitation timeline |
| resuscitation_medications | CRITICAL | ClinicalDepthController | pct_inpatient.resuscitation_medications | Meds during resuscitation |
| cpr_cycles | CRITICAL | ClinicalDepthController | pct_inpatient.cpr_cycles | Cycle-by-cycle CPR |
| labour_monitoring_entries | CRITICAL | (maternity flow) | pct_inpatient.labour_monitoring | Labor progression |
| maternity_partograph_sessions | CRITICAL | (maternity flow) | pct_inpatient.partograph_sessions | Partograph management |
| maternity_partograph_points | CRITICAL | (maternity flow) | pct_inpatient.partograph_points | Partograph data points |
| ctg_monitoring_sessions | CRITICAL | (maternity flow) | pct_inpatient.ctg_sessions | CTG session management |
| ctg_trace_chunks | CRITICAL | (maternity flow) | pct_inpatient.ctg_traces | CTG waveform data |
| ctg_annotations | CRITICAL | (maternity flow) | pct_inpatient.ctg_annotations | Clinician annotations |

### 1.5 pct_finance schema (Financial Orchestration Anchors)

| BFF Table | Priority | BFF Controller | PCT Schema | Notes |
|-----------|----------|---------------|------------|-------|
| encounters.costa_bill_id | MEDIUM | (billing bridge) | pct_finance.encounter_billing_refs | Bridge to COSTA |

**PCT total: ~55 tables** — the core of the clinical monolith.

---

## 2. Tables → VITO (Client Registry)

| BFF Table | Priority | BFF Controller | Notes |
|-----------|----------|---------------|-------|
| patients | CRITICAL | MobileProviderExtendedController | Read cache only; writes → VITO API |
| citizen_health_ids | HIGH | (citizen flow) | Health ID card management |
| emergency_contacts | HIGH | (patient demographics) | PII — must live in VITO |
| family_history_members | HIGH | (EHR history) | Identity-scoped history |
| family_history_conditions | HIGH | (EHR history) | Conditions linked to family |
| social_history_entries | HIGH | (EHR history) | Social/lifestyle history |
| advance_directives | HIGH | (EHR) | Medical directives (consent-adjacent) |
| functional_assessments | HIGH | (EHR) | Functional status scales |
| patient_procedures | HIGH | (EHR) | Procedure history |
| consent_preferences | MEDIUM | (citizen flow) | → TSHEPO Consent Service |

**VITO total: ~10 tables**

---

## 3. Tables → TUSO (Facility Registry)

| BFF Table | Priority | BFF Controller | Notes |
|-----------|----------|---------------|-------|
| facilities | CRITICAL | (seeded, read cache) | Read-only cache from TUSO |
| workspaces | HIGH | (seeded) | Read-only cache from TUSO |
| shifts | HIGH | (shift flow) | → TUSO shift management |
| on_call_assignments | HIGH | (staffing) | → TUSO resource scheduling |
| on_call_swap_requests | MEDIUM | (staffing) | → TUSO resource scheduling |
| appointments | HIGH | (scheduling) | Bridge: tuso_booking_id exists → TUSO Booking API |

**TUSO total: ~6 tables**

---

## 4. Tables → Channels Service (Omnichannel — Ring 0 Kernel)

| BFF Table | Priority | BFF Controller | Notes |
|-----------|----------|---------------|-------|
| conversations | MEDIUM | CommunicationController | → Channels session/messaging |
| conversation_participants | MEDIUM | CommunicationController | → Channels |
| messages | MEDIUM | CommunicationController | → Channels messaging |
| message_channels | MEDIUM | CommunicationController | → Channels |
| clinical_messages | HIGH | CommunicationController | Secure clinical messaging |
| clinical_pages | HIGH | CommunicationController, MobileProviderExtended | Urgent clinical paging |
| omni_callback_queue | MEDIUM | (omnichannel flow) | → Channels callback |
| omni_channel_configs | MEDIUM | (omnichannel flow) | → Channels config |
| omni_disclosure_rules | MEDIUM | (omnichannel flow) | → Channels disclosure |
| omni_sms_journeys | MEDIUM | (omnichannel flow) | → Channels SMS automation |
| omni_ussd_menus | MEDIUM | (omnichannel flow) | → Channels USSD |
| omni_ivr_flows | MEDIUM | (omnichannel flow) | → Channels IVR |
| telemedicine_sessions | MEDIUM | (telemedicine flow) | → Channels virtual sessions |
| citizen_telehealth_sessions | MEDIUM | (citizen flow) | → Channels virtual sessions |

**Channels total: ~14 tables**

---

## 5. Tables → Notification Service

| BFF Table | Priority | BFF Controller | Notes |
|-----------|----------|---------------|-------|
| announcements | MEDIUM | CommunicationController | → Notification templates |
| announcement_acknowledgments | MEDIUM | CommunicationController | → Notification tracking |
| reminders | LOW | (citizen flow) | → Notification reminders |

**Notification total: ~3 tables**

---

## 6. Tables → Other Sovereign Services

| BFF Table | Priority | Target Service | Notes |
|-----------|----------|---------------|-------|
| inventory_items | MEDIUM | Inventory Service | Stock master data |
| inventory_stock_counts | MEDIUM | Inventory Service | Physical counts |
| inventory_movements | MEDIUM | Inventory Service | Movement audit trail |
| inventory_requisitions | MEDIUM | Inventory Service | Internal requisitions |
| stock_transfers | MEDIUM | Inventory Service | Transfer workflow |
| stock_recalls | MEDIUM | Inventory Service | Batch recalls |
| marketplace_orders | MEDIUM | MSIKA-Flow | Bridge to OrderEntity |
| marketplace_services | MEDIUM | MSIKA-Flow | Service catalog |
| coverage_plans | HIGH | Coverage Service | Insurance/coverage plans |
| health_wallets | MEDIUM | MUSHEX | Financial wallets |
| wallet_transactions | MEDIUM | MUSHEX | Wallet ledger |
| admin_users | MEDIUM | TSHEPO | Admin user directory |
| monitoring_devices | MEDIUM | IoT Ingestion | Device registry |
| sos_alerts | HIGH | Notification/Channels | Emergency alerts |
| registry_providers | MEDIUM | VARAPI | Provider directory cache |

**Other services total: ~15 tables**

---

## 7. Tables → BFF (Stays — Experience-Native)

| BFF Table | Priority | Rationale |
|-----------|----------|-----------|
| event_outbox | INTERNAL | BFF eventing infrastructure |
| experience_bff_idempotency | INTERNAL | Request deduplication |
| audit_log | INTERNAL | BFF audit trail |
| report_jobs | LOW | Report generation queue |
| wellness_activities | LOW | Experience doctrine: wellness is BFF-native |
| wellness_vitals_log | LOW | Self-tracked (distinct from clinical vitals) |
| wellness_mood_log | LOW | Mood/mental health self-reporting |
| wellness_challenges | LOW | Wellness challenge campaigns |
| wellness_challenge_participants | LOW | Challenge participation |
| wellness_clubs | LOW | Wellness club management |
| wellness_club_members | LOW | Club membership |
| wellness_connect_ingest_log | LOW | Health Connect dedup log |
| wellness_connect_extension | LOW | Health Connect long-tail storage |
| wellness_sleep_segments | LOW | Sleep tracking |
| wellness_exercise_sessions | LOW | Exercise tracking |
| feed_items | LOW | Social feed |
| feed_likes | LOW | Feed engagement |
| community_groups | LOW | Support groups |
| community_group_members | LOW | Group membership |
| discussion_posts | LOW | Discussion posts |
| discussion_comments | LOW | Discussion comments |
| professional_pages | LOW | Provider public profiles |
| crowdfunding_campaigns | LOW | Medical crowdfunding |
| crowdfunding_donations | LOW | Donation tracking |
| citizen_support_tickets | LOW | Citizen support |
| citizen_queue_tickets | LOW | Citizen-visible queue status |

**BFF retains: ~26 tables** (down from 123)

---

## 8. Summary

| Destination | Table Count | Priority Mix |
|-------------|-------------|-------------|
| **PCT** | ~55 | 30 CRITICAL, 20 HIGH, 5 MEDIUM |
| **VITO** | ~10 | 1 CRITICAL, 7 HIGH, 2 MEDIUM |
| **TUSO** | ~6 | 1 CRITICAL, 4 HIGH, 1 MEDIUM |
| **Channels** | ~14 | 2 HIGH, 12 MEDIUM |
| **Notification** | ~3 | 3 MEDIUM/LOW |
| **Other services** | ~15 | 1 HIGH, 12 MEDIUM, 2 LOW |
| **BFF (stays)** | ~26 | 26 LOW/INTERNAL |
| **TOTAL** | ~129 | |

PCT absorbs the lion's share because it IS the clinical execution engine.
The BFF shrinks from 123 business tables to ~26 experience-native tables.
