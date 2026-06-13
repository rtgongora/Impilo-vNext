"use client";
import { useState, useEffect, useCallback } from "react";
import { opsApi, type StuckRoute } from "@/lib/opsApi";

export default function StuckOrdersPage() {
  const [routes, setRoutes] = useState<StuckRoute[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStuck = useCallback(async () => {
    try {
      setLoading(true);
      const data = await opsApi.getStuckOrders();
      setRoutes(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load stuck orders");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStuck();
  }, [loadStuck]);

  if (loading)
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
        </div>
      </div>
    );

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Stuck Orders</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Orders that failed routing and need manual intervention.
          </p>
        </div>
        <button onClick={loadStuck} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Route ID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Order ID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Route Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Retries</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Last Error</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Updated</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {routes.map((route) => (
              <tr key={route.id} className="hover:bg-neutral-50">
                <td className="px-4 py-3 font-mono text-xs">{route.id}</td>
                <td className="px-4 py-3 font-mono text-xs">{route.orderId}</td>
                <td className="px-4 py-3">{route.routeType}</td>
                <td className="px-4 py-3 text-center">{route.retryCount}</td>
                <td className="px-4 py-3 text-xs text-red-600 max-w-xs truncate">
                  {route.lastError || "\u2014"}
                </td>
                <td className="px-4 py-3 text-xs text-neutral-500">
                  {new Date(route.updatedAt).toLocaleString()}
                </td>
              </tr>
            ))}
            {routes.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-neutral-500">
                  No stuck orders. All routes healthy.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
