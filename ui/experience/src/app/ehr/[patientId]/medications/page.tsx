"use client";

/**
 * Medications — View existing prescriptions and create new ones.
 * Route: /ehr/[patientId]/medications | pageTitle: "Medications"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Pill, Plus, Loader2, ArrowLeft } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

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
  created_at: string;
}

type PrescriptionsResponse = ApiResponse<Prescription[]>;

/* ------------------------------------------------------------------ */
/*  Inline hook — fetches prescriptions for a patient                  */
/* ------------------------------------------------------------------ */

function usePatientPrescriptions(patientId: string) {
  return useQuery<PrescriptionsResponse>({
    queryKey: ["prescriptions", { patientId }],
    queryFn: () =>
      apiClient.get<PrescriptionsResponse>(
        `/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`,
      ),
    enabled: !!patientId,
  });
}

/* ------------------------------------------------------------------ */
/*  Status badge colours                                               */
/* ------------------------------------------------------------------ */

const STATUS_BADGE: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  DISPENSED: "bg-green-100 text-green-700",
  CANCELLED: "bg-gray-100 text-gray-600",
};

/* ------------------------------------------------------------------ */
/*  Empty form state                                                   */
/* ------------------------------------------------------------------ */

const EMPTY_FORM = {
  medication_name: "",
  dosage: "",
  frequency: "",
  duration: "",
  quantity: 1,
  prescribed_by: "",
};

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function MedicationsPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;
  const { user } = useAuthStore();
  const queryClient = useQueryClient();

  const { data: prescriptionsData, isLoading } =
    usePatientPrescriptions(patientId);
  const prescriptions = prescriptionsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM, prescribed_by: user?.displayName ?? user?.email ?? "" });
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(false);

  function updateField(field: string, value: string | number) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setSubmitError(false);

    try {
      await apiClient.post("/internal/v1/pharmacy/prescriptions", {
        patient_id: patientId,
        medication_name: form.medication_name,
        dosage: form.dosage,
        frequency: form.frequency,
        duration: form.duration,
        quantity: form.quantity,
        prescribed_by: form.prescribed_by,
      });

      queryClient.invalidateQueries({
        queryKey: ["prescriptions", { patientId }],
      });
      setForm({ ...EMPTY_FORM });
      setShowForm(false);
    } catch {
      setSubmitError(true);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="Medications">
        {/* Back link */}
        <div className="mb-4">
          <Link
            href={`/ehr/${patientId}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to patient chart
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">
              Loading medications...
            </span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header with Add Prescription button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Pill className="w-5 h-5 text-indigo-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Prescriptions ({prescriptions.length})
                </h2>
              </div>
              <button
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Add Prescription
              </button>
            </div>

            {/* Add Prescription Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <Pill className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">
                    New Prescription
                  </h3>
                </div>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Medication Name
                      </label>
                      <input
                        type="text"
                        value={form.medication_name}
                        onChange={(e) =>
                          updateField("medication_name", e.target.value)
                        }
                        placeholder="e.g. Amoxicillin 500mg"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Dosage
                      </label>
                      <input
                        type="text"
                        value={form.dosage}
                        onChange={(e) => updateField("dosage", e.target.value)}
                        placeholder="e.g. 500mg"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Frequency
                      </label>
                      <input
                        type="text"
                        value={form.frequency}
                        onChange={(e) =>
                          updateField("frequency", e.target.value)
                        }
                        placeholder="e.g. 3 times daily"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Duration
                      </label>
                      <input
                        type="text"
                        value={form.duration}
                        onChange={(e) => updateField("duration", e.target.value)}
                        placeholder="e.g. 7 days"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Quantity
                      </label>
                      <input
                        type="number"
                        min={1}
                        value={form.quantity}
                        onChange={(e) =>
                          updateField("quantity", parseInt(e.target.value, 10) || 1)
                        }
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Prescribed By
                      </label>
                      <input
                        type="text"
                        value={form.prescribed_by}
                        onChange={(e) =>
                          updateField("prescribed_by", e.target.value)
                        }
                        placeholder="Dr. Jane Smith"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setShowForm(false);
                        setForm({ ...EMPTY_FORM });
                        setSubmitError(false);
                      }}
                      className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={submitting}
                      className="flex-1 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
                    >
                      {submitting ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Submitting...
                        </>
                      ) : (
                        <>
                          <Plus className="w-4 h-4" />
                          Submit Prescription
                        </>
                      )}
                    </button>
                  </div>
                  {submitError && (
                    <p className="text-sm text-red-600 text-center">
                      Failed to create prescription. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Prescriptions Card List */}
            {prescriptions.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Pill className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">
                  No prescriptions found
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4">
                {prescriptions.map((rx) => (
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
                      <span
                        className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                          STATUS_BADGE[rx.status] ?? "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {rx.status}
                      </span>
                    </div>
                    <div className="mt-3 flex items-center gap-4 text-xs text-gray-500">
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
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
