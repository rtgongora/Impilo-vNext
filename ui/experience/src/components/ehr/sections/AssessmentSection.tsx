"use client";

/**
 * AssessmentSection -- Full clinical assessment view with:
 * - Triage panel (category, chief complaint, danger signs, vitals)
 * - History panel (presenting complaint, HPI SOCRATES, PMH, PSH, medications, allergies, social)
 * - Examination panel (general, CVS, respiratory, abdominal, neurological -- all coded, zero free-text)
 * - ICD-10 coded assessment entry, problem list, differential diagnosis, clinical reasoning
 *
 * Uses mock data; production would integrate with VITO / HAPI FHIR.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  AlertTriangle,
  Ambulance,
  Activity,
  Stethoscope,
  FileText,
  Heart,
  Brain,
  Wind,
  User,
  Pill,
  Users,
  CheckCircle2,
  Thermometer,
  Shield,
  TestTube,
  Clock,
  ClipboardList,
  Loader2,
} from "lucide-react";
import { useTriage } from "@/hooks/queries/useTriage";
import { useEncounterVitals } from "@/hooks/queries/useVitals";
import { useEncounterHistory } from "@/hooks/queries/useEncounterHistory";

// ---------------------------------------------------------------------------
// Data fetched via hooks (useTriage, useEncounterVitals, useEncounterHistory)
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Color maps
// ---------------------------------------------------------------------------

const triageColors = {
  red: { bg: "bg-red-600", border: "border-red-600", text: "text-white", label: "Immediate" },
  orange: { bg: "bg-orange-500", border: "border-orange-500", text: "text-white", label: "Very Urgent" },
  yellow: { bg: "bg-yellow-500", border: "border-yellow-500", text: "text-black", label: "Urgent" },
  green: { bg: "bg-green-600", border: "border-green-600", text: "text-white", label: "Standard" },
};

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function TriagePanel({ triage, vitals, isLoading }: { triage: any; vitals: any; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12 text-gray-400">
        <Loader2 className="h-6 w-6 animate-spin mr-2" />
        <span className="text-sm">Loading triage data...</span>
      </div>
    );
  }

  if (!triage) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-gray-400">
        <AlertTriangle className="h-6 w-6 mb-2" />
        <p className="text-sm">No triage data available for this encounter</p>
      </div>
    );
  }

  const color = triageColors[triage.category as keyof typeof triageColors] || triageColors.green;
  const triageTime = new Date(triage.triageTime).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  const arrivalTime = new Date(triage.arrivalTime).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });

  return (
    <div className="space-y-4">
      {/* Triage Category Banner */}
      <div className={`border-2 ${color.border} rounded-lg p-4`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className={`w-12 h-12 rounded-full ${color.bg} flex items-center justify-center`}>
              <AlertTriangle className={`w-6 h-6 ${color.text}`} />
            </div>
            <div>
              <div className="text-lg font-semibold">{color.label}</div>
              <div className="text-sm text-gray-500">
                Triaged at {triageTime} by {triage.triagedBy}
              </div>
            </div>
          </div>
          <div className="text-right">
            <span className="inline-flex items-center gap-1 text-xs border rounded px-2 py-0.5 text-gray-600 border-gray-200 mb-1">
              <Ambulance className="w-3 h-3" />
              {triage.arrivalMode.replace("-", " ")}
            </span>
            <div className="text-xs text-gray-500">Arrived: {arrivalTime}</div>
          </div>
        </div>
      </div>

      {/* Chief Complaint */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold">Chief Complaint</h3>
        </div>
        <div className="p-4">
          <p className="text-gray-900">{triage.chiefComplaint}</p>
          {triage.notes && (
            <p className="text-sm text-gray-500 mt-2">{triage.notes}</p>
          )}
        </div>
      </div>

      {/* Danger Signs */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold">Danger Signs Screening</h3>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-2 gap-2">
            {(triage.dangerSigns ?? []).map((sign: any) => (
              <div
                key={sign.id}
                className={`flex items-center gap-2 p-2 rounded-lg ${
                  sign.present
                    ? "bg-red-50 border border-red-200"
                    : "bg-gray-50"
                }`}
              >
                {sign.present ? (
                  <AlertTriangle className="w-4 h-4 text-red-600" />
                ) : (
                  <CheckCircle2 className="w-4 h-4 text-green-600" />
                )}
                <span className="text-sm">{sign.name}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Triage Vitals */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold">Triage Vitals</h3>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-3 gap-4">
            {[
              { icon: Heart, value: vitals?.heartRate ?? vitals?.attributes?.heartRate ?? "--", unit: "HR (bpm)" },
              { icon: Activity, value: vitals ? `${vitals.bpSystolic ?? vitals.attributes?.systolic ?? "--"}/${vitals.bpDiastolic ?? vitals.attributes?.diastolic ?? "--"}` : "--/--", unit: "BP (mmHg)" },
              { icon: Wind, value: vitals ? `${vitals.spo2 ?? vitals.attributes?.oxygenSaturation ?? "--"}%` : "--%", unit: "SpO2" },
              { icon: Thermometer, value: vitals ? `${vitals.temperature ?? vitals.attributes?.temperature ?? "--"}C` : "--C", unit: "Temp" },
              { icon: Wind, value: vitals?.respRate ?? vitals?.attributes?.respiratoryRate ?? "--", unit: "RR (/min)" },
            ].map((v, i) => {
              const Icon = v.icon;
              return (
                <div key={i} className="text-center p-3 bg-gray-50 rounded-lg">
                  <Icon className="w-5 h-5 mx-auto text-gray-400 mb-1" />
                  <div className="text-xl font-semibold">{v.value}</div>
                  <div className="text-xs text-gray-500">{v.unit}</div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

function HistoryPanel({ history, isLoading }: { history: any; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12 text-gray-400">
        <Loader2 className="h-6 w-6 animate-spin mr-2" />
        <span className="text-sm">Loading history...</span>
      </div>
    );
  }

  if (!history) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-gray-400">
        <FileText className="h-6 w-6 mb-2" />
        <p className="text-sm">No history data available for this encounter</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Presenting Complaint */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <FileText className="w-5 h-5" />
            Presenting Complaint
          </h3>
          <span className="text-[10px] border rounded px-1.5 py-0.5 text-gray-500 border-gray-200">
            ICD-10 coded
          </span>
        </div>
        <div className="p-4">
          <p className="font-medium text-sm">{history.presentingComplaint}</p>
          <span className="inline-block mt-2 text-xs bg-gray-100 text-gray-700 rounded px-2 py-0.5">
            R50.9 -- Fever, unspecified
          </span>
        </div>
      </div>

      {/* HPI -- SOCRATES structured */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <h3 className="text-base font-semibold">History of Present Illness</h3>
          <span className="text-[10px] border rounded px-1.5 py-0.5 text-gray-500 border-gray-200">
            SOCRATES
          </span>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-2 gap-3">
            {[
              { label: "Site", value: "Generalised body aches" },
              { label: "Onset", value: "3 days ago, gradual" },
              { label: "Character", value: "Continuous, dull" },
              { label: "Radiation", value: "None" },
              { label: "Associated Sx", value: "Chills, headache, myalgia" },
              { label: "Timing", value: "Worse at night" },
              { label: "Exacerbating", value: "Activity" },
              { label: "Severity", value: "7/10" },
            ].map((item) => (
              <div key={item.label} className="p-2.5 bg-gray-50 rounded-lg">
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                  {item.label}
                </div>
                <div className="text-sm font-medium mt-0.5">{item.value}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Past Medical History */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Stethoscope className="w-5 h-5" />
            Past Medical History
          </h3>
          <span className="text-[10px] border rounded px-1.5 py-0.5 text-gray-500 border-gray-200">
            ICD-10 / SNOMED CT
          </span>
        </div>
        <div className="p-4 space-y-2">
          {(history.pastMedicalHistory ?? []).map((condition: any) => (
            <div
              key={condition.id}
              className="flex items-center justify-between p-2.5 bg-gray-50 rounded-lg"
            >
              <div className="flex items-center gap-2">
                <span
                  className={`text-xs font-medium rounded px-2 py-0.5 ${
                    condition.status === "active"
                      ? "bg-impilo-500 text-white"
                      : "bg-gray-200 text-gray-600"
                  }`}
                >
                  {condition.status}
                </span>
                <span className="font-medium text-sm">{condition.condition}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-mono border rounded px-1.5 py-0.5 text-gray-500 border-gray-200">
                  {condition.icdCode}
                </span>
                <span className="text-xs text-gray-500">{condition.diagnosed}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Past Surgical History */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold">Past Surgical History</h3>
        </div>
        <div className="p-4 space-y-2">
          {(history.pastSurgicalHistory ?? []).map((surgery: any) => (
            <div
              key={surgery.id}
              className="flex items-center justify-between p-2.5 bg-gray-50 rounded-lg"
            >
              <span className="text-sm font-medium">{surgery.procedure}</span>
              <span className="text-xs text-gray-500">{surgery.date}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Current Medications */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Pill className="w-5 h-5" />
            Current Medications
          </h3>
          <span className="text-[10px] border rounded px-1.5 py-0.5 text-gray-500 border-gray-200">
            SNOMED CT / ATC
          </span>
        </div>
        <div className="p-4 space-y-2">
          {(history.medications ?? []).map((drug: any) => (
            <div
              key={drug.id}
              className="flex items-center justify-between p-2.5 bg-gray-50 rounded-lg"
            >
              <div>
                <span className="font-medium text-sm">{drug.medication}</span>
                <span className="text-gray-500 text-sm ml-2">{drug.dose}</span>
              </div>
              <span className="text-sm text-gray-500">{drug.frequency}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Allergies */}
      <div className="border-2 border-amber-300 rounded-lg">
        <div className="px-4 py-3 border-b border-amber-200">
          <h3 className="text-base font-semibold flex items-center gap-2 text-amber-600">
            <AlertTriangle className="w-5 h-5" />
            Allergies
          </h3>
        </div>
        <div className="p-4 space-y-2">
          {(history.allergies ?? []).map((allergy: any) => (
            <div
              key={allergy.id}
              className={`p-3 rounded-lg ${
                allergy.severity === "life_threatening"
                  ? "bg-red-50 border border-red-200"
                  : "bg-amber-50 border border-amber-200"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-medium text-sm">{allergy.allergen}</span>
                <span
                  className={`text-xs font-medium rounded px-2 py-0.5 ${
                    allergy.severity === "life_threatening"
                      ? "bg-red-600 text-white"
                      : "border border-gray-300 text-gray-600"
                  }`}
                >
                  {allergy.severity.replace("_", " ")}
                </span>
              </div>
              <p className="text-sm text-gray-500 mt-1">{allergy.reaction}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Social History */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Users className="w-5 h-5" />
            Social History
          </h3>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-3 gap-4">
            {[
              { label: "Occupation", value: history.socialHistory?.occupation ?? "--" },
              { label: "Smoking", value: history.socialHistory?.smokingStatus ?? "--" },
              { label: "Alcohol", value: history.socialHistory?.alcoholUse ?? "--" },
            ].map((item) => (
              <div key={item.label} className="p-3 bg-gray-50 rounded-lg">
                <div className="text-xs text-gray-500 mb-1">{item.label}</div>
                <div className="font-medium text-sm capitalize">{item.value}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function ExaminationPanel() {
  const [findings, setFindings] = useState<Record<string, string>>({
    consciousness: "Alert",
    distress: "Not in distress",
    build: "Average",
    hydration: "Well hydrated",
    pallor: "Absent",
    jaundice: "Absent",
    cyanosis: "Absent",
    clubbing: "Absent",
    edema: "Absent",
    lymphadenopathy: "Absent",
    pulse_rate: "78",
    pulse_rhythm: "Regular",
    pulse_volume: "Normal",
    bp_systolic: "130",
    bp_diastolic: "82",
    jvp: "Not raised",
    heart_sounds: "S1 S2 normal",
    murmurs: "None",
    resp_rate: "18",
    breathing_pattern: "Normal",
    trachea: "Central",
    chest_expansion: "Symmetrical",
    percussion: "Resonant bilaterally",
    breath_sounds: "Vesicular",
    added_sounds: "None",
    spo2: "97",
    abdo_shape: "Flat",
    abdo_tenderness: "RUQ tenderness",
    abdo_guarding: "Absent",
    abdo_rigidity: "Absent",
    abdo_rebound: "Absent",
    liver: "Not palpable",
    spleen: "Not palpable",
    bowel_sounds: "Normal",
    ascites: "Absent",
    murphy_sign: "Positive",
    gcs_eye: "4",
    gcs_verbal: "5",
    gcs_motor: "6",
    pupils: "Equal and reactive",
    focal_deficit: "None",
    power_upper: "5/5",
    power_lower: "5/5",
    tone: "Normal",
    reflexes: "Normal",
    meningism: "Absent",
  });

  const updateFinding = (key: string, value: string) => {
    setFindings((prev) => ({ ...prev, [key]: value }));
  };

  const SelectField = ({
    label,
    fieldKey,
    options,
  }: {
    label: string;
    fieldKey: string;
    options: string[];
  }) => (
    <div className="space-y-1">
      <label className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
        {label}
      </label>
      <select
        value={findings[fieldKey]}
        onChange={(e) => updateFinding(fieldKey, e.target.value)}
        className="w-full h-9 px-2 text-sm rounded-md border border-gray-300 bg-white focus:ring-1 focus:ring-impilo-400"
      >
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
    </div>
  );

  const NumberField = ({
    label,
    fieldKey,
    unit,
  }: {
    label: string;
    fieldKey: string;
    unit?: string;
  }) => (
    <div className="space-y-1">
      <label className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
        {label}
      </label>
      <div className="flex items-center gap-2">
        <input
          type="number"
          value={findings[fieldKey]}
          onChange={(e) => updateFinding(fieldKey, e.target.value)}
          className="w-full h-9 px-2 text-sm rounded-md border border-gray-300 bg-white focus:ring-1 focus:ring-impilo-400 tabular-nums"
        />
        {unit && (
          <span className="text-xs text-gray-500 shrink-0">{unit}</span>
        )}
      </div>
    </div>
  );

  const gcsTotal =
    (parseInt(findings.gcs_eye) || 0) +
    (parseInt(findings.gcs_verbal) || 0) +
    (parseInt(findings.gcs_motor) || 0);

  return (
    <div className="space-y-4">
      {/* General Examination */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <User className="w-5 h-5" />
            General Examination
          </h3>
          <span className="text-[10px] border rounded px-1.5 py-0.5 text-gray-500 border-gray-200">
            SNOMED CT coded
          </span>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-3 gap-3">
            <SelectField label="Consciousness" fieldKey="consciousness" options={["Alert", "Drowsy", "Confused", "Obtunded", "Comatose"]} />
            <SelectField label="Distress" fieldKey="distress" options={["Not in distress", "Mild distress", "Moderate distress", "Severe distress"]} />
            <SelectField label="Build" fieldKey="build" options={["Wasted", "Thin", "Average", "Overweight", "Obese"]} />
            <SelectField label="Hydration" fieldKey="hydration" options={["Well hydrated", "Mild dehydration", "Moderate dehydration", "Severe dehydration"]} />
            <SelectField label="Pallor" fieldKey="pallor" options={["Absent", "Mild", "Moderate", "Severe"]} />
            <SelectField label="Jaundice" fieldKey="jaundice" options={["Absent", "Present"]} />
            <SelectField label="Cyanosis" fieldKey="cyanosis" options={["Absent", "Peripheral", "Central"]} />
            <SelectField label="Clubbing" fieldKey="clubbing" options={["Absent", "Present"]} />
            <SelectField label="Oedema" fieldKey="edema" options={["Absent", "Pitting - ankles", "Pitting - knees", "Pitting - sacral", "Generalised", "Non-pitting"]} />
            <SelectField label="Lymphadenopathy" fieldKey="lymphadenopathy" options={["Absent", "Cervical", "Axillary", "Inguinal", "Generalised"]} />
          </div>
        </div>
      </div>

      {/* Cardiovascular */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Heart className="w-5 h-5" />
            Cardiovascular System
          </h3>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-3 gap-3">
            <NumberField label="Pulse Rate" fieldKey="pulse_rate" unit="bpm" />
            <SelectField label="Rhythm" fieldKey="pulse_rhythm" options={["Regular", "Regularly irregular", "Irregularly irregular"]} />
            <SelectField label="Volume" fieldKey="pulse_volume" options={["Normal", "Bounding", "Thready", "Weak"]} />
            <NumberField label="Systolic BP" fieldKey="bp_systolic" unit="mmHg" />
            <NumberField label="Diastolic BP" fieldKey="bp_diastolic" unit="mmHg" />
            <SelectField label="JVP" fieldKey="jvp" options={["Not raised", "Raised", "Not visible"]} />
            <SelectField label="Heart Sounds" fieldKey="heart_sounds" options={["S1 S2 normal", "S3 present", "S4 present", "Muffled", "Loud S2"]} />
            <SelectField label="Murmurs" fieldKey="murmurs" options={["None", "Systolic - apex", "Systolic - LLSE", "Diastolic", "Pan-systolic", "Ejection systolic"]} />
          </div>
        </div>
      </div>

      {/* Respiratory */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Wind className="w-5 h-5" />
            Respiratory System
          </h3>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-3 gap-3">
            <NumberField label="Resp Rate" fieldKey="resp_rate" unit="/min" />
            <SelectField label="Pattern" fieldKey="breathing_pattern" options={["Normal", "Tachypnoea", "Bradypnoea", "Kussmaul", "Cheyne-Stokes"]} />
            <SelectField label="Trachea" fieldKey="trachea" options={["Central", "Deviated left", "Deviated right"]} />
            <SelectField label="Chest Expansion" fieldKey="chest_expansion" options={["Symmetrical", "Reduced left", "Reduced right", "Reduced bilaterally"]} />
            <SelectField label="Percussion" fieldKey="percussion" options={["Resonant bilaterally", "Dull left", "Dull right", "Dull bilaterally", "Stony dull", "Hyperresonant"]} />
            <SelectField label="Breath Sounds" fieldKey="breath_sounds" options={["Vesicular", "Bronchial", "Reduced left", "Reduced right", "Reduced bilaterally", "Absent"]} />
            <SelectField label="Added Sounds" fieldKey="added_sounds" options={["None", "Fine crackles", "Coarse crackles", "Wheeze", "Pleural rub", "Stridor"]} />
            <NumberField label="SpO2" fieldKey="spo2" unit="%" />
          </div>
        </div>
      </div>

      {/* Abdominal */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Activity className="w-5 h-5" />
            Abdominal Examination
          </h3>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-3 gap-3">
            <SelectField label="Shape" fieldKey="abdo_shape" options={["Flat", "Distended", "Scaphoid", "Obese"]} />
            <SelectField label="Tenderness" fieldKey="abdo_tenderness" options={["None", "RUQ tenderness", "RLQ tenderness", "LUQ tenderness", "LLQ tenderness", "Epigastric", "Suprapubic", "Generalised"]} />
            <SelectField label="Guarding" fieldKey="abdo_guarding" options={["Absent", "Voluntary", "Involuntary"]} />
            <SelectField label="Rigidity" fieldKey="abdo_rigidity" options={["Absent", "Present"]} />
            <SelectField label="Rebound" fieldKey="abdo_rebound" options={["Absent", "Present"]} />
            <SelectField label="Liver" fieldKey="liver" options={["Not palpable", "Palpable - smooth", "Palpable - irregular", "Tender"]} />
            <SelectField label="Spleen" fieldKey="spleen" options={["Not palpable", "1 finger", "2 fingers", "3 fingers", "Massive"]} />
            <SelectField label="Bowel Sounds" fieldKey="bowel_sounds" options={["Normal", "Hyperactive", "Hypoactive", "Absent", "Tinkling"]} />
            <SelectField label="Ascites" fieldKey="ascites" options={["Absent", "Mild", "Moderate", "Tense"]} />
            <SelectField label="Murphy's Sign" fieldKey="murphy_sign" options={["Negative", "Positive"]} />
          </div>
        </div>
      </div>

      {/* Neurological */}
      <div className="border rounded-lg">
        <div className="px-4 py-3 border-b">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Brain className="w-5 h-5" />
            Neurological Examination
          </h3>
        </div>
        <div className="p-4 space-y-4">
          {/* GCS */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
                Glasgow Coma Scale
              </span>
              <span
                className={`text-sm font-bold rounded px-2 py-0.5 ${
                  gcsTotal >= 13
                    ? "bg-impilo-500 text-white"
                    : gcsTotal >= 9
                      ? "bg-gray-200 text-gray-700"
                      : "bg-red-600 text-white"
                }`}
              >
                GCS {gcsTotal}/15
              </span>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <SelectField label="Eye (E)" fieldKey="gcs_eye" options={["1", "2", "3", "4"]} />
              <SelectField label="Verbal (V)" fieldKey="gcs_verbal" options={["1", "2", "3", "4", "5"]} />
              <SelectField label="Motor (M)" fieldKey="gcs_motor" options={["1", "2", "3", "4", "5", "6"]} />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <SelectField label="Pupils" fieldKey="pupils" options={["Equal and reactive", "Unequal", "Fixed dilated", "Fixed constricted", "Sluggish"]} />
            <SelectField label="Focal Deficit" fieldKey="focal_deficit" options={["None", "Left hemiparesis", "Right hemiparesis", "Paraparesis", "Cranial nerve palsy"]} />
            <SelectField label="Meningism" fieldKey="meningism" options={["Absent", "Neck stiffness", "Kernig positive", "Brudzinski positive"]} />
            <SelectField label="Upper Limb Power" fieldKey="power_upper" options={["0/5", "1/5", "2/5", "3/5", "4/5", "5/5"]} />
            <SelectField label="Lower Limb Power" fieldKey="power_lower" options={["0/5", "1/5", "2/5", "3/5", "4/5", "5/5"]} />
            <SelectField label="Tone" fieldKey="tone" options={["Normal", "Increased - spasticity", "Increased - rigidity", "Decreased", "Flaccid"]} />
            <SelectField label="Reflexes" fieldKey="reflexes" options={["Normal", "Hyperreflexic", "Hyporeflexic", "Absent", "Clonus present"]} />
          </div>
        </div>
      </div>

      <div className="flex justify-end gap-2">
        <button className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-impilo-500 rounded-md hover:bg-impilo-600 transition-colors">
          <CheckCircle2 className="w-4 h-4" />
          Save Examination
        </button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

import { useCadreFormConfig } from "@/hooks/useCadreFormConfig";
import { CadreHistoryForm } from "@/components/ehr/CadreHistoryForm";
import { CadreExamForm } from "@/components/ehr/CadreExamForm";
import { VitalsRecorder } from "@/components/clinical/VitalsRecorder";
import { LabResultsSystem } from "@/components/lab/LabResultsSystem";
import { PatientTimeline } from "@/components/timeline/PatientTimeline";
import { ClerkingTemplateSelector } from "@/components/ehr/clerking/ClerkingTemplateSelector";
import { ClerkingFormEditor } from "@/components/ehr/clerking/ClerkingFormEditor";
import { CLERKING_TEMPLATES, type ClerkingTemplate, type CadreLevel } from "@/data/clerkingTemplates";

type AssessmentTab = "triage" | "vitals" | "clerking" | "cadre-history" | "cadre-exam" | "history" | "examination" | "labs" | "timeline";

export function AssessmentSection() {
  const params = useParams<{ encounterId?: string }>();
  const encounterId = params.encounterId ?? "";
  const cadreConfig = useCadreFormConfig();
  const isSimplified = cadreConfig.complexity === "simplified";
  const isComprehensive = cadreConfig.complexity === "comprehensive";
  const [selectedTemplate, setSelectedTemplate] = useState<ClerkingTemplate | null>(null);

  // Fetch triage, vitals, and history from BFF
  const { data: triageData, isLoading: triageLoading } = useTriage(encounterId);
  const { data: vitalsData, isLoading: vitalsLoading } = useEncounterVitals(encounterId);
  const { data: historyData, isLoading: historyLoading } = useEncounterHistory(encounterId);

  const triage = triageData?.data ?? null;
  const latestVitals = vitalsData?.data?.[0]?.attributes ?? vitalsData?.data?.[0] ?? null;
  const history = historyData?.data ?? null;

  const [activeTab, setActiveTab] = useState<AssessmentTab>(isSimplified ? "cadre-history" : "triage");

  const tabs: { key: AssessmentTab; label: string; icon: React.ElementType; show: boolean }[] = [
    { key: "triage", label: "Triage", icon: Shield, show: !isSimplified },
    { key: "vitals", label: "Record Vitals", icon: Heart, show: !isSimplified },
    { key: "clerking", label: "Clerking", icon: ClipboardList, show: isComprehensive },
    { key: "cadre-history", label: cadreConfig.labels.historyTabLabel, icon: FileText, show: true },
    { key: "cadre-exam", label: cadreConfig.labels.examTabLabel, icon: Stethoscope, show: true },
    { key: "history", label: "History Review", icon: ClipboardList, show: !isSimplified },
    { key: "examination", label: "Full Exam", icon: Activity, show: isComprehensive },
    { key: "labs", label: "Labs", icon: TestTube, show: !isSimplified },
    { key: "timeline", label: "Timeline", icon: Clock, show: true },
  ];

  const visibleTabs = tabs.filter(t => t.show);

  return (
    <div className="space-y-4">
      {/* Cadre Context Badges */}
      <div className="flex items-center gap-2 flex-wrap">
        <span className="px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600 capitalize">{cadreConfig.cadre} &middot; {cadreConfig.complexity}</span>
        <span className="px-2 py-0.5 rounded text-xs font-medium bg-impilo-100 text-impilo-600 capitalize">{cadreConfig.visitType} visit</span>
        <span className={`px-2 py-0.5 rounded text-xs font-medium capitalize ${cadreConfig.acuity === "red" ? "bg-red-100 text-red-700" : cadreConfig.acuity === "orange" ? "bg-amber-100 text-amber-700" : cadreConfig.acuity === "yellow" ? "bg-yellow-100 text-yellow-700" : "bg-green-100 text-green-700"}`}>{cadreConfig.acuity} acuity</span>
      </div>

      {/* Tab bar */}
      <div className="border-b border-gray-200">
        <nav className="flex gap-1">
          {visibleTabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === tab.key
                    ? "border-impilo-500 text-impilo-500"
                    : "border-transparent text-gray-500 hover:text-gray-700"
                }`}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Tab content */}
      {activeTab === "triage" && <TriagePanel triage={triage} vitals={latestVitals} isLoading={triageLoading || vitalsLoading} />}
      {activeTab === "vitals" && encounterId && <VitalsRecorder encounterId={encounterId} />}
      {activeTab === "clerking" && (
        selectedTemplate
          ? <ClerkingFormEditor template={selectedTemplate} cadreLevel={cadreConfig.cadre as CadreLevel} />
          : <ClerkingTemplateSelector onSelect={setSelectedTemplate} />
      )}
      {activeTab === "cadre-history" && <CadreHistoryForm config={cadreConfig} />}
      {activeTab === "cadre-exam" && <CadreExamForm config={cadreConfig} />}
      {activeTab === "history" && <HistoryPanel history={history} isLoading={historyLoading} />}
      {activeTab === "examination" && <ExaminationPanel />}
      {activeTab === "labs" && <LabResultsSystem />}
      {activeTab === "timeline" && <PatientTimeline />}
    </div>
  );
}
