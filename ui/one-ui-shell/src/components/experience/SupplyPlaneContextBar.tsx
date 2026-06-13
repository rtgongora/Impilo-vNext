"use client";

/**
 * Supply Plane — inventory, stock operations, and asset continuity (Experience).
 */

import Link from "next/link";
import { Package } from "lucide-react";

export function SupplyPlaneContextBar() {
  return (
    <div className="mb-4 rounded-xl border border-teal-300/90 bg-gradient-to-r from-teal-50 to-emerald-50/60 px-4 py-3 text-sm text-teal-950 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-start gap-2">
          <Package className="mt-0.5 h-4 w-4 shrink-0 text-teal-800" aria-hidden />
          <div>
            <p className="font-semibold text-teal-950">Supply Plane</p>
            <p className="text-xs text-teal-900/85">
              Catalog, on-hand stock, movements (FEFO), requisitions, counts, and fixed assets — scoped to your selected
              facility where applicable.
            </p>
          </div>
        </div>
        <Link
          href="/operations"
          className="shrink-0 rounded-lg border border-teal-400/80 bg-card/80 px-3 py-1.5 text-xs font-medium text-teal-950 hover:bg-card"
        >
          Operations hub
        </Link>
      </div>
    </div>
  );
}
