"use client";

/**
 * CadreHistoryForm — Faithful port of Lovable CadreHistoryForm.tsx (1058 lines).
 * 3 cadre-specific sub-forms driven by config.history.* flags:
 * - CHW: Danger signs, symptom checklist, refer/reassure decision
 * - Nurse: Focused assessment, danger signs, PMH checklist, obs quick screen
 * - Doctor: Full SOCRATES, ICD-10 PMH, surgical/family/social/obs-gyn, drugs, allergies, ROS
 */

import { useState } from "react";
import {
  AlertTriangle, FileText, Pill, Users, Heart, Baby, Plus,
  CheckCircle2, Search, X, ShieldAlert, Brain, Stethoscope,
  ClipboardCheck, ArrowRight,
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

const SYMPTOM_CHECKLIST = [
  "Fever", "Cough", "Diarrhoea", "Headache", "Body pain", "Rash", "Sore throat",
  "Ear pain", "Eye problem", "Skin problem", "Not eating", "Weight loss", "Swelling", "Wound / injury",
];

interface CadreHistoryFormProps { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void; }

export function CadreHistoryForm({ config, onSave }: CadreHistoryFormProps) {
  if (config.complexityLevel === "simplified") return <CHWHistoryScreen config={config} onSave={onSave} />;
  if (config.complexityLevel === "focused") return <NursingHistoryForm config={config} onSave={onSave} />;
  return <DoctorHistoryForm config={config} onSave={onSave} />;
}

// ══════════════════════════════════════════════════════
// HELPER COMPONENTS
// ══════════════════════════════════════════════════════
function SelectField({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: string[] }) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">{label}</label>
      <select value={value} onChange={e => onChange(e.target.value)} className="w-full h-10 px-3 text-sm rounded-md border border-gray-300 bg-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
        <option value="">Select...</option>
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    </div>
  );
}

function NumberInput({ label, value, onChange, unit }: { label: string; value: string; onChange: (v: string) => void; unit?: string }) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">{label}</label>
      <div className="flex items-center gap-2">
        <input type="number" value={value} onChange={e => onChange(e.target.value)} className="w-full h-10 px-3 text-sm rounded-md border border-gray-300 bg-white tabular-nums focus:ring-2 focus:ring-blue-500" />
        {unit && <span className="text-xs text-gray-500 shrink-0">{unit}</span>}
      </div>
    </div>
  );
}

