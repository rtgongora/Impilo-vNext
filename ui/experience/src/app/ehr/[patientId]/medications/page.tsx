"use client";

/**
 * Medications — Lovable-aligned prescribing with route, instructions,
 * encounter/facility context, and dispense action.
 * Route: /ehr/[patientId]/medications | pageTitle: "Medications"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Pill, Plus, Loader2, CheckCircle2, Save } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useEncounters } from "@/hooks/queries/useEncounters";

interface PrescriptionResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

const STATUS_BADGE: Record<string, string> = {
  PENDING: "bg-blue-100 text-blue-700",
  ACTIVE: "bg-green-100 text-green-700",
  DISPENSED: "bg-purple-100 text-purple-700",
  CANCELLED: "bg-gray-100 text-gray-600",
};

const ROUTES = [
  { value: "oral", label: "Oral (PO)" },
  { value: "iv", label: "Intravenous (IV)" },
  { value: "im", label: "Intramuscular (IM)" },
  { value: "sc", label: "Subcutaneous (SC)" },
  { value: "topical", label: "Topical" },
  { value: "inhaled", label: "Inhaled" },
  { value: "rectal", label: "Rectal (PR)" },
  { value: "sublingual", label: "Sublingual (SL)" },
  { value: "ophthalmic", label: "Ophthalmic" },
  { value: "nasal", label: "Nasal" },
];

const FREQUENCIES = [
  "Once daily (OD)", "Twice daily (BD)", "Three times daily (TDS)",
  "Four times daily (QDS)", "Every 4 hours (Q4H)", "Every 6 hours (Q6H)",
  "Every 8 hours (Q8H)", "Every 12 hours (Q12H)", "At bedtime (Nocte)",
  "As needed (PRN)", "Stat (once only)",
];

function usePatientPrescriptions(patientId: string) {
  return useQuery<ApiResponse<PrescriptionResource[]>>({
    queryKey: ["prescriptions", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
}

export default function MedicationsPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const queryClient = useQueryClient();
  const { isPrescriber, isDispenser } = useRoleGroup();
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );

  const { data: prescriptionsData, isLoading } = usePatientPrescriptions(patientId);
  const prescriptions = prescriptionsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    medication_name: "",
    generic_name: "",
    dosage: "",
    route: "oral",
    frequency: "Twice daily (BD)",
    duration: "",
    quantity: 1,
    instructions: "",
    indication: "",
    prescribed_by: user?.displayName ?? user?.email ?? "",
  });
  const [submitting, setSubmitting] = useState(false);
  const [dispensing, setDispensing] = useState<string | null>(null);

  function updateField(field: string, value: string | number) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await apiClient.post("/internal/v1/pharmacy/prescriptions", {
        patient_id: patientId,
        facility_id: facility?.id ?? null,
        encounter_id: activeEncounter?.id ?? null,
        medication_name: form.medication_name,
        generic_name: form.generic_name || null,
        dosage: form.dosage,
        route: form.route,
        frequency: form.frequency,
        duration: form.duration,
        quantity: form.quantity,
        instructions: form.instructions || null,
        indication: form.indication || null,
        prescribed_by: form.prescribed_by,
      });
      queryClient.invalidateQueries({ queryKey: ["prescriptions", { patientId }] });
      setForm({
        medication_name: "", generic_name: "", dosage: "", route: "oral",
        frequency: "Twice daily (BD)", duration: "", quantity: 1,
        instructions: "", indication: "",
        prescribed_by: user?.displayName ?? user?.email ?? "",
      });
      setShowForm(false);
    } catch {
      // Error handled by UI
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDispense(rxId: string) {
    setDispensing(rxId);
    try {
      await apiClient.post("/internal/v1/pharmacy/dispense", {
        prescription_id: rxId,
        dispensed_by: user?.id ?? "system",
      });
      queryClient.invalidateQueries({ queryKey: ["prescriptions", { patientId }] });
    } catch {
      // Error handled by UI
    } finally {
      setDispensing(null);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="Medications">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : (
          <div className="space-y-5">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Pill className="w-5 h-5 text-green-500" />
                <h2 className="text-sm font-semibold text-gray-900">
                  Prescriptions ({prescriptions.length})
                </h2>
              </div>
              {isPrescriber && (
              <button
                onClick={() => setShowForm((v) => !v)}
                className="inline-flex items-center gap-1.5 px-3 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Prescribe
              </button>
              )}
            </div>

            {/* Prescribing Form */}
            {showForm && (
              <form onSubmit={handleSubmit} className="bg-white rounded-lg border border-gray-200 p-5 space-y-4">
                <h3 className="text-sm font-medium text-gray-900 flex items-center gap-2">
                  <Pill className="w-4 h-4 text-green-500" />
                  New Prescription
                </h3>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Medication Name *</label>
                    <input type="text" required value={form.medication_name} onChange={(e) => updateField("medication_name", e.target.value)}
                      placeholder="e.g. Amoxicillin 500mg"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Generic Name</label>
                    <input type="text" value={form.generic_name} onChange={(e) => updateField("generic_name", e.target.value)}
                      placeholder="e.g. Amoxicillin"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Dosage *</label>
                    <input type="text" required value={form.dosage} onChange={(e) => updateField("dosage", e.target.value)}
                      placeholder="e.g. 500mg, 10ml"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Route</label>
                    <select value={form.route} onChange={(e) => updateField("route", e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500">
                      {ROUTES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Frequency</label>
                    <select value={form.frequency} onChange={(e) => updateField("frequency", e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500">
                      {FREQUENCIES.map((f) => <option key={f} value={f}>{f}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Duration</label>
                    <input type="text" value={form.duration} onChange={(e) => updateField("duration", e.target.value)}
                      placeholder="e.g. 5 days, 2 weeks"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Quantity</label>
                    <input type="number" min={1} value={form.quantity} onChange={(e) => updateField("quantity", parseInt(e.target.value) || 1)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Indication</label>
                    <input type="text" value={form.indication} onChange={(e) => updateField("indication", e.target.value)}
                      placeholder="e.g. UTI, Hypertension"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Special Instructions</label>
                  <textarea value={form.instructions} onChange={(e) => updateField("instructions", e.target.value)}
                    rows={2} placeholder="e.g. Take with food, complete full course"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 resize-none" />
                </div>
                <div className="flex gap-3">
                  <button type="button" onClick={() => setShowForm(false)}
                    className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors">
                    Cancel
                  </button>
                  <button type="submit" disabled={submitting}
                    className="flex-1 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                    {submitting ? <><Loader2 className="w-4 h-4 animate-spin" /> Prescribing...</> : <><Save className="w-4 h-4" /> Prescribe</>}
                  </button>
                </div>
              </form>
            )}

            {/* Prescription List */}
            {prescriptions.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Pill className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No prescriptions for this patient</p>
              </div>
            ) : (
              <div className="space-y-3">
                {prescriptions.map((rx) => {
                  const a = rx.attributes;
                  const statusStyle = STATUS_BADGE[String(a.status)] ?? "bg-gray-100 text-gray-600";
                  const isPending = a.status === "PENDING" || a.status === "ACTIVE";

                  return (
                    <div key={rx.id} className="bg-white rounded-lg border border-gray-200 p-4">
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-sm font-semibold text-gray-900">
                              {String(a.medication_name ?? a.medicationName ?? "")}
                            </span>
                            <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>
                              {String(a.status)}
                            </span>
                          </div>
                          <div className="flex items-center gap-3 text-xs text-gray-500">
                            {typeof a.dosage === "string" && <span>{String(a.dosage)}</span>}
                            {typeof a.route === "string" && <span className="capitalize">{String(a.route)}</span>}
                            {typeof a.frequency === "string" && <span>{String(a.frequency)}</span>}
                            {typeof a.duration === "string" && <span>{String(a.duration)}</span>}
                          </div>
                          {typeof a.instructions === "string" && (
                            <p className="text-xs text-gray-500 mt-1 italic">{String(a.instructions)}</p>
                          )}
                          {typeof a.indication === "string" && (
                            <p className="text-xs text-gray-400 mt-0.5">Indication: {String(a.indication)}</p>
                          )}
                          <p className="text-xs text-gray-400 mt-1">
                            Prescribed by: {String(a.prescribed_by ?? a.prescribedBy ?? "")}
                            {typeof a.created_at === "string" && ` · ${new Date(String(a.created_at ?? a.createdAt)).toLocaleDateString()}`}
                          </p>
                          {a.status === "DISPENSED" && (
                            <p className="text-xs text-purple-600 mt-0.5">
                              Dispensed by: {String(a.dispensed_by ?? a.dispensedBy ?? "")}
                              {typeof (a.dispensed_at ?? a.dispensedAt) === "string" && ` · ${new Date(String(a.dispensed_at ?? a.dispensedAt)).toLocaleString()}`}
                            </p>
                          )}
                        </div>
                        {isPending && (
                          <div className="flex flex-col gap-1">
                            <button
                              onClick={() => handleDispense(rx.id)}
                              disabled={dispensing === rx.id}
                              className="px-3 py-1.5 bg-purple-100 text-purple-700 text-xs font-medium rounded-lg hover:bg-purple-200 disabled:opacity-50 transition-colors flex items-center gap-1"
                            >
                              {dispensing === rx.id ? (
                                <Loader2 className="w-3 h-3 animate-spin" />
                              ) : (
                                <CheckCircle2 className="w-3 h-3" />
                              )}
                              Dispense
                            </button>
                            <button
                              onClick={async () => {
                                try {
                                  await apiClient.post(`/internal/v1/pharmacy/prescriptions/${rx.id}/cancel`);
                                  queryClient.invalidateQueries({ queryKey: ["prescriptions", { patientId }] });
                                } catch { /* handled */ }
                              }}
                              className="px-3 py-1.5 bg-red-50 text-red-600 text-xs font-medium rounded-lg hover:bg-red-100 transition-colors"
                            >
                              Cancel
                            </button>
                          </div>
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
    </EHRLayout>
  );
}
