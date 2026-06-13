"use client";

import Link from "next/link";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";

const DATA_TABS = [
  { label: "Overview", href: "/data-intelligence" },
  { label: "Quality", href: "/data-intelligence/quality" },
  { label: "Pipelines", href: "/data-intelligence/pipelines" },
  { label: "Integration", href: "/data-intelligence/integration" },
  { label: "Reports", href: "/data-intelligence/reports" },
  { label: "Audit", href: "/data-intelligence/audit" },
];

export default function DataReportsHubPage() {
  return (
    <PlaneWorkspaceShell
      title="Reporting hub"
      subtitle="Clinical, facility, operational, and finance reports"
      plane="Data & Intelligence Plane"
      maturity="partial"
      tabs={DATA_TABS}
    >
      <TrustContextBanner purposeOfUse="REPORTING" />
      <div className="grid gap-3 sm:grid-cols-2">
        {[
          { label: "All reports", href: "/reports" },
          { label: "Clinical reports", href: "/reports/clinical" },
          { label: "Facility reports", href: "/reports/facility" },
          { label: "Operational reports", href: "/reports/operational" },
          { label: "Financial reports", href: "/finance/reports" },
          { label: "Learning reports", href: "/learning/reports" },
        ].map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className="rounded-xl border border-border px-4 py-3 text-sm font-medium text-foreground hover:border-primary/25 hover:bg-primary-soft"
          >
            {item.label}
          </Link>
        ))}
      </div>
    </PlaneWorkspaceShell>
  );
}
