"use client";

import type { AgeBand } from "@/lib/clinical-forms/types";
import { evaluateNumericVital } from "@/lib/clinical-forms/vitals-reference/evaluate";
import type { VitalId } from "@/lib/clinical-forms/vitals-reference/metadata";
import { InterpretedVitalsPanel } from "./InterpretedVitalsPanel";
import type { VitalValues } from "@/lib/clinical/vitals-to-observations";

export interface EncounterVitalsGuidanceProps {
  ageBand: AgeBand;
  systolic?: string;
  diastolic?: string;
  heartRate?: string;
  temperature?: string;
  respiratoryRate?: string;
  oxygenSat?: string;
  painScore?: string;
  /** When supplied, also renders governed, auditable server-side interpreted flags. */
  patientId?: string;
  ageYears?: number;
  sex?: string;
  encounterId?: string;
}

function toNum(s: string | undefined): number | null {
  if (s == null || s.trim() === "") return null;
  const n = Number(s);
  return Number.isNaN(n) ? null : n;
}

export function EncounterVitalsGuidance(props: EncounterVitalsGuidanceProps) {
  const { ageBand, systolic, diastolic, heartRate, temperature, respiratoryRate, oxygenSat, painScore } = props;
  const { patientId, ageYears, sex, encounterId } = props;
  const pairs: Array<{ id: VitalId; val: number | null }> = [
    { id: "systolic_bp", val: toNum(systolic) },
    { id: "diastolic_bp", val: toNum(diastolic) },
    { id: "heart_rate", val: toNum(heartRate) },
    { id: "temperature_c", val: toNum(temperature) },
    { id: "respiratory_rate", val: toNum(respiratoryRate) },
    { id: "spo2", val: toNum(oxygenSat) },
    { id: "pain_score", val: toNum(painScore) },
  ];
  const msgs = pairs.flatMap((p) => evaluateNumericVital(p.id, p.val, ageBand)).filter((m) => m.code !== "WITHIN_NORMAL");

  const vitals: VitalValues = Object.fromEntries(
    pairs.filter((p) => p.val != null).map((p) => [p.id, p.val]),
  ) as VitalValues;
  const showServerPanel = Boolean(patientId) && Object.keys(vitals).length > 0;

  if (!msgs.length && !showServerPanel) return null;

  return (
    <>
      {msgs.length > 0 && (
        <div className="mt-2 space-y-1 rounded-lg border border-warning/35 bg-warning-soft/80 p-2 text-xs text-warning-foreground">
          <p className="font-medium text-warning-foreground">Vitals reference checks</p>
          <ul className="list-disc pl-4 space-y-0.5">
            {msgs.map((m, i) => (
              <li key={`${m.vitalId}-${i}`}>{m.message}</li>
            ))}
          </ul>
          {msgs.some((m) => m.requiresOverrideReason) && (
            <p className="text-[10px] text-warning-foreground/90 mt-1">
              Implausible values require documented override before save (wire to save path in a follow-up).
            </p>
          )}
        </div>
      )}
      {showServerPanel && (
        <InterpretedVitalsPanel
          patientId={patientId as string}
          vitals={vitals}
          ageYears={ageYears}
          sex={sex}
          encounterId={encounterId}
        />
      )}
    </>
  );
}
