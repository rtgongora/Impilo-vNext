"use client";

/**
 * EHRLayout — Electronic Health Record layout with TopBar + PatientBanner + EncounterMenu.
 * Layout variant: "ehr" (used by /ehr/* routes)
 *
 * Structure (Lovable-aligned):
 *   [TopBar]
 *   [ClinicalSupportStrip — Comms Hub, Help, System Support]
 *   [OperationalContextStrip — operational mode]
 *   [PatientBanner]
 *   [ClinicalToolbar — pathways, EDLIZ, CDS, tools]
 *   [ClinicalWizardHeader — configurable encounter workflow]
 *   [EncounterMenu] [Main Content Area + ClinicalKnowledgeDock]
 *   Nompilo via ShellChrome taskbar (Ask / Ctrl+K), not floating page chrome.
 */

import { useState, type ReactNode, useMemo, useEffect } from "react";
import { useParams, usePathname } from "next/navigation";
import { TopBar } from "./TopBar";
import { OperationalContextStrip } from "./experience/OperationalContextStrip";
import { PatientBanner } from "./PatientBanner";
import { EncounterMenu } from "./EncounterMenu";
import { PanelLeft, PanelRight } from "lucide-react";
import { ClinicalGuidanceProvider } from "@/components/clinical/ClinicalGuidanceContext";
import { ClinicalToolbar } from "@/components/clinical/ClinicalToolbar";
import { ClinicalKnowledgeDock } from "@/components/clinical/ClinicalKnowledgeDock";
import { ClinicalSupportStrip } from "@/components/clinical/ClinicalSupportStrip";
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
        <div className="flex flex-col h-screen bg-gray-50">
          <TopBar />
          <ClinicalSupportStrip />
          <OperationalContextStrip />
          <PatientBanner />
          {isEhrShell && <ClinicalToolbar hasActivePatient />}
          {showEncounterWizard && (
            <ClinicalWizardHeader patientId={patientId} encounterId={encounterId} pathname={pathname} />
          )}
          <div className={`flex flex-1 min-h-0 ${menuRight ? "flex-row-reverse" : "flex-row"}`}>
            <div className="relative">
              <EncounterMenu />
              <button
                onClick={togglePosition}
                className="absolute top-2 right-2 p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded transition-colors z-10"
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
