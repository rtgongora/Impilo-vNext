"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2, BedDouble, AlertTriangle } from "lucide-react";
import { usePatient } from "@/hooks/queries/usePatients";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useCreateAdmission, useActiveAdmission } from "@/hooks/queries/useInpatient";
import { BedPickerField } from "./BedPickerField";

const ADMISSION_TYPES = ["EMERGENCY", "ELECTIVE", "TRANSFER"];

function admissionError(error: unknown): string {
  if (error && typeof error === "object") {
    const err = (error as { error?: { message?: string } }).error;
    if (err?.message && typeof err.message === "string") return err.message;
  }
  return "Could not create the admission. Please try again.";
}

function extractRef(response: unknown): string | null {
  if (!response || typeof response !== "object") return null;
  const r = response as Record<string, unknown>;
  const meta = r.meta as Record<string, unknown> | undefined;
  if (meta && typeof meta.admission_ref === "string") return meta.admission_ref;
  const data = r.data as Record<string, unknown> | undefined;
  if (data) {
    if (typeof data.admissionRef === "string") return data.admissionRef;
    if (typeof data.id === "string") return data.id;
  }
  return null;
}

function hasActiveAdmission(response: unknown): { ref: string } | null {
  if (!response || typeof response !== "object") return null;
  const data = (response as Record<string, unknown>).data;
  const rows = Array.isArray(data) ? data : data ? [data] : [];
  for (const row of rows) {
    if (row && typeof row === "object") {
      const r = row as Record<string, unknown>;
      const ref = r.admissionRef ?? r.id;
      if (ref) return { ref: String(ref) };
    }
  }
  return null;
}

export function NewAdmissionForm({
  patientId,
  encounterId,
  source,
  initialWardId = null,
  initialBedId = null,
}: {
  patientId: string;
  encounterId: string | null;
  source: string | null;
  initialWardId?: string | null;
  initialBedId?: string | null;
}) {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const { data: patientData, isPending: patientPending } = usePatient(patientId);
  const patient = patientData?.data;
  const subjectCpid = patient?.attributes?.cpid ?? patientId;

  const { data: activeData } = useActiveAdmission(subjectCpid, facility?.id);
  const active = useMemo(() => hasActiveAdmission(activeData), [activeData]);

  const create = useCreateAdmission();

  const [admittingDiagnosis, setAdmittingDiagnosis] = useState("");
  const [admissionType, setAdmissionType] = useState("EMERGENCY");
  const [wardId, setWardId] = useState<string | null>(initialWardId || null);
  const [bedId, setBedId] = useState<string | null>(initialBedId || null);
  const [dietOrders, setDietOrders] = useState("REGULAR");
  const [activityLevel, setActivityLevel] = useState("BED_REST");

  const missingEncounter = !encounterId;
  const missingFacility = !facility?.id;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (missingEncounter || missingFacility || active) return;
    create.mutate(
      {
        encounterId,
        subjectCpid,
        facilityId: facility!.id,
        wardId: wardId ?? undefined,
        bedId: bedId ?? undefined,
        admittingDiagnosis: admittingDiagnosis || undefined,
        admissionType,
        dietOrders,
        activityLevel,
      },
      {
        onSuccess: (response) => {
          const ref = extractRef(response);
          router.push(ref ? `/clinical/inpatient/admissions/${ref}` : "/clinical/inpatient/admissions");
        },
      },
    );
  }

  if (patientPending) {
    return (
      <div className="flex items-center gap-2 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" /> Loading patient…
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="max-w-2xl space-y-5">
      <div className="rounded-2xl border border-border bg-card p-4">
        <p className="text-sm text-muted-foreground">Admitting</p>
        <p className="text-lg font-semibold text-foreground">
          {patient?.attributes?.displayName ?? subjectCpid}
        </p>
        <p className="text-xs text-muted-foreground">CPID {subjectCpid}</p>
      </div>

      {active && (
        <div className="flex items-start gap-2 rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm text-warning-foreground">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>
            This patient already has an active admission.{" "}
            <Link href={`/clinical/inpatient/admissions/${active.ref}`} className="font-medium underline">
              Open the current episode
            </Link>
            .
          </span>
        </div>
      )}

      {missingFacility && (
        <div className="rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm text-warning-foreground">
          Select a facility context before admitting.
        </div>
      )}

      {missingEncounter && (
        <div className="rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm text-warning-foreground">
          This admission has no encounter context. Start or open the patient&apos;s encounter first, then admit from
          the visit outcome.
        </div>
      )}

      <div>
        <label className="block text-sm font-medium text-foreground">
          Admitting diagnosis
          <textarea
            value={admittingDiagnosis}
            onChange={(e) => setAdmittingDiagnosis(e.target.value)}
            rows={2}
            placeholder="e.g. Decompensated heart failure, NYHA III"
            className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm"
          />
        </label>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <label className="block text-sm font-medium text-foreground">
          Admission type
          <select
            value={admissionType}
            onChange={(e) => setAdmissionType(e.target.value)}
            className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm bg-card"
          >
            {ADMISSION_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </label>
        <label className="block text-sm font-medium text-foreground">
          Diet
          <input
            value={dietOrders}
            onChange={(e) => setDietOrders(e.target.value)}
            className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm"
          />
        </label>
        <label className="block text-sm font-medium text-foreground">
          Activity
          <input
            value={activityLevel}
            onChange={(e) => setActivityLevel(e.target.value)}
            className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm"
          />
        </label>
      </div>

      <div>
        <p className="mb-1 flex items-center gap-1 text-sm font-medium text-foreground">
          <BedDouble className="h-4 w-4" /> Bed (optional — you can assign one later)
        </p>
        {facility?.id ? (
          <BedPickerField
            facilityId={facility.id}
            value={bedId}
            onChange={(newBedId, newWardId) => {
              setBedId(newBedId);
              setWardId(newWardId);
            }}
          />
        ) : null}
      </div>

      {create.isError && (
        <div className="flex items-start gap-2 rounded-xl border border-danger/30 bg-danger-soft p-3 text-sm text-rose-800">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          {admissionError(create.error)}
        </div>
      )}

      <div className="flex gap-3">
        <Link
          href="/clinical/inpatient/admissions"
          className="rounded-xl border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background"
        >
          Cancel
        </Link>
        <button
          type="submit"
          disabled={create.isPending || Boolean(active) || missingEncounter || missingFacility}
          className="inline-flex items-center gap-2 rounded-xl bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
        >
          {create.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          Admit patient
        </button>
      </div>
    </form>
  );
}
