"use client";

import { useState, useEffect, useCallback } from "react";
import {
  inventoryApi,
  type OnHandPosition,
  type StockoutRisk,
  type PagedResponse,
} from "@/lib/inventoryApi";

const CATEGORY_BADGE: Record<string, string> = {
  PHARMACEUTICAL: "badge-category-pharmaceutical",
  MEDICAL_SUPPLY: "badge-category-medical-supply",
  LAB_REAGENT: "badge-category-lab-reagent",
  EQUIPMENT: "badge-category-equipment",
  CLINICAL_SUPPLY: "badge bg-cyan-100 text-cyan-800",
  GENERAL: "badge bg-neutral-100 text-neutral-700",
};

function daysUntilExpiry(expiryDate: string): number {
  const now = new Date();
  const exp = new Date(expiryDate);
  return Math.ceil((exp.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
}

function expiryBadge(expiryDate: string): string {
  const days = daysUntilExpiry(expiryDate);
  if (days <= 0) return "badge-expiry-critical";
  if (days <= 30) return "badge-expiry-critical";
  if (days <= 90) return "badge-expiry-warning";
  return "badge bg-neutral-100 text-neutral-600";
}

export default function DashboardPage() {
  const [onHand, setOnHand] = useState<PagedResponse<OnHandPosition> | null>(null);
  const [nearExpiry, setNearExpiry] = useState<PagedResponse<OnHandPosition> | null>(null);
  const [stockouts, setStockouts] = useState<PagedResponse<StockoutRisk> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [filterCategory, setFilterCategory] = useState<string>("");

  const loadDashboard = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = { size: "50" };
      if (filterCategory) params.category = filterCategory;

      const [onHandRes, nearExpiryRes, stockoutsRes] = await Promise.all([
        inventoryApi.getOnHand(params),
        inventoryApi.getNearExpiry({ size: "10" }),
        inventoryApi.getStockouts({ size: "10" }),
      ]);

      setOnHand(onHandRes);
      setNearExpiry(nearExpiryRes);
      setStockouts(stockoutsRes);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dashboard");
    } finally {
      setLoading(false);
    }
  }, [filterCategory]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="grid grid-cols-3 gap-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="card p-6 h-24 bg-neutral-100" />
            ))}
          </div>
          <div className="card p-6 space-y-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="h-10 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  const totalItems = onHand?.totalElements ?? 0;
  const lowStockCount = onHand?.content.filter((p) => p.belowReorder).length ?? 0;
  const nearExpiryCount = nearExpiry?.totalElements ?? 0;
  const stockoutCount = stockouts?.totalElements ?? 0;

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Store Dashboard</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Stock on-hand overview with low-stock and near-expiry highlights.
          </p>
        </div>
        <button onClick={loadDashboard} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <div className="card p-5">
          <p className="text-sm text-neutral-500 mb-1">Total Stock Lines</p>
          <p className="text-2xl font-semibold text-neutral-900">{totalItems}</p>
        </div>
        <div className="card p-5">
          <p className="text-sm text-neutral-500 mb-1">Low Stock Items</p>
          <p className="text-2xl font-semibold text-amber-600">{lowStockCount}</p>
        </div>
        <div className="card p-5">
          <p className="text-sm text-neutral-500 mb-1">Near-Expiry Items</p>
          <p className="text-2xl font-semibold text-orange-600">{nearExpiryCount}</p>
        </div>
        <div className="card p-5">
          <p className="text-sm text-neutral-500 mb-1">Stockout Risk</p>
          <p className="text-2xl font-semibold text-red-600">{stockoutCount}</p>
        </div>
      </div>

      {/* Near-Expiry Alert */}
      {nearExpiry && nearExpiry.content.length > 0 && (
        <div className="card p-5 mb-6 border-orange-200 bg-orange-50">
          <h2 className="text-sm font-semibold text-orange-800 mb-3">
            Near-Expiry Items (within 90 days)
          </h2>
          <div className="space-y-2">
            {nearExpiry.content.slice(0, 5).map((item) => (
              <div key={item.id} className="flex items-center justify-between text-sm">
                <div>
                  <span className="font-medium text-neutral-900">{item.itemName}</span>
                  <span className="text-neutral-500 ml-2">Batch: {item.batchNumber}</span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-neutral-600">Qty: {item.quantityOnHand}</span>
                  <span className={expiryBadge(item.expiryDate)}>
                    {daysUntilExpiry(item.expiryDate) <= 0
                      ? "EXPIRED"
                      : `${daysUntilExpiry(item.expiryDate)}d left`}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Stockout Risk Alert */}
      {stockouts && stockouts.content.length > 0 && (
        <div className="card p-5 mb-6 border-red-200 bg-red-50">
          <h2 className="text-sm font-semibold text-red-800 mb-3">
            Stockout Risk Items
          </h2>
          <div className="space-y-2">
            {stockouts.content.slice(0, 5).map((item, idx) => (
              <div key={idx} className="flex items-center justify-between text-sm">
                <div>
                  <span className="font-medium text-neutral-900">{item.itemName}</span>
                  <span className="text-neutral-500 ml-2">({item.itemCode})</span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-neutral-600">On-hand: {item.totalOnHand}</span>
                  <span className={item.isStockedOut ? "badge-stockout" : "badge-low-stock"}>
                    {item.isStockedOut
                      ? "STOCKED OUT"
                      : `${item.daysOfStock.toFixed(0)}d supply`}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Filter */}
      <div className="flex gap-4 mb-4">
        <select
          value={filterCategory}
          onChange={(e) => setFilterCategory(e.target.value)}
          className="select-field w-48"
        >
          <option value="">All Categories</option>
          <option value="PHARMACEUTICAL">Pharmaceutical</option>
          <option value="MEDICAL_SUPPLY">Medical Supply</option>
          <option value="LAB_REAGENT">Lab Reagent</option>
          <option value="EQUIPMENT">Equipment</option>
          <option value="CLINICAL_SUPPLY">Clinical Supply</option>
          <option value="GENERAL">General</option>
        </select>
      </div>

      {/* On-Hand table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Item Code</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Category</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Batch</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Expiry</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">On-Hand</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Available</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {onHand?.content.map((pos) => {
              const days = daysUntilExpiry(pos.expiryDate);
              return (
                <tr key={pos.id} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-3 text-neutral-900 font-mono text-xs font-medium">
                    {pos.itemCode}
                  </td>
                  <td className="px-4 py-3 text-neutral-900">
                    {pos.itemName}
                  </td>
                  <td className="px-4 py-3">
                    <span className={CATEGORY_BADGE[pos.category] || "badge bg-neutral-100 text-neutral-600"}>
                      {pos.category.replace("_", " ")}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-neutral-600 font-mono text-xs">
                    {pos.batchNumber}
                  </td>
                  <td className="px-4 py-3">
                    <span className={expiryBadge(pos.expiryDate)}>
                      {pos.expiryDate}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-neutral-900 font-medium">
                    {pos.quantityOnHand}
                  </td>
                  <td className="px-4 py-3 text-right text-neutral-600">
                    {pos.quantityAvailable}
                  </td>
                  <td className="px-4 py-3">
                    {pos.belowReorder && (
                      <span className="badge-low-stock">LOW</span>
                    )}
                    {days <= 0 && (
                      <span className="badge-expiry-critical ml-1">EXPIRED</span>
                    )}
                    {days > 0 && days <= 30 && (
                      <span className="badge-expiry-warning ml-1">EXPIRING</span>
                    )}
                    {!pos.belowReorder && days > 30 && (
                      <span className="badge bg-emerald-100 text-emerald-800">OK</span>
                    )}
                  </td>
                </tr>
              );
            })}
            {(!onHand || onHand.content.length === 0) && (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                  No stock positions found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        Showing {onHand?.content.length ?? 0} of {totalItems} stock position{totalItems !== 1 ? "s" : ""}
      </div>
    </div>
  );
}
