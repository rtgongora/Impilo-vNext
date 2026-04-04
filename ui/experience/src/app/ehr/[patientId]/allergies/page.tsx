"use client";

/**
 * Allergies — View and record patient allergies.
 * Route: /ehr/[patientId]/allergies | pageTitle: "Allergies"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ShieldAlert, Plus, Loader2, ArrowLeft } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAllergies,
  useCreateAllergy,
  type AllergyResource,
} from "@/hooks/queries/useAllergies";
import { useAuthStore } from "@/hooks/useAuthStore";

/* ------------------------------------------------------------------ */
/*  Badge helpers                                                      */
/* ------------------------------------------------------------------ */

const TYPE_BADGE: Record<string, string> = {
  MEDICATION: "bg-purple-100 text-purple-700",
  FOOD: "bg-orange-100 text-orange-700",
  ENVIRONMENTAL: "bg-teal-100 text-teal-700",
};

const SEVERITY_BADGE: Record<string, string> = {
  MILD: "bg-green-100 text-green-700",
  MODERATE: "bg-yellow-100 text-yellow-700",
  SEVERE: "bg-red-100 text-red-700",
};

/* ------------------------------------------------------------------ */
/*  Empty form state                                                   */
/* ------------------------------------------------------------------ */

const EMPTY_FORM = {
  allergen: "",
  allergen_type: "MEDICATION",
  reaction: "",
  severity: "MILD",
  onset_date: "",
  recorded_by: "",
};

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function AllergiesPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const { data: allergiesData, isLoading } = useAllergies(patientId);
  const createAllergy = useCreateAllergy();

  const allergies: AllergyResource[] = allergiesData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM, recorded_by: user?.displayName ?? user?.email ?? "" });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    createAllergy.mutate(
      {
        patientId,
        allergen: form.allergen,
        allergenType: form.allergen_type,
        reaction: form.reaction.trim() || null,
        severity: form.severity,
        onsetDate: form.onset_date || null,
        recorded_by: form.recorded_by,
      },
      {
        onSuccess: () => {
          setForm({ ...EMPTY_FORM });
          setShowForm(false);
        },
      },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Allergies" subtitle="Patient allergy records and management">
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
            <span className="ml-2 text-sm text-gray-500">Loading allergies...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-red-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Allergies ({allergies.length})
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Add Allergy
              </button>
            </div>

            {/* Add Allergy form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-4">New Allergy</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Allergen
                      </label>
                      <input
                        type="text"
                        value={form.allergen}
                        onChange={(e) => updateField("allergen", e.target.value)}
                        placeholder="e.g. Penicillin"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Allergen Type
                      </label>
                      <select
                        value={form.allergen_type}
                        onChange={(e) => updateField("allergen_type", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="MEDICATION">Medication</option>
                        <option value="FOOD">Food</option>
                        <option value="ENVIRONMENTAL">Environmental</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Reaction
                      </label>
                      <input
                        type="text"
                        value={form.reaction}
                        onChange={(e) => updateField("reaction", e.target.value)}
                        placeholder="e.g. Rash, Anaphylaxis"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Severity
                      </label>
                      <select
                        value={form.severity}
                        onChange={(e) => updateField("severity", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="MILD">Mild</option>
                        <option value="MODERATE">Moderate</option>
                        <option value="SEVERE">Severe</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Onset Date
                      </label>
                      <input
                        type="date"
                        value={form.onset_date}
                        onChange={(e) => updateField("onset_date", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Recorded By
                      </label>
                      <input
                        type="text"
                        value={form.recorded_by}
                        onChange={(e) => updateField("recorded_by", e.target.value)}
                        placeholder="Dr. Jane Smith"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={createAllergy.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {createAllergy.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Save Allergy
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setForm({ ...EMPTY_FORM });
                        setShowForm(false);
                      }}
                      className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>

                  {createAllergy.isError && (
                    <p className="text-sm text-red-600">
                      Failed to record allergy. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Allergies table */}
            {allergies.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <ShieldAlert className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No allergies recorded yet</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Allergen</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Severity</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Reaction</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Onset Date</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {allergies.map((allergy) => {
                        const a = allergy.attributes;
                        return (
                          <tr
                            key={allergy.id}
                            className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                          >
                            <td className="px-4 py-3 font-medium text-gray-900">{a.allergen}</td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  TYPE_BADGE[a.allergenType] ?? "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {a.allergenType}
                              </span>
                            </td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  SEVERITY_BADGE[a.severity] ?? "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {a.severity}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-gray-700">{a.reaction ?? "—"}</td>
                            <td className="px-4 py-3 text-gray-700">
                              {a.onsetDate
                                ? new Date(a.onsetDate).toLocaleDateString()
                                : "—"}
                            </td>
                            <td className="px-4 py-3 text-gray-700">{a.status}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
