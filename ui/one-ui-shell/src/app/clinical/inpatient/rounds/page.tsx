"use client";

import Link from "next/link";
import { Loader2 } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { WorkspaceEmptyState } from "@/components/workspace/WorkspaceEmptyState";
import { useAdmissions, useWardRounds } from "@/hooks/queries/useInpatient";

const INPATIENT_TABS = [
  { label: "Overview", href: "/clinical/inpatient" },
  { label: "Admissions", href: "/clinical/inpatient/admissions" },
  { label: "Ward board", href: "/clinical/inpatient/ward-board" },
  { label: "Nursing", href: "/clinical/inpatient/nursing" },
  { label: "Rounds", href: "/clinical/inpatient/rounds" },
];

export default function InpatientRoundsPage() {
  const admissionsQuery = useAdmissions();
  const firstAdmissionId = (() => {
    const root = admissionsQuery.data;
    if (!root || typeof root !== "object") return undefined;
    const data = (root as { data?: unknown }).data;
    const list = Array.isArray(data) ? data : [];
    const first = list[0] as Record<string, unknown> | undefined;
    return first
      ? String(first.admissionRef ?? first.admissionId ?? first.id ?? "")
      : undefined;
  })();

  const roundsQuery = useWardRounds(firstAdmissionId);

  return (
    <PlaneWorkspaceShell
      title="Medical rounds"
      subtitle="Ward round notes, assessment and plan, orders, and discharge planning"
      plane="Clinical Plane"
      maturity="partial"
      tabs={INPATIENT_TABS}
      relatedLinks={[
        { label: "Admissions", href: "/clinical/inpatient/admissions" },
        { label: "Lab / results", href: "/lab/results" },
        { label: "Pharmacy", href: "/pharmacy" },
      ]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CLINICAL" />

      {admissionsQuery.isLoading ? (
        <div className="flex items-center py-8 text-muted-foreground">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          Loading admissions for rounds…
        </div>
      ) : !firstAdmissionId ? (
        <WorkspaceEmptyState
          title="No admissions for rounds"
          description="Create or open an admission first, then record ward round notes from the episode dashboard."
          actionLabel="View admissions"
          actionHref="/clinical/inpatient/admissions"
        />
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Showing ward rounds for admission{" "}
            <Link href={`/clinical/inpatient/admissions/${firstAdmissionId}`} className="font-medium text-primary">
              {firstAdmissionId}
            </Link>
          </p>
          {roundsQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading ward rounds…</p>
          ) : roundsQuery.data ? (
            <div className="rounded-xl border border-border bg-background p-4 text-sm text-foreground">
              Ward round data returned from BFF. Open the admission episode for full documentation.
            </div>
          ) : (
            <WorkspaceEmptyState
              title="No ward rounds recorded"
              description="Record the first ward round from the inpatient episode or EHR clinical note."
              actionLabel="Open episode"
              actionHref={`/clinical/inpatient/admissions/${firstAdmissionId}`}
            />
          )}
        </div>
      )}
    </PlaneWorkspaceShell>
  );
}
