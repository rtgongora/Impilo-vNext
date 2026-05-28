"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { WorkspaceEmptyState } from "@/components/workspace/WorkspaceEmptyState";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { useAdmission, useWardRounds } from "@/hooks/queries/useInpatient";

export default function InpatientAdmissionDetailPage() {
  const params = useParams();
  const admissionId = params?.admissionId as string | undefined;
  const { data, isLoading, isError } = useAdmission(admissionId);
  const roundsQuery = useWardRounds(admissionId);

  const attrs =
    data && typeof data === "object" && "data" in data
      ? ((data as { data?: Record<string, unknown> }).data ?? {})
      : {};

  return (
    <PlaneWorkspaceShell
      title={`Inpatient episode ${admissionId ?? ""}`}
      subtitle="Episode dashboard — clinical activity, orders, and discharge handoff"
      plane="Clinical Plane"
      maturity="partial"
      contextPatient={String(attrs.patientName ?? attrs.patientCpid ?? "")}
      relatedLinks={[
        { label: "Ward rounds", href: "/clinical/inpatient/rounds" },
        { label: "Pharmacy", href: "/pharmacy/prescriptions" },
        { label: "Discharge", href: `/clinical/inpatient/discharge/${admissionId}` },
        { label: "Audit / transactions", href: "/core-transaction" },
      ]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CARE" />

      {isLoading ? (
        <div className="flex items-center py-12 text-slate-500">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          Loading episode…
        </div>
      ) : isError ? (
        <WorkspaceEmptyState
          title="Admission not found"
          description="Verify the admission ID and that inpatient-service is reachable via the BFF."
          actionLabel="Back to admissions"
          actionHref="/clinical/inpatient/admissions"
        />
      ) : (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-xl border border-slate-200 p-4">
              <h3 className="text-xs font-semibold uppercase text-slate-400">Episode</h3>
              <dl className="mt-2 space-y-1 text-sm">
                <div className="flex justify-between">
                  <dt className="text-slate-500">Status</dt>
                  <dd>{String(attrs.status ?? "ACTIVE")}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-500">Ward</dt>
                  <dd>{String(attrs.wardName ?? attrs.ward ?? "—")}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-500">Bed</dt>
                  <dd>{String(attrs.bedNumber ?? attrs.bed ?? "—")}</dd>
                </div>
              </dl>
            </div>
            <div className="rounded-xl border border-slate-200 p-4">
              <h3 className="text-xs font-semibold uppercase text-slate-400">Quick actions</h3>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link href="/clinical/inpatient/rounds" className="rounded-lg border px-3 py-1.5 text-sm hover:bg-slate-50">
                  Ward round
                </Link>
                <Link href="/pharmacy/prescriptions" className="rounded-lg border px-3 py-1.5 text-sm hover:bg-slate-50">
                  Orders / Rx
                </Link>
                <Link
                  href={`/clinical/inpatient/discharge/${admissionId}`}
                  className="rounded-lg border border-impilo-200 bg-impilo-50 px-3 py-1.5 text-sm text-impilo-700"
                >
                  Discharge
                </Link>
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 p-4">
            <h3 className="text-sm font-semibold text-slate-900">Ward rounds</h3>
            {roundsQuery.isLoading ? (
              <p className="mt-2 text-sm text-slate-500">Loading rounds…</p>
            ) : (
              <p className="mt-2 text-sm text-slate-600">
                {roundsQuery.data ? "Round history available via BFF." : "No ward rounds recorded yet."}
              </p>
            )}
            <Link href="/clinical/inpatient/rounds" className="mt-2 inline-block text-sm text-impilo-600 hover:underline">
              Open medical rounds workspace →
            </Link>
          </div>
        </div>
      )}
    </PlaneWorkspaceShell>
  );
}
