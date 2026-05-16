"use client";

/**
 * NdilaSpatialInsightCard — small card that renders a single deterministic
 * Ndila intelligence signal (risk cluster, coordinate-quality issue,
 * inspection gap, etc.). Used inside dashboards and Nompilo summaries.
 */

import { AlertTriangle, Compass, MapPinned, ShieldAlert } from "lucide-react";

interface Signal {
  type: string;
  count?: number;
  severity?: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  trend?: string;
  proximity?: string;
  description?: string;
}

interface Props {
  signal: Signal;
  areaLabel?: string;
}

function iconFor(type: string) {
  if (type.includes("RISK")) return AlertTriangle;
  if (type.includes("COORDINATE")) return Compass;
  if (type.includes("GAP")) return MapPinned;
  return ShieldAlert;
}

const severityClass: Record<string, string> = {
  LOW:      "bg-emerald-50 text-emerald-900 border-emerald-200",
  MEDIUM:   "bg-amber-50 text-amber-900 border-amber-200",
  HIGH:     "bg-orange-50 text-orange-900 border-orange-200",
  CRITICAL: "bg-red-50 text-red-900 border-red-200",
};

export function NdilaSpatialInsightCard({ signal, areaLabel }: Props) {
  const Icon = iconFor(signal.type);
  const sev = severityClass[signal.severity ?? "LOW"] ?? severityClass.LOW;
  return (
    <div className={`rounded-md border p-2 ${sev}`}>
      <div className="flex items-center gap-2 text-xs font-medium">
        <Icon className="h-3.5 w-3.5" />
        <span>{signal.type.replaceAll("_", " ")}</span>
        {signal.count !== undefined && (
          <span className="tabular-nums ml-auto">{signal.count}</span>
        )}
      </div>
      {areaLabel && <p className="text-[10px] mt-0.5 opacity-75">{areaLabel}</p>}
      {signal.description && <p className="text-[11px] mt-0.5">{signal.description}</p>}
      {(signal.trend || signal.proximity) && (
        <p className="text-[10px] mt-0.5 opacity-75">
          {[signal.trend, signal.proximity].filter(Boolean).join(" · ")}
        </p>
      )}
    </div>
  );
}
