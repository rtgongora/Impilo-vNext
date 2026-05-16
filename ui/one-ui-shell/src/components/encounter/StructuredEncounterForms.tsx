"use client";

/**
 * Role-specific structured encounter forms.
 *
 * Replaces free-text SOAP with checklists, dropdowns, and structured
 * fields specific to the logged-in clinician's role. Free text is still
 * available as "Additional Notes" but is no longer primary.
 */

import { useState } from "react";
import {
  Activity, Baby, ChevronDown,
  ClipboardCheck, Heart, Pill, Stethoscope,
} from "lucide-react";

// ── Common Types ──────────────────────────────────────────────────

interface CheckItem {
  id: string;
  label: string;
  category?: string;
}

function ChecklistSection({
  title,
  icon: Icon,
  items,
  checked,
  onToggle,
}: {
  title: string;
  icon: React.ElementType;
  items: CheckItem[];
  checked: Set<string>;
  onToggle: (id: string) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <button onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-2 px-3 py-2 bg-gray-50 text-left text-sm font-medium text-gray-700 hover:bg-gray-100">
        <Icon className="w-4 h-4 text-gray-500" />
        <span className="flex-1">{title}</span>
        <span className="text-xs text-gray-400">{checked.size}/{items.length}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-gray-400 transition-transform ${expanded ? "rotate-180" : ""}`} />
      </button>
      {expanded && (
        <div className="p-2 grid grid-cols-2 gap-1">
          {items.map((item) => (
            <label key={item.id} className="flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-gray-50 cursor-pointer text-sm">
              <input type="checkbox" checked={checked.has(item.id)} onChange={() => onToggle(item.id)}
                className="rounded border-gray-300 text-impilo-500 focus:ring-impilo-400" />
              <span className={checked.has(item.id) ? "text-gray-900" : "text-gray-600"}>{item.label}</span>
            </label>
          ))}
        </div>
      )}
    </div>
  );
}

function DropdownField({ label, value, onChange, options, placeholder }: {
  label: string; value: string; onChange: (v: string) => void;
  options: string[]; placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-gray-600">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}
        className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
        <option value="">{placeholder || "Select..."}</option>
        {options.map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
    </label>
  );
}

// ── Role-Specific Forms ───────────────────────────────────────────

// ── CLINICIAN / DOCTOR ────────────────────────────────────────────
const DOCTOR_SYSTEMS_REVIEW: CheckItem[] = [
  { id: "constitutional", label: "Constitutional (fever, weight, fatigue)" },
  { id: "heent", label: "HEENT" },
  { id: "respiratory", label: "Respiratory" },
  { id: "cardiovascular", label: "Cardiovascular" },
  { id: "gastrointestinal", label: "Gastrointestinal" },
  { id: "genitourinary", label: "Genitourinary" },
  { id: "musculoskeletal", label: "Musculoskeletal" },
  { id: "neurological", label: "Neurological" },
  { id: "psychiatric", label: "Psychiatric" },
  { id: "skin", label: "Skin / Integumentary" },
  { id: "endocrine", label: "Endocrine" },
  { id: "lymphatic", label: "Haem / Lymphatic" },
];

const DOCTOR_EXAM_FINDINGS: CheckItem[] = [
  { id: "general_appearance", label: "General appearance normal" },
  { id: "vitals_stable", label: "Vitals stable" },
  { id: "head_neck_normal", label: "Head & neck NAD" },
  { id: "chest_clear", label: "Chest clear" },
  { id: "heart_sounds_normal", label: "Heart sounds normal" },
  { id: "abdomen_soft", label: "Abdomen soft, non-tender" },
  { id: "limbs_normal", label: "Limbs — normal power/tone" },
  { id: "neuro_intact", label: "Neuro — grossly intact" },
  { id: "skin_normal", label: "Skin — no rash/lesions" },
  { id: "lymph_normal", label: "Lymph nodes — not enlarged" },
];

export function DoctorEncounterForm({ onDataChange }: { onDataChange: (data: Record<string, unknown>) => void }) {
  void onDataChange;
  const [systemsChecked, setSystemsChecked] = useState(new Set<string>());
  const [examChecked, setExamChecked] = useState(new Set<string>());
  const [chiefComplaint, setChiefComplaint] = useState("");
  const [hpi, setHpi] = useState("");
  const [clinicalImpression, setClinicalImpression] = useState("");
  const [severity, setSeverity] = useState("");
  const [differentials, setDifferentials] = useState("");
  const [planMedications, setPlanMedications] = useState("");
  const [planInvestigations, setPlanInvestigations] = useState("");
  const [planReferral, setPlanReferral] = useState("");
  const [planFollowUp, setPlanFollowUp] = useState("");
  const [additionalNotes, setAdditionalNotes] = useState("");

  function toggle(set: Set<string>, setFn: (s: Set<string>) => void, id: string) {
    const next = new Set(set);
    if (next.has(id)) next.delete(id); else next.add(id);
    setFn(next);
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
        <Stethoscope className="w-4 h-4 text-impilo-500" /> Clinician Encounter Form
      </h3>

      {/* Chief Complaint */}
      <label className="block">
        <span className="text-xs font-medium text-gray-600">Chief complaint *</span>
        <input value={chiefComplaint} onChange={(e) => setChiefComplaint(e.target.value)}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          placeholder="Main reason for visit in patient's own words..." />
      </label>

      {/* HPI */}
      <label className="block">
        <span className="text-xs font-medium text-gray-600">History of presenting illness</span>
        <textarea value={hpi} onChange={(e) => setHpi(e.target.value)} rows={3}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          placeholder="Onset, duration, severity, associated symptoms, aggravating/relieving factors..." />
      </label>

      {/* Systems Review — checklist */}
      <ChecklistSection title="Systems Review" icon={ClipboardCheck}
        items={DOCTOR_SYSTEMS_REVIEW} checked={systemsChecked}
        onToggle={(id) => toggle(systemsChecked, setSystemsChecked, id)} />

      {/* Physical Exam — checklist */}
      <ChecklistSection title="Physical Examination" icon={Activity}
        items={DOCTOR_EXAM_FINDINGS} checked={examChecked}
        onToggle={(id) => toggle(examChecked, setExamChecked, id)} />

      {/* Clinical Impression */}
      <div className="grid grid-cols-2 gap-3">
        <label className="block">
          <span className="text-xs font-medium text-gray-600">Clinical impression / diagnosis *</span>
          <input value={clinicalImpression} onChange={(e) => setClinicalImpression(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="Working diagnosis..." />
        </label>
        <DropdownField label="Severity" value={severity} onChange={setSeverity}
          options={["Mild", "Moderate", "Severe", "Critical", "Life-threatening"]} />
      </div>

      <label className="block">
        <span className="text-xs font-medium text-gray-600">Differential diagnoses</span>
        <input value={differentials} onChange={(e) => setDifferentials(e.target.value)}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          placeholder="Comma-separated alternatives..." />
      </label>

      {/* Management Plan — structured */}
      <div className="border border-impilo-200 rounded-lg p-3 bg-impilo-50/30 space-y-3">
        <h4 className="text-xs font-semibold text-impilo-700 uppercase">Management Plan</h4>
        <label className="block">
          <span className="text-xs font-medium text-gray-600">Medications to prescribe</span>
          <textarea value={planMedications} onChange={(e) => setPlanMedications(e.target.value)} rows={2}
            className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="Drug, dose, frequency, duration (one per line)..." />
        </label>
        <label className="block">
          <span className="text-xs font-medium text-gray-600">Investigations to order</span>
          <textarea value={planInvestigations} onChange={(e) => setPlanInvestigations(e.target.value)} rows={2}
            className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="FBC, U&E, LFTs, X-ray chest PA (one per line)..." />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <DropdownField label="Referral needed" value={planReferral} onChange={setPlanReferral}
            options={["None", "Specialist", "Teleconsult", "Emergency transfer", "Social services"]} />
          <DropdownField label="Follow-up" value={planFollowUp} onChange={setPlanFollowUp}
            options={["None", "1 week", "2 weeks", "1 month", "3 months", "6 months", "As needed", "If deteriorating"]} />
        </div>
      </div>

      {/* Additional notes — free text fallback */}
      <label className="block">
        <span className="text-xs font-medium text-gray-600">Additional notes (optional)</span>
        <textarea value={additionalNotes} onChange={(e) => setAdditionalNotes(e.target.value)} rows={2}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-500"
          placeholder="Any other observations..." />
      </label>
    </div>
  );
}

// ── NURSE ─────────────────────────────────────────────────────────
const NURSE_ASSESSMENT: CheckItem[] = [
  { id: "pain_assessed", label: "Pain assessed (score recorded)" },
  { id: "fall_risk", label: "Fall risk assessed" },
  { id: "pressure_ulcer", label: "Pressure ulcer risk assessed" },
  { id: "nutrition_screen", label: "Nutrition screening done" },
  { id: "fluid_balance", label: "Fluid balance recorded" },
  { id: "wound_assessed", label: "Wound/dressing assessed" },
  { id: "patient_education", label: "Patient education given" },
  { id: "medication_check", label: "Medication reconciliation done" },
  { id: "allergy_verified", label: "Allergies verified" },
  { id: "consent_confirmed", label: "Consent confirmed" },
];

const NURSE_INTERVENTIONS: CheckItem[] = [
  { id: "vitals_taken", label: "Vitals recorded" },
  { id: "iv_access", label: "IV access established/checked" },
  { id: "medication_admin", label: "Medications administered" },
  { id: "wound_care", label: "Wound care performed" },
  { id: "catheter_care", label: "Catheter care" },
  { id: "positioning", label: "Patient repositioned" },
  { id: "oral_care", label: "Oral care given" },
  { id: "hygiene_assist", label: "Hygiene assistance" },
  { id: "specimen_collected", label: "Specimen collected" },
  { id: "patient_mobilised", label: "Patient mobilised" },
];

export function NurseEncounterForm({ onDataChange }: { onDataChange: (data: Record<string, unknown>) => void }) {
  void onDataChange;
  const [assessChecked, setAssessChecked] = useState(new Set<string>());
  const [interventionsChecked, setInterventionsChecked] = useState(new Set<string>());
  const [painScore, setPainScore] = useState("");
  const [newsScore, setNewsScore] = useState("");
  const [handoverNotes, setHandoverNotes] = useState("");

  function toggle(set: Set<string>, setFn: (s: Set<string>) => void, id: string) {
    const next = new Set(set);
    if (next.has(id)) next.delete(id); else next.add(id);
    setFn(next);
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
        <Heart className="w-4 h-4 text-pink-500" /> Nursing Assessment & Care
      </h3>

      <div className="grid grid-cols-2 gap-3">
        <DropdownField label="Pain score (0-10)" value={painScore} onChange={setPainScore}
          options={["0 - None", "1", "2", "3 - Mild", "4", "5 - Moderate", "6", "7 - Severe", "8", "9", "10 - Worst"]} />
        <DropdownField label="NEWS2 score" value={newsScore} onChange={setNewsScore}
          options={["0 - Low risk", "1-4 - Low risk", "5-6 - Medium risk", "7+ - High risk"]} />
      </div>

      <ChecklistSection title="Nursing Assessment" icon={ClipboardCheck}
        items={NURSE_ASSESSMENT} checked={assessChecked}
        onToggle={(id) => toggle(assessChecked, setAssessChecked, id)} />

      <ChecklistSection title="Interventions Performed" icon={Activity}
        items={NURSE_INTERVENTIONS} checked={interventionsChecked}
        onToggle={(id) => toggle(interventionsChecked, setInterventionsChecked, id)} />

      <label className="block">
        <span className="text-xs font-medium text-gray-600">Handover / shift notes</span>
        <textarea value={handoverNotes} onChange={(e) => setHandoverNotes(e.target.value)} rows={3}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          placeholder="Key points for the next shift..." />
      </label>
    </div>
  );
}

// ── PHARMACIST ────────────────────────────────────────────────────
const PHARMACIST_CHECKS: CheckItem[] = [
  { id: "prescription_valid", label: "Prescription valid and legible" },
  { id: "dose_appropriate", label: "Dose appropriate for age/weight" },
  { id: "interactions_checked", label: "Drug interactions checked" },
  { id: "allergy_checked", label: "Allergy history verified" },
  { id: "duplicate_checked", label: "Duplicate therapy checked" },
  { id: "renal_hepatic", label: "Renal/hepatic adjustment considered" },
  { id: "formulary_compliant", label: "On formulary / EDLIZ-compliant" },
  { id: "patient_counselled", label: "Patient counselled on use" },
  { id: "storage_advised", label: "Storage instructions given" },
  { id: "follow_up_advised", label: "Follow-up advice given" },
];

export function PharmacistEncounterForm({ onDataChange }: { onDataChange: (data: Record<string, unknown>) => void }) {
  void onDataChange;
  const [checksChecked, setChecksChecked] = useState(new Set<string>());
  const [clinicalIntervention, setClinicalIntervention] = useState("");
  const [interventionType, setInterventionType] = useState("");
  const [substitution, setSubstitution] = useState("");

  function toggle(set: Set<string>, setFn: (s: Set<string>) => void, id: string) {
    const next = new Set(set);
    if (next.has(id)) next.delete(id); else next.add(id);
    setFn(next);
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
        <Pill className="w-4 h-4 text-green-500" /> Pharmacist Verification & Counselling
      </h3>

      <ChecklistSection title="Dispensing Checks" icon={ClipboardCheck}
        items={PHARMACIST_CHECKS} checked={checksChecked}
        onToggle={(id) => toggle(checksChecked, setChecksChecked, id)} />

      <DropdownField label="Clinical intervention" value={interventionType} onChange={setInterventionType}
        options={["None", "Dose adjustment", "Drug substitution", "Interaction warning", "Allergy alert", "Therapeutic duplication", "Patient education", "Prescriber contacted"]} />

      {interventionType && interventionType !== "None" && (
        <label className="block">
          <span className="text-xs font-medium text-gray-600">Intervention details</span>
          <textarea value={clinicalIntervention} onChange={(e) => setClinicalIntervention(e.target.value)} rows={2}
            className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="Describe the intervention and outcome..." />
        </label>
      )}

      <label className="block">
        <span className="text-xs font-medium text-gray-600">Substitution notes</span>
        <input value={substitution} onChange={(e) => setSubstitution(e.target.value)}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          placeholder="If generic/therapeutic substitution was made..." />
      </label>
    </div>
  );
}

// ── MIDWIFE / ANC ─────────────────────────────────────────────────
const MIDWIFE_ANC_CHECKS: CheckItem[] = [
  { id: "bp_recorded", label: "Blood pressure recorded" },
  { id: "weight_recorded", label: "Weight recorded" },
  { id: "fundal_height", label: "Fundal height measured" },
  { id: "fetal_heart", label: "Fetal heart rate checked" },
  { id: "fetal_movement", label: "Fetal movement confirmed" },
  { id: "urine_protein", label: "Urine protein tested" },
  { id: "urine_glucose", label: "Urine glucose tested" },
  { id: "haemoglobin", label: "Haemoglobin checked" },
  { id: "hiv_status", label: "HIV status confirmed" },
  { id: "syphilis_screen", label: "Syphilis screening done" },
  { id: "tetanus_vaccine", label: "Tetanus vaccine status" },
  { id: "iron_folate", label: "Iron/folate dispensed" },
  { id: "danger_signs", label: "Danger signs education given" },
  { id: "birth_plan", label: "Birth plan discussed" },
];

export function MidwifeEncounterForm({ onDataChange }: { onDataChange: (data: Record<string, unknown>) => void }) {
  void onDataChange;
  const [ancChecked, setAncChecked] = useState(new Set<string>());
  const [gestationalAge, setGestationalAge] = useState("");
  const [gravida, setGravida] = useState("");
  const [para, setPara] = useState("");
  const [presentation, setPresentation] = useState("");
  const [riskLevel, setRiskLevel] = useState("");

  function toggle(set: Set<string>, setFn: (s: Set<string>) => void, id: string) {
    const next = new Set(set);
    if (next.has(id)) next.delete(id); else next.add(id);
    setFn(next);
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
        <Baby className="w-4 h-4 text-pink-500" /> Antenatal / Midwifery Assessment
      </h3>

      <div className="grid grid-cols-3 gap-3">
        <label className="block">
          <span className="text-xs font-medium text-gray-600">Gestational age (weeks)</span>
          <input type="number" value={gestationalAge} onChange={(e) => setGestationalAge(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. 28" />
        </label>
        <label className="block">
          <span className="text-xs font-medium text-gray-600">Gravida</span>
          <input type="number" value={gravida} onChange={(e) => setGravida(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="G" />
        </label>
        <label className="block">
          <span className="text-xs font-medium text-gray-600">Para</span>
          <input type="number" value={para} onChange={(e) => setPara(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="P" />
        </label>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <DropdownField label="Presentation" value={presentation} onChange={setPresentation}
          options={["Cephalic", "Breech", "Transverse", "Oblique", "Unstable lie", "Not assessed"]} />
        <DropdownField label="Risk level" value={riskLevel} onChange={setRiskLevel}
          options={["Low risk", "Medium risk", "High risk"]} />
      </div>

      <ChecklistSection title="ANC Visit Checklist" icon={ClipboardCheck}
        items={MIDWIFE_ANC_CHECKS} checked={ancChecked}
        onToggle={(id) => toggle(ancChecked, setAncChecked, id)} />
    </div>
  );
}

// ── Role selector ─────────────────────────────────────────────────

// ── Allied & Specialist Forms (imported from sub-module) ─────────
import {
  PhysiotherapyForm, OccupationalTherapyForm, PsychologyForm,
  NutritionForm, SocialWorkerForm, SpeechTherapyForm,
  RadiographerForm, LabTechForm, OptometristForm, DentalForm,
  EMTForm, CHWForm, EHOForm, RespiratoryTherapistForm,
  AudiologistForm, PodiatristForm, RadiotherapyForm,
} from "./forms/AlliedHealthForms";

const ROLE_FORM_MAP: Record<string, React.ComponentType> = {
  CLINICIAN: () => <DoctorEncounterForm onDataChange={() => {}} />,
  DOCTOR: () => <DoctorEncounterForm onDataChange={() => {}} />,
  NURSE: () => <NurseEncounterForm onDataChange={() => {}} />,
  PHARMACIST: () => <PharmacistEncounterForm onDataChange={() => {}} />,
  MIDWIFE: () => <MidwifeEncounterForm onDataChange={() => {}} />,
  ANC: () => <MidwifeEncounterForm onDataChange={() => {}} />,
  PHYSIOTHERAPIST: PhysiotherapyForm,
  OCCUPATIONAL_THERAPIST: OccupationalTherapyForm,
  PSYCHOLOGIST: PsychologyForm,
  COUNSELLOR: PsychologyForm,
  NUTRITIONIST: NutritionForm,
  DIETITIAN: NutritionForm,
  SOCIAL_WORKER: SocialWorkerForm,
  SPEECH_THERAPIST: SpeechTherapyForm,
  SLT: SpeechTherapyForm,
  RADIOGRAPHER: RadiographerForm,
  IMAGING_TECH: RadiographerForm,
  LAB_TECHNOLOGIST: LabTechForm,
  LAB_TECH: LabTechForm,
  OPTOMETRIST: OptometristForm,
  DENTIST: DentalForm,
  ORAL_HYGIENIST: DentalForm,
  EMT: EMTForm,
  PARAMEDIC: EMTForm,
  CHW: CHWForm,
  COMMUNITY_HEALTH_WORKER: CHWForm,
  EHO: EHOForm,
  ENVIRONMENTAL_HEALTH: EHOForm,
  RESPIRATORY_THERAPIST: RespiratoryTherapistForm,
  AUDIOLOGIST: AudiologistForm,
  PODIATRIST: PodiatristForm,
  RADIOTHERAPIST: RadiotherapyForm,
  RADIATION_THERAPIST: RadiotherapyForm,
};

export function RoleSpecificEncounterForm({ role, onDataChange }: {
  role: string;
  onDataChange: (data: Record<string, unknown>) => void;
}) {
  const FormComponent = ROLE_FORM_MAP[role];
  if (FormComponent) return <FormComponent />;
  // Default to doctor form for unrecognised roles
  return <DoctorEncounterForm onDataChange={onDataChange} />;
}

/** All supported clinical roles for UI display */
export const CLINICAL_ROLE_LABELS: Record<string, string> = {
  CLINICIAN: "Clinician / Doctor",
  NURSE: "Nurse",
  PHARMACIST: "Pharmacist",
  MIDWIFE: "Midwife",
  PHYSIOTHERAPIST: "Physiotherapist",
  OCCUPATIONAL_THERAPIST: "Occupational Therapist",
  PSYCHOLOGIST: "Psychologist / Counsellor",
  NUTRITIONIST: "Nutritionist / Dietitian",
  SOCIAL_WORKER: "Social Worker",
  SPEECH_THERAPIST: "Speech & Language Therapist",
  RADIOGRAPHER: "Radiographer / Imaging Tech",
  LAB_TECHNOLOGIST: "Lab Technologist",
  OPTOMETRIST: "Optometrist",
  DENTIST: "Dentist / Oral Hygienist",
  EMT: "EMT / Paramedic",
  CHW: "Community Health Worker",
  EHO: "Environmental Health Officer",
  RESPIRATORY_THERAPIST: "Respiratory Therapist",
  AUDIOLOGIST: "Audiologist",
  PODIATRIST: "Podiatrist",
  RADIOTHERAPIST: "Radiotherapist / Radiation Therapist",
};
