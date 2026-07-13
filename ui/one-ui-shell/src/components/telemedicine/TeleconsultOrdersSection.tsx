"use client";

import { useState } from "react";
import { Loader2, Pill, Plus } from "lucide-react";
import { EncounterLabOrdersPanel } from "@/components/encounter/EncounterLabOrdersPanel";
import { EncounterImagingOrdersPanel } from "@/components/encounter/EncounterImagingOrdersPanel";
import { useCreatePrescription, usePrescriptions } from "@/hooks/queries/usePharmacy";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";

/**
 * In-session clinical orders for a teleconsult (R5/G17). Replaces the free-text "orders" note with
 * REAL coded orders written to the system-of-record rails — lab (OROS) + imaging (OROS) via the
 * shared encounter panels, and medication (pharmacy-service) via a compact prescribe form. The
 * teleconsult session already holds patientId + encounterId + provider identity, so these reuse the
 * exact same rails the walk-in encounter uses; no new prescribing/ordering SoR is introduced.
 */
export function TeleconsultOrdersSection({
  patientId,
  encounterId,
  patientCpid,
}: {
  patientId: string;
  encounterId: string;
  patientCpid?: string;
}) {
  return (
    <div className="space-y-3" data-testid="teleconsult-orders-section">
      <p className="text-xs text-muted-foreground">
        Coded orders below are written to the clinical rails (OROS / pharmacy) and linked to encounter{" "}
        <span className="font-mono">{encounterId.substring(0, 12)}</span>. The free-text note above is a summary only.
      </p>
      <TeleconsultPrescriptionPanel patientId={patientId} encounterId={encounterId} />
      <EncounterLabOrdersPanel patientId={patientId} encounterId={encounterId} patientCpid={patientCpid} />
      <EncounterImagingOrdersPanel patientId={patientId} encounterId={encounterId} patientCpid={patientCpid} />
    </div>
  );
}

/** Compact prescribe form → pharmacy SoR, encounter-scoped (mirrors the encounter meds path). */
function TeleconsultPrescriptionPanel({ patientId, encounterId }: { patientId: string; encounterId: string }) {
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const { data, isLoading } = usePrescriptions({ patientId });
  const createRx = useCreatePrescription();

  const [medication, setMedication] = useState("");
  const [dosage, setDosage] = useState("");
  const [frequency, setFrequency] = useState("");
  const [duration, setDuration] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const rows = (data?.data ?? []).filter((rx) => {
    const enc = (rx.attributes as { encounterId?: string; encounter_id?: string });
    return String(enc.encounterId ?? enc.encounter_id ?? "") === encounterId;
  });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    const prescribedBy = user?.id ?? "";
    if (!prescribedBy) return setError("Provider identity is required to prescribe.");
    if (!medication.trim() || !dosage.trim() || !frequency.trim()) {
      return setError("Medication, dosage and frequency are required.");
    }
    try {
      await createRx.mutateAsync({
        patient_id: patientId,
        encounter_id: encounterId,
        facility_id: facility?.id,
        medication_name: medication.trim(),
        dosage: dosage.trim(),
        frequency: frequency.trim(),
        duration: duration.trim() || undefined,
        prescribed_by: prescribedBy,
      });
      setMedication("");
      setDosage("");
      setFrequency("");
      setDuration("");
      setSuccess("Prescription submitted to pharmacy.");
    } catch {
      setError("Failed to prescribe. Verify BFF and pharmacy availability.");
    }
  }

  return (
    <section className="rounded-xl border border-info/35 bg-card p-4 shadow-sm" data-testid="teleconsult-prescription-panel">
      <div className="flex items-center gap-2">
        <Pill className="h-4 w-4 text-info" />
        <h3 className="text-sm font-semibold text-foreground">Prescribe medication</h3>
      </div>

      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
      ) : (
        <ul className="mt-3 space-y-1 text-sm">
          {rows.length === 0 ? (
            <li className="text-muted-foreground">No prescriptions linked to this encounter yet.</li>
          ) : (
            rows.map((rx) => {
              const a = rx.attributes as Record<string, unknown>;
              const name = String(a.medicationName ?? a.medication_name ?? (Array.isArray(a.items) && a.items[0] ? (a.items[0] as { medication?: string }).medication : "") ?? "Medication");
              return (
                <li key={rx.id} className="flex justify-between gap-2 rounded-lg bg-background px-3 py-2">
                  <span>{name}</span>
                  <span className="text-xs font-medium uppercase text-muted-foreground">{String(a.status ?? "ORDERED")}</span>
                </li>
              );
            })
          )}
        </ul>
      )}

      <form onSubmit={(e) => void submit(e)} className="mt-4 space-y-3 border-t border-border pt-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <input value={medication} onChange={(e) => setMedication(e.target.value)} placeholder="Medication (e.g. Amoxicillin 500mg)"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm" />
          <input value={dosage} onChange={(e) => setDosage(e.target.value)} placeholder="Dosage (e.g. 500mg)"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm" />
          <input value={frequency} onChange={(e) => setFrequency(e.target.value)} placeholder="Frequency (e.g. TDS)"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm" />
          <input value={duration} onChange={(e) => setDuration(e.target.value)} placeholder="Duration (e.g. 5 days)"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm" />
        </div>
        {error ? <p className="text-xs text-red-600">{error}</p> : null}
        {success ? <p className="text-xs text-primary-hover">{success}</p> : null}
        <button type="submit" disabled={createRx.isPending}
          className="inline-flex items-center gap-1.5 rounded-lg bg-info px-3 py-2 text-xs font-medium text-white hover:opacity-90 disabled:opacity-50">
          {createRx.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
          Prescribe
        </button>
      </form>
    </section>
  );
}
