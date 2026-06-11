"use client";

/**
 * EHRLayout — Electronic Health Record layout with TopBar + PatientBanner + EncounterMenu.
 * Layout variant: "ehr" (used by /ehr/* routes)
 *
 * Structure (Lovable-aligned):
 *   [TopBar (operational actions, breadcrumbs)]
 *   [PatientBanner (persistent patient identity + encounter context)]
 *   [ClinicalToolbar — pathways, EDLIZ, CDS, tools]
 *   [EncounterMenu (left or right)] [Main Content Area + ClinicalKnowledgeDock]
 *
 * Menu position persists in sessionStorage.
 */

import { useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { TopBar } from "./TopBar";
import { OperationalContextStrip } from "./experience/OperationalContextStrip";
import { PatientBanner } from "./PatientBanner";
import { EncounterMenu } from "./EncounterMenu";
import { PanelLeft, PanelRight } from "lucide-react";
import { ClinicalGuidanceProvider } from "@/components/clinical/ClinicalGuidanceContext";
import { ClinicalToolbar } from "@/components/clinical/ClinicalToolbar";
import { ClinicalKnowledgeDock } from "@/components/clinical/ClinicalKnowledgeDock";

export function EHRLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isEhrShell = pathname.startsWith("/ehr");

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
    <ClinicalGuidanceProvider>
      <div className="flex h-dvh flex-col overflow-hidden bg-gray-50">
        <TopBar />
        <OperationalContextStrip />
        <PatientBanner />
        {isEhrShell && <ClinicalToolbar hasActivePatient />}
        <div className={`flex min-h-0 flex-1 overflow-hidden ${menuRight ? "flex-row-reverse" : "flex-row"}`}>
          <div className="relative flex min-h-0 w-52 shrink-0 flex-col overflow-hidden">
            <EncounterMenu />
            <button
              onClick={togglePosition}
              className="absolute top-2 right-2 z-10 rounded p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600"
              title={menuRight ? "Move menu to left" : "Move menu to right"}
            >
              {menuRight ? <PanelLeft className="w-3.5 h-3.5" /> : <PanelRight className="w-3.5 h-3.5" />}
            </button>
          </div>
          <main className="relative min-h-0 flex-1 overflow-y-auto overflow-x-hidden p-3 pb-[calc(var(--shell-taskbar-height,0px)+0.75rem)]">
            {children}
            <ClinicalKnowledgeDock />
          </main>
        </div>
      </div>
    </ClinicalGuidanceProvider>
  );
}
