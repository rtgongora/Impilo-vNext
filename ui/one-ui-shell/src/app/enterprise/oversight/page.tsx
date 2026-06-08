"use client";

import Link from "next/link";
import { Globe2, ScanLine, Shield, Stethoscope } from "lucide-react";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";
import { NationalRevenueOversightPanel } from "@/components/enterprise/NationalRevenueOversightPanel";

const OVERSIGHT_LINKS = [
  {
    href: "/public-health",
    label: "Public health command",
    description: "Surveillance, campaigns, and site registry for national PH oversight.",
    icon: Stethoscope,
    tone: "border-emerald-200 bg-emerald-50",
  },
  {
    href: "/public-health/surveillance",
    label: "Surveillance & outbreaks",
    description: "Ndila-backed outbreak orchestration and case geography.",
    icon: Globe2,
    tone: "border-sky-200 bg-sky-50",
  },
  {
    href: "/coverage",
    label: "Coverage operations",
    description: "Schemes, eligibility, claims, settlement, and payer intelligence.",
    icon: Shield,
    tone: "border-violet-200 bg-violet-50",
  },
  {
    href: "/imaging/facility",
    label: "Facility imaging dashboard",
    description: "PACS worklist and imaging ops summary for contracted facilities.",
    icon: ScanLine,
    tone: "border-cyan-200 bg-cyan-50",
  },
  {
    href: "/operations/facility-operations/district-view",
    label: "District & national queue view",
    description: "Multi-facility queue pressure without synthetic KPIs.",
    icon: Globe2,
    tone: "border-amber-200 bg-amber-50",
  },
  {
    href: "/imaging/worklist",
    label: "Imaging worklist",
    description: "OROS imaging orders awaiting acquisition or reporting.",
    icon: ScanLine,
    tone: "border-slate-200 bg-slate-50",
  },
];

export default function EnterpriseOversightPage() {
  return (
    <EnterpriseWorkspaceShell
      title="National enterprise oversight"
      subtitle="Drill-down entry points for public health, coverage, imaging, and multi-facility operations — all BFF-backed routes."
    >
      <div className="mb-6">
        <NationalRevenueOversightPanel />
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {OVERSIGHT_LINKS.map(({ href, label, description, icon: Icon, tone }) => (
          <Link
            key={href}
            href={href}
            className={`block rounded-2xl border p-5 shadow-sm transition hover:shadow-md ${tone}`}
          >
            <div className="flex items-start gap-3">
              <div className="rounded-lg bg-white p-2 shadow-sm">
                <Icon className="h-5 w-5 text-slate-700" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-slate-900">{label}</h2>
                <p className="mt-1 text-xs text-slate-600">{description}</p>
                <span className="mt-2 inline-block text-xs font-medium text-impilo-700">Open →</span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </EnterpriseWorkspaceShell>
  );
}
