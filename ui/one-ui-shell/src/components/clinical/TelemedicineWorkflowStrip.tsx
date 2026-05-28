"use client";

import { TELEMEDICINE_WORKFLOW_STAGES, resolveTelemedicineWorkflowIndex } from "@/lib/clinical/telemedicine-workflow-stages";

export function TelemedicineWorkflowStrip({
  status,
  bffStage,
  className = "",
}: {
  status?: string | null;
  bffStage?: number | null;
  className?: string;
}) {
  const current = resolveTelemedicineWorkflowIndex({ status, bffStage });

  return (
    <div className={`rounded-xl border border-slate-200 bg-slate-50/90 px-3 py-2 ${className}`}>
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Telemedicine workflow</p>
      <div className="mt-1.5 flex flex-wrap gap-1">
        {TELEMEDICINE_WORKFLOW_STAGES.map((step, idx) => {
          const done = idx < current;
          const active = idx === current;
          return (
            <span
              key={step.id}
              className={[
                "inline-flex max-w-[10.5rem] items-center rounded-full border px-2 py-0.5 text-[10px] font-medium leading-tight",
                active ? "border-impilo-400 bg-impilo-100 text-impilo-900"
                : done ? "border-emerald-200 bg-emerald-50 text-emerald-900"
                : "border-slate-200 bg-white text-slate-500",
              ].join(" ")}
              title={step.label}
            >
              <span className="mr-0.5 shrink-0 font-mono text-[9px] opacity-70">{step.id}</span>
              <span className="truncate">{step.label}</span>
            </span>
          );
        })}
      </div>
    </div>
  );
}
