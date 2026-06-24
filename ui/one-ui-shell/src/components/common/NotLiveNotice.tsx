"use client";

import { AlertTriangle } from "lucide-react";
import type { ReactNode } from "react";

/**
 * Honest, prominent marker for a section whose figures are illustrative fixtures,
 * not data from a backend service. Use on KPI tiles / tabs that have no live path
 * yet, so demo numbers are never mistaken for real operational data.
 */
export function NotLiveNotice({
  children,
  className = "",
}: {
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div
      role="note"
      className={`flex items-start gap-2 rounded-md border border-warning/35 bg-amber-50 px-3 py-2 text-xs text-warning-foreground ${className}`}
    >
      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
      <span>
        {children ?? (
          <>
            <span className="font-semibold">Not live yet.</span> Illustrative demo
            figures — no backend service supplies this section.
          </>
        )}
      </span>
    </div>
  );
}
