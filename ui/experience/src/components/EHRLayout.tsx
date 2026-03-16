"use client";

/**
 * EHRLayout — Electronic Health Record layout with TopBar + EncounterMenu.
 * Layout variant: "ehr" (used by /ehr/* routes)
 *
 * Structure:
 *   [TopBar (operational actions, breadcrumbs)]
 *   [EncounterMenu (sidebar)] [Main Content Area]
 */

import { type ReactNode } from "react";
import { TopBar } from "./TopBar";
import { EncounterMenu } from "./EncounterMenu";

export function EHRLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col h-screen bg-gray-50">
      <TopBar />
      <div className="flex flex-1 min-h-0">
        <EncounterMenu />
        <main className="flex-1 overflow-auto p-4">{children}</main>
      </div>
    </div>
  );
}
