"use client";

/**
 * Pharmacy Dispense — View pending prescriptions and dispense them.
 * Route: /pharmacy/dispense | pageTitle: "Dispensing"
 */

import { useState } from "react";
import Link from "next/link";
import { Pill, Loader2, CheckCircle2, ArrowLeft } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface Prescription {
  id: string;
  medication_name: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  status: string;
  prescribed_by: string;
  patient_name: string;
  patient_id: string;
  created_at: string;
}

type PrescriptionsResponse = ApiResponse<Prescription[]>;

/* ------------------------------------------------------------------ */
/*  Inline hook — fetches pending prescriptions                        */
/* ------------------------------------------------------------------ */

function usePendingPrescriptions() {
  return useQuery<PrescriptionsResponse>({
    queryKey: ["prescriptions", { status: "PENDING" }],
    queryFn: () =>
      apiClient.get<PrescriptionsResponse>(
        `/internal/v1/pharmacy/prescriptions?status=PENDING`,
      ),
  });
}

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function PharmacyDispensePage() {
  const queryClient = useQueryClient();

  const { data: prescriptionsData, isLoading, isError } =
    usePendingPrescriptions();
  const prescriptions = prescriptionsData?.data ?? [];

  const [dispensingId, setDispensingId] = useState<string | null>(null);
  const [dispensedIds, setDispensedIds] = useState<Set<string>>(new Set());
  const [dispenseError, setDispenseError] = useState<string | null>(null);

  async function handleDispense(prescriptionId: string) {
    setDispensingId(prescriptionId);
    setDispenseError(null);

    try {
      await apiClient.post("/internal/v1/pharmacy/dispense", {
        prescription_id: prescriptionId,
        dispensed_by: "current-user",
      });

      setDispensedIds((prev) => new Set(prev).add(prescriptionId));
      queryClient.invalidateQueries({
        queryKey: ["prescriptions", { status: "PENDING" }],
      });
    } catch {
      setDispenseError(prescriptionId);
    } finally {
      setDispensingId(null);
    }
  }

  return (
    <AppLayout>
      <PageShell title="Dispensing" subtitle="Review and dispense pending prescriptions">
        {/* Back link */}
        <div className="mb-4">
          <Link
            href="/pharmacy"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to pharmacy
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">
              Loading pending prescriptions...
            </span>
          </div>
        ) : isError ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-12 text-center">
            <p className="text-red-600 text-sm">
              Failed to load prescriptions. Please try again later.
            </p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center gap-2">
              <Pill className="w-5 h-5 text-indigo-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                Pending Prescriptions ({prescriptions.length})
              </h2>
            </div>

            {/* Prescription Cards */}
            {prescriptions.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Pill className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">
                  No pending prescriptions to dispense
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4">
                {prescriptions.map((rx) => {
                  const isDispensing = dispensingId === rx.id;
                  const isDispensed = dispensedIds.has(rx.id);
                  const hasError = dispenseError === rx.id;

                  return (
                    <div
                      key={rx.id}
                      className="bg-white rounded-lg border border-gray-200 p-4 hover:shadow-sm transition-shadow"
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex items-center gap-2">
                          <Pill className="w-4 h-4 text-indigo-500 mt-0.5" />
                          <div>
                            <p className="text-sm font-semibold text-gray-900">
                              {rx.medication_name}
                            </p>
                            <p className="text-xs text-gray-500 mt-0.5">
                              {rx.dosage} &middot; {rx.frequency} &middot;{" "}
                              {rx.duration}
                            </p>
                          </div>
                        </div>
                        <span className="px-2.5 py-0.5 text-xs font-medium rounded-full bg-yellow-100 text-yellow-700">
                          {rx.status}
                        </span>
                      </div>

                      <div className="mt-3 flex items-center gap-4 text-xs text-gray-500">
                        <span>
                          Patient:{" "}
                          <span className="font-medium">{rx.patient_name}</span>
                        </span>
                        <span>
                          Qty: <span className="font-medium">{rx.quantity}</span>
                        </span>
                        <span>
                          Prescribed by:{" "}
                          <span className="font-medium">{rx.prescribed_by}</span>
                        </span>
                        <span>
                          {new Date(rx.created_at).toLocaleDateString()}
                        </span>
                      </div>

                      <div className="mt-3 flex items-center gap-3">
                        {isDispensed ? (
                          <div className="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-green-700">
                            <CheckCircle2 className="w-4 h-4" />
                            Dispensed
                          </div>
                        ) : (
                          <button
                            onClick={() => handleDispense(rx.id)}
                            disabled={isDispensing}
                            className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                          >
                            {isDispensing ? (
                              <>
                                <Loader2 className="w-4 h-4 animate-spin" />
                                Dispensing...
                              </>
                            ) : (
                              <>
                                <Pill className="w-4 h-4" />
                                Dispense
                              </>
                            )}
                          </button>
                        )}
                        {hasError && (
                          <p className="text-sm text-red-600">
                            Failed to dispense. Please try again.
                          </p>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
