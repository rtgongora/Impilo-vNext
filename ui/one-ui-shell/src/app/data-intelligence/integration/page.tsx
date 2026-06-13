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

export default function DataIntegrationPage() {
  return (
    <PlaneWorkspaceShell
      title="Integration monitor"
      subtitle="Internal service integrations and external adapter status"
      plane="Data & Intelligence Plane"
      maturity="partial"
      tabs={DATA_TABS}
    >
      <TrustContextBanner purposeOfUse="INTEGRATION_OPS" />
      <p className="text-sm text-muted-foreground">
        Full integration status dashboards live on the admin integration monitor. This hub links operational intelligence to adapter health.
      </p>
      <Link
        href="/admin/integration-status"
        className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
      >
        Open integration status admin
      </Link>
    </PlaneWorkspaceShell>
  );
}
