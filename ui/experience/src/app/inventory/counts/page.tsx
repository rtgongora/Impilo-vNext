"use client";

/**
 * Stock Counts — Physical stock count records.
 * Route: /inventory/counts
 */

import { Loader2, AlertTriangle, ClipboardList } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface StockCount {
  id: string;
  type: "stock_count";
  attributes: {
    countDate: string;
    countedBy: string;
    itemCount: number;
    discrepancies: number;
    status: string;
    [key: string]: unknown;
  };
}

const STATUS_STYLES: Record<string, string> = {
  COMPLETED: "bg-green-100 text-green-700",
  IN_PROGRESS: "bg-amber-100 text-amber-700",
  DRAFT: "bg-gray-100 text-gray-700",
};

export default function StockCountsPage() {
  const { data, isLoading, error } = useQuery<ApiResponse<StockCount[]>>({
    queryKey: ["inventory-counts"],
    queryFn: () => apiClient.get<ApiResponse<StockCount[]>>("/internal/v1/inventory/counts"),
  });

  const counts = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Stock Counts" subtitle="Physical stock count records and discrepancies">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading stock counts...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load stock counts</p>
          </div>
        ) : counts.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <ClipboardList className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No stock counts recorded</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Count Date</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Counted By</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Item Count</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Discrepancies</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody>
                {counts.map((count) => {
                  const statusStyle = STATUS_STYLES[count.attributes.status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={count.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 text-gray-900">
                        {new Date(count.attributes.countDate).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-gray-600">{count.attributes.countedBy}</td>
                      <td className="px-4 py-3 text-gray-600">{count.attributes.itemCount}</td>
                      <td className="px-4 py-3">
                        {count.attributes.discrepancies > 0 ? (
                          <span className="text-red-600 font-medium">{count.attributes.discrepancies}</span>
                        ) : (
                          <span className="text-green-600">0</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {count.attributes.status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
