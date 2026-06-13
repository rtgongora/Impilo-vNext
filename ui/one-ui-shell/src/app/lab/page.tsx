"use client";

/**
 * Lab Hub — absorbs oros-web sidecar
 * Multi-type order orchestration: worklists, results, catalog, reconciliation.
 * Route: /lab | Guard: shift (clinical staff)
 */

import Link from "next/link";
import { useState } from "react";
import {
  FlaskConical,
  ListChecks,
  FileSearch,
  BookOpen,
  RefreshCcw,
  Clock,
  AlertCircle,
  CheckCircle2,
  Loader2,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useLabWorklist } from "@/hooks/queries/useLabWorklist";

const ORDER_TYPES = [
  { value: "LAB", label: "Laboratory" },
  { value: "IMAGING", label: "Imaging" },
  { value: "PHARMACY", label: "Pharmacy" },
  { value: "BLOOD", label: "Blood" },
] as const;

const SECTIONS = [
  {
    href: "/lab/worklist",
    label: "Worklist",
    description: "Pending and in-progress orders for this facility",
    Icon: ListChecks,
    queryKey: "type",
  },
  {
    href: "/lab/results",
    label: "Results Review",
    description: "Review and authorize order results",
    Icon: FileSearch,
  },
  {
    href: "/lab/catalog",
    label: "Test Catalog",
    description: "Available orderable items and capabilities",
    Icon: BookOpen,
    queryKey: "order_type",
  },
  {
    href: "/lab/reconciliation",
    label: "Reconciliation",
    description: "Reconcile hybrid orders with external LIS",
    Icon: RefreshCcw,
  },
] as const;

export default function LabPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [orderType, setOrderType] = useState("LAB");

  const worklistQ = useLabWorklist({
    facilityId: facility?.id,
    type: orderType,
    size: 50,
  });

  const summary = worklistQ.data?.data?.summary;

  const summaryCards = [
    {
      label: "Pending",
      value: String(summary?.pending_collection ?? 0),
      Icon: Clock,
      color: "bg-warning-soft text-amber-600",
    },
    {
      label: "In Progress",
      value: String(summary?.in_progress ?? 0),
      Icon: ListChecks,
      color: "bg-primary-soft text-primary",
    },
    {
      label: "Completed",
      value: String(summary?.completed ?? 0),
      Icon: CheckCircle2,
      color: "bg-green-50 text-green-600",
    },
    {
      label: "Urgent",
      value: String(summary?.urgent ?? 0),
      Icon: AlertCircle,
      color: "bg-danger-soft text-red-600",
    },
  ];

  function sectionHref(section: (typeof SECTIONS)[number]): string {
    if (!("queryKey" in section) || !section.queryKey) {
      return section.href;
    }
    const param = section.queryKey === "order_type" ? "order_type" : "type";
    return `${section.href}?${param}=${orderType}`;
  }

  return (
    <AppLayout>
      <PageShell
        title="Order Hub"
        subtitle="Multi-type order orchestration — lab, imaging, pharmacy, and blood"
        icon={<FlaskConical className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <FeatureMaturityBadge
            status="live"
            detail="OROS worklist summaries via /internal/v1/lab-worklists with type filters"
          />

          <div className="flex flex-wrap gap-2">
            {ORDER_TYPES.map(({ value, label }) => (
              <button
                key={value}
                type="button"
                onClick={() => setOrderType(value)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  orderType === value
                    ? "bg-violet-600 text-white"
                    : "border border-border text-foreground hover:bg-background"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          {!facility?.id ? (
            <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
              Select a facility to load order worklist summaries for this shift.
            </div>
          ) : worklistQ.isLoading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading {orderType} worklist summary…
            </div>
          ) : worklistQ.isError ? (
            <div className="rounded-lg border border-danger/28 bg-danger-soft p-4 text-sm text-red-800">
              Unable to load worklist summary for {orderType} orders.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              {summaryCards.map(({ label, value, Icon, color }) => (
                <div key={label} className="rounded-lg border border-border bg-card p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm text-muted-foreground">{label}</span>
                    <div className={`rounded-lg p-1.5 ${color.split(" ")[0]}`}>
                      <Icon className={`h-4 w-4 ${color.split(" ")[1]}`} />
                    </div>
                  </div>
                  <p className="text-2xl font-bold text-foreground">{value}</p>
                </div>
              ))}
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {SECTIONS.map((section) => (
              <Link
                key={section.href}
                href={sectionHref(section)}
                className="rounded-lg border border-border bg-card p-5 hover:border-impilo-400 hover:shadow-sm transition-all"
              >
                <div className="flex items-center gap-3 mb-2">
                  <div className="rounded-lg bg-violet-50 p-2">
                    <section.Icon className="h-5 w-5 text-violet-600" />
                  </div>
                  <h3 className="font-semibold text-foreground">{section.label}</h3>
                </div>
                <p className="text-sm text-muted-foreground">{section.description}</p>
              </Link>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
