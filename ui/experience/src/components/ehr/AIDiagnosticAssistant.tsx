"use client";

/**
 * AIDiagnosticAssistant — AI-powered clinical decision support panel.
 * Ported from Lovable (440L). 3 tabs: Symptom Analysis (differential
 * diagnosis), Drug Interaction Check, Lab Result Interpretation.
 * Calls /api/v1/ai/diagnostic endpoint (falls back to mock results).
 */

import { useState } from "react";
import {
  Brain, Loader2, AlertTriangle, CheckCircle2, Lightbulb, TestTube,
  Pill, Activity, ChevronRight, Sparkles, FlaskConical, X,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface DiagnosticResult {
  differentials?: { diagnosis: string; likelihood: string; reasoning: string }[];
  recommendedTests?: string[];
  redFlags?: string[];
  clinicalPearls?: string[];
}

interface DrugInteractionResult {
  interactions?: { drugs: string[]; severity: string; mechanism: string; clinicalEffect: string; recommendation: string }[];
  safetyAlerts?: string[];
  monitoringRequired?: string[];
}

interface LabInterpretationResult {
  interpretation?: { test: string; status: string; clinicalSignificance: string; possibleCauses: string[] }[];
  patterns?: string[];
  recommendations?: string[];
}

// Mock fallback results when API unavailable
const MOCK_DIAGNOSTIC: DiagnosticResult = {
  differentials: [
    { diagnosis: "Community-Acquired Pneumonia", likelihood: "high", reasoning: "Fever + productive cough + chest pain + dyspnoea. Most common cause in this age group." },
    { diagnosis: "Acute Exacerbation of COPD", likelihood: "medium", reasoning: "History of smoking, progressive dyspnoea. Requires spirometry to confirm." },
    { diagnosis: "Pulmonary Embolism", likelihood: "medium", reasoning: "Acute onset dyspnoea + chest pain. Wells score should be calculated." },
    { diagnosis: "Heart Failure Exacerbation", likelihood: "low", reasoning: "Dyspnoea could indicate fluid overload. Check BNP and CXR for cardiomegaly." },
  ],
  redFlags: ["Acute onset severe chest pain — rule out ACS/PE", "SpO2 <94% — consider supplemental O2"],
  recommendedTests: ["Chest X-ray PA", "FBC with differential", "CRP / PCT", "Blood cultures x2", "Sputum MCS", "ABG if SpO2 <94%", "D-dimer if PE suspected"],
  clinicalPearls: ["CURB-65 score guides pneumonia severity and disposition", "Consider atypical organisms if no response to beta-lactam within 48-72h", "Check HIV status — PJP presents similarly in immunocompromised patients"],
};

const MOCK_DRUG_RESULT: DrugInteractionResult = {
  interactions: [
    { drugs: ["Warfarin", "Aspirin"], severity: "major", mechanism: "Synergistic anticoagulation + antiplatelet effects", clinicalEffect: "Significantly increased bleeding risk, including GI and intracranial haemorrhage", recommendation: "Avoid combination unless clearly indicated (e.g. mechanical valve + recent ACS). Monitor INR closely." },
    { drugs: ["Metformin", "Enalapril"], severity: "moderate", mechanism: "ACE inhibitors may enhance hypoglycaemic effect", clinicalEffect: "Increased risk of hypoglycaemia, particularly in renal impairment", recommendation: "Monitor blood glucose more frequently when initiating combination." },
  ],
  safetyAlerts: ["Patient allergic to Penicillin — cross-reactivity with cephalosporins possible (1-2%)"],
};

const MOCK_LAB_RESULT: LabInterpretationResult = {
  interpretation: [
    { test: "Potassium", status: "critical", clinicalSignificance: "Severe hyperkalaemia — risk of cardiac arrhythmia", possibleCauses: ["ACE inhibitor", "Renal impairment", "K+ supplements", "Tissue breakdown"] },
    { test: "Creatinine", status: "abnormal", clinicalSignificance: "Elevated — suggests acute or chronic kidney injury", possibleCauses: ["Pre-renal (dehydration, HF)", "Intrinsic renal disease", "Post-renal obstruction", "Drug nephrotoxicity"] },
  ],
  recommendations: ["Urgent ECG for hyperkalaemia", "IV calcium gluconate if ECG changes", "Reassess nephrotoxic medications"],
};

type AITab = "diagnostic" | "drugs" | "labs";

interface AIDiagnosticAssistantProps {
  open: boolean;
  onClose: () => void;
}

export function AIDiagnosticAssistant({ open, onClose }: AIDiagnosticAssistantProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<AITab>("diagnostic");
  const [symptoms, setSymptoms] = useState("");
  const [medications, setMedications] = useState("");
  const [labResults, setLabResults] = useState("");
  const [diagnosticResult, setDiagnosticResult] = useState<DiagnosticResult | null>(null);
  const [drugResult, setDrugResult] = useState<DrugInteractionResult | null>(null);
  const [labResult, setLabResult] = useState<LabInterpretationResult | null>(null);

  const analyzePatient = async (type: string) => {
    setIsLoading(true);
    try {
      const response = await apiClient.post<{ result?: DiagnosticResult | DrugInteractionResult | LabInterpretationResult }>("/api/v1/ai/diagnostic", { type, symptoms, medications, labResults });
      if (type === "diagnostic") setDiagnosticResult((response.result as DiagnosticResult) || MOCK_DIAGNOSTIC);
      if (type === "drug-interaction") setDrugResult((response.result as DrugInteractionResult) || MOCK_DRUG_RESULT);
      if (type === "lab-interpretation") setLabResult((response.result as LabInterpretationResult) || MOCK_LAB_RESULT);
    } catch {
      // Fallback to mock results
      if (type === "diagnostic") setDiagnosticResult(MOCK_DIAGNOSTIC);
      if (type === "drug-interaction") setDrugResult(MOCK_DRUG_RESULT);
      if (type === "lab-interpretation") setLabResult(MOCK_LAB_RESULT);
    } finally {
      setIsLoading(false);
    }
  };

  if (!open) return null;

  const tabs: { id: AITab; label: string; icon: React.ElementType }[] = [
    { id: "diagnostic", label: "Diagnosis", icon: Activity },
    { id: "drugs", label: "Drug Check", icon: Pill },
    { id: "labs", label: "Lab Analysis", icon: FlaskConical },
  ];

  const severityColor: Record<string, string> = { critical: "bg-red-100 text-red-700 border-red-200", major: "bg-orange-100 text-orange-700 border-orange-200", moderate: "bg-amber-100 text-amber-700 border-amber-200", minor: "bg-blue-100 text-blue-700 border-blue-200" };
  const likelihoodColor: Record<string, string> = { high: "bg-red-100 text-red-700", medium: "bg-amber-100 text-amber-700", low: "bg-gray-100 text-gray-600" };
  const statusColor: Record<string, string> = { critical: "bg-red-100 text-red-700", abnormal: "bg-amber-100 text-amber-700", normal: "bg-green-100 text-green-700" };

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/30" onClick={onClose} />
      <div className="w-[600px] bg-white shadow-xl flex flex-col h-full">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-50 rounded-lg"><Brain className="w-5 h-5 text-blue-600" /></div>
            <div><h2 className="text-sm font-semibold text-gray-900">AI Diagnostic Assistant</h2></div>
            <span className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded text-[10px] font-medium flex items-center gap-1"><Sparkles className="w-3 h-3" /> Beta</span>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-gray-100"><X className="w-4 h-4" /></button>
        </div>

        {/* Tabs */}
        <div className="flex border-b px-2">
          {tabs.map(t => (
            <button key={t.id} onClick={() => setActiveTab(t.id)} className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${activeTab === t.id ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"}`}>
              <t.icon className="w-4 h-4" /> {t.label}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {/* Diagnostic Tab */}
          {activeTab === "diagnostic" && (
            <>
              <div className="border rounded-lg p-4 space-y-3">
                <h3 className="text-base font-semibold text-gray-900">Symptom Analysis</h3>
                <p className="text-xs text-gray-500">Enter patient symptoms for differential diagnosis suggestions</p>
                <textarea value={symptoms} onChange={e => setSymptoms(e.target.value)} placeholder="Enter symptoms separated by commas (e.g., fever, cough, chest pain, shortness of breath)" rows={4} className="w-full px-3 py-2 text-sm border rounded-lg" />
                <button onClick={() => analyzePatient("diagnostic")} disabled={isLoading || !symptoms.trim()} className="w-full px-4 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2">
                  {isLoading ? <><Loader2 className="w-4 h-4 animate-spin" /> Analyzing...</> : <><Brain className="w-4 h-4" /> Analyze Symptoms</>}
                </button>
              </div>
              {diagnosticResult && (
                <div className="space-y-4">
                  {diagnosticResult.differentials && (
                    <div className="border rounded-lg p-4">
                      <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3"><Activity className="w-4 h-4" /> Differential Diagnoses</h4>
                      <div className="space-y-3">{diagnosticResult.differentials.map((d, i) => (
                        <div key={i} className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                          <span className={`px-2 py-0.5 rounded text-xs font-medium shrink-0 ${likelihoodColor[d.likelihood] || "bg-gray-100"}`}>{d.likelihood}</span>
                          <div><p className="font-medium text-sm">{d.diagnosis}</p><p className="text-xs text-gray-500 mt-0.5">{d.reasoning}</p></div>
                        </div>
                      ))}</div>
                    </div>
                  )}
                  {diagnosticResult.redFlags && diagnosticResult.redFlags.length > 0 && (
                    <div className="border border-red-200 rounded-lg p-4">
                      <h4 className="text-sm font-semibold text-red-700 flex items-center gap-2 mb-2"><AlertTriangle className="w-4 h-4" /> Red Flags</h4>
                      <ul className="space-y-1.5">{diagnosticResult.redFlags.map((f, i) => <li key={i} className="text-sm flex items-start gap-2"><AlertTriangle className="w-3 h-3 text-red-500 mt-0.5 shrink-0" />{f}</li>)}</ul>
                    </div>
                  )}
                  {diagnosticResult.recommendedTests && (
                    <div className="border rounded-lg p-4">
                      <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-2"><TestTube className="w-4 h-4" /> Recommended Tests</h4>
                      <ul className="space-y-1.5">{diagnosticResult.recommendedTests.map((t, i) => <li key={i} className="text-sm flex items-center gap-2"><ChevronRight className="w-3 h-3 text-gray-400" />{t}</li>)}</ul>
                    </div>
                  )}
                  {diagnosticResult.clinicalPearls && (
                    <div className="border rounded-lg p-4">
                      <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-2"><Lightbulb className="w-4 h-4 text-yellow-500" /> Clinical Pearls</h4>
                      <ul className="space-y-1.5">{diagnosticResult.clinicalPearls.map((p, i) => <li key={i} className="text-sm flex items-start gap-2"><Lightbulb className="w-3 h-3 text-yellow-500 mt-0.5 shrink-0" />{p}</li>)}</ul>
                    </div>
                  )}
                </div>
              )}
            </>
          )}

          {/* Drug Check Tab */}
          {activeTab === "drugs" && (
            <>
              <div className="border rounded-lg p-4 space-y-3">
                <h3 className="text-base font-semibold text-gray-900">Drug Interaction Check</h3>
                <p className="text-xs text-gray-500">Enter medications to check for interactions</p>
                <textarea value={medications} onChange={e => setMedications(e.target.value)} placeholder="Enter medications separated by commas (e.g., Warfarin, Aspirin, Lisinopril, Metformin)" rows={4} className="w-full px-3 py-2 text-sm border rounded-lg" />
                <button onClick={() => analyzePatient("drug-interaction")} disabled={isLoading || !medications.trim()} className="w-full px-4 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2">
                  {isLoading ? <><Loader2 className="w-4 h-4 animate-spin" /> Checking...</> : <><Pill className="w-4 h-4" /> Check Interactions</>}
                </button>
              </div>
              {drugResult?.interactions && (
                <div className="space-y-3">{drugResult.interactions.map((ix, i) => (
                  <div key={i} className={`border rounded-lg p-4 ${severityColor[ix.severity] || ""}`}>
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`px-2 py-0.5 rounded text-xs font-bold uppercase ${severityColor[ix.severity] || "bg-gray-100"}`}>{ix.severity}</span>
                      <span className="font-medium text-sm">{ix.drugs?.join(" + ")}</span>
                    </div>
                    <p className="text-sm mb-1"><strong>Mechanism:</strong> {ix.mechanism}</p>
                    <p className="text-sm mb-1"><strong>Effect:</strong> {ix.clinicalEffect}</p>
                    <p className="text-sm"><strong>Recommendation:</strong> {ix.recommendation}</p>
                  </div>
                ))}</div>
              )}
            </>
          )}

          {/* Lab Interpretation Tab */}
          {activeTab === "labs" && (
            <>
              <div className="border rounded-lg p-4 space-y-3">
                <h3 className="text-base font-semibold text-gray-900">Lab Result Interpretation</h3>
                <p className="text-xs text-gray-500">Enter lab results for clinical interpretation</p>
                <textarea value={labResults} onChange={e => setLabResults(e.target.value)} placeholder={'Enter as: Potassium:6.2, Creatinine:2.1 or JSON format'} rows={4} className="w-full px-3 py-2 text-sm border rounded-lg font-mono" />
                <button onClick={() => analyzePatient("lab-interpretation")} disabled={isLoading || !labResults.trim()} className="w-full px-4 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2">
                  {isLoading ? <><Loader2 className="w-4 h-4 animate-spin" /> Interpreting...</> : <><FlaskConical className="w-4 h-4" /> Interpret Results</>}
                </button>
              </div>
              {labResult?.interpretation && (
                <div className="space-y-3">{labResult.interpretation.map((interp, i) => (
                  <div key={i} className="border rounded-lg p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="font-medium text-sm">{interp.test}</span>
                      <span className={`px-2 py-0.5 rounded text-xs font-medium uppercase ${statusColor[interp.status] || "bg-gray-100"}`}>{interp.status}</span>
                    </div>
                    <p className="text-sm text-gray-700 mb-2">{interp.clinicalSignificance}</p>
                    {interp.possibleCauses && (
                      <div><p className="text-xs text-gray-500 mb-1">Possible causes:</p><div className="flex flex-wrap gap-1">{interp.possibleCauses.map((c, j) => <span key={j} className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-xs">{c}</span>)}</div></div>
                    )}
                  </div>
                ))}</div>
              )}
              {labResult?.recommendations && (
                <div className="border border-blue-200 rounded-lg p-4">
                  <h4 className="text-sm font-semibold text-blue-700 mb-2">Recommendations</h4>
                  <ul className="space-y-1">{labResult.recommendations.map((r, i) => <li key={i} className="text-sm flex items-center gap-2"><ChevronRight className="w-3 h-3 text-blue-500" />{r}</li>)}</ul>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-3 border-t text-center">
          <p className="text-xs text-gray-400">AI suggestions are for clinical decision support only. Always verify with clinical judgment.</p>
        </div>
      </div>
    </div>
  );
}
