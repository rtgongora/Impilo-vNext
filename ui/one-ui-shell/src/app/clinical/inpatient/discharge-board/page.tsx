"use client";

import { useState } from "react";
import { ClipboardCheck, FileText, Loader2 } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { WorkspaceEmptyState } from "@/components/workspace/WorkspaceEmptyState";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import {
  useAdmissions,
  useCountersignDischargeSummary,
  useDischargeSummary,
  useFinaliseDischargeSummary,
  type DischargeSummary,
} from "@/hooks/queries/useInpatient";

const INPATIENT_TABS = [
  { label: "Overview", href: "/clinical/inpatient" },
  { label: "Admissions", href: "/clinical/inpatient/admissions" },
  { label: "Ward board", href: "/clinical/inpatient/ward-board" },
  { label: "Nursing", href: "/clinical/inpatient/nursing" },
  { label: "Escalations", href: "/clinical/inpatient/escalations" },
  { label: "Discharge", href: "/clinical/inpatient/discharge-board" },
];

function admissionRows(payload: unknown): Record<string, unknown>[] {
  if (!payload || typeof payload !== "object") return [];
  const data = (payload as Record<string, unknown>).data;
  if (Array.isArray(data)) return data as Record<string, unknown>[];
  return [];
}

function attr(row: Record<string, unknown>, ...keys: string[]): string | null {
  const a = (row.attributes ?? row) as Record<string, unknown>;
  for (const k of keys) {
    const v = a[k] ?? row[k];
    if (v != null && String(v).length > 0) return String(v);
  }
  return null;
}

function DischargeSummaryPanel({ encounterId }: { encounterId: string }) {
  const { data, isLoading } = useDischargeSummary(encounterId);
  const finalise = useFinaliseDischargeSummary();
  const countersign = useCountersignDischargeSummary();
  const [attestation, setAttestation] = useState("");
  const summary = (data?.data ?? null) as DischargeSummary | null;

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading summary…
      </div>
    );
  }

  if (!summary || !summary.id) {
    return (
      <p className="text-sm text-slate-500">
        No discharge summary drafted yet for this encounter. Draft one from the patient chart before finalising.
      </p>
    );
  }

  const finalised = summary.status === "FINALISED";
  const countersignRequired = summary.countersign_required === true;
  const countersigned = Boolean(summary.countersigned_by);
  const countersignPending = countersignRequired && !countersigned;

  return (
    <div className="space-y-2">
      <div className="text-sm">
        <span className="font-medium">Status:</span> {summary.status}
      </div>
      {summary.discharge_diagnosis && (
        <div className="text-sm">
          <span className="font-medium">Diagnosis:</span> {summary.discharge_diagnosis}
        </div>
      )}
      {countersignRequired && (
        <div className="text-sm" data-testid="discharge-countersign-status">
          <span className="font-medium">Countersignature:</span>{" "}
          {countersigned ? (
            <span className="text-green-700">countersigned by {summary.countersigned_by}</span>
          ) : (
            <span className="text-amber-700">required — not yet countersigned</span>
          )}
        </div>
      )}
      {countersignPending && !finalised && (
        <div className="flex flex-wrap items-center gap-2" data-testid="discharge-countersign-form">
          <input
            value={attestation}
            onChange={(e) => setAttestation(e.target.value)}
            placeholder="Attestation (e.g. reviewed and agreed)"
            aria-label="Countersign attestation"
            className="w-64 rounded border border-slate-300 px-2 py-1 text-sm"
          />
          <button
            type="button"
            disabled={countersign.isPending}
            onClick={() => countersign.mutate({ encounterId, attestation: attestation || undefined })}
            className="rounded border border-slate-900 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50"
          >
            {countersign.isPending ? "Countersigning…" : "Countersign summary"}
          </button>
        </div>
      )}
      {countersign.isError && (
        <p className="text-sm text-red-600">
          Countersign was rejected — the author cannot countersign their own summary, and a summary
          can only be countersigned once.
        </p>
      )}
      {finalise.isError && (
        <p className="text-sm text-red-600">
          Discharge is blocked — pending clearances or a missing required countersignature. Resolve
          them first; the summary is never silently finalised.
        </p>
      )}
      <button
        type="button"
        disabled={finalised || finalise.isPending || countersignPending}
        onClick={() => finalise.mutate({ encounterId })}
        className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-700 disabled:opacity-50"
      >
        {finalised
          ? "Finalised"
          : countersignPending
            ? "Countersignature required before finalise"
            : finalise.isPending
              ? "Finalising…"
              : "Finalise discharge summary"}
      </button>
    </div>
  );
}

export default function InpatientDischargeBoardPage() {
  const { data, isLoading, isError } = useAdmissions();
  const [openEncounter, setOpenEncounter] = useState<string | null>(null);

  const admissions = admissionRows(data).filter(
    (r) => attr(r, "status") === "ADMITTED",
  );

  return (
    <PlaneWorkspaceShell
      title="Discharge board"
      subtitle="Inpatients ready for discharge — clearance-gated summary finalisation"
      plane="Clinical Plane"
      tabs={INPATIENT_TABS}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CARE" />
      <NompiloContextualGuidance routePath="/clinical/inpatient/discharge-board" />

      {isLoading && (
        <div className="flex items-center gap-2 text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading admissions…
        </div>
      )}

      {isError && (
        <WorkspaceEmptyState
          icon={<FileText className="h-6 w-6" />}
          title="Discharge board unavailable"
          description="The inpatient service could not be reached. No data is shown rather than placeholder values."
        />
      )}

      {!isLoading && !isError && admissions.length === 0 && (
        <WorkspaceEmptyState
          icon={<ClipboardCheck className="h-6 w-6" />}
          title="No active inpatients"
          description="There are no admitted patients to plan discharge for right now."
        />
      )}

      {admissions.length > 0 && (
        <ul className="space-y-3">
          {admissions.map((row) => {
            const admissionRef = attr(row, "admission_ref", "admissionRef", "id") ?? "";
            const encounterId = attr(row, "encounter_id", "encounterId");
            const cpid = attr(row, "subject_cpid", "patient_cpid", "patientCpid");
            return (
              <li key={admissionRef} className="rounded-lg border border-slate-200 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="font-semibold">Patient {cpid ?? "—"}</div>
                    <div className="text-sm text-slate-500">
                      Admission {admissionRef}
                      {attr(row, "admitting_diagnosis", "admittingDiagnosis")
                        ? ` · ${attr(row, "admitting_diagnosis", "admittingDiagnosis")}`
                        : ""}
                    </div>
                  </div>
                  {encounterId && (
                    <button
                      type="button"
                      onClick={() =>
                        setOpenEncounter(openEncounter === encounterId ? null : encounterId)
                      }
                      className="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50"
                    >
                      {openEncounter === encounterId ? "Hide summary" : "Discharge summary"}
                    </button>
                  )}
                </div>
                {encounterId && openEncounter === encounterId && (
                  <div className="mt-3 border-t border-slate-100 pt-3">
                    <DischargeSummaryPanel encounterId={encounterId} />
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </PlaneWorkspaceShell>
  );
}
