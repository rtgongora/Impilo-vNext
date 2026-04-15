"use client";

/**
 * Allied Health & Specialist encounter forms.
 *
 * Each form provides structured checklists and assessment tools
 * specific to the discipline. Covers:
 * - Physiotherapy
 * - Occupational Therapy
 * - Psychology / Counselling
 * - Nutritionist / Dietitian
 * - Social Worker
 * - Speech & Language Therapy
 * - Radiographer / Imaging Tech
 * - Lab Technologist
 * - Optometrist
 * - Audiologist
 * - Podiatrist
 * - Respiratory Therapist
 * - Dental (Dentist / Oral Hygienist)
 * - Radiotherapy / Oncology
 * - Emergency Medical Technician (EMT / Paramedic)
 * - Community Health Worker (CHW)
 * - Environmental Health Officer (EHO)
 */

import { useState } from "react";
import {
  Activity, Brain, ClipboardCheck, Eye, Heart, Loader2,
  MessageCircle, Stethoscope, Thermometer, Users, Zap,
} from "lucide-react";

// ── Shared checklist component ────────────────────────────────────

interface CheckItem { id: string; label: string }

function Checklist({ title, items, checked, onToggle }: {
  title: string; items: CheckItem[]; checked: Set<string>; onToggle: (id: string) => void;
}) {
  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <div className="px-3 py-2 bg-gray-50 text-xs font-semibold text-gray-600 flex justify-between">
        <span>{title}</span>
        <span className="text-gray-400">{checked.size}/{items.length}</span>
      </div>
      <div className="p-2 grid grid-cols-2 gap-1">
        {items.map((item) => (
          <label key={item.id} className="flex items-center gap-2 px-2 py-1 rounded hover:bg-gray-50 cursor-pointer text-sm">
            <input type="checkbox" checked={checked.has(item.id)} onChange={() => onToggle(item.id)}
              className="rounded border-gray-300 text-impilo-500 focus:ring-impilo-400" />
            <span className={checked.has(item.id) ? "text-gray-900" : "text-gray-600"}>{item.label}</span>
          </label>
        ))}
      </div>
    </div>
  );
}

