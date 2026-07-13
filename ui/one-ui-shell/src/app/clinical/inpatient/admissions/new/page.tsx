"use client";

import { useSearchParams } from "next/navigation";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { NewAdmissionForm } from "@/components/inpatient/NewAdmissionForm";

const INPATIENT_TABS = [
  { label: "Overview", href: "/clinical/inpatient" },
  { label: "Admissions", href: "/clinical/inpatient/admissions" },
  { label: "Ward board", href: "/clinical/inpatient/ward-board" },
  { label: "Nursing", href: "/clinical/inpatient/nursing" },
  { label: "Rounds", href: "/clinical/inpatient/rounds" },
];

export default function NewAdmissionPage() {
  const searchParams = useSearchParams();
  const patientId = searchParams.get("patientId") ?? "";
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");
  const initialWardId = searchParams.get("wardId");
  const initialBedId = searchParams.get("bedId");

  return (
    <PlaneWorkspaceShell
      title="New admission"
      subtitle="Admit a patient to an inpatient bed"
      plane="Clinical Plane"
      maturity="live"
      tabs={INPATIENT_TABS}
      relatedLinks={[{ label: "Admissions", href: "/clinical/inpatient/admissions" }]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CARE" />
      {patientId ? (
        <NewAdmissionForm
          patientId={patientId}
          encounterId={encounterId}
          source={source}
          initialWardId={initialWardId}
          initialBedId={initialBedId}
        />
      ) : (
        <div className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          No patient selected. Open a patient&apos;s encounter and admit from the visit outcome.
        </div>
      )}
    </PlaneWorkspaceShell>
  );
}
