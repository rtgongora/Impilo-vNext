"use client";

import Link from "next/link";
import { Activity, FileText, Loader2, Pill } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { WorkspaceEmptyState } from "@/components/workspace/WorkspaceEmptyState";
import { useAdmissions } from "@/hooks/queries/useInpatient";

const INPATIENT_TABS = [
  { label: "Overview", href: "/clinical/inpatient" },
  { label: "Admissions", href: "/clinical/inpatient/admissions" },
  { label: "Ward board", href: "/clinical/inpatient/ward-board" },
  { label: "Nursing", href: "/clinical/inpatient/nursing" },
  { label: "Rounds", href: "/clinical/inpatient/rounds" },
];

function extractAdmissionRows(payload: unknown): Record<string, unknown>[] {
  if (!payload || typeof payload !== "object") return [];
  const root = payload as Record<string, unknown>;
  const data = root.data;
  if (Array.isArray(data)) return data as Record<string, unknown>[];
  if (data && typeof data === "object") {
    const inner = data as Record<string, unknown>;
    if (Array.isArray(inner.items)) return inner.items as Record<string, unknown>[];
    if (Array.isArray(inner.admissions)) return inner.admissions as Record<string, unknown>[];
  }
  if (Array.isArray(root.items)) return root.items as Record<string, unknown>[];
  return [];
}

function resolvePatientCpid(row: Record<string, unknown>): string | null {
  const attrs = (row.attributes ?? row) as Record<string, unknown>;
  const cpid = attrs.patient_cpid ?? attrs.patientCpid ?? attrs.cpid ?? row.patientCpid;
  return cpid ? String(cpid) : null;
}

export default function InpatientNursingPage() {
  const { data, isLoading, isError, error } = useAdmissions();
  const rows = extractAdmissionRows(data);

  return (
    <PlaneWorkspaceShell
      title="Nursing workbench"
      subtitle="Assigned patients, vitals tasks, nursing notes, and handover — inline shortcuts into EHR sections"
      plane="Clinical Plane"
      maturity="partial"
      tabs={INPATIENT_TABS}
      nompiloHint="Ask Nompilo for nursing task priorities or handover checklist hints."
      relatedLinks={[
        { label: "Ward board", href: "/clinical/inpatient/ward-board" },
        { label: "Shift handover", href: "/shift/handover" },
      ]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_NURSING" />

      {isLoading ? (
        <div className="flex items-center justify-center py-12 text-slate-500">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          Loading assigned patients…
        </div>
      ) : isError ? (
        <WorkspaceEmptyState
          title="Could not load nursing assignments"
          description={error instanceof Error ? error.message : "Check BFF and inpatient-service availability."}
          actionLabel="Open ward board"
          actionHref="/clinical/inpatient/ward-board"
        />
      ) : rows.length === 0 ? (
        <WorkspaceEmptyState
          title="No inpatient assignments"
          description="Admissions from the BFF will appear here with inline vitals, medication, and notes shortcuts."
          actionLabel="Open ward board"
          actionHref="/clinical/inpatient/ward-board"
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {rows.map((row, idx) => {
            const id = String(row.id ?? row.admissionId ?? idx);
            const attrs = (row.attributes ?? row) as Record<string, unknown>;
            const patientLabel = String(attrs.patientName ?? attrs.patient_cpid ?? attrs.patientCpid ?? "Patient");
            const cpid = resolvePatientCpid(row);
            const ward = String(attrs.wardName ?? attrs.ward ?? "—");
            const bed = String(attrs.bedNumber ?? attrs.bed ?? "—");

            return (
              <article key={id} className="rounded-xl border border-slate-200 bg-white p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <Link
                      href={`/clinical/inpatient/admissions/${id}`}
                      className="text-sm font-semibold text-impilo-600 hover:underline"
                    >
                      {patientLabel}
                    </Link>
                    <p className="mt-1 text-xs text-slate-500">
                      Admission {id} · {ward} / bed {bed}
                    </p>
                  </div>
                  <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-medium text-amber-700">
                    {String(attrs.status ?? "ACTIVE")}
                  </span>
                </div>

                {cpid ? (
                  <div className="mt-4 grid gap-2 sm:grid-cols-3">
                    <Link
                      href={`/ehr/${cpid}/vitals?source=inpatient-nursing&admissionId=${id}`}
                      className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 hover:border-impilo-200 hover:bg-impilo-50"
                    >
                      <Activity className="h-3.5 w-3.5 text-rose-500" />
                      Chart vitals
                    </Link>
                    <Link
                      href={`/ehr/${cpid}/medications?source=inpatient-nursing&admissionId=${id}`}
                      className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 hover:border-impilo-200 hover:bg-impilo-50"
                    >
                      <Pill className="h-3.5 w-3.5 text-emerald-600" />
                      Meds & MAR
                    </Link>
                    <Link
                      href={`/ehr/${cpid}/notes?source=inpatient-nursing&admissionId=${id}`}
                      className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 hover:border-impilo-200 hover:bg-impilo-50"
                    >
                      <FileText className="h-3.5 w-3.5 text-slate-500" />
                      Nursing note
                    </Link>
                  </div>
                ) : (
                  <p className="mt-3 text-xs text-amber-700">
                    Patient CPID not returned — open the{" "}
                    <Link href={`/clinical/inpatient/admissions/${id}`} className="font-medium underline">
                      admission detail
                    </Link>{" "}
                    to link chart tasks.
                  </p>
                )}
              </article>
            );
          })}
        </div>
      )}

      <div className="mt-6 flex flex-wrap gap-2">
        <Link href="/clinical/inpatient/ward-board" className="rounded-lg border px-3 py-2 text-sm hover:bg-slate-50">
          Ward board
        </Link>
        <Link href="/shift/handover" className="rounded-lg border px-3 py-2 text-sm hover:bg-slate-50">
          Handover notes
        </Link>
      </div>
    </PlaneWorkspaceShell>
  );
}
