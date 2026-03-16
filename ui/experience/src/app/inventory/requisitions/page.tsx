"use client";

/**
 * Requisitions — Manage stock requisition requests.
 * Route: /inventory/requisitions
 */

import { Loader2, AlertTriangle, FileInput } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface Requisition {
  id: string;
  type: "requisition";
  attributes: {
    requisitionNumber: string;
    requestedBy: string;
    itemCount: number;
    status: string;
    createdAt: string;
    [key: string]: unknown;
  };
}

const STATUS_STYLES: Record<string, string> = {
  DRAFT: "bg-gray-100 text-gray-700",
  SUBMITTED: "bg-blue-100 text-blue-700",
  APPROVED: "bg-green-100 text-green-700",
  FULFILLED: "bg-emerald-100 text-emerald-700",
  REJECTED: "bg-red-100 text-red-700",
};

export default function RequisitionsPage() {
  const { data, isLoading, error } = useQuery<ApiResponse<Requisition[]>>({
    queryKey: ["inventory-requisitions"],
    queryFn: () => apiClient.get<ApiResponse<Requisition[]>>("/internal/v1/inventory/requisitions"),
  });

  const requisitions = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Requisitions" subtitle="Manage stock requisition requests">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading requisitions...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load requisitions</p>
          </div>
        ) : requisitions.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <FileInput className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No requisitions found</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Req #</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Requested By</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Items</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                </tr>
              </thead>
              <tbody>
                {requisitions.map((req) => {
                  const statusStyle = STATUS_STYLES[req.attributes.status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={req.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-xs text-gray-700">
                        {req.attributes.requisitionNumber || req.id.slice(0, 8).toUpperCase()}
                      </td>
                      <td className="px-4 py-3 text-gray-900">{req.attributes.requestedBy}</td>
                      <td className="px-4 py-3 text-gray-600">{req.attributes.itemCount}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {req.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {new Date(req.attributes.createdAt).toLocaleDateString()}
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
