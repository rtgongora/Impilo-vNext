"use client";

import { TELEMEDICINE_WORKFLOW_STAGES } from "@/lib/clinical/telemedicine-workflow-stages";

/** Read-only legend for hub / education surfaces (no fabricated session state). */
export function TelemedicineWorkflowLegend() {
  return (
    <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Seven-stage telemedicine workflow</p>
      <ol className="mt-3 flex flex-wrap gap-2">
        {TELEMEDICINE_WORKFLOW_STAGES.map((s) => (
          <li
            key={s.id}
            className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-medium text-slate-700"
          >
            <span className="mr-1 font-mono text-[10px] text-slate-500">{s.id}</span>
            {s.label}
          </li>
        ))}
      </ol>
      <p className="mt-2 text-[11px] text-slate-500">
        Session rows below map into these stages using BFF stage numbers when present, otherwise telemedicine status.
      </p>
    </div>
  );
}
