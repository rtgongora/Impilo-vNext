"use client";

/**
 * EHRLayout — Electronic Health Record layout with TopBar + PatientBanner + EncounterMenu.
 * Layout variant: "ehr" (used by /ehr/* routes)
 *
 * Structure (Lovable-aligned):
 *   [TopBar]
 *   [PatientBanner]
 *   [ClinicalToolbar — pathways, EDLIZ, CDS, tools]
 *   [ClinicalWizardHeader — configurable encounter workflow]
 *   [EncounterMenu] [Main Content Area + ClinicalKnowledgeDock]
 *   Nompilo via ShellChrome taskbar (Ask / Ctrl+K), not floating page chrome.
 */

import { useState, type ReactNode, useMemo, useEffect } from "react";
import { useParams, usePathname } from "next/navigation";
import { TopBar } from "./TopBar";
import { PatientBanner } from "./PatientBanner";
import { EncounterMenu } from "./EncounterMenu";
import { PanelLeft, PanelRight } from "lucide-react";
import { ClinicalGuidanceProvider } from "@/components/clinical/ClinicalGuidanceContext";
import { ClinicalToolbar } from "@/components/clinical/ClinicalToolbar";
import type { CDSGuidanceItem } from "@/components/clinical/ActiveCDSBanner";
import { useClinicalCdsAlerts, useCdsInsight, type CdsAlert } from "@/hooks/queries/useClinicalCds";
import { ClinicalKnowledgeDock } from "@/components/clinical/ClinicalKnowledgeDock";
import { ClinicalWizardHeader } from "@/components/clinical/ClinicalWizardHeader";
import { ClinicalWorkflowProvider, type ClinicalWorkflowConfig } from "@/components/clinical/ClinicalWorkflowContext";
import { useEncounters } from "@/hooks/queries/useEncounters";
import {
  DEFAULT_CLINICAL_WIZARD_STEPS,
  type ClinicalWorkflowStepId,
  type WizardStepModel,
} from "@/lib/clinical/encounter-workspace-nav";

const WIZARD_SESSION_KEY = "exp:clinical-wizard-workflow";

function parseSessionWorkflow(raw: string | null): ClinicalWorkflowConfig | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as { steps?: unknown };
    if (!parsed?.steps || !Array.isArray(parsed.steps)) return null;
    const allowed = new Set<ClinicalWorkflowStepId>(DEFAULT_CLINICAL_WIZARD_STEPS.map((s) => s.id));
    const steps: WizardStepModel[] = [];
    for (const entry of parsed.steps) {
      if (!entry || typeof entry !== "object") continue;
      const id = (entry as { id?: unknown }).id;
      const label = (entry as { label?: unknown }).label;
      if (typeof id !== "string" || typeof label !== "string") continue;
      if (!allowed.has(id as ClinicalWorkflowStepId)) continue;
      steps.push({ id: id as ClinicalWorkflowStepId, label });
    }
    return steps.length ? { steps } : null;
  } catch {
    return null;
  }
}

/** Map a deterministic rules-engine alert to the toolbar banner's display item. No fabrication. */
function mapCdsAlert(a: CdsAlert): CDSGuidanceItem {
  const sev = (a.severity ?? "").toUpperCase();
  const severity: CDSGuidanceItem["severity"] =
    sev === "CRITICAL" ? "critical" : sev === "HIGH" ? "high" : sev === "MEDIUM" ? "moderate" : "info";
  const type: CDSGuidanceItem["type"] =
    severity === "critical" || (severity === "high" && a.interruptive) ? "alert" : severity === "info" ? "reminder" : "recommendation";
  const title = (a.code ?? "Clinical alert")
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
  return {
    id: a.code,
    type,
    severity,
    title,
    message: a.message,
    source: "Clinical rules engine",
    timestamp: new Date(),
    dismissed: false,
  };
}

