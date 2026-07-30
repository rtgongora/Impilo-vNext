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
 *
 * Calculators live in components/clinical/calculators. Both the list and the arithmetic come from
 * the platform — this page holds neither, which is what stops the list drifting from what actually
 * computes.
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
import { AIDiagnosticAssistant } from "@/components/clinical/AIDiagnosticAssistant";
import { CalculatorsPanel } from "@/components/clinical/calculators/CalculatorsPanel";

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
  { key: "drugs", label: "Drugs", icon: Pill, color: "text-primary", activeColor: "bg-blue-600", description: "Drug monographs, dosing, contraindications, and safety information" },
  { key: "conditions", label: "Conditions", icon: HeartPulse, color: "text-rose-600", activeColor: "bg-rose-600", description: "Diseases & conditions — presentation, diagnosis, management, and prognosis" },
  { key: "interactions", label: "Interaction Checker", icon: FlaskConical, color: "text-amber-600", activeColor: "bg-amber-600", description: "Check multi-drug interactions, severity, and clinical significance" },
  { key: "calculators", label: "Calculators", icon: Calculator, color: "text-primary", activeColor: "bg-emerald-600", description: "Clinical scoring tools — GFR, BMI, CURB-65, Wells, MELD, APGAR, and more" },
  { key: "formulary", label: "Formulary", icon: BookOpen, color: "text-purple-600", activeColor: "bg-purple-600", description: "National formulary (EDLIZ) — approved medications, tiers, and alternatives" },
  { key: "directory", label: "Directory", icon: Contact, color: "text-muted-foreground", activeColor: "bg-slate-700", description: "Provider and facility directory — find specialists, refer, and contact" },
];

