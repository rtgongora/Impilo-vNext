"use client";

/**
 * CadreHistoryForm — Cadre-adaptive history taking form.
 * Doctor: Full SOCRATES HPI, coded ICD-10 PMH, surgical, family, social, obs/gyn, drug/allergy, systems review
 * Nurse: Focused assessment, presenting complaint, danger signs, checklist-driven
 * CHW: Danger sign screening, symptom checklist, refer-or-reassure decision
 * Ported from Lovable CadreHistoryForm.tsx (1058 lines).
 */

import { useState } from "react";
import {
  AlertTriangle, FileText, Pill, Users, Heart, Baby, Plus,
  CheckCircle2, Search, X, ShieldAlert, Brain, Stethoscope,
  ClipboardCheck, ArrowRight,
} from "lucide-react";
import { type CadreFormConfig } from "@/hooks/useCadreFormConfig";

// ── Clinical Reference Data ─────────────────────────
const ICD10_CONDITIONS = [
  { code: "E11", display: "Type 2 Diabetes Mellitus" }, { code: "I10", display: "Essential Hypertension" },
  { code: "J45", display: "Asthma" }, { code: "B20", display: "HIV Disease" },
  { code: "A15", display: "Respiratory Tuberculosis" }, { code: "E78", display: "Dyslipidaemia" },
  { code: "N18", display: "Chronic Kidney Disease" }, { code: "I25", display: "Chronic Ischaemic Heart Disease" },
  { code: "J44", display: "COPD" }, { code: "K21", display: "GORD" },
  { code: "M06", display: "Rheumatoid Arthritis" }, { code: "G40", display: "Epilepsy" },
  { code: "F32", display: "Major Depressive Disorder" }, { code: "E05", display: "Thyrotoxicosis" },
  { code: "D50", display: "Iron Deficiency Anaemia" }, { code: "I48", display: "Atrial Fibrillation" },
  { code: "I50", display: "Heart Failure" }, { code: "E10", display: "Type 1 Diabetes Mellitus" },
  { code: "K70", display: "Alcoholic Liver Disease" }, { code: "N40", display: "Benign Prostatic Hyperplasia" },
];

const DANGER_SIGNS_ADULT = [
  "Unable to drink or eat", "Repeated vomiting", "Convulsions / seizures", "Difficulty breathing",
  "Altered consciousness / confusion", "High fever (>39°C)", "Severe dehydration",
  "Chest pain at rest", "Sudden weakness one side", "Severe bleeding",
];
const DANGER_SIGNS_CHILD = [
  "Not able to breastfeed / drink", "Vomiting everything", "Convulsions", "Lethargy / unconscious",
  "Chest indrawing", "Stridor when calm", "Severe malnutrition (visible wasting)",
  "Severe pallor", "High fever (>38.5°C)", "Bulging fontanelle",
];
const DANGER_SIGNS_MATERNAL = [
  "Vaginal bleeding", "Severe headache with blurred vision", "Convulsions / fits",
  "Fever with inability to get out of bed", "Severe abdominal pain",
  "Fast or difficult breathing", "Foul-smelling vaginal discharge",
  "Reduced fetal movements", "Water breaking >12hrs without labor", "Swollen face/hands (sudden onset)",
];

const SOCIAL_CATEGORIES = [
  { id: "smoking", label: "Smoking", options: ["Never", "Former", "Current (<10/day)", "Current (10-20/day)", "Current (>20/day)"] },
  { id: "alcohol", label: "Alcohol", options: ["None", "Social", "Moderate (1-2/day)", "Heavy (>3/day)", "Binge"] },
  { id: "substances", label: "Substance Use", options: ["None", "Cannabis", "Other (specify)"] },
  { id: "occupation", label: "Occupation", options: [] },
  { id: "exercise", label: "Exercise", options: ["Sedentary", "Light (1-2/week)", "Moderate (3-5/week)", "Active (daily)"] },
];

