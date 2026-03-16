"use client";

/**
 * Immunizations — View and record patient immunization history.
 * Route: /ehr/[patientId]/immunizations | pageTitle: "Immunizations"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Syringe, Plus, Loader2, ArrowLeft } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useImmunizations,
  useRecordImmunization,
  type ImmunizationResource,
} from "@/hooks/queries/useImmunizations";

/* ------------------------------------------------------------------ */
/*  Badge helpers                                                      */
/* ------------------------------------------------------------------ */

const STATUS_BADGE: Record<string, string> = {
  COMPLETED: "bg-green-100 text-green-700",
  NOT_DONE: "bg-gray-100 text-gray-600",
  ENTERED_IN_ERROR: "bg-red-100 text-red-700",
};

/* ------------------------------------------------------------------ */
/*  Empty form state                                                   */
/* ------------------------------------------------------------------ */

const EMPTY_FORM = {
  vaccine_name: "",
  vaccine_code: "",
  dose_number: "",
  dose_sequence: "",
  lot_number: "",
  site: "",
  route: "",
  administered_by: "",
  notes: "",
};

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function ImmunizationsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data: immunizationsData, isLoading } = useImmunizations(patientId);
  const recordImmunization = useRecordImmunization();

  const immunizations: ImmunizationResource[] = immunizationsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    recordImmunization.mutate(
      {
        patientId,
        vaccineName: form.vaccine_name,
        vaccineCode: form.vaccine_code.trim() || null,
        doseNumber: form.dose_number ? Number(form.dose_number) : null,
        doseSequence: form.dose_sequence.trim() || null,
        lotNumber: form.lot_number.trim() || null,
        site: form.site.trim() || null,
        route: form.route.trim() || null,
        administeredAt: new Date().toISOString(),
        notes: form.notes.trim() || null,
        administered_by: form.administered_by,
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
      <PageShell title="Immunizations" subtitle="Patient immunization records">
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
            <span className="ml-2 text-sm text-gray-500">Loading immunizations...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Syringe className="w-5 h-5 text-cyan-600" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Immunizations ({immunizations.length})
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Record Immunization
              </button>
            </div>

            {/* Record Immunization form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-4">Record Immunization</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Vaccine Name
                      </label>
                      <input
                        type="text"
                        value={form.vaccine_name}
                        onChange={(e) => updateField("vaccine_name", e.target.value)}
                        placeholder="e.g. Influenza"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Vaccine Code
                      </label>
                      <input
                        type="text"
                        value={form.vaccine_code}
                        onChange={(e) => updateField("vaccine_code", e.target.value)}
                        placeholder="e.g. CVX-141"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Dose Number
                      </label>
                      <input
                        type="number"
                        min="1"
                        value={form.dose_number}
                        onChange={(e) => updateField("dose_number", e.target.value)}
                        placeholder="1"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Dose Sequence
                      </label>
                      <input
                        type="text"
                        value={form.dose_sequence}
                        onChange={(e) => updateField("dose_sequence", e.target.value)}
                        placeholder="e.g. 1 of 3"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Lot Number
                      </label>
                      <input
                        type="text"
                        value={form.lot_number}
                        onChange={(e) => updateField("lot_number", e.target.value)}
                        placeholder="e.g. AB1234"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Site
                      </label>
                      <input
                        type="text"
                        value={form.site}
                        onChange={(e) => updateField("site", e.target.value)}
                        placeholder="e.g. Left deltoid"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Route
                      </label>
                      <input
                        type="text"
                        value={form.route}
                        onChange={(e) => updateField("route", e.target.value)}
                        placeholder="e.g. Intramuscular"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Administered By
                      </label>
                      <input
                        type="text"
                        value={form.administered_by}
                        onChange={(e) => updateField("administered_by", e.target.value)}
                        placeholder="Nurse Jane"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div className="md:col-span-2 lg:col-span-3">
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Notes
                      </label>
                      <textarea
                        value={form.notes}
                        onChange={(e) => updateField("notes", e.target.value)}
                        rows={2}
                        placeholder="Additional notes..."
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={recordImmunization.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {recordImmunization.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Save Immunization
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

                  {recordImmunization.isError && (
                    <p className="text-sm text-red-600">
                      Failed to record immunization. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Immunizations table */}
            {immunizations.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Syringe className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No immunizations recorded yet</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Vaccine</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Dose #</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Sequence</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Lot Number</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Site</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Route</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Administered By</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {immunizations.map((imm) => {
                        const a = imm.attributes;
                        return (
                          <tr
                            key={imm.id}
                            className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                          >
                            <td className="px-4 py-3 font-medium text-gray-900">{a.vaccineName}</td>
                            <td className="px-4 py-3 text-gray-700">{a.doseNumber ?? "—"}</td>
                            <td className="px-4 py-3 text-gray-700">{a.doseSequence ?? "—"}</td>
                            <td className="px-4 py-3 text-gray-700 font-mono text-xs">
                              {a.lotNumber ?? "—"}
                            </td>
                            <td className="px-4 py-3 text-gray-700">{a.site ?? "—"}</td>
                            <td className="px-4 py-3 text-gray-700">{a.route ?? "—"}</td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  STATUS_BADGE[a.status] ?? "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {a.status}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-gray-700">{a.administeredBy}</td>
                            <td className="px-4 py-3 text-gray-700">
                              {new Date(a.administeredAt).toLocaleDateString()}
                            </td>
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
