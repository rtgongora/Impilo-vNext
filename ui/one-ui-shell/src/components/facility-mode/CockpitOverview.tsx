"use client";

import Link from "next/link";
import {
  Activity,
  Users,
  LayoutGrid,
  Bed,
  ListChecks,
  Radio,
  ShieldCheck,
  ArrowRight,
} from "lucide-react";
import type { FacilityModeContextEnvelope } from "./types";

/**
 * Facility Mode cockpit overview: home / quick-actions rendered strictly from the
 * FacilityModeContext read-model. No dead buttons — quick actions only link to
 * routes that exist. Aggregate-only visibility hides row-level detail.
 */
export function CockpitOverview({
  facilityId,
  envelope,
}: {
  facilityId: string;
  envelope: FacilityModeContextEnvelope;
}) {
  const { context, liveOps, liveOpsSource } = envelope;
  const { facility, ops, setupState, visibility } = context;
  const aggregateOnly = visibility === "AGGREGATE_ONLY";

  const queueDepth = liveOps?.waiting ?? liveOps?.total ?? ops.queueDepth;
  const openEncounters = liveOps?.inProgress ?? ops.openEncounters;

  const stat = (
    icon: React.ReactNode,
    label: string,
    value: string | number,
    hint?: string,
  ) => (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="mb-2 flex items-center gap-2 text-muted-foreground">
        {icon}
        <span className="text-xs font-medium uppercase tracking-wide">{label}</span>
      </div>
      <div className="text-2xl font-semibold text-foreground">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted-foreground">{hint}</div>}
    </div>
  );

  return (
    <div className="space-y-6">
      {/* Facility header */}
      <div className="rounded-lg border border-border bg-card p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-foreground">{facility.name}</h2>
            <p className="text-sm text-muted-foreground">
              {facility.code ? `${facility.code} · ` : ""}
              {facility.type ?? "Facility"}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {facility.regulatoryStatus && (
              <span className="inline-flex items-center gap-1 rounded-full bg-muted px-3 py-1 text-xs font-medium text-foreground">
                <ShieldCheck className="h-3.5 w-3.5" />
                {facility.regulatoryStatus.replace(/_/g, " ")}
              </span>
            )}
            <span
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                setupState.goLive
                  ? "bg-emerald-500/15 text-emerald-600"
                  : "bg-amber-500/15 text-amber-600"
              }`}
            >
              {setupState.goLive ? "Live" : "Setup in progress"}
            </span>
          </div>
        </div>
        {aggregateOnly && (
          <p className="mt-3 rounded-md bg-amber-500/10 px-3 py-2 text-xs text-amber-700">
            Aggregate-only visibility: row-level operational detail is hidden under
            cross-tenant guard.
          </p>
        )}
      </div>

      {/* Live ops */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {stat(
          <Users className="h-4 w-4" />,
          "Queue depth",
          aggregateOnly ? "—" : queueDepth,
          liveOpsSource === "PCT" ? "Live (PCT)" : undefined,
        )}
        {stat(
          <Activity className="h-4 w-4" />,
          "Open encounters",
          aggregateOnly ? "—" : openEncounters,
        )}
        {stat(
          <Bed className="h-4 w-4" />,
          "Bed occupancy",
          ops.bedOccupancy != null ? `${ops.bedOccupancy}%` : "—",
        )}
        {stat(
          <LayoutGrid className="h-4 w-4" />,
          "Service points",
          facility.servicePoints.length,
        )}
      </div>

      {/* Quick actions — only routes that exist */}
      <div>
        <h3 className="mb-2 text-sm font-medium text-foreground">Quick actions</h3>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <QuickAction
            href={`/facility/${facilityId}/setup`}
            icon={<ListChecks className="h-4 w-4" />}
            label="Continue setup"
            detail={
              setupState.goLive
                ? "Facility is live"
                : "Configure departments, service points and go-live"
            }
          />
          <QuickAction
            href={`/facility/${facilityId}/control-tower`}
            icon={<Radio className="h-4 w-4" />}
            label="Control tower"
            detail="Real-time operations and alerts"
          />
          <QuickAction
            href={`/facility/${facilityId}/departments`}
            icon={<LayoutGrid className="h-4 w-4" />}
            label="Departments & service points"
            detail={`${facility.departments.length} departments · ${facility.servicePoints.length} service points`}
          />
          <QuickAction
            href={`/facility/${facilityId}/regulators`}
            icon={<ShieldCheck className="h-4 w-4" />}
            label="Regulators"
            detail="Councils that regulate this facility"
          />
          <QuickAction
            href={`/facility/${facilityId}`}
            icon={<Activity className="h-4 w-4" />}
            label="Facility details"
            detail="View facility info, workspaces and staff"
          />
        </div>
      </div>
    </div>
  );
}

function QuickAction({
  href,
  icon,
  label,
  detail,
}: {
  href: string;
  icon: React.ReactNode;
  label: string;
  detail: string;
}) {
  return (
    <Link
      href={href}
      className="group flex items-center gap-3 rounded-lg border border-border bg-card p-3 transition hover:border-primary/40 hover:bg-primary-soft/30"
    >
      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-soft text-primary">
        {icon}
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-medium text-foreground">{label}</span>
        <span className="block truncate text-xs text-muted-foreground">{detail}</span>
      </span>
      <ArrowRight className="ml-auto h-4 w-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-primary" />
    </Link>
  );
}
