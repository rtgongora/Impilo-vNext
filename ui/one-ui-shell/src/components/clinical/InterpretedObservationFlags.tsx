"use client";

import React from "react";
import { AlertTriangle, TrendingUp, TrendingDown, Minus, Info } from "lucide-react";
import type { InterpretedObservationDto } from "@/hooks/queries/useGuidance";

/* interpretation → presentation */
const FLAG_CONFIG: Record<string, { label: string; cls: string }> = {
  NORMAL: { label: "Normal", cls: "bg-success-soft text-primary-hover border border-success/25" },
  LOW: { label: "Low", cls: "bg-primary-soft text-primary border border-primary/25" },
  HIGH: { label: "High", cls: "bg-warning-soft text-warning-foreground border border-warning/35" },
  CRITICAL_LOW: { label: "Critical low", cls: "bg-danger-soft text-danger border border-danger/28" },
  CRITICAL_HIGH: { label: "Critical high", cls: "bg-danger-soft text-danger border border-danger/28" },
  NO_REFERENCE_RANGE: { label: "No reference range", cls: "bg-neutral-100 text-muted-foreground border border-neutral-200" },
  UNIT_MISMATCH: { label: "Unit mismatch", cls: "bg-neutral-100 text-muted-foreground border border-neutral-200" },
  DATA_QUALITY: { label: "Data quality", cls: "bg-warning-soft text-warning-foreground border border-warning/35" },
};

function flagFor(code: string) {
  return FLAG_CONFIG[code] ?? { label: code, cls: "bg-neutral-100 text-muted-foreground border border-neutral-200" };
}

function rangeText(o: InterpretedObservationDto): string | null {
  const r = o.reference_range;
  if (!r) return null;
  const lo = r.low ?? "–";
  const hi = r.high ?? "–";
  return `Ref ${lo}–${hi}${r.unit ? " " + r.unit : ""}`;
}

function TrendIcon({ trend }: { trend: string | null }) {
  if (trend === "RISING") return <TrendingUp className="h-3.5 w-3.5" aria-label="rising" />;
  if (trend === "FALLING") return <TrendingDown className="h-3.5 w-3.5" aria-label="falling" />;
  if (trend === "STABLE") return <Minus className="h-3.5 w-3.5" aria-label="stable" />;
  return null;
}

export interface InterpretedObservationFlagsProps {
  observations: InterpretedObservationDto[];
  traceId?: string;
  /** Show observations interpreted NORMAL too (default false → only flags worth attention). */
  showNormal?: boolean;
}

/**
 * Renders context-aware interpreted flags (NORMAL/LOW/HIGH/CRITICAL + reference range + trend) produced
 * by the CDS interpretation engine. Advisory only — never overrides clinician judgement. Honest about
 * NO_REFERENCE_RANGE (explicitly not "normal").
 */
export function InterpretedObservationFlags({ observations, traceId, showNormal = false }: InterpretedObservationFlagsProps) {
  const shown = observations.filter((o) => showNormal || o.interpretation !== "NORMAL");
  if (!shown.length) return null;

  const hasCritical = shown.some((o) => o.critical);

  return (
    <div
      data-testid="interpreted-observation-flags"
      className="mt-2 space-y-1.5 rounded-lg border border-neutral-200 bg-white/70 p-2 text-xs"
    >
      <div className="flex items-center gap-1.5 font-medium text-foreground">
        {hasCritical ? (
          <AlertTriangle className="h-3.5 w-3.5 text-danger" />
        ) : (
          <Info className="h-3.5 w-3.5 text-muted-foreground" />
        )}
        <span>Interpreted results</span>
      </div>
      <ul className="space-y-1">
        {shown.map((o, i) => {
          const f = flagFor(o.interpretation);
          const ref = rangeText(o);
          return (
            <li key={`${o.loinc_code ?? o.display_name ?? "obs"}-${i}`} className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
              <span className="font-medium text-foreground">{o.display_name ?? o.loinc_code ?? "Result"}</span>
              {o.value != null && (
                <span className="text-muted-foreground">
                  {o.value}
                  {o.unit ? " " + o.unit : ""}
                </span>
              )}
              <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-semibold ${f.cls}`}>
                {f.label}
              </span>
              {ref && <span className="text-[10px] text-muted-foreground">{ref}</span>}
              <span className="inline-flex items-center text-muted-foreground">
                <TrendIcon trend={o.trend} />
              </span>
            </li>
          );
        })}
      </ul>
      <p className="text-[10px] text-muted-foreground">
        Advisory — does not override clinical judgement.
        {traceId ? ` Trace ${traceId.slice(0, 8)}.` : ""}
      </p>
    </div>
  );
}
