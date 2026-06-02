"use client";

import { AlertTriangle, X } from "lucide-react";
import { useBackendPendingToast } from "@/hooks/useBackendPendingToast";

export function BackendPendingToastHost() {
  const { visible, route, label, dismiss } = useBackendPendingToast();

  if (!visible) return null;

  return (
    <div className="fixed bottom-4 right-4 z-[200] max-w-md pointer-events-none">
      <div
        role="status"
        className="pointer-events-auto rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 shadow-lg"
      >
        <div className="flex items-start gap-3">
          <AlertTriangle className="h-5 w-5 shrink-0 text-amber-600 mt-0.5" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-amber-950">Backend API pending</p>
            {label && <p className="text-xs text-amber-900 mt-0.5">{label}</p>}
            <p className="text-xs font-mono text-amber-800 mt-1 break-all">{route}</p>
            <p className="text-[11px] text-amber-700 mt-1">
              Route to be added on the Experience BFF. Existing Experience APIs are unchanged.
            </p>
          </div>
          <button
            type="button"
            onClick={dismiss}
            className="text-amber-500 hover:text-amber-800 shrink-0"
            aria-label="Dismiss"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
