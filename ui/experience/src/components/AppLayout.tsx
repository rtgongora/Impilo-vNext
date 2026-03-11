"use client";

/**
 * AppLayout — Standard application layout with 3-zone sidebar navigation.
 * Layout variant: "app" (used by most non-EHR routes)
 *
 * Structure:
 *   [Sidebar (3-zone nav)] [Main Content Area] [Header Bar]
 */

import { type ReactNode } from "react";
import { ZoneNavigation } from "./ZoneNavigation";

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen bg-gray-50">
      <ZoneNavigation />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-14 border-b bg-white px-6 flex items-center justify-between shrink-0">
          <div className="text-sm text-gray-500">Impilo vNext</div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-600">Experience Platform</span>
          </div>
        </header>
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  );
}
