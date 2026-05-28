"use client";

import Link from "next/link";
import {
  Activity,
  BarChart3,
  Database,
  FileText,
  GitBranch,
  Shield,
} from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";

const DATA_INTEL_MODULES = [
  {
    label: "Data quality",
    description: "Completeness, validation errors, facility-level gaps",
    href: "/data-intelligence/quality",
    icon: Activity,
  },
  {
    label: "Pipelines & events",
    description: "Pipeline health, outbox, and event stream status",
    href: "/data-intelligence/pipelines",
    icon: GitBranch,
  },
  {
    label: "Integration monitor",
    description: "Internal and external adapter sync status",
    href: "/data-intelligence/integration",
    icon: Database,
  },
  {
    label: "Reporting",
    description: "Standard, clinical, and operational reports",
    href: "/data-intelligence/reports",
    icon: FileText,
  },
  {
    label: "Audit intelligence",
    description: "Transaction audit and data access logs",
    href: "/data-intelligence/audit",
    icon: Shield,
  },
  {
    label: "Health intelligence",
    description: "Nompilo-assisted fused search and insights",
    href: "/intelligence",
    icon: BarChart3,
  },
];

const DATA_TABS = [
  { label: "Overview", href: "/data-intelligence" },
  { label: "Quality", href: "/data-intelligence/quality" },
  { label: "Pipelines", href: "/data-intelligence/pipelines" },
  { label: "Integration", href: "/data-intelligence/integration" },
  { label: "Reports", href: "/data-intelligence/reports" },
  { label: "Audit", href: "/data-intelligence/audit" },
];

export default function DataIntelligenceOverviewPage() {
  return (
    <PlaneWorkspaceShell
      title="Data & intelligence"
      subtitle="Data quality, pipelines, integration, reporting, analytics, audit, and Nompilo insights"
      plane="Data & Intelligence Plane"
      maturity="partial"
      tabs={DATA_TABS}
      nompiloHint="Ask Nompilo to summarize data quality issues, integration gaps, or report outputs."
      relatedLinks={[
        { label: "Production Command Centre", href: "/production-command-centre" },
        { label: "Data governance", href: "/admin/data-governance" },
        { label: "Platform monitor", href: "/platform-journey" },
      ]}
    >
      <TrustContextBanner purposeOfUse="DATA_ANALYTICS" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {DATA_INTEL_MODULES.map((mod) => {
          const Icon = mod.icon;
          return (
            <Link
              key={mod.href}
              href={mod.href}
              className="rounded-xl border border-slate-200 bg-white p-5 hover:border-impilo-200 hover:shadow-md"
            >
              <Icon className="mb-3 h-6 w-6 text-impilo-500" />
              <h3 className="text-sm font-semibold text-slate-900">{mod.label}</h3>
              <p className="mt-1 text-xs text-slate-500">{mod.description}</p>
            </Link>
          );
        })}
      </div>
    </PlaneWorkspaceShell>
  );
}
