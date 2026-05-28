"use client";

/**
 * Clinical Care Hub — entry point for clinical workflows.
 * Route: /clinical
 */

import Link from "next/link";
import {
  Ambulance,
  BedDouble,
  ClipboardList,
  DoorOpen,
  FlaskConical,
  Gauge,
  MessageSquare,
  Pill,
  Scan,
  Stethoscope,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { NompiloContextPanel } from "@/components/intelligent/NompiloContextPanel";
import { RelatedServicesPanel } from "@/components/workspace/RelatedServicesPanel";

const CLINICAL_RELATED_LINKS = [
  { label: "Inpatient care", href: "/clinical/inpatient", description: "Admissions, ward board, nursing" },
  { label: "Rx transaction journey", href: "/pharmacy/transaction-journey", description: "Rx · pay · dispatch demo path" },
  { label: "Core transactions", href: "/core-transaction", description: "Transaction audit and state history" },
  { label: "Data & intelligence", href: "/data-intelligence", description: "Quality, pipelines, and audit intel" },
];

const CLINICAL_MODULES = [
  { label: "My Dashboard", description: "Worklist, tasks, and alerts", href: "/queue", icon: ClipboardList, color: "bg-impilo-100 text-impilo-500" },
  { label: "Patient Queue", description: "Waiting patients and triage", href: "/queue", icon: Users, color: "bg-orange-100 text-orange-600" },
  { label: "ED / Casualty", description: "Emergency activations on live BFF", href: "/clinical/emergency", icon: Ambulance, color: "bg-red-100 text-red-700" },
  { label: "Inpatient workspace", description: "Admissions, ward board, rounds, discharge", href: "/clinical/inpatient", icon: BedDouble, color: "bg-purple-100 text-purple-700", clinical: true },
  { label: "Patient encounters", description: "Clinical documentation and care", href: "/queue/search", icon: Stethoscope, color: "bg-impilo-100 text-impilo-500", clinical: true },
  { label: "Nursing workbench", description: "Assigned patients and nursing tasks", href: "/clinical/inpatient/nursing", icon: Users, color: "bg-teal-100 text-teal-700", clinical: true },
  { label: "Medical rounds", description: "Ward round notes and reviews", href: "/clinical/inpatient/rounds", icon: Stethoscope, color: "bg-cyan-100 text-cyan-700", clinical: true },
  { label: "Orders & results", description: "Lab worklist and results", href: "/lab", icon: FlaskConical, color: "bg-blue-100 text-blue-700", clinical: true },
  { label: "Rx / pharmacy", description: "Prescriptions and dispensing", href: "/pharmacy", icon: Pill, color: "bg-green-100 text-green-700", clinical: true },
  { label: "PACS / imaging", description: "Imaging studies via patient chart", href: "/queue/search", icon: Scan, color: "bg-indigo-100 text-indigo-700", clinical: true },
  { label: "Discharge", description: "Discharge planning and summaries", href: "/clinical/inpatient/admissions", icon: DoorOpen, color: "bg-amber-100 text-amber-600", clinical: true },
  { label: "Control tower", description: "Real-time facility operations", href: "/clinical/control-tower", icon: Gauge, color: "bg-rose-100 text-rose-600" },
  { label: "Communication", description: "Messages and notifications", href: "/home/notifications", icon: MessageSquare, color: "bg-impilo-100 text-impilo-500" },
];

export default function ClinicalHubPage() {
  return (
    <AppLayout>
      <PageShell title="Clinical Care" subtitle="Outpatient, inpatient, emergency, orders, Rx, and clinical operations">
        <div className="space-y-4">
          <TrustContextBanner purposeOfUse="CLINICAL_CARE" />
        <div className="grid gap-6 lg:grid-cols-[1fr_260px]">
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
            {CLINICAL_MODULES.map((mod) => {
              const Icon = mod.icon;
              return (
                <Link
                  key={mod.label}
                  href={mod.href}
                  className="group rounded-lg border border-gray-200 bg-white p-5 text-center hover:border-impilo-200 hover:shadow-md"
                >
                  <div className={`mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-lg ${mod.color}`}>
                    <Icon className="h-6 w-6" />
                  </div>
                  <h3 className="text-sm font-medium text-gray-900 group-hover:text-impilo-500">{mod.label}</h3>
                  <p className="mt-1 text-xs text-gray-500">{mod.description}</p>
                </Link>
              );
            })}
          </div>
          <div className="space-y-4">
            <RelatedServicesPanel links={CLINICAL_RELATED_LINKS} />
            <NompiloContextPanel plane="Clinical Plane" hint="Ask Nompilo to find a patient, start an encounter, or guide inpatient discharge." />
          </div>
        </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
