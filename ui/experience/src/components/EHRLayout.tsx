"use client";

/**
 * EHRLayout — Electronic Health Record layout with patient context bar + encounter menu.
 * Layout variant: "ehr" (used by /ehr/* routes)
 */

import { type ReactNode } from "react";

export function EHRLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen bg-gray-50">
      <nav className="w-16 bg-gray-900 text-white flex flex-col items-center py-4 gap-3 shrink-0">
        <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-xs font-bold">
          EHR
        </div>
      </nav>
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-12 border-b bg-white px-4 flex items-center gap-4 shrink-0">
          <span className="text-sm font-medium text-gray-700">Patient Chart</span>
          <div className="flex-1" />
          <span className="text-xs text-gray-500">EHR View</span>
        </header>
        <main className="flex-1 overflow-auto p-4">{children}</main>
      </div>
    </div>
  );
}
