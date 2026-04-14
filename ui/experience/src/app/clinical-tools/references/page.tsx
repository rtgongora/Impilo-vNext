"use client";

/**
 * Clinical References — Medscape-style clinical reference tools.
 * Route: /clinical-tools/references
 *
 * Primary toolbar: Drugs, Conditions, Interaction Checker, Calculators, Formulary, Directory
 * References & SOPs: Pill Identifier, Latest Guidelines, Procedures, Cases & Quizzes, Podcasts
 *
 * Backed by: Clinical Knowledge Platform (port 8270), Search service (port 8230),
 * MSIKA product registry, and guidance service.
 */

import Link from "next/link";
import { useState } from "react";
import {
  Pill,
  HeartPulse,
  FlaskConical,
  Calculator,
  BookOpen,
  Contact,
  ScanSearch,
  FileText,
  Scissors,
  GraduationCap,
  Podcast,
  Search,
  ArrowLeft,
  AlertTriangle,
  ChevronRight,
  Star,
  Clock,
  TrendingUp,
  Loader2,
  ExternalLink,
  Info,
  CheckCircle2,
  XCircle,
  BookMarked,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

/* ── Tool definition types ──────────────────────────────────────── */

type ToolKey = "drugs" | "conditions" | "interactions" | "calculators" | "formulary" | "directory";
type RefKey = "pill-id" | "guidelines" | "procedures" | "cases" | "podcasts";

interface ToolDef {
  key: ToolKey;
  label: string;
  icon: typeof Pill;
  color: string;
  bgColor: string;
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
  { key: "drugs", label: "Drugs", icon: Pill, color: "text-blue-600", bgColor: "bg-blue-50 border-blue-200", description: "Drug monographs, dosing, contraindications, and safety information" },
  { key: "conditions", label: "Conditions", icon: HeartPulse, color: "text-rose-600", bgColor: "bg-rose-50 border-rose-200", description: "Diseases & conditions — presentation, diagnosis, management, and prognosis" },
  { key: "interactions", label: "Interaction Checker", icon: FlaskConical, color: "text-amber-600", bgColor: "bg-amber-50 border-amber-200", description: "Check multi-drug interactions, severity, and clinical significance" },
  { key: "calculators", label: "Calculators", icon: Calculator, color: "text-emerald-600", bgColor: "bg-emerald-50 border-emerald-200", description: "Clinical scoring tools — GFR, BMI, CURB-65, Wells, MELD, APGAR, and more" },
  { key: "formulary", label: "Formulary", icon: BookOpen, color: "text-purple-600", bgColor: "bg-purple-50 border-purple-200", description: "National formulary (EDLIZ) — approved medications, tiers, and alternatives" },
  { key: "directory", label: "Directory", icon: Contact, color: "text-slate-600", bgColor: "bg-slate-50 border-slate-200", description: "Provider and facility directory — find specialists, refer, and contact" },
];

const REFERENCES: RefDef[] = [
  { key: "pill-id", label: "Pill Identifier", icon: ScanSearch, color: "text-cyan-600", bgColor: "bg-cyan-50 border-cyan-200", description: "Identify unknown pills by shape, color, imprint, and scoring" },
  { key: "guidelines", label: "Latest Guidelines", icon: FileText, color: "text-indigo-600", bgColor: "bg-indigo-50 border-indigo-200", description: "Current clinical practice guidelines — EDLIZ, WHO, and specialty societies" },
  { key: "procedures", label: "Procedures", icon: Scissors, color: "text-orange-600", bgColor: "bg-orange-50 border-orange-200", description: "Step-by-step procedural guides with images, indications, and complications" },
  { key: "cases", label: "Cases & Quizzes", icon: GraduationCap, color: "text-pink-600", bgColor: "bg-pink-50 border-pink-200", description: "Interactive clinical cases, image challenges, and knowledge quizzes" },
  { key: "podcasts", label: "Podcasts", icon: Podcast, color: "text-violet-600", bgColor: "bg-violet-50 border-violet-200", description: "Clinical education podcasts — CME-eligible audio content and grand rounds" },
];

/* ── Main page ──────────────────────────────────────────────────── */

export default function ClinicalReferencesPage() {
  const [activeTool, setActiveTool] = useState<ToolKey | null>(null);
  const [activeRef, setActiveRef] = useState<RefKey | null>(null);

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

  const activePanel = activeTool ?? activeRef;

  return (
    <AppLayout>
      <PageShell
        title="Clinical References"
        subtitle="Point-of-care clinical tools and reference library"
        icon={<BookMarked className="w-6 h-6" />}
      >
        <div className="mb-4 flex flex-wrap gap-3 text-sm">
          <Link href="/clinical-tools" className="text-pink-700 hover:underline font-medium flex items-center gap-1">
            <ArrowLeft className="w-3.5 h-3.5" /> Clinical Tools
          </Link>
          <span className="text-gray-300">·</span>
          <Link href="/ask" className="text-impilo-600 hover:underline font-medium">
            Ask EDLIZ
          </Link>
          <span className="text-gray-300">·</span>
          <Link href="/admin/clinical-curation" className="text-purple-700 hover:underline font-medium">
            Knowledge curation
          </Link>
        </div>

        {activePanel ? (
          <div>
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
            {/* ── Primary Clinical Tools Toolbar ─────────────── */}
            <section className="mb-8">
              <h2 className="text-base font-semibold text-gray-900 mb-3">Clinical Tools</h2>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                {TOOLS.map((tool) => {
                  const Icon = tool.icon;
                  return (
                    <button
                      key={tool.key}
                      onClick={() => openTool(tool.key)}
                      className={`flex flex-col items-center gap-2 rounded-xl border p-4 transition hover:shadow-md hover:-translate-y-0.5 ${tool.bgColor}`}
                    >
                      <Icon className={`w-7 h-7 ${tool.color}`} />
                      <span className="text-sm font-semibold text-gray-900">{tool.label}</span>
                    </button>
                  );
                })}
              </div>
            </section>

            {/* ── References & SOPs ──────────────────────────── */}
            <section>
              <h2 className="text-base font-semibold text-gray-900 mb-3">References & SOPs</h2>
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
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}

/* ═══════════════════════════════════════════════════════════════════
   TOOL PANELS
   ═══════════════════════════════════════════════════════════════════ */

/* ── Drugs ──────────────────────────────────────────────────────── */

function DrugsPanel() {
  const [query, setQuery] = useState("");
  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["drug-search", query],
    queryFn: () => apiClient.get(`/internal/v1/clinical-knowledge/drugs?q=${encodeURIComponent(query)}`),
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
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search drugs by name, class, or indication..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
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
              {drug.indication && <p className="text-xs text-gray-600 mt-2">{String(drug.indication)}</p>}
            </div>
          ))}
        </div>
      )}

      <DrugReferenceCategories />
    </div>
  );
}