export function EHRLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname() ?? "";
  const params = useParams();
  const patientId = params?.patientId as string | undefined;
  const encounterFromRoute = params?.encounterId as string | undefined;
  const isEhrShell = pathname.startsWith("/ehr");

  const { data: encountersData } = useEncounters(patientId ?? "");
  const activeEncounter = useMemo(
    () =>
      (encountersData?.data ?? []).find(
        (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS",
      ),
    [encountersData?.data],
  );
  const encounterId = encounterFromRoute ?? activeEncounter?.id ?? null;
  const showEncounterWizard = isEhrShell && !!patientId;

  // Real, patient-specific CDS alerts from the governed rules engine (empty when nothing triggers).
  const { alerts: cdsRawAlerts, isError: cdsUnavailable } =
    useClinicalCdsAlerts(isEhrShell ? patientId : undefined);
  // Grounded, fail-closed AI insight over those deterministic alerts (absent when LLM unavailable).
  const { insight: cdsInsight } = useCdsInsight(isEhrShell ? patientId : undefined, cdsRawAlerts, encounterId ?? undefined);
  const cdsAlerts = useMemo(() => {
    const items = cdsRawAlerts.map(mapCdsAlert);
    if (cdsUnavailable) {
      // An empty alert rail is read as "nothing triggered". When the rules engine or its source
      // records could not be read, say so on the rail rather than presenting a silent all-clear.
      items.push({
        id: "cds-unavailable",
        type: "alert",
        severity: "high",
        title: "Decision support unavailable",
        message:
          "Clinical alerts could not be evaluated. Do not read the absence of alerts here as an all-clear.",
        source: "Clinical decision support",
        timestamp: new Date(),
        dismissed: false,
      });
    }
    if (cdsInsight?.summary) {
      // Advisory only (info) — never outranks a deterministic alert; clearly AI-attributed.
      items.push({
        id: "ai-insight",
        type: "ai-insight",
        severity: "info",
        title: "AI summary",
        message: cdsInsight.summary,
        source: "AI advisory · governed reasoner",
        timestamp: new Date(),
        dismissed: false,
      });
    }
    return items;
  }, [cdsRawAlerts, cdsInsight, cdsUnavailable]);

  const [programmeWorkflow, setProgrammeWorkflow] = useState<ClinicalWorkflowConfig | null>(null);
  useEffect(() => {
    if (typeof window === "undefined") return;
    function refresh() {
      setProgrammeWorkflow(parseSessionWorkflow(sessionStorage.getItem(WIZARD_SESSION_KEY)));
    }
    refresh();
    function onStorage(e: StorageEvent) {
      if (e.key === WIZARD_SESSION_KEY || e.key === null) refresh();
    }
    function onCustom() {
      refresh();
    }
    window.addEventListener("storage", onStorage);
    window.addEventListener("impilo:clinical-wizard-workflow-changed", onCustom as EventListener);
    return () => {
      window.removeEventListener("storage", onStorage);
      window.removeEventListener("impilo:clinical-wizard-workflow-changed", onCustom as EventListener);
    };
  }, [pathname, patientId]);

  const [menuRight, setMenuRight] = useState(() => {
    if (typeof window === "undefined") return false;
    return sessionStorage.getItem("exp:ehr-menu-right") === "true";
  });

  function togglePosition() {
    const next = !menuRight;
    setMenuRight(next);
    sessionStorage.setItem("exp:ehr-menu-right", String(next));
  }

  return (
    <ClinicalWorkflowProvider value={programmeWorkflow}>
      <ClinicalGuidanceProvider>
        <div className="flex flex-col h-screen bg-background">
          <TopBar />
          <PatientBanner />
          {isEhrShell && <ClinicalToolbar hasActivePatient cdsAlerts={cdsAlerts} />}
          {showEncounterWizard && (
            <ClinicalWizardHeader patientId={patientId} encounterId={encounterId} pathname={pathname} />
          )}
          <div className={`flex flex-1 min-h-0 ${menuRight ? "flex-row-reverse" : "flex-row"}`}>
            <div className="relative">
              <EncounterMenu />
              <button
                onClick={togglePosition}
                className="absolute top-2 right-2 p-1 text-muted-foreground hover:text-muted-foreground hover:bg-neutral-100 rounded transition-colors z-10"
                title={menuRight ? "Move menu to left" : "Move menu to right"}
              >
                {menuRight ? <PanelLeft className="w-3.5 h-3.5" /> : <PanelRight className="w-3.5 h-3.5" />}
              </button>
            </div>
            <main className="relative flex-1 overflow-auto p-3 pb-[var(--shell-taskbar-height,0px)]">
              {children}
              <ClinicalKnowledgeDock />
            </main>
          </div>
        </div>
      </ClinicalGuidanceProvider>
    </ClinicalWorkflowProvider>
  );
}
