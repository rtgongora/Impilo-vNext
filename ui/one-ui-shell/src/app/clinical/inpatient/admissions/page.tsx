"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Loader2, Plus } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { WorkspaceEmptyState } from "@/components/workspace/WorkspaceEmptyState";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { InpatientAdmissionHandoffBanner } from "@/components/inpatient/InpatientAdmissionHandoffBanner";
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

export default function InpatientAdmissionsPage() {
  const searchParams = useSearchParams();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const transactionId = searchParams.get("transaction_id");
  const source = searchParams.get("source");
  const { data, isLoading, isError, error } = useAdmissions();
  const rows = extractAdmissionRows(data);

  return (
    <PlaneWorkspaceShell
      title="Inpatient admissions"
      subtitle="Active episodes — search, open, transfer, and discharge via BFF"
      plane="Clinical Plane"
      maturity="live"
      tabs={INPATIENT_TABS}
      relatedLinks={[
        { label: "Ward board", href: "/clinical/inpatient/ward-board" },
        { label: "Bed management", href: "/beds" },
      ]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CARE" />
      <InpatientAdmissionHandoffBanner
        patientId={patientId}
        encounterId={encounterId}
        transactionId={transactionId}
        source={source}
      />

      <div className="flex justify-end">
        <Link
          href={
            patientId
              ? `/clinical/inpatient/admissions/new?patientId=${encodeURIComponent(patientId)}${encounterId ? `&encounterId=${encodeURIComponent(encounterId)}` : ""}`
              : "/clinical/inpatient/admissions/new"
          }
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover"
        >
          <Plus className="h-4 w-4" />
          New admission
        </Link>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-12 text-muted-foreground">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          Loading admissions…
        </div>
      ) : isError ? (
        <WorkspaceEmptyState
          title="Could not load admissions"
          description={error instanceof Error ? error.message : "Check BFF and inpatient-service availability."}
          actionLabel="Open ward board"
          actionHref="/clinical/inpatient/ward-board"
        />
      ) : rows.length === 0 ? (
        <WorkspaceEmptyState
          title="No admissions yet"
          description="Use bed management to allocate a bed and create an admission, or verify demo seed data is loaded."
          actionLabel="Open ward board"
          actionHref="/clinical/inpatient/ward-board"
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-border">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-background">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Admission</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Patient</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Ward / bed</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-card">
              {rows.map((row, idx) => {
                const id = String(row.id ?? row.admissionId ?? idx);
                const attrs = (row.attributes ?? row) as Record<string, unknown>;
                return (
                  <tr key={id} className="hover:bg-background">
                    <td className="px-4 py-3">
                      <Link href={`/clinical/inpatient/admissions/${id}`} className="font-medium text-primary hover:underline">
                        {id}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-foreground">
                      {String(attrs.patientName ?? attrs.patient_cpid ?? attrs.patientCpid ?? "—")}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {String(attrs.wardName ?? attrs.ward ?? "—")} / {String(attrs.bedNumber ?? attrs.bed ?? "—")}
                    </td>
                    <td className="px-4 py-3">
                      <span className="rounded-full bg-warning-soft px-2 py-0.5 text-xs font-medium text-warning-foreground">
                        {String(attrs.status ?? "ACTIVE")}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </PlaneWorkspaceShell>
  );
}
