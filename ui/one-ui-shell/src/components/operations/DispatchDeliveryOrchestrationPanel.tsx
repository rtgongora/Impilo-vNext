"use client";

import Link from "next/link";
import { Loader2, Package, Truck } from "lucide-react";
import { useDispatchConsole, useDispatchDashboard, useDispatchDeliveries } from "@/hooks/queries/useDispatchOps";

function readCount(payload: unknown, key: string): number {
  if (!payload || typeof payload !== "object") return 0;
  const root = payload as Record<string, unknown>;
  const data = (root.data ?? root) as Record<string, unknown>;
  const counters = (data.counters ?? data) as Record<string, unknown>;
  const value = counters[key] ?? data[key];
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function listLength(payload: unknown): number {
  if (!payload || typeof payload !== "object") return 0;
  const root = payload as Record<string, unknown>;
  const data = root.data ?? root;
  if (Array.isArray(data)) return data.length;
  if (data && typeof data === "object") {
    const items = (data as Record<string, unknown>).items;
    if (Array.isArray(items)) return items.length;
  }
  return 0;
}

export function DispatchDeliveryOrchestrationPanel() {
  const dashboardQ = useDispatchDashboard();
  const consoleQ = useDispatchConsole();
  const deliveriesQ = useDispatchDeliveries();

  const inTransit = readCount(dashboardQ.data, "in_transit");
  const dispatchPending = readCount(dashboardQ.data, "dispatch_pending");
  const pendingConsole = listLength(consoleQ.data?.data ?? consoleQ.data);
  const deliveryCount = listLength(deliveriesQ.data);

  return (
    <section
      className="mb-6 rounded-2xl border border-sky-200 bg-sky-50/80 p-4"
      data-testid="dispatch-delivery-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-sky-900">
            <Truck className="h-4 w-4" />
            Dispatch &amp; delivery orchestration
          </p>
          <p className="mt-1 text-xs text-sky-800" data-testid="dispatch-kpi-strip">
            {dashboardQ.isLoading
              ? "Loading NHUME dashboard counters…"
              : `${dispatchPending} dispatch pending · ${inTransit} in transit · ${deliveryCount} delivery row(s)`}
          </p>
          <p className="mt-1 text-xs text-sky-700" data-testid="dispatch-console-probe">
            {consoleQ.isLoading
              ? "Dispatcher console: probing queue…"
              : `${pendingConsole} console queue item(s) ready for assignment`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/nhume/dashboard"
            className="inline-flex items-center gap-1 rounded-lg border border-sky-200 bg-white px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            <Package className="h-3.5 w-3.5" />
            NHUME dashboard
          </Link>
          <Link
            href="/nhume/dispatcher"
            className="rounded-lg border border-sky-200 bg-white px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            Dispatcher console
          </Link>
          <Link
            href="/nhume/deliveries"
            className="rounded-lg border border-sky-200 bg-white px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            Deliveries
          </Link>
        </div>
      </div>
      {(dashboardQ.isLoading || deliveriesQ.isLoading) && (
        <div className="mt-2 flex items-center gap-2 text-xs text-sky-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Binding dispatch BFF → NHUME delivery chain…
        </div>
      )}
    </section>
  );
}
