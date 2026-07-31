"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useClerkingContinuityContext } from "./useClerkingContinuityContext";

type Stance = "STILL_TRUE" | "CHANGED" | "NOT_ASSESSED";

const SECTIONS: { key: string; label: string; summary: (ctx: ReturnType<typeof useClerkingContinuityContext>) => string }[] = [
  {
    key: "PROBLEMS",
    label: "Problems / PMH",
    summary: (ctx) =>
      ctx.unavailable.includes("problem list")
        ? "Problem list unavailable"
        : `${ctx.activeProblems.length} active problem(s)`,
  },
  {
    key: "ALLERGIES",
    label: "Allergies",
    summary: (ctx) =>
      ctx.unavailable.includes("allergies")
        ? "Allergies unavailable"
        : `${ctx.allergies.length} allergy row(s)`,
  },
  {
    key: "MEDICATIONS",
    label: "Medications",
    summary: (ctx) =>
      ctx.unavailable.includes("medications")
        ? "Medications unavailable"
        : `${ctx.medications.length} active medication(s)`,
  },
  {
    key: "IMMUNIZATIONS",
    label: "Immunizations",
    summary: (ctx) =>
      ctx.unavailable.includes("immunizations")
        ? "Immunizations unavailable"
        : `${ctx.immunizations.length} immunization(s)`,
  },
  {
    key: "FUNCTIONAL",
    label: "Functional status",
    summary: (ctx) =>
      ctx.unavailable.includes("functional status")
        ? "Functional status unavailable"
        : `${ctx.functional.length} assessment(s)`,
  },
  {
    key: "SOCIAL_HISTORY",
    label: "Social history",
    summary: () => "See social-history page for structured categories",
  },
  {
    key: "SYSTEMS_REVIEW",
    label: "Systems review",
    summary: () => "Last ROS from clerking extensions (if recorded)",
  },
  {
    key: "PRESENTING_CONCERN",
    label: "Presenting concern",
    summary: () => "Last presenting concern from clerking extensions (if recorded)",
  },
];

/**
 * Visit-scoped still-true / changed / not-assessed.
 * Never pre-selects STILL_TRUE — silence ≠ confirmation.
 */
export function ConditionalClerkingPanel({ patientId }: { patientId: string }) {
  const ctx = useClerkingContinuityContext(patientId);
  const encounters = useEncounters(patientId);
  const encounterId = (encounters.data?.data ?? []).find((e) => e.attributes.isOpen)?.id ?? "";
  const qc = useQueryClient();

  const attestations = useQuery({
    queryKey: ["clerking", "visit-attestations", patientId, encounterId],
    queryFn: () =>
      apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/clerking/visit-attestations?subject_cpid=${encodeURIComponent(patientId)}&encounter_id=${encodeURIComponent(encounterId)}`
      ),
    enabled: !!patientId && !!encounterId,
  });

  const attest = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/clerking/visit-attestations", {
        subject_cpid: patientId,
        encounter_id: encounterId,
        ...body,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["clerking", "visit-attestations", patientId, encounterId] });
    },
  });

  const bySection = useMemo(() => {
    const map = new Map<string, string>();
    for (const row of attestations.data?.data ?? []) {
      const key = String(row.section_key ?? "");
      if (key && !map.has(key)) map.set(key, String(row.stance ?? ""));
    }
    return map;
  }, [attestations.data]);

  const [draft, setDraft] = useState<Record<string, Stance | "">>({});

  if (!encounterId) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="conditional-clerking-needs-encounter">
        Open an encounter to attest still-true / changed / not-assessed for this visit.
      </p>
    );
  }

  return (
    <div className="space-y-3 rounded-lg border border-border bg-card p-4" data-testid="conditional-clerking-panel">
      <div>
        <h3 className="text-sm font-semibold">Do not re-document what is known</h3>
        <p className="text-xs text-muted-foreground mt-1">
          Each section requires an explicit stance. Nothing is pre-checked as still true — omitting a
          stance does not invent confirmation.
        </p>
      </div>

      {SECTIONS.map((section) => {
        const recorded = bySection.get(section.key);
        const collapsed = recorded === "STILL_TRUE";
        const value = draft[section.key] ?? "";
        return (
          <div
            key={section.key}
            className="rounded border border-border p-3 space-y-2"
            data-testid={`conditional-section-${section.key}`}
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-sm font-medium">{section.label}</p>
                <p className="text-xs text-muted-foreground">{section.summary(ctx)}</p>
              </div>
              {recorded && (
                <span className="text-xs text-muted-foreground">Attested: {recorded}</span>
              )}
            </div>
            {!collapsed && (
              <>
                <div className="flex flex-wrap gap-3 text-xs">
                  {(["STILL_TRUE", "CHANGED", "NOT_ASSESSED"] as Stance[]).map((stance) => (
                    <label key={stance} className="inline-flex items-center gap-1">
                      <input
                        type="radio"
                        name={`stance-${section.key}`}
                        value={stance}
                        checked={value === stance}
                        onChange={() => setDraft((d) => ({ ...d, [section.key]: stance }))}
                      />
                      {stance === "STILL_TRUE"
                        ? "Still true"
                        : stance === "CHANGED"
                          ? "Changed"
                          : "Not assessed this visit"}
                    </label>
                  ))}
                </div>
                <button
                  type="button"
                  disabled={!value || attest.isPending}
                  onClick={() => {
                    if (!value) return;
                    attest.mutate(
                      { section_key: section.key, stance: value },
                      { onSuccess: () => setDraft((d) => ({ ...d, [section.key]: "" })) }
                    );
                  }}
                  className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
                >
                  Record stance
                </button>
              </>
            )}
            {collapsed && (
              <p className="text-xs text-muted-foreground">
                Section collapsed after still-true attestation for this visit.
              </p>
            )}
          </div>
        );
      })}
    </div>
  );
}
