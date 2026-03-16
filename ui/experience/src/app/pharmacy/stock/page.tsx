"use client";

/**
 * Pharmacy Stock — View pharmacy stock levels.
 * Route: /pharmacy/stock
 */

import { Loader2, AlertTriangle, Package, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface StockItem {
  id: string;
  type: "pharmacy_stock";
  attributes: {
    medicationName: string;
    currentStock: number;
    reorderLevel: number;
    unit: string;
    lastRestocked: string | null;
    [key: string]: unknown;
  };
}

export default function PharmacyStockPage() {
  const { data, isLoading, error } = useQuery<ApiResponse<StockItem[]>>({
    queryKey: ["pharmacy-stock"],
    queryFn: () => apiClient.get<ApiResponse<StockItem[]>>("/internal/v1/pharmacy/stock"),
  });

  const items = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Pharmacy Stock" subtitle="Current medication stock levels">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading stock data...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load stock data</p>
          </div>
        ) : items.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Package className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No stock items found</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Medication</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Current Stock</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Reorder Level</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Unit</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Last Restocked</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const isLow = item.attributes.currentStock <= item.attributes.reorderLevel;
                  return (
                    <tr key={item.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {item.attributes.medicationName}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <span className={isLow ? "text-red-600 font-medium" : ""}>
                          {item.attributes.currentStock}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{item.attributes.reorderLevel}</td>
                      <td className="px-4 py-3 text-gray-600">{item.attributes.unit}</td>
                      <td className="px-4 py-3 text-gray-600">
                        {item.attributes.lastRestocked
                          ? new Date(item.attributes.lastRestocked).toLocaleDateString()
                          : "\u2014"}
                      </td>
                      <td className="px-4 py-3">
                        {isLow ? (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full font-medium bg-red-100 text-red-700">
                            <AlertCircle className="w-3 h-3" /> Low Stock
                          </span>
                        ) : (
                          <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-green-100 text-green-700">
                            In Stock
                          </span>
                        )}
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
