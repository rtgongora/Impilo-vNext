"use client";

/**
 * MedscapeTools — Drug database, interaction checker, clinical calculators, conditions browser.
 * Ported from Lovable prototype's Medscape-style clinical tools integration.
 */

import { useState, useMemo, useCallback } from "react";
import {
  Pill, Search, AlertTriangle, AlertCircle, Info, X, Plus, Trash2,
  Stethoscope, ClipboardList, Calculator, BookOpen, ChevronRight,
  User, FileWarning,
} from "lucide-react";

// ── Patient Context (mock — in production fetched from encounter) ──
function usePatientContext() {
  const currentMedications = ["Metformin", "Amlodipine", "Hydrochlorothiazide"];
  const allergies = ["Penicillin", "Sulfa drugs"];
  const activeConditions = [
    { name: "Type 2 Diabetes Mellitus", icd10: "E11" },
    { name: "Essential Hypertension", icd10: "I10" },
  ];
  const recentVitals = { weight: 72, height: 165, serumCreatinine: 1.1, age: 65 };
  return { currentMedications, allergies, activeConditions, recentVitals };
}

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
  { name: "Ceftriaxone", genericName: "Ceftriaxone sodium", drugClass: "Third-Gen Cephalosporin", route: "IV/IM", commonDoses: ["1g OD IV", "2g OD IV (severe)"], sideEffects: ["Diarrhoea", "Rash", "Biliary sludge", "Phlebitis"], contraindications: ["Cephalosporin allergy", "Neonates with jaundice", "IV calcium co-admin in neonates"], interactions: ["Calcium-containing IV solutions", "Warfarin"], pregnancyCategory: "B" },
  { name: "Salbutamol", genericName: "Salbutamol sulphate", drugClass: "Short-Acting Beta-2 Agonist", route: "Inhaled/Nebulised/IV", commonDoses: ["2-4 puffs PRN", "2.5-5mg nebulised Q4-6H"], sideEffects: ["Tremor", "Tachycardia", "Hypokalaemia", "Palpitations"], contraindications: ["Hypertrophic cardiomyopathy"], interactions: ["Beta-blockers (antagonism)", "Digoxin"], pregnancyCategory: "C" },
  { name: "Morphine", genericName: "Morphine sulphate", drugClass: "Opioid Analgesic", route: "Oral/IV/IM/SC", commonDoses: ["2.5-10mg IV Q4H PRN", "10-30mg PO Q4H"], sideEffects: ["Respiratory depression", "Nausea", "Constipation", "Sedation", "Pruritus"], contraindications: ["Respiratory depression", "Acute abdomen (relative)", "Head injury with raised ICP"], interactions: ["Benzodiazepines (respiratory depression)", "MAOIs", "CNS depressants"], pregnancyCategory: "C" },
  { name: "Warfarin", genericName: "Warfarin sodium", drugClass: "Vitamin K Antagonist", route: "Oral", commonDoses: ["Start 5mg OD, titrate to INR 2-3"], sideEffects: ["Bleeding", "Skin necrosis (rare)", "Purple toe syndrome (rare)"], contraindications: ["Active bleeding", "Pregnancy (1st/3rd trimester)", "Severe liver disease"], interactions: ["NSAIDs", "Amiodarone", "Rifampicin", "Cranberry juice", "Numerous others"], pregnancyCategory: "X" },
  { name: "Cotrimoxazole", genericName: "Sulfamethoxazole/Trimethoprim", drugClass: "Sulfonamide Antibiotic", route: "Oral/IV", commonDoses: ["960mg BD (treatment)", "480mg OD (prophylaxis)"], sideEffects: ["Rash", "GI upset", "Stevens-Johnson (rare)", "Bone marrow suppression"], contraindications: ["Sulfa allergy", "Severe renal impairment", "Megaloblastic anaemia due to folate deficiency"], interactions: ["Methotrexate", "Warfarin", "Phenytoin"], pregnancyCategory: "D" },
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
  { code: "J18", display: "Community-Acquired Pneumonia", category: "Respiratory" },
  { code: "I26", display: "Pulmonary Embolism", category: "Respiratory" },
  { code: "I82", display: "Deep Vein Thrombosis", category: "Cardiovascular" },
  { code: "I63", display: "Ischaemic Stroke", category: "Neurological" },
  { code: "G43", display: "Migraine", category: "Neurological" },
  { code: "B50", display: "Plasmodium Falciparum Malaria", category: "Infectious" },
  { code: "A41", display: "Sepsis", category: "Infectious" },
  { code: "G03", display: "Bacterial Meningitis", category: "Infectious" },
  { code: "K27", display: "Peptic Ulcer Disease", category: "Gastrointestinal" },
  { code: "K81", display: "Acute Cholecystitis", category: "Gastrointestinal" },
  { code: "K35", display: "Acute Appendicitis", category: "Gastrointestinal" },
  { code: "K74", display: "Cirrhosis of Liver", category: "Gastrointestinal" },
  { code: "E03", display: "Hypothyroidism", category: "Endocrine" },
  { code: "E10.1", display: "Diabetic Ketoacidosis", category: "Endocrine" },
  { code: "O14", display: "Pre-eclampsia", category: "Obstetric" },
  { code: "O72", display: "Postpartum Haemorrhage", category: "Obstetric" },
  { code: "O00", display: "Ectopic Pregnancy", category: "Obstetric" },
  { code: "M15", display: "Osteoarthritis", category: "Musculoskeletal" },
  { code: "M10", display: "Gout", category: "Musculoskeletal" },
];

