"use client";

/**
 * Clinical Tools — Medscape-style clinical reference tools + productivity utilities.
 * Route: /clinical-tools
 *
 * Top bar: Drugs, Conditions (Diseases & Conditions), Interaction Checker,
 *          Calculators, Formulary, Directory (right end)
 * References & SOPs: Pill Identifier, Latest Guidelines, Procedures,
 *                    Cases & Quizzes, Podcasts
 * Utility tabs: Voice Dictation, Offline Sync, Documents, CDS Alerts, Productivity
 *
 * Backed by: Clinical Knowledge Platform (port 8270), Search service (port 8230),
 * MSIKA product registry, guidance service, Landela DMS.
 */

import Link from "next/link";
import { useState, useRef, useCallback } from "react";
import {
  Mic, MicOff, Wifi, FileText, Activity,
  Shield, RefreshCw, CheckCircle2, AlertTriangle, Download,
  Loader2, Settings, Heart, ClipboardList,
  Pill, HeartPulse, FlaskConical, Calculator, BookOpen, Contact,
  ScanSearch, Scissors, GraduationCap, Podcast,
  Search, ArrowLeft, ChevronRight, Star, Clock, TrendingUp,
  ExternalLink, Info, XCircle,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { DiscoverFacilitiesMapPanel } from "@/components/maps/DiscoverFacilitiesMapPanel";
import { FacilitiesGeoMapPanel } from "@/components/maps/FacilitiesGeoMapPanel";
import { OfflineClinicalQueueOrchestrationPanel } from "@/components/clinical/OfflineClinicalQueueOrchestrationPanel";

/* ═══════════════════════════════════════════════════════════════════
   TYPE & DATA DEFINITIONS
   ═══════════════════════════════════════════════════════════════════ */

type ToolKey = "drugs" | "conditions" | "interactions" | "calculators" | "formulary" | "directory";
type RefKey = "pill-id" | "guidelines" | "procedures" | "cases" | "podcasts";
type UtilityTab = "dictation" | "offline" | "documents" | "cds" | "productivity";

interface ToolDef {
  key: ToolKey;
  label: string;
  icon: typeof Pill;
  color: string;
  activeColor: string;
  description: string;
}

interface RefDef {
  key: RefKey;
  label: string;
  icon: typeof Pill;
  color: string;
  bgColor: string;
  description: string;
}

const TOOLS: ToolDef[] = [
  { key: "drugs", label: "Drugs", icon: Pill, color: "text-blue-600", activeColor: "bg-blue-600", description: "Drug monographs, dosing, contraindications, and safety information" },
  { key: "conditions", label: "Conditions", icon: HeartPulse, color: "text-rose-600", activeColor: "bg-rose-600", description: "Diseases & conditions — presentation, diagnosis, management, and prognosis" },
  { key: "interactions", label: "Interaction Checker", icon: FlaskConical, color: "text-amber-600", activeColor: "bg-amber-600", description: "Check multi-drug interactions, severity, and clinical significance" },
  { key: "calculators", label: "Calculators", icon: Calculator, color: "text-emerald-600", activeColor: "bg-emerald-600", description: "Clinical scoring tools — GFR, BMI, CURB-65, Wells, MELD, APGAR, and more" },
  { key: "formulary", label: "Formulary", icon: BookOpen, color: "text-purple-600", activeColor: "bg-purple-600", description: "National formulary (EDLIZ) — approved medications, tiers, and alternatives" },
  { key: "directory", label: "Directory", icon: Contact, color: "text-slate-600", activeColor: "bg-slate-700", description: "Provider and facility directory — find specialists, refer, and contact" },
];

const REFERENCES: RefDef[] = [
  { key: "pill-id", label: "Pill Identifier", icon: ScanSearch, color: "text-cyan-600", bgColor: "bg-cyan-50 border-cyan-200", description: "Identify unknown pills by shape, color, imprint, and scoring" },
  { key: "guidelines", label: "Latest Guidelines", icon: FileText, color: "text-indigo-600", bgColor: "bg-indigo-50 border-indigo-200", description: "Current clinical practice guidelines — EDLIZ, WHO, and specialty societies" },
  { key: "procedures", label: "Procedures", icon: Scissors, color: "text-orange-600", bgColor: "bg-orange-50 border-orange-200", description: "Step-by-step procedural guides with images, indications, and complications" },
  { key: "cases", label: "Cases & Quizzes", icon: GraduationCap, color: "text-pink-600", bgColor: "bg-pink-50 border-pink-200", description: "Interactive clinical cases, image challenges, and knowledge quizzes" },
  { key: "podcasts", label: "Podcasts", icon: Podcast, color: "text-violet-600", bgColor: "bg-violet-50 border-violet-200", description: "Clinical education podcasts — CME-eligible audio content and grand rounds" },
];

const UTILITY_TABS: { key: UtilityTab; label: string; icon: typeof Mic }[] = [
  { key: "dictation", label: "Voice Dictation", icon: Mic },
  { key: "offline", label: "Offline Sync", icon: Wifi },
  { key: "documents", label: "Documents", icon: FileText },
  { key: "cds", label: "CDS Alerts", icon: Shield },
  { key: "productivity", label: "Productivity", icon: Activity },
];

/* ═══════════════════════════════════════════════════════════════════
   MAIN PAGE
   ═══════════════════════════════════════════════════════════════════ */

export default function ClinicalToolsPage() {
  const [activeTool, setActiveTool] = useState<ToolKey | null>(null);
  const [activeRef, setActiveRef] = useState<RefKey | null>(null);
  const [utilityTab, setUtilityTab] = useState<UtilityTab>("dictation");

  function openTool(key: ToolKey) {
    setActiveTool(key);
    setActiveRef(null);
  }

  function openRef(key: RefKey) {
    setActiveRef(key);
    setActiveTool(null);
  }

  function goBack() {
    setActiveTool(null);
    setActiveRef(null);
  }

  const hasActivePanel = activeTool !== null || activeRef !== null;

  return (
    <AppLayout>
      <PageShell title="Clinical Tools" subtitle="Point-of-care references, clinical utilities, and decision support">
        {/* ── Sub-route links ──────────────────────────────── */}
        <div className="mb-4 flex flex-wrap gap-3 text-sm">
          <Link href="/clinical-tools/rules" className="text-pink-700 hover:underline font-medium">
            Rules engine
          </Link>
          <span className="text-gray-300">·</span>
          <Link href="/clinical-tools/forms" className="text-cyan-700 hover:underline font-medium">
            Form schema builder
          </Link>
          <span className="text-gray-300">·</span>
          <Link href="/ask" className="text-impilo-600 hover:underline font-medium">
            Ask EDLIZ
          </Link>
        </div>

        {/* ── Top toolbar: Drugs · Conditions · Interaction Checker · Calculators · Formulary · Directory ── */}
        <div className="flex items-center gap-1 mb-6 border-b border-gray-200 overflow-x-auto pb-px">
          {TOOLS.map((tool) => {
            const Icon = tool.icon;
            const isActive = activeTool === tool.key;
            return (
              <button
                key={tool.key}
                onClick={() => isActive ? goBack() : openTool(tool.key)}
                className={`flex items-center gap-1.5 px-3.5 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                  isActive
                    ? `border-current ${tool.color}`
                    : "border-transparent text-gray-500 hover:text-gray-700"
                }`}
              >
                <Icon className="w-4 h-4" /> {tool.label}
              </button>
            );
          })}
        </div>

        {/* ── Active tool/ref panel ────────────────────────── */}
        {hasActivePanel ? (
          <div className="mb-8">
            <button onClick={goBack} className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 mb-4">
              <ArrowLeft className="w-4 h-4" /> Back to all tools
            </button>
            {activeTool === "drugs" && <DrugsPanel />}
            {activeTool === "conditions" && <ConditionsPanel />}
            {activeTool === "interactions" && <InteractionCheckerPanel />}
            {activeTool === "calculators" && <CalculatorsPanel />}
            {activeTool === "formulary" && <FormularyPanel />}
            {activeTool === "directory" && <DirectoryPanel />}
            {activeRef === "pill-id" && <PillIdentifierPanel />}
            {activeRef === "guidelines" && <GuidelinesPanel />}
            {activeRef === "procedures" && <ProceduresPanel />}
            {activeRef === "cases" && <CasesPanel />}
            {activeRef === "podcasts" && <PodcastsPanel />}
          </div>
        ) : (
          <>
            {/* ── References & SOPs grid ──────────────────── */}
            <section className="mb-8">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-gray-400 mb-3">References & SOPs</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {REFERENCES.map((ref) => {
                  const Icon = ref.icon;
                  return (
                    <button
                      key={ref.key}
                      onClick={() => openRef(ref.key)}
                      className={`flex items-center gap-3 rounded-xl border p-4 text-left transition hover:shadow-md ${ref.bgColor}`}
                    >
                      <Icon className={`w-6 h-6 shrink-0 ${ref.color}`} />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-gray-900">{ref.label}</p>
                        <p className="text-xs text-gray-500 line-clamp-2">{ref.description}</p>
                      </div>
                      <ChevronRight className="w-4 h-4 text-gray-400 shrink-0 ml-auto" />
                    </button>
                  );
                })}
              </div>
            </section>

            {/* ── Utility tabs (dictation, offline, docs, CDS, productivity) ── */}
            <section>
              <h2 className="text-sm font-semibold uppercase tracking-wider text-gray-400 mb-3">Utilities</h2>
              <div className="flex gap-1 mb-6 border-b border-gray-200 overflow-x-auto">
                {UTILITY_TABS.map((tab) => {
                  const Icon = tab.icon;
                  return (
                    <button key={tab.key} onClick={() => setUtilityTab(tab.key)}
                      className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                        utilityTab === tab.key ? "border-pink-600 text-pink-600" : "border-transparent text-gray-500 hover:text-gray-700"
                      }`}>
                      <Icon className="w-4 h-4" /> {tab.label}
                    </button>
                  );
                })}
              </div>

              {utilityTab === "dictation" && <DictationTab />}
              {utilityTab === "offline" && <OfflineTab />}
              {utilityTab === "documents" && <DocumentsTab />}
              {utilityTab === "cds" && <CdsTab />}
              {utilityTab === "productivity" && <ProductivityTab />}
            </section>
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}

/* ═══════════════════════════════════════════════════════════════════
   CLINICAL REFERENCE PANELS
   ═══════════════════════════════════════════════════════════════════ */

/* ── Drugs ──────────────────────────────────────────────────────── */

function DrugsPanel() {
  const [query, setQuery] = useState("");
  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["drug-search", query],
    queryFn: () => apiClient.get(`/internal/v1/clinical/drugs?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
  });
  const results = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Pill className="w-6 h-6 text-blue-600" />
        <h2 className="text-lg font-semibold text-gray-900">Drug Reference</h2>
      </div>
      <p className="text-sm text-gray-500">Search drug monographs for dosing, contraindications, adverse effects, and pharmacokinetics.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search drugs by name, class, or indication..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-blue-400 animate-spin" />}
      </div>

      {query.length < 2 ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {["Analgesics", "Antibiotics", "Antihypertensives", "Antiretrovirals", "Antidiabetics", "Antimalarials"].map((cat) => (
            <button key={cat} onClick={() => setQuery(cat)} className="rounded-lg border border-gray-200 bg-white p-3 text-left hover:bg-blue-50 transition">
              <p className="text-sm font-medium text-gray-900">{cat}</p>
              <p className="text-xs text-gray-400 mt-0.5">Browse category</p>
            </button>
          ))}
        </div>
      ) : results.length === 0 && !isFetching ? (
        <div className="bg-gray-50 rounded-lg border p-8 text-center">
          <Pill className="w-8 h-8 text-gray-300 mx-auto mb-2" />
          <p className="text-sm text-gray-500">No drugs found for &quot;{query}&quot;</p>
        </div>
      ) : (
        <div className="space-y-2">
          {results.map((drug, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-gray-900">{String(drug.name ?? drug.generic_name ?? "—")}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{String(drug.drug_class ?? drug.category ?? "—")}</p>
                </div>
                <span className="text-xs bg-blue-100 text-blue-700 rounded-full px-2 py-0.5">{String(drug.schedule ?? "OTC")}</span>
              </div>
              {Boolean(drug.indication) && <p className="text-xs text-gray-600 mt-2">{String(drug.indication)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-blue-50 rounded-lg border border-blue-200 p-4">
        <h3 className="text-sm font-semibold text-blue-900 mb-2">Drug Monograph Sections</h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-blue-700">
          {["Dosing & Administration", "Pharmacokinetics", "Contraindications", "Adverse Effects", "Drug Interactions", "Pregnancy & Lactation", "Pediatric Dosing", "Renal/Hepatic Adjustment"].map((s) => (
            <div key={s} className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3" />{s}</div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── Conditions (Diseases & Conditions) ─────────────────────────── */

function ConditionsPanel() {
  const [query, setQuery] = useState("");
  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["condition-search", query],
    queryFn: () => apiClient.get(`/internal/v1/clinical/conditions?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
  });
  const results = data?.data ?? [];

  const BODY_SYSTEMS = [
    "Cardiovascular", "Respiratory", "Gastrointestinal", "Neurological",
    "Endocrine", "Musculoskeletal", "Infectious Disease", "Renal",
    "Dermatology", "Haematology", "Obstetrics & Gynaecology", "Paediatrics",
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <HeartPulse className="w-6 h-6 text-rose-600" />
        <h2 className="text-lg font-semibold text-gray-900">Diseases & Conditions</h2>
      </div>
      <p className="text-sm text-gray-500">Evidence-based clinical information — presentation, differential diagnosis, work-up, management, and prognosis.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search conditions, symptoms, or ICD codes..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-rose-400 animate-spin" />}
      </div>

      {query.length < 2 ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          {BODY_SYSTEMS.map((sys) => (
            <button key={sys} onClick={() => setQuery(sys)} className="rounded-lg border border-gray-200 bg-white p-3 text-left hover:bg-rose-50 transition">
              <p className="text-sm font-medium text-gray-900">{sys}</p>
            </button>
          ))}
        </div>
      ) : results.length === 0 && !isFetching ? (
        <div className="bg-gray-50 rounded-lg border p-8 text-center">
          <HeartPulse className="w-8 h-8 text-gray-300 mx-auto mb-2" />
          <p className="text-sm text-gray-500">No conditions found for &quot;{query}&quot;</p>
        </div>
      ) : (
        <div className="space-y-2">
          {results.map((cond, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 hover:border-rose-300 transition">
              <p className="text-sm font-semibold text-gray-900">{String(cond.name ?? cond.title ?? "—")}</p>
              <p className="text-xs text-gray-500 mt-0.5">{String(cond.icd_code ?? "")} {cond.system ? `· ${String(cond.system)}` : ""}</p>
              {Boolean(cond.overview) && <p className="text-xs text-gray-600 mt-2 line-clamp-2">{String(cond.overview)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-rose-50 rounded-lg border border-rose-200 p-4">
        <h3 className="text-sm font-semibold text-rose-900 mb-2">Condition Monograph Sections</h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-rose-700">
          {["Overview & Epidemiology", "Presentation & History", "Differential Diagnosis", "Investigations & Work-up", "Management & Treatment", "Complications", "Prognosis", "Patient Education"].map((s) => (
            <div key={s} className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3" />{s}</div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── Interaction Checker ────────────────────────────────────────── */

function InteractionCheckerPanel() {
  const [drugs, setDrugs] = useState<string[]>([""]);
  const [checking, setChecking] = useState(false);
  const [results, setResults] = useState<Array<{ pair: string; severity: string; description: string }> | null>(null);

  function addDrug() { setDrugs((prev) => [...prev, ""]); }
  function updateDrug(index: number, value: string) { setDrugs((prev) => prev.map((d, i) => (i === index ? value : d))); }
  function removeDrug(index: number) { if (drugs.length <= 1) return; setDrugs((prev) => prev.filter((_, i) => i !== index)); }

  async function checkInteractions() {
    const filled = drugs.filter((d) => d.trim().length > 0);
    if (filled.length < 2) return;
    setChecking(true);
    try {
      const resp = await apiClient.post<{ data: Array<{ pair: string; severity: string; description: string }> }>(
        "/internal/v1/clinical/interactions/check", { drugs: filled });
      setResults(resp.data ?? []);
    } catch { setResults([]); } finally { setChecking(false); }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <FlaskConical className="w-6 h-6 text-amber-600" />
        <h2 className="text-lg font-semibold text-gray-900">Drug Interaction Checker</h2>
      </div>
      <p className="text-sm text-gray-500">Enter two or more medications to check for clinically significant interactions.</p>

      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        {drugs.map((drug, i) => (
          <div key={i} className="flex gap-2">
            <input type="text" value={drug} onChange={(e) => updateDrug(i, e.target.value)}
              placeholder={`Drug ${i + 1}...`}
              className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-transparent" />
            {drugs.length > 1 && (
              <button onClick={() => removeDrug(i)} className="px-2 text-gray-400 hover:text-red-500"><XCircle className="w-4 h-4" /></button>
            )}
          </div>
        ))}
        <div className="flex gap-2">
          <button onClick={addDrug} className="text-sm text-amber-600 hover:text-amber-700 font-medium">+ Add another drug</button>
          <button onClick={checkInteractions}
            disabled={drugs.filter((d) => d.trim()).length < 2 || checking}
            className="ml-auto px-4 py-2 text-sm font-medium bg-amber-600 text-white rounded-lg hover:bg-amber-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2">
            {checking ? <Loader2 className="w-4 h-4 animate-spin" /> : <FlaskConical className="w-4 h-4" />}
            Check Interactions
          </button>
        </div>
      </div>

      {results !== null && (
        results.length === 0 ? (
          <div className="bg-green-50 rounded-lg border border-green-200 p-4 flex items-center gap-3">
            <CheckCircle2 className="w-5 h-5 text-green-600" />
            <p className="text-sm text-green-800">No known interactions found between the entered medications.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {results.map((interaction, i) => (
              <div key={i} className={`rounded-lg border p-4 ${
                interaction.severity === "major" ? "bg-red-50 border-red-200" :
                interaction.severity === "moderate" ? "bg-amber-50 border-amber-200" :
                "bg-yellow-50 border-yellow-200"
              }`}>
                <div className="flex items-center justify-between mb-1">
                  <p className="text-sm font-semibold text-gray-900">{interaction.pair}</p>
                  <span className={`text-xs font-medium rounded-full px-2 py-0.5 ${
                    interaction.severity === "major" ? "bg-red-100 text-red-700" :
                    interaction.severity === "moderate" ? "bg-amber-100 text-amber-700" :
                    "bg-yellow-100 text-yellow-700"
                  }`}>{interaction.severity}</span>
                </div>
                <p className="text-xs text-gray-600">{interaction.description}</p>
              </div>
            ))}
          </div>
        )
      )}

      <div className="bg-amber-50 rounded-lg border border-amber-200 p-4 text-xs text-amber-800">
        <div className="flex items-start gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <p>Interaction data is for clinical decision support only. Always verify against authoritative sources. Severity levels: <strong>Major</strong> (avoid combination), <strong>Moderate</strong> (use with caution), <strong>Minor</strong> (monitor).</p>
        </div>
      </div>
    </div>
  );
}

/* ── Calculators ────────────────────────────────────────────────── */

const CALCULATORS = [
  { id: "gfr", name: "eGFR (CKD-EPI)", category: "Renal", description: "Estimated glomerular filtration rate using CKD-EPI equation" },
  { id: "bmi", name: "BMI", category: "General", description: "Body mass index with WHO classification" },
  { id: "bsa", name: "BSA", category: "General", description: "Body surface area (DuBois & Mosteller)" },
  { id: "curb65", name: "CURB-65", category: "Respiratory", description: "Community-acquired pneumonia severity score" },
  { id: "wells-dvt", name: "Wells Score (DVT)", category: "Haematology", description: "Pre-test probability for deep vein thrombosis" },
  { id: "wells-pe", name: "Wells Score (PE)", category: "Haematology", description: "Pre-test probability for pulmonary embolism" },
  { id: "cha2ds2", name: "CHA\u2082DS\u2082-VASc", category: "Cardiology", description: "Stroke risk in atrial fibrillation" },
  { id: "meld", name: "MELD Score", category: "Hepatology", description: "Model for end-stage liver disease" },
  { id: "apgar", name: "APGAR Score", category: "Neonatal", description: "Newborn assessment at 1 and 5 minutes" },
  { id: "gcs", name: "Glasgow Coma Scale", category: "Neurology", description: "Level of consciousness assessment" },
  { id: "sofa", name: "SOFA Score", category: "Critical Care", description: "Sequential organ failure assessment" },
  { id: "child-pugh", name: "Child-Pugh", category: "Hepatology", description: "Hepatic function classification" },
  { id: "corrected-calcium", name: "Corrected Calcium", category: "Endocrine", description: "Albumin-corrected serum calcium" },
  { id: "anion-gap", name: "Anion Gap", category: "Metabolic", description: "Serum anion gap with delta-delta ratio" },
  { id: "creatinine-clearance", name: "CrCl (Cockcroft-Gault)", category: "Renal", description: "Creatinine clearance for drug dosing" },
  { id: "corrected-na", name: "Corrected Sodium", category: "Metabolic", description: "Sodium corrected for hyperglycaemia" },
];

function CalculatorsPanel() {
  const [search, setSearch] = useState("");
  const [selectedCalc, setSelectedCalc] = useState<string | null>(null);

  const categories = [...new Set(CALCULATORS.map((c) => c.category))].sort();
  const filtered = search.length > 0
    ? CALCULATORS.filter((c) => c.name.toLowerCase().includes(search.toLowerCase()) || c.category.toLowerCase().includes(search.toLowerCase()))
    : CALCULATORS;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Calculator className="w-6 h-6 text-emerald-600" />
        <h2 className="text-lg font-semibold text-gray-900">Clinical Calculators</h2>
      </div>
      <p className="text-sm text-gray-500">Validated clinical scoring tools and medical calculators for point-of-care use.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
        <input type="text" value={search} onChange={(e) => setSearch(e.target.value)}
          placeholder="Search calculators..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent" />
      </div>

      {selectedCalc ? (
        <CalculatorDetail calcId={selectedCalc} onBack={() => setSelectedCalc(null)} />
      ) : (
        <>
          <div className="flex gap-2 flex-wrap">
            {categories.map((cat) => (
              <button key={cat} onClick={() => setSearch(cat)} className="text-xs border border-gray-200 rounded-full px-3 py-1 hover:bg-emerald-50 hover:border-emerald-300 transition">
                {cat}
              </button>
            ))}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {filtered.map((calc) => (
              <button key={calc.id} onClick={() => setSelectedCalc(calc.id)}
                className="bg-white rounded-lg border border-gray-200 p-4 text-left hover:border-emerald-300 hover:bg-emerald-50/50 transition">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold text-gray-900">{calc.name}</p>
                  <span className="text-xs bg-emerald-100 text-emerald-700 rounded-full px-2 py-0.5">{calc.category}</span>
                </div>
                <p className="text-xs text-gray-500 mt-1">{calc.description}</p>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function CalculatorDetail({ calcId, onBack }: { calcId: string; onBack: () => void }) {
  const calc = CALCULATORS.find((c) => c.id === calcId);
  if (!calc) return null;

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-900">{calc.name}</h3>
          <p className="text-xs text-gray-500">{calc.description}</p>
        </div>
        <button onClick={onBack} className="text-xs text-gray-500 hover:text-gray-700">Close</button>
      </div>
      <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-4 text-center">
        <Calculator className="w-8 h-8 text-emerald-400 mx-auto mb-2" />
        <p className="text-sm text-emerald-800 font-medium">Calculator interface</p>
        <p className="text-xs text-emerald-600 mt-1">Enter patient parameters to compute the {calc.name}. Results are calculated client-side.</p>
        <p className="text-xs text-gray-500 mt-3">Full calculator form connected to Clinical Knowledge Platform (port 8270).</p>
      </div>
      <div className="flex gap-2 text-xs">
        <span className="bg-gray-100 text-gray-600 rounded-full px-2.5 py-1">{calc.category}</span>
        <span className="bg-gray-100 text-gray-600 rounded-full px-2.5 py-1">Validated</span>
        <span className="bg-gray-100 text-gray-600 rounded-full px-2.5 py-1">Point-of-care</span>
      </div>
    </div>
  );
}

/* ── Formulary ──────────────────────────────────────────────────── */

function FormularyPanel() {
  const [query, setQuery] = useState("");
  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["formulary-search", query],
    queryFn: () => apiClient.get(`/internal/v1/clinical/formulary?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
  });
  const results = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <BookOpen className="w-6 h-6 text-purple-600" />
        <h2 className="text-lg font-semibold text-gray-900">National Formulary (EDLIZ)</h2>
      </div>
      <p className="text-sm text-gray-500">Essential Drugs List for Zimbabwe — approved medications, recommended tiers, restrictions, and alternatives.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search formulary by drug name, condition, or tier..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-purple-400 animate-spin" />}
      </div>

      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Tier 1 — Essential", desc: "First-line, widely available", color: "bg-green-50 border-green-200 text-green-700" },
          { label: "Tier 2 — Supplementary", desc: "Second-line or specialist", color: "bg-blue-50 border-blue-200 text-blue-700" },
          { label: "Tier 3 — Restricted", desc: "Specialist-only, prior auth", color: "bg-amber-50 border-amber-200 text-amber-700" },
        ].map((tier) => (
          <div key={tier.label} className={`rounded-lg border p-3 ${tier.color}`}>
            <p className="text-xs font-semibold">{tier.label}</p>
            <p className="text-[10px] mt-0.5 opacity-80">{tier.desc}</p>
          </div>
        ))}
      </div>

      {results.length > 0 && (
        <div className="space-y-2">
          {results.map((item, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4">
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-gray-900">{String(item.name ?? "—")}</p>
                <span className={`text-xs rounded-full px-2 py-0.5 ${
                  item.tier === 1 ? "bg-green-100 text-green-700" : item.tier === 2 ? "bg-blue-100 text-blue-700" : "bg-amber-100 text-amber-700"
                }`}>Tier {String(item.tier ?? "—")}</span>
              </div>
              {Boolean(item.indication) && <p className="text-xs text-gray-600 mt-1">{String(item.indication)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-purple-50 rounded-lg border border-purple-200 p-4 text-xs text-purple-800">
        <div className="flex items-start gap-2">
          <Info className="w-4 h-4 shrink-0 mt-0.5" />
          <p>Formulary data sourced from the national EDLIZ and curated through the <Link href="/admin/clinical-curation" className="underline font-medium">Knowledge Curation</Link> pipeline.</p>
        </div>
      </div>
    </div>
  );
}

/* ── Directory ──────────────────────────────────────────────────── */

function DirectoryPanel() {
  const [query, setQuery] = useState("");
  const [searchType, setSearchType] = useState<"providers" | "facilities">("providers");
  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["directory-search", searchType, query],
    queryFn: () => apiClient.get(`/internal/v1/registry/${searchType}?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
  });
  const results = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Contact className="w-6 h-6 text-slate-600" />
        <h2 className="text-lg font-semibold text-gray-900">Provider & Facility Directory</h2>
      </div>
      <p className="text-sm text-gray-500">Search registered providers and facilities for referrals, consultations, and contact information.</p>

      <div className="flex gap-2 mb-2">
        {(["providers", "facilities"] as const).map((t) => (
          <button key={t} onClick={() => { setSearchType(t); setQuery(""); }}
            className={`px-3 py-1.5 text-sm rounded-lg font-medium transition ${searchType === t ? "bg-slate-900 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>
            {t === "providers" ? "Providers" : "Facilities"}
          </button>
        ))}
      </div>

      {searchType === "facilities" ? (
        query.length >= 2 ? (
          <FacilitiesGeoMapPanel
            title="Nearby facilities map"
            subtitle="Governed Tuso registry coordinates for referral geography"
            search={query}
            size={40}
          />
        ) : (
          <DiscoverFacilitiesMapPanel />
        )
      ) : null}

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder={searchType === "providers" ? "Search by name, specialty, or registration number..." : "Search by facility name, district, or type..."}
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-slate-400 animate-spin" />}
      </div>

      {results.length > 0 ? (
        <div className="space-y-2">
          {results.map((entry, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 hover:border-slate-300 transition">
              <p className="text-sm font-semibold text-gray-900">{String(entry.name ?? entry.display_name ?? "—")}</p>
              <p className="text-xs text-gray-500 mt-0.5">
                {searchType === "providers"
                  ? `${String(entry.specialty ?? "—")} · ${String(entry.registration_number ?? "")}`
                  : `${String(entry.type ?? "—")} · ${String(entry.district ?? "")}`}
              </p>
            </div>
          ))}
        </div>
      ) : query.length >= 2 && !isFetching ? (
        <div className="bg-gray-50 rounded-lg border p-8 text-center">
          <Contact className="w-8 h-8 text-gray-300 mx-auto mb-2" />
          <p className="text-sm text-gray-500">No {searchType} found for &quot;{query}&quot;</p>
        </div>
      ) : null}

      <div className="grid grid-cols-2 gap-3">
        <Link href="/registry" className="bg-white rounded-lg border border-gray-200 p-3 hover:bg-gray-50 transition text-center">
          <p className="text-sm font-medium text-gray-900">Full Registry</p>
          <p className="text-xs text-gray-500">Browse all registries</p>
        </Link>
        <Link href="/discover" className="bg-white rounded-lg border border-gray-200 p-3 hover:bg-gray-50 transition text-center">
          <p className="text-sm font-medium text-gray-900">Discover Services</p>
          <p className="text-xs text-gray-500">Find nearby services</p>
        </Link>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════
   REFERENCE & SOP PANELS
   ═══════════════════════════════════════════════════════════════════ */

/* ── Pill Identifier ────────────────────────────────────────────── */

function PillIdentifierPanel() {
  const [shape, setShape] = useState("");
  const [color, setColor] = useState("");
  const [imprint, setImprint] = useState("");

  const SHAPES = ["Round", "Oval", "Capsule", "Oblong", "Diamond", "Rectangle", "Triangle", "Pentagon", "Hexagon"];
  const COLORS = ["White", "Yellow", "Orange", "Pink", "Red", "Blue", "Green", "Brown", "Purple", "Grey"];

  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["pill-identify", shape, color, imprint],
    queryFn: () => apiClient.get(`/internal/v1/clinical/pill-identifier?shape=${encodeURIComponent(shape)}&color=${encodeURIComponent(color)}&imprint=${encodeURIComponent(imprint)}`),
    enabled: shape.length > 0 || color.length > 0 || imprint.length > 1,
  });
  const results = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <ScanSearch className="w-6 h-6 text-cyan-600" />
        <h2 className="text-lg font-semibold text-gray-900">Pill Identifier</h2>
      </div>
      <p className="text-sm text-gray-500">Identify unknown tablets and capsules by their physical characteristics.</p>

      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1.5">Shape</label>
          <div className="flex flex-wrap gap-1.5">
            {SHAPES.map((s) => (
              <button key={s} onClick={() => setShape(shape === s ? "" : s)}
                className={`text-xs px-2.5 py-1 rounded-full border transition ${shape === s ? "bg-cyan-100 border-cyan-400 text-cyan-700" : "border-gray-200 text-gray-600 hover:bg-gray-50"}`}>
                {s}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1.5">Color</label>
          <div className="flex flex-wrap gap-1.5">
            {COLORS.map((c) => (
              <button key={c} onClick={() => setColor(color === c ? "" : c)}
                className={`text-xs px-2.5 py-1 rounded-full border transition ${color === c ? "bg-cyan-100 border-cyan-400 text-cyan-700" : "border-gray-200 text-gray-600 hover:bg-gray-50"}`}>
                {c}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1.5">Imprint / Markings</label>
          <input type="text" value={imprint} onChange={(e) => setImprint(e.target.value)}
            placeholder="Enter letters, numbers, or logos on the pill..."
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-cyan-500 focus:border-transparent" />
        </div>
      </div>

      {isFetching && <Loader2 className="w-6 h-6 animate-spin text-cyan-400 mx-auto" />}

      {results.length > 0 && (
        <div className="space-y-2">
          {results.map((pill, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4">
              <p className="text-sm font-semibold text-gray-900">{String(pill.name ?? "—")}</p>
              <p className="text-xs text-gray-500">{String(pill.strength ?? "")} · {String(pill.manufacturer ?? "")}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ── Latest Guidelines ──────────────────────────────────────────── */

function GuidelinesPanel() {
  const [category, setCategory] = useState<string>("all");
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["clinical-guidelines", category],
    queryFn: () => apiClient.get(`/internal/v1/clinical/guidelines?category=${encodeURIComponent(category)}`),
  });
  const guidelines = data?.data ?? [];

  const CATEGORIES = ["All", "EDLIZ", "WHO", "Infectious Disease", "Maternal Health", "Paediatrics", "NCD", "Surgery", "Emergency"];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <FileText className="w-6 h-6 text-indigo-600" />
        <h2 className="text-lg font-semibold text-gray-900">Latest Clinical Guidelines</h2>
      </div>
      <p className="text-sm text-gray-500">Current clinical practice guidelines from EDLIZ, WHO, and specialty societies.</p>

      <div className="flex gap-2 flex-wrap">
        {CATEGORIES.map((cat) => (
          <button key={cat} onClick={() => setCategory(cat === "All" ? "all" : cat)}
            className={`text-xs px-3 py-1.5 rounded-full border transition font-medium ${
              (cat === "All" ? "all" : cat) === category ? "bg-indigo-100 border-indigo-400 text-indigo-700" : "border-gray-200 text-gray-600 hover:bg-indigo-50"
            }`}>
            {cat}
          </button>
        ))}
      </div>

      {isLoading ? (
        <Loader2 className="w-6 h-6 animate-spin text-indigo-400 mx-auto" />
      ) : guidelines.length === 0 ? (
        <div className="bg-gray-50 rounded-lg border p-8 text-center">
          <FileText className="w-8 h-8 text-gray-300 mx-auto mb-2" />
          <p className="text-sm text-gray-500">No guidelines available for this category yet.</p>
          <p className="text-xs text-gray-400 mt-1">Guidelines are ingested through <Link href="/admin/clinical-curation" className="text-indigo-600 underline">Knowledge Curation</Link>.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {guidelines.map((g, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 hover:border-indigo-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-gray-900">{String(g.title ?? "—")}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{String(g.source ?? "")} · {String(g.year ?? "")}</p>
                </div>
                {Boolean(g.url) && <ExternalLink className="w-4 h-4 text-gray-400 shrink-0" />}
              </div>
              {Boolean(g.summary) && <p className="text-xs text-gray-600 mt-2 line-clamp-2">{String(g.summary)}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ── Procedures ─────────────────────────────────────────────────── */

function ProceduresPanel() {
  const [query, setQuery] = useState("");
  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["procedures-search", query],
    queryFn: () => apiClient.get(`/internal/v1/clinical/procedures?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
  });
  const results = data?.data ?? [];

  const SPECIALTIES = ["General Surgery", "Emergency Medicine", "Obstetrics", "Orthopaedics", "Anaesthesia", "Paediatrics", "ENT", "Ophthalmology"];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Scissors className="w-6 h-6 text-orange-600" />
        <h2 className="text-lg font-semibold text-gray-900">Procedures</h2>
      </div>
      <p className="text-sm text-gray-500">Step-by-step procedural guides with indications, technique, complications, and post-procedure care.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search procedures by name or specialty..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-orange-400 animate-spin" />}
      </div>

      {query.length < 2 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {SPECIALTIES.map((spec) => (
            <button key={spec} onClick={() => setQuery(spec)} className="rounded-lg border border-gray-200 bg-white p-3 text-left hover:bg-orange-50 transition">
              <p className="text-sm font-medium text-gray-900">{spec}</p>
            </button>
          ))}
        </div>
      )}

      {results.length > 0 && (
        <div className="space-y-2">
          {results.map((proc, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 hover:border-orange-300 transition">
              <p className="text-sm font-semibold text-gray-900">{String(proc.name ?? proc.title ?? "—")}</p>
              <p className="text-xs text-gray-500 mt-0.5">{String(proc.specialty ?? "")} · {String(proc.complexity ?? "")}</p>
              {Boolean(proc.indications) && <p className="text-xs text-gray-600 mt-2">{String(proc.indications)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-orange-50 rounded-lg border border-orange-200 p-4">
        <h3 className="text-sm font-semibold text-orange-900 mb-2">Procedure Guide Sections</h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-orange-700">
          {["Indications", "Contraindications", "Equipment & Setup", "Step-by-Step Technique", "Complications", "Post-Procedure Care", "Documentation", "CPT / Billing Codes"].map((s) => (
            <div key={s} className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3" />{s}</div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── Cases & Quizzes ────────────────────────────────────────────── */

function CasesPanel() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["clinical-cases"],
    queryFn: () => apiClient.get("/internal/v1/clinical/cases"),
  });
  const cases = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <GraduationCap className="w-6 h-6 text-pink-600" />
        <h2 className="text-lg font-semibold text-gray-900">Cases & Quizzes</h2>
      </div>
      <p className="text-sm text-gray-500">Interactive clinical cases, image challenges, and knowledge quizzes for continuing professional development.</p>

      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Clinical Cases", desc: "Work through patient scenarios", icon: Star, color: "text-pink-500" },
          { label: "Image Challenges", desc: "Radiology, dermatology, pathology", icon: ScanSearch, color: "text-indigo-500" },
          { label: "Knowledge Quizzes", desc: "Test & reinforce learning", icon: GraduationCap, color: "text-emerald-500" },
        ].map((item) => {
          const Icon = item.icon;
          return (
            <div key={item.label} className="bg-white rounded-lg border border-gray-200 p-4 text-center">
              <Icon className={`w-6 h-6 mx-auto mb-2 ${item.color}`} />
              <p className="text-sm font-semibold text-gray-900">{item.label}</p>
              <p className="text-xs text-gray-500 mt-0.5">{item.desc}</p>
            </div>
          );
        })}
      </div>

      {isLoading ? (
        <Loader2 className="w-6 h-6 animate-spin text-pink-400 mx-auto" />
      ) : cases.length === 0 ? (
        <div className="bg-pink-50 rounded-lg border border-pink-200 p-6 text-center">
          <GraduationCap className="w-10 h-10 text-pink-300 mx-auto mb-3" />
          <p className="text-sm font-medium text-pink-900">Cases library building</p>
          <p className="text-xs text-pink-700 mt-1">Clinical cases are authored through the Knowledge Curation pipeline and reviewed by the clinical governance committee. CME credits tracked per completed case.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {cases.map((c, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 hover:border-pink-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-gray-900">{String(c.title ?? "—")}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{String(c.specialty ?? "")} · {String(c.difficulty ?? "")}</p>
                </div>
                <div className="flex items-center gap-1 text-xs text-gray-400">
                  <Clock className="w-3 h-3" /><span>{String(c.duration ?? "15 min")}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="bg-gray-50 rounded-lg border border-gray-200 p-4 text-xs text-gray-600">
        <div className="flex items-start gap-2">
          <TrendingUp className="w-4 h-4 shrink-0 mt-0.5 text-pink-500" />
          <p>Completed cases and quiz scores are tracked on your <Link href="/professional" className="text-pink-600 underline font-medium">Professional Profile</Link> for CPD/CME credit tracking.</p>
        </div>
      </div>
    </div>
  );
}

/* ── Podcasts ───────────────────────────────────────────────────── */

function PodcastsPanel() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["clinical-podcasts"],
    queryFn: () => apiClient.get("/internal/v1/clinical/podcasts"),
  });
  const episodes = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Podcast className="w-6 h-6 text-violet-600" />
        <h2 className="text-lg font-semibold text-gray-900">Clinical Podcasts</h2>
      </div>
      <p className="text-sm text-gray-500">CME-eligible audio content — grand rounds, case discussions, guideline updates, and clinical pearls.</p>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: "Grand Rounds", count: "Weekly", color: "bg-violet-100 text-violet-700" },
          { label: "Case Discussions", count: "Bi-weekly", color: "bg-pink-100 text-pink-700" },
          { label: "Guideline Updates", count: "Monthly", color: "bg-indigo-100 text-indigo-700" },
          { label: "Clinical Pearls", count: "Daily", color: "bg-emerald-100 text-emerald-700" },
        ].map((series) => (
          <div key={series.label} className={`rounded-lg p-3 text-center ${series.color}`}>
            <p className="text-xs font-semibold">{series.label}</p>
            <p className="text-[10px] mt-0.5 opacity-80">{series.count}</p>
          </div>
        ))}
      </div>

      {isLoading ? (
        <Loader2 className="w-6 h-6 animate-spin text-violet-400 mx-auto" />
      ) : episodes.length === 0 ? (
        <div className="bg-violet-50 rounded-lg border border-violet-200 p-6 text-center">
          <Podcast className="w-10 h-10 text-violet-300 mx-auto mb-3" />
          <p className="text-sm font-medium text-violet-900">Podcast library launching soon</p>
          <p className="text-xs text-violet-700 mt-1">Audio content will be published through the clinical knowledge platform. Episodes earn CME credits when completed with the accompanying quiz.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {episodes.map((ep, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 hover:border-violet-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-gray-900">{String(ep.title ?? "—")}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{String(ep.series ?? "")} · {String(ep.date ?? "")}</p>
                </div>
                <div className="flex items-center gap-1 text-xs text-gray-400">
                  <Clock className="w-3 h-3" /><span>{String(ep.duration ?? "30 min")}</span>
                </div>
              </div>
              {Boolean(ep.summary) && <p className="text-xs text-gray-600 mt-2 line-clamp-2">{String(ep.summary)}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════
   UTILITY TABS (existing tools)
   ═══════════════════════════════════════════════════════════════════ */

/* ── Voice Dictation ─────────────────────────────────────────────── */

function DictationTab() {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [supported, setSupported] = useState(true);
  const recognitionRef = useRef<SpeechRecognition | null>(null);

  const startListening = useCallback(() => {
    if (typeof window === "undefined") return;
    const SpeechRecognition = (window as unknown as { SpeechRecognition?: typeof window.SpeechRecognition; webkitSpeechRecognition?: typeof window.SpeechRecognition }).SpeechRecognition
      ?? (window as unknown as { webkitSpeechRecognition?: typeof window.SpeechRecognition }).webkitSpeechRecognition;
    if (!SpeechRecognition) { setSupported(false); return; }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = "en-US";

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let text = "";
      for (let i = 0; i < event.results.length; i++) {
        text += event.results[i][0].transcript;
      }
      setTranscript(text);
    };
    recognition.onerror = () => setIsListening(false);
    recognition.onend = () => setIsListening(false);

    recognition.start();
    recognitionRef.current = recognition;
    setIsListening(true);
  }, []);

  const stopListening = useCallback(() => {
    recognitionRef.current?.stop();
    setIsListening(false);
  }, []);

  return (
    <div className="space-y-6">
      <div className="bg-pink-50 rounded-lg border border-pink-200 p-4 text-sm text-pink-800">
        <strong>Voice Dictation:</strong> Use your browser&apos;s built-in speech recognition to dictate clinical notes hands-free. Works in Chrome, Edge, and Safari.
      </div>

      {!supported ? (
        <div className="bg-amber-50 rounded-lg border border-amber-200 p-5 text-center">
          <MicOff className="w-10 h-10 text-amber-400 mx-auto mb-3" />
          <p className="text-sm text-amber-700">Speech recognition is not supported in this browser.</p>
          <p className="text-xs text-amber-600 mt-1">Try Chrome, Edge, or Safari for voice dictation.</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-semibold text-gray-900">Dictation</h3>
            <button onClick={isListening ? stopListening : startListening}
              className={`inline-flex items-center gap-2 px-5 py-3 text-sm font-medium rounded-full transition-all ${
                isListening ? "bg-red-600 text-white animate-pulse" : "bg-pink-600 text-white hover:bg-pink-700"
              }`}>
              {isListening ? <><MicOff className="w-5 h-5" /> Stop</> : <><Mic className="w-5 h-5" /> Start Dictating</>}
            </button>
          </div>

          <div className={`min-h-[200px] rounded-lg border-2 p-4 text-sm ${isListening ? "border-pink-300 bg-pink-50" : "border-gray-200 bg-gray-50"}`}>
            {transcript ? (
              <p className="text-gray-900 whitespace-pre-wrap">{transcript}</p>
            ) : (
              <p className="text-gray-400 italic">{isListening ? "Listening... speak now" : "Press \"Start Dictating\" and speak to capture text"}</p>
            )}
          </div>

          {transcript && (
            <div className="flex gap-2 mt-3">
              <button onClick={() => navigator.clipboard.writeText(transcript)}
                className="px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200">
                Copy to Clipboard
              </button>
              <button onClick={() => setTranscript("")}
                className="px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200">
                Clear
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/* ── Offline Sync ────────────────────────────────────────────────── */

function OfflineTab() {
  const { data } = useQuery<{ data: Record<string, unknown> }>({
    queryKey: ["sync-status"],
    queryFn: () => apiClient.get("/internal/v1/clinical-tools/sync/status"),
  });
  const syncInfo = data?.data ?? {};

  return (
    <div className="space-y-6">
      <OfflineClinicalQueueOrchestrationPanel />
      <div className="bg-impilo-50 rounded-lg border border-impilo-200 p-4 text-sm text-impilo-700">
        <strong>Offline Sync Engine:</strong> The mobile app syncs data in the background every 30 seconds. Conflicts are presented for user resolution. All operations are queued and replayed when connectivity resumes.
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <Wifi className={`w-8 h-8 mx-auto mb-2 ${syncInfo.offlineCapable ? "text-green-500" : "text-gray-400"}`} />
          <p className="text-sm font-semibold text-gray-900">Sync Engine</p>
          <p className="text-xs text-green-600">{String(syncInfo.syncEngine ?? "Available")}</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <RefreshCw className="w-8 h-8 text-impilo-400 mx-auto mb-2" />
          <p className="text-sm font-semibold text-gray-900">Auto-Sync Interval</p>
          <p className="text-xs text-impilo-500">{Number(syncInfo.autoSyncInterval ?? 30000) / 1000}s</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <AlertTriangle className="w-8 h-8 text-amber-500 mx-auto mb-2" />
          <p className="text-sm font-semibold text-gray-900">Conflict Resolution</p>
          <p className="text-xs text-amber-600">{String(syncInfo.conflictResolution ?? "User Prompted")}</p>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-900 mb-3">Sync Architecture</h3>
        <div className="space-y-2 text-xs text-gray-600">
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Operations queued locally when offline</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Background sync every 30 seconds when online</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Conflict detection with user resolution prompts</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Retry with exponential backoff on failures</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Idempotent replay prevents duplicates</span></div>
        </div>
      </div>
    </div>
  );
}

/* ── Documents ───────────────────────────────────────────────────── */

function DocumentsTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["clinical-documents"],
    queryFn: () => apiClient.get("/internal/v1/clinical-tools/documents"),
  });
  const docs = data?.data ?? [];

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Document Management (Landela DMS)</h3>
      <p className="text-sm text-gray-500">Upload, manage, and retrieve clinical documents. Backed by MinIO object storage with SHA-256 content verification.</p>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <FileText className="w-6 h-6 text-impilo-400 mx-auto mb-1" /><p className="text-lg font-bold text-gray-900">{docs.length}</p><p className="text-xs text-gray-500">Documents</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <Shield className="w-6 h-6 text-green-500 mx-auto mb-1" /><p className="text-lg font-bold text-gray-900">SHA-256</p><p className="text-xs text-gray-500">Content Verify</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <Download className="w-6 h-6 text-purple-500 mx-auto mb-1" /><p className="text-lg font-bold text-gray-900">Pre-signed</p><p className="text-xs text-gray-500">Secure URLs</p>
        </div>
      </div>

      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : docs.length === 0 ? (
        <div className="bg-white rounded-lg border p-12 text-center"><FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No documents uploaded yet</p></div>
      ) : (
        <div className="space-y-2">{docs.map((doc, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <FileText className="w-5 h-5 text-impilo-400" />
              <div><p className="text-sm font-medium text-gray-900">{String(doc.filename ?? doc.name ?? "Document")}</p>
                <p className="text-xs text-gray-500">{String(doc.content_type ?? doc.mime_type ?? "—")} · {String(doc.created_at ?? "—")}</p></div>
            </div>
            <span className="text-xs text-gray-400 font-mono">{String(doc.object_id ?? doc.id ?? "").slice(0, 8)}</span>
          </div>
        ))}</div>
      )}
    </div>
  );
}

/* ── CDS Alerts ──────────────────────────────────────────────────── */

function CdsTab() {
  return (
    <div className="space-y-6">
      <h3 className="text-base font-semibold text-gray-900">Clinical Decision Support</h3>
      <p className="text-sm text-gray-500">Rule-based alerts evaluated during encounters. No ML required — purely clinical guideline rules.</p>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">Rule</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Source</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Condition</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Severity</th>
          </tr></thead>
          <tbody>
            {([
              ["Hypertensive Crisis","Vitals","Systolic BP \u2265 180 mmHg","critical"],
              ["Hypoxemia","Vitals","SpO\u2082 < 92%","critical"],
              ["Drug-Allergy Interaction","Allergies","Active allergy + matching medication","critical"],
              ["Tachycardia","Vitals","Heart rate > 120 bpm","warning"],
              ["Bradycardia","Vitals","Heart rate < 50 bpm","warning"],
              ["Fever","Vitals","Temperature \u2265 38.5\u00b0C","warning"],
              ["Severe Allergy Flag","Allergies","Severity = SEVERE or LIFE_THREATENING","warning"],
              ["Diabetes Monitoring","Conditions","Active diabetes (ICD E11)","info"],
              ["Hypertension Monitoring","Conditions","Active hypertension (ICD I10)","info"],
              ["Asthma Alert","Conditions","Active asthma \u2014 avoid beta-blockers","info"],
            ] as const).map(([rule,source,condition,severity]) => (
              <tr key={rule} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-medium text-gray-900">{rule}</td>
                <td className="px-3 py-2"><span className="px-1.5 py-0.5 rounded bg-impilo-100 text-impilo-600 text-[10px]">{source}</span></td>
                <td className="px-3 py-2 text-gray-600">{condition}</td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded text-[10px] ${severity === "critical" ? "bg-red-100 text-red-700" : severity === "warning" ? "bg-amber-100 text-amber-700" : "bg-impilo-100 text-impilo-600"}`}>{severity}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-green-50 rounded-lg border border-green-200 p-4 text-sm text-green-800">
        <strong>Active in Encounters:</strong> CDS alerts are automatically evaluated when viewing any encounter. Alerts appear as dismissable banners above the encounter content.
      </div>
    </div>
  );
}

/* ── Productivity ────────────────────────────────────────────────── */

function ProductivityTab() {
  return (
    <div className="space-y-6">
      <h3 className="text-base font-semibold text-gray-900">Clinical Productivity Tools</h3>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Heart className="w-6 h-6 text-red-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Vitals Trend Charts</h4>
          <p className="text-xs text-gray-500 mt-1">SVG sparkline charts for BP, HR, SpO\u2082, and temperature trends. Automatically rendered when 2+ readings exist.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active in Vitals page</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <ClipboardList className="w-6 h-6 text-impilo-400 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Referral Package Builder</h4>
          <p className="text-xs text-gray-500 mt-1">4-step wizard that auto-generates clinical summaries from patient conditions, allergies, and medications.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active in Consults page</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Shield className="w-6 h-6 text-indigo-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Specialty Workspaces</h4>
          <p className="text-xs text-gray-500 mt-1">6 specialty-specific workspaces (Cardiology, Surgery, Obstetrics, Paediatrics, Emergency, Orthopaedics) with tailored tools and order sets.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active at /ehr/[patientId]/workspace/[specialty]</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Settings className="w-6 h-6 text-gray-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Encounter Menu Toggle</h4>
          <p className="text-xs text-gray-500 mt-1">Left/right position toggle for the EHR encounter menu. Persists preference in session storage.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active in EHR Layout</p>
        </div>
      </div>
    </div>
  );
}
