"use client";

/**
 * Nhume Operations Dashboard
 *
 * Operational snapshot of dispatch & delivery: counts of in-flight work, fleet
 * & courier availability, SLA-at-risk panel, cold-chain alerts and chain of
 * custody exceptions. The map provider banner is read from Ndila via the
 * backend so this page never hard-codes a single map vendor.
 */

import Link from "next/link";
import {
  LayoutDashboard,
  PackagePlus,
  AlertTriangle,
  Snowflake,
  ShieldAlert,
  Clock,
  Loader2,
  Truck,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip, NhumePriorityChip } from "@/components/nhume/NhumeStatusChip";
import { useNhumeDashboard } from "@/hooks/useNhume";
import { UnifiedLogisticsMapPanel } from "@/components/operations/UnifiedLogisticsMapPanel";
import { DispatchDeliveryOrchestrationPanel } from "@/components/operations/DispatchDeliveryOrchestrationPanel";

const COUNTER_LAYOUT = [
  { key: "total_today", label: "Total Today" },
  { key: "pending_dispatch", label: "Pending Dispatch" },
  { key: "active", label: "Active" },
  { key: "delayed", label: "Delayed" },
  { key: "failed", label: "Failed" },
  { key: "completed_today", label: "Completed Today" },
  { key: "high_priority", label: "High Priority" },
  { key: "cold_chain_alerts", label: "Cold-Chain Alerts" },
  { key: "custody_exceptions", label: "Custody Exceptions" },
];

