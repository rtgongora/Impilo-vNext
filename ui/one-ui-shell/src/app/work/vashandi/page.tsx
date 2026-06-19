"use client";

import Link from "next/link";
import { Users, CalendarDays, ClipboardList, Clock, Palmtree, Upload, ShieldAlert, BarChart3 } from "lucide-react";
import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { ADMINISTRATION_SURFACES } from "@/lib/administration-governance/surfaces";
import { vashandiWorkspaceAllowed } from "@/lib/vashandi/access";

const SECTION_LINKS = [
  { href: "/work/vashandi/workforce", label: "Workforce", icon: Users, workspace: "vashandi.workforce_registry" },
  { href: "/work/vashandi/assignments", label: "Assignments", icon: ClipboardList, workspace: "vashandi.assignments" },
  { href: "/work/vashandi/rosters", label: "Rosters", icon: CalendarDays, workspace: "vashandi.rosters" },
  { href: "/work/vashandi/attendance", label: "Attendance", icon: Clock, workspace: "vashandi.my_attendance" },
  { href: "/work/vashandi/leave-availability", label: "Leave", icon: Palmtree, workspace: "vashandi.leave_availability" },
  { href: "/work/vashandi/access-review", label: "Access review", icon: ShieldAlert, workspace: "vashandi.access_review" },
  { href: "/work/vashandi/analytics", label: "Analytics", icon: BarChart3, workspace: "vashandi.analytics" },
  { href: "/work/vashandi/imports", label: "Imports", icon: Upload, workspace: "vashandi.imports" },
];

export default function Page() {
  const surface = ADMINISTRATION_SURFACES.vashandi;
  const { contract } = useSessionExperienceContract();

  return (
    <VashandiShell title={surface.title} subtitle={surface.subtitle} requireDashboard>
      <div className="space-y-6">
        <div className="rounded-xl border border-indigo-100 bg-info-soft/60 px-4 py-3 text-sm text-primary-hover">
          Operational workforce — roster, shift, attendance, leave and access risk. OPA-enforced; not HSC employment authority.
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          {SECTION_LINKS.map((section) => {
            const enabled = contract ? vashandiWorkspaceAllowed(contract, section.workspace) : false;
            return enabled ? (
              <Link
                key={section.href}
                href={section.href}
                className="flex items-start gap-3 rounded-2xl border border-border bg-card p-5 shadow-sm transition hover:border-indigo-300"
              >
                <section.icon className="mt-0.5 h-5 w-5 text-primary" aria-hidden />
                <div>
                  <h4 className="font-medium text-foreground">{section.label}</h4>
                  <p className="mt-1 text-sm text-muted-foreground">Open {section.label.toLowerCase()} workspace</p>
                </div>
              </Link>
            ) : (
              <div key={section.href} className="rounded-2xl border border-dashed border-border bg-muted/20 p-5 opacity-70">
                <h4 className="font-medium text-muted-foreground">{section.label}</h4>
                <p className="mt-1 text-sm text-muted-foreground">Not in your Vashandi workspace scope</p>
              </div>
            );
          })}
        </div>
      </div>
    </VashandiShell>
  );
}
