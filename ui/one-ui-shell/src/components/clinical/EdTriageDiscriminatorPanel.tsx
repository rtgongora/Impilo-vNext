"use client";

import { useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";

const BOOL_FIELDS = [
  { id: "requires_resuscitation", label: "Requires immediate life-saving intervention" },
  { id: "airway_compromise", label: "Airway compromise" },
  { id: "high_risk_situation", label: "High-risk situation" },
  { id: "altered_consciousness", label: "Altered consciousness / confusion" },
  { id: "severe_pain_distress", label: "Severe pain or distress" },
  { id: "vital_sign_danger", label: "Vital signs in danger zone" },
  { id: "haemorrhage_uncontrolled", label: "Uncontrolled haemorrhage (MTS)" },
] as const;

interface ScorePreview {
  esi?: { acuity: number; category: string; rationale: string };
  mts?: { acuity: number; category: string; rationale: string };
  recommended_acuity?: number;
  recommended_system?: string;
  acuity?: number;
  category?: string;
  rationale?: string;
}

interface Props {
  triageSystem: "ESI" | "MTS" | "IMPILO_5";
  painScore: number;
  discriminators: Record<string, boolean | number>;
  vitals: Record<string, number>;
  onDiscriminatorChange: (id: string, value: boolean | number) => void;
  onRecommendedAcuity?: (acuity: number, system: string) => void;
}

export function EdTriageDiscriminatorPanel({
  triageSystem,
  painScore,
  discriminators,
  vitals,
  onDiscriminatorChange,
  onRecommendedAcuity,
}: Props) {
  const [preview, setPreview] = useState<ScorePreview | null>(null);

  useEffect(() => {
    if (triageSystem === "IMPILO_5") {
      setPreview(null);
      return;
    }
    const body: Record<string, unknown> = {
      triageSystem,
      painScore,
      scoreBoth: true,
      discriminators: { ...discriminators, pain_score: painScore },
      vitals,
    };
    const timer = window.setTimeout(() => {
      void apiClient
        .post<{ data: Record<string, unknown> }>("/internal/v1/ed/triage/score", body)
        .then((res) => {
          const raw = res.data ?? {};
          const esi = raw.esi as Record<string, unknown> | undefined;
          const mts = raw.mts as Record<string, unknown> | undefined;
          const next: ScorePreview = {
            recommended_acuity: Number(raw.recommended_acuity ?? raw.acuity ?? 0) || undefined,
            recommended_system: String(raw.recommended_system ?? triageSystem),
            esi: esi
              ? {
                  acuity: Number(esi.acuity),
                  category: String(esi.category ?? ""),
                  rationale: String(esi.rationale ?? ""),
                }
              : undefined,
            mts: mts
              ? {
                  acuity: Number(mts.acuity),
                  category: String(mts.category ?? ""),
                  rationale: String(mts.rationale ?? ""),
                }
              : undefined,
            acuity: Number(raw.acuity) || undefined,
            category: raw.category ? String(raw.category) : undefined,
            rationale: raw.rationale ? String(raw.rationale) : undefined,
          };
          setPreview(next);
          const acuity = next.recommended_acuity ?? next.acuity;
          const system = next.recommended_system ?? triageSystem;
          if (acuity && onRecommendedAcuity) onRecommendedAcuity(acuity, system);
        })
        .catch(() => setPreview(null));
    }, 400);
    return () => window.clearTimeout(timer);
  }, [triageSystem, painScore, discriminators, vitals, onRecommendedAcuity]);

  if (triageSystem === "IMPILO_5") return null;

  return (
    <div className="rounded-lg border border-dashed border-gray-300 p-3 space-y-3">
      <h3 className="text-sm font-semibold">ESI / MTS discriminators</h3>
      <div className="grid gap-2 sm:grid-cols-2">
        {BOOL_FIELDS.map((f) => (
          <label key={f.id} className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(discriminators[f.id])}
              onChange={(e) => onDiscriminatorChange(f.id, e.target.checked)}
              className="mt-0.5"
            />
            <span>{f.label}</span>
          </label>
        ))}
        <label className="text-sm">
          Expected ED resources (0–5)
          <input
            type="number"
            min={0}
            max={5}
            value={Number(discriminators.resources_needed ?? 0)}
            onChange={(e) => onDiscriminatorChange("resources_needed", Number(e.target.value))}
            className="ml-2 w-16 rounded border px-2"
          />
        </label>
      </div>
      {preview && (
        <div className="text-xs bg-gray-50 rounded p-2 space-y-1">
          {preview.esi && (
            <p>
              <strong>ESI {preview.esi.acuity}</strong> ({preview.esi.category}) — {preview.esi.rationale}
            </p>
          )}
          {preview.mts && (
            <p>
              <strong>MTS {preview.mts.category}</strong> (acuity {preview.mts.acuity}) — {preview.mts.rationale}
            </p>
          )}
          {preview.recommended_acuity && (
            <p className="text-red-700 font-medium">
              Recommended: acuity {preview.recommended_acuity} via {preview.recommended_system}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
