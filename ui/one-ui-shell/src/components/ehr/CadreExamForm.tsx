"use client";

/**
 * Cadre-Adaptive Physical Examination Form
 *
 * Doctor: Full systems examination (General, CVS, Resp, Abdo, Neuro, MSK, ENT, Derm)
 * Nurse: Focused nursing assessment (vitals, consciousness, danger signs, wound)
 * CHW: Danger sign physical check (AVPU, breathing, dehydration, pallor)
 */

import { useState } from "react";
import {
  User, Heart, Wind, Activity, Brain, Eye, Bone,
  CheckCircle2, AlertTriangle, ShieldAlert, Stethoscope, Baby,
} from "lucide-react";
import { type CadreFormConfig } from "@/hooks/useCadreFormConfig";

interface CadreExamFormProps {
  config: CadreFormConfig;
  onSave?: (data: Record<string, string>) => void;
}

export function CadreExamForm({ config, onSave }: CadreExamFormProps) {
  if (config.complexity === "simplified") return <CHWExamScreen config={config} onSave={onSave} />;
  if (config.complexity === "focused") return <NursingExamForm config={config} onSave={onSave} />;
  return <DoctorExamForm config={config} onSave={onSave} />;
}

// ── Shared Components ─────────────────────────
function SelectField({ label, value, onChange, options }: {
  label: string; value: string; onChange: (v: string) => void; options: string[];
}) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">{label}</label>
      <select value={value} onChange={(e) => onChange(e.target.value)}
        className="w-full h-10 px-3 text-sm rounded-md border border-gray-300 bg-white focus:ring-2 focus:ring-violet-500">
        {options.map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
    </div>
  );
}

function NumberField({ label, value, onChange, unit }: {
  label: string; value: string; onChange: (v: string) => void; unit?: string;
}) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">{label}</label>
      <div className="flex items-center gap-2">
        <input type="number" value={value} onChange={(e) => onChange(e.target.value)}
          className="w-full h-10 px-3 text-sm rounded-md border border-gray-300 bg-white tabular-nums" />
        {unit && <span className="text-xs text-gray-500 shrink-0">{unit}</span>}
      </div>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// CHW DANGER SIGN PHYSICAL CHECK
