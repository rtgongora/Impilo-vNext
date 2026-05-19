"use client";

/**
 * Nhume Dispatcher Console
 *
 * Operational surface for dispatchers: pending queue, suggested assignments,
 * available couriers/assets and SLA exceptions. The page is intentionally
 * action-dense — every row is a deep-link or a one-click suggestion accept.
 */

import Link from "next/link";
import { Navigation, Loader2, Users, Truck, Clock, AlertTriangle, Wand2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip, NhumePriorityChip } from "@/components/nhume/NhumeStatusChip";
import { useNhumeDispatcherConsole, useAssignDelivery } from "@/hooks/useNhume";

export default function NhumeDispatcherPage() {
  const { data, isPending, isError } = useNhumeDispatcherConsole();

  return (
    <AppLayout>
      <PageShell
        title="Nhume Dispatcher Console"
        subtitle="Pending queue, suggested assignments, SLA exceptions and active routes"
        icon={<Navigation className="h-6 w-6" />}
      >
        {isPending && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading dispatcher console…
          </div>
        )}
        {isError && (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            Dispatcher console could not be loaded.
          </div>
        )}
        {data && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <PendingQueue rows={(data.pending_queue ?? []) as unknown as Array<Record<string, unknown>>} />
              <Suggestions rows={(data.suggested_assignments ?? []) as unknown as Array<Record<string, unknown>>} />
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <AvailabilityList title="Available couriers" icon={<Users className="h-5 w-5 text-teal-600" />} rows={data.available_couriers ?? []} kind="couriers" />
              <AvailabilityList title="Available assets" icon={<Truck className="h-5 w-5 text-teal-600" />} rows={data.available_assets ?? []} kind="fleet" />
            </div>
            <SlaBreaches rows={(data.sla_breaches ?? []) as unknown as Array<Record<string, unknown>>} />
            <ActiveRoutes rows={data.active_routes ?? []} />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function PendingQueue({ rows }: { rows: Array<Record<string, unknown>> }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="border-b border-gray-100 px-5 py-4 flex items-center gap-2">
        <Clock className="h-5 w-5 text-teal-600" />
        <h3 className="font-semibold text-gray-900">Pending queue</h3>
      </div>
      {rows.length === 0 ? (
        <Empty text="No pending deliveries — dispatch is clear." />
      ) : (
        <ul className="divide-y divide-gray-100">
          {rows.map((d, idx) => (
            <li key={idx} className="px-5 py-3 flex items-center justify-between">
              <div>
                <Link href={`/nhume/deliveries/${d.delivery_id}`} className="font-medium text-teal-700 hover:text-teal-900">
                  {String(d.reference ?? d.delivery_id)}
                </Link>
                <div className="text-xs text-gray-500 mt-1">
                  {String(d.delivery_type ?? "—")} · {String(d.origin_label ?? "?")} → {String(d.destination_label ?? "?")}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <NhumePriorityChip priority={String(d.priority ?? "")} />
                <NhumeStatusChip status={String(d.status ?? "")} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Suggestions({ rows }: { rows: Array<Record<string, unknown>> }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="border-b border-gray-100 px-5 py-4 flex items-center gap-2">
        <Wand2 className="h-5 w-5 text-amber-600" />
        <h3 className="font-semibold text-gray-900">Suggested assignments</h3>
      </div>
      {rows.length === 0 ? (
        <Empty text="No suggested assignments at the moment." />
      ) : (
        <ul className="divide-y divide-gray-100">
          {rows.map((s, idx) => <SuggestionRow key={idx} s={s} />)}
        </ul>
      )}
    </div>
  );
}

function SuggestionRow({ s }: { s: Record<string, unknown> }) {
  const id = String(s.delivery_id ?? "");
  const assign = useAssignDelivery(id);
  return (
    <li className="px-5 py-3 flex items-center justify-between">
      <div>
        <Link href={`/nhume/deliveries/${id}`} className="font-medium text-teal-700 hover:text-teal-900">
          {String(s.reference ?? id)}
        </Link>
        <div className="text-xs text-gray-500 mt-1">
          courier {String(s.suggested_courier_id ?? "—")} · asset {String(s.suggested_asset_id ?? "—")}
          {typeof s.score === "number" && <> · score {(s.score as number).toFixed(2)}</>}
        </div>
        {s.rationale ? <div className="text-xs text-gray-600 italic mt-1">{String(s.rationale)}</div> : null}
      </div>
      <button
        disabled={!s.suggested_courier_id || assign.isPending}
        onClick={() =>
          assign.mutate({
            courier_id: String(s.suggested_courier_id),
            asset_id: s.suggested_asset_id ? String(s.suggested_asset_id) : undefined,
            reason: "dispatcher accepted suggestion",
          })
        }
        className="rounded-xl bg-teal-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
      >
        Accept
      </button>
    </li>
  );
}

function AvailabilityList({
  title,
  icon,
  rows,
  kind,
}: {
  title: string;
  icon: React.ReactNode;
  rows: Array<Record<string, unknown>>;
  kind: "couriers" | "fleet";
}) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="border-b border-gray-100 px-5 py-4 flex items-center gap-2">
        {icon}
        <h3 className="font-semibold text-gray-900">{title}</h3>
      </div>
      {rows.length === 0 ? (
        <Empty text={`No ${title.toLowerCase()} on duty.`} />
      ) : (
        <ul className="divide-y divide-gray-100">
          {rows.slice(0, 12).map((row, idx) => {
            const id = String(row.id ?? row.asset_id ?? row.courier_id ?? "");
            const label = String(row.display_name ?? row.registration ?? row.label ?? id);
            const detail = String(row.mode ?? row.vehicle_type ?? row.role ?? "");
            return (
              <li key={idx} className="px-5 py-3 flex items-center justify-between text-sm">
                <Link href={`/nhume/${kind}/${id}`} className="text-teal-700 hover:text-teal-900 font-medium">{label}</Link>
                <span className="text-xs text-gray-500">{detail}</span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function SlaBreaches({ rows }: { rows: Array<Record<string, unknown>> }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="border-b border-gray-100 px-5 py-4 flex items-center gap-2">
        <AlertTriangle className="h-5 w-5 text-amber-600" />
        <h3 className="font-semibold text-gray-900">SLA breaches</h3>
      </div>
      {rows.length === 0 ? (
        <Empty text="No SLA breaches recorded." />
      ) : (
        <ul className="divide-y divide-gray-100">
          {rows.map((d, idx) => (
            <li key={idx} className="px-5 py-3 flex items-center justify-between">
              <Link href={`/nhume/deliveries/${d.delivery_id}`} className="font-medium text-rose-700 hover:text-rose-900">
                {String(d.reference ?? d.delivery_id)}
              </Link>
              <div className="flex items-center gap-2">
                <NhumeStatusChip status={String(d.status ?? "")} />
                <span className="text-xs text-rose-700">
                  due {d.sla_due_at ? new Date(String(d.sla_due_at)).toLocaleTimeString() : ""}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ActiveRoutes({ rows }: { rows: Array<Record<string, unknown>> }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="border-b border-gray-100 px-5 py-4 flex items-center gap-2">
        <Navigation className="h-5 w-5 text-teal-600" />
        <h3 className="font-semibold text-gray-900">Active routes</h3>
      </div>
      {rows.length === 0 ? (
        <Empty text="No active multi-stop routes." />
      ) : (
        <ul className="divide-y divide-gray-100">
          {rows.map((r, idx) => (
            <li key={idx} className="px-5 py-3 text-sm">
              {String(r.route_label ?? r.route_id ?? "route")}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return <div className="px-5 py-8 text-center text-sm text-gray-500">{text}</div>;
}
