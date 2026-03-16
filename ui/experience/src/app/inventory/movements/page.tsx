"use client";

/**
 * Stock Movements — Log of stock transfers between locations.
 * Route: /inventory/movements
 */

import { Loader2, AlertTriangle, ArrowLeftRight } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface StockMovement {
  id: string;
  type: "stock_movement";
  attributes: {
    date: string;
    itemName: string;
    fromLocation: string;
    toLocation: string;
    quantity: number;
    reason: string;
    performedBy: string;
    [key: string]: unknown;
  };
}

export default function StockMovementsPage() {
  const { data, isLoading, error } = useQuery<ApiResponse<StockMovement[]>>({
    queryKey: ["inventory-movements"],
    queryFn: () => apiClient.get<ApiResponse<StockMovement[]>>("/internal/v1/inventory/movements"),
  });

  const movements = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Stock Movements" subtitle="Track stock transfers between locations">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading movements...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load movements</p>
          </div>
        ) : movements.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <ArrowLeftRight className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No stock movements recorded</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Item</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">From</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">To</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Quantity</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Reason</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Performed By</th>
                </tr>
              </thead>
              <tbody>
                {movements.map((mov) => (
                  <tr key={mov.id} className="border-b last:border-b-0 hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-900">
                      {new Date(mov.attributes.date).toLocaleDateString()}
                    </td>
                    <td className="px-4 py-3 font-medium text-gray-900">{mov.attributes.itemName}</td>
                    <td className="px-4 py-3 text-gray-600">{mov.attributes.fromLocation}</td>
                    <td className="px-4 py-3 text-gray-600">{mov.attributes.toLocation}</td>
                    <td className="px-4 py-3 text-gray-600">{mov.attributes.quantity}</td>
                    <td className="px-4 py-3 text-gray-600">{mov.attributes.reason}</td>
                    <td className="px-4 py-3 text-gray-600">{mov.attributes.performedBy}</td>
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
