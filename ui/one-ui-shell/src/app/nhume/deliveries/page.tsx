"use client";

/**
 * Nhume Deliveries — list, filter, create
 *
 * Read-and-act surface for any role that is allowed to see deliveries. The
 * filter set mirrors the backend collection query (status, priority, search
 * and assigned-to). Each row is a deep link into the delivery detail page.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { ListChecks, PackagePlus, Search, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip, NhumePriorityChip } from "@/components/nhume/NhumeStatusChip";
import { useNhumeDeliveries } from "@/hooks/useNhume";

const STATUSES = [
  "", "DRAFT", "SUBMITTED", "APPROVED", "ASSIGNED", "ACCEPTED",
  "EN_ROUTE_TO_PICKUP", "PICKED_UP", "IN_TRANSIT", "AT_DESTINATION",
  "DELIVERED", "FAILED", "RETURNED", "CANCELLED",
];

const PRIORITIES = ["", "ROUTINE", "STANDARD", "URGENT", "EMERGENCY"];

export default function NhumeDeliveriesPage() {
  const [status, setStatus] = useState("");
  const [priority, setPriority] = useState("");
  const [q, setQ] = useState("");
  const [draftQ, setDraftQ] = useState("");

  const { data, isPending, isError, error } = useNhumeDeliveries({
    status: status || undefined,
    priority: priority || undefined,
    q: q || undefined,
    size: 100,
  });

  const rows = useMemo(() => data?.data ?? [], [data]);

  return (
    <AppLayout>
      <PageShell
        title="Nhume Deliveries"
        subtitle="Browse, filter, create and progress delivery requests"
        icon={<ListChecks className="h-6 w-6" />}
      >
        <div className="flex flex-wrap items-center gap-3 mb-4">
          <div className="relative flex-1 min-w-[240px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              value={draftQ}
              onChange={(e) => setDraftQ(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") setQ(draftQ);
              }}
              onBlur={() => setQ(draftQ)}
              placeholder="Search by reference, recipient, item…"
              className="w-full rounded-xl border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />
          </div>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white"
          >
            {STATUSES.map((s) => (
              <option key={s || "any"} value={s}>
                {s || "Any status"}
              </option>
            ))}
          </select>
          <select
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            className="rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white"
          >
            {PRIORITIES.map((p) => (
              <option key={p || "any"} value={p}>
                {p || "Any priority"}
              </option>
            ))}
          </select>
          <Link
            href="/nhume/deliveries/new"
            className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 transition-colors ml-auto"
          >
            <PackagePlus className="h-4 w-4" />
            New Delivery
          </Link>
        </div>

        {isPending && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading deliveries…
          </div>
        )}

        {isError && (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4" />
            Deliveries could not be loaded.{" "}
            {(error as { error?: { message?: string } })?.error?.message ?? "Please retry shortly."}
          </div>
        )}

        {!isPending && !isError && rows.length === 0 && (
          <div className="rounded-2xl border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <ListChecks className="mx-auto h-10 w-10 text-gray-400" />
            <h3 className="mt-3 font-semibold text-gray-900">No deliveries match your filters</h3>
            <p className="mt-1 text-sm text-gray-600">Adjust the filters or create a new delivery request.</p>
          </div>
        )}

        {!isPending && !isError && rows.length > 0 && (
          <div className="rounded-2xl border border-gray-200 bg-white overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <tr>
                  <th className="px-4 py-3 text-left">Reference</th>
                  <th className="px-4 py-3 text-left">Type</th>
                  <th className="px-4 py-3 text-left">Priority</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Origin → Destination</th>
                  <th className="px-4 py-3 text-left">SLA</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {rows.map((d) => (
                  <tr key={d.delivery_id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link
                        href={`/nhume/deliveries/${d.delivery_id}`}
                        className="font-medium text-teal-700 hover:text-teal-900"
                      >
                        {d.reference}
                      </Link>
                      {d.is_demo && (
                        <span className="ml-2 inline-flex items-center rounded bg-gray-100 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-gray-600">
                          demo
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-700">{d.delivery_type ?? "—"}</td>
                    <td className="px-4 py-3">
                      <NhumePriorityChip priority={String(d.priority ?? "")} />
                    </td>
                    <td className="px-4 py-3">
                      <NhumeStatusChip status={String(d.status)} />
                    </td>
                    <td className="px-4 py-3 text-gray-700">
                      <span className="text-gray-900">{d.origin_label ?? "—"}</span>
                      <span className="mx-1 text-gray-400">→</span>
                      <span>{d.destination_label ?? "—"}</span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {d.sla_due_at ? new Date(d.sla_due_at).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
