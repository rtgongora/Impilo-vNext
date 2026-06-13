"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { useAdmission, useDischargeAdmission } from "@/hooks/queries/useInpatient";

export default function InpatientDischargePage() {
  const params = useParams();
  const router = useRouter();
  const admissionId = params?.admissionId as string | undefined;
  const { data, isLoading } = useAdmission(admissionId);
  const discharge = useDischargeAdmission();

  const attrs =
    data && typeof data === "object" && "data" in data
      ? ((data as { data?: Record<string, unknown> }).data ?? {})
      : {};

  return (
    <PlaneWorkspaceShell
      title="Discharge planning"
      subtitle="Discharge readiness, summary, medications, follow-up, and audit trail"
      plane="Clinical Plane"
      maturity="partial"
      contextPatient={String(attrs.patientName ?? attrs.patientCpid ?? "")}
      relatedLinks={[
        { label: "Episode dashboard", href: `/clinical/inpatient/admissions/${admissionId}` },
        { label: "Pharmacy (discharge Rx)", href: "/pharmacy/prescriptions" },
        { label: "Core transactions", href: "/core-transaction" },
        { label: "Reports", href: "/reports/clinical" },
      ]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_DISCHARGE" />

      {isLoading ? (
        <div className="flex items-center py-8 text-muted-foreground">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          Loading admission…
        </div>
      ) : (
        <div className="space-y-4 rounded-xl border border-border p-6">
          <h3 className="text-sm font-semibold text-foreground">Discharge checklist</h3>
          <ul className="list-inside list-disc space-y-1 text-sm text-muted-foreground">
            <li>Confirm discharge diagnosis and summary</li>
            <li>Issue discharge prescription via pharmacy</li>
            <li>Book follow-up or referral if needed</li>
            <li>Update shared health record and transaction audit</li>
          </ul>
          <div className="flex flex-wrap gap-2 pt-2">
            <button
              type="button"
              disabled={!admissionId || discharge.isPending}
              onClick={() => {
                if (!admissionId) return;
                discharge.mutate(
                  { id: admissionId, body: { reason: "PLANNED_DISCHARGE" } },
                  { onSuccess: () => router.push("/clinical/inpatient/admissions") },
                );
              }}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
            >
              {discharge.isPending ? "Discharging…" : "Complete discharge (BFF)"}
            </button>
            <Link href="/pharmacy/prescriptions" className="rounded-lg border px-4 py-2 text-sm hover:bg-background">
              Discharge Rx
            </Link>
            <Link href="/core-transaction" className="rounded-lg border px-4 py-2 text-sm hover:bg-background">
              View audit trail
            </Link>
          </div>
          {discharge.isError ? (
            <p className="text-sm text-rose-600">Discharge failed — verify BFF authorization and admission state.</p>
          ) : null}
        </div>
      )}
    </PlaneWorkspaceShell>
  );
}
