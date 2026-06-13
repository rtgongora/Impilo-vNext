"use client";

/**
 * ClinicalToolbar — Cadre-aware clinical toolbar.
 *
 * - Comprehensive cadres (Doctors, Specialists): All tools visible
 * - Focused cadres (Nurses, Allied Health): Drugs, Interactions, Calculators (filtered), Formulary
 * - Simplified cadres (CHW, Admin): Drugs, basic Calculators only
 *
 * Provides CDS toggle, AI diagnostic assistant button, clinical tools menu,
 * national pathways + EDLIZ dock (EHR), and clinical references.
 */

import { useState } from "react";
import { Route, ToggleLeft, ToggleRight, Users, Sparkles, BookMarked } from "lucide-react";
import { cn } from "@/lib/accessibility";
import { ClinicalToolsMenu } from "@/components/clinical/ClinicalToolsMenu";
import { ClinicalReferences } from "@/components/clinical/ClinicalReferences";
import { ActiveCDSBanner } from "@/components/clinical/ActiveCDSBanner";
import { SystemFeedbackStrip } from "@/components/clinical/SystemFeedbackStrip";
import { useCadreFormConfig } from "@/hooks/useCadreFormConfig";
import { useClinicalGuidance } from "@/components/clinical/ClinicalGuidanceContext";

interface ClinicalToolbarProps {
  hasActivePatient?: boolean;
}

export function ClinicalToolbar({ hasActivePatient = true }: ClinicalToolbarProps) {
  const { pathwaysHighlighted, togglePathwaysDock, openDock } = useClinicalGuidance();
  const cadreConfig = useCadreFormConfig();
  const complexity = cadreConfig.complexity;

  const [cdsEnabled, setCdsEnabled] = useState(true);
  const [aiAssistEnabled, setAiAssistEnabled] = useState(true);

  if (!hasActivePatient) return null;

  return (
    <div className="shrink-0 border-b">
      <SystemFeedbackStrip />

      <div className="relative h-11 min-h-[2.75rem] bg-background flex items-center px-3 overflow-x-auto">
        <div className="flex items-center gap-0.5 flex-1 min-w-0">
          <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider shrink-0 mr-1.5">
            Tools
          </span>
          <div className="h-4 w-px bg-neutral-100 mr-0.5 shrink-0" />

          <ClinicalToolsMenu complexity={complexity} />

          <div className="h-4 w-px bg-neutral-100 mx-0.5 shrink-0" />

          <button
            type="button"
            className={cn(
              "h-8 gap-1.5 text-xs shrink-0 px-2 rounded flex items-center hover:bg-neutral-100",
              pathwaysHighlighted && "bg-primary-soft text-primary",
            )}
            onClick={togglePathwaysDock}
            title="National clinical pathways"
          >
            <Route className="w-3.5 h-3.5" />
            Pathways
          </button>

          <button
            type="button"
            className="h-8 gap-1.5 text-xs shrink-0 px-2 rounded flex items-center hover:bg-neutral-100 text-primary-hover"
            onClick={() => openDock("prescribing")}
            title="EDLIZ prescribing evaluation"
          >
            <BookMarked className="w-3.5 h-3.5" />
            EDLIZ
          </button>

          <button
            className="h-7 w-7 shrink-0 flex items-center justify-center rounded hover:bg-neutral-100"
            onClick={() => setAiAssistEnabled(!aiAssistEnabled)}
            title={aiAssistEnabled ? "Disable AI Assist" : "Enable AI Assist"}
          >
            {aiAssistEnabled ? (
              <ToggleRight className="h-4 w-4 text-primary" />
            ) : (
              <ToggleLeft className="h-4 w-4 text-muted-foreground" />
            )}
          </button>
          {aiAssistEnabled && (
            <button className="h-8 gap-1.5 text-xs shrink-0 px-2 rounded flex items-center hover:bg-neutral-100 text-primary">
              <Sparkles className="w-3.5 h-3.5" />
              AI Assist
            </button>
          )}

          <ClinicalReferences />

          <button
            className="h-7 w-7 shrink-0 flex items-center justify-center rounded hover:bg-neutral-100"
            onClick={() => setCdsEnabled(!cdsEnabled)}
            title={
              cdsEnabled
                ? "Disable Clinical Decision Support"
                : "Enable Clinical Decision Support"
            }
          >
            {cdsEnabled ? (
              <ToggleRight className="h-4 w-4 text-primary" />
            ) : (
              <ToggleLeft className="h-4 w-4 text-muted-foreground" />
            )}
          </button>

          <div className="flex-1" />

          <div className="h-4 w-px bg-neutral-100 mx-0.5 shrink-0" />
          <button className="h-8 gap-1.5 text-xs shrink-0 px-2 rounded flex items-center hover:bg-neutral-100">
            <Users className="w-3.5 h-3.5" />
            Directory
          </button>
        </div>
      </div>

      {cdsEnabled && <ActiveCDSBanner hasActivePatient={hasActivePatient} />}
    </div>
  );
}