const SYSTEMS_REVIEW = [
  "General (fever, weight loss, fatigue)", "Cardiovascular (chest pain, palpitations, oedema)",
  "Respiratory (cough, dyspnoea, wheeze)", "GI (nausea, vomiting, diarrhoea, abdominal pain)",
  "Neurological (headache, dizziness, weakness)", "Musculoskeletal (joint pain, swelling)",
  "Genitourinary (dysuria, frequency, haematuria)", "Dermatological (rash, itching, lesions)",
  "Psychiatric (mood, sleep, anxiety)", "ENT (sore throat, ear pain, nasal congestion)",
];

const SYMPTOM_CHECKLIST = [
  "Fever", "Cough", "Diarrhoea", "Headache", "Body pain", "Rash", "Sore throat",
  "Ear pain", "Eye problem", "Skin problem", "Not eating", "Weight loss", "Swelling", "Wound / injury",
];

const NURSING_ASSESSMENT_ITEMS = [
  "Airway patent", "Breathing regular", "Circulation adequate", "Disability (AVPU) Alert",
  "Exposure (temperature checked)", "Pain assessed (scale 0-10)", "Fall risk assessed",
  "Skin integrity assessed", "Nutritional status assessed", "Mental state assessed",
];

interface CadreHistoryFormProps { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void; }

export function CadreHistoryForm({ config, onSave }: CadreHistoryFormProps) {
  if (config.complexityLevel === "simplified") return <CHWHistoryScreen config={config} onSave={onSave} />;
  if (config.complexityLevel === "focused") return <NursingHistoryForm config={config} onSave={onSave} />;
  return <DoctorHistoryForm config={config} onSave={onSave} />;
}

