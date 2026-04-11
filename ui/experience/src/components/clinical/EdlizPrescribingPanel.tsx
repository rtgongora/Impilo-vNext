"use client";

/**
 * National EDLIZ-aligned prescribing check — calls BFF → clinical-knowledge-platform
 * with `record_trace` for audit. Shown on active encounter for prescribers.
 */

import { useState, useMemo } from "react";
import { useClinicalPrescribingEvaluate } from "@/hooks/queries/useGuidance";
import { Pill, Loader2, ShieldAlert } from "lucide-react";

type Props = {
  patientId: string;
  encounterId: string;
  actorId: string;
  role?: string;
  diagnoses: string[];
  activeMedicationGenerics: string[];
};

function readTenantId(): string {
  if (typeof window === "undefined") return "tenant-moh-zw";
  return sessionStorage.getItem("exp:tenant_id") || "tenant-moh-zw";
}

export function EdlizPrescribingPanel({
  patientId,
  encounterId,
  actorId,
  role = "CLINICIAN",
  diagnoses,
  activeMedicationGenerics,
}: Props) {
  const [generic, setGeneric] = useState("");
  const [doseMg, setDoseMg] = useState("");
  const [days, setDays] = useState("");
  const evalRx = useClinicalPrescribingEvaluate();

  const mergedDiagnoses = useMemo(() => {
    const s = new Set(diagnoses.map((d) => d.toUpperCase().replace(/\s+/g, "_")));
    return Array.from(s);
  }, [diagnoses]);

  const activeLines = useMemo(
    () =>
      activeMedicationGenerics.map((g) => ({
        genericName: g.toLowerCase(),
        route: "ORAL",
        daysOnTherapy: days ? Number.parseInt(days, 10) || 0 : 0,
      })),
    [activeMedicationGenerics, days]
  );

  const runCheck = () => {
    const proposed =
      generic.trim().length > 0
        ? [
            {
              genericName: generic.trim().toLowerCase(),
              doseMg: doseMg ? Number.parseFloat(doseMg) : undefined,
              route: "ORAL",
              daysOnTherapy: days ? Number.parseInt(days, 10) : undefined,
            },
          ]
        : [];

    evalRx.mutate({
      tenant_id: readTenantId(),
      actor_id: actorId,
      role,
      patient_id: patientId,
      encounter_id: encounterId,
      record_trace: true,
      diagnoses: mergedDiagnoses,
      activeMedications: activeLines,
      proposedMedications: proposed,
      facility_level: "C",
    });
  };

  const data = evalRx.data?.data as Record<string, unknown> | undefined;
  const alerts = (data?.alerts as Array<Record<string, unknown>> | undefined) ?? [];

  return (
    <div className="rounded-lg border border-emerald-200 bg-emerald-50/40 p-4">
      <div className="flex items-center gap-2 text-sm font-medium text-emerald-900">
        <Pill className="h-4 w-4" />
        EDLIZ prescribing check
      </div>
      <p className="mt-1 text-xs text-emerald-800/90">
        Evaluates national demo guidance + deterministic rules. Uses audited trace when you run a check.
      </p>
      <div className="mt-3 grid gap-2 sm:grid-cols-3">
        <input
          className="rounded border border-emerald-200 bg-white px-2 py-1.5 text-sm"
          placeholder="Proposed generic (e.g. ceftriaxone)"
          value={generic}
          onChange={(e) => setGeneric(e.target.value)}
        />
        <input
          className="rounded border border-emerald-200 bg-white px-2 py-1.5 text-sm"
          placeholder="Dose mg (optional)"
          value={doseMg}
          onChange={(e) => setDoseMg(e.target.value)}
        />
        <input
          className="rounded border border-emerald-200 bg-white px-2 py-1.5 text-sm"
          placeholder="Days on therapy (optional)"
          value={days}
          onChange={(e) => setDays(e.target.value)}
        />
      </div>
      <button
        type="button"
        onClick={runCheck}
        disabled={evalRx.isPending}
        className="mt-3 inline-flex items-center gap-2 rounded-md bg-emerald-700 px-3 py-1.5 text-sm text-white hover:bg-emerald-800 disabled:opacity-50"
      >
        {evalRx.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Run check
      </button>
      {alerts.length > 0 && (
        <ul className="mt-3 space-y-2 text-sm text-amber-900">
          {alerts.map((a, i) => (
            <li key={i} className="flex gap-2 rounded border border-amber-200 bg-amber-50/80 p-2">
              <ShieldAlert className="h-4 w-4 shrink-0 text-amber-700" />
              <span>{String(a.message ?? a.code ?? "Alert")}</span>
            </li>
          ))}
        </ul>
      )}
      {evalRx.isError && <p className="mt-2 text-xs text-red-600">Request failed — check BFF and clinical service.</p>}
    </div>
  );
}
