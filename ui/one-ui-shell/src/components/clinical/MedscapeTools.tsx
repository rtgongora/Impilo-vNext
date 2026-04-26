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
  schedule: string; indication: string; dosing: string;
  commonDoses: string[]; sideEffects: string[]; contraindications: string[];
  interactions: string[]; pregnancyCategory: string;
}

const DRUG_DATABASE: Drug[] = [
  {
    name: "Metformin",
    genericName: "Metformin Hydrochloride",
    drugClass: "Biguanide / Antidiabetic",
    route: "Oral",
    schedule: "Prescription",
    indication: "Type 2 diabetes mellitus; first-line agent for glycaemic control",
    dosing: "500 mg BD initially, titrate to max 1000 mg BD. Take with meals.",
    commonDoses: ["500mg BD", "850mg BD", "1000mg BD"],
    sideEffects: ["Nausea", "Diarrhoea", "Abdominal pain", "Lactic acidosis (rare)", "Vitamin B12 deficiency"],
    contraindications: ["eGFR <30 mL/min", "Metabolic acidosis", "Severe hepatic impairment", "Acute decompensated HF"],
    interactions: ["Contrast dye (withhold 48h)", "Alcohol (lactic acidosis risk)", "Carbonic anhydrase inhibitors"],
    pregnancyCategory: "B — Generally considered safe",
  },
  {
    name: "Amlodipine",
    genericName: "Amlodipine Besylate",
    drugClass: "Calcium Channel Blocker",
    route: "Oral",
    schedule: "Prescription",
    indication: "Hypertension, chronic stable angina, vasospastic angina",
    dosing: "5 mg OD initially, max 10 mg OD",
    commonDoses: ["5mg OD", "10mg OD"],
    sideEffects: ["Peripheral oedema", "Dizziness", "Flushing", "Palpitations", "Fatigue"],
    contraindications: ["Severe aortic stenosis", "Cardiogenic shock", "Unstable angina"],
    interactions: ["Simvastatin (max 20 mg)", "CYP3A4 inhibitors", "Cyclosporine"],
    pregnancyCategory: "C — Use only if benefit outweighs risk",
  },
  {
    name: "Enalapril",
    genericName: "Enalapril Maleate",
    drugClass: "ACE Inhibitor",
    route: "Oral",
    schedule: "Prescription",
    indication: "Hypertension, heart failure, diabetic nephropathy",
    dosing: "5 mg OD initially, titrate to 10-20 mg BD. Monitor renal function and K+.",
    commonDoses: ["5mg OD", "10mg BD", "20mg BD"],
    sideEffects: ["Dry cough", "Hyperkalaemia", "Angioedema", "Dizziness", "Renal impairment"],
    contraindications: ["Pregnancy", "Bilateral renal artery stenosis", "History of angioedema"],
    interactions: ["K+ supplements", "NSAIDs", "Lithium", "Aliskiren"],
    pregnancyCategory: "D — Contraindicated in pregnancy",
  },
  {
    name: "Hydrochlorothiazide",
    genericName: "Hydrochlorothiazide",
    drugClass: "Thiazide Diuretic",
    route: "Oral",
    schedule: "Prescription",
    indication: "Hypertension, oedema in heart failure, nephrotic syndrome",
    dosing: "12.5-25 mg OD in the morning. Monitor electrolytes.",
    commonDoses: ["12.5mg OD", "25mg OD"],
    sideEffects: ["Hypokalaemia", "Hyperuricaemia", "Photosensitivity", "Hyponatraemia"],
    contraindications: ["Anuria", "Severe renal impairment", "Addison's disease"],
    interactions: ["Lithium", "Digoxin", "NSAIDs"],
    pregnancyCategory: "B — Generally safe",
  },
  {
    name: "Amoxicillin",
    genericName: "Amoxicillin Trihydrate",
    drugClass: "Penicillin Antibiotic",
    route: "Oral",
    schedule: "Prescription",
    indication: "Bacterial infections: otitis media, pneumonia, UTI, H. pylori eradication",
    dosing: "250-500 mg TDS or 1 g TDS for severe infections. Complete the course.",
    commonDoses: ["250mg TDS", "500mg TDS", "1g TDS"],
    sideEffects: ["Diarrhoea", "Rash", "Nausea", "Candidiasis", "Anaphylaxis (rare)"],
    contraindications: ["Penicillin allergy", "Infectious mononucleosis (rash risk)"],
    interactions: ["Methotrexate (increased toxicity)", "Warfarin (increased INR)", "OCP (reduced efficacy)"],
    pregnancyCategory: "B — Generally safe",
  },
  {
    name: "Paracetamol",
    genericName: "Acetaminophen",
    drugClass: "Analgesic / Antipyretic",
    route: "Oral/IV/Rectal",
    schedule: "OTC / Prescription",
    indication: "Mild-moderate pain, fever reduction, osteoarthritis",
    dosing: "500 mg-1 g Q4-6H. Max 4 g/day (2 g/day in hepatic impairment).",
    commonDoses: ["500mg-1g Q4-6H", "Max 4g/day"],
    sideEffects: ["Hepatotoxicity (overdose)", "Rare allergic reactions", "Thrombocytopaenia (rare)"],
    contraindications: ["Severe hepatic impairment", "Active liver disease"],
    interactions: ["Warfarin (high doses)", "Alcohol", "Isoniazid"],
    pregnancyCategory: "B — Generally safe",
  },
  {
    name: "Omeprazole",
    genericName: "Omeprazole",
    drugClass: "Proton Pump Inhibitor",
    route: "Oral",
    schedule: "Prescription / OTC",
    indication: "GORD, peptic ulcer disease, H. pylori eradication, Zollinger-Ellison syndrome",
    dosing: "20 mg OD for maintenance, 40 mg OD for active ulcer. Take before breakfast.",
    commonDoses: ["20mg OD", "40mg OD"],
    sideEffects: ["Headache", "Diarrhoea", "B12 deficiency (long-term)", "Hypomagnesaemia", "C. diff risk"],
    contraindications: ["Rilpivirine co-admin", "Hypersensitivity to PPIs"],
    interactions: ["Clopidogrel (reduced activation)", "Methotrexate", "Diazepam", "Phenytoin"],
    pregnancyCategory: "C — Use with caution",
  },
  {
    name: "Atorvastatin",
    genericName: "Atorvastatin Calcium",
    drugClass: "HMG-CoA Reductase Inhibitor (Statin)",
    route: "Oral",
    schedule: "Prescription",
    indication: "Hyperlipidaemia, primary prevention of CVD, secondary prevention post-ACS",
    dosing: "10-20 mg OD initially, max 80 mg OD. Take at any time of day.",
    commonDoses: ["10mg OD", "20mg OD", "40mg OD", "80mg OD"],
    sideEffects: ["Myalgia", "Elevated LFTs", "Rhabdomyolysis (rare)", "Diabetes risk"],
    contraindications: ["Active liver disease", "Pregnancy", "Breastfeeding"],
    interactions: ["Clarithromycin", "Grapefruit", "Gemfibrozil", "Ciclosporin"],
    pregnancyCategory: "X — Contraindicated",
  },
  {
    name: "Tenofovir/Lamivudine/Dolutegravir",
    genericName: "TLD (Fixed-Dose Combination)",
    drugClass: "Antiretroviral (NRTI + INSTI)",
    route: "Oral",
    schedule: "Prescription",
    indication: "HIV-1 infection — first-line ART per WHO and Zimbabwe national guidelines",
    dosing: "1 tablet OD. No food restriction. Double DTG dose to 50 mg BD if on rifampicin.",
    commonDoses: ["1 tablet OD"],
    sideEffects: ["Weight gain", "Insomnia", "Renal toxicity (tenofovir)", "Hepatotoxicity"],
    contraindications: ["CrCl <50 mL/min (tenofovir component)", "Co-admin with dofetilide"],
    interactions: ["Rifampicin (double DTG dose)", "Antacids (separate by 2h)", "Iron/calcium supplements"],
    pregnancyCategory: "B — Recommended for PMTCT",
  },
  {
    name: "Rifampicin",
    genericName: "Rifampicin",
    drugClass: "Rifamycin Antibiotic",
    route: "Oral",
    schedule: "Prescription",
    indication: "Tuberculosis (all forms), leprosy, serious staphylococcal infections",
    dosing: "450 mg OD (<50 kg) or 600 mg OD (≥50 kg). Take on empty stomach 30 min before food.",
    commonDoses: ["450mg OD (<50kg)", "600mg OD (≥50kg)"],
    sideEffects: ["Orange discolouration of bodily fluids", "Hepatotoxicity", "Thrombocytopaenia", "Flu-like syndrome"],
    contraindications: ["Jaundice", "Porphyria", "Concurrent ritonavir/saquinavir"],
    interactions: ["Warfarin", "OCP", "Dolutegravir", "Protease inhibitors", "Many CYP3A4 substrates"],
    pregnancyCategory: "C — Use if benefit outweighs risk",
  },
  {
    name: "Ceftriaxone",
    genericName: "Ceftriaxone Sodium",
    drugClass: "Third-Generation Cephalosporin",
    route: "IV/IM",
    schedule: "Prescription",
    indication: "Serious bacterial infections: meningitis, pneumonia, UTI, gonorrhoea, sepsis",
    dosing: "1 g OD IV/IM. 2 g OD for meningitis/severe sepsis. Max 4 g/day.",
    commonDoses: ["1g OD IV", "2g OD IV (severe)"],
    sideEffects: ["Diarrhoea", "Rash", "Biliary sludge", "Phlebitis", "C. difficile"],
    contraindications: ["Cephalosporin allergy", "Neonates with jaundice", "IV calcium co-admin in neonates"],
    interactions: ["Calcium-containing IV solutions", "Warfarin"],
    pregnancyCategory: "B — Generally safe",
  },
  {
    name: "Salbutamol",
    genericName: "Salbutamol Sulphate",
    drugClass: "Short-Acting Beta-2 Agonist (SABA)",
    route: "Inhaled/Nebulised/IV",
    schedule: "Prescription",
    indication: "Acute bronchospasm in asthma and COPD, acute hyperkalaemia",
    dosing: "2-4 puffs MDI PRN via spacer. Nebulised: 2.5-5 mg Q4-6H. IV: 5 mcg/min.",
    commonDoses: ["2-4 puffs PRN", "2.5-5mg nebulised Q4-6H"],
    sideEffects: ["Tremor", "Tachycardia", "Hypokalaemia", "Palpitations"],
    contraindications: ["Hypertrophic cardiomyopathy"],
    interactions: ["Beta-blockers (antagonism)", "Digoxin", "Theophylline"],
    pregnancyCategory: "C — Use if benefit outweighs risk",
  },
  {
    name: "Morphine",
    genericName: "Morphine Sulphate",
    drugClass: "Opioid Analgesic",
    route: "Oral/IV/IM/SC",
    schedule: "Schedule 7 (Controlled Substance)",
    indication: "Severe pain, acute MI, pulmonary oedema, palliative care",
    dosing: "IV: 2.5-10 mg Q4H PRN (titrate to effect). PO: 10-30 mg Q4H. Reduce in elderly/renal impairment.",
    commonDoses: ["2.5-10mg IV Q4H PRN", "10-30mg PO Q4H"],
    sideEffects: ["Respiratory depression", "Nausea", "Constipation", "Sedation", "Pruritus", "Dependence"],
    contraindications: ["Respiratory depression", "Acute abdomen (relative)", "Head injury with raised ICP", "Paralytic ileus"],
    interactions: ["Benzodiazepines (respiratory depression)", "MAOIs (serotonin syndrome)", "CNS depressants"],
    pregnancyCategory: "C — Use with extreme caution",
  },
  {
    name: "Warfarin",
    genericName: "Warfarin Sodium",
    drugClass: "Vitamin K Antagonist (Anticoagulant)",
    route: "Oral",
    schedule: "Prescription",
    indication: "DVT/PE treatment & prophylaxis, AF stroke prevention, prosthetic heart valves",
    dosing: "Start 5 mg OD, titrate to target INR 2-3 (2.5-3.5 for mechanical valves). Check INR regularly.",
    commonDoses: ["Start 5mg OD, titrate to INR 2-3"],
    sideEffects: ["Bleeding", "Skin necrosis (rare)", "Purple toe syndrome (rare)", "Teratogenicity"],
    contraindications: ["Active bleeding", "Pregnancy (1st/3rd trimester)", "Severe liver disease", "Recent neurosurgery"],
    interactions: ["NSAIDs", "Amiodarone", "Rifampicin", "Cranberry juice", "Numerous others"],
    pregnancyCategory: "X — Contraindicated",
  },
  {
    name: "Cotrimoxazole",
    genericName: "Sulfamethoxazole/Trimethoprim",
    drugClass: "Sulfonamide Antibiotic / Folate Inhibitor",
    route: "Oral/IV",
    schedule: "Prescription",
    indication: "PJP prophylaxis (HIV), UTI, toxoplasmosis, Nocardia, Isospora",
    dosing: "960 mg BD for treatment, 480 mg OD for prophylaxis. Ensure adequate hydration.",
    commonDoses: ["960mg BD (treatment)", "480mg OD (prophylaxis)"],
    sideEffects: ["Rash", "GI upset", "Stevens-Johnson syndrome (rare)", "Bone marrow suppression", "Hyperkalaemia"],
    contraindications: ["Sulfa allergy", "Severe renal impairment", "Megaloblastic anaemia due to folate deficiency"],
    interactions: ["Methotrexate", "Warfarin", "Phenytoin", "ACE inhibitors (hyperkalaemia)"],
    pregnancyCategory: "D — Avoid if possible",
  },
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

  const severityColor = { major: "bg-red-100 text-red-700 border-red-200", moderate: "bg-amber-100 text-amber-700 border-amber-200", minor: "bg-impilo-100 text-impilo-600 border-impilo-200" };

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
            <button key={t.id} onClick={() => setActiveTab(t.id)} className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${activeTab === t.id ? "border-impilo-500 text-impilo-500" : "border-transparent text-gray-500 hover:text-gray-700"}`}>
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
                <div className="p-2.5 rounded-lg bg-impilo-50 border border-impilo-100">
                  <div className="flex items-center gap-1.5 mb-1"><User className="h-3.5 w-3.5 text-impilo-500" /><span className="text-[10px] font-semibold text-impilo-500 uppercase tracking-wider">Current Medications</span></div>
                  <div className="flex flex-wrap gap-1">{currentMedications.map(m => <span key={m} className="px-2 py-0.5 bg-impilo-100 text-impilo-600 rounded text-[10px] font-medium">{m}</span>)}</div>
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
                <input value={drugSearch} onChange={e => setDrugSearch(e.target.value)} placeholder="Search drugs by name or class..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-impilo-400" />
              </div>
              {selectedDrug ? (
                <DrugMonograph drug={selectedDrug} onBack={() => setSelectedDrug(null)} isCurrentMed={isCurrentMed(selectedDrug)} hasAllergyConflict={hasAllergyConflict(selectedDrug)} />
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
                          {currentMedFlag && <span className="px-1.5 py-0 bg-impilo-100 text-impilo-600 rounded text-[9px] font-medium">CURRENT</span>}
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
              {/* Auto-load patient medications */}
              {currentMedications.length > 0 && interactionDrugs.length === 0 && (
                <button onClick={() => { setInteractionDrugs([...currentMedications]); }} className="w-full flex items-center justify-center gap-2 px-3 py-2.5 bg-impilo-50 border border-impilo-200 rounded-lg text-sm font-medium text-impilo-600 hover:bg-impilo-100 transition-colors">
                  <User className="w-4 h-4" /> Load patient&apos;s {currentMedications.length} active medications
                </button>
              )}
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input value={interactionSearch} onChange={e => setInteractionSearch(e.target.value)} placeholder="Add drug to check..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-impilo-400" />
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
                  <span key={d} className="inline-flex items-center gap-1 px-2 py-1 bg-impilo-50 text-impilo-600 rounded text-xs">
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
                {bmi && <div className="mt-2 p-2 bg-impilo-50 rounded text-center"><span className="text-lg font-bold text-impilo-600">{bmi}</span><span className="text-xs text-impilo-500 ml-1">kg/m²</span><p className="text-[10px] text-impilo-400">{parseFloat(bmi) < 18.5 ? "Underweight" : parseFloat(bmi) < 25 ? "Normal" : parseFloat(bmi) < 30 ? "Overweight" : "Obese"}</p></div>}
              </div>
              {/* eGFR */}
              <div className="border rounded-lg p-3">
                <h4 className="text-sm font-semibold text-gray-900 mb-2">eGFR (CKD-EPI 2021)</h4>
                <div className="grid grid-cols-3 gap-2">
                  <div><label className="text-[10px] text-gray-500">Age</label><input value={calcAge} onChange={e => setCalcAge(e.target.value)} type="number" className="w-full px-2 py-1.5 text-sm border rounded" /></div>
                  <div><label className="text-[10px] text-gray-500">Creatinine (mg/dL)</label><input value={calcCreatinine} onChange={e => setCalcCreatinine(e.target.value)} type="number" step="0.1" className="w-full px-2 py-1.5 text-sm border rounded" /></div>
                  <div><label className="text-[10px] text-gray-500">Sex</label><select value={calcSex} onChange={e => setCalcSex(e.target.value as "male"|"female")} className="w-full px-2 py-1.5 text-sm border rounded"><option value="male">Male</option><option value="female">Female</option></select></div>
                </div>
                {gfr !== null && <div className="mt-2 p-2 bg-impilo-50 rounded text-center"><span className="text-lg font-bold text-impilo-600">{gfr}</span><span className="text-xs text-impilo-500 ml-1">mL/min/1.73m²</span><p className="text-[10px] text-impilo-400">{gfr >= 90 ? "G1 Normal" : gfr >= 60 ? "G2 Mild" : gfr >= 45 ? "G3a Mild-Moderate" : gfr >= 30 ? "G3b Moderate-Severe" : gfr >= 15 ? "G4 Severe" : "G5 Kidney Failure"}</p></div>}
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
                    <div className="flex items-center gap-2"><input type="checkbox" checked={wellsItems.includes(item.id)} onChange={() => setWellsItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-impilo-500" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">{item.pts > 0 ? `+${item.pts}` : item.pts}</span>
                  </label>
                ))}
                {wellsItems.length > 0 && (() => { const score = wellsItems.reduce((sum, id) => sum + ({ cancer: 1, paralysis: 1, bedridden: 1, tenderness: 1, swelling: 1, calf: 1, pitting: 1, collateral: 1, previous: 1, alternative: -2 }[id] || 0), 0); return <div className="mt-2 p-2 bg-impilo-50 rounded text-center"><span className="text-lg font-bold text-impilo-600">{score}</span><p className="text-[10px] text-impilo-400">{score >= 3 ? "High probability" : score >= 1 ? "Moderate probability" : "Low probability"}</p></div>; })()}
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
                    <div className="flex items-center gap-2"><input type="checkbox" checked={chadItems.includes(item.id)} onChange={() => setChadItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-impilo-500" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">+{item.pts}</span>
                  </label>
                ))}
                {chadItems.length > 0 && (() => { const pts: Record<string, number> = { chf: 1, htn: 1, age75: 2, dm: 1, stroke: 2, vasc: 1, age65: 1, female: 1 }; const score = chadItems.reduce((sum, id) => sum + (pts[id] || 0), 0); return <div className="mt-2 p-2 bg-impilo-50 rounded text-center"><span className="text-lg font-bold text-impilo-600">{score}</span><p className="text-[10px] text-impilo-400">{score >= 2 ? "Anticoagulation recommended" : score === 1 ? "Consider anticoagulation" : "No anticoagulation needed"}</p></div>; })()}
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
                    <div className="flex items-center gap-2"><input type="checkbox" checked={curbItems.includes(item.id)} onChange={() => setCurbItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-impilo-500" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">+1</span>
                  </label>
                ))}
                {curbItems.length > 0 && (() => { const score = curbItems.length; return <div className="mt-2 p-2 bg-impilo-50 rounded text-center"><span className="text-lg font-bold text-impilo-600">{score}</span><p className="text-[10px] text-impilo-400">{score >= 3 ? "Severe — consider ICU" : score === 2 ? "Moderate — consider admission" : "Mild — consider outpatient"}</p></div>; })()}
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
                    <div className="flex items-center gap-2"><input type="checkbox" checked={qsofaItems.includes(item.id)} onChange={() => setQsofaItems(prev => prev.includes(item.id) ? prev.filter(x => x !== item.id) : [...prev, item.id])} className="rounded border-gray-300 text-impilo-500" /><span className="text-xs text-gray-700">{item.label}</span></div>
                    <span className="text-xs text-gray-400">+1</span>
                  </label>
                ))}
                {qsofaItems.length > 0 && <div className="mt-2 p-2 bg-impilo-50 rounded text-center"><span className="text-lg font-bold text-impilo-600">{qsofaItems.length}</span><p className="text-[10px] text-impilo-400">{qsofaItems.length >= 2 ? "Sepsis likely — assess organ dysfunction (full SOFA)" : "Low risk — monitor clinically"}</p></div>}
              </div>
            </div>
          )}

          {activeTab === "conditions" && (
            <div className="p-4 space-y-3">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input value={conditionSearch} onChange={e => setConditionSearch(e.target.value)} placeholder="Search ICD-10 conditions..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-impilo-400" />
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

// ── Drug Monograph Detail View ─────────────────────────
function DrugMonograph({ drug, onBack, isCurrentMed, hasAllergyConflict }: {
  drug: Drug; onBack: () => void; isCurrentMed: boolean; hasAllergyConflict: boolean;
}) {
  const [monographTab, setMonographTab] = useState<"overview" | "dosing" | "safety" | "interactions">("overview");

  return (
    <div className="p-4 space-y-4">
      <button onClick={onBack} className="text-xs text-impilo-500 hover:underline">&larr; Back to results</button>

      {hasAllergyConflict && (
        <div className="p-3 rounded-lg bg-red-50 border border-red-200 flex items-start gap-2">
          <AlertTriangle className="h-4 w-4 text-red-600 mt-0.5 shrink-0" />
          <div><p className="text-sm font-semibold text-red-800">Allergy Alert</p><p className="text-xs text-red-700">This patient has a known allergy that may conflict with this medication. Review before prescribing.</p></div>
        </div>
      )}

      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-lg font-bold text-gray-900">{drug.name}</h2>
          {isCurrentMed && <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded text-[10px] font-medium">Patient is on this medication</span>}
        </div>
        <p className="text-sm text-gray-500">{drug.genericName}</p>
        <div className="flex gap-2 mt-2">
          <span className="px-2 py-0.5 bg-gray-100 text-gray-700 rounded text-xs">{drug.drugClass}</span>
          <span className="px-2 py-0.5 border border-gray-200 text-gray-600 rounded text-xs">{drug.schedule}</span>
          <span className="px-2 py-0.5 border border-gray-200 text-gray-600 rounded text-[10px]">{drug.route}</span>
        </div>
      </div>

      {/* Monograph tabs */}
      <div className="border-b">
        <div className="flex">
          {(["overview", "dosing", "safety", "interactions"] as const).map(tab => (
            <button key={tab} onClick={() => setMonographTab(tab)} className={`px-3 py-2 text-xs font-medium border-b-2 capitalize transition-colors ${monographTab === tab ? "border-impilo-500 text-impilo-500" : "border-transparent text-gray-500 hover:text-gray-700"}`}>{tab}</button>
          ))}
        </div>
      </div>

      {monographTab === "overview" && (
        <div className="space-y-3">
          <div className="border rounded-lg p-3"><h4 className="text-sm font-semibold text-gray-900 mb-1">Indication</h4><p className="text-sm text-gray-700">{drug.indication}</p></div>
          <div className="border rounded-lg p-3"><h4 className="text-sm font-semibold text-gray-900 mb-1">Pregnancy</h4><p className="text-sm text-gray-700">Category {drug.pregnancyCategory}</p></div>
          <div className="border rounded-lg p-3"><h4 className="text-sm font-semibold text-gray-900 mb-1">Common Doses</h4><div className="flex flex-wrap gap-1">{drug.commonDoses.map(d => <span key={d} className="px-2 py-0.5 bg-impilo-50 text-impilo-600 rounded text-xs">{d}</span>)}</div></div>
        </div>
      )}

      {monographTab === "dosing" && (
        <div className="border rounded-lg p-3"><h4 className="text-sm font-semibold text-gray-900 mb-2">Dosing</h4><p className="text-sm text-gray-700 whitespace-pre-line">{drug.dosing}</p></div>
      )}

      {monographTab === "safety" && (
        <div className="space-y-3">
          <div className="border rounded-lg p-3">
            <h4 className="text-sm font-semibold text-gray-900 mb-2">Contraindications</h4>
            <ul className="space-y-1">{drug.contraindications.map(c => <li key={c} className="text-sm flex items-start gap-2"><AlertTriangle className="h-3.5 w-3.5 text-red-500 mt-0.5 shrink-0" />{c}</li>)}</ul>
          </div>
          <div className="border rounded-lg p-3">
            <h4 className="text-sm font-semibold text-gray-900 mb-2">Side Effects</h4>
            <div className="flex flex-wrap gap-1.5">{drug.sideEffects.map(s => <span key={s} className="px-2 py-0.5 bg-amber-50 text-amber-700 rounded text-xs">{s}</span>)}</div>
          </div>
        </div>
      )}

      {monographTab === "interactions" && (
        <div className="border rounded-lg p-3">
          <h4 className="text-sm font-semibold text-gray-900 mb-2">Known Interactions</h4>
          <ul className="space-y-1.5">{drug.interactions.map(i => <li key={i} className="text-sm flex items-start gap-2"><AlertCircle className="h-3.5 w-3.5 text-amber-500 mt-0.5 shrink-0" />{i}</li>)}</ul>
        </div>
      )}
    </div>
  );
}
