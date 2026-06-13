"use client";

/**
 * Staffing & scheduling hub — roster and appointment operations without clinical shift guard.
 * Route: /organization-admin/staffing | ORGANIZATION_ADMIN
 */

import Link from "next/link";
import { ArrowLeft, ArrowUpRight, CalendarDays, CalendarRange, Megaphone, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";

const STAFFING_DESTINATIONS = [
  {
    href: "/scheduling/roster",
    title: "Staff roster",
    description: "Weekly roster grid and coverage — workspace context required, not an active clinical shift.",
    icon: Users,
  },
  {
    href: "/scheduling/on-call",
    title: "On-call schedule",
    description: "On-call posture for the facility workspace.",
    icon: CalendarRange,
  },
  {
    href: "/scheduling",
    title: "Appointments & booking",
    description: "Live scheduling API — confirm and manage patient appointments for this facility.",
    icon: CalendarDays,
  },
  {
    href: "/scheduling/noticeboard",
    title: "Provider noticeboard",
    description: "Announcements and acknowledgments via `/internal/v1/communication/announcements`.",
    icon: Megaphone,
  },
] as const;

export default function OrganizationStaffingHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Staffing & scheduling"
        subtitle="Workforce and calendar operations distinct from Facility Work queue execution"
      >
        <OrganizationPlaneContextBar preferStore />

        <div className="mb-4">
          <Link
            href="/organization-admin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4" /> Back to organization administration
          </Link>
        </div>

        <p className="mb-4 text-sm text-muted-foreground">
          Select a facility and workspace first (auth guard). These tools are for administrators planning coverage and
          visits — patient queue and chart work remain under Facility Work after shift start.
        </p>

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {STAFFING_DESTINATIONS.map((d) => {
            const Icon = d.icon;
            return (
              <Link
                key={d.href}
                href={`${d.href}?from=organization-admin`}
                className="group flex flex-col rounded-2xl border border-violet-200 bg-card p-5 shadow-sm transition hover:border-violet-400 hover:shadow-md"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-100 text-violet-800">
                    <Icon className="h-5 w-5" />
                  </div>
                  <ArrowUpRight className="h-4 w-4 text-muted-foreground group-hover:text-violet-700" />
                </div>
                <h3 className="mt-3 font-medium text-foreground group-hover:text-violet-900">{d.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{d.description}</p>
              </Link>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}
