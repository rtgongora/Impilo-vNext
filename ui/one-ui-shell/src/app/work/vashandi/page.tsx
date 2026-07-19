"use client";

import Link from "next/link";
import { Users, CalendarDays, ClipboardList, Clock, GraduationCap, Palmtree, Upload, ShieldAlert, BarChart3, RadioTower, KeyRound, ArrowRight } from "lucide-react";
import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { ADMINISTRATION_SURFACES } from "@/lib/administration-governance/surfaces";
import { vashandiWorkspaceAllowed } from "@/lib/vashandi/access";

const SECTION_LINKS = [
  { href: "/work/vashandi/workforce", label: "Workforce", icon: Users, workspace: "vashandi.workforce_registry" },
  { href: "/work/vashandi/assignments", label: "Assignments", icon: ClipboardList, workspace: "vashandi.assignments" },
  { href: "/work/vashandi/rosters", label: "Rosters", icon: CalendarDays, workspace: "vashandi.rosters" },
  { href: "/work/vashandi/on-call", label: "On-call pools", icon: RadioTower, workspace: "vashandi.rosters" },
  { href: "/work/vashandi/attendance", label: "Attendance", icon: Clock, workspace: "vashandi.my_attendance" },
  { href: "/work/vashandi/leave-availability", label: "Leave", icon: Palmtree, workspace: "vashandi.leave_availability" },
  { href: "/work/vashandi/access-review", label: "Access review", icon: ShieldAlert, workspace: "vashandi.access_review" },
  { href: "/work/vashandi/training-requirements", label: "Training requirements", icon: GraduationCap, workspace: "vashandi.organisation_workforce" },
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

        {/* Identity-program integration (D-P7): how identity becomes an operative posting. */}
        <div className="rounded-2xl border border-border bg-card p-4">
          <div className="flex items-start gap-3">
            <KeyRound className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden />
            <div className="min-w-0">
              <h4 className="text-sm font-semibold text-foreground">
                How work access is granted here
              </h4>
              <p className="mt-1 text-sm text-muted-foreground">
                A registered professional (Varapi) does not, by itself, authorise work. A facility
                access request, once approved, <span className="font-medium text-foreground">materialises an assignment</span> here with an
                engagement type and validity window. Starting a <span className="font-medium text-foreground">work session</span> proves that
                assignment and mints a short-lived work-context token; when the assignment expires,
                the sweep ends it and the matching token is torn down. Identity → assignment →
                session → expiry — one governed chain.
              </p>
              <Link
                href="/provider/workplace"
                className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
              >
                Start or switch a work session <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          </div>
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
