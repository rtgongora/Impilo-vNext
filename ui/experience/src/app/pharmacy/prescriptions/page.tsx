"use client";

/**
 * Prescriptions List — View and manage prescriptions.
 * Route: /pharmacy/prescriptions
 */

import { useState } from "react";
import { Loader2, AlertTriangle, FileText } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { usePrescriptions } from "@/hooks/queries/usePharmacy";

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  DISPENSED: "bg-green-100 text-green-700",
  CANCELLED: "bg-red-100 text-red-700",
};

const STATUS_FILTERS = [
  { value: "", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "DISPENSED", label: "Dispensed" },
  { value: "CANCELLED", label: "Cancelled" },
];

export default function PrescriptionsPage() {
  const [statusFilter, setStatusFilter] = useState("");
  const { data, isLoading, error } = usePrescriptions(
    statusFilter ? { status: statusFilter } : undefined,
  );

  const prescriptions = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Prescriptions" subtitle="View and manage patient prescriptions">
        {/* Filter */}
        <div className="mb-4 flex gap-2">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => setStatusFilter(f.value)}
              className={`px-3 py-1.5 text-xs font-medium rounded-full border transition-colors ${
                statusFilter === f.value
                  ? "bg-blue-600 text-white border-blue-600"
                  : "bg-white text-gray-600 border-gray-300 hover:border-gray-400"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading prescriptions...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load prescriptions</p>
          </div>
        ) : prescriptions.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No prescriptions found</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Rx #</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Medication</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Dosage</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Prescriber</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                </tr>
              </thead>
              <tbody>
                {prescriptions.map((rx) => {
                  const attrs = rx.attributes as Record<string, unknown>;
                  const firstItem = rx.attributes.items?.[0];
                  const statusStyle = STATUS_STYLES[rx.attributes.status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={rx.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-xs text-gray-700">
                        {rx.id.slice(0, 8).toUpperCase()}
                      </td>
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {(attrs.patientName as string) || rx.attributes.patientId}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {firstItem?.medication || "\u2014"}
                        {rx.attributes.items.length > 1 && (
                          <span className="text-gray-400 ml-1">+{rx.attributes.items.length - 1} more</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-gray-600">{firstItem?.dosage || "\u2014"}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {rx.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {(attrs.prescriberName as string) || rx.attributes.prescriberId}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {(attrs.createdAt as string)
                          ? new Date(attrs.createdAt as string).toLocaleDateString()
                          : "\u2014"}
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