type ToolTab = "drugs" | "interactions" | "calculators" | "conditions";

interface MedscapeToolsProps { open: boolean; onClose: () => void; toolId?: string | null; complexity?: string; }

function resolveInitialTab(toolId?: string | null): ToolTab {
  if (!toolId) return "drugs";
  if (toolId.startsWith("calc-")) return "calculators";
  if (toolId.startsWith("drug-interaction") || toolId.startsWith("drug-allergy") || toolId.startsWith("food-drug")) return "interactions";
  if (toolId.startsWith("disease") || toolId.startsWith("icd")) return "conditions";
  return "drugs";
}

export function MedscapeTools({ open, onClose, toolId }: MedscapeToolsProps) {
  const { currentMedications, allergies, activeConditions, recentVitals } = usePatientContext();
  const [activeTab, setActiveTab] = useState<ToolTab>(resolveInitialTab(toolId));
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
  // Wells DVT Score
  const [wellsItems, setWellsItems] = useState<string[]>([]);
  // CHA2DS2-VASc
  const [chadItems, setChadItems] = useState<string[]>([]);
  // CURB-65
  const [curbItems, setCurbItems] = useState<string[]>([]);
  // qSOFA
  const [qsofaItems, setQsofaItems] = useState<string[]>([]);

  const filteredDrugs = useMemo(() => DRUG_DATABASE.filter(d =>
    d.name.toLowerCase().includes(drugSearch.toLowerCase()) ||
    d.genericName.toLowerCase().includes(drugSearch.toLowerCase()) ||
    d.drugClass.toLowerCase().includes(drugSearch.toLowerCase())
  ), [drugSearch]);

  // Allergy conflict detection
  const hasAllergyConflict = useCallback((drug: Drug) => {
    const lowerAllergies = allergies.map(a => a.toLowerCase());
    if (lowerAllergies.includes("penicillin") && drug.drugClass.toLowerCase().includes("penicillin")) return true;
    if (lowerAllergies.includes("sulfa drugs") && drug.name.toLowerCase().includes("sulfa")) return true;
    return drug.contraindications.some(c => lowerAllergies.some(a => c.toLowerCase().includes(a)));
  }, [allergies]);

  const isCurrentMed = useCallback((drug: Drug) => currentMedications.includes(drug.name), [currentMedications]);

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
              {/* Patient Context Banners */}
              {currentMedications.length > 0 && (
                <div className="p-2.5 rounded-lg bg-blue-50 border border-blue-100">
                  <div className="flex items-center gap-1.5 mb-1"><User className="h-3.5 w-3.5 text-blue-600" /><span className="text-[10px] font-semibold text-blue-600 uppercase tracking-wider">Current Medications</span></div>
                  <div className="flex flex-wrap gap-1">{currentMedications.map(m => <span key={m} className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded text-[10px] font-medium">{m}</span>)}</div>
                </div>
              )}
              {allergies.length > 0 && (
                <div className="p-2.5 rounded-lg bg-red-50 border border-red-100">
                  <div className="flex items-center gap-1.5 mb-1"><FileWarning className="h-3.5 w-3.5 text-red-600" /><span className="text-[10px] font-semibold text-red-600 uppercase tracking-wider">Known Allergies</span></div>
                  <div className="flex flex-wrap gap-1">{allergies.map(a => <span key={a} className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-[10px] font-medium">{a}</span>)}</div>
                </div>
              )}
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
                  {filteredDrugs.map(d => {
                    const allergyFlag = hasAllergyConflict(d);
                    const currentMedFlag = isCurrentMed(d);
                    return (
                    <button key={d.name} onClick={() => setSelectedDrug(d)} className={`w-full flex items-center justify-between p-3 rounded-lg text-left transition-colors ${allergyFlag ? "bg-red-50 border border-red-200 hover:bg-red-100" : "hover:bg-gray-50"}`}>
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-medium text-gray-900">{d.name}</p>
                          {currentMedFlag && <span className="px-1.5 py-0 bg-blue-100 text-blue-700 rounded text-[9px] font-medium">CURRENT</span>}
                          {allergyFlag && <span className="px-1.5 py-0 bg-red-100 text-red-700 rounded text-[9px] font-medium flex items-center gap-0.5"><AlertTriangle className="w-3 h-3" />ALLERGY</span>}
                        </div>
                        <p className="text-xs text-gray-500">{d.drugClass} &middot; {d.route}</p>
                      </div>
                      <ChevronRight className="w-4 h-4 text-gray-400" />
                    </button>
                    );
                  })}
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
              {/* Wells DVT Score */}
              <div className="border rounded-lg p-3">
                <h4 className="text-sm font-semibold text-gray-900 mb-2">Wells DVT Score</h4>
                {[
                  { id: "cancer", label: "Active cancer (within 6 months)", pts: 1 },
                  { id: "paralysis", label: "Paralysis, paresis, or recent cast", pts: 1 },
                  { id: "bedridden", label: "Bedridden >3 days or surgery within 12 weeks", pts: 1 },
                  { id: "tenderness", label: "Localised tenderness along deep veins", pts: 1 },
                  { id: "swelling", label: "Entire leg swollen", pts: 1 },
                  { id: "calf", label: "Calf swelling >3cm compared to other leg", pts: 1 },
                  { id: "pitting", label: "Pitting oedema (symptomatic leg only)", pts: 1 },
                  { id: "collateral", label: "Collateral superficial veins (non-varicose)", pts: 1 },
                  { id: "previous", label: "Previously documented DVT", pts: 1 },
                  { id: "alternative", label: "Alternative diagnosis equally likely", pts: -2 },
                ].map(item => (
                  <label key={item.id} className="flex items-center justify-between px-2 py-1.5 rounded hover:bg-gray-50 cursor-pointer">
                    <div className="flex items-center gap-2"><input type="checkbox" checked={wellsItems.includes(item.id)} onChange={() => setWellsItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-blue-600" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">{item.pts > 0 ? `+${item.pts}` : item.pts}</span>
                  </label>
                ))}
                {wellsItems.length > 0 && (() => { const score = wellsItems.reduce((sum, id) => sum + ({ cancer: 1, paralysis: 1, bedridden: 1, tenderness: 1, swelling: 1, calf: 1, pitting: 1, collateral: 1, previous: 1, alternative: -2 }[id] || 0), 0); return <div className="mt-2 p-2 bg-blue-50 rounded text-center"><span className="text-lg font-bold text-blue-700">{score}</span><p className="text-[10px] text-blue-500">{score >= 3 ? "High probability" : score >= 1 ? "Moderate probability" : "Low probability"}</p></div>; })()}
              </div>
              {/* CHA2DS2-VASc */}
              <div className="border rounded-lg p-3">
                <h4 className="text-sm font-semibold text-gray-900 mb-2">CHA₂DS₂-VASc Score</h4>
                {[
                  { id: "chf", label: "CHF / LV dysfunction", pts: 1 },
                  { id: "htn", label: "Hypertension", pts: 1 },
                  { id: "age75", label: "Age ≥75", pts: 2 },
                  { id: "dm", label: "Diabetes mellitus", pts: 1 },
                  { id: "stroke", label: "Stroke / TIA / thromboembolism", pts: 2 },
                  { id: "vasc", label: "Vascular disease (MI, PAD, aortic plaque)", pts: 1 },
                  { id: "age65", label: "Age 65-74", pts: 1 },
                  { id: "female", label: "Sex category (female)", pts: 1 },
                ].map(item => (
                  <label key={item.id} className="flex items-center justify-between px-2 py-1.5 rounded hover:bg-gray-50 cursor-pointer">
                    <div className="flex items-center gap-2"><input type="checkbox" checked={chadItems.includes(item.id)} onChange={() => setChadItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-blue-600" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">+{item.pts}</span>
                  </label>
                ))}
                {chadItems.length > 0 && (() => { const pts: Record<string, number> = { chf: 1, htn: 1, age75: 2, dm: 1, stroke: 2, vasc: 1, age65: 1, female: 1 }; const score = chadItems.reduce((sum, id) => sum + (pts[id] || 0), 0); return <div className="mt-2 p-2 bg-blue-50 rounded text-center"><span className="text-lg font-bold text-blue-700">{score}</span><p className="text-[10px] text-blue-500">{score >= 2 ? "Anticoagulation recommended" : score === 1 ? "Consider anticoagulation" : "No anticoagulation needed"}</p></div>; })()}
              </div>
              {/* CURB-65 */}
              <div className="border rounded-lg p-3">
                <h4 className="text-sm font-semibold text-gray-900 mb-2">CURB-65 (Pneumonia Severity)</h4>
                {[
                  { id: "confusion", label: "Confusion (AMT ≤8 or new disorientation)", pts: 1 },
                  { id: "urea", label: "Urea >7 mmol/L", pts: 1 },
                  { id: "rr", label: "Respiratory rate ≥30/min", pts: 1 },
                  { id: "bp", label: "BP: systolic <90 or diastolic ≤60", pts: 1 },
                  { id: "age65c", label: "Age ≥65", pts: 1 },
                ].map(item => (
                  <label key={item.id} className="flex items-center justify-between px-2 py-1.5 rounded hover:bg-gray-50 cursor-pointer">
                    <div className="flex items-center gap-2"><input type="checkbox" checked={curbItems.includes(item.id)} onChange={() => setCurbItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-blue-600" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">+1</span>
                  </label>
                ))}
                {curbItems.length > 0 && (() => { const score = curbItems.length; return <div className="mt-2 p-2 bg-blue-50 rounded text-center"><span className="text-lg font-bold text-blue-700">{score}</span><p className="text-[10px] text-blue-500">{score >= 3 ? "Severe — consider ICU" : score === 2 ? "Moderate — consider admission" : "Mild — consider outpatient"}</p></div>; })()}
              </div>
              {/* qSOFA Score */}
              <div className="border rounded-lg p-3">
                <h4 className="text-sm font-semibold text-gray-900 mb-2">qSOFA (Quick Sepsis Assessment)</h4>
                {[
                  { id: "sbp", label: "Systolic BP ≤100 mmHg", pts: 1 },
                  { id: "rr", label: "Respiratory rate ≥22/min", pts: 1 },
                  { id: "gcs", label: "Altered mental status (GCS <15)", pts: 1 },
                ].map(item => (
                  <label key={item.id} className="flex items-center justify-between px-2 py-1.5 rounded hover:bg-gray-50 cursor-pointer">
                    <div className="flex items-center gap-2"><input type="checkbox" checked={qsofaItems.includes(item.id)} onChange={() => setQsofaItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-blue-600" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">+1</span>
                  </label>
                ))}
                {qsofaItems.length > 0 && <div className="mt-2 p-2 bg-blue-50 rounded text-center"><span className="text-lg font-bold text-blue-700">{qsofaItems.length}</span><p className="text-[10px] text-blue-500">{qsofaItems.length >= 2 ? "Sepsis likely — assess organ dysfunction (full SOFA)" : "Low risk — monitor clinically"}</p></div>}
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
