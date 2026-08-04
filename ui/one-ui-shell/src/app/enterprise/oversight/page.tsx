"use client";

import Link from "next/link";
import { useMemo } from "react";
import { Globe2, ScanLine, Shield, Stethoscope } from "lucide-react";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";
import { NationalRevenueOversightPanel } from "@/components/enterprise/NationalRevenueOversightPanel";
import { useAuthStore } from "@/hooks/useAuthStore";
import { reachableByRole } from "@/lib/auth/route-reachability";

const OVERSIGHT_LINKS = [
  {
    href: "/public-health",
    label: "Public health command",
    description: "Surveillance, campaigns, and site registry for national PH oversight.",
    icon: Stethoscope,
    tone: "border-success/25 bg-success-soft",
  },
  {
    href: "/public-health/surveillance",
    label: "Surveillance & outbreaks",
    description: "Ndila-backed outbreak orchestration and case geography.",
    icon: Globe2,
    tone: "border-sky-200 bg-sky-50",
  },
  {
    // This page's guard is "auth", so every signed-in person reaches it — but /coverage admits
    // only ADMIN. /ruvimbo resolves each caller to the face they can actually open.
    href: "/ruvimbo",
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
    tone: "border-warning/35 bg-warning-soft",
  },
  {
    href: "/imaging/worklist",
    label: "Imaging worklist",
    description: "OROS imaging orders awaiting acquisition or reporting.",
    icon: ScanLine,
    tone: "border-border bg-background",
  },
];

export default function EnterpriseOversightPage() {
  // This route's own guard is "auth", so every signed-in person lands here — but several of
  // the drill-downs below are role-guarded. Offering one to someone it refuses produces a
  // bounce back to /home with no explanation, so only show the ones that will open.
  const hasRole = useAuthStore((s) => s.hasRole);
  const links = useMemo(() => reachableByRole(OVERSIGHT_LINKS, hasRole), [hasRole]);

  return (
    <EnterpriseWorkspaceShell
      title="National enterprise oversight"
      subtitle="Drill-down entry points for public health, coverage, imaging, and multi-facility operations — all BFF-backed routes."
    >
      <div className="mb-6">
        <NationalRevenueOversightPanel />
      </div>
      {links.length === 0 && (
        <p className="rounded-2xl border border-border bg-background p-5 text-sm text-muted-foreground">
          No oversight drill-downs are available to your current roles. This is an access
          boundary, not an empty dataset — ask your administrator for the oversight role you
          need.
        </p>
      )}
      <div className="grid gap-4 md:grid-cols-2">
        {links.map(({ href, label, description, icon: Icon, tone }) => (
          <Link
            key={href}
            href={href}
            className={`block rounded-2xl border p-5 shadow-sm transition hover:shadow-md ${tone}`}
          >
            <div className="flex items-start gap-3">
              <div className="rounded-lg bg-card p-2 shadow-sm">
                <Icon className="h-5 w-5 text-foreground" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-foreground">{label}</h2>
                <p className="mt-1 text-xs text-muted-foreground">{description}</p>
                <span className="mt-2 inline-block text-xs font-medium text-primary-hover">Open →</span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </EnterpriseWorkspaceShell>
  );
}