// ══════════════════════════════════════════════════════
function CHWExamScreen({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, string>) => void }) {
  const [findings, setFindings] = useState<Record<string, string>>({
    avpu: "", breathing: "", dehydration: "", pallor: "", fever: "", rash: "", swelling: "",
  });

  const update = (k: string, v: string) => setFindings((prev) => ({ ...prev, [k]: v }));

  const hasAbnormal = Object.entries(findings).some(([k, v]) => {
    if (k === "avpu" && v && v !== "Alert") return true;
    if (k === "breathing" && v === "Difficulty") return true;
    if (k === "dehydration" && v && v !== "None") return true;
    if (k === "pallor" && v && v !== "None") return true;
    if (k === "fever" && v === "Yes — hot to touch") return true;
    return false;
  });

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-violet-200 bg-violet-50 p-4 flex items-center gap-3">
        <ShieldAlert className="w-6 h-6 text-violet-600" />
        <div>
          <p className="font-semibold text-base">Quick Physical Check</p>
          <p className="text-sm text-gray-500">Check for danger signs — refer immediately if found</p>
        </div>
      </div>

      <div className={`rounded-lg border p-4 space-y-4 ${hasAbnormal ? "border-red-300" : "border-gray-200"}`}>
        {[
          { label: "AVPU Scale", field: "avpu", options: ["Alert", "Voice responsive", "Pain responsive", "Unresponsive"] },
          { label: "Breathing", field: "breathing", options: ["Normal", "Fast", "Difficulty", "Not breathing"] },
          { label: "Dehydration Signs", field: "dehydration", options: ["None", "Dry mouth", "Sunken eyes", "Skin pinch slow", "Severe"] },
          { label: "Pallor (palm/conjunctiva)", field: "pallor", options: ["None", "Mild", "Severe"] },
          { label: "Fever (touch)", field: "fever", options: ["No", "Yes — hot to touch"] },
          { label: "Rash or Skin Problem", field: "rash", options: ["None", "Rash present", "Wound / ulcer", "Swelling"] },
          { label: "Oedema / Swelling", field: "swelling", options: ["None", "Feet/ankles", "Face", "Generalised"] },
        ].map((item) => (
          <div key={item.field}>
            <label className="text-sm font-semibold mb-2 block">{item.label}</label>
            <div className="flex flex-wrap gap-2">
              {item.options.map((opt) => (
                <button key={opt} onClick={() => update(item.field, opt)}
                  className={`px-4 py-2.5 rounded-lg text-sm font-medium transition-all ${
                    findings[item.field] === opt
                      ? (opt === item.options[0] || opt === "No" || opt === "None")
                        ? "bg-green-100 border-2 border-green-500 text-green-700"
                        : "bg-red-100 border-2 border-red-500 text-red-700"
                      : "bg-gray-100 border-2 border-transparent hover:bg-gray-200"
                  }`}
                >{opt}</button>
              ))}
            </div>
          </div>
        ))}
      </div>

      <button className="w-full h-14 text-lg font-bold bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors"
        onClick={() => onSave?.(findings)}>
        {config.labels.saveLabel}
      </button>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// NURSE FOCUSED ASSESSMENT
// ══════════════════════════════════════════════════════
function NursingExamForm({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, string>) => void }) {
  const [findings, setFindings] = useState<Record<string, string>>({
    consciousness: "Alert", distress: "Not in distress", hydration: "Well hydrated",
    pallor: "Absent", jaundice: "Absent", cyanosis: "Absent", edema: "Absent",
    resp_effort: "Normal", chest_sounds: "Clear", abdo: "Soft, non-tender",
    wound: "None", mobility: "Independent", pain_score: "0",
  });

  const update = (k: string, v: string) => setFindings((prev) => ({ ...prev, [k]: v }));

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-violet-200 bg-violet-50 p-4 flex items-center gap-3">
        <Stethoscope className="w-6 h-6 text-violet-600" />
        <div>
          <p className="font-semibold text-base">Nursing Physical Assessment</p>
          <p className="text-sm text-gray-500">Focused examination — flag abnormalities for medical review</p>
        </div>
      </div>

      {/* General Appearance */}
      <div className="rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
          <h3 className="text-base font-semibold flex items-center gap-2"><User className="w-5 h-5" />General Appearance</h3>
        </div>
        <div className="p-4 grid grid-cols-2 gap-3">
          <SelectField label="Consciousness" value={findings.consciousness} onChange={(v) => update("consciousness", v)}
            options={["Alert", "Drowsy", "Confused", "Unresponsive"]} />
          <SelectField label="Distress" value={findings.distress} onChange={(v) => update("distress", v)}
            options={["Not in distress", "Mild distress", "Moderate distress", "Severe distress"]} />
          <SelectField label="Hydration" value={findings.hydration} onChange={(v) => update("hydration", v)}
            options={["Well hydrated", "Dry mucous membranes", "Poor skin turgor", "Severely dehydrated"]} />
          <SelectField label="Pallor" value={findings.pallor} onChange={(v) => update("pallor", v)}
            options={["Absent", "Mild", "Moderate", "Severe"]} />
          <SelectField label="Jaundice" value={findings.jaundice} onChange={(v) => update("jaundice", v)}
            options={["Absent", "Present"]} />
          <SelectField label="Oedema" value={findings.edema} onChange={(v) => update("edema", v)}
            options={["Absent", "Ankles", "Knees", "Sacral", "Generalised"]} />
        </div>
      </div>

      {/* Quick Systems */}
      <div className="rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
          <h3 className="text-base font-semibold">Focused Systems Check</h3>
        </div>
        <div className="p-4 grid grid-cols-2 gap-3">
          <SelectField label="Respiratory Effort" value={findings.resp_effort} onChange={(v) => update("resp_effort", v)}
            options={["Normal", "Tachypnoea", "Accessory muscles", "Laboured", "Apnoeic spells"]} />
          <SelectField label="Chest Sounds" value={findings.chest_sounds} onChange={(v) => update("chest_sounds", v)}
            options={["Clear", "Crackles", "Wheeze", "Reduced air entry", "Stridor"]} />
          <SelectField label="Abdomen" value={findings.abdo} onChange={(v) => update("abdo", v)}
            options={["Soft, non-tender", "Tender", "Distended", "Rigid", "Mass palpable"]} />
          <SelectField label="Wound / Skin" value={findings.wound} onChange={(v) => update("wound", v)}
            options={["None", "Healing well", "Signs of infection", "Dehiscence", "Pressure injury"]} />
          <SelectField label="Mobility" value={findings.mobility} onChange={(v) => update("mobility", v)}
            options={["Independent", "Needs assistance", "Bed-bound", "Fall risk"]} />
          <SelectField label="Pain Score (0-10)" value={findings.pain_score} onChange={(v) => update("pain_score", v)}
            options={Array.from({ length: 11 }, (_, i) => `${i}`)} />
        </div>
      </div>

      {/* GCS if high acuity */}
      {config.exam.showGCS && (
        <div className="rounded-lg border border-gray-200 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
            <h3 className="text-base font-semibold flex items-center gap-2"><Brain className="w-5 h-5" />Glasgow Coma Scale</h3>
          </div>
          <div className="p-4">
            <div className="grid grid-cols-3 gap-3">
              <SelectField label="Eye (E)" value={findings.gcs_eye || "4"} onChange={(v) => update("gcs_eye", v)}
                options={["1", "2", "3", "4"]} />
              <SelectField label="Verbal (V)" value={findings.gcs_verbal || "5"} onChange={(v) => update("gcs_verbal", v)}
                options={["1", "2", "3", "4", "5"]} />
              <SelectField label="Motor (M)" value={findings.gcs_motor || "6"} onChange={(v) => update("gcs_motor", v)}
                options={["1", "2", "3", "4", "5", "6"]} />
            </div>
            <div className="mt-2 text-center">
              <span className={`inline-block text-base font-bold px-4 py-1 rounded-full ${
                (parseInt(findings.gcs_eye || "4") + parseInt(findings.gcs_verbal || "5") + parseInt(findings.gcs_motor || "6")) >= 13
                  ? "bg-violet-100 text-violet-700" : "bg-red-100 text-red-700"
              }`}>
                GCS {(parseInt(findings.gcs_eye || "4") + parseInt(findings.gcs_verbal || "5") + parseInt(findings.gcs_motor || "6"))}/15
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Obstetric if ANC */}
      {config.exam.showObstetricExam && (
        <div className="rounded-lg border border-gray-200 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
            <h3 className="text-base font-semibold flex items-center gap-2"><Baby className="w-5 h-5" />Obstetric Examination</h3>
          </div>
          <div className="p-4 grid grid-cols-2 gap-3">
            <NumberField label="Fundal Height" value={findings.fundal_height || ""} onChange={(v) => update("fundal_height", v)} unit="cm" />
            <SelectField label="Lie" value={findings.lie || ""} onChange={(v) => update("lie", v)}
              options={["", "Longitudinal", "Transverse", "Oblique"]} />
            <SelectField label="Presentation" value={findings.presentation || ""} onChange={(v) => update("presentation", v)}
              options={["", "Cephalic", "Breech", "Shoulder", "Uncertain"]} />
            <SelectField label="Fetal Heart" value={findings.fetal_heart || ""} onChange={(v) => update("fetal_heart", v)}
              options={["", "Present — regular", "Present — irregular", "Not heard"]} />
            <NumberField label="FHR" value={findings.fhr || ""} onChange={(v) => update("fhr", v)} unit="bpm" />
            <SelectField label="Contractions" value={findings.contractions || ""} onChange={(v) => update("contractions", v)}
              options={["", "None", "Irregular", "Regular — mild", "Regular — strong"]} />
          </div>
        </div>
      )}

      <button className="w-full h-12 text-base font-semibold bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors flex items-center justify-center gap-2"
        onClick={() => onSave?.(findings)}>
        <CheckCircle2 className="w-5 h-5" />
        {config.labels.saveLabel}
      </button>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// DOCTOR COMPREHENSIVE SYSTEMS EXAMINATION
// ══════════════════════════════════════════════════════
function DoctorExamForm({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, string>) => void }) {
  const [findings, setFindings] = useState<Record<string, string>>({
    consciousness: "Alert", distress: "Not in distress", build: "Average",
    hydration: "Well hydrated", pallor: "Absent", jaundice: "Absent",
    cyanosis: "Absent", clubbing: "Absent", edema: "Absent", lymphadenopathy: "Absent",
    pulse_rate: "78", pulse_rhythm: "Regular", pulse_volume: "Normal",
    bp_systolic: "130", bp_diastolic: "82", jvp: "Not raised",
    heart_sounds: "S1 S2 normal", murmurs: "None",
    resp_rate: "18", breathing_pattern: "Normal", trachea: "Central",
    chest_expansion: "Symmetrical", percussion: "Resonant bilaterally",
    breath_sounds: "Vesicular", added_sounds: "None", spo2: "97",
    abdo_shape: "Flat", abdo_tenderness: "None", abdo_guarding: "Absent",
    abdo_rigidity: "Absent", abdo_rebound: "Absent", liver: "Not palpable",
    spleen: "Not palpable", bowel_sounds: "Normal", ascites: "Absent",
    gcs_eye: "4", gcs_verbal: "5", gcs_motor: "6",
    pupils: "Equal and reactive", focal_deficit: "None",
    power_upper: "5/5", power_lower: "5/5", tone: "Normal",
    reflexes: "Normal", meningism: "Absent",
  });

  const update = (k: string, v: string) => setFindings((prev) => ({ ...prev, [k]: v }));
  const gcsTotal = (parseInt(findings.gcs_eye) || 0) + (parseInt(findings.gcs_verbal) || 0) + (parseInt(findings.gcs_motor) || 0);

  const SectionCard = ({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) => (
    <div className="rounded-lg border border-gray-200 overflow-hidden">
      <div className="px-4 py-3 border-b border-gray-200 bg-gray-50 flex items-center justify-between">
        <h3 className="text-base font-semibold flex items-center gap-2">{icon}{title}</h3>
      </div>
      <div className="p-4">{children}</div>
    </div>
  );

  return (
    <div className="space-y-4">
      {/* General */}
      <SectionCard title="General Examination" icon={<User className="w-5 h-5" />}>
        <div className="grid grid-cols-3 gap-3">
          <SelectField label="Consciousness" value={findings.consciousness} onChange={(v) => update("consciousness", v)}
            options={["Alert", "Drowsy", "Confused", "Obtunded", "Comatose"]} />
          <SelectField label="Distress" value={findings.distress} onChange={(v) => update("distress", v)}
            options={["Not in distress", "Mild distress", "Moderate distress", "Severe distress"]} />
          <SelectField label="Build" value={findings.build} onChange={(v) => update("build", v)}
            options={["Wasted", "Thin", "Average", "Overweight", "Obese"]} />
          <SelectField label="Hydration" value={findings.hydration} onChange={(v) => update("hydration", v)}
            options={["Well hydrated", "Mild dehydration", "Moderate dehydration", "Severe dehydration"]} />
          <SelectField label="Pallor" value={findings.pallor} onChange={(v) => update("pallor", v)}
            options={["Absent", "Mild", "Moderate", "Severe"]} />
          <SelectField label="Jaundice" value={findings.jaundice} onChange={(v) => update("jaundice", v)}
            options={["Absent", "Present"]} />
          <SelectField label="Cyanosis" value={findings.cyanosis} onChange={(v) => update("cyanosis", v)}
            options={["Absent", "Peripheral", "Central"]} />
          <SelectField label="Clubbing" value={findings.clubbing} onChange={(v) => update("clubbing", v)}
            options={["Absent", "Present"]} />
          <SelectField label="Oedema" value={findings.edema} onChange={(v) => update("edema", v)}
            options={["Absent", "Pitting - ankles", "Pitting - knees", "Pitting - sacral", "Generalised", "Non-pitting"]} />
          <SelectField label="Lymphadenopathy" value={findings.lymphadenopathy} onChange={(v) => update("lymphadenopathy", v)}
            options={["Absent", "Cervical", "Axillary", "Inguinal", "Generalised"]} />
        </div>
      </SectionCard>

      {/* CVS */}
      <SectionCard title="Cardiovascular System" icon={<Heart className="w-5 h-5" />}>
        <div className="grid grid-cols-3 gap-3">
          <NumberField label="Pulse Rate" value={findings.pulse_rate} onChange={(v) => update("pulse_rate", v)} unit="bpm" />
          <SelectField label="Rhythm" value={findings.pulse_rhythm} onChange={(v) => update("pulse_rhythm", v)}
            options={["Regular", "Regularly irregular", "Irregularly irregular"]} />
          <SelectField label="Volume" value={findings.pulse_volume} onChange={(v) => update("pulse_volume", v)}
            options={["Normal", "Bounding", "Thready", "Weak"]} />
          <NumberField label="Systolic BP" value={findings.bp_systolic} onChange={(v) => update("bp_systolic", v)} unit="mmHg" />
          <NumberField label="Diastolic BP" value={findings.bp_diastolic} onChange={(v) => update("bp_diastolic", v)} unit="mmHg" />
          <SelectField label="JVP" value={findings.jvp} onChange={(v) => update("jvp", v)}
            options={["Not raised", "Raised", "Not visible"]} />
          <SelectField label="Heart Sounds" value={findings.heart_sounds} onChange={(v) => update("heart_sounds", v)}
            options={["S1 S2 normal", "S3 present", "S4 present", "Muffled", "Loud S2"]} />
          <SelectField label="Murmurs" value={findings.murmurs} onChange={(v) => update("murmurs", v)}
            options={["None", "Systolic - apex", "Systolic - LLSE", "Diastolic", "Pan-systolic", "Ejection systolic"]} />
        </div>
      </SectionCard>

      {/* Respiratory */}
      <SectionCard title="Respiratory System" icon={<Wind className="w-5 h-5" />}>
        <div className="grid grid-cols-3 gap-3">
          <NumberField label="Resp Rate" value={findings.resp_rate} onChange={(v) => update("resp_rate", v)} unit="/min" />
          <SelectField label="Pattern" value={findings.breathing_pattern} onChange={(v) => update("breathing_pattern", v)}
            options={["Normal", "Tachypnoea", "Bradypnoea", "Kussmaul", "Cheyne-Stokes"]} />
          <SelectField label="Trachea" value={findings.trachea} onChange={(v) => update("trachea", v)}
            options={["Central", "Deviated left", "Deviated right"]} />
          <SelectField label="Chest Expansion" value={findings.chest_expansion} onChange={(v) => update("chest_expansion", v)}
            options={["Symmetrical", "Reduced left", "Reduced right", "Reduced bilaterally"]} />
          <SelectField label="Percussion" value={findings.percussion} onChange={(v) => update("percussion", v)}
            options={["Resonant bilaterally", "Dull left", "Dull right", "Dull bilaterally", "Stony dull", "Hyperresonant"]} />
          <SelectField label="Breath Sounds" value={findings.breath_sounds} onChange={(v) => update("breath_sounds", v)}
            options={["Vesicular", "Bronchial", "Reduced left", "Reduced right", "Reduced bilaterally", "Absent"]} />
          <SelectField label="Added Sounds" value={findings.added_sounds} onChange={(v) => update("added_sounds", v)}
            options={["None", "Fine crackles", "Coarse crackles", "Wheeze", "Pleural rub", "Stridor"]} />
          <NumberField label="SpO2" value={findings.spo2} onChange={(v) => update("spo2", v)} unit="%" />
        </div>
      </SectionCard>

      {/* Abdominal */}
      <SectionCard title="Abdominal Examination" icon={<Activity className="w-5 h-5" />}>
        <div className="grid grid-cols-3 gap-3">
          <SelectField label="Shape" value={findings.abdo_shape} onChange={(v) => update("abdo_shape", v)}
            options={["Flat", "Distended", "Scaphoid", "Obese"]} />
          <SelectField label="Tenderness" value={findings.abdo_tenderness} onChange={(v) => update("abdo_tenderness", v)}
            options={["None", "RUQ", "RLQ", "LUQ", "LLQ", "Epigastric", "Suprapubic", "Generalised"]} />
          <SelectField label="Guarding" value={findings.abdo_guarding} onChange={(v) => update("abdo_guarding", v)}
            options={["Absent", "Voluntary", "Involuntary"]} />
          <SelectField label="Rigidity" value={findings.abdo_rigidity} onChange={(v) => update("abdo_rigidity", v)}
            options={["Absent", "Present"]} />
          <SelectField label="Rebound" value={findings.abdo_rebound} onChange={(v) => update("abdo_rebound", v)}
            options={["Absent", "Present"]} />
          <SelectField label="Liver" value={findings.liver} onChange={(v) => update("liver", v)}
            options={["Not palpable", "Palpable - smooth", "Palpable - irregular", "Tender"]} />
          <SelectField label="Spleen" value={findings.spleen} onChange={(v) => update("spleen", v)}
            options={["Not palpable", "1 finger", "2 fingers", "3 fingers", "Massive"]} />
          <SelectField label="Bowel Sounds" value={findings.bowel_sounds} onChange={(v) => update("bowel_sounds", v)}
            options={["Normal", "Hyperactive", "Hypoactive", "Absent", "Tinkling"]} />
          <SelectField label="Ascites" value={findings.ascites} onChange={(v) => update("ascites", v)}
            options={["Absent", "Mild", "Moderate", "Tense"]} />
        </div>
      </SectionCard>

      {/* Neurological */}
      <SectionCard title="Neurological Examination" icon={<Brain className="w-5 h-5" />}>
        <div className="space-y-4">
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">Glasgow Coma Scale</span>
              <span className={`text-sm font-bold px-3 py-0.5 rounded-full ${
                gcsTotal >= 13 ? "bg-violet-100 text-violet-700" : gcsTotal >= 9 ? "bg-gray-100 text-gray-700" : "bg-red-100 text-red-700"
              }`}>
                GCS {gcsTotal}/15
              </span>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <SelectField label="Eye (E)" value={findings.gcs_eye} onChange={(v) => update("gcs_eye", v)} options={["1", "2", "3", "4"]} />
              <SelectField label="Verbal (V)" value={findings.gcs_verbal} onChange={(v) => update("gcs_verbal", v)} options={["1", "2", "3", "4", "5"]} />
              <SelectField label="Motor (M)" value={findings.gcs_motor} onChange={(v) => update("gcs_motor", v)} options={["1", "2", "3", "4", "5", "6"]} />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <SelectField label="Pupils" value={findings.pupils} onChange={(v) => update("pupils", v)}
              options={["Equal and reactive", "Unequal", "Fixed dilated", "Fixed constricted", "Sluggish"]} />
            <SelectField label="Focal Deficit" value={findings.focal_deficit} onChange={(v) => update("focal_deficit", v)}
              options={["None", "Left hemiparesis", "Right hemiparesis", "Paraparesis", "Cranial nerve palsy"]} />
            <SelectField label="Meningism" value={findings.meningism} onChange={(v) => update("meningism", v)}
              options={["Absent", "Neck stiffness", "Kernig positive", "Brudzinski positive"]} />
            <SelectField label="Upper Limb Power" value={findings.power_upper} onChange={(v) => update("power_upper", v)}
              options={["0/5", "1/5", "2/5", "3/5", "4/5", "5/5"]} />
            <SelectField label="Lower Limb Power" value={findings.power_lower} onChange={(v) => update("power_lower", v)}
              options={["0/5", "1/5", "2/5", "3/5", "4/5", "5/5"]} />
            <SelectField label="Tone" value={findings.tone} onChange={(v) => update("tone", v)}
              options={["Normal", "Increased - spasticity", "Increased - rigidity", "Decreased", "Flaccid"]} />
            <SelectField label="Reflexes" value={findings.reflexes} onChange={(v) => update("reflexes", v)}
              options={["Normal", "Hyperreflexic", "Hyporeflexic", "Absent", "Clonus present"]} />
          </div>
        </div>
      </SectionCard>

      {/* MSK if applicable */}
      {config.exam.showMusculoskeletal && (
        <SectionCard title="Musculoskeletal" icon={<Bone className="w-5 h-5" />}>
          <div className="grid grid-cols-3 gap-3">
            <SelectField label="Gait" value={findings.gait || "Normal"} onChange={(v) => update("gait", v)}
              options={["Normal", "Antalgic", "Ataxic", "Hemiplegic", "Shuffling", "Waddling", "Unable"]} />
            <SelectField label="Spine" value={findings.spine || "Normal"} onChange={(v) => update("spine", v)}
              options={["Normal", "Tenderness", "Reduced ROM", "Deformity", "Kyphosis", "Scoliosis"]} />
            <SelectField label="Joint Swelling" value={findings.joint_swelling || "None"} onChange={(v) => update("joint_swelling", v)}
              options={["None", "Small joints", "Large joints", "Multiple", "Single joint"]} />
          </div>
        </SectionCard>
      )}

      {/* ENT if applicable */}
      {config.exam.showENT && (
        <SectionCard title="ENT & Eyes" icon={<Eye className="w-5 h-5" />}>
          <div className="grid grid-cols-3 gap-3">
            <SelectField label="Throat" value={findings.throat || "Normal"} onChange={(v) => update("throat", v)}
              options={["Normal", "Erythema", "Exudate", "Ulceration", "Tonsillar enlargement"]} />
            <SelectField label="Ears" value={findings.ears || "Normal"} onChange={(v) => update("ears", v)}
              options={["Normal", "Otitis externa", "Otitis media", "Effusion", "Perforation"]} />
            <SelectField label="Eyes" value={findings.eyes || "Normal"} onChange={(v) => update("eyes", v)}
              options={["Normal", "Conjunctivitis", "Jaundice", "Pallor", "Ptosis", "Proptosis"]} />
          </div>
        </SectionCard>
      )}

      <div className="flex justify-end gap-2">
        <button className="px-6 py-3 text-base font-semibold bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors flex items-center gap-2"
          onClick={() => onSave?.(findings)}>
          <CheckCircle2 className="w-5 h-5" />
          {config.labels.saveLabel}
        </button>
      </div>
    </div>
  );
}
