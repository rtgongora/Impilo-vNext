"use client";

/**
 * Facility & ward administration hub — concrete admin routes for org operators.
 * Route: /organization-admin/facility | ORGANIZATION_ADMIN
 */

import Link from "next/link";
import { ArrowLeft, ArrowUpRight, BedDouble, ClipboardList, LayoutDashboard, Users, ScrollText, Plug } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";

const FACILITY_DESTINATIONS = [
  {
    href: "/admin/beds",
    title: "Beds & wards",
    description: "Capacity, admissions handoff, and ward operations (`/admin/beds`).",
    icon: BedDouble,
  },
  {
    href: "/admin/queues",
    title: "Queue configuration",
    description: "Templates and flow for facility queues (`/admin/queues`).",
    icon: ClipboardList,
  },
  {
    href: "/admin/users",
    title: "Users & access",
    description: "Directory and account lifecycle (`/admin/users`).",
    icon: Users,
  },
  {
    href: "/admin/roles",
    title: "Roles",
    description: "Role bundles aligned to Keycloak (`/admin/roles`).",
    icon: Users,
  },
  {
    href: "/admin/audit",
    title: "Audit trail",
    description: "Immutable operational audit stream (`/admin/audit`).",
    icon: ScrollText,
  },
  {
    href: "/admin/integration-status",
    title: "Integrations",
    description: "Interface health for this tenant (`/admin/integration-status`).",
    icon: Plug,
  },
  {
    href: "/admin",
    title: "Administration overview",
    description: "Full admin home with every governance tile (`/admin`).",
    icon: LayoutDashboard,
  },
] as const;

export default function OrganizationFacilityHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Facility & ward administration"
        subtitle="Operational configuration for the selected facility — each tile is an existing admin route"
      >
        <OrganizationPlaneContextBar preferStore />

        <div className="mb-4">
          <Link
            href="/organization-admin"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" /> Back to organization administration
          </Link>
        </div>

        <p className="mb-4 text-sm text-gray-600">
          This hub replaces a vague jump to <code className="rounded bg-gray-100 px-1 text-xs">/admin</code> alone.
          Pick the subsystem you need; all destinations require admin roles (see route registry).
        </p>

        <div className="grid gap-4 md:grid-cols-2">
          {FACILITY_DESTINATIONS.map((d) => {
            const Icon = d.icon;
            return (
              <Link
                key={d.href}
                href={`${d.href}?from=organization-admin`}
                className="group flex flex-col rounded-2xl border border-violet-200 bg-white p-5 shadow-sm transition hover:border-violet-400 hover:shadow-md"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-100 text-violet-800">
                    <Icon className="h-5 w-5" />
                  </div>
                  <ArrowUpRight className="h-4 w-4 text-gray-400 group-hover:text-violet-700" />
                </div>
                <h3 className="mt-3 font-medium text-gray-900 group-hover:text-violet-900">{d.title}</h3>
                <p className="mt-1 text-sm text-gray-600">{d.description}</p>
              </Link>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}