export default function NhumeDashboardPage() {
  const { data, isPending, isError, error } = useNhumeDashboard();

  return (
    <AppLayout>
      <PageShell
        title="Nhume Operations Dashboard"
        subtitle="Live state of dispatch, fleet and delivery across the tenant"
        icon={<LayoutDashboard className="h-6 w-6" />}
      >
        <DispatchDeliveryOrchestrationPanel />

        <div className="flex items-center justify-between mb-4">
          <div className="text-sm text-gray-500">
            {data?.generated_at && (
              <>Generated at {new Date(data.generated_at).toLocaleString()}</>
            )}
          </div>
          <Link
            href="/nhume/deliveries/new"
            className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 transition-colors"
          >
            <PackagePlus className="h-4 w-4" />
            New Delivery
          </Link>
        </div>

        {isPending && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading dashboard…
          </div>
        )}

        {isError && (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            Dashboard could not be loaded.{" "}
            {(error as { error?: { message?: string } })?.error?.message ?? "Please retry shortly."}
          </div>
        )}

        {data && (
          <div className="space-y-6">
            {/* Map provider banner */}
            <div className="rounded-2xl border border-teal-100 bg-teal-50 p-4 text-sm text-teal-900 flex items-center justify-between">
              <div>
                <strong>Map provider:</strong> {data.map_provider?.provider ?? "Ndila"}
                {data.map_provider?.simulated && (
                  <span className="ml-2 inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-[11px] text-amber-800">
                    Simulation
                  </span>
                )}
              </div>
              <Link href="/nhume/map" className="text-teal-700 hover:text-teal-900 font-medium">
                Open fleet map →
              </Link>
            </div>

            <UnifiedLogisticsMapPanel
              title="Fleet & delivery map"
              subtitle="Live Nhume and dispatch assets on Ndila tiles"
              nhumeDeliveryStatus="ACTIVE"
            />

            {/* Counters */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
              {COUNTER_LAYOUT.map(({ key, label }) => (
                <div key={key} className="rounded-2xl border border-gray-200 bg-white p-4">
                  <div className="text-xs uppercase tracking-wide text-gray-500">{label}</div>
                  <div className="mt-2 text-2xl font-bold text-gray-900">
                    {data.counters?.[key] ?? 0}
                  </div>
                </div>
              ))}
            </div>

            {/* Fleet + couriers */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <SnapshotCard
                title="Fleet snapshot"
                icon={<Truck className="h-5 w-5 text-teal-600" />}
                items={[
                  ["Available", data.fleet?.available ?? 0],
                  ["In use", data.fleet?.in_use ?? 0],
                  ["Maintenance", data.fleet?.maintenance ?? 0],
                  ["Offline", data.fleet?.offline ?? 0],
                  ["Total", data.fleet?.total ?? 0],
                ]}
                href="/nhume/fleet"
              />
              <SnapshotCard
                title="Courier snapshot"
                icon={<Users className="h-5 w-5 text-teal-600" />}
                items={[
                  ["Available", data.couriers?.available ?? 0],
                  ["On duty", data.couriers?.on_duty ?? 0],
                  ["Off duty", data.couriers?.off_duty ?? 0],
                  ["Suspended", data.couriers?.suspended ?? 0],
                  ["Total", data.couriers?.total ?? 0],
                ]}
                href="/nhume/couriers"
              />
            </div>

            {/* SLA at risk */}
            <div className="rounded-2xl border border-gray-200 bg-white">
              <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
                <div className="flex items-center gap-2">
                  <Clock className="h-5 w-5 text-amber-600" />
                  <h3 className="font-semibold text-gray-900">SLA breaches & at-risk</h3>
                </div>
                <Link href="/nhume/dispatcher" className="text-sm text-teal-700 hover:text-teal-900">
                  Open dispatcher →
                </Link>
              </div>
              {data.sla_at_risk?.length ? (
                <ul className="divide-y divide-gray-100">
                  {data.sla_at_risk.slice(0, 8).map((d) => (
                    <li key={d.delivery_id} className="flex items-center justify-between px-5 py-3">
                      <Link href={`/nhume/deliveries/${d.delivery_id}`} className="font-medium text-gray-900 hover:text-teal-700">
                        {d.reference}
                      </Link>
                      <div className="flex items-center gap-2">
                        <NhumePriorityChip priority={String(d.priority ?? "")} />
                        <NhumeStatusChip status={String(d.status)} />
                        {d.sla_due_at && (
                          <span className="text-xs text-gray-500">
                            due {new Date(d.sla_due_at).toLocaleTimeString()}
                          </span>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState text="No SLA risk right now." />
              )}
            </div>

            {/* Cold chain + custody panels */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <AlertPanel
                title="Cold-chain alerts"
                icon={<Snowflake className="h-5 w-5 text-sky-600" />}
                items={data.cold_chain_alerts ?? []}
                emptyText="No cold-chain breaches recorded."
              />
              <AlertPanel
                title="Chain-of-custody exceptions"
                icon={<ShieldAlert className="h-5 w-5 text-rose-600" />}
                items={data.custody_exceptions ?? []}
                emptyText="No custody exceptions raised."
              />
            </div>

            {/* Recent events */}
            <div className="rounded-2xl border border-gray-200 bg-white">
              <div className="border-b border-gray-100 px-5 py-4 flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-gray-500" />
                <h3 className="font-semibold text-gray-900">Recent events</h3>
              </div>
              {data.recent_events?.length ? (
                <ul className="divide-y divide-gray-100">
                  {data.recent_events.slice(0, 10).map((event, idx) => {
                    const ev = event as Record<string, unknown>;
                    const at = ev.recorded_at ? new Date(String(ev.recorded_at)).toLocaleString() : "";
                    return (
                      <li key={idx} className="px-5 py-3 text-sm">
                        <div className="flex justify-between">
                          <span className="font-medium text-gray-900">{String(ev.event_type ?? "event")}</span>
                          <span className="text-xs text-gray-500">{at}</span>
                        </div>
                        {ev.summary ? (
                          <p className="text-xs text-gray-600 mt-1">{String(ev.summary)}</p>
                        ) : null}
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <EmptyState text="No recent events." />
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function SnapshotCard({
  title,
  icon,
  items,
  href,
}: {
  title: string;
  icon: React.ReactNode;
  items: Array<[string, number]>;
  href: string;
}) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          {icon}
          <h3 className="font-semibold text-gray-900">{title}</h3>
        </div>
        <Link href={href} className="text-sm text-teal-700 hover:text-teal-900">
          View →
        </Link>
      </div>
      <dl className="grid grid-cols-2 gap-y-2 text-sm">
        {items.map(([label, value]) => (
          <div key={label} className="flex justify-between border-b border-gray-100 pb-2">
            <dt className="text-gray-500">{label}</dt>
            <dd className="font-medium text-gray-900">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function AlertPanel({
  title,
  icon,
  items,
  emptyText,
}: {
  title: string;
  icon: React.ReactNode;
  items: Array<Record<string, unknown>>;
  emptyText: string;
}) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="border-b border-gray-100 px-5 py-4 flex items-center gap-2">
        {icon}
        <h3 className="font-semibold text-gray-900">{title}</h3>
      </div>
      {items.length === 0 ? (
        <EmptyState text={emptyText} />
      ) : (
        <ul className="divide-y divide-gray-100">
          {items.slice(0, 6).map((item, idx) => (
            <li key={idx} className="px-5 py-3 text-sm">
              <div className="font-medium text-gray-900">{String(item.delivery_reference ?? item.reference ?? "delivery")}</div>
              <div className="text-xs text-gray-600 mt-1">{String(item.summary ?? item.notes ?? "")}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="px-5 py-10 text-center text-sm text-gray-500">{text}</div>
  );
}
