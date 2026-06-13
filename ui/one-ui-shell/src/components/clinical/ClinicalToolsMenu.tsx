"use client";

/**
 * ClinicalToolsMenu — Comprehensive clinical tools popover menu.
 *
 * Categories: Drug database, Interaction checker, Clinical calculators,
 * Conditions browser (ICD-10), Dosing guidelines, Formulary.
 *
 * Searchable, grouped by category, with sheet-based tool panels.
 */

import { useState } from "react";
import {
  Pill,
  Stethoscope,
  ArrowLeftRight,
  Calculator,
  ClipboardList,
  Search,
  ChevronRight,
  BookOpen,
} from "lucide-react";
import { cn } from "@/lib/accessibility";
import type { FormComplexity } from "@/hooks/useCadreFormConfig";
import { MedscapeTools } from "@/components/clinical/MedscapeTools";
import { useClinicalGuidanceOptional } from "@/components/clinical/ClinicalGuidanceContext";

// ── Tool definitions ──────────────────────────────────────

interface ClinicalTool {
  id: string;
  icon: typeof Pill;
  label: string;
  description: string;
  category: "drugs" | "conditions" | "interactions" | "calculators" | "formulary";
}

const clinicalTools: ClinicalTool[] = [
  // Drugs
  { id: "drug-search", icon: Pill, label: "Drug Database", description: "Search medications, dosing, indications & contraindications", category: "drugs" },
  { id: "drug-monograph", icon: Pill, label: "Drug Monographs", description: "Detailed pharmacology, PK/PD, adverse effects", category: "drugs" },
  { id: "otc-rx", icon: Pill, label: "OTC & Rx Lookup", description: "Over-the-counter and prescription drug information", category: "drugs" },

  // Conditions
  { id: "disease-lookup", icon: Stethoscope, label: "Diseases & Conditions", description: "Clinical presentations, differential diagnosis, management", category: "conditions" },
  { id: "icd-browser", icon: Stethoscope, label: "ICD-10 Browser", description: "Search and browse diagnostic codes", category: "conditions" },
  { id: "clinical-images", icon: Stethoscope, label: "Clinical Images Library", description: "Dermatology, radiology, pathology image references", category: "conditions" },

  // Interaction Checker
  { id: "drug-interaction", icon: ArrowLeftRight, label: "Drug Interaction Checker", description: "Check multi-drug interactions and severity", category: "interactions" },
  { id: "drug-allergy", icon: ArrowLeftRight, label: "Drug-Allergy Cross-Check", description: "Cross-reactivity and allergy verification", category: "interactions" },
  { id: "food-drug", icon: ArrowLeftRight, label: "Food-Drug Interactions", description: "Dietary considerations affecting drug efficacy", category: "interactions" },

  // Calculators
  { id: "calc-egfr", icon: Calculator, label: "eGFR Calculator", description: "CKD-EPI, Cockcroft-Gault renal function", category: "calculators" },
  { id: "calc-bmi", icon: Calculator, label: "BMI Calculator", description: "Body mass index with classification", category: "calculators" },
  { id: "calc-wells", icon: Calculator, label: "Wells Score (DVT/PE)", description: "Deep vein thrombosis & pulmonary embolism risk", category: "calculators" },
  { id: "calc-chadsvasc", icon: Calculator, label: "CHA2DS2-VASc", description: "Atrial fibrillation stroke risk stratification", category: "calculators" },
  { id: "calc-sofa", icon: Calculator, label: "SOFA / qSOFA Score", description: "Sepsis-related organ failure assessment", category: "calculators" },
  { id: "calc-apgar", icon: Calculator, label: "APGAR Score", description: "Neonatal assessment at birth", category: "calculators" },
  { id: "calc-paed-dose", icon: Calculator, label: "Paediatric Dosing", description: "Weight-based dose calculations", category: "calculators" },

  // Formulary
  { id: "formulary-national", icon: ClipboardList, label: "National Essential Medicines", description: "EML with standard treatment regimens", category: "formulary" },
  { id: "formulary-facility", icon: ClipboardList, label: "Facility Formulary", description: "Locally available medicines and stock status", category: "formulary" },
  { id: "formulary-restricted", icon: ClipboardList, label: "Restricted Medicines List", description: "Medicines requiring special authorisation", category: "formulary" },
];

const categoryConfig: Record<string, { label: string; color: string; icon: typeof Pill }> = {
  drugs: { label: "Drugs", color: "bg-primary-soft text-primary", icon: Pill },
  conditions: { label: "Diseases & Conditions", color: "bg-success-soft text-primary-hover", icon: Stethoscope },
  interactions: { label: "Interaction Checker", color: "bg-warning-soft text-warning-foreground", icon: ArrowLeftRight },
  calculators: { label: "Calculators", color: "bg-warning-soft text-warning-foreground", icon: Calculator },
  formulary: { label: "Formulary", color: "bg-danger-soft text-danger", icon: ClipboardList },
};

