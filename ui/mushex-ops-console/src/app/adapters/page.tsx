"use client";

import { useState, useEffect } from "react";
import { mushexApi, type AdapterConfig, type AdapterType } from "@/lib/mushexApi";

const ADAPTER_TYPE_BADGE: Record<AdapterType, string> = {
  MOBILE_MONEY: "bg-emerald-100 text-primary-hover",
  BANK_TRANSFER: "bg-blue-100 text-blue-800",
  CARD_GATEWAY: "bg-purple-100 text-purple-800",
  SANDBOX: "bg-neutral-100 text-neutral-600",
};

export default function AdaptersPage() {
  const [adapters, setAdapters] = useState<AdapterConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const data = await mushexApi.listAdapters();
        setAdapters(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load adapters");
      } finally {
        setLoading(false);
      }
    }

    load();
  }, []);

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Payment Adapters</h1>
        <p className="text-sm text-neutral-500 mt-1">
          View configured payment adapters per tenant, their type, and status.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {loading ? (
        <div className="p-8 text-center text-neutral-500 text-sm">Loading...</div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200 bg-neutral-50">
                <th className="text-left px-4 py-3 font-medium text-neutral-600">ID</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Type</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Tenant</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {adapters.map((adapter) => (
                <tr key={adapter.id} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                    {adapter.id}
                  </td>
                  <td className="px-4 py-3 text-sm text-neutral-900 font-medium">
                    {adapter.name}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`badge ${ADAPTER_TYPE_BADGE[adapter.adapterType]}`}>
                      {adapter.adapterType.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-neutral-500">
                    {adapter.tenantId}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`badge ${
                      adapter.enabled
                        ? "bg-emerald-100 text-primary-hover"
                        : "bg-red-100 text-red-800"
                    }`}>
                      {adapter.enabled ? "ENABLED" : "DISABLED"}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {new Date(adapter.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
              {adapters.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-neutral-500">
                    No adapters configured. Adapters are registered at the service level.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-3 text-xs text-neutral-400">
        {adapters.length} adapter{adapters.length !== 1 ? "s" : ""} configured
      </div>
    </div>
  );
}
