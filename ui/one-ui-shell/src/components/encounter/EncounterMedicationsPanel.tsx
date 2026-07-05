"use client";

/**
 * Encounter medications / prescribing-seam panel.
 *
 * Read-only medication and pharmacy status inside the encounter cockpit, backed by the EXISTING
 * BFF pharmacy endpoint (`GET /internal/v1/pharmacy/prescriptions?patient_id=`) — pharmacy-service
 * remains the source of truth. Prescribing FROM the cockpit rides the structured-forms seam:
 * PRESCRIBE forms are cadre-gated by the PCT Cadre Engine / FormScopeEngine and their
 * MEDICATION_REQUEST extractions route to OROS (→ pharmacy). This panel never fabricates
 * medication state — unreachable services and empty records are shown honestly.
 */

import Link from "next/link";
import { Loader2, Pill } from "lucide-react";
import { usePrescriptions } from "@/hooks/queries/usePharmacy";

interface EncounterMedicationsPanelProps {
  patientId: string;
  encounterId: string;
}

export function EncounterMedicationsPanel({ patientId, encounterId }: EncounterMedicationsPanelProps) {
  const { data, isLoading, isError } = usePrescriptions({ patientId });
  const prescriptions = data?.data ?? [];

  return (
    <section
      className="rounded-xl border border-warning/35 bg-card p-4 shadow-sm"
      data-testid="encounter-medications-panel"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Pill className="h-4 w-4 text-emerald-600" />
          <h3 className="text-sm font-semibold text-foreground">Medications &amp; pharmacy status</h3>
        </div>
        <Link
          href={`/pharmacy/prescriptions?patientId=${encodeURIComponent(patientId)}&encounterId=${encodeURIComponent(encounterId)}&source=encounter`}
          className="text-xs font-medium text-primary hover:underline"
        >
          Open pharmacy workspace
        </Link>
      </div>

      <p className="mt-1 text-xs text-muted-foreground">
        Pharmacy-service is the medication source of truth. Prescribing from this cockpit is done through
        structured <span className="font-medium">PRESCRIBE</span> forms (cadre-gated, countersign-aware);
        their medication requests route via OROS to pharmacy.
      </p>

      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading medication status…
        </div>
      ) : isError ? (
        <p className="mt-3 text-xs text-red-700" role="alert">
          Pharmacy service unreachable — medication status unavailable. No placeholder data is shown.
        </p>
      ) : prescriptions.length === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">
          No prescriptions on record for this patient.
        </p>
      ) : (
        <ul className="mt-3 space-y-1 text-sm text-foreground">
          {prescriptions.map((rx) => {
            const items = Array.isArray(rx.attributes?.items) ? rx.attributes.items : [];
            const label =
              items.length > 0
                ? items
                    .map((i) => [i.medication, i.dosage].filter(Boolean).join(" "))
                    .filter(Boolean)
                    .join(", ")
                : "Prescription";
            return (
              <li key={rx.id} className="flex justify-between gap-2 rounded-lg bg-background px-3 py-2">
                <span>{label}</span>
                <span className="text-xs font-medium uppercase text-muted-foreground">
                  {String(rx.attributes?.status ?? "UNKNOWN")}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
