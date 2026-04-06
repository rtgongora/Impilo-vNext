"use client";

/**
 * MedscapeTools — Drug database, interaction checker, clinical calculators, conditions browser.
 * Ported from Lovable prototype's Medscape-style clinical tools integration.
 */

import { useState, useMemo, useCallback } from "react";
import {
  Pill, Search, AlertTriangle, AlertCircle, Info, X, Plus, Trash2,
  Stethoscope, ClipboardList, Calculator, BookOpen, ChevronRight,
} from "lucide-react";

// ── Drug Database ──────────────────────────────────────────
interface Drug {
  name: string; genericName: string; drugClass: string; route: string;
  commonDoses: string[]; sideEffects: string[]; contraindications: string[];
  interactions: string[]; pregnancyCategory: string;
}

const DRUG_DATABASE: Drug[] = [
  { name: "Metformin", genericName: "Metformin HCl", drugClass: "Biguanide", route: "Oral", commonDoses: ["500mg BD", "850mg BD", "1000mg BD"], sideEffects: ["Nausea", "Diarrhoea", "Lactic acidosis (rare)"], contraindications: ["eGFR <30", "Hepatic impairment", "Acute MI"], interactions: ["Contrast dye", "Alcohol"], pregnancyCategory: "B" },
  { name: "Amlodipine", genericName: "Amlodipine besylate", drugClass: "CCB (Dihydropyridine)", route: "Oral", commonDoses: ["5mg OD", "10mg OD"], sideEffects: ["Peripheral oedema", "Headache", "Flushing"], contraindications: ["Severe aortic stenosis", "Cardiogenic shock"], interactions: ["Simvastatin (max 20mg)", "CYP3A4 inhibitors"], pregnancyCategory: "C" },
  { name: "Enalapril", genericName: "Enalapril maleate", drugClass: "ACE Inhibitor", route: "Oral", commonDoses: ["5mg OD", "10mg BD", "20mg BD"], sideEffects: ["Dry cough", "Hyperkalaemia", "Angioedema"], contraindications: ["Pregnancy", "Bilateral renal artery stenosis", "History of angioedema"], interactions: ["K+ supplements", "NSAIDs", "Lithium"], pregnancyCategory: "D" },
  { name: "Hydrochlorothiazide", genericName: "Hydrochlorothiazide", drugClass: "Thiazide Diuretic", route: "Oral", commonDoses: ["12.5mg OD", "25mg OD"], sideEffects: ["Hypokalaemia", "Hyperuricaemia", "Photosensitivity"], contraindications: ["Anuria", "Severe renal impairment"], interactions: ["Lithium", "Digoxin", "NSAIDs"], pregnancyCategory: "B" },
  { name: "Amoxicillin", genericName: "Amoxicillin trihydrate", drugClass: "Penicillin", route: "Oral", commonDoses: ["250mg TDS", "500mg TDS", "1g TDS"], sideEffects: ["Diarrhoea", "Rash", "Nausea"], contraindications: ["Penicillin allergy"], interactions: ["Methotrexate", "Warfarin"], pregnancyCategory: "B" },
  { name: "Paracetamol", genericName: "Acetaminophen", drugClass: "Analgesic/Antipyretic", route: "Oral/IV/Rectal", commonDoses: ["500mg-1g Q4-6H", "Max 4g/day"], sideEffects: ["Hepatotoxicity (overdose)", "Rare allergic reactions"], contraindications: ["Severe hepatic impairment"], interactions: ["Warfarin (high doses)", "Alcohol"], pregnancyCategory: "B" },
  { name: "Omeprazole", genericName: "Omeprazole", drugClass: "PPI", route: "Oral", commonDoses: ["20mg OD", "40mg OD"], sideEffects: ["Headache", "Diarrhoea", "B12 deficiency (long-term)"], contraindications: ["Rilpivirine co-admin"], interactions: ["Clopidogrel", "Methotrexate", "Diazepam"], pregnancyCategory: "C" },
  { name: "Atorvastatin", genericName: "Atorvastatin calcium", drugClass: "HMG-CoA Reductase Inhibitor", route: "Oral", commonDoses: ["10mg OD", "20mg OD", "40mg OD", "80mg OD"], sideEffects: ["Myalgia", "Elevated LFTs", "Rhabdomyolysis (rare)"], contraindications: ["Active liver disease", "Pregnancy"], interactions: ["Clarithromycin", "Grapefruit", "Gemfibrozil"], pregnancyCategory: "X" },
  { name: "Tenofovir/Lamivudine/Dolutegravir", genericName: "TLD", drugClass: "Antiretroviral (NRTI+INSTI)", route: "Oral", commonDoses: ["1 tablet OD"], sideEffects: ["Weight gain", "Insomnia", "Renal toxicity (tenofovir)"], contraindications: ["CrCl <50 (tenofovir)", "Co-admin with dofetilide"], interactions: ["Rifampicin (double DTG dose)", "Antacids (separate by 2h)"], pregnancyCategory: "B" },
  { name: "Rifampicin", genericName: "Rifampicin", drugClass: "Rifamycin", route: "Oral", commonDoses: ["450mg OD (<50kg)", "600mg OD (≥50kg)"], sideEffects: ["Orange discolouration of fluids", "Hepatotoxicity", "Thrombocytopaenia"], contraindications: ["Jaundice", "Porphyria"], interactions: ["Warfarin", "OCP", "Dolutegravir", "Protease inhibitors"], pregnancyCategory: "C" },
];

