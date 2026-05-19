"use client";

/**
 * NdilaIntelligencePanel — surfaces Ndila Spatial Intelligence outputs for a
 * given area scope (ward / district / province / facility). Designed to host
 * the Nompilo-safe AI context packet alongside deterministic signals.
 *
 * Used by:
 *   - Public Health Operations dashboards
 *   - Data / Health Intelligence views
 *   - Tuso facility coordinate-quality drilldowns
 *   - Nhume/Dispatch delivery performance overlays
 */

import { useEffect, useState } from "react";
import { BrainCircuit, Loader2, ShieldCheck } from "lucide-react";
import { ndilaAiContextPacket, type NdilaAiContextPacket } from "@/lib/ndila/ndila-client";

interface Props {
  question: string;
  areaScope?: { level: "WARD"|"DISTRICT"|"PROVINCE"|"NATIONAL"|"FACILITY"|"CUSTOM"; id?: string };
  purposeOfUse?: string;
  enableNompiloSummary?: boolean;
  signals?: string[];
}

export function NdilaIntelligencePanel({
  question, areaScope, purposeOfUse = "OPERATIONS_MANAGEMENT",
  enableNompiloSummary = true, signals,
}: Props) {
  const [packet, setPacket] = useState<NdilaAiContextPacket | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(null);
    ndilaAiContextPacket({ question, areaScope, purposeOfUse, includeRecommendations: enableNompiloSummary })
      .then((p) => {
        if (cancelled) return;
        if (p.success === false) { setError(p.denialReason || "Tshepo denied"); return; }
        setPacket(p);
      })
      .catch(() => { if (!cancelled) setError("Ndila intelligence unavailable"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [question, areaScope?.level, areaScope?.id, purposeOfUse, enableNompiloSummary]);

  return (
    <div className="rounded-lg border border-indigo-200 bg-indigo-50/40 p-3">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2 text-sm text-indigo-900">
          <BrainCircuit className="h-4 w-4" />
          <span className="font-medium">Ndila Intelligence</span>
        </div>
        <span className="text-[10px] text-indigo-700 inline-flex items-center gap-1">
          <ShieldCheck className="h-3 w-3" /> Tshepo-guarded · PII-minimized
        </span>
      </div>
      <p className="text-[11px] text-indigo-900/80 mb-2">{question}</p>
      {loading ? (
        <div className="flex items-center gap-2 text-xs text-indigo-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Composing AI-safe context packet…
        </div>
      ) : error ? (
        <p className="text-xs text-red-700">{error}</p>
      ) : packet ? (
        <div className="space-y-2">
          {packet.summary && (
            <p className="text-xs text-gray-900 leading-relaxed">{packet.summary}</p>
          )}
          {packet.signals?.length ? (
            <ul className="text-[11px] text-gray-800 grid grid-cols-1 sm:grid-cols-2 gap-1">
              {packet.signals
                .filter((s) => !signals?.length || signals.includes(s.type))
                .map((s, i) => (
                <li key={i} className="rounded bg-white border border-indigo-100 px-2 py-1">
                  <span className="font-medium">{s.type}</span>{" "}
                  {s.count !== undefined ? <>· <span className="tabular-nums">{s.count}</span></> : null}
                  {s.severity ? ` · ${s.severity}` : ""}
                </li>
              ))}
            </ul>
          ) : null}
          {packet.recommendedActions?.length ? (
            <div className="text-[11px] text-gray-900">
              <div className="font-medium text-indigo-900 mb-0.5">Recommended actions</div>
              <ul className="list-disc pl-4 space-y-0.5">
                {packet.recommendedActions.map((a, i) => <li key={i}>{a}</li>)}
              </ul>
            </div>
          ) : null}
          {packet.restrictions?.length ? (
            <p className="text-[10px] text-gray-500">
              {packet.restrictions.join(" · ")}
            </p>
          ) : null}
          {packet.confidence !== undefined && (
            <p className="text-[10px] text-gray-500 tabular-nums">
              Ndila confidence: {(packet.confidence * 100).toFixed(0)}%
            </p>
          )}
        </div>
      ) : null}
    </div>
  );
}
