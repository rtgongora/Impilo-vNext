"use client";

import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { WardManagementPanel } from "@/components/ward/WardManagementPanel";

const INPATIENT_TABS = [
  { label: "Overview", href: "/clinical/inpatient" },
  { label: "Admissions", href: "/clinical/inpatient/admissions" },
  { label: "Ward board", href: "/clinical/inpatient/ward-board" },
  { label: "Nursing", href: "/clinical/inpatient/nursing" },
  { label: "Rounds", href: "/clinical/inpatient/rounds" },
];

export default function InpatientWardBoardPage() {
  return (
    <PlaneWorkspaceShell
      title="Ward board"
      subtitle="Bed occupancy, transfers, and ward-level patient list"
      plane="Clinical Plane"
      maturity="partial"
      tabs={INPATIENT_TABS}
      relatedLinks={[
        { label: "Admissions list", href: "/clinical/inpatient/admissions" },
        { label: "Nursing workbench", href: "/clinical/inpatient/nursing" },
      ]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CARE" />
      <WardManagementPanel />
    </PlaneWorkspaceShell>
  );
}