function useChecklist() {
  const [checked, setChecked] = useState(new Set<string>());
  const toggle = (id: string) => setChecked((prev) => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  return { checked, toggle };
}

function Dropdown({ label, value, onChange, options }: {
  label: string; value: string; onChange: (v: string) => void; options: string[];
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-gray-600">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}
        className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
        <option value="">Select...</option>
        {options.map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
    </label>
  );
}

function TextArea({ label, value, onChange, placeholder, rows = 2 }: {
  label: string; value: string; onChange: (v: string) => void; placeholder?: string; rows?: number;
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-gray-600">{label}</span>
      <textarea value={value} onChange={(e) => onChange(e.target.value)} rows={rows} placeholder={placeholder}
        className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
    </label>
  );
}

function Field({ label, value, onChange, placeholder }: {
  label: string; value: string; onChange: (v: string) => void; placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-gray-600">{label}</span>
      <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder}
        className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
    </label>
  );
}

// ═══════════════════════════════════════════════════════════════════
// PHYSIOTHERAPY
// ═══════════════════════════════════════════════════════════════════

const PHYSIO_ASSESSMENT: CheckItem[] = [
  { id: "rom_assessed", label: "Range of motion assessed" },
  { id: "strength_tested", label: "Muscle strength tested" },
  { id: "gait_assessed", label: "Gait analysis done" },
  { id: "balance_tested", label: "Balance assessed" },
  { id: "posture_assessed", label: "Posture assessed" },
  { id: "neuro_screen", label: "Neurological screen done" },
  { id: "pain_localised", label: "Pain site localised & graded" },
  { id: "functional_status", label: "Functional status scored" },
  { id: "joint_integrity", label: "Joint integrity assessed" },
  { id: "soft_tissue", label: "Soft tissue palpation done" },
];

export function PhysiotherapyForm() {
  const assess = useChecklist();
  const [painScore, setPainScore] = useState("");
  const [site, setSite] = useState("");
  const [diagnosis, setDiagnosis] = useState("");
  const [goals, setGoals] = useState("");
  const [treatment, setTreatment] = useState("");
  const [modalities, setModalities] = useState("");
  const [frequency, setFrequency] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Physiotherapy Assessment</h3>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Pain score (0-10)" value={painScore} onChange={setPainScore} placeholder="e.g. 6/10" />
        <Field label="Primary site / region" value={site} onChange={setSite} placeholder="e.g. Lumbar spine, Right knee" />
      </div>
      <Checklist title="Assessment Performed" items={PHYSIO_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <Field label="Physiotherapy diagnosis" value={diagnosis} onChange={setDiagnosis} placeholder="e.g. Mechanical low back pain" />
      <TextArea label="Treatment goals" value={goals} onChange={setGoals} placeholder="Short-term and long-term goals..." />
      <TextArea label="Treatment given" value={treatment} onChange={setTreatment} placeholder="Mobilisation, exercises, manual therapy..." />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Modalities used" value={modalities} onChange={setModalities} placeholder="TENS, ultrasound, heat..." />
        <Dropdown label="Follow-up frequency" value={frequency} onChange={setFrequency}
          options={["Daily", "2x/week", "3x/week", "Weekly", "Fortnightly", "Monthly", "Discharge"]} />
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// OCCUPATIONAL THERAPY
// ═══════════════════════════════════════════════════════════════════

const OT_ASSESSMENT: CheckItem[] = [
  { id: "adl_assessed", label: "ADLs assessed (feeding, dressing, bathing)" },
  { id: "iadl_assessed", label: "IADLs assessed (cooking, cleaning, transport)" },
  { id: "cognitive_screen", label: "Cognitive screening done" },
  { id: "hand_function", label: "Hand function assessed" },
  { id: "sensory_assessment", label: "Sensory assessment done" },
  { id: "environmental_assessment", label: "Home/environment assessed" },
  { id: "assistive_device", label: "Assistive device needs assessed" },
  { id: "splint_needed", label: "Splinting needs assessed" },
  { id: "vocational_assessment", label: "Vocational assessment done" },
  { id: "caregiver_training", label: "Caregiver training given" },
];

export function OccupationalTherapyForm() {
  const assess = useChecklist();
  const [barthel, setBarthel] = useState("");
  const [goals, setGoals] = useState("");
  const [treatment, setTreatment] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Occupational Therapy Assessment</h3>
      <Field label="Barthel Index / FIM score" value={barthel} onChange={setBarthel} placeholder="e.g. 75/100" />
      <Checklist title="Assessment Performed" items={OT_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <TextArea label="Treatment goals" value={goals} onChange={setGoals} placeholder="Functional independence goals..." />
      <TextArea label="Treatment / intervention" value={treatment} onChange={setTreatment} placeholder="ADL training, splinting, ergonomic advice..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// PSYCHOLOGY / COUNSELLING
// ═══════════════════════════════════════════════════════════════════

const PSYCH_ASSESSMENT: CheckItem[] = [
  { id: "mental_status_exam", label: "Mental status exam (MSE) done" },
  { id: "mood_assessed", label: "Mood & affect assessed" },
  { id: "thought_content", label: "Thought content assessed" },
  { id: "cognition_tested", label: "Cognition tested (MMSE/MoCA)" },
  { id: "risk_assessed", label: "Suicide / self-harm risk assessed" },
  { id: "substance_screen", label: "Substance use screening done" },
  { id: "trauma_screen", label: "Trauma history explored" },
  { id: "social_support", label: "Social support assessed" },
  { id: "coping_strategies", label: "Coping strategies explored" },
  { id: "safety_plan", label: "Safety plan discussed (if needed)" },
];

export function PsychologyForm() {
  const assess = useChecklist();
  const [phq9, setPhq9] = useState("");
  const [gad7, setGad7] = useState("");
  const [riskLevel, setRiskLevel] = useState("");
  const [formulation, setFormulation] = useState("");
  const [intervention, setIntervention] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Psychology / Counselling Session</h3>
      <div className="grid grid-cols-3 gap-3">
        <Field label="PHQ-9 score" value={phq9} onChange={setPhq9} placeholder="0-27" />
        <Field label="GAD-7 score" value={gad7} onChange={setGad7} placeholder="0-21" />
        <Dropdown label="Risk level" value={riskLevel} onChange={setRiskLevel}
          options={["No risk", "Low", "Moderate", "High", "Imminent"]} />
      </div>
      <Checklist title="Assessment Performed" items={PSYCH_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <TextArea label="Case formulation" value={formulation} onChange={setFormulation} placeholder="Presenting issues, precipitating factors, maintaining factors..." />
      <TextArea label="Therapeutic intervention" value={intervention} onChange={setIntervention} placeholder="CBT, motivational interviewing, psychoeducation..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// NUTRITIONIST / DIETITIAN
// ═══════════════════════════════════════════════════════════════════

const NUTRITION_ASSESSMENT: CheckItem[] = [
  { id: "anthropometry", label: "Anthropometry (weight, height, BMI, MUAC)" },
  { id: "dietary_recall", label: "24-hour dietary recall done" },
  { id: "food_frequency", label: "Food frequency questionnaire" },
  { id: "clinical_signs", label: "Clinical signs of malnutrition checked" },
  { id: "biochemistry_reviewed", label: "Biochemistry reviewed (albumin, Hb)" },
  { id: "fluid_intake", label: "Fluid intake assessed" },
  { id: "feeding_difficulties", label: "Feeding difficulties assessed" },
  { id: "allergies_intolerances", label: "Food allergies/intolerances documented" },
  { id: "socioeconomic", label: "Socioeconomic factors considered" },
  { id: "education_given", label: "Nutrition education given" },
];

export function NutritionForm() {
  const assess = useChecklist();
  const [bmi, setBmi] = useState("");
  const [muac, setMuac] = useState("");
  const [classification, setClassification] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Nutrition / Dietetics Assessment</h3>
      <div className="grid grid-cols-3 gap-3">
        <Field label="BMI" value={bmi} onChange={setBmi} placeholder="e.g. 22.5" />
        <Field label="MUAC (cm)" value={muac} onChange={setMuac} placeholder="e.g. 24.0" />
        <Dropdown label="Nutrition status" value={classification} onChange={setClassification}
          options={["Normal", "At risk", "Moderate acute malnutrition (MAM)", "Severe acute malnutrition (SAM)", "Overweight", "Obese", "Underweight"]} />
      </div>
      <Checklist title="Assessment Performed" items={NUTRITION_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <TextArea label="Dietary plan / intervention" value={plan} onChange={setPlan} placeholder="Meal plan, supplements, RUTF, therapeutic feeding..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// SOCIAL WORKER
// ═══════════════════════════════════════════════════════════════════

const SOCIAL_WORK_ASSESSMENT: CheckItem[] = [
  { id: "psychosocial_assessed", label: "Psychosocial needs assessed" },
  { id: "financial_assessed", label: "Financial situation assessed" },
  { id: "housing_assessed", label: "Housing / living situation assessed" },
  { id: "support_network", label: "Support network identified" },
  { id: "abuse_screened", label: "Abuse / GBV screening done" },
  { id: "child_protection", label: "Child protection concerns assessed" },
  { id: "substance_assessed", label: "Substance use explored" },
  { id: "legal_needs", label: "Legal needs identified" },
  { id: "referrals_made", label: "Community referrals made" },
  { id: "discharge_planning", label: "Discharge planning initiated" },
];

export function SocialWorkerForm() {
  const assess = useChecklist();
  const [concerns, setConcerns] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Social Work Assessment</h3>
      <Checklist title="Assessment Performed" items={SOCIAL_WORK_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <TextArea label="Key concerns" value={concerns} onChange={setConcerns} placeholder="Social, financial, safety concerns..." />
      <TextArea label="Social work plan" value={plan} onChange={setPlan} placeholder="Referrals, support services, follow-up..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// SPEECH & LANGUAGE THERAPY
// ═══════════════════════════════════════════════════════════════════

const SLT_ASSESSMENT: CheckItem[] = [
  { id: "swallowing_assessed", label: "Swallowing assessment done" },
  { id: "speech_assessed", label: "Speech production assessed" },
  { id: "language_assessed", label: "Language comprehension/expression assessed" },
  { id: "voice_assessed", label: "Voice quality assessed" },
  { id: "fluency_assessed", label: "Fluency assessed" },
  { id: "cognitive_comm", label: "Cognitive communication assessed" },
  { id: "aac_needs", label: "AAC needs assessed" },
  { id: "feeding_assessed", label: "Paediatric feeding assessed" },
];

export function SpeechTherapyForm() {
  const assess = useChecklist();
  const [dysphagia, setDysphagia] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Speech & Language Therapy</h3>
      <Dropdown label="Dysphagia grade" value={dysphagia} onChange={setDysphagia}
        options={["None", "Mild", "Moderate", "Severe", "NPO (nil by mouth)"]} />
      <Checklist title="Assessment Performed" items={SLT_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <TextArea label="Therapy plan" value={plan} onChange={setPlan} placeholder="Exercises, diet modification, AAC recommendations..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// RADIOGRAPHER / IMAGING TECHNOLOGIST
// ═══════════════════════════════════════════════════════════════════

const RADIOGRAPHER_CHECKS: CheckItem[] = [
  { id: "request_verified", label: "Imaging request verified" },
  { id: "patient_id_confirmed", label: "Patient identity confirmed" },
  { id: "pregnancy_checked", label: "Pregnancy status checked (if applicable)" },
  { id: "contrast_allergy", label: "Contrast allergy checked" },
  { id: "consent_obtained", label: "Consent obtained (if invasive)" },
  { id: "positioning_correct", label: "Patient positioned correctly" },
  { id: "radiation_dose_recorded", label: "Radiation dose recorded" },
  { id: "image_quality_checked", label: "Image quality verified" },
  { id: "images_sent_pacs", label: "Images sent to PACS" },
  { id: "report_requested", label: "Radiologist report requested" },
];

export function RadiographerForm() {
  const checks = useChecklist();
  const [modality, setModality] = useState("");
  const [bodyPart, setBodyPart] = useState("");
  const [notes, setNotes] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Radiographer / Imaging Tech</h3>
      <div className="grid grid-cols-2 gap-3">
        <Dropdown label="Modality" value={modality} onChange={setModality}
          options={["X-ray", "CT", "MRI", "Ultrasound", "Fluoroscopy", "Mammography", "Nuclear medicine", "PET-CT"]} />
        <Field label="Body region" value={bodyPart} onChange={setBodyPart} placeholder="e.g. Chest PA, Abdomen, L-spine" />
      </div>
      <Checklist title="Imaging Checklist" items={RADIOGRAPHER_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
      <TextArea label="Technologist notes" value={notes} onChange={setNotes} placeholder="Image quality, patient cooperation, incidental findings..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// LAB TECHNOLOGIST
// ═══════════════════════════════════════════════════════════════════

const LAB_CHECKS: CheckItem[] = [
  { id: "specimen_received", label: "Specimen received & labelled" },
  { id: "specimen_adequate", label: "Specimen adequacy confirmed" },
  { id: "rejection_criteria", label: "Rejection criteria checked" },
  { id: "test_performed", label: "Test performed" },
  { id: "qc_passed", label: "Quality control passed" },
  { id: "results_verified", label: "Results verified" },
  { id: "critical_checked", label: "Critical values checked & called" },
  { id: "results_released", label: "Results released to LIS" },
];

export function LabTechForm() {
  const checks = useChecklist();
  const [testType, setTestType] = useState("");
  const [notes, setNotes] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Laboratory Technologist</h3>
      <Dropdown label="Test category" value={testType} onChange={setTestType}
        options={["Haematology", "Biochemistry", "Microbiology", "Serology", "Parasitology", "Histopathology", "Cytology", "Blood bank", "Molecular / PCR"]} />
      <Checklist title="Lab Processing Checklist" items={LAB_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
      <TextArea label="Notes / observations" value={notes} onChange={setNotes} placeholder="Specimen quality, unusual findings, repeat needed..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// OPTOMETRIST
// ═══════════════════════════════════════════════════════════════════

const OPTOMETRY_ASSESSMENT: CheckItem[] = [
  { id: "va_tested", label: "Visual acuity tested (distance & near)" },
  { id: "refraction", label: "Refraction done" },
  { id: "iop_measured", label: "IOP measured (tonometry)" },
  { id: "slit_lamp", label: "Slit lamp examination done" },
  { id: "fundoscopy", label: "Fundoscopy done" },
  { id: "visual_fields", label: "Visual field testing done" },
  { id: "colour_vision", label: "Colour vision tested" },
  { id: "contact_lens_fit", label: "Contact lens fitting done" },
];

export function OptometristForm() {
  const assess = useChecklist();
  const [vaRight, setVaRight] = useState("");
  const [vaLeft, setVaLeft] = useState("");
  const [iopRight, setIopRight] = useState("");
  const [iopLeft, setIopLeft] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Optometry Assessment</h3>
      <div className="grid grid-cols-4 gap-3">
        <Field label="VA Right" value={vaRight} onChange={setVaRight} placeholder="6/6" />
        <Field label="VA Left" value={vaLeft} onChange={setVaLeft} placeholder="6/6" />
        <Field label="IOP Right (mmHg)" value={iopRight} onChange={setIopRight} placeholder="15" />
        <Field label="IOP Left (mmHg)" value={iopLeft} onChange={setIopLeft} placeholder="15" />
      </div>
      <Checklist title="Assessment Performed" items={OPTOMETRY_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <TextArea label="Plan" value={plan} onChange={setPlan} placeholder="Prescription, referral to ophthalmology, follow-up..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DENTAL
// ═══════════════════════════════════════════════════════════════════

const DENTAL_ASSESSMENT: CheckItem[] = [
  { id: "extraoral_exam", label: "Extra-oral examination done" },
  { id: "intraoral_exam", label: "Intra-oral examination done" },
  { id: "charting_done", label: "Dental charting done" },
  { id: "periodontal_screen", label: "Periodontal screening done" },
  { id: "radiographs_taken", label: "Radiographs taken" },
  { id: "caries_assessed", label: "Caries risk assessed" },
  { id: "oral_hygiene_scored", label: "Oral hygiene scored" },
  { id: "oral_cancer_screen", label: "Oral cancer screening done" },
];

export function DentalForm() {
  const assess = useChecklist();
  const [treatment, setTreatment] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Dental Assessment</h3>
      <Checklist title="Assessment Performed" items={DENTAL_ASSESSMENT} checked={assess.checked} onToggle={assess.toggle} />
      <Dropdown label="Treatment performed" value={treatment} onChange={setTreatment}
        options={["Examination only", "Scaling & polishing", "Restoration / filling", "Extraction", "Root canal", "Crown / bridge", "Denture", "Emergency treatment", "Referral"]} />
      <TextArea label="Treatment plan" value={plan} onChange={setPlan} placeholder="Next steps, follow-up..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// EMERGENCY MEDICAL TECHNICIAN (EMT / PARAMEDIC)
// ═══════════════════════════════════════════════════════════════════

const EMT_CHECKS: CheckItem[] = [
  { id: "scene_safe", label: "Scene safety assessed" },
  { id: "primary_survey", label: "Primary survey (ABCDE) done" },
  { id: "secondary_survey", label: "Secondary survey done" },
  { id: "c_spine", label: "C-spine immobilisation (if indicated)" },
  { id: "iv_access", label: "IV access established" },
  { id: "airway_managed", label: "Airway managed" },
  { id: "bleeding_controlled", label: "Haemorrhage controlled" },
  { id: "splinting", label: "Fracture splinting done" },
  { id: "medications_given", label: "Medications administered" },
  { id: "monitoring", label: "Continuous monitoring in transit" },
  { id: "handover_given", label: "Handover to receiving facility given" },
];

export function EMTForm() {
  const checks = useChecklist();
  const [mechanism, setMechanism] = useState("");
  const [gcs, setGcs] = useState("");
  const [triage, setTriage] = useState("");
  const [transport, setTransport] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">EMT / Paramedic Assessment</h3>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Mechanism of injury / illness" value={mechanism} onChange={setMechanism} placeholder="e.g. RTA, fall, chest pain" />
        <Field label="GCS score" value={gcs} onChange={setGcs} placeholder="e.g. 15/15" />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Dropdown label="Triage category" value={triage} onChange={setTriage}
          options={["Red — Immediate", "Yellow — Urgent", "Green — Delayed", "Black — Deceased"]} />
        <Dropdown label="Transport decision" value={transport} onChange={setTransport}
          options={["Ambulance (lights & sirens)", "Ambulance (routine)", "Patient transport", "Self-transport", "Not transported"]} />
      </div>
      <Checklist title="Pre-hospital Checklist" items={EMT_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// COMMUNITY HEALTH WORKER (CHW)
// ═══════════════════════════════════════════════════════════════════

const CHW_CHECKS: CheckItem[] = [
  { id: "household_registered", label: "Household registered" },
  { id: "members_enumerated", label: "All household members enumerated" },
  { id: "u5_screened", label: "Under-5 children screened (MUAC, temperature)" },
  { id: "pregnant_identified", label: "Pregnant women identified" },
  { id: "anc_attendance", label: "ANC attendance checked" },
  { id: "immunisation_status", label: "Immunisation status checked" },
  { id: "tb_screening", label: "TB symptom screening done" },
  { id: "hiv_testing_offered", label: "HIV testing offered" },
  { id: "malaria_rdt", label: "Malaria RDT done (if febrile)" },
  { id: "health_education", label: "Health education given" },
  { id: "referral_made", label: "Referral to facility made (if needed)" },
  { id: "defaulter_traced", label: "Treatment defaulter traced" },
];

export function CHWForm() {
  const checks = useChecklist();
  const [visitType, setVisitType] = useState("");
  const [notes, setNotes] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Community Health Worker Visit</h3>
      <Dropdown label="Visit type" value={visitType} onChange={setVisitType}
        options={["Routine household visit", "Defaulter tracing", "Contact tracing", "ANC follow-up", "Postnatal visit", "Under-5 screening", "Growth monitoring", "Health education", "Referral follow-up"]} />
      <Checklist title="Visit Checklist" items={CHW_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
      <TextArea label="Visit notes" value={notes} onChange={setNotes} placeholder="Key findings, referrals, follow-up needed..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// ENVIRONMENTAL HEALTH OFFICER (EHO)
// ═══════════════════════════════════════════════════════════════════

const EHO_CHECKS: CheckItem[] = [
  { id: "water_tested", label: "Water quality tested" },
  { id: "sanitation_inspected", label: "Sanitation facilities inspected" },
  { id: "waste_management", label: "Waste management assessed" },
  { id: "food_safety", label: "Food safety inspection done" },
  { id: "vector_control", label: "Vector breeding sites checked" },
  { id: "air_quality", label: "Air quality / ventilation assessed" },
  { id: "occupational_hazards", label: "Occupational hazards assessed" },
  { id: "housing_conditions", label: "Housing conditions assessed" },
  { id: "samples_collected", label: "Samples collected for lab" },
  { id: "enforcement_issued", label: "Enforcement notice issued" },
];

export function EHOForm() {
  const checks = useChecklist();
  const [siteType, setSiteType] = useState("");
  const [findings, setFindings] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Environmental Health Assessment</h3>
      <Dropdown label="Site type" value={siteType} onChange={setSiteType}
        options={["Food premises", "Water source", "School", "Market", "Housing", "Workplace", "Abattoir", "Burial site", "Healthcare facility", "Swimming pool"]} />
      <Checklist title="Inspection Checklist" items={EHO_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
      <TextArea label="Findings & recommendations" value={findings} onChange={setFindings} placeholder="Key findings, corrective actions, compliance status..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// RESPIRATORY THERAPIST
// ═══════════════════════════════════════════════════════════════════

const RESPIRATORY_CHECKS: CheckItem[] = [
  { id: "airway_assessed", label: "Airway patency assessed" },
  { id: "breath_sounds", label: "Breath sounds auscultated" },
  { id: "spo2_monitored", label: "SpO2 monitored" },
  { id: "abg_reviewed", label: "ABG reviewed" },
  { id: "spirometry", label: "Spirometry performed" },
  { id: "nebuliser_given", label: "Nebuliser therapy given" },
  { id: "oxygen_titrated", label: "Oxygen therapy titrated" },
  { id: "chest_physio", label: "Chest physiotherapy done" },
  { id: "ventilator_check", label: "Ventilator settings checked" },
  { id: "weaning_assessed", label: "Weaning readiness assessed" },
];

export function RespiratoryTherapistForm() {
  const checks = useChecklist();
  const [spo2, setSpo2] = useState("");
  const [fio2, setFio2] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Respiratory Therapy</h3>
      <div className="grid grid-cols-2 gap-3">
        <Field label="SpO2 (%)" value={spo2} onChange={setSpo2} placeholder="e.g. 96" />
        <Field label="FiO2 (%)" value={fio2} onChange={setFio2} placeholder="e.g. 40" />
      </div>
      <Checklist title="Respiratory Assessment" items={RESPIRATORY_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
      <TextArea label="Respiratory plan" value={plan} onChange={setPlan} placeholder="O2 target, nebuliser schedule, ventilator adjustments..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// AUDIOLOGIST
// ═══════════════════════════════════════════════════════════════════

export function AudiologistForm() {
  const [ptaRight, setPtaRight] = useState("");
  const [ptaLeft, setPtaLeft] = useState("");
  const [type, setType] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Audiology Assessment</h3>
      <div className="grid grid-cols-3 gap-3">
        <Field label="PTA Right (dBHL)" value={ptaRight} onChange={setPtaRight} placeholder="e.g. 25" />
        <Field label="PTA Left (dBHL)" value={ptaLeft} onChange={setPtaLeft} placeholder="e.g. 30" />
        <Dropdown label="Hearing loss type" value={type} onChange={setType}
          options={["Normal", "Conductive", "Sensorineural", "Mixed", "Central"]} />
      </div>
      <TextArea label="Plan" value={plan} onChange={setPlan} placeholder="Hearing aid fitting, ENT referral, follow-up..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// PODIATRIST
// ═══════════════════════════════════════════════════════════════════

const PODIATRY_CHECKS: CheckItem[] = [
  { id: "vascular_assessed", label: "Vascular assessment done (pulses, CRT)" },
  { id: "neurological_assessed", label: "Neurological assessment (monofilament)" },
  { id: "dermatological_assessed", label: "Skin & nail assessment done" },
  { id: "biomechanical_assessed", label: "Biomechanical assessment done" },
  { id: "footwear_assessed", label: "Footwear assessed" },
  { id: "wound_assessed", label: "Wound/ulcer assessed (if present)" },
  { id: "education_given", label: "Foot care education given" },
  { id: "risk_classified", label: "Risk classification done" },
];

export function PodiatristForm() {
  const checks = useChecklist();
  const [risk, setRisk] = useState("");
  const [plan, setPlan] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Podiatry Assessment</h3>
      <Dropdown label="Diabetic foot risk category" value={risk} onChange={setRisk}
        options={["Low risk", "Moderate risk", "High risk", "Active foot disease"]} />
      <Checklist title="Assessment Performed" items={PODIATRY_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
      <TextArea label="Treatment plan" value={plan} onChange={setPlan} placeholder="Nail care, wound management, orthotic referral..." />
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// RADIOTHERAPY / ONCOLOGY THERAPY
// ═══════════════════════════════════════════════════════════════════

const RADIOTHERAPY_CHECKS: CheckItem[] = [
  { id: "treatment_plan_verified", label: "Treatment plan verified" },
  { id: "patient_positioned", label: "Patient positioned & immobilised" },
  { id: "dose_verified", label: "Dose calculation verified" },
  { id: "field_verified", label: "Treatment field verified" },
  { id: "beam_delivered", label: "Beam delivered" },
  { id: "side_effects_assessed", label: "Acute side effects assessed" },
  { id: "skin_reaction_graded", label: "Skin reaction graded" },
  { id: "patient_counselled", label: "Patient counselled on care" },
];

export function RadiotherapyForm() {
  const checks = useChecklist();
  const [fractionNum, setFractionNum] = useState("");
  const [totalFractions, setTotalFractions] = useState("");
  const [sideEffects, setSideEffects] = useState("");
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-900">Radiotherapy Session</h3>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Fraction number" value={fractionNum} onChange={setFractionNum} placeholder="e.g. 15" />
        <Field label="Total fractions" value={totalFractions} onChange={setTotalFractions} placeholder="e.g. 30" />
      </div>
      <Checklist title="Treatment Checklist" items={RADIOTHERAPY_CHECKS} checked={checks.checked} onToggle={checks.toggle} />
      <TextArea label="Side effects / toxicity notes" value={sideEffects} onChange={setSideEffects} placeholder="Skin, mucositis, fatigue, nausea..." />
    </div>
  );
}