function CheckGroup({ title, items, checked, onToggle }: { title: string; items: string[]; checked: string[]; onToggle: (item: string) => void }) {
  return (
    <div className="space-y-2">
      {title && <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">{title}</label>}
      <div className="grid grid-cols-2 gap-1.5">
        {items.map(item => {
          const isChecked = checked.includes(item);
          return (
            <button key={item} type="button" onClick={() => onToggle(item)}
              className={`flex items-center gap-2 p-2.5 rounded-lg text-left text-sm transition-all ${isChecked ? "bg-blue-50 border border-blue-200 font-medium" : "bg-gray-50 border border-transparent hover:bg-gray-100"}`}>
              <div className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 ${isChecked ? "border-blue-500 bg-blue-500 text-white" : "border-gray-300"}`}>
                {isChecked && <CheckCircle2 className="w-3 h-3" />}
              </div>
              {item}
            </button>
          );
        })}
      </div>
    </div>
  );
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
        <div><p className="font-semibold text-base text-blue-900">Community Health Worker Screening</p><p className="text-sm text-blue-700">{config.labels.guidanceText}</p></div>
      </div>
      <div className={`bg-white rounded-lg border ${hasDangerSigns ? "border-red-300" : ""} p-4`}>
        <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2 mb-3">
          <AlertTriangle className={`w-6 h-6 ${hasDangerSigns ? "text-red-600" : "text-amber-500"}`} /> Danger Signs
          {hasDangerSigns && <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-sm font-bold ml-2">{checkedSigns.length} PRESENT</span>}
        </h3>
        <div className="space-y-2">
          {dangerSigns.map(sign => (
            <button key={sign} type="button" onClick={() => toggleSign(sign)} className={`w-full flex items-center gap-3 p-3.5 rounded-lg text-left text-base font-medium transition-all ${checkedSigns.includes(sign) ? "bg-red-50 border-2 border-red-400 text-red-800" : "bg-gray-50 border-2 border-transparent hover:bg-gray-100"}`}>
              <div className={`w-6 h-6 rounded-md border-2 flex items-center justify-center shrink-0 ${checkedSigns.includes(sign) ? "border-red-500 bg-red-500 text-white" : "border-gray-300"}`}>{checkedSigns.includes(sign) && <CheckCircle2 className="w-4 h-4" />}</div>
              {sign}
            </button>
          ))}
        </div>
      </div>
      <div className="bg-white rounded-lg border p-4">
        <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2 mb-3"><ClipboardCheck className="w-6 h-6" /> Symptom Checklist</h3>
        <div className="grid grid-cols-2 gap-2">
          {SYMPTOM_CHECKLIST.map(s => (
            <button key={s} type="button" onClick={() => toggleSymptom(s)} className={`flex items-center gap-2 p-3 rounded-lg text-left text-sm font-medium transition-all ${symptoms.includes(s) ? "bg-blue-50 border-2 border-blue-300" : "bg-gray-50 border-2 border-transparent hover:bg-gray-100"}`}>
              <div className={`w-5 h-5 rounded border-2 flex items-center justify-center shrink-0 ${symptoms.includes(s) ? "border-blue-500 bg-blue-500 text-white" : "border-gray-300"}`}>{symptoms.includes(s) && <CheckCircle2 className="w-3 h-3" />}</div>
              {s}
            </button>
          ))}
        </div>
      </div>
      <div className="bg-white rounded-lg border-2 border-blue-200 p-4">
        <h3 className="text-lg font-semibold mb-3">Decision</h3>
        <div className="grid grid-cols-2 gap-4">
          <button type="button" onClick={() => setDecision("refer")} className={`h-20 rounded-lg text-lg font-bold flex flex-col items-center justify-center gap-1 transition-all ${decision === "refer" ? "bg-red-600 text-white" : "border-2 border-gray-200 text-gray-600 hover:bg-gray-50"}`}><ArrowRight className="w-7 h-7" /> REFER</button>
          <button type="button" onClick={() => setDecision("reassure")} className={`h-20 rounded-lg text-lg font-bold flex flex-col items-center justify-center gap-1 transition-all ${decision === "reassure" ? "bg-green-600 text-white" : "border-2 border-gray-200 text-gray-600 hover:bg-gray-50"}`}><CheckCircle2 className="w-7 h-7" /> REASSURE</button>
        </div>
        {hasDangerSigns && decision !== "refer" && <div className="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-red-800 text-sm font-semibold flex items-center gap-2"><AlertTriangle className="w-5 h-5" /> Danger signs present — referral strongly recommended</div>}
      </div>
      <button type="button" onClick={() => onSave?.({ checkedSigns, symptoms, decision })} className="w-full h-14 bg-blue-600 text-white text-lg font-bold rounded-lg hover:bg-blue-700">{config.labels.saveLabel}</button>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// NURSE / MIDWIFE FOCUSED ASSESSMENT
// ══════════════════════════════════════════════════════
function NursingHistoryForm({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void }) {
  const [formData, setFormData] = useState<Record<string, string>>({});
  const [checkedItems, setCheckedItems] = useState<Record<string, string[]>>({});
  const update = (key: string, val: string) => setFormData(prev => ({ ...prev, [key]: val }));
  const toggleCheck = (group: string, item: string) => {
    setCheckedItems(prev => { const c = prev[group] || []; return { ...prev, [group]: c.includes(item) ? c.filter(i => i !== item) : [...c, item] }; });
  };
  const dangerSigns = config.visitType === "anc" || config.visitType === "pnc" ? DANGER_SIGNS_MATERNAL : DANGER_SIGNS_ADULT.slice(0, 6);

  return (
    <div className="space-y-4">
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 flex items-center gap-3">
        <Stethoscope className="w-6 h-6 text-blue-600 shrink-0" />
        <div><p className="font-semibold text-base text-blue-900">Nursing Assessment</p><p className="text-sm text-blue-700">{config.labels.guidanceText}</p></div>
      </div>

      {/* Presenting Complaint */}
      <div className="bg-white rounded-lg border p-4 space-y-3">
        <h3 className="text-base font-semibold text-gray-900">Presenting Complaint</h3>
        <SelectField label="Main Complaint" value={formData.chief_complaint || ""} onChange={v => update("chief_complaint", v)} options={["Fever", "Cough", "Diarrhoea", "Abdominal pain", "Chest pain", "Headache", "Difficulty breathing", "Injury / trauma", "Skin problem", "Pregnancy-related", "Labour pains", "Post-delivery complaint", "Other"]} />
        <SelectField label="Duration" value={formData.duration || ""} onChange={v => update("duration", v)} options={["<24 hours", "1-3 days", "4-7 days", "1-2 weeks", "2-4 weeks", ">1 month"]} />
        <SelectField label="Severity" value={formData.severity || ""} onChange={v => update("severity", v)} options={["Mild — able to perform daily activities", "Moderate — limited daily activities", "Severe — unable to perform daily activities"]} />
      </div>

      {/* Danger Signs */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-3"><AlertTriangle className="w-5 h-5 text-amber-500" /> Danger Sign Screen</h3>
        <CheckGroup title="" items={dangerSigns} checked={checkedItems.danger_signs || []} onToggle={item => toggleCheck("danger_signs", item)} />
      </div>

      {/* Focused History */}
      <div className="bg-white rounded-lg border p-4 space-y-3">
        <h3 className="text-base font-semibold text-gray-900">Focused History</h3>
        <CheckGroup title="Known Conditions" items={["Hypertension", "Diabetes", "HIV", "TB", "Asthma/COPD", "Heart disease", "Epilepsy", "Mental health condition", "Cancer", "None known"]} checked={checkedItems.pmh || []} onToggle={item => toggleCheck("pmh", item)} />
        <SelectField label="Currently on Medication" value={formData.on_meds || ""} onChange={v => update("on_meds", v)} options={["Yes — compliant", "Yes — not compliant", "No", "Unknown"]} />
        <SelectField label="Known Allergies" value={formData.allergies || ""} onChange={v => update("allergies", v)} options={["NKDA", "Penicillin", "Sulfa drugs", "NSAIDs", "Other — specify"]} />
      </div>

      {/* ANC-specific obstetric screen */}
      {config.history.showObsGyn && (
        <div className="bg-white rounded-lg border p-4 space-y-3">
          <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><Baby className="w-5 h-5" /> Obstetric Quick Screen</h3>
          <div className="grid grid-cols-4 gap-3">
            {["Gravida", "Para", "Miscarriages", "Living Children"].map(label => (
              <div key={label} className="space-y-1.5">
                <label className="text-xs font-bold text-gray-500">{label}</label>
                <input type="number" min={0} max={20} value={formData[label.toLowerCase()] || ""} onChange={e => update(label.toLowerCase(), e.target.value)} className="w-full h-10 px-3 text-center text-lg font-bold rounded-md border border-gray-300 bg-white" />
              </div>
            ))}
          </div>
          <SelectField label="Last Delivery Outcome" value={formData.last_delivery || ""} onChange={v => update("last_delivery", v)} options={["Normal vaginal delivery", "Assisted delivery", "Caesarean section", "Stillbirth", "Miscarriage", "N/A — primigravida"]} />
          <CheckGroup title="Previous Complications" items={["Pre-eclampsia", "Eclampsia", "PPH", "Obstructed labour", "Preterm delivery", "Neonatal death", "None"]} checked={checkedItems.obs_complications || []} onToggle={item => toggleCheck("obs_complications", item)} />
        </div>
      )}

      {/* Social screen */}
      {config.history.showSocialHistory && (
        <div className="bg-white rounded-lg border p-4 space-y-3">
          <h3 className="text-base font-semibold text-gray-900">Social Screen</h3>
          <SelectField label="Smoking" value={formData.smoking || ""} onChange={v => update("smoking", v)} options={["Never", "Current", "Ex-smoker"]} />
          <SelectField label="Alcohol" value={formData.alcohol || ""} onChange={v => update("alcohol", v)} options={["None", "Social", "Heavy", "Dependent"]} />
          <SelectField label="Support System" value={formData.support || ""} onChange={v => update("support", v)} options={["Good support", "Limited support", "No support / isolated"]} />
        </div>
      )}

      <button type="button" onClick={() => onSave?.({ formData, checkedItems })} className="w-full h-12 bg-blue-600 text-white text-base font-semibold rounded-lg hover:bg-blue-700 flex items-center justify-center gap-2"><CheckCircle2 className="w-5 h-5" /> {config.labels.saveLabel}</button>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// DOCTOR / SPECIALIST COMPREHENSIVE HISTORY
// ══════════════════════════════════════════════════════
function DoctorHistoryForm({ config, onSave }: { config: CadreFormConfig; onSave?: (data: Record<string, unknown>) => void }) {
  const [activeSection, setActiveSection] = useState("hpi");
  const [isAddingEntry, setIsAddingEntry] = useState(true);
  const [formData, setFormData] = useState<Record<string, string>>({});
  const [selectedConditions, setSelectedConditions] = useState<typeof ICD10_CONDITIONS[number][]>([]);
  const [conditionSearch, setConditionSearch] = useState("");
  const [checkedItems, setCheckedItems] = useState<Record<string, string[]>>({});
  const [savedEntries, setSavedEntries] = useState<{ section: string; time: Date }[]>([]);

  const update = (key: string, val: string) => setFormData(prev => ({ ...prev, [key]: val }));
  const toggleCheck = (group: string, item: string) => {
    setCheckedItems(prev => { const c = prev[group] || []; return { ...prev, [group]: c.includes(item) ? c.filter(i => i !== item) : [...c, item] }; });
  };
  const filteredConditions = ICD10_CONDITIONS.filter(c => c.display.toLowerCase().includes(conditionSearch.toLowerCase()) || c.code.toLowerCase().includes(conditionSearch.toLowerCase()));
  const saveSection = () => { setSavedEntries(prev => [...prev, { section: activeSection, time: new Date() }]); };

  const sections = [
    { id: "hpi", label: "HPI", icon: FileText },
    { id: "pmh", label: "PMH", icon: Heart },
    ...(config.history.showSurgicalHistory ? [{ id: "psh", label: "Surgical", icon: Stethoscope }] : []),
    ...(config.history.showFamilyHistory ? [{ id: "fhx", label: "Family", icon: Users }] : []),
    ...(config.history.showSocialHistory ? [{ id: "shx", label: "Social", icon: Users }] : []),
    ...(config.history.showObsGyn ? [{ id: "obgyn", label: "Obs/Gyn", icon: Baby }] : []),
    { id: "drugs", label: "Medications", icon: Pill },
    { id: "allergies", label: "Allergies", icon: AlertTriangle },
    ...(config.history.showFullROS ? [{ id: "ros", label: "ROS", icon: ClipboardCheck }] : []),
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">ICD-10 / SNOMED CT coded</span>
          <span className="px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-700">{savedEntries.length} entries saved</span>
        </div>
        <button type="button" onClick={() => setIsAddingEntry(!isAddingEntry)} className={`px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 ${isAddingEntry ? "bg-gray-100 text-gray-600" : "bg-blue-600 text-white"}`}>
          {isAddingEntry ? <><X className="w-4 h-4" /> Close</> : <><Plus className="w-4 h-4" /> New History Entry</>}
        </button>
      </div>

      {isAddingEntry && (
        <>
          {/* Section Navigator */}
          <div className="flex gap-1.5 flex-wrap">
            {sections.map(s => {
              const Icon = s.icon;
              const isSaved = savedEntries.some(e => e.section === s.id);
              return (
                <button key={s.id} type="button" onClick={() => setActiveSection(s.id)}
                  className={`px-3 py-1.5 rounded-lg text-sm font-medium flex items-center gap-1.5 transition-colors ${activeSection === s.id ? "bg-blue-600 text-white" : isSaved ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>
                  {isSaved ? <CheckCircle2 className="w-4 h-4" /> : <Icon className="w-4 h-4" />} {s.label}
                </button>
              );
            })}
          </div>

          {/* HPI — SOCRATES */}
          {activeSection === "hpi" && (
            <div className="bg-white rounded-lg border p-4">
              <div className="flex items-center justify-between mb-3"><h3 className="text-base font-semibold text-gray-900">History of Present Illness</h3><span className="px-2 py-0.5 rounded text-[10px] font-medium bg-gray-100 text-gray-500">SOCRATES Format</span></div>
              <div className="grid grid-cols-2 gap-3">
                <SelectField label="Site" value={formData.hpi_site || ""} onChange={v => update("hpi_site", v)} options={["Head", "Neck", "Chest", "Abdomen", "Back", "Upper limb", "Lower limb", "Generalised", "Pelvic", "Flank", "Epigastric", "Retrosternal"]} />
                <SelectField label="Onset" value={formData.hpi_onset || ""} onChange={v => update("hpi_onset", v)} options={["Sudden (<minutes)", "Acute (<hours)", "Subacute (days)", "Gradual (weeks)", "Chronic (months)"]} />
                <SelectField label="Character" value={formData.hpi_character || ""} onChange={v => update("hpi_character", v)} options={["Sharp", "Dull", "Burning", "Cramping", "Stabbing", "Throbbing", "Pressure", "Tearing", "Colicky", "Aching"]} />
                <SelectField label="Radiation" value={formData.hpi_radiation || ""} onChange={v => update("hpi_radiation", v)} options={["None", "To arm (L)", "To arm (R)", "To back", "To jaw", "To shoulder", "To groin", "To leg", "Diffuse"]} />
                <SelectField label="Timing" value={formData.hpi_timing || ""} onChange={v => update("hpi_timing", v)} options={["Constant", "Intermittent", "Worse at night", "Worse in morning", "Post-prandial", "On exertion", "At rest", "Episodic"]} />
                <SelectField label="Severity (0-10)" value={formData.hpi_severity || ""} onChange={v => update("hpi_severity", v)} options={Array.from({ length: 11 }, (_, i) => `${i}/10 — ${i <= 3 ? "Mild" : i <= 6 ? "Moderate" : "Severe"}`)} />
                <div className="col-span-2">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Associated Symptoms</label>
                  <div className="grid grid-cols-4 gap-1.5 mt-1.5">
                    {["Nausea", "Vomiting", "Fever", "Chills", "Sweating", "Weight loss", "Anorexia", "Fatigue", "Dyspnoea", "Palpitations", "Dizziness", "Syncope"].map(s => {
                      const checked = (checkedItems.associated || []).includes(s);
                      return <button key={s} type="button" onClick={() => toggleCheck("associated", s)} className={`p-2 rounded text-xs font-medium transition-all ${checked ? "bg-blue-50 border border-blue-200" : "bg-gray-50 border border-transparent hover:bg-gray-100"}`}>{s}</button>;
                    })}
                  </div>
                </div>
                <SelectField label="Exacerbating Factors" value={formData.hpi_exacerbating || ""} onChange={v => update("hpi_exacerbating", v)} options={["Movement", "Breathing", "Eating", "Lying flat", "Coughing", "Exercise", "Stress", "Cold", "Nothing specific"]} />
                <SelectField label="Relieving Factors" value={formData.hpi_relieving || ""} onChange={v => update("hpi_relieving", v)} options={["Rest", "Analgesics", "Antacids", "Sitting up", "Heat", "Cold", "Food", "Nothing helps"]} />
              </div>
              <div className="flex justify-end mt-4"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save HPI</button></div>
            </div>
          )}

          {/* PMH — ICD-10 Coded */}
          {activeSection === "pmh" && (
            <div className="bg-white rounded-lg border p-4 space-y-3">
              <div className="flex items-center justify-between"><h3 className="text-base font-semibold text-gray-900">Past Medical History</h3><span className="px-2 py-0.5 rounded text-[10px] font-medium bg-gray-100 text-gray-500">ICD-10</span></div>
              <div className="relative"><Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" /><input type="text" placeholder="Search ICD-10 conditions..." value={conditionSearch} onChange={e => setConditionSearch(e.target.value)} className="w-full h-10 pl-10 pr-3 text-sm rounded-md border border-gray-300 bg-white" /></div>
              {selectedConditions.length > 0 && <div className="flex flex-wrap gap-2">{selectedConditions.map(c => (
                <span key={c.code} className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-700 rounded text-xs font-medium"><span className="font-mono text-[10px]">{c.code}</span> {c.display}<button type="button" onClick={() => setSelectedConditions(prev => prev.filter(x => x.code !== c.code))} className="ml-1 hover:bg-blue-200 rounded-full p-0.5"><X className="w-3 h-3" /></button></span>
              ))}</div>}
              <div className="max-h-64 overflow-y-auto space-y-1">{filteredConditions.map(c => {
                const selected = selectedConditions.some(s => s.code === c.code);
                return <button key={c.code} type="button" onClick={() => selected ? setSelectedConditions(prev => prev.filter(x => x.code !== c.code)) : setSelectedConditions(prev => [...prev, c])} className={`w-full flex items-center justify-between p-2.5 rounded-lg text-left text-sm transition-all ${selected ? "bg-blue-50 border border-blue-200" : "hover:bg-gray-50"}`}><span className="font-medium">{c.display}</span><span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-gray-100 text-gray-500">{c.code}</span></button>;
              })}</div>
              {selectedConditions.length > 0 && <div className="space-y-2 pt-2 border-t">{selectedConditions.map(c => (
                <div key={c.code} className="flex items-center gap-3"><span className="text-sm font-medium flex-1">{c.display}</span>
                  <select className="h-8 px-2 text-xs rounded border border-gray-300 bg-white" onChange={e => update(`pmh_status_${c.code}`, e.target.value)}><option value="active">Active</option><option value="resolved">Resolved</option><option value="in_remission">In Remission</option></select>
                  <input type="number" placeholder="Year Dx" min={1950} max={2026} className="w-24 h-8 px-2 text-xs rounded border border-gray-300 bg-white" onChange={e => update(`pmh_year_${c.code}`, e.target.value)} />
                </div>
              ))}</div>}
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save PMH</button></div>
            </div>
          )}

          {/* Surgical History */}
          {activeSection === "psh" && (
            <div className="bg-white rounded-lg border p-4 space-y-3">
              <h3 className="text-base font-semibold text-gray-900">Past Surgical History</h3>
              {[1, 2, 3].map(i => (
                <div key={i} className="grid grid-cols-3 gap-3 p-3 bg-gray-50 rounded-lg">
                  <SelectField label={`Procedure ${i}`} value={formData[`psh_proc_${i}`] || ""} onChange={v => update(`psh_proc_${i}`, v)} options={["", "Appendicectomy", "Cholecystectomy", "Caesarean Section", "Hernia Repair", "Hysterectomy", "CABG", "Valve Replacement", "Joint Replacement", "Fracture Fixation", "Laparotomy", "Thoracotomy", "Craniotomy", "Other"]} />
                  <NumberInput label="Year" value={formData[`psh_year_${i}`] || ""} onChange={v => update(`psh_year_${i}`, v)} />
                  <SelectField label="Complications" value={formData[`psh_comp_${i}`] || ""} onChange={v => update(`psh_comp_${i}`, v)} options={["None", "Infection", "Bleeding", "DVT/PE", "Anaesthetic complication", "Other"]} />
                </div>
              ))}
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save Surgical Hx</button></div>
            </div>
          )}

          {/* Family History */}
          {activeSection === "fhx" && (
            <div className="bg-white rounded-lg border p-4 space-y-3">
              <h3 className="text-base font-semibold text-gray-900">Family History</h3>
              {[
                { condition: "Hypertension", field: "fhx_htn" }, { condition: "Diabetes Mellitus", field: "fhx_dm" },
                { condition: "Ischaemic Heart Disease", field: "fhx_ihd" }, { condition: "Stroke / CVA", field: "fhx_cva" },
                { condition: "Cancer (any)", field: "fhx_ca" }, { condition: "Asthma / Atopy", field: "fhx_asthma" },
                { condition: "Mental Health Disorder", field: "fhx_psych" }, { condition: "Tuberculosis", field: "fhx_tb" },
                { condition: "Sickle Cell / Thalassaemia", field: "fhx_haem" },
              ].map(item => (
                <div key={item.field} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <span className="text-sm font-medium text-gray-700">{item.condition}</span>
                  <div className="flex gap-1.5">{["None", "Father", "Mother", "Sibling", "Multiple"].map(rel => (
                    <button key={rel} type="button" onClick={() => update(item.field, rel)} className={`px-3 py-1.5 rounded text-xs font-medium transition-all ${formData[item.field] === rel ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-600 hover:bg-gray-300"}`}>{rel}</button>
                  ))}</div>
                </div>
              ))}
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save Family Hx</button></div>
            </div>
          )}

          {/* Social History */}
          {activeSection === "shx" && (
            <div className="bg-white rounded-lg border p-4 space-y-3">
              <h3 className="text-base font-semibold text-gray-900">Social History</h3>
              <SelectField label="Occupation" value={formData.shx_occupation || ""} onChange={v => update("shx_occupation", v)} options={["Employed", "Unemployed", "Student", "Retired", "Self-employed", "Homemaker"]} />
              <SelectField label="Smoking Status" value={formData.shx_smoking || ""} onChange={v => update("shx_smoking", v)} options={["Never smoked", "Current smoker", "Ex-smoker (<1yr)", "Ex-smoker (>1yr)"]} />
              {formData.shx_smoking?.includes("smoker") && <NumberInput label="Pack-Years" value={formData.shx_pack_years || ""} onChange={v => update("shx_pack_years", v)} />}
              <SelectField label="Alcohol Use" value={formData.shx_alcohol || ""} onChange={v => update("shx_alcohol", v)} options={["None", "Social (<14 units/wk)", "Moderate (14-21 units/wk)", "Heavy (>21 units/wk)", "Dependent"]} />
              <SelectField label="Recreational Drug Use" value={formData.shx_drugs || ""} onChange={v => update("shx_drugs", v)} options={["None", "Cannabis", "Cocaine", "Heroin/Opioids", "Methamphetamine", "Multiple", "Declined to answer"]} />
              <SelectField label="Exercise" value={formData.shx_exercise || ""} onChange={v => update("shx_exercise", v)} options={["Sedentary", "Light (1-2x/wk)", "Moderate (3-4x/wk)", "Active (5+/wk)"]} />
              <SelectField label="Diet" value={formData.shx_diet || ""} onChange={v => update("shx_diet", v)} options={["Balanced", "Vegetarian", "Vegan", "High salt/fat", "Restricted — medical"]} />
              <SelectField label="Living Situation" value={formData.shx_living || ""} onChange={v => update("shx_living", v)} options={["Lives alone", "With spouse/partner", "With family", "Assisted living", "Homeless", "Institution"]} />
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save Social Hx</button></div>
            </div>
          )}

          {/* Obs/Gyn */}
          {activeSection === "obgyn" && (
            <div className="bg-white rounded-lg border p-4 space-y-4">
              <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><Baby className="w-5 h-5" /> Obstetric & Gynaecological History</h3>
              <div className="grid grid-cols-4 gap-3">{[{ label: "Gravida", field: "obs_gravida" }, { label: "Para", field: "obs_para" }, { label: "Abortions", field: "obs_abortions" }, { label: "Living Children", field: "obs_living" }].map(item => (
                <div key={item.field} className="space-y-1.5"><label className="text-xs font-bold text-gray-500 uppercase">{item.label}</label><input type="number" min={0} max={20} value={formData[item.field] || ""} onChange={e => update(item.field, e.target.value)} className="w-full h-12 px-3 text-center text-2xl font-bold rounded-md border border-gray-300 bg-white" /></div>
              ))}</div>
              <SelectField label="Menarche Age" value={formData.obs_menarche || ""} onChange={v => update("obs_menarche", v)} options={Array.from({ length: 10 }, (_, i) => `${i + 9} years`)} />
              <SelectField label="Menstrual Cycle" value={formData.obs_cycle || ""} onChange={v => update("obs_cycle", v)} options={["Regular (28±7 days)", "Irregular", "Amenorrhoea", "Post-menopausal", "On contraception"]} />
              <SelectField label="Contraception" value={formData.obs_contraception || ""} onChange={v => update("obs_contraception", v)} options={["None", "OCP", "Injectable", "IUD/IUS", "Implant", "Condom", "Sterilised", "Natural methods"]} />
              <SelectField label="Last Cervical Screening" value={formData.obs_pap || ""} onChange={v => update("obs_pap", v)} options={["Within 1 year", "1-3 years ago", "3-5 years ago", ">5 years / Never", "Unknown"]} />
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save Obs/Gyn</button></div>
            </div>
          )}

          {/* Drug History */}
          {activeSection === "drugs" && (
            <div className="bg-white rounded-lg border p-4 space-y-3">
              <div className="flex items-center justify-between"><h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><Pill className="w-5 h-5" /> Current Medications</h3><span className="px-2 py-0.5 rounded text-[10px] font-medium bg-gray-100 text-gray-500">ATC coded</span></div>
              {[1, 2, 3, 4, 5].map(i => (
                <div key={i} className="grid grid-cols-4 gap-2 p-2.5 bg-gray-50 rounded-lg">
                  <div className="col-span-1 space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Medication</label><input type="text" placeholder={`Drug ${i}`} value={formData[`drug_name_${i}`] || ""} onChange={e => update(`drug_name_${i}`, e.target.value)} className="w-full h-9 px-2 text-sm rounded border border-gray-300 bg-white" /></div>
                  <div className="space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Dose</label><input type="text" placeholder="e.g. 500mg" value={formData[`drug_dose_${i}`] || ""} onChange={e => update(`drug_dose_${i}`, e.target.value)} className="w-full h-9 px-2 text-sm rounded border border-gray-300 bg-white" /></div>
                  <div className="space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Route</label><select value={formData[`drug_route_${i}`] || ""} onChange={e => update(`drug_route_${i}`, e.target.value)} className="w-full h-9 px-2 text-xs rounded border border-gray-300 bg-white"><option value="">—</option><option>PO</option><option>IV</option><option>IM</option><option>SC</option><option>INH</option><option>TOP</option><option>PR</option><option>SL</option></select></div>
                  <div className="space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Frequency</label><select value={formData[`drug_freq_${i}`] || ""} onChange={e => update(`drug_freq_${i}`, e.target.value)} className="w-full h-9 px-2 text-xs rounded border border-gray-300 bg-white"><option value="">—</option><option>OD</option><option>BD</option><option>TDS</option><option>QDS</option><option>PRN</option><option>Nocte</option><option>Stat</option><option>Weekly</option></select></div>
                </div>
              ))}
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save Medications</button></div>
            </div>
          )}

          {/* Allergies */}
          {activeSection === "allergies" && (
            <div className="bg-white rounded-lg border border-amber-200 p-4 space-y-3">
              <h3 className="text-base font-semibold text-amber-700 flex items-center gap-2"><AlertTriangle className="w-5 h-5" /> Allergies & Adverse Reactions</h3>
              <div className="flex gap-3 mb-2">{["NKDA", "Unable to assess", "Has allergies"].map(opt => (
                <button key={opt} type="button" onClick={() => update("allergy_status", opt)} className={`px-4 py-2 rounded-lg text-sm font-medium transition-all ${formData.allergy_status === opt ? opt === "Has allergies" ? "bg-amber-100 border-2 border-amber-400 text-amber-800" : "bg-blue-50 border-2 border-blue-300 text-blue-700" : "bg-gray-100 border-2 border-transparent hover:bg-gray-200 text-gray-600"}`}>{opt}</button>
              ))}</div>
              {formData.allergy_status === "Has allergies" && [1, 2, 3].map(i => (
                <div key={i} className="grid grid-cols-4 gap-2 p-3 bg-amber-50 border border-amber-100 rounded-lg">
                  <div className="space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Allergen</label><input type="text" placeholder="e.g. Penicillin" value={formData[`allergen_${i}`] || ""} onChange={e => update(`allergen_${i}`, e.target.value)} className="w-full h-9 px-2 text-sm rounded border border-gray-300 bg-white" /></div>
                  <div className="space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Type</label><select value={formData[`allergen_type_${i}`] || ""} onChange={e => update(`allergen_type_${i}`, e.target.value)} className="w-full h-9 px-2 text-xs rounded border border-gray-300 bg-white"><option value="">—</option><option>Drug</option><option>Food</option><option>Environmental</option><option>Latex</option><option>Other</option></select></div>
                  <div className="space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Reaction</label><select value={formData[`allergen_reaction_${i}`] || ""} onChange={e => update(`allergen_reaction_${i}`, e.target.value)} className="w-full h-9 px-2 text-xs rounded border border-gray-300 bg-white"><option value="">—</option><option>Rash</option><option>Urticaria</option><option>Anaphylaxis</option><option>Angioedema</option><option>GI upset</option><option>Bronchospasm</option><option>Other</option></select></div>
                  <div className="space-y-1"><label className="text-[10px] font-bold text-gray-500 uppercase">Severity</label><select value={formData[`allergen_sev_${i}`] || ""} onChange={e => update(`allergen_sev_${i}`, e.target.value)} className="w-full h-9 px-2 text-xs rounded border border-gray-300 bg-white"><option value="">—</option><option>Mild</option><option>Moderate</option><option>Severe</option><option>Life-threatening</option></select></div>
                </div>
              ))}
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save Allergies</button></div>
            </div>
          )}

          {/* Review of Systems */}
          {activeSection === "ros" && (
            <div className="bg-white rounded-lg border p-4 space-y-4">
              <h3 className="text-base font-semibold text-gray-900">Review of Systems</h3>
              {[
                { title: "General", items: ["Fever", "Weight loss", "Fatigue", "Night sweats", "Appetite change"] },
                { title: "HEENT", items: ["Headache", "Vision changes", "Hearing loss", "Sore throat", "Nasal congestion"] },
                { title: "Cardiovascular", items: ["Chest pain", "Palpitations", "Orthopnoea", "PND", "Leg swelling"] },
                { title: "Respiratory", items: ["Cough", "Dyspnoea", "Wheezing", "Haemoptysis", "Pleuritic pain"] },
                { title: "GI", items: ["Nausea", "Vomiting", "Diarrhoea", "Constipation", "Abdominal pain", "Melaena"] },
                { title: "GU", items: ["Dysuria", "Frequency", "Urgency", "Haematuria", "Incontinence"] },
                { title: "MSK", items: ["Joint pain", "Muscle weakness", "Back pain", "Stiffness", "Swelling"] },
                { title: "Neuro", items: ["Numbness", "Tingling", "Weakness", "Seizures", "Dizziness", "Syncope"] },
                { title: "Psychiatric", items: ["Depression", "Anxiety", "Sleep disturbance", "Mood changes"] },
                { title: "Skin", items: ["Rash", "Pruritus", "Lesions", "Colour changes"] },
              ].map(system => (
                <div key={system.title} className="space-y-1.5">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">{system.title}</label>
                  <div className="flex flex-wrap gap-1.5">
                    {system.items.map(item => {
                      const checked = (checkedItems[`ros_${system.title}`] || []).includes(item);
                      return <button key={item} type="button" onClick={() => toggleCheck(`ros_${system.title}`, item)} className={`px-3 py-1.5 rounded-full text-xs font-medium transition-all ${checked ? "bg-amber-100 border border-amber-300 text-amber-800" : "bg-gray-100 border border-transparent text-gray-600 hover:bg-gray-200"}`}>{checked && "⚠ "}{item}</button>;
                    })}
                  </div>
                </div>
              ))}
              <div className="flex justify-end"><button type="button" onClick={saveSection} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> Save ROS</button></div>
            </div>
          )}
        </>
      )}

      {/* Saved Entries Summary */}
      {savedEntries.length > 0 && (
        <div className="bg-white rounded-lg border p-4">
          <h3 className="text-base font-semibold text-gray-900 mb-3">Documented History</h3>
          <div className="space-y-2">{savedEntries.map((entry, i) => (
            <div key={i} className="flex items-center justify-between p-3 bg-green-50 border border-green-200 rounded-lg">
              <div className="flex items-center gap-2"><CheckCircle2 className="w-5 h-5 text-green-600" /><span className="text-sm font-medium capitalize">{entry.section.replace(/_/g, " ")}</span></div>
              <span className="text-xs text-gray-500">{entry.time.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
            </div>
          ))}</div>
        </div>
      )}
    </div>
  );
}
