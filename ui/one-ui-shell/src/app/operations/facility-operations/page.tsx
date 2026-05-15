"use client";

import Link from "next/link";
import { AlertTriangle, Building2, Route, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SURFACES = [
  {
    href: "/operations",
    label: "District View",
    description: "District-level facility operations summary and oversight.",
    Icon: Building2,
    status: "Integrated in One UI",
  },
  {
    href: "/queue",
    label: "Patient Flow",
    description: "Facility queue and patient-flow operations are surfaced through Queue and Clinical routes.",
    Icon: Route,
    status: "Integrated in One UI",
  },
  {
    href: "/enterprise",
    label: "Resource Operations",
    description: "Resource and inventory operations are surfaced through Enterprise and Inventory routes.",
    Icon: Users,
    status: "Integrated in One UI",
  },
];

export default function FacilityOperationsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Facility Operations"
        subtitle="Canonical One UI surfacing for district, flow, and resource operations"
        icon={<Building2 className="h-6 w-6" />}
      >
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <div className="flex items-start gap-2">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              Some historical facility-operations screens remain outside One UI and are tracked as
              activation backlog debt until fully wired.
            </p>
          </div>
        </div>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {SURFACES.map(({ href, label, description, Icon, status }) => (
            <Link
              key={href}
              href={href}
              className="rounded-lg border border-gray-200 bg-white p-5 transition-all hover:border-impilo-400 hover:shadow-sm"
            >
              <div className="mb-2 flex items-center gap-3">
                <div className="rounded-lg bg-slate-100 p-2">
                  <Icon className="h-5 w-5 text-slate-700" />
                </div>
                <h3 className="font-semibold text-gray-900">{label}</h3>
              </div>
              <p className="text-sm text-gray-600">{description}</p>
              <p className="mt-3 text-xs font-medium text-impilo-700">{status}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
