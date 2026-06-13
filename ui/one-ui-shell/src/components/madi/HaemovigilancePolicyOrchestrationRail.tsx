"use client";

import Link from "next/link";
import { Globe2, ShieldAlert } from "lucide-react";

/** Documents national haemovigilance web-only policy at facility scope. */
export function HaemovigilancePolicyOrchestrationRail() {
  return (
    <section
      className="mb-6 rounded-xl border border-danger/28 bg-danger-soft/60 px-4 py-3 text-sm text-danger"
      data-testid="haemovigilance-policy-orchestration-rail"
    >
      <div className="flex items-start gap-3">
        <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-danger" />
        <div className="space-y-2">
          <p className="font-medium text-danger">Facility haemovigilance scope</p>
          <p className="text-xs text-rose-800">
            Facility operators report transfusion reactions here. National aggregation, cross-facility case review,
            and regulator dashboards remain web-only at the national route — mobile parity is intentionally not in
            scope for sovereign haemovigilance oversight.
          </p>
          <Link
            href="/madi/haemovigilance/national"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-danger hover:text-danger"
          >
            <Globe2 className="h-3.5 w-3.5" />
            Open national haemovigilance (ADMIN)
          </Link>
        </div>
      </div>
    </section>
  );
}
