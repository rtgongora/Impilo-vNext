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

function admissionTasks(row: Record<string, unknown>, admissionId: string, cpid: string | null) {
  const attrs = (row.attributes ?? row) as Record<string, unknown>;
  const tasks: Array<{ id: string; label: string; href?: string; status: string }> = [];

  const vitalsDue = attrs.vitalsDue ?? attrs.vitals_due ?? attrs.nextVitalsDue;
  tasks.push({
    id: `${admissionId}-vitals`,
    label: "Chart vitals",
    href: cpid ? `/ehr/${cpid}/vitals?source=inpatient-nursing&admissionId=${admissionId}` : undefined,
    status: vitalsDue ? "DUE" : "ROUTINE",
  });

  const medsDue = attrs.medicationsDue ?? attrs.medications_due ?? attrs.marPending;
  tasks.push({
    id: `${admissionId}-meds`,
    label: "Medication administration",
    href: cpid ? `/ehr/${cpid}/medications?source=inpatient-nursing&admissionId=${admissionId}` : undefined,
    status: medsDue ? "DUE" : "SCHEDULED",
  });

  const notesDue = attrs.nursingNoteDue ?? attrs.nursing_note_due;
  tasks.push({
    id: `${admissionId}-notes`,
    label: "Nursing note",
    href: cpid ? `/ehr/${cpid}/notes?source=inpatient-nursing&admissionId=${admissionId}` : undefined,
    status: notesDue ? "DUE" : "OPTIONAL",
  });

  const handover = attrs.handoverRequired ?? attrs.handover_required;
  if (handover) {
    tasks.push({
      id: `${admissionId}-handover`,
      label: "Shift handover item",
      href: "/shift/handover",
      status: "DUE",
    });
  }

  return tasks;
}

export default function InpatientNursingPage() {
  const { data, isLoading, isError, error } = useAdmissions();
  const rows = extractAdmissionRows(data);

  return (
    <PlaneWorkspaceShell
      title="Nursing workbench"
      subtitle="Assigned patients, vitals tasks, nursing notes, and handover — inline shortcuts into EHR sections"
      plane="Clinical Plane"
      maturity="live"
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

                <div className="mt-4">
                  <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">Admission tasks</p>
                  <ul className="mt-2 space-y-2">
                    {admissionTasks(row, id, cpid).map((task) => (
                      <li key={task.id} className="flex items-center justify-between gap-2 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
                        {task.href ? (
                          <Link href={task.href} className="text-xs font-medium text-impilo-600 hover:underline">
                            {task.label}
                          </Link>
                        ) : (
                          <span className="text-xs text-slate-600">{task.label}</span>
                        )}
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${
                            task.status === "DUE" ? "bg-rose-50 text-rose-700" : "bg-slate-100 text-slate-600"
                          }`}
                        >
                          {task.status}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>

                {!cpid ? (
                  <p className="mt-3 text-xs text-amber-700">
                    Patient CPID not returned — open the{" "}
                    <Link href={`/clinical/inpatient/admissions/${id}`} className="font-medium underline">
                      admission detail
                    </Link>{" "}
                    to link chart tasks.
                  </p>
                ) : null}
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