// ══════════════════════════════════════════════════════
// CHW SIMPLIFIED SCREENING
// ══════════════════════════════════════════════════════
function CHWHistoryScreen({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void }) {
  const [checkedSigns, setCheckedSigns] = useState<string[]>([]);
  const [symptoms, setSymptoms] = useState<string[]>([]);
  const [decision, setDecision] = useState<"refer" | "reassure" | null>(null);

  const dangerSigns = config.visitType === "anc" || config.visitType === "pnc" ? DANGER_SIGNS_MATERNAL
    : config.visitType === "pediatric" ? DANGER_SIGNS_CHILD : DANGER_SIGNS_ADULT;

  const toggleSign = (s: string) => setCheckedSigns(prev => prev.includes(s) ? prev.filter(x => x !== s) : [...prev, s]);
  const toggleSymptom = (s: string) => setSymptoms(prev => prev.includes(s) ? prev.filter(x => x !== s) : [...prev, s]);
  const hasDangerSigns = checkedSigns.length > 0;

  return (
    <div className="space-y-4">
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 flex items-center gap-3">
        <ShieldAlert className="w-6 h-6 text-blue-600 shrink-0" />
        <div><p className="font-semibold text-base text-blue-900">Community Health Worker Screening</p><p className="text-sm text-blue-700">Check for danger signs first. If any are present, refer immediately.</p></div>
      </div>

      {/* Danger Signs */}
      <div className={`bg-white rounded-lg border ${hasDangerSigns ? "border-red-300" : ""} p-4`}>
        <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2 mb-3">
          <AlertTriangle className={`w-6 h-6 ${hasDangerSigns ? "text-red-600" : "text-amber-500"}`} /> Danger Signs
          {hasDangerSigns && <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-sm font-bold ml-2">{checkedSigns.length} PRESENT</span>}
        </h3>
        <div className="space-y-2">
          {dangerSigns.map(sign => (
            <button key={sign} onClick={() => toggleSign(sign)}
              className={`w-full flex items-center gap-3 p-3.5 rounded-lg text-left text-base font-medium transition-all ${
                checkedSigns.includes(sign) ? "bg-red-50 border-2 border-red-400 text-red-800" : "bg-gray-50 border-2 border-transparent hover:bg-gray-100"
              }`}>
              <div className={`w-6 h-6 rounded-md border-2 flex items-center justify-center shrink-0 ${
                checkedSigns.includes(sign) ? "border-red-500 bg-red-500 text-white" : "border-gray-300"
              }`}>{checkedSigns.includes(sign) && <CheckCircle2 className="w-4 h-4" />}</div>
              {sign}
            </button>
          ))}
        </div>
      </div>

      {/* Symptoms */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2 mb-3"><ClipboardCheck className="w-6 h-6" /> Symptom Checklist</h3>
        <div className="grid grid-cols-2 gap-2">
          {SYMPTOM_CHECKLIST.map(s => (
            <button key={s} onClick={() => toggleSymptom(s)}
              className={`flex items-center gap-2 p-3 rounded-lg text-left text-sm font-medium transition-all ${
                symptoms.includes(s) ? "bg-blue-50 border-2 border-blue-300" : "bg-gray-50 border-2 border-transparent hover:bg-gray-100"
              }`}>
              <div className={`w-5 h-5 rounded border-2 flex items-center justify-center shrink-0 ${
                symptoms.includes(s) ? "border-blue-500 bg-blue-500 text-white" : "border-gray-300"
              }`}>{symptoms.includes(s) && <CheckCircle2 className="w-3 h-3" />}</div>
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Decision */}
      <div className="bg-white rounded-lg border-2 border-blue-200 p-4">
        <h3 className="text-lg font-semibold mb-3">Decision</h3>
        <div className="grid grid-cols-2 gap-4">
          <button onClick={() => setDecision("refer")}
            className={`h-20 rounded-lg text-lg font-bold flex flex-col items-center justify-center gap-1 transition-all ${
              decision === "refer" ? "bg-red-600 text-white" : "border-2 border-gray-200 text-gray-600 hover:bg-gray-50"
            }`}><ArrowRight className="w-7 h-7" /> REFER</button>
          <button onClick={() => setDecision("reassure")}
            className={`h-20 rounded-lg text-lg font-bold flex flex-col items-center justify-center gap-1 transition-all ${
              decision === "reassure" ? "bg-green-600 text-white" : "border-2 border-gray-200 text-gray-600 hover:bg-gray-50"
            }`}><CheckCircle2 className="w-7 h-7" /> REASSURE</button>
        </div>
        {hasDangerSigns && decision !== "refer" && (
          <div className="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-red-800 text-sm font-semibold flex items-center gap-2">
            <AlertTriangle className="w-5 h-5" /> Danger signs present — referral strongly recommended
          </div>
        )}
      </div>

      <button onClick={() => onSave?.({ checkedSigns, symptoms, decision })}
        className="w-full h-14 bg-blue-600 text-white text-lg font-bold rounded-lg hover:bg-blue-700">Save Screening</button>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// NURSE / MIDWIFE FOCUSED ASSESSMENT
// ══════════════════════════════════════════════════════
function NursingHistoryForm({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void }) {
  const [complaint, setComplaint] = useState("");
  const [assessmentChecks, setAssessmentChecks] = useState<string[]>([]);
  const [dangerChecks, setDangerChecks] = useState<string[]>([]);
  const [painScore, setPainScore] = useState("");
  const [allergies, setAllergies] = useState("");
  const [currentMeds, setCurrentMeds] = useState("");

  const dangerSigns = config.visitType === "anc" || config.visitType === "pnc" ? DANGER_SIGNS_MATERNAL
    : config.visitType === "pediatric" ? DANGER_SIGNS_CHILD : DANGER_SIGNS_ADULT;

  const toggleAssessment = (item: string) => setAssessmentChecks(prev => prev.includes(item) ? prev.filter(x => x !== item) : [...prev, item]);
  const toggleDanger = (item: string) => setDangerChecks(prev => prev.includes(item) ? prev.filter(x => x !== item) : [...prev, item]);

  return (
    <div className="space-y-4">
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 flex items-center gap-3">
        <Stethoscope className="w-6 h-6 text-blue-600 shrink-0" />
        <div><p className="font-semibold text-base text-blue-900">Nursing Assessment</p><p className="text-sm text-blue-700">Focused clinical assessment — presenting complaint, rapid review, danger signs.</p></div>
      </div>

      {/* Presenting Complaint */}
      <div className="bg-white rounded-lg border p-4 space-y-3">
        <h3 className="text-sm font-semibold text-gray-900">Presenting Complaint</h3>
        <textarea value={complaint} onChange={e => setComplaint(e.target.value)} rows={3} className="w-full px-3 py-2 text-sm border rounded-lg" placeholder="What brought the patient in today?" />
        <div>
          <label className="text-[10px] font-medium text-gray-500 uppercase">Pain Score (0-10)</label>
          <div className="flex gap-1 mt-1">{Array.from({ length: 11 }, (_, i) => (
            <button key={i} onClick={() => setPainScore(String(i))}
              className={`w-8 h-8 rounded-lg text-xs font-bold transition-colors ${
                painScore === String(i) ? i <= 3 ? "bg-green-500 text-white" : i <= 6 ? "bg-amber-500 text-white" : "bg-red-500 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}>{i}</button>
          ))}</div>
        </div>
      </div>

      {/* Rapid Assessment (ABCDE) */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="text-sm font-semibold text-gray-900 mb-3">Rapid Assessment Checklist</h3>
        <div className="grid grid-cols-2 gap-1.5">
          {NURSING_ASSESSMENT_ITEMS.map(item => {
            const checked = assessmentChecks.includes(item);
            return (
              <button key={item} onClick={() => toggleAssessment(item)}
                className={`flex items-center gap-2 p-2.5 rounded-lg text-left text-sm transition-all ${
                  checked ? "bg-green-50 border border-green-200 font-medium" : "bg-gray-50 border border-transparent hover:bg-gray-100"
                }`}>
                <div className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 ${
                  checked ? "border-green-500 bg-green-500 text-white" : "border-gray-300"
                }`}>{checked && <CheckCircle2 className="w-3 h-3" />}</div>
                {item}
              </button>
            );
          })}
        </div>
      </div>

      {/* Danger Signs */}
      <div className={`bg-white rounded-lg border ${dangerChecks.length > 0 ? "border-red-300" : ""} p-4`}>
        <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-amber-500" /> Danger Signs
          {dangerChecks.length > 0 && <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-bold">{dangerChecks.length} PRESENT</span>}
        </h3>
        <div className="space-y-1.5">
          {dangerSigns.map(sign => (
            <button key={sign} onClick={() => toggleDanger(sign)}
              className={`w-full flex items-center gap-2 p-2.5 rounded-lg text-left text-sm transition-all ${
                dangerChecks.includes(sign) ? "bg-red-50 border border-red-300 font-medium text-red-800" : "bg-gray-50 border border-transparent hover:bg-gray-100"
              }`}>
              <div className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 ${
                dangerChecks.includes(sign) ? "border-red-500 bg-red-500 text-white" : "border-gray-300"
              }`}>{dangerChecks.includes(sign) && <CheckCircle2 className="w-3 h-3" />}</div>
              {sign}
            </button>
          ))}
        </div>
      </div>

      {/* Medications & Allergies */}
      <div className="bg-white rounded-lg border p-4 space-y-3">
        <h3 className="text-sm font-semibold text-gray-900">Medications & Allergies</h3>
        <div><label className="text-[10px] font-medium text-gray-500 uppercase">Current Medications</label><textarea value={currentMeds} onChange={e => setCurrentMeds(e.target.value)} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="List current medications..." /></div>
        <div><label className="text-[10px] font-medium text-gray-500 uppercase">Known Allergies</label><textarea value={allergies} onChange={e => setAllergies(e.target.value)} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="List allergies..." /></div>
      </div>

      <button onClick={() => onSave?.({})} className="w-full py-3 bg-blue-600 text-white text-sm font-bold rounded-lg hover:bg-blue-700">Save Nursing Assessment</button>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// DOCTOR / SPECIALIST COMPREHENSIVE HISTORY
// ══════════════════════════════════════════════════════
function DoctorHistoryForm({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void }) {
  const [activeTab, setActiveTab] = useState("hpi");
  const [hpiText, setHpiText] = useState("");
  const [socratesData, setSocratesData] = useState({ site: "", onset: "", character: "", radiation: "", associations: "", timing: "", exacerbating: "", severity: "" });
  const [selectedPMH, setSelectedPMH] = useState<string[]>([]);
  const [pmhSearch, setPmhSearch] = useState("");
  const [socialData, setSocialData] = useState<Record<string, string>>({});
  const [systemsReview, setSystemsReview] = useState<Record<string, "normal" | "abnormal" | "not-assessed">>({});
  const [allergies, setAllergies] = useState("");
  const [currentMeds, setCurrentMeds] = useState("");
  const [familyHistory, setFamilyHistory] = useState("");
  const [surgicalHistory, setSurgicalHistory] = useState("");
  const [obsGynHistory, setObsGynHistory] = useState({ gravida: "", para: "", lmp: "", contraception: "" });

  const filteredPMH = ICD10_CONDITIONS.filter(c =>
    c.display.toLowerCase().includes(pmhSearch.toLowerCase()) || c.code.toLowerCase().includes(pmhSearch.toLowerCase())
  );

  const tabs = [
    { id: "hpi", label: "HPI (SOCRATES)", icon: FileText },
    { id: "pmh", label: "Past Medical Hx", icon: Stethoscope },
    { id: "surgical", label: "Surgical Hx", icon: ClipboardCheck },
    { id: "family", label: "Family Hx", icon: Users },
    { id: "social", label: "Social Hx", icon: Heart },
    { id: "systems", label: "Systems Review", icon: Brain },
    { id: "drugs", label: "Drugs & Allergies", icon: Pill },
    { id: "obsgyn", label: "Obs/Gyn", icon: Baby },
  ];

  return (
    <div className="bg-white rounded-lg border">
      <div className="px-4 py-2 border-b bg-gray-50 flex items-center gap-2">
        <span className="px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-700">Comprehensive</span>
        <span className="text-xs text-gray-500">Medical Practitioner History</span>
      </div>

      <div className="flex border-b overflow-x-auto px-2 gap-1">
        {tabs.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)}
            className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium whitespace-nowrap border-b-2 transition-colors ${
              activeTab === t.id ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}><t.icon className="w-3.5 h-3.5" /> {t.label}</button>
        ))}
      </div>

      <div className="p-4">
        {activeTab === "hpi" && (
          <div className="space-y-4">
            <h3 className="text-sm font-semibold text-gray-900">SOCRATES Pain Assessment</h3>
            <div className="grid grid-cols-2 gap-3">
              {Object.entries({ site: "Site", onset: "Onset", character: "Character", radiation: "Radiation", associations: "Associations", timing: "Timing", exacerbating: "Exacerbating/Relieving", severity: "Severity (0-10)" }).map(([key, label]) => (
                <div key={key}><label className="text-[10px] font-medium text-gray-500 uppercase">{label}</label>
                  <input value={socratesData[key as keyof typeof socratesData]} onChange={e => setSocratesData(prev => ({ ...prev, [key]: e.target.value }))}
                    className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded focus:outline-none focus:ring-2 focus:ring-blue-500" /></div>
              ))}
            </div>
            <div><label className="text-[10px] font-medium text-gray-500 uppercase">Additional HPI Notes</label>
              <textarea value={hpiText} onChange={e => setHpiText(e.target.value)} rows={4} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Free-text history of presenting illness..." /></div>
          </div>
        )}

        {activeTab === "pmh" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Past Medical History (ICD-10 Coded)</h3>
            <div className="relative"><Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input value={pmhSearch} onChange={e => setPmhSearch(e.target.value)} placeholder="Search conditions..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg" /></div>
            <div className="flex flex-wrap gap-1">{selectedPMH.map(code => {
              const c = ICD10_CONDITIONS.find(x => x.code === code);
              return <span key={code} className="inline-flex items-center gap-1 px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs">{c?.display} ({code})
                <button onClick={() => setSelectedPMH(prev => prev.filter(x => x !== code))}><X className="w-3 h-3" /></button></span>;
            })}</div>
            <div className="max-h-48 overflow-y-auto space-y-1">{filteredPMH.filter(c => !selectedPMH.includes(c.code)).map(c => (
              <button key={c.code} onClick={() => setSelectedPMH(prev => [...prev, c.code])}
                className="w-full flex items-center justify-between px-3 py-2 rounded hover:bg-gray-50 text-left">
                <span className="text-sm text-gray-700">{c.display}</span><span className="text-xs text-gray-400 font-mono">{c.code}</span>
              </button>
            ))}</div>
          </div>
        )}

        {activeTab === "surgical" && (
          <div><h3 className="text-sm font-semibold text-gray-900 mb-2">Surgical History</h3>
            <textarea value={surgicalHistory} onChange={e => setSurgicalHistory(e.target.value)} rows={5} className="w-full px-3 py-2 text-sm border rounded-lg" placeholder="List previous surgeries with dates and outcomes..." /></div>
        )}

        {activeTab === "family" && (
          <div><h3 className="text-sm font-semibold text-gray-900 mb-2">Family History</h3>
            <textarea value={familyHistory} onChange={e => setFamilyHistory(e.target.value)} rows={5} className="w-full px-3 py-2 text-sm border rounded-lg" placeholder="Document family medical history (DM, HTN, Ca, CVA, psychiatric...)..." /></div>
        )}

        {activeTab === "social" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Social History</h3>
            {SOCIAL_CATEGORIES.map(cat => (
              <div key={cat.id}><label className="text-[10px] font-medium text-gray-500 uppercase">{cat.label}</label>
                {cat.options.length > 0 ? (
                  <select value={socialData[cat.id] || ""} onChange={e => setSocialData(prev => ({ ...prev, [cat.id]: e.target.value }))}
                    className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded"><option value="">Select...</option>{cat.options.map(o => <option key={o} value={o}>{o}</option>)}</select>
                ) : <input value={socialData[cat.id] || ""} onChange={e => setSocialData(prev => ({ ...prev, [cat.id]: e.target.value }))}
                    className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded" />}
              </div>
            ))}
          </div>
        )}

        {activeTab === "systems" && (
          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-gray-900">Systems Review</h3>
            {SYSTEMS_REVIEW.map(sys => (
              <div key={sys} className="flex items-center justify-between px-3 py-2 rounded-lg hover:bg-gray-50">
                <span className="text-sm text-gray-700">{sys}</span>
                <div className="flex gap-1">
                  {(["normal", "abnormal", "not-assessed"] as const).map(status => (
                    <button key={status} onClick={() => setSystemsReview(prev => ({ ...prev, [sys]: status }))}
                      className={`px-2 py-0.5 rounded text-xs font-medium ${
                        systemsReview[sys] === status ? status === "normal" ? "bg-green-100 text-green-700" : status === "abnormal" ? "bg-red-100 text-red-700" : "bg-gray-100 text-gray-600"
                        : "text-gray-400 hover:bg-gray-100"
                      }`}>{status === "normal" ? "N" : status === "abnormal" ? "A" : "—"}</button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {activeTab === "drugs" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Current Medications & Allergies</h3>
            <div><label className="text-[10px] font-medium text-gray-500 uppercase">Current Medications</label>
              <textarea value={currentMeds} onChange={e => setCurrentMeds(e.target.value)} rows={3} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="One medication per line..." /></div>
            <div><label className="text-[10px] font-medium text-gray-500 uppercase">Known Allergies</label>
              <textarea value={allergies} onChange={e => setAllergies(e.target.value)} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="One allergy per line (include reaction type)..." /></div>
          </div>
        )}

        {activeTab === "obsgyn" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Obstetric & Gynaecological History</h3>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Gravida</label><input value={obsGynHistory.gravida} onChange={e => setObsGynHistory(prev => ({ ...prev, gravida: e.target.value }))} className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded" /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Para</label><input value={obsGynHistory.para} onChange={e => setObsGynHistory(prev => ({ ...prev, para: e.target.value }))} className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded" /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">LMP</label><input type="date" value={obsGynHistory.lmp} onChange={e => setObsGynHistory(prev => ({ ...prev, lmp: e.target.value }))} className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded" /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Contraception</label><input value={obsGynHistory.contraception} onChange={e => setObsGynHistory(prev => ({ ...prev, contraception: e.target.value }))} className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded" /></div>
            </div>
          </div>
        )}
      </div>

      <div className="px-4 py-3 border-t bg-gray-50 flex justify-end">
        <button onClick={() => onSave?.({})} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">Save History</button>
      </div>
    </div>
  );
}