// ── Interaction Database ─────────────────────────────────
interface Interaction { drug1: string; drug2: string; severity: "major" | "moderate" | "minor"; description: string; }

const INTERACTIONS: Interaction[] = [
  { drug1: "Metformin", drug2: "Contrast dye", severity: "major", description: "Risk of lactic acidosis. Hold metformin 48h before and after iodinated contrast." },
  { drug1: "Enalapril", drug2: "Hydrochlorothiazide", severity: "moderate", description: "Enhanced hypotensive effect. Monitor BP closely when initiating combination." },
  { drug1: "Amlodipine", drug2: "Atorvastatin", severity: "moderate", description: "Amlodipine may increase atorvastatin levels. Limit atorvastatin to 40mg max." },
  { drug1: "Rifampicin", drug2: "Tenofovir/Lamivudine/Dolutegravir", severity: "major", description: "Rifampicin induces CYP3A4/UGT1A1, reducing dolutegravir levels by 54%. Double DTG dose to 50mg BD." },
  { drug1: "Omeprazole", drug2: "Tenofovir/Lamivudine/Dolutegravir", severity: "minor", description: "Antacids/PPIs may reduce dolutegravir absorption. Separate administration by 2 hours." },
  { drug1: "Enalapril", drug2: "Paracetamol", severity: "minor", description: "High-dose paracetamol may slightly reduce antihypertensive effect." },
  { drug1: "Amoxicillin", drug2: "Methotrexate", severity: "major", description: "Penicillins decrease renal clearance of methotrexate. Monitor levels and toxicity." },
];

// ── ICD-10 Conditions ────────────────────────────────────
const ICD10_CONDITIONS = [
  { code: "E11", display: "Type 2 Diabetes Mellitus", category: "Endocrine" },
  { code: "I10", display: "Essential Hypertension", category: "Cardiovascular" },
  { code: "J45", display: "Asthma", category: "Respiratory" },
  { code: "B20", display: "HIV Disease", category: "Infectious" },
  { code: "A15", display: "Respiratory Tuberculosis", category: "Infectious" },
  { code: "I25", display: "Chronic Ischaemic Heart Disease", category: "Cardiovascular" },
  { code: "J44", display: "COPD", category: "Respiratory" },
  { code: "N18", display: "Chronic Kidney Disease", category: "Renal" },
  { code: "F32", display: "Major Depressive Disorder", category: "Psychiatric" },
  { code: "I50", display: "Heart Failure", category: "Cardiovascular" },
  { code: "G40", display: "Epilepsy", category: "Neurological" },
  { code: "D50", display: "Iron Deficiency Anaemia", category: "Haematological" },
  { code: "K21", display: "GORD", category: "Gastrointestinal" },
  { code: "M06", display: "Rheumatoid Arthritis", category: "Musculoskeletal" },
  { code: "E05", display: "Thyrotoxicosis", category: "Endocrine" },
  { code: "O80", display: "Single Spontaneous Delivery", category: "Obstetric" },
  { code: "P07", display: "Disorders Related to Short Gestation/Low Birth Weight", category: "Perinatal" },
  { code: "R50", display: "Fever of Unknown Origin", category: "Symptoms" },
];

