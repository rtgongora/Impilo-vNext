"use client";

import { AlertTriangle, Loader2, Pill, Stethoscope, ShieldAlert, ClipboardList } from "lucide-react";
import { usePatientSummary } from "@/hooks/queries/useSummary";

/**
 * TM-B14/B5 — provider-facing patient context on the teleconsult console.
 *
 * Reuses the longitudinal `usePatientSummary` (BUTANO/HAPI via the Experience BFF, CPID-only)
 * that the console previously never surfaced. Renders a compact, safety-first snapshot:
 * allergies are visually prominent (red) because they are the highest-signal safety item in a
 * live consult, followed by active medications, active conditions, and a recent-encounters count.
 *
 * Honest states throughout — loading, error, and empty are each distinct and never fabricate
 * clinical content.
 */
export function TeleconsultPatientSummaryPanel({ patientId }: { patientId: string }) {
  const { data, isLoading, isError } = usePatientSummary(patientId);
  const summary = data?.data;

  const allergies = summary?.allergies ?? [];
  const isSevere = (crit?: string) => {
    const c = (crit ?? "").toUpperCase();
    return c === "HIGH" || c === "SEVERE";
  };
  const activeMeds = (summary?.medications ?? []).filter(
    (m) => (m.status ?? "").toLowerCase() !== "stopped" && (m.status ?? "").toLowerCase() !== "completed",
  );
  const activeConditions = (summary?.conditions ?? []).filter(
    (c) => (c.clinicalStatus ?? "").toLowerCase() !== "resolved" && (c.clinicalStatus ?? "").toLowerCase() !== "inactive",
  );
  const encounterCount = summary?.recentEncounters?.length ?? 0;

  return (
    <section
      className="rounded-xl border border-border bg-card p-3 shadow-sm"
      data-testid="teleconsult-patient-summary"
    >
      <div className="flex items-center gap-2">
        <ClipboardList className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Patient context</h3>
      </div>

      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground" data-testid="teleconsult-summary-loading">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading patient summary…
        </div>
      ) : isError ? (
        <p className="mt-3 text-sm text-red-600" data-testid="teleconsult-summary-error">
          Unable to load the patient summary. The shared record (BUTANO) may be unavailable.
        </p>
      ) : !summary ? (
        <p className="mt-3 text-sm text-muted-foreground" data-testid="teleconsult-summary-empty">
          No patient summary is available for this record.
        </p>
      ) : (
        <div className="mt-3 space-y-3">
          {/* Allergies — prominent (safety) */}
          <div
            className={`rounded-lg border p-2 ${
              allergies.length > 0 ? "border-red-300 bg-danger-soft" : "border-border bg-background"
            }`}
            data-testid="teleconsult-summary-allergies"
          >
            <div className="flex items-center gap-1.5">
              <ShieldAlert className={`h-4 w-4 ${allergies.length > 0 ? "text-red-600" : "text-green-600"}`} />
              <h4 className={`text-xs font-semibold uppercase tracking-wider ${allergies.length > 0 ? "text-red-700" : "text-muted-foreground"}`}>
                Allergies ({allergies.length})
              </h4>
            </div>
            {allergies.length === 0 ? (
              <p className="mt-1 text-xs text-green-700">No known drug allergies (NKDA)</p>
            ) : (
              <ul className="mt-1.5 space-y-1">
                {allergies.slice(0, 6).map((a, i) => (
                  <li key={`${a.substance}-${i}`} className="flex items-center justify-between gap-2 text-xs">
                    <span className="font-semibold text-red-800 flex items-center gap-1">
                      {isSevere(a.criticality) && <AlertTriangle className="h-3 w-3 text-red-600 shrink-0" />}
                      {a.substance || "Unknown substance"}
                    </span>
                    <span className={`shrink-0 capitalize ${isSevere(a.criticality) ? "font-semibold text-red-700" : "text-red-600/80"}`}>
                      {a.reaction || a.criticality || "—"}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Active medications */}
          <div data-testid="teleconsult-summary-medications">
            <div className="flex items-center gap-1.5 mb-1">
              <Pill className="h-3.5 w-3.5 text-green-600" />
              <h4 className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                Active medications ({activeMeds.length})
              </h4>
            </div>
            {activeMeds.length === 0 ? (
              <p className="text-xs text-muted-foreground">No active medications</p>
            ) : (
              <ul className="space-y-1">
                {activeMeds.slice(0, 5).map((m, i) => (
                  <li key={`${m.code}-${i}`} className="rounded bg-background px-2 py-1 text-xs">
                    <span className="font-medium text-foreground">{m.display || m.code}</span>
                    {m.dosage ? <span className="text-muted-foreground"> · {m.dosage}</span> : null}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Active conditions */}
          <div data-testid="teleconsult-summary-conditions">
            <div className="flex items-center gap-1.5 mb-1">
              <Stethoscope className="h-3.5 w-3.5 text-orange-500" />
              <h4 className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                Active conditions ({activeConditions.length})
              </h4>
            </div>
            {activeConditions.length === 0 ? (
              <p className="text-xs text-muted-foreground">No active conditions</p>
            ) : (
              <ul className="space-y-1">
                {activeConditions.slice(0, 5).map((c, i) => (
                  <li key={`${c.code}-${i}`} className="rounded bg-background px-2 py-1 text-xs font-medium text-foreground">
                    {c.display || c.code}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Recent-encounters count */}
          <div
            className="flex items-center justify-between rounded bg-background px-2 py-1.5 text-xs"
            data-testid="teleconsult-summary-encounters"
          >
            <span className="text-muted-foreground">Recent encounters</span>
            <span className="font-semibold text-foreground">{encounterCount}</span>
          </div>
        </div>
      )}
    </section>
  );
}
