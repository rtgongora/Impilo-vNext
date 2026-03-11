"use client";

/**
 * AuthLayout — Minimal centered layout for authentication flows.
 * Layout variant: "auth" (used by /auth/* routes)
 */

import { type ReactNode } from "react";

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Impilo vNext</h1>
          <p className="text-sm text-gray-500 mt-1">Health Information Exchange</p>
        </div>
        <div className="bg-white rounded-xl shadow-lg p-8">{children}</div>
      </div>
    </div>
  );
}
