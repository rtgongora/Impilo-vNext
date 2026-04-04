"use client";

/**
 * EHRLayout — Electronic Health Record layout with TopBar + PatientBanner + EncounterMenu.
 * Layout variant: "ehr" (used by /ehr/* routes)
 *
 * Structure (Lovable-aligned):
 *   [TopBar (operational actions, breadcrumbs)]
 *   [PatientBanner (persistent patient identity + encounter context)]
 *   [EncounterMenu (sidebar)] [Main Content Area]
 */

import { type ReactNode } from "react";
import { TopBar } from "./TopBar";
import { PatientBanner } from "./PatientBanner";
import { EncounterMenu } from "./EncounterMenu";

export function EHRLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col h-screen bg-gray-50">
      <TopBar />
      <PatientBanner />
      <div className="flex flex-1 min-h-0">
        <EncounterMenu />
        <main className="flex-1 overflow-auto p-4">{children}</main>
      </div>
    </div>
  );
}
