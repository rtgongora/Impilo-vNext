"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, Droplets, HelpCircle, Loader2 } from "lucide-react";
import { usePatientEgfr } from "@/hooks/queries/usePatientEgfr";
import {
  RENAL_DOSING_DRUG_CLASSES,
  useRenalDosingAdvise,
  type RenalDosingLevel,
} from "@/hooks/queries/useRenalDosing";

const LEVEL_STYLE: Record<RenalDosingLevel, string> = {
  ADJUST: "border-danger/40 bg-red-50 text-danger",
  CAUTION: "border-warning/40 bg-amber-50 text-warning-foreground",
  NO_CHANGE: "border-success/30 bg-green-50 text-green-800",
  UNKNOWN: "border-border bg-muted/30 text-muted-foreground",
};

export interface RenalDosingPanelProps {
  patientId: string;
}

/**
 * Renal dosing awareness panel (Wave 5.5).
 *
 * Uses patient eGFR from observations when present; otherwise states honestly that advice is
 * unavailable. Does not check interactions or formulary status.
 */
export function RenalDosingPanel({ patientId }: RenalDosingPanelProps) {
  const egfrState = usePatientEgfr(patientId);
  const advise = useRenalDosingAdvise();
  const [drugClass, setDrugClass] = useState<string>(RENAL_DOSING_DRUG_CLASSES[0]?.key ?? "BIGUANIDE");

  useEffect(() => {
    if (egfrState.isLoading || egfrState.egfr == null || !drugClass) {
      return;
    }
    advise.mutate({ egfr: egfrState.egfr, drugClass });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-advise when eGFR or class changes
  }, [egfrState.egfr, egfrState.isLoading, drugClass]);

  const advice = advise.data?.data;

  return (
    <section
      className="rounded-lg border border-border bg-card p-4 space-y-3"
      data-testid="renal-dosing-panel"
    >
      <div className="flex items-center gap-2">
        <Droplets className="w-4 h-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Renal dosing awareness</h3>
      </div>
      <p className="text-xs text-muted-foreground">
        Advisory bands only — not interaction checking or formulary lookup. Dose changes remain a
        clinical decision.
      </p>

      {egfrState.isLoading ? (
        <p className="text-sm text-muted-foreground flex items-center gap-2" data-testid="renal-dosing-egfr-loading">
          <Loader2 className="w-4 h-4 animate-spin" /> Checking renal function…
        </p>
      ) : egfrState.isError ? (
        <p className="text-sm text-danger flex items-start gap-2" data-testid="renal-dosing-egfr-error">
          <AlertTriangle className="w-4 h-4 mt-0.5" />
          Renal function could not be loaded — dosing advice unavailable.
        </p>
      ) : egfrState.egfr == null ? (
        <p className="text-sm text-muted-foreground flex items-start gap-2" data-testid="renal-dosing-egfr-unknown">
          <HelpCircle className="w-4 h-4 mt-0.5" />
          eGFR unknown — dosing advice unavailable.
        </p>
      ) : (
        <p className="text-sm text-foreground" data-testid="renal-dosing-egfr-value">
          Patient eGFR: <strong>{egfrState.egfr}</strong> mL/min/1.73m²
          {egfrState.source ? ` (${egfrState.source})` : null}
        </p>
      )}

      <div>
        <label htmlFor="renal-dosing-drug-class" className="block text-xs font-medium text-muted-foreground mb-1">
          Drug class
        </label>
        <select
          id="renal-dosing-drug-class"
          value={drugClass}
          onChange={(e) => setDrugClass(e.target.value)}
          disabled={egfrState.egfr == null}
          className="w-full max-w-md px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-50"
          data-testid="renal-dosing-drug-class"
        >
          {RENAL_DOSING_DRUG_CLASSES.map((c) => (
            <option key={c.key} value={c.key}>
              {c.label}
            </option>
          ))}
        </select>
      </div>

      {egfrState.egfr != null && advise.isPending && (
        <p className="text-sm text-muted-foreground flex items-center gap-2" data-testid="renal-dosing-advice-loading">
          <Loader2 className="w-4 h-4 animate-spin" /> Evaluating renal dosing band…
        </p>
      )}

      {advise.isError && (
        <p className="text-sm text-danger" data-testid="renal-dosing-advice-error">
          Renal dosing advice could not be loaded — do not assume no adjustment is needed.
        </p>
      )}

      {advice && (
        <div
          className={`rounded-lg border p-3 text-sm ${LEVEL_STYLE[advice.level] ?? LEVEL_STYLE.UNKNOWN}`}
          data-testid="renal-dosing-advice"
        >
          <p className="font-medium" data-testid="renal-dosing-advice-level">
            {advice.level.replace("_", " ")}
          </p>
          <p className="mt-1">{advice.message}</p>
        </div>
      )}
    </section>
  );
}
