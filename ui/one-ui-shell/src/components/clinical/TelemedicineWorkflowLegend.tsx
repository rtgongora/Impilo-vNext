"use client";

import { TELEMEDICINE_WORKFLOW_STAGES } from "@/lib/clinical/telemedicine-workflow-stages";

/** Read-only legend for hub / education surfaces (no fabricated session state). */
export function TelemedicineWorkflowLegend() {
  return (
    <div className="mb-6 rounded-2xl border border-border bg-card p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Seven-stage telemedicine workflow</p>
      <ol className="mt-3 flex flex-wrap gap-2">
        {TELEMEDICINE_WORKFLOW_STAGES.map((s) => (
          <li
            key={s.id}
            className="rounded-full border border-border bg-background px-2.5 py-1 text-[11px] font-medium text-foreground"
          >
            <span className="mr-1 font-mono text-[10px] text-muted-foreground">{s.id}</span>
            {s.label}
          </li>
        ))}
      </ol>
      <p className="mt-2 text-[11px] text-muted-foreground">
        Session rows below map into these stages using BFF stage numbers when present, otherwise telemedicine status.
      </p>
    </div>
  );
}