type ToolTab = "drugs" | "interactions" | "calculators" | "conditions";

interface MedscapeToolsProps { open: boolean; onClose: () => void; }

export function MedscapeTools({ open, onClose }: MedscapeToolsProps) {
  const [activeTab, setActiveTab] = useState<ToolTab>("drugs");
  const [drugSearch, setDrugSearch] = useState("");
  const [selectedDrug, setSelectedDrug] = useState<Drug | null>(null);
  const [interactionDrugs, setInteractionDrugs] = useState<string[]>([]);
  const [interactionSearch, setInteractionSearch] = useState("");
  const [conditionSearch, setConditionSearch] = useState("");
  // Calculator state
  const [calcWeight, setCalcWeight] = useState("");
  const [calcHeight, setCalcHeight] = useState("");
  const [calcAge, setCalcAge] = useState("");
  const [calcCreatinine, setCalcCreatinine] = useState("");
  const [calcSex, setCalcSex] = useState<"male" | "female">("male");

  const filteredDrugs = useMemo(() => DRUG_DATABASE.filter(d =>
    d.name.toLowerCase().includes(drugSearch.toLowerCase()) ||
    d.genericName.toLowerCase().includes(drugSearch.toLowerCase()) ||
    d.drugClass.toLowerCase().includes(drugSearch.toLowerCase())
  ), [drugSearch]);

  const filteredConditions = useMemo(() => ICD10_CONDITIONS.filter(c =>
    c.display.toLowerCase().includes(conditionSearch.toLowerCase()) ||
    c.code.toLowerCase().includes(conditionSearch.toLowerCase()) ||
    c.category.toLowerCase().includes(conditionSearch.toLowerCase())
  ), [conditionSearch]);

  const foundInteractions = useMemo(() => {
    if (interactionDrugs.length < 2) return [];
    return INTERACTIONS.filter(i =>
      interactionDrugs.some(d => d.toLowerCase() === i.drug1.toLowerCase()) &&
      interactionDrugs.some(d => d.toLowerCase() === i.drug2.toLowerCase())
    );
  }, [interactionDrugs]);

  const addInteractionDrug = useCallback((name: string) => {
    if (!interactionDrugs.includes(name)) setInteractionDrugs(prev => [...prev, name]);
    setInteractionSearch("");
  }, [interactionDrugs]);

  // Calculators
  const bmi = useMemo(() => {
    const w = parseFloat(calcWeight), h = parseFloat(calcHeight) / 100;
    if (!w || !h) return null;
    return (w / (h * h)).toFixed(1);
  }, [calcWeight, calcHeight]);

  const gfr = useMemo(() => {
    const age = parseFloat(calcAge), cr = parseFloat(calcCreatinine);
    if (!age || !cr) return null;
    // CKD-EPI 2021
    const k = calcSex === "female" ? 0.7 : 0.9;
    const a = calcSex === "female" ? -0.241 : -0.302;
    const scr_k = cr / k;
    const eGFR = 142 * Math.pow(Math.min(scr_k, 1), a) * Math.pow(Math.max(scr_k, 1), -1.200) * Math.pow(0.9938, age) * (calcSex === "female" ? 1.012 : 1);
    return Math.round(eGFR);
  }, [calcAge, calcCreatinine, calcSex]);

  if (!open) return null;

  const tabs: { id: ToolTab; label: string; icon: React.ElementType }[] = [
    { id: "drugs", label: "Drug Database", icon: Pill },
    { id: "interactions", label: "Interactions", icon: AlertTriangle },
    { id: "calculators", label: "Calculators", icon: Calculator },
    { id: "conditions", label: "Conditions", icon: BookOpen },
  ];

  const severityColor = { major: "bg-red-100 text-red-700 border-red-200", moderate: "bg-amber-100 text-amber-700 border-amber-200", minor: "bg-blue-100 text-blue-700 border-blue-200" };

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/30" onClick={onClose} />
      <div className="w-[520px] bg-white shadow-xl flex flex-col h-full">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">Clinical Tools</h2>
            <p className="text-xs text-gray-500">Drug database, interactions & calculators</p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-gray-100"><X className="w-4 h-4" /></button>
        </div>

        {/* Tabs */}
        <div className="flex border-b px-2 gap-1">
          {tabs.map(t => (
            <button key={t.id} onClick={() => setActiveTab(t.id)} className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${activeTab === t.id ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"}`}>
              <t.icon className="w-3.5 h-3.5" /> {t.label}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto">
          {activeTab === "drugs" && (
            <div className="p-4 space-y-3">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input value={drugSearch} onChange={e => setDrugSearch(e.target.value)} placeholder="Search drugs by name or class..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              {selectedDrug ? (
                <div className="space-y-3">
                  <button onClick={() => setSelectedDrug(null)} className="text-xs text-blue-600 hover:underline">&larr; Back to list</button>
                  <h3 className="text-base font-semibold text-gray-900">{selectedDrug.name}</h3>
                  <p className="text-xs text-gray-500">{selectedDrug.genericName} &middot; {selectedDrug.drugClass} &middot; {selectedDrug.route} &middot; Pregnancy: {selectedDrug.pregnancyCategory}</p>
                  <div className="space-y-2">
                    {[{ l: "Common Doses", items: selectedDrug.commonDoses, c: "bg-blue-50 text-blue-700" }, { l: "Side Effects", items: selectedDrug.sideEffects, c: "bg-amber-50 text-amber-700" }, { l: "Contraindications", items: selectedDrug.contraindications, c: "bg-red-50 text-red-700" }, { l: "Interactions", items: selectedDrug.interactions, c: "bg-purple-50 text-purple-700" }].map(s => (
                      <div key={s.l}><p className="text-[10px] font-semibold text-gray-400 uppercase mb-1">{s.l}</p><div className="flex flex-wrap gap-1">{s.items.map(i => <span key={i} className={`px-2 py-0.5 rounded text-xs ${s.c}`}>{i}</span>)}</div></div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="space-y-1">
                  {filteredDrugs.map(d => (
                    <button key={d.name} onClick={() => setSelectedDrug(d)} className="w-full flex items-center justify-between p-3 rounded-lg hover:bg-gray-50 text-left">
                      <div><p className="text-sm font-medium text-gray-900">{d.name}</p><p className="text-xs text-gray-500">{d.drugClass} &middot; {d.route}</p></div>
                      <ChevronRight className="w-4 h-4 text-gray-400" />
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === "interactions" && (
            <div className="p-4 space-y-3">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input value={interactionSearch} onChange={e => setInteractionSearch(e.target.value)} placeholder="Add drug to check..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              {interactionSearch && (
                <div className="border rounded-lg max-h-32 overflow-y-auto">
                  {DRUG_DATABASE.filter(d => d.name.toLowerCase().includes(interactionSearch.toLowerCase())).map(d => (
                    <button key={d.name} onClick={() => addInteractionDrug(d.name)} className="w-full px-3 py-2 text-sm text-left hover:bg-gray-50 flex items-center gap-2">
                      <Plus className="w-3 h-3 text-gray-400" /> {d.name}
                    </button>
                  ))}
                </div>
              )}
              <div className="flex flex-wrap gap-1">
                {interactionDrugs.map(d => (
                  <span key={d} className="inline-flex items-center gap-1 px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs">
                    {d} <button onClick={() => setInteractionDrugs(prev => prev.filter(x => x !== d))}><Trash2 className="w-3 h-3" /></button>
                  </span>
                ))}
              </div>
              {interactionDrugs.length >= 2 && (
                <div className="space-y-2">
                  {foundInteractions.length === 0 ? (
                    <div className="p-4 bg-green-50 rounded-lg text-center"><p className="text-sm text-green-700 font-medium">No known interactions found</p></div>
                  ) : foundInteractions.map((ix, i) => (
                    <div key={i} className={`p-3 rounded-lg border ${severityColor[ix.severity]}`}>
                      <div className="flex items-center gap-2 mb-1">
                        <AlertCircle className="w-4 h-4" />
                        <span className="text-xs font-semibold uppercase">{ix.severity}</span>
                        <span className="text-xs">{ix.drug1} + {ix.drug2}</span>
                      </div>
                      <p className="text-xs">{ix.description}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === "calculators" && (
            <div className="p-4 space-y-4">
              {/* BMI */}
              <div className="border rounded-lg p-3">
                <h4 className="text-sm font-semibold text-gray-900 mb-2">BMI Calculator</h4>
                <div className="grid grid-cols-2 gap-2">
                  <div><label className="text-[10px] text-gray-500">Weight (kg)</label><input value={calcWeight} onChange={e => setCalcWeight(e.target.value)} type="number" className="w-full px-2 py-1.5 text-sm border rounded" /></div>
                  <div><label className="text-[10px] text-gray-500">Height (cm)</label><input value={calcHeight} onChange={e => setCalcHeight(e.target.value)} type="number" className="w-full px-2 py-1.5 text-sm border rounded" /></div>
                </div>
                {bmi && <div className="mt-2 p-2 bg-blue-50 rounded text-center"><span className="text-lg font-bold text-blue-700">{bmi}</span><span className="text-xs text-blue-600 ml-1">kg/m²</span><p className="text-[10px] text-blue-500">{parseFloat(bmi) < 18.5 ? "Underweight" : parseFloat(bmi) < 25 ? "Normal" : parseFloat(bmi) < 30 ? "Overweight" : "Obese"}</p></div>}
              </div>
              {/* eGFR */}
              <div className="border rounded-lg p-3">
                <h4 className="text-sm font-semibold text-gray-900 mb-2">eGFR (CKD-EPI 2021)</h4>
                <div className="grid grid-cols-3 gap-2">
                  <div><label className="text-[10px] text-gray-500">Age</label><input value={calcAge} onChange={e => setCalcAge(e.target.value)} type="number" className="w-full px-2 py-1.5 text-sm border rounded" /></div>
                  <div><label className="text-[10px] text-gray-500">Creatinine (mg/dL)</label><input value={calcCreatinine} onChange={e => setCalcCreatinine(e.target.value)} type="number" step="0.1" className="w-full px-2 py-1.5 text-sm border rounded" /></div>
                  <div><label className="text-[10px] text-gray-500">Sex</label><select value={calcSex} onChange={e => setCalcSex(e.target.value as "male"|"female")} className="w-full px-2 py-1.5 text-sm border rounded"><option value="male">Male</option><option value="female">Female</option></select></div>
                </div>
                {gfr !== null && <div className="mt-2 p-2 bg-blue-50 rounded text-center"><span className="text-lg font-bold text-blue-700">{gfr}</span><span className="text-xs text-blue-600 ml-1">mL/min/1.73m²</span><p className="text-[10px] text-blue-500">{gfr >= 90 ? "G1 Normal" : gfr >= 60 ? "G2 Mild" : gfr >= 45 ? "G3a Mild-Moderate" : gfr >= 30 ? "G3b Moderate-Severe" : gfr >= 15 ? "G4 Severe" : "G5 Kidney Failure"}</p></div>}
              </div>
            </div>
          )}

          {activeTab === "conditions" && (
            <div className="p-4 space-y-3">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input value={conditionSearch} onChange={e => setConditionSearch(e.target.value)} placeholder="Search ICD-10 conditions..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div className="space-y-1">
                {filteredConditions.map(c => (
                  <div key={c.code} className="flex items-center justify-between p-3 rounded-lg hover:bg-gray-50">
                    <div><p className="text-sm font-medium text-gray-900">{c.display}</p><p className="text-xs text-gray-500">{c.code} &middot; {c.category}</p></div>
                    <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-xs font-mono">{c.code}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
