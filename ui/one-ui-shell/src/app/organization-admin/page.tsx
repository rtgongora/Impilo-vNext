"use client";

/**
 * Organization Administration landing — facility/enterprise ops (not sovereign registry).
 * Route: /organization-admin | operational context: organization_admin
 */

import { useEffect, useMemo } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  ArrowUpRight,
  ClipboardList,
  Cpu,
  FileBarChart2,
  Settings,
  Shield,
  Users,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PlaneTrustBanner } from "@/components/experience/PlaneTrustBanner";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import type { OrganizationPlaneCard } from "@/lib/plane-landings";
import { ORGANIZATION_PLANE_CARDS } from "@/lib/plane-landings";
import {
  isOrganizationAdminSurface,
  principalHasOrganizationAdminPlane,
} from "@/lib/operational-context";

const SURFACE_ICONS = {
  finance_revenue: Wallet,
  facility_admin: Shield,
  service_point_admin: Cpu,
  staffing_roster: Users,
  approvals: ClipboardList,
  reports: FileBarChart2,
  settings: Settings,
} as const;

function isFinanceOnlyOperator(roles: string[]) {
  return (
    roles.includes("FINANCE") &&
    !roles.some((r) => ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"].includes(r))
  );
}

export default function OrganizationAdminLandingPage() {
  const searchParams = useSearchParams();
  const rehydrate = useOperationalContextStore((s) => s.rehydrateFromSession);
  const user = useAuthStore((s) => s.user);
  const roles = useMemo(() => user?.roles ?? [], [user?.roles]);

  const displayCards = useMemo((): OrganizationPlaneCard[] => {
    if (isFinanceOnlyOperator(roles)) {
      return ORGANIZATION_PLANE_CARDS.filter((c) =>
        ["finance_revenue", "reports", "settings"].includes(c.surface),
      );
    }
    return ORGANIZATION_PLANE_CARDS;
  }, [roles]);

  useEffect(() => {
    const u = useAuthStore.getState().user;
    if (principalHasOrganizationAdminPlane(u)) {
      useOperationalContextStore.getState().setOperationalMode("organization_admin");
    }
  }, []);

  useEffect(() => {
    const raw = searchParams.get("surface");
    if (raw && isOrganizationAdminSurface(raw)) {
      useOperationalContextStore.getState().setOrganizationAdminSurface(raw);
    }
  }, [searchParams]);

  return (
    <AppLayout>
      <PageShell
        title="Organization administration"
        subtitle="Facility and enterprise operations — separate from registry governance"
      >
        <div className="space-y-6">
          <Link
            href="/home"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" /> Back to home
          </Link>

          <PlaneTrustBanner
            variant="organization"
            eyebrow="Operational administration"
            title="You are in the organization administration context"
          >
            <p>
              Configure how <strong>this organization and its facilities</strong> run day to day: devices,
              policies, reporting, and local settings. Sovereign registry changes belong in{" "}
              <Link href="/registry-admin" className="font-medium underline-offset-2 hover:underline">
                Registry administration
              </Link>
              , not here.
            </p>
          </PlaneTrustBanner>

          {isFinanceOnlyOperator(roles) && (
            <div className="rounded-xl border border-emerald-200 bg-emerald-50/90 px-4 py-3 text-sm text-emerald-950">
              <strong>Finance-focused entry:</strong> admin routes that require{" "}
              <code className="rounded bg-white/80 px-1">FACILITY_ADMIN</code> or{" "}
              <code className="rounded bg-white/80 px-1">SYSTEM_ADMIN</code> are hidden below. Use{" "}
              <Link href="/finance" className="font-medium underline">
                Finance
              </Link>{" "}
              for revenue-cycle work.
            </div>
          )}

          <div>
            <h3 className="text-sm font-semibold text-gray-900">Administrative surfaces</h3>
            <p className="mt-1 text-xs text-gray-500">
              Each card opens a live route or hub backed by existing APIs. Your surface focus is remembered for this
              session (
              <button
                type="button"
                className="text-violet-700 hover:underline"
                onClick={() => {
                  useOperationalContextStore.getState().setOrganizationAdminSurface(null);
                  rehydrate();
                }}
              >
                clear surface
              </button>
              ).
            </p>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              {displayCards.map((card) => {
                const Icon = SURFACE_ICONS[card.surface];
                const href = `${card.href}${card.href.includes("?") ? "&" : "?"}from=organization-admin`;
                return (
                  <Link
                    key={`${card.surface}:${card.href}`}
                    href={href}
                    onClick={() =>
                      useOperationalContextStore.getState().setOrganizationAdminSurface(card.surface)
                    }
                    className="group flex flex-col rounded-2xl border border-violet-200/80 bg-white p-5 shadow-sm transition hover:border-violet-400 hover:shadow-md"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-start gap-3">
                        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-violet-800">
                          <Icon className="h-5 w-5" />
                        </div>
                        <div>
                          <h4 className="font-medium text-gray-900 group-hover:text-violet-900">{card.title}</h4>
                          <p className="mt-1 text-sm text-gray-600">{card.description}</p>
                        </div>
                      </div>
                      <ArrowUpRight className="h-4 w-4 shrink-0 text-gray-400 group-hover:text-violet-700" />
                    </div>
                  </Link>
                );
              })}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
