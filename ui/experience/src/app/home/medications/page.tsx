"use client";

/**
 * My Medications — Patient-facing prescription list with refill request.
 * Route: /home/medications
 *
 * Uses the citizen prescription endpoint (same backing as mobile citizen app)
 * for viewing personal prescriptions and requesting refills.
 */

import Link from "next/link";
import { ArrowLeft, Pill, Loader2, RefreshCw, CheckCircle2, AlertCircle } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface PrescriptionResource {
  id: string;
  type: "prescription";
  attributes: {
    medication_name: string;
    generic_name?: string;
    dosage: string;
    frequency: string;
    status: string;
    prescribed_by?: string;
    created_at?: string;
    [key: string]: unknown;
  };
}

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  ACTIVE: "bg-green-100 text-green-700",
  DISPENSED: "bg-blue-100 text-blue-700",
  CANCELLED: "bg-gray-100 text-gray-600",
  REFILL_REQUESTED: "bg-purple-100 text-purple-700",
};

function useMyPrescriptions() {
  return useQuery<ApiResponse<PrescriptionResource[]>>({
    queryKey: ["my-prescriptions"],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/prescriptions"),
  });
}

export default function MyMedicationsPage() {
  const user = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useMyPrescriptions();
  const prescriptions = data?.data ?? [];

  const requestRefill = useMutation({
    mutationFn: (prescriptionId: string) =>
      apiClient.post(`/internal/v1/mobile/citizen/prescriptions/${prescriptionId}/refill`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["my-prescriptions"] });
    },
  });

  return (
    <AppLayout>
      <PageShell title="My Medications" subtitle="View your prescriptions and request refills">
        <div className="mb-4">
          <Link href="/home" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors">
            <ArrowLeft className="w-4 h-4" /> Back to Home
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load prescriptions</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading prescriptions...</span>
          </div>
        ) : prescriptions.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Pill className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No prescriptions on record</p>
          </div>
        ) : (
          <div className="space-y-3">
            {prescriptions.map((rx) => {
              const a = rx.attributes;
              const statusStyle = STATUS_STYLES[a.status] ?? "bg-gray-100 text-gray-600";
              const canRefill = a.status === "DISPENSED" || a.status === "ACTIVE";
              return (
                <div key={rx.id} className="bg-white rounded-lg border border-gray-200 p-5">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="text-sm font-semibold text-gray-900">{a.medication_name}</h3>
                      {a.generic_name && (
                        <p className="text-xs text-gray-500">{a.generic_name}</p>
                      )}
                      <p className="text-xs text-gray-600 mt-1">
                        {a.dosage} · {a.frequency}
                      </p>
                      {a.prescribed_by && (
                        <p className="text-xs text-gray-400 mt-0.5">
                          Prescribed by: {a.prescribed_by}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                        {a.status.replace(/_/g, " ")}
                      </span>
                      {canRefill && (
                        <button
                          onClick={() => requestRefill.mutate(rx.id)}
                          disabled={requestRefill.isPending}
                          className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                        >
                          {requestRefill.isPending
                            ? <Loader2 className="w-3 h-3 animate-spin" />
                            : <RefreshCw className="w-3 h-3" />}
                          Request Refill
                        </button>
                      )}
                    </div>
                  </div>
                  {requestRefill.isSuccess && requestRefill.variables === rx.id && (
                    <div className="mt-2 flex items-center gap-1 text-xs text-green-600">
                      <CheckCircle2 className="w-3 h-3" /> Refill request submitted
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
