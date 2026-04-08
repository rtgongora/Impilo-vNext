"use client";

/**
 * EHRLayout — Electronic Health Record layout with TopBar + PatientBanner + EncounterMenu.
 * Layout variant: "ehr" (used by /ehr/* routes)
 *
 * Structure (Lovable-aligned):
 *   [TopBar (operational actions, breadcrumbs)]
 *   [PatientBanner (persistent patient identity + encounter context)]
 *   [EncounterMenu (left or right)] [Main Content Area]
 *
 * Menu position persists in sessionStorage.
 */

import { useState, type ReactNode } from "react";
import { TopBar } from "./TopBar";
import { OperationalContextStrip } from "./experience/OperationalContextStrip";
import { PatientBanner } from "./PatientBanner";
import { EncounterMenu } from "./EncounterMenu";
import { PanelLeft, PanelRight } from "lucide-react";

export function EHRLayout({ children }: { children: ReactNode }) {
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
    <div className="flex flex-col h-screen bg-gray-50">
      <TopBar />
      <OperationalContextStrip />
      <PatientBanner />
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
        <main className="flex-1 overflow-auto p-4">{children}</main>
      </div>
    </div>
  );
}
