"use client";

import { useMemo } from "react";
import { RecordSummary } from "@/components/common/QueryResultPanel";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";
import {
  useDispatchConsole,
  useDispatchCouriers,
  useDispatchDashboard,
  useDispatchDeliveries,
  useDispatchFleet,
  useDispatchMissions,
} from "@/hooks/queries/useDispatchOps";

export default function EnterpriseFleetPage() {
  const dashboard = useDispatchDashboard();
  const deliveries = useDispatchDeliveries();
  const fleet = useDispatchFleet();
  const couriers = useDispatchCouriers();
  const missions = useDispatchMissions();
  const consoleView = useDispatchConsole();

  const recentTracking = useMemo(() => {
    const data = (consoleView.data?.data as { deliveries?: Array<Record<string, unknown>> } | undefined)?.deliveries ?? [];
    return data.slice(0, 5);
  }, [consoleView.data?.data]);

  return (
    <EnterpriseWorkspaceShell
      title="Fleet & logistics"
      subtitle="Dispatch operations console for deliveries, fleet, couriers, and autonomous missions."
    >
      <div className="space-y-4">
        <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {[
            { label: "Total deliveries", value: (dashboard.data?.data as { total_deliveries?: number } | undefined)?.total_deliveries ?? "-" },
            { label: "In transit", value: (dashboard.data?.data as { in_transit?: number } | undefined)?.in_transit ?? "-" },
            { label: "Delivered", value: (dashboard.data?.data as { delivered?: number } | undefined)?.delivered ?? "-" },
            { label: "Active fleet", value: (dashboard.data?.data as { active_fleet_assets?: number } | undefined)?.active_fleet_assets ?? "-" },
          ].map((metric) => (
            <div key={metric.label} className="rounded-xl border border-slate-200 bg-white p-3">
              <p className="text-xs uppercase tracking-wide text-slate-500">{metric.label}</p>
              <p className="mt-1 text-xl font-semibold text-slate-900">{String(metric.value)}</p>
            </div>
          ))}
        </section>

        <section className="grid gap-4 lg:grid-cols-2">
          <ListCard title="Deliveries" rows={(deliveries.data?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []} />
          <ListCard title="Fleet" rows={(fleet.data?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []} />
          <ListCard title="Couriers" rows={(couriers.data?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []} />
          <ListCard title="Missions" rows={(missions.data?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []} />
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-4">
          <h3 className="text-sm font-semibold text-slate-900">Recent tracking events</h3>
          <div className="mt-2 space-y-2">
            {recentTracking.length === 0 ? (
              <p className="text-sm text-slate-500">No tracking events yet.</p>
            ) : (
              recentTracking.map((row, idx) => (
                <div key={idx} className="rounded-md border border-slate-100 bg-slate-50 p-2 text-xs text-slate-700">
                  <RecordSummary record={row} />
                </div>
              ))
            )}
          </div>
        </section>
      </div>
    </EnterpriseWorkspaceShell>
  );
}

function ListCard({ title, rows }: { title: string; rows: Array<Record<string, unknown>> }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      <div className="mt-2 space-y-2">
        {rows.length === 0 ? (
          <p className="text-sm text-slate-500">No records.</p>
        ) : (
          rows.slice(0, 10).map((row, idx) => (
            <div key={idx} className="rounded-md border border-slate-100 bg-slate-50 p-2 text-xs text-slate-700">
              <RecordSummary record={row} />
            </div>
          ))
        )}
      </div>
    </div>
  );
}
