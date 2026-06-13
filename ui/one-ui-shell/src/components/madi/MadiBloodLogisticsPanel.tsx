"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Droplets, Loader2, Package, Truck } from "lucide-react";
import { useDispatchDashboard, useDispatchDeliveries } from "@/hooks/queries/useDispatchOps";
import { useBloodBankStock } from "@/hooks/queries/useMadi";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { getStoredBloodBankId } from "@/lib/madi-session";

function readCount(payload: unknown, key: string): number {
  if (!payload || typeof payload !== "object") return 0;
  const root = payload as Record<string, unknown>;
  const data = (root.data ?? root) as Record<string, unknown>;
  const counters = (data.counters ?? data) as Record<string, unknown>;
  const value = counters[key] ?? data[key];
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function extractDeliveryRows(payload: unknown): Array<Record<string, unknown>> {
  if (!payload || typeof payload !== "object") return [];
  const root = payload as Record<string, unknown>;
  const data = root.data ?? root;
  if (Array.isArray(data)) return data.filter((row): row is Record<string, unknown> => typeof row === "object" && row !== null);
  if (data && typeof data === "object") {
    const items = (data as Record<string, unknown>).items;
    if (Array.isArray(items)) {
      return items.filter((row): row is Record<string, unknown> => typeof row === "object" && row !== null);
    }
  }
  return [];
}

function isBloodLogisticsRow(row: Record<string, unknown>): boolean {
  const type = String(row.deliveryType ?? row.delivery_type ?? row.type ?? "").toUpperCase();
  return type.includes("BLOOD") || (type === "COLD_CHAIN" && String(row.itemDescription ?? row.item_description ?? "").toUpperCase().includes("BLOOD"));
}

function readLabel(row: Record<string, unknown>): string {
  return String(row.reference ?? row.deliveryId ?? row.delivery_id ?? row.id ?? "Delivery");
}

function readStatus(row: Record<string, unknown>): string {
  return String(row.status ?? row.lifecycleStatus ?? row.lifecycle_status ?? "—");
}

export function MadiBloodLogisticsPanel() {
  const facility = useFacilityStore((s) => s.facility);
  const [bloodBankId, setBloodBankId] = useState("");
  const dashboardQ = useDispatchDashboard();
  const deliveriesQ = useDispatchDeliveries();
  const stockQ = useBloodBankStock(bloodBankId || undefined);

  useEffect(() => {
    if (facility?.id) setBloodBankId(getStoredBloodBankId(facility.id));
  }, [facility?.id]);

  const stockRows = stockQ.data ?? [];
  const totalAvailable = stockRows.reduce((sum, row) => sum + (row.quantityAvailable ?? 0), 0);
  const totalReserved = stockRows.reduce((sum, row) => sum + (row.quantityReserved ?? 0), 0);

  const bloodRows = extractDeliveryRows(deliveriesQ.data).filter(isBloodLogisticsRow);
  const inTransit = readCount(dashboardQ.data, "in_transit");
  const dispatchPending = readCount(dashboardQ.data, "dispatch_pending");
  const nhumeUnavailable = dashboardQ.isError && deliveriesQ.isError;

  return (
    <section
      className="rounded-2xl border border-teal-200 bg-teal-50/80 p-4"
      data-testid="madi-blood-logistics-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-teal-900">
            <Truck className="h-4 w-4" />
            MADI → Nhume blood logistics boundary
          </p>
          <p className="mt-1 text-xs text-teal-800" data-testid="madi-logistics-kpi-strip">
            {nhumeUnavailable
              ? "Nhume dispatch BFF unavailable — issue units in MADI, then retry dispatch tracking."
              : dashboardQ.isLoading || deliveriesQ.isLoading
                ? "Loading NHUME dispatch counters for blood cold-chain…"
                : `${dispatchPending} dispatch pending · ${inTransit} in transit · ${bloodRows.length} blood delivery row(s)`}
          </p>
          <p className="mt-1 text-xs text-teal-700" data-testid="madi-blood-stock-summary">
            {bloodBankId ? (
              stockQ.isLoading ? (
                "Blood bank stock: loading MADI summary…"
              ) : stockQ.isError ? (
                "Blood bank stock: unavailable from MADI BFF"
              ) : (
                `Blood bank stock: ${stockRows.length} line(s) · ${totalAvailable} available · ${totalReserved} reserved`
              )
            ) : (
              "Configure blood bank to show MADI stock summary alongside dispatch"
            )}
          </p>
          <p className="mt-1 text-xs text-teal-700">
            MADI owns blood bank issue and transfusion truth. Last-mile custody, fleet assignment, and chain-of-custody
            exceptions live in Nhume (dispatch-service).
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/nhume/deliveries/new"
            className="inline-flex items-center gap-1 rounded-lg border border-teal-200 bg-card px-2.5 py-1.5 font-medium text-teal-900 hover:border-teal-300"
          >
            <Package className="h-3.5 w-3.5" />
            New BLOOD_PRODUCT dispatch
          </Link>
          <Link
            href="/madi/blood-bank/stock"
            className="rounded-lg border border-teal-200 bg-card px-2.5 py-1.5 font-medium text-teal-900 hover:border-teal-300"
          >
            <Droplets className="inline h-3.5 w-3.5 mr-1" />
            Blood stock
          </Link>
          <Link
            href="/nhume/deliveries"
            className="rounded-lg border border-teal-200 bg-card px-2.5 py-1.5 font-medium text-teal-900 hover:border-teal-300"
          >
            Track deliveries
          </Link>
        </div>
      </div>

      {(dashboardQ.isLoading || deliveriesQ.isLoading) && !nhumeUnavailable ? (
        <div className="mt-2 flex items-center gap-2 text-xs text-teal-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Binding MADI logistics view → useDispatchOps /internal/v1/dispatch/*
        </div>
      ) : null}

      {!nhumeUnavailable && bloodRows.length > 0 ? (
        <ul className="mt-3 space-y-2 text-xs">
          {bloodRows.slice(0, 6).map((row) => (
            <li
              key={readLabel(row)}
              className="flex items-center justify-between gap-2 rounded-lg border border-teal-100 bg-card px-2.5 py-2"
            >
              <span className="font-medium text-teal-900">{readLabel(row)}</span>
              <span className="text-teal-700">{readStatus(row)}</span>
            </li>
          ))}
        </ul>
      ) : !nhumeUnavailable && !deliveriesQ.isLoading ? (
        <p className="mt-3 text-xs text-teal-800">
          No active blood-product deliveries in Nhume yet. After issuing from the blood bank, create a{" "}
          <strong>BLOOD_PRODUCT</strong> delivery with cold-chain enabled.
        </p>
      ) : null}
    </section>
  );
}
