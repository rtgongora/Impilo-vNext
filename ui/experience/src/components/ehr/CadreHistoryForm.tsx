"use client";

/**
 * CadreHistoryForm — Cadre-adaptive history taking form.
 * Doctor: Full SOCRATES HPI, coded ICD-10 PMH, surgical, family, social, obs/gyn, drug/allergy
 * Nurse: Focused assessment, danger signs, ANC checklists, simplified drug/allergy
 * CHW: Danger sign screening, symptom checklist, refer-or-reassure
 */

import { useState } from "react";
import {
  AlertTriangle, FileText, Pill, Users, Heart, Baby,
  Plus, CheckCircle2, Search, X, ShieldAlert, Brain,
  Stethoscope, ClipboardCheck,
} from "lucide-react";
import { type CadreFormConfig } from "@/hooks/useCadreFormConfig";

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

interface CadreHistoryFormProps { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void; }

export function CadreHistoryForm({ config, onSave }: CadreHistoryFormProps) {
  const [activeTab, setActiveTab] = useState("hpi");
  const [hpiText, setHpiText] = useState("");
  const [socratesData, setSocratesData] = useState({ site: "", onset: "", character: "", radiation: "", associations: "", timing: "", exacerbating: "", severity: "" });
  const [selectedPMH, setSelectedPMH] = useState<string[]>([]);
  const [pmhSearch, setPmhSearch] = useState("");
  const [dangerSigns, setDangerSigns] = useState<string[]>([]);
  const [socialData, setSocialData] = useState<Record<string, string>>({});
  const [systemsReview, setSystemsReview] = useState<Record<string, "normal" | "abnormal" | "not-assessed">>({});
  const [allergies, setAllergies] = useState<string[]>([]);
  const [currentMeds, setCurrentMeds] = useState<string[]>([]);
  const [familyHistory, setFamilyHistory] = useState("");
  const [surgicalHistory, setSurgicalHistory] = useState("");
  const [obsGynHistory, setObsGynHistory] = useState({ gravida: "", para: "", lmp: "", contraception: "" });

  const isComprehensive = config.complexityLevel === "comprehensive";
  const isFocused = config.complexityLevel === "focused";
  const isSimplified = config.complexityLevel === "simplified";

  const filteredPMH = ICD10_CONDITIONS.filter(c => c.display.toLowerCase().includes(pmhSearch.toLowerCase()) || c.code.toLowerCase().includes(pmhSearch.toLowerCase()));

  const toggleDangerSign = (sign: string) => setDangerSigns(prev => prev.includes(sign) ? prev.filter(s => s !== sign) : [...prev, sign]);

  const hasDangerSigns = dangerSigns.length > 0;

  const tabs = [
    ...(isComprehensive ? [
      { id: "hpi", label: "HPI (SOCRATES)", icon: FileText },
      { id: "pmh", label: "Past Medical Hx", icon: Stethoscope },
      { id: "surgical", label: "Surgical Hx", icon: ClipboardCheck },
      { id: "family", label: "Family Hx", icon: Users },
      { id: "social", label: "Social Hx", icon: Heart },
      { id: "systems", label: "Systems Review", icon: Brain },
      { id: "drugs", label: "Drugs & Allergies", icon: Pill },
      { id: "obsgyn", label: "Obs/Gyn", icon: Baby },
    ] : []),
    ...(isFocused ? [
      { id: "hpi", label: "Presenting Complaint", icon: FileText },
      { id: "danger", label: "Danger Signs", icon: AlertTriangle },
      { id: "pmh", label: "Relevant PMH", icon: Stethoscope },
      { id: "drugs", label: "Drugs & Allergies", icon: Pill },
    ] : []),
    ...(isSimplified ? [
      { id: "danger", label: "Danger Signs", icon: ShieldAlert },
      { id: "symptoms", label: "Symptom Checklist", icon: ClipboardCheck },
    ] : []),
  ];

  return (
    <div className="bg-white rounded-lg border">
      {/* Cadre badge */}
      <div className="px-4 py-2 border-b bg-gray-50 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className={`px-2 py-0.5 rounded text-xs font-medium ${isComprehensive ? "bg-blue-100 text-blue-700" : isFocused ? "bg-amber-100 text-amber-700" : "bg-green-100 text-green-700"}`}>
            {config.complexityLevel.charAt(0).toUpperCase() + config.complexityLevel.slice(1)}
          </span>
          <span className="text-xs text-gray-500">History Taking</span>
        </div>
        {hasDangerSigns && <span className="flex items-center gap-1 px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-medium"><AlertTriangle className="w-3 h-3" /> {dangerSigns.length} danger sign(s)</span>}
      </div>

      {/* Tab bar */}
      <div className="flex border-b overflow-x-auto px-2 gap-1">
        {tabs.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)} className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium whitespace-nowrap border-b-2 transition-colors ${activeTab === t.id ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"}`}>
            <t.icon className="w-3.5 h-3.5" /> {t.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="p-4">
        {activeTab === "hpi" && (
          <div className="space-y-4">
            {isComprehensive && (
              <>
                <h3 className="text-sm font-semibold text-gray-900">SOCRATES Pain Assessment</h3>
                <div className="grid grid-cols-2 gap-3">
                  {Object.entries({ site: "Site", onset: "Onset", character: "Character", radiation: "Radiation", associations: "Associations", timing: "Timing", exacerbating: "Exacerbating/Relieving", severity: "Severity (0-10)" }).map(([key, label]) => (
                    <div key={key}><label className="text-[10px] font-medium text-gray-500 uppercase">{label}</label><input value={socratesData[key as keyof typeof socratesData]} onChange={e => setSocratesData(prev => ({ ...prev, [key]: e.target.value }))} className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded focus:outline-none focus:ring-2 focus:ring-blue-500" /></div>
                  ))}
                </div>
              </>
            )}
            <div><label className="text-[10px] font-medium text-gray-500 uppercase">{isComprehensive ? "Additional HPI Notes" : "Presenting Complaint"}</label><textarea value={hpiText} onChange={e => setHpiText(e.target.value)} rows={4} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Describe the presenting complaint..." /></div>
          </div>
        )}

        {activeTab === "danger" && (
          <div className="space-y-4">
            <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><ShieldAlert className="w-4 h-4 text-red-600" /> Danger Sign Screening</h3>
            {[{ label: "Adult Danger Signs", signs: DANGER_SIGNS_ADULT }, { label: "Child Danger Signs", signs: DANGER_SIGNS_CHILD }, { label: "Maternal Danger Signs", signs: DANGER_SIGNS_MATERNAL }].map(group => (
              <div key={group.label}>
                <p className="text-xs font-medium text-gray-600 mb-2">{group.label}</p>
                <div className="space-y-1">
                  {group.signs.map(sign => (
                    <label key={sign} className={`flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer transition-colors ${dangerSigns.includes(sign) ? "bg-red-50 border border-red-200" : "hover:bg-gray-50 border border-transparent"}`}>
                      <input type="checkbox" checked={dangerSigns.includes(sign)} onChange={() => toggleDangerSign(sign)} className="rounded border-gray-300 text-red-600 focus:ring-red-500" />
                      <span className={`text-sm ${dangerSigns.includes(sign) ? "text-red-700 font-medium" : "text-gray-700"}`}>{sign}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
            {hasDangerSigns && <div className="p-3 bg-red-50 border border-red-200 rounded-lg"><p className="text-sm font-semibold text-red-800">REFER IMMEDIATELY</p><p className="text-xs text-red-700 mt-1">{dangerSigns.length} danger sign(s) identified. Refer patient to higher level of care.</p></div>}
          </div>
        )}

        {activeTab === "pmh" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Past Medical History (ICD-10 Coded)</h3>
            <div className="relative"><Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" /><input value={pmhSearch} onChange={e => setPmhSearch(e.target.value)} placeholder="Search conditions..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg" /></div>
            <div className="flex flex-wrap gap-1">{selectedPMH.map(code => { const c = ICD10_CONDITIONS.find(x => x.code === code); return <span key={code} className="inline-flex items-center gap-1 px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs">{c?.display} ({code}) <button onClick={() => setSelectedPMH(prev => prev.filter(x => x !== code))}><X className="w-3 h-3" /></button></span>; })}</div>
            <div className="max-h-48 overflow-y-auto space-y-1">{filteredPMH.filter(c => !selectedPMH.includes(c.code)).map(c => (
              <button key={c.code} onClick={() => setSelectedPMH(prev => [...prev, c.code])} className="w-full flex items-center justify-between px-3 py-2 rounded hover:bg-gray-50 text-left"><span className="text-sm text-gray-700">{c.display}</span><span className="text-xs text-gray-400 font-mono">{c.code}</span></button>
            ))}</div>
          </div>
        )}

        {activeTab === "social" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Social History</h3>
            {SOCIAL_CATEGORIES.map(cat => (
              <div key={cat.id}><label className="text-[10px] font-medium text-gray-500 uppercase">{cat.label}</label>
                {cat.options.length > 0 ? (
                  <select value={socialData[cat.id] || ""} onChange={e => setSocialData(prev => ({ ...prev, [cat.id]: e.target.value }))} className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded">
                    <option value="">Select...</option>{cat.options.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                ) : <input value={socialData[cat.id] || ""} onChange={e => setSocialData(prev => ({ ...prev, [cat.id]: e.target.value }))} className="w-full mt-0.5 px-2 py-1.5 text-sm border rounded" />}
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
                    <button key={status} onClick={() => setSystemsReview(prev => ({ ...prev, [sys]: status }))} className={`px-2 py-0.5 rounded text-xs font-medium ${systemsReview[sys] === status ? status === "normal" ? "bg-green-100 text-green-700" : status === "abnormal" ? "bg-red-100 text-red-700" : "bg-gray-100 text-gray-600" : "text-gray-400 hover:bg-gray-100"}`}>
                      {status === "normal" ? "N" : status === "abnormal" ? "A" : "—"}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {activeTab === "drugs" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Current Medications & Allergies</h3>
            <div><label className="text-[10px] font-medium text-gray-500 uppercase">Current Medications</label><textarea value={currentMeds.join("\n")} onChange={e => setCurrentMeds(e.target.value.split("\n"))} rows={3} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="One medication per line..." /></div>
            <div><label className="text-[10px] font-medium text-gray-500 uppercase">Known Allergies</label><textarea value={allergies.join("\n")} onChange={e => setAllergies(e.target.value.split("\n"))} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="One allergy per line..." /></div>
          </div>
        )}

        {activeTab === "family" && <div><h3 className="text-sm font-semibold text-gray-900 mb-2">Family History</h3><textarea value={familyHistory} onChange={e => setFamilyHistory(e.target.value)} rows={4} className="w-full px-3 py-2 text-sm border rounded-lg" placeholder="Document family medical history..." /></div>}

        {activeTab === "surgical" && <div><h3 className="text-sm font-semibold text-gray-900 mb-2">Surgical History</h3><textarea value={surgicalHistory} onChange={e => setSurgicalHistory(e.target.value)} rows={4} className="w-full px-3 py-2 text-sm border rounded-lg" placeholder="List previous surgeries with dates..." /></div>}

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

        {activeTab === "symptoms" && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Symptom Checklist</h3>
            <p className="text-xs text-gray-500">Tick all symptoms the patient reports</p>
            {["Fever", "Cough", "Difficulty breathing", "Diarrhoea", "Vomiting", "Headache", "Body aches", "Rash", "Sore throat", "Abdominal pain", "Chest pain", "Swelling", "Weight loss", "Loss of appetite", "Fatigue"].map(s => (
              <label key={s} className="flex items-center gap-2 px-3 py-2 rounded hover:bg-gray-50 cursor-pointer">
                <input type="checkbox" className="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                <span className="text-sm text-gray-700">{s}</span>
              </label>
            ))}
          </div>
        )}
      </div>

      {/* Save */}
      <div className="px-4 py-3 border-t bg-gray-50 flex justify-end">
        <button onClick={() => onSave?.({})} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">Save History</button>
      </div>
    </div>
  );
}
