"use client";

/**
 * Reports Hub — Card grid for report categories.
 * Route: /reports
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FileText, Building2, BarChart3, Wrench } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";

const REPORT_CATEGORIES = [
  {
    title: "Clinical Reports",
    description: "Patient census, diagnosis summary, lab tests, prescriptions",
    href: "/reports/clinical",
    icon: FileText,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "Facility Reports",
    description: "Bed occupancy, resource utilization, staff attendance",
    href: "/reports/facility",
    icon: Building2,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Operational Reports",
    description: "Queue wait times, encounter duration, patient flow",
    href: "/reports/operational",
    icon: BarChart3,
    color: "bg-amber-100 text-amber-600",
  },
  {
    title: "Custom Reports",
    description: "Build custom reports with flexible parameters and filters",
    href: "/reports/custom",
    icon: Wrench,
    color: "bg-green-100 text-green-600",
  },
];

export default function ReportsHubPage() {
  const searchParams = useSearchParams();
  const fromOrg = searchParams.get("from") === "organization-admin";
  const withPlane = (href: string) => (fromOrg ? `${href}?from=organization-admin` : href);

  return (
    <AppLayout>
      <PageShell title="Reports" subtitle="Generate and view reports across your facility">
        <OrganizationPlaneContextBar />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {REPORT_CATEGORIES.map((cat) => (
            <Link
              key={cat.href}
              href={withPlane(cat.href)}
              className="bg-white rounded-lg border border-gray-200 p-5 hover:border-blue-300 hover:shadow-md transition-all group"
            >
              <div className="flex items-start gap-4">
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 ${cat.color}`}>
                  <cat.icon className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-medium text-gray-900 group-hover:text-blue-700">{cat.title}</h3>
                  <p className="text-xs text-gray-500 mt-1">{cat.description}</p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
