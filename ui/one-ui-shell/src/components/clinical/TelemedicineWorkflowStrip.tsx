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
    <div className={`rounded-xl border border-border bg-background/90 px-3 py-2 ${className}`}>
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Telemedicine workflow</p>
      <div className="mt-1.5 flex flex-wrap gap-1">
        {TELEMEDICINE_WORKFLOW_STAGES.map((step, idx) => {
          const done = idx < current;
          const active = idx === current;
          return (
            <span
              key={step.id}
              className={[
                "inline-flex max-w-[10.5rem] items-center rounded-full border px-2 py-0.5 text-[10px] font-medium leading-tight",
                active ? "border-impilo-400 bg-primary-soft text-impilo-900"
                : done ? "border-success/25 bg-success-soft text-primary-hover"
                : "border-border bg-card text-muted-foreground",
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
