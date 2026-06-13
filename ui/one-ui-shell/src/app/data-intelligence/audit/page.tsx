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

export default function DataAuditPage() {
  return (
    <PlaneWorkspaceShell
      title="Audit intelligence"
      subtitle="Transaction audit logs, data access monitoring, and high-risk action trails"
      plane="Data & Intelligence Plane"
      maturity="partial"
      tabs={DATA_TABS}
    >
      <TrustContextBanner purposeOfUse="AUDIT_REVIEW" />
      <div className="flex flex-wrap gap-3">
        <Link href="/admin/audit" className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white">
          Audit trail admin
        </Link>
        <Link href="/core-transaction" className="rounded-lg border px-4 py-2 text-sm hover:bg-background">
          Core transaction history
        </Link>
        <Link href="/admin/data-governance" className="rounded-lg border px-4 py-2 text-sm hover:bg-background">
          Data governance
        </Link>
      </div>
    </PlaneWorkspaceShell>
  );
}
