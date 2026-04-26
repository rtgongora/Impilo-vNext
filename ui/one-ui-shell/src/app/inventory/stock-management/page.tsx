"use client";

/**
 * Enhanced Stock Management — unified dashboard for stock operations.
 * Route: /inventory/stock-management | pageTitle: "Stock Management"
 *
 * Surfaces KPIs, threshold alerts, stock table, and action forms
 * (order, receive, adjust, transfer, recall) in one governed workspace.
 */

import { useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowLeftRight,
  BarChart3,
  Box,
  ClipboardList,
  PackageCheck,
  PackageMinus,
  PackagePlus,
  RefreshCw,
  ShieldAlert,
  Truck,
  X,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/* ---------- types ---------- */

interface StockItem {
  id: string;
  name: string;
  code: string;
  category: string;
  currentQty: number;
  unit: string;
  reorderLevel: number;
  status: "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK" | "OVERSTOCK";
  lastMovement: string;
}

type ActionModal = "order" | "receive" | "adjust" | "transfer" | "recall" | null;

/* ---------- sample data ---------- */

const STOCK_ITEMS: StockItem[] = [
  { id: "1", name: "Paracetamol 500 mg Tabs", code: "MED-001", category: "Medication", currentQty: 2400, unit: "tabs", reorderLevel: 500, status: "IN_STOCK", lastMovement: "2026-04-11T08:30:00" },
  { id: "2", name: "Normal Saline 0.9% 1 L", code: "IV-003", category: "IV Fluids", currentQty: 45, unit: "bags", reorderLevel: 100, status: "LOW_STOCK", lastMovement: "2026-04-10T14:15:00" },
  { id: "3", name: "Surgical Gloves (M)", code: "CON-012", category: "Consumables", currentQty: 0, unit: "boxes", reorderLevel: 20, status: "OUT_OF_STOCK", lastMovement: "2026-04-09T11:00:00" },
  { id: "4", name: "Ceftriaxone 1 g Vials", code: "MED-045", category: "Medication", currentQty: 18, unit: "vials", reorderLevel: 50, status: "LOW_STOCK", lastMovement: "2026-04-10T16:45:00" },
  { id: "5", name: "Oxygen Mask (Adult)", code: "DEV-008", category: "Devices", currentQty: 150, unit: "units", reorderLevel: 30, status: "IN_STOCK", lastMovement: "2026-04-11T06:20:00" },
  { id: "6", name: "Metformin 500 mg Tabs", code: "MED-022", category: "Medication", currentQty: 3200, unit: "tabs", reorderLevel: 400, status: "IN_STOCK", lastMovement: "2026-04-08T09:10:00" },
  { id: "7", name: "Gauze Swabs 10x10 cm", code: "CON-003", category: "Consumables", currentQty: 12, unit: "packs", reorderLevel: 40, status: "LOW_STOCK", lastMovement: "2026-04-10T07:30:00" },
  { id: "8", name: "Insulin Syringes 1 mL", code: "CON-019", category: "Consumables", currentQty: 800, unit: "units", reorderLevel: 200, status: "OVERSTOCK", lastMovement: "2026-04-07T10:00:00" },
];

const STATUS_BADGES: Record<string, string> = {
  IN_STOCK: "bg-emerald-100 text-emerald-800",
  LOW_STOCK: "bg-amber-100 text-amber-800",
  OUT_OF_STOCK: "bg-red-100 text-red-800",
  OVERSTOCK: "bg-sky-100 text-sky-800",
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" });
}

/* ---------- component ---------- */

export default function StockManagementPage() {
  const [activeModal, setActiveModal] = useState<ActionModal>(null);
  const [transferForm, setTransferForm] = useState({ from: "", to: "", item: "", qty: "", reason: "" });

  const totalItems = STOCK_ITEMS.length;
  const lowStockAlerts = STOCK_ITEMS.filter((i) => i.status === "LOW_STOCK" || i.status === "OUT_OF_STOCK").length;
  const pendingOrders = 3;
  const recentMovements = 12;

  const kpis = [
    { label: "Stock Turnover Rate", value: "4.2x", sub: "per month" },
    { label: "Days of Supply", value: "18.5", sub: "days average" },
    { label: "Wastage Rate", value: "1.3%", sub: "of total value" },
    { label: "Fill Rate", value: "94.7%", sub: "orders fulfilled" },
  ];

  return (
    <AppLayout>
      <PageShell title="Stock Management" subtitle="Unified view of stock position, threshold alerts, and operational actions across the facility.">
        <div className="space-y-6">
          {/* Dashboard cards */}
          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500"><Box className="h-4 w-4" /> Total Items</div>
              <div className="mt-2 text-3xl font-semibold text-slate-900">{totalItems}</div>
              <p className="mt-1 text-sm text-slate-500">Active stock lines in this facility</p>
            </div>
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 shadow-sm">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-amber-700"><AlertTriangle className="h-4 w-4" /> Low Stock Alerts</div>
              <div className="mt-2 text-3xl font-semibold text-amber-900">{lowStockAlerts}</div>
              <p className="mt-1 text-sm text-amber-700">Items below reorder level</p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500"><ClipboardList className="h-4 w-4" /> Pending Orders</div>
              <div className="mt-2 text-3xl font-semibold text-slate-900">{pendingOrders}</div>
              <p className="mt-1 text-sm text-slate-500">Orders awaiting delivery</p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500"><ArrowLeftRight className="h-4 w-4" /> Recent Movements</div>
              <div className="mt-2 text-3xl font-semibold text-slate-900">{recentMovements}</div>
              <p className="mt-1 text-sm text-slate-500">Movements in last 7 days</p>
            </div>
          </section>

          {/* Stock actions */}
          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Stock Actions</h2>
            <div className="flex flex-wrap gap-2">
              <button onClick={() => setActiveModal("order")} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors">
                <PackagePlus className="h-4 w-4 text-sky-600" /> Order
              </button>
              <button onClick={() => setActiveModal("receive")} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors">
                <PackageCheck className="h-4 w-4 text-emerald-600" /> Receive
              </button>
              <button onClick={() => setActiveModal("adjust")} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors">
                <RefreshCw className="h-4 w-4 text-amber-600" /> Adjust
              </button>
              <button onClick={() => setActiveModal("transfer")} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors">
                <Truck className="h-4 w-4 text-indigo-600" /> Transfer
              </button>
              <button onClick={() => setActiveModal("recall")} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors">
                <ShieldAlert className="h-4 w-4 text-red-600" /> Recall
              </button>
            </div>
          </section>

          {/* Action modal overlay */}
          {activeModal && (
            <div className="rounded-2xl border border-sky-200 bg-sky-50 p-5 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-base font-semibold text-slate-900 capitalize">{activeModal} Stock</h3>
                <button onClick={() => setActiveModal(null)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-600"><X className="h-5 w-5" /></button>
              </div>

              {activeModal === "transfer" ? (
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block text-sm">
                    <span className="font-medium text-slate-700">From Location</span>
                    <input type="text" placeholder="e.g. Main Pharmacy" value={transferForm.from} onChange={(e) => setTransferForm((p) => ({ ...p, from: e.target.value }))}
                      className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                  </label>
                  <label className="block text-sm">
                    <span className="font-medium text-slate-700">To Location</span>
                    <input type="text" placeholder="e.g. Ward B Store" value={transferForm.to} onChange={(e) => setTransferForm((p) => ({ ...p, to: e.target.value }))}
                      className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                  </label>
                  <label className="block text-sm">
                    <span className="font-medium text-slate-700">Item</span>
                    <select value={transferForm.item} onChange={(e) => setTransferForm((p) => ({ ...p, item: e.target.value }))}
                      className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400">
                      <option value="">Select item...</option>
                      {STOCK_ITEMS.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
                    </select>
                  </label>
                  <label className="block text-sm">
                    <span className="font-medium text-slate-700">Quantity</span>
                    <input type="number" min={1} value={transferForm.qty} onChange={(e) => setTransferForm((p) => ({ ...p, qty: e.target.value }))}
                      className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                  </label>
                  <label className="block text-sm sm:col-span-2">
                    <span className="font-medium text-slate-700">Reason</span>
                    <input type="text" placeholder="e.g. Ward restock" value={transferForm.reason} onChange={(e) => setTransferForm((p) => ({ ...p, reason: e.target.value }))}
                      className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                  </label>
                  <div className="sm:col-span-2 flex justify-end">
                    <button onClick={() => setActiveModal(null)} className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition-colors">Submit Transfer</button>
                  </div>
                </div>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block text-sm">
                    <span className="font-medium text-slate-700">Item</span>
                    <select className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400">
                      <option value="">Select item...</option>
                      {STOCK_ITEMS.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
                    </select>
                  </label>
                  <label className="block text-sm">
                    <span className="font-medium text-slate-700">Quantity</span>
                    <input type="number" min={1} placeholder="0"
                      className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                  </label>
                  {activeModal === "adjust" && (
                    <label className="block text-sm sm:col-span-2">
                      <span className="font-medium text-slate-700">Reason for Adjustment</span>
                      <input type="text" placeholder="e.g. Expired stock removal"
                        className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                    </label>
                  )}
                  {activeModal === "recall" && (
                    <label className="block text-sm sm:col-span-2">
                      <span className="font-medium text-slate-700">Batch Number / Recall Reference</span>
                      <input type="text" placeholder="e.g. BATCH-2026-0411"
                        className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                    </label>
                  )}
                  <div className="sm:col-span-2 flex justify-end">
                    <button onClick={() => setActiveModal(null)} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 transition-colors capitalize">Submit {activeModal}</button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Stock table */}
          <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-slate-600">
                  <tr>
                    <th className="px-4 py-3 font-medium">Item</th>
                    <th className="px-4 py-3 font-medium">Code</th>
                    <th className="px-4 py-3 font-medium">Category</th>
                    <th className="px-4 py-3 font-medium text-right">Current Qty</th>
                    <th className="px-4 py-3 font-medium text-right">Reorder Level</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Last Movement</th>
                  </tr>
                </thead>
                <tbody>
                  {STOCK_ITEMS.map((item) => {
                    const belowReorder = item.currentQty <= item.reorderLevel;
                    return (
                      <tr key={item.id} className={`border-t border-slate-100 ${belowReorder ? "bg-red-50" : ""}`}>
                        <td className="px-4 py-3 font-medium text-slate-900">
                          {belowReorder && <AlertTriangle className="mr-1 inline h-3.5 w-3.5 text-red-500" />}
                          {item.name}
                        </td>
                        <td className="px-4 py-3 text-slate-500">{item.code}</td>
                        <td className="px-4 py-3 text-slate-600">{item.category}</td>
                        <td className="px-4 py-3 text-right font-semibold text-slate-900">{item.currentQty} {item.unit}</td>
                        <td className="px-4 py-3 text-right text-slate-500">{item.reorderLevel} {item.unit}</td>
                        <td className="px-4 py-3">
                          <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_BADGES[item.status] ?? ""}`}>
                            {item.status.replace(/_/g, " ")}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-slate-500">{formatDate(item.lastMovement)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          {/* KPI section */}
          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center gap-2 mb-4">
              <BarChart3 className="h-5 w-5 text-slate-500" />
              <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Key Performance Indicators</h2>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {kpis.map((kpi) => (
                <div key={kpi.label} className="rounded-xl border border-slate-200 p-4">
                  <div className="text-xs font-medium text-slate-500">{kpi.label}</div>
                  <div className="mt-1 text-2xl font-semibold text-slate-900">{kpi.value}</div>
                  <div className="text-xs text-slate-400">{kpi.sub}</div>
                </div>
              ))}
            </div>
          </section>

          {/* Navigation links */}
          <div className="flex flex-wrap gap-2 text-sm">
            <Link href="/inventory" className="rounded-lg border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-50">Inventory Overview</Link>
            <Link href="/inventory/movements" className="rounded-lg border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-50">Movements</Link>
            <Link href="/inventory/counts" className="rounded-lg border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-50">Counts</Link>
            <Link href="/inventory/requisitions" className="rounded-lg border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-50">Requisitions</Link>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