const REFERENCES: RefDef[] = [
  { key: "pill-id", label: "Pill Identifier", icon: ScanSearch, color: "text-cyan-600", bgColor: "bg-cyan-50 border-cyan-200", description: "Identify unknown pills by shape, color, imprint, and scoring" },
  { key: "guidelines", label: "Latest Guidelines", icon: FileText, color: "text-indigo-600", bgColor: "bg-info-soft border-info/25", description: "Current clinical practice guidelines — EDLIZ, WHO, and specialty societies" },
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
          <span className="text-muted-foreground">·</span>
          <Link href="/clinical-tools/forms" className="text-cyan-700 hover:underline font-medium">
            Form schema builder
          </Link>
          <span className="text-muted-foreground">·</span>
          <Link href="/ask" className="text-primary hover:underline font-medium">
            Ask EDLIZ
          </Link>
        </div>

        {/* ── Top toolbar: Drugs · Conditions · Interaction Checker · Calculators · Formulary · Directory ── */}
        <div className="flex items-center gap-1 mb-6 border-b border-border overflow-x-auto pb-px">
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
                    : "border-transparent text-muted-foreground hover:text-foreground"
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
            <button onClick={goBack} className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-4">
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
              <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground mb-3">References & SOPs</h2>
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
                        <p className="text-sm font-semibold text-foreground">{ref.label}</p>
                        <p className="text-xs text-muted-foreground line-clamp-2">{ref.description}</p>
                      </div>
                      <ChevronRight className="w-4 h-4 text-muted-foreground shrink-0 ml-auto" />
                    </button>
                  );
                })}
              </div>
            </section>

            {/* ── Utility tabs (dictation, offline, docs, CDS, productivity) ── */}
            <section>
              <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground mb-3">Utilities</h2>
              <div className="flex gap-1 mb-6 border-b border-border overflow-x-auto">
                {UTILITY_TABS.map((tab) => {
                  const Icon = tab.icon;
                  return (
                    <button key={tab.key} onClick={() => setUtilityTab(tab.key)}
                      className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                        utilityTab === tab.key ? "border-pink-600 text-pink-600" : "border-transparent text-muted-foreground hover:text-foreground"
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
        <Pill className="w-6 h-6 text-primary" />
        <h2 className="text-lg font-semibold text-foreground">Drug Reference</h2>
      </div>
      <p className="text-sm text-muted-foreground">Search drug monographs for dosing, contraindications, adverse effects, and pharmacokinetics.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-muted-foreground" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search drugs by name, class, or indication..."
          className="w-full rounded-lg border border-border pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-blue-400 animate-spin" />}
      </div>

      {query.length < 2 ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {["Analgesics", "Antibiotics", "Antihypertensives", "Antiretrovirals", "Antidiabetics", "Antimalarials"].map((cat) => (
            <button key={cat} onClick={() => setQuery(cat)} className="rounded-lg border border-border bg-card p-3 text-left hover:bg-info-soft transition">
              <p className="text-sm font-medium text-foreground">{cat}</p>
              <p className="text-xs text-muted-foreground mt-0.5">Browse category</p>
            </button>
          ))}
        </div>
      ) : results.length === 0 && !isFetching ? (
        <div className="bg-background rounded-lg border p-8 text-center">
          <Pill className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">No drugs found for &quot;{query}&quot;</p>
        </div>
      ) : (
        <div className="space-y-2">
          {results.map((drug, i) => (
            <div key={i} className="bg-card rounded-lg border border-border p-4 hover:border-blue-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-foreground">{String(drug.name ?? drug.generic_name ?? "—")}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{String(drug.drug_class ?? drug.category ?? "—")}</p>
                </div>
                <span className="text-xs bg-blue-100 text-primary-hover rounded-full px-2 py-0.5">{String(drug.schedule ?? "OTC")}</span>
              </div>
              {Boolean(drug.indication) && <p className="text-xs text-muted-foreground mt-2">{String(drug.indication)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-info-soft rounded-lg border border-info/25 p-4">
        <h3 className="text-sm font-semibold text-blue-900 mb-2">Drug Monograph Sections</h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-primary-hover">
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
        <h2 className="text-lg font-semibold text-foreground">Diseases & Conditions</h2>
      </div>
      <p className="text-sm text-muted-foreground">Evidence-based clinical information — presentation, differential diagnosis, work-up, management, and prognosis.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-muted-foreground" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search conditions, symptoms, or ICD codes..."
          className="w-full rounded-lg border border-border pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-rose-400 animate-spin" />}
      </div>

      {query.length < 2 ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          {BODY_SYSTEMS.map((sys) => (
            <button key={sys} onClick={() => setQuery(sys)} className="rounded-lg border border-border bg-card p-3 text-left hover:bg-danger-soft transition">
              <p className="text-sm font-medium text-foreground">{sys}</p>
            </button>
          ))}
        </div>
      ) : results.length === 0 && !isFetching ? (
        <div className="bg-background rounded-lg border p-8 text-center">
          <HeartPulse className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">No conditions found for &quot;{query}&quot;</p>
        </div>
      ) : (
        <div className="space-y-2">
          {results.map((cond, i) => (
            <div key={i} className="bg-card rounded-lg border border-border p-4 hover:border-rose-300 transition">
              <p className="text-sm font-semibold text-foreground">{String(cond.name ?? cond.title ?? "—")}</p>
              <p className="text-xs text-muted-foreground mt-0.5">{String(cond.icd_code ?? "")} {cond.system ? `· ${String(cond.system)}` : ""}</p>
              {Boolean(cond.overview) && <p className="text-xs text-muted-foreground mt-2 line-clamp-2">{String(cond.overview)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-danger-soft rounded-lg border border-danger/28 p-4">
        <h3 className="text-sm font-semibold text-danger mb-2">Condition Monograph Sections</h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-danger">
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
        <h2 className="text-lg font-semibold text-foreground">Drug Interaction Checker</h2>
      </div>
      <p className="text-sm text-muted-foreground">Enter two or more medications to check for clinically significant interactions.</p>

      <div className="bg-card rounded-lg border border-border p-4 space-y-3">
        {drugs.map((drug, i) => (
          <div key={i} className="flex gap-2">
            <input type="text" value={drug} onChange={(e) => updateDrug(i, e.target.value)}
              placeholder={`Drug ${i + 1}...`}
              className="flex-1 rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-transparent" />
            {drugs.length > 1 && (
              <button onClick={() => removeDrug(i)} className="px-2 text-muted-foreground hover:text-red-500"><XCircle className="w-4 h-4" /></button>
            )}
          </div>
        ))}
        <div className="flex gap-2">
          <button onClick={addDrug} className="text-sm text-amber-600 hover:text-warning-foreground font-medium">+ Add another drug</button>
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
                interaction.severity === "major" ? "bg-danger-soft border-danger/28" :
                interaction.severity === "moderate" ? "bg-warning-soft border-warning/35" :
                "bg-yellow-50 border-yellow-200"
              }`}>
                <div className="flex items-center justify-between mb-1">
                  <p className="text-sm font-semibold text-foreground">{interaction.pair}</p>
                  <span className={`text-xs font-medium rounded-full px-2 py-0.5 ${
                    interaction.severity === "major" ? "bg-red-100 text-danger" :
                    interaction.severity === "moderate" ? "bg-amber-100 text-warning-foreground" :
                    "bg-yellow-100 text-yellow-700"
                  }`}>{interaction.severity}</span>
                </div>
                <p className="text-xs text-muted-foreground">{interaction.description}</p>
              </div>
            ))}
          </div>
        )
      )}

      <div className="bg-warning-soft rounded-lg border border-warning/35 p-4 text-xs text-warning-foreground">
        <div className="flex items-start gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <p>Interaction data is for clinical decision support only. Always verify against authoritative sources. Severity levels: <strong>Major</strong> (avoid combination), <strong>Moderate</strong> (use with caution), <strong>Minor</strong> (monitor).</p>
        </div>
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
        <h2 className="text-lg font-semibold text-foreground">National Formulary (EDLIZ)</h2>
      </div>
      <p className="text-sm text-muted-foreground">Essential Drugs List for Zimbabwe — approved medications, recommended tiers, restrictions, and alternatives.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-muted-foreground" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search formulary by drug name, condition, or tier..."
          className="w-full rounded-lg border border-border pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-purple-400 animate-spin" />}
      </div>

      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Tier 1 — Essential", desc: "First-line, widely available", color: "bg-green-50 border-green-200 text-green-700" },
          { label: "Tier 2 — Supplementary", desc: "Second-line or specialist", color: "bg-info-soft border-info/25 text-primary-hover" },
          { label: "Tier 3 — Restricted", desc: "Specialist-only, prior auth", color: "bg-warning-soft border-warning/35 text-warning-foreground" },
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
            <div key={i} className="bg-card rounded-lg border border-border p-4">
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-foreground">{String(item.name ?? "—")}</p>
                <span className={`text-xs rounded-full px-2 py-0.5 ${
                  item.tier === 1 ? "bg-green-100 text-green-700" : item.tier === 2 ? "bg-blue-100 text-primary-hover" : "bg-amber-100 text-warning-foreground"
                }`}>Tier {String(item.tier ?? "—")}</span>
              </div>
              {Boolean(item.indication) && <p className="text-xs text-muted-foreground mt-1">{String(item.indication)}</p>}
            </div>
          ))}
        </div>
      )}

      <div className="bg-warning-soft rounded-lg border border-warning/35 p-4 text-xs text-purple-800">
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
        <Contact className="w-6 h-6 text-muted-foreground" />
        <h2 className="text-lg font-semibold text-foreground">Provider & Facility Directory</h2>
      </div>
      <p className="text-sm text-muted-foreground">Search registered providers and facilities for referrals, consultations, and contact information.</p>

      <div className="flex gap-2 mb-2">
        {(["providers", "facilities"] as const).map((t) => (
          <button key={t} onClick={() => { setSearchType(t); setQuery(""); }}
            className={`px-3 py-1.5 text-sm rounded-lg font-medium transition ${searchType === t ? "bg-neutral-900 text-white" : "bg-neutral-100 text-muted-foreground hover:bg-neutral-100"}`}>
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
        <Search className="absolute left-3 top-3 w-4 h-4 text-muted-foreground" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder={searchType === "providers" ? "Search by name, specialty, or registration number..." : "Search by facility name, district, or type..."}
          className="w-full rounded-lg border border-border pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-muted-foreground animate-spin" />}
      </div>

      {results.length > 0 ? (
        <div className="space-y-2">
          {results.map((entry, i) => (
            <div key={i} className="bg-card rounded-lg border border-border p-4 hover:border-border transition">
              <p className="text-sm font-semibold text-foreground">{String(entry.name ?? entry.display_name ?? "—")}</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                {searchType === "providers"
                  ? `${String(entry.specialty ?? "—")} · ${String(entry.registration_number ?? "")}`
                  : `${String(entry.type ?? "—")} · ${String(entry.district ?? "")}`}
              </p>
            </div>
          ))}
        </div>
      ) : query.length >= 2 && !isFetching ? (
        <div className="bg-background rounded-lg border p-8 text-center">
          <Contact className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">No {searchType} found for &quot;{query}&quot;</p>
        </div>
      ) : null}

      <div className="grid grid-cols-2 gap-3">
        <Link href="/registry" className="bg-card rounded-lg border border-border p-3 hover:bg-background transition text-center">
          <p className="text-sm font-medium text-foreground">Full Registry</p>
          <p className="text-xs text-muted-foreground">Browse all registries</p>
        </Link>
        <Link href="/discover" className="bg-card rounded-lg border border-border p-3 hover:bg-background transition text-center">
          <p className="text-sm font-medium text-foreground">Discover Services</p>
          <p className="text-xs text-muted-foreground">Find nearby services</p>
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
        <h2 className="text-lg font-semibold text-foreground">Pill Identifier</h2>
      </div>
      <p className="text-sm text-muted-foreground">Identify unknown tablets and capsules by their physical characteristics.</p>

      <div className="bg-card rounded-lg border border-border p-4 space-y-4">
        <div>
          <label className="block text-xs font-medium text-foreground mb-1.5">Shape</label>
          <div className="flex flex-wrap gap-1.5">
            {SHAPES.map((s) => (
              <button key={s} onClick={() => setShape(shape === s ? "" : s)}
                className={`text-xs px-2.5 py-1 rounded-full border transition ${shape === s ? "bg-cyan-100 border-cyan-400 text-cyan-700" : "border-border text-muted-foreground hover:bg-background"}`}>
                {s}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-foreground mb-1.5">Color</label>
          <div className="flex flex-wrap gap-1.5">
            {COLORS.map((c) => (
              <button key={c} onClick={() => setColor(color === c ? "" : c)}
                className={`text-xs px-2.5 py-1 rounded-full border transition ${color === c ? "bg-cyan-100 border-cyan-400 text-cyan-700" : "border-border text-muted-foreground hover:bg-background"}`}>
                {c}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-foreground mb-1.5">Imprint / Markings</label>
          <input type="text" value={imprint} onChange={(e) => setImprint(e.target.value)}
            placeholder="Enter letters, numbers, or logos on the pill..."
            className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-cyan-500 focus:border-transparent" />
        </div>
      </div>

      {isFetching && <Loader2 className="w-6 h-6 animate-spin text-cyan-400 mx-auto" />}

      {results.length > 0 && (
        <div className="space-y-2">
          {results.map((pill, i) => (
            <div key={i} className="bg-card rounded-lg border border-border p-4">
              <p className="text-sm font-semibold text-foreground">{String(pill.name ?? "—")}</p>
              <p className="text-xs text-muted-foreground">{String(pill.strength ?? "")} · {String(pill.manufacturer ?? "")}</p>
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
        <h2 className="text-lg font-semibold text-foreground">Latest Clinical Guidelines</h2>
      </div>
      <p className="text-sm text-muted-foreground">Current clinical practice guidelines from EDLIZ, WHO, and specialty societies.</p>

      <div className="flex gap-2 flex-wrap">
        {CATEGORIES.map((cat) => (
          <button key={cat} onClick={() => setCategory(cat === "All" ? "all" : cat)}
            className={`text-xs px-3 py-1.5 rounded-full border transition font-medium ${
              (cat === "All" ? "all" : cat) === category ? "bg-indigo-100 border-indigo-400 text-primary-hover" : "border-border text-muted-foreground hover:bg-info-soft"
            }`}>
            {cat}
          </button>
        ))}
      </div>

      {isLoading ? (
        <Loader2 className="w-6 h-6 animate-spin text-indigo-400 mx-auto" />
      ) : guidelines.length === 0 ? (
        <div className="bg-background rounded-lg border p-8 text-center">
          <FileText className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">No guidelines available for this category yet.</p>
          <p className="text-xs text-muted-foreground mt-1">Guidelines are ingested through <Link href="/admin/clinical-curation" className="text-indigo-600 underline">Knowledge Curation</Link>.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {guidelines.map((g, i) => (
            <div key={i} className="bg-card rounded-lg border border-border p-4 hover:border-indigo-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-foreground">{String(g.title ?? "—")}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{String(g.source ?? "")} · {String(g.year ?? "")}</p>
                </div>
                {Boolean(g.url) && <ExternalLink className="w-4 h-4 text-muted-foreground shrink-0" />}
              </div>
              {Boolean(g.summary) && <p className="text-xs text-muted-foreground mt-2 line-clamp-2">{String(g.summary)}</p>}
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
        <h2 className="text-lg font-semibold text-foreground">Procedures</h2>
      </div>
      <p className="text-sm text-muted-foreground">Step-by-step procedural guides with indications, technique, complications, and post-procedure care.</p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-muted-foreground" />
        <input type="text" value={query} onChange={(e) => setQuery(e.target.value)}
          placeholder="Search procedures by name or specialty..."
          className="w-full rounded-lg border border-border pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent" />
        {isFetching && <Loader2 className="absolute right-3 top-3 w-4 h-4 text-orange-400 animate-spin" />}
      </div>

      {query.length < 2 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {SPECIALTIES.map((spec) => (
            <button key={spec} onClick={() => setQuery(spec)} className="rounded-lg border border-border bg-card p-3 text-left hover:bg-orange-50 transition">
              <p className="text-sm font-medium text-foreground">{spec}</p>
            </button>
          ))}
        </div>
      )}

      {results.length > 0 && (
        <div className="space-y-2">
          {results.map((proc, i) => (
            <div key={i} className="bg-card rounded-lg border border-border p-4 hover:border-orange-300 transition">
              <p className="text-sm font-semibold text-foreground">{String(proc.name ?? proc.title ?? "—")}</p>
              <p className="text-xs text-muted-foreground mt-0.5">{String(proc.specialty ?? "")} · {String(proc.complexity ?? "")}</p>
              {Boolean(proc.indications) && <p className="text-xs text-muted-foreground mt-2">{String(proc.indications)}</p>}
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
        <h2 className="text-lg font-semibold text-foreground">Cases & Quizzes</h2>
      </div>
      <p className="text-sm text-muted-foreground">Interactive clinical cases, image challenges, and knowledge quizzes for continuing professional development.</p>

      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Clinical Cases", desc: "Work through patient scenarios", icon: Star, color: "text-pink-500" },
          { label: "Image Challenges", desc: "Radiology, dermatology, pathology", icon: ScanSearch, color: "text-indigo-500" },
          { label: "Knowledge Quizzes", desc: "Test & reinforce learning", icon: GraduationCap, color: "text-emerald-500" },
        ].map((item) => {
          const Icon = item.icon;
          return (
            <div key={item.label} className="bg-card rounded-lg border border-border p-4 text-center">
              <Icon className={`w-6 h-6 mx-auto mb-2 ${item.color}`} />
              <p className="text-sm font-semibold text-foreground">{item.label}</p>
              <p className="text-xs text-muted-foreground mt-0.5">{item.desc}</p>
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
            <div key={i} className="bg-card rounded-lg border border-border p-4 hover:border-pink-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-foreground">{String(c.title ?? "—")}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{String(c.specialty ?? "")} · {String(c.difficulty ?? "")}</p>
                </div>
                <div className="flex items-center gap-1 text-xs text-muted-foreground">
                  <Clock className="w-3 h-3" /><span>{String(c.duration ?? "15 min")}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="bg-background rounded-lg border border-border p-4 text-xs text-muted-foreground">
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
        <h2 className="text-lg font-semibold text-foreground">Clinical Podcasts</h2>
      </div>
      <p className="text-sm text-muted-foreground">CME-eligible audio content — grand rounds, case discussions, guideline updates, and clinical pearls.</p>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: "Grand Rounds", count: "Weekly", color: "bg-violet-100 text-violet-700" },
          { label: "Case Discussions", count: "Bi-weekly", color: "bg-pink-100 text-pink-700" },
          { label: "Guideline Updates", count: "Monthly", color: "bg-indigo-100 text-primary-hover" },
          { label: "Clinical Pearls", count: "Daily", color: "bg-emerald-100 text-primary-hover" },
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
            <div key={i} className="bg-card rounded-lg border border-border p-4 hover:border-violet-300 transition">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-semibold text-foreground">{String(ep.title ?? "—")}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{String(ep.series ?? "")} · {String(ep.date ?? "")}</p>
                </div>
                <div className="flex items-center gap-1 text-xs text-muted-foreground">
                  <Clock className="w-3 h-3" /><span>{String(ep.duration ?? "30 min")}</span>
                </div>
              </div>
              {Boolean(ep.summary) && <p className="text-xs text-muted-foreground mt-2 line-clamp-2">{String(ep.summary)}</p>}
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
        <div className="bg-warning-soft rounded-lg border border-warning/35 p-5 text-center">
          <MicOff className="w-10 h-10 text-amber-400 mx-auto mb-3" />
          <p className="text-sm text-warning-foreground">Speech recognition is not supported in this browser.</p>
          <p className="text-xs text-amber-600 mt-1">Try Chrome, Edge, or Safari for voice dictation.</p>
        </div>
      ) : (
        <div className="bg-card rounded-lg border border-border p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-semibold text-foreground">Dictation</h3>
            <button onClick={isListening ? stopListening : startListening}
              className={`inline-flex items-center gap-2 px-5 py-3 text-sm font-medium rounded-full transition-all ${
                isListening ? "bg-red-600 text-white animate-pulse" : "bg-pink-600 text-white hover:bg-pink-700"
              }`}>
              {isListening ? <><MicOff className="w-5 h-5" /> Stop</> : <><Mic className="w-5 h-5" /> Start Dictating</>}
            </button>
          </div>

          <div className={`min-h-[200px] rounded-lg border-2 p-4 text-sm ${isListening ? "border-pink-300 bg-pink-50" : "border-border bg-background"}`}>
            {transcript ? (
              <p className="text-foreground whitespace-pre-wrap">{transcript}</p>
            ) : (
              <p className="text-muted-foreground italic">{isListening ? "Listening... speak now" : "Press \"Start Dictating\" and speak to capture text"}</p>
            )}
          </div>

          {transcript && (
            <div className="flex gap-2 mt-3">
              <button onClick={() => navigator.clipboard.writeText(transcript)}
                className="px-3 py-1.5 text-xs font-medium bg-neutral-100 text-foreground rounded-lg hover:bg-neutral-100">
                Copy to Clipboard
              </button>
              <button onClick={() => setTranscript("")}
                className="px-3 py-1.5 text-xs font-medium bg-neutral-100 text-foreground rounded-lg hover:bg-neutral-100">
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
      <div className="bg-primary-soft rounded-lg border border-primary/25 p-4 text-sm text-primary-hover">
        <strong>Offline Sync Engine:</strong> The mobile app syncs data in the background every 30 seconds. Conflicts are presented for user resolution. All operations are queued and replayed when connectivity resumes.
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-card rounded-lg border border-border p-4 text-center">
          <Wifi className={`w-8 h-8 mx-auto mb-2 ${syncInfo.offlineCapable ? "text-green-500" : "text-muted-foreground"}`} />
          <p className="text-sm font-semibold text-foreground">Sync Engine</p>
          <p className="text-xs text-green-600">{String(syncInfo.syncEngine ?? "Available")}</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-4 text-center">
          <RefreshCw className="w-8 h-8 text-impilo-400 mx-auto mb-2" />
          <p className="text-sm font-semibold text-foreground">Auto-Sync Interval</p>
          <p className="text-xs text-primary">{Number(syncInfo.autoSyncInterval ?? 30000) / 1000}s</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-4 text-center">
          <AlertTriangle className="w-8 h-8 text-amber-500 mx-auto mb-2" />
          <p className="text-sm font-semibold text-foreground">Conflict Resolution</p>
          <p className="text-xs text-amber-600">{String(syncInfo.conflictResolution ?? "User Prompted")}</p>
        </div>
      </div>

      <div className="bg-card rounded-lg border border-border p-5">
        <h3 className="text-sm font-semibold text-foreground mb-3">Sync Architecture</h3>
        <div className="space-y-2 text-xs text-muted-foreground">
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
      <h3 className="text-base font-semibold text-foreground">Document Management (Landela DMS)</h3>
      <p className="text-sm text-muted-foreground">Upload, manage, and retrieve clinical documents. Backed by MinIO object storage with SHA-256 content verification.</p>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-card rounded-lg border border-border p-4 text-center">
          <FileText className="w-6 h-6 text-impilo-400 mx-auto mb-1" /><p className="text-lg font-bold text-foreground">{docs.length}</p><p className="text-xs text-muted-foreground">Documents</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-4 text-center">
          <Shield className="w-6 h-6 text-green-500 mx-auto mb-1" /><p className="text-lg font-bold text-foreground">SHA-256</p><p className="text-xs text-muted-foreground">Content Verify</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-4 text-center">
          <Download className="w-6 h-6 text-purple-500 mx-auto mb-1" /><p className="text-lg font-bold text-foreground">Pre-signed</p><p className="text-xs text-muted-foreground">Secure URLs</p>
        </div>
      </div>

      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-muted-foreground mx-auto" /> : docs.length === 0 ? (
        <div className="bg-card rounded-lg border p-12 text-center"><FileText className="w-10 h-10 text-muted-foreground mx-auto mb-3" /><p className="text-muted-foreground text-sm">No documents uploaded yet</p></div>
      ) : (
        <div className="space-y-2">{docs.map((doc, i) => (
          <div key={i} className="bg-card rounded-lg border border-border p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <FileText className="w-5 h-5 text-impilo-400" />
              <div><p className="text-sm font-medium text-foreground">{String(doc.filename ?? doc.name ?? "Document")}</p>
                <p className="text-xs text-muted-foreground">{String(doc.content_type ?? doc.mime_type ?? "—")} · {String(doc.created_at ?? "—")}</p></div>
            </div>
            <span className="text-xs text-muted-foreground font-mono">{String(doc.object_id ?? doc.id ?? "").slice(0, 8)}</span>
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
      <h3 className="text-base font-semibold text-foreground">Clinical Decision Support</h3>
      <p className="text-sm text-muted-foreground">Rule-based alerts evaluated during encounters. No ML required — purely clinical guideline rules.</p>

      {/* Real, governed decision-support tool — backed by clinical-knowledge-platform-service. */}
      <AIDiagnosticAssistant />

      <p className="text-sm font-medium text-foreground">Encounter rule catalogue</p>
      <div className="bg-card rounded-lg border border-border overflow-hidden">
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-background">
            <th className="text-left px-3 py-2 font-medium text-muted-foreground">Rule</th>
            <th className="text-left px-3 py-2 font-medium text-muted-foreground">Source</th>
            <th className="text-left px-3 py-2 font-medium text-muted-foreground">Condition</th>
            <th className="text-left px-3 py-2 font-medium text-muted-foreground">Severity</th>
          </tr></thead>
          <tbody>
            {([
              ["Hypertensive Crisis","Vitals","Systolic BP \u2265 180 mmHg","critical"],
              ["Hypoxemia (pending SpO\u2082 capture)","Vitals","SpO\u2082 < 92% \u2014 rule implemented; dormant until SpO\u2082 is recorded","critical"],
              ["Drug-Allergy Interaction","Allergies","Active allergy + matching medication","critical"],
              ["Tachycardia","Vitals","Heart rate > 120 bpm","warning"],
              ["Bradycardia","Vitals","Heart rate < 50 bpm","warning"],
              ["Fever","Vitals","Temperature \u2265 38.5\u00b0C","warning"],
              ["Severe Allergy Flag","Allergies","Severity = SEVERE or LIFE_THREATENING","warning"],
              ["Diabetes Monitoring","Conditions","Active diabetes (ICD E11)","info"],
              ["Hypertension Monitoring","Conditions","Active hypertension (ICD I10)","info"],
              ["Asthma Alert","Conditions","Active asthma \u2014 avoid beta-blockers","info"],
            ] as const).map(([rule,source,condition,severity]) => (
              <tr key={rule} className="border-b last:border-0 hover:bg-background">
                <td className="px-3 py-2 font-medium text-foreground">{rule}</td>
                <td className="px-3 py-2"><span className="px-1.5 py-0.5 rounded bg-primary-soft text-primary text-[10px]">{source}</span></td>
                <td className="px-3 py-2 text-muted-foreground">{condition}</td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded text-[10px] ${severity === "critical" ? "bg-red-100 text-danger" : severity === "warning" ? "bg-amber-100 text-warning-foreground" : "bg-primary-soft text-primary"}`}>{severity}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-primary-soft rounded-lg border border-primary/20 p-4 text-sm text-foreground">
        <strong>How alerts surface:</strong> in the patient EHR, the clinical toolbar evaluates this rule catalogue against
        the patient&apos;s own records (active conditions, prescriptions, latest vitals, documented allergies) via the
        governed rules engine, and any matching alerts appear as dismissable banners above the encounter content. Use the
        decision-support tool above for guideline-grounded answers to specific questions.
      </div>
    </div>
  );
}

/* ── Productivity ────────────────────────────────────────────────── */

function ProductivityTab() {
  return (
    <div className="space-y-6">
      <h3 className="text-base font-semibold text-foreground">Clinical Productivity Tools</h3>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-card rounded-lg border border-border p-5">
          <Heart className="w-6 h-6 text-red-500 mb-2" />
          <h4 className="text-sm font-semibold text-foreground">Vitals Trend Charts</h4>
          <p className="text-xs text-muted-foreground mt-1">SVG sparkline charts for BP, HR, SpO\u2082, and temperature trends. Automatically rendered when 2+ readings exist.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active in Vitals page</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-5">
          <ClipboardList className="w-6 h-6 text-impilo-400 mb-2" />
          <h4 className="text-sm font-semibold text-foreground">Referral Package Builder</h4>
          <p className="text-xs text-muted-foreground mt-1">4-step wizard that auto-generates clinical summaries from patient conditions, allergies, and medications.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active in Consults page</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-5">
          <Shield className="w-6 h-6 text-indigo-500 mb-2" />
          <h4 className="text-sm font-semibold text-foreground">Specialty Workspaces</h4>
          <p className="text-xs text-muted-foreground mt-1">6 specialty-specific workspaces (Cardiology, Surgery, Obstetrics, Paediatrics, Emergency, Orthopaedics) with tailored tools and order sets.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active at /ehr/[patientId]/workspace/[specialty]</p>
        </div>
        <div className="bg-card rounded-lg border border-border p-5">
          <Settings className="w-6 h-6 text-muted-foreground mb-2" />
          <h4 className="text-sm font-semibold text-foreground">Encounter Menu Toggle</h4>
          <p className="text-xs text-muted-foreground mt-1">Left/right position toggle for the EHR encounter menu. Persists preference in session storage.</p>
          <p className="text-xs text-green-600 mt-2">\u2713 Active in EHR Layout</p>
        </div>
      </div>
    </div>
  );
}