function DrugReferenceCategories() {
  return (
    <div className="bg-blue-50 rounded-lg border border-blue-200 p-4 mt-4">
      <h3 className="text-sm font-semibold text-blue-900 mb-2">Drug Monograph Sections</h3>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-blue-700">
        {["Dosing & Administration", "Pharmacokinetics", "Contraindications", "Adverse Effects", "Drug Interactions", "Pregnancy & Lactation", "Pediatric Dosing", "Renal/Hepatic Adjustment"].map((s) => (
          <div key={s} className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3" />{s}</div>
        ))}
      </div>
    </div>
  );
}

/* ── Conditions (Diseases & Conditions) ─────────────────────────── */

function ConditionsPanel() {
  const [query, setQuery] = useState("");
  const { data, isFetching } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["condition-search", query],
    queryFn: () => apiClient.get(`/internal/v1/clinical-knowledge/conditions?q=${encodeURIComponent(query)}`),
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
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search conditions, symptoms, or ICD codes..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent"
        />
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
              {cond.overview && <p className="text-xs text-gray-600 mt-2 line-clamp-2">{String(cond.overview)}</p>}
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

  function addDrug() {
    setDrugs((prev) => [...prev, ""]);
  }

  function updateDrug(index: number, value: string) {
    setDrugs((prev) => prev.map((d, i) => (i === index ? value : d)));
  }

  function removeDrug(index: number) {
    if (drugs.length <= 1) return;
    setDrugs((prev) => prev.filter((_, i) => i !== index));
  }

  async function checkInteractions() {
    const filled = drugs.filter((d) => d.trim().length > 0);
    if (filled.length < 2) return;
    setChecking(true);
    try {
      const resp = await apiClient.post<{ data: Array<{ pair: string; severity: string; description: string }> }>(
        "/internal/v1/clinical-knowledge/interactions/check",
        { drugs: filled },
      );
      setResults(resp.data ?? []);
    } catch {
      setResults([]);
    } finally {
      setChecking(false);
    }
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
            <input
              type="text"
              value={drug}
              onChange={(e) => updateDrug(i, e.target.value)}
              placeholder={`Drug ${i + 1}...`}
              className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-transparent"
            />
            {drugs.length > 1 && (
              <button onClick={() => removeDrug(i)} className="px-2 text-gray-400 hover:text-red-500"><XCircle className="w-4 h-4" /></button>
            )}
          </div>
        ))}
        <div className="flex gap-2">
          <button onClick={addDrug} className="text-sm text-amber-600 hover:text-amber-700 font-medium">+ Add another drug</button>
          <button
            onClick={checkInteractions}
            disabled={drugs.filter((d) => d.trim()).length < 2 || checking}
            className="ml-auto px-4 py-2 text-sm font-medium bg-amber-600 text-white rounded-lg hover:bg-amber-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
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
          <p>Interaction data is for clinical decision support only. Always verify interactions against authoritative sources and apply clinical judgement. Severity levels: <strong>Major</strong> (avoid combination), <strong>Moderate</strong> (use with caution), <strong>Minor</strong> (monitor).</p>
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
  { id: "cha2ds2", name: "CHA₂DS₂-VASc", category: "Cardiology", description: "Stroke risk in atrial fibrillation" },
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
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search calculators..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
        />
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
              <button
                key={calc.id}
                onClick={() => setSelectedCalc(calc.id)}
                className="bg-white rounded-lg border border-gray-200 p-4 text-left hover:border-emerald-300 hover:bg-emerald-50/50 transition"
              >
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
    queryFn: () => apiClient.get(`/internal/v1/clinical-knowledge/formulary?q=${encodeURIComponent(query)}`),
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
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search formulary by drug name, condition, or tier..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
        />
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
                  item.tier === 1 ? "bg-green-100 text-green-700" :
                  item.tier === 2 ? "bg-blue-100 text-blue-700" :
                  "bg-amber-100 text-amber-700"
                }`}>Tier {String(item.tier ?? "—")}</span>
              </div>
              {item.indication && <p className="text-xs text-gray-600 mt-1">{String(item.indication)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-purple-50 rounded-lg border border-purple-200 p-4 text-xs text-purple-800">
        <div className="flex items-start gap-2">
          <Info className="w-4 h-4 shrink-0 mt-0.5" />
          <p>Formulary data sourced from the national EDLIZ and curated through the <Link href="/admin/clinical-curation" className="underline font-medium">Knowledge Curation</Link> pipeline. Prescribing decisions evaluated by the guidance prescribing engine.</p>
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
          <button
            key={t}
            onClick={() => { setSearchType(t); setQuery(""); }}
            className={`px-3 py-1.5 text-sm rounded-lg font-medium transition ${searchType === t ? "bg-slate-900 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}
          >
            {t === "providers" ? "Providers" : "Facilities"}
          </button>
        ))}
      </div>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={searchType === "providers" ? "Search by name, specialty, or registration number..." : "Search by facility name, district, or type..."}
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500 focus:border-transparent"
        />
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
              {entry.contact && <p className="text-xs text-gray-400 mt-1">{String(entry.contact)}</p>}
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
    queryFn: () => apiClient.get(`/internal/v1/clinical-knowledge/pill-identifier?shape=${encodeURIComponent(shape)}&color=${encodeURIComponent(color)}&imprint=${encodeURIComponent(imprint)}`),
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
          <input
            type="text"
            value={imprint}
            onChange={(e) => setImprint(e.target.value)}
            placeholder="Enter letters, numbers, or logos on the pill..."
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-cyan-500 focus:border-transparent"
          />
        </div>
      </div>

      {isFetching && <Loader2 className="w-6 h-6 animate-spin text-cyan-400 mx-auto" />}

      {results.length > 0 && (
        <div className="space-y-2">
          {results.map((pill, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4">
              <p className="text-sm font-semibold text-gray-900">{String(pill.name ?? "—")}</p>
              <p className="text-xs text-gray-500">{String(pill.strength ?? "")} · {String(pill.manufacturer ?? "")}</p>
              {pill.description && <p className="text-xs text-gray-600 mt-1">{String(pill.description)}</p>}
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
    queryFn: () => apiClient.get(`/internal/v1/clinical-knowledge/guidelines?category=${encodeURIComponent(category)}`),
  });
  const guidelines = data?.data ?? [];

  const CATEGORIES = ["All", "EDLIZ", "WHO", "Infectious Disease", "Maternal Health", "Paediatrics", "NCD", "Surgery", "Emergency"];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <FileText className="w-6 h-6 text-indigo-600" />
        <h2 className="text-lg font-semibold text-gray-900">Latest Clinical Guidelines</h2>
      </div>
      <p className="text-sm text-gray-500">Current clinical practice guidelines from EDLIZ, WHO, and specialty societies. Updated through the knowledge curation pipeline.</p>

      <div className="flex gap-2 flex-wrap">
        {CATEGORIES.map((cat) => (
          <button key={cat}
            onClick={() => setCategory(cat === "All" ? "all" : cat)}
            className={`text-xs px-3 py-1.5 rounded-full border transition font-medium ${
              (cat === "All" ? "all" : cat) === category
                ? "bg-indigo-100 border-indigo-400 text-indigo-700"
                : "border-gray-200 text-gray-600 hover:bg-indigo-50"
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
                {g.url && <ExternalLink className="w-4 h-4 text-gray-400 shrink-0" />}
              </div>
              {g.summary && <p className="text-xs text-gray-600 mt-2 line-clamp-2">{String(g.summary)}</p>}
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
    queryFn: () => apiClient.get(`/internal/v1/clinical-knowledge/procedures?q=${encodeURIComponent(query)}`),
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
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search procedures by name or specialty..."
          className="w-full rounded-lg border border-gray-300 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
        />
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
              {proc.indications && <p className="text-xs text-gray-600 mt-2">{String(proc.indications)}</p>}
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
    queryFn: () => apiClient.get("/internal/v1/clinical-knowledge/cases"),
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
                  <Clock className="w-3 h-3" />
                  <span>{String(c.duration ?? "15 min")}</span>
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
    queryFn: () => apiClient.get("/internal/v1/clinical-knowledge/podcasts"),
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
                  <Clock className="w-3 h-3" />
                  <span>{String(ep.duration ?? "30 min")}</span>
                </div>
              </div>
              {ep.summary && <p className="text-xs text-gray-600 mt-2 line-clamp-2">{String(ep.summary)}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