// ── Component ─────────────────────────────────────────────

interface ClinicalToolsMenuProps {
  complexity?: FormComplexity;
}

export function ClinicalToolsMenu({ complexity = "comprehensive" }: ClinicalToolsMenuProps) {
  const guidance = useClinicalGuidanceOptional();
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeToolId, setActiveToolId] = useState<string | null>(null);

  const filtered = clinicalTools.filter(
    (tool) =>
      tool.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
      tool.description.toLowerCase().includes(searchQuery.toLowerCase()),
  );

  const grouped = Object.keys(categoryConfig).reduce(
    (acc, cat) => {
      acc[cat] = filtered.filter((t) => t.category === cat);
      return acc;
    },
    {} as Record<string, ClinicalTool[]>,
  );

  const openTool = (toolId: string) => {
    setOpen(false);
    setActiveToolId(toolId);
  };

  return (
    <div className="relative shrink-0">
      {/* Trigger button */}
      <button
        className={cn(
          "h-8 gap-1.5 text-xs px-2 rounded flex items-center hover:bg-neutral-100",
          open && "bg-primary-soft text-primary",
        )}
        onClick={() => setOpen(!open)}
      >
        <Pill className="w-3.5 h-3.5" />
        Drugs
      </button>

      {/* Popover */}
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full z-50 mt-1 w-80 rounded-lg border border-border bg-card shadow-lg">
            <div className="p-3 pb-2">
              <h3 className="font-semibold text-sm">Clinical Tools</h3>
              <p className="text-xs text-muted-foreground">
                Drug references, calculators & formulary
              </p>
            </div>

            {guidance && (
              <div className="px-3 pb-2 space-y-1">
                <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider">National guidance</p>
                <button
                  type="button"
                  onClick={() => {
                    guidance.openDock("pathways");
                    setOpen(false);
                  }}
                  className="flex w-full items-center gap-2 rounded-md border border-primary/20 bg-primary-soft/50 px-2 py-2 text-left text-xs text-impilo-800 hover:bg-primary-soft"
                >
                  <BookOpen className="h-3.5 w-3.5 shrink-0" />
                  <span>Pathways &amp; structured flows</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    guidance.openDock("prescribing");
                    setOpen(false);
                  }}
                  className="flex w-full items-center gap-2 rounded-md border border-emerald-100 bg-success-soft/50 px-2 py-2 text-left text-xs text-primary-hover hover:bg-success-soft"
                >
                  <Pill className="h-3.5 w-3.5 shrink-0" />
                  <span>EDLIZ prescribing check</span>
                </button>
              </div>
            )}

            <div className="px-3 pb-2">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                <input
                  placeholder="Search tools..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="h-8 w-full pl-8 text-sm rounded-md border border-border bg-card focus:outline-none focus:ring-2 focus:ring-primary/40"
                />
              </div>
            </div>

            <div className="border-t border-border" />

            <div className="max-h-[400px] overflow-y-auto">
              {Object.entries(grouped).map(([category, items]) => {
                if (items.length === 0) return null;
                const config = categoryConfig[category];
                return (
                  <div key={category} className="p-2">
                    <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider px-2 mb-1">
                      {config.label}
                    </p>
                    {items.map((tool) => (
                      <button
                        key={tool.id}
                        onClick={() => openTool(tool.id)}
                        className="w-full flex items-center gap-3 px-2 py-2 rounded-md hover:bg-background transition-colors text-left"
                      >
                        <div className="h-7 w-7 rounded-md bg-primary-soft flex items-center justify-center shrink-0">
                          <tool.icon className="h-3.5 w-3.5 text-primary" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-medium leading-tight">{tool.label}</p>
                          <p className="text-[11px] text-muted-foreground leading-snug">{tool.description}</p>
                        </div>
                        <ChevronRight className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                      </button>
                    ))}
                  </div>
                );
              })}
              {filtered.length === 0 && (
                <div className="p-6 text-center">
                  <p className="text-sm text-muted-foreground">No tools found</p>
                </div>
              )}
            </div>
          </div>
        </>
      )}

      {/* Tool Sheet Overlay */}
      {activeToolId && (
        <MedscapeTools
          open={true}
          toolId={activeToolId}
          complexity={complexity}
          onClose={() => setActiveToolId(null)}
        />
      )}
    </div>
  );
}
