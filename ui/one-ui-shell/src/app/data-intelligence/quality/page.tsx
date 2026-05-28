"use client";

import Link from "next/link";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";

const DATA_TABS = [
  { label: "Overview", href: "/data-intelligence" },
  { label: "Quality", href: "/data-intelligence/quality" },
  { label: "Pipelines", href: "/data-intelligence/pipelines" },
  { label: "Integration", href: "/data-intelligence/integration" },
  { label: "Reports", href: "/data-intelligence/reports" },
  { label: "Audit", href: "/data-intelligence/audit" },
];

export default function DataQualityPage() {
  return (
    <PlaneWorkspaceShell
      title="Data quality & completeness"
      subtitle="Missing data indicators, validation errors, and programme-level completeness"
      plane="Data & Intelligence Plane"
      maturity="partial"
      tabs={DATA_TABS}
    >
      <TrustContextBanner purposeOfUse="DATA_QUALITY_REVIEW" />
      <div className="rounded-xl border border-slate-200 p-6">
        <div className="mb-4 flex items-center gap-2">
          <FeatureMaturityBadge status="partial" detail="Registry heuristics via intelligence BFF" />
        </div>
        <p className="text-sm text-slate-600">
          Use Health Intelligence data-quality hints for registry completeness heuristics, or open facility reports for operational completeness views.
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <Link href="/intelligence" className="rounded-lg bg-impilo-500 px-3 py-2 text-sm text-white">
            Data quality hints (Nompilo)
          </Link>
          <Link href="/reports/facility" className="rounded-lg border px-3 py-2 text-sm hover:bg-slate-50">
            Facility reports
          </Link>
        </div>
      </div>
    </PlaneWorkspaceShell>
  );
}
