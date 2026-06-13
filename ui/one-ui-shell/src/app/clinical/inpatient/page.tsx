"use client";

import Link from "next/link";
import {
  BedDouble,
  ClipboardList,
  DoorOpen,
  HeartPulse,
  Stethoscope,
  Users,
} from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";

const INPATIENT_MODULES = [
  {
    label: "Admissions",
    description: "Active and historical inpatient episodes",
    href: "/clinical/inpatient/admissions",
    icon: ClipboardList,
  },
  {
    label: "Ward board",
    description: "Bed occupancy and ward status",
    href: "/clinical/inpatient/ward-board",
    icon: BedDouble,
  },
  {
    label: "Nursing workbench",
    description: "Assigned patients and nursing tasks",
    href: "/clinical/inpatient/nursing",
    icon: HeartPulse,
  },
  {
    label: "Medical rounds",
    description: "Ward round notes and reviews",
    href: "/clinical/inpatient/rounds",
    icon: Stethoscope,
  },
  {
    label: "Bed admin",
    description: "Capacity configuration",
    href: "/beds",
    icon: Users,
  },
  {
    label: "Discharge",
    description: "Discharge planning and summaries",
    href: "/clinical/inpatient/admissions",
    icon: DoorOpen,
  },
];

const INPATIENT_TABS = [
  { label: "Overview", href: "/clinical/inpatient" },
  { label: "Admissions", href: "/clinical/inpatient/admissions" },
  { label: "Ward board", href: "/clinical/inpatient/ward-board" },
  { label: "Nursing", href: "/clinical/inpatient/nursing" },
  { label: "Rounds", href: "/clinical/inpatient/rounds" },
];

export default function InpatientOverviewPage() {
  return (
    <PlaneWorkspaceShell
      title="Inpatient care"
      subtitle="Admissions, ward operations, nursing workbench, medical rounds, and discharge"
      plane="Clinical Plane"
      maturity="partial"
      maturityDetail="BFF inpatient endpoints; depth varies by deployment"
      tabs={INPATIENT_TABS}
      nompiloHint="Ask Nompilo to find an admission, explain ward status, or guide discharge steps."
      relatedLinks={[
        { label: "Clinical hub", href: "/clinical", description: "All clinical modules" },
        { label: "Control tower", href: "/clinical/control-tower", description: "Facility operations" },
        { label: "Pharmacy / Rx", href: "/pharmacy", description: "Inpatient medication orders" },
        { label: "Core transactions", href: "/core-transaction", description: "Audit and transaction history" },
      ]}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CARE" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {INPATIENT_MODULES.map((mod) => {
          const Icon = mod.icon;
          return (
            <Link
              key={mod.label}
              href={mod.href}
              className="rounded-xl border border-border bg-card p-5 hover:border-primary/25 hover:shadow-md"
            >
              <Icon className="mb-3 h-6 w-6 text-primary" />
              <h3 className="text-sm font-semibold text-foreground">{mod.label}</h3>
              <p className="mt-1 text-xs text-muted-foreground">{mod.description}</p>
            </Link>
          );
        })}
      </div>
    </PlaneWorkspaceShell>
  );
}
