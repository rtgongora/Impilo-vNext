"use client";

/**
 * Encounters — View encounter history and start new encounters.
 * Route: /ehr/[patientId]/encounters | pageTitle: "Encounters"
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ClipboardList, Plus, Loader2} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useEncounters,
  useCreateEncounter,
  type EncounterResource } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

/* ------------------------------------------------------------------ */
/*  Badge helpers                                                      */
/* ------------------------------------------------------------------ */

const STATUS_BADGE: Record<string, string> = {
  IN_PROGRESS: "bg-green-100 text-green-700",
  ACTIVE: "bg-green-100 text-green-700",
  COMPLETED: "bg-gray-100 text-gray-600",
  DISCHARGED: "bg-blue-100 text-blue-700",
  ADMITTED: "bg-purple-100 text-purple-700",
  TRANSFERRED: "bg-amber-100 text-amber-700",
  REFERRED: "bg-indigo-100 text-indigo-700",
  DECEASED: "bg-red-100 text-red-700",
  LAMA: "bg-gray-200 text-gray-700",
  CANCELLED: "bg-red-100 text-red-700" };

/* ------------------------------------------------------------------ */
/*  Empty form state                                                   */
/* ------------------------------------------------------------------ */

const EMPTY_FORM = {
  encounter_type: "OUTPATIENT",
  chief_complaint: "" };

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function EncountersPage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const patientId = params.patientId;

  const facility = useFacilityStore((s) => s.facility);
  const { data: encountersData, isLoading } = useEncounters(patientId);
  const createEncounter = useCreateEncounter();

  const encounters: EncounterResource[] = encountersData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    createEncounter.mutate(
      {
        patientId,
        facilityId: facility?.id ?? "",
        encounterType: form.encounter_type,
        chief_complaint: form.chief_complaint.trim() || undefined },
      {
        onSuccess: (data) => {
          setForm({ ...EMPTY_FORM });
          setShowForm(false);
          const newEncounterId = data?.data?.id;
          if (newEncounterId) {
            router.push(`/ehr/${patientId}/encounter/${newEncounterId}`);
          }
        } },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Encounters" subtitle="Patient encounter history">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading encounters...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="w-5 h-5 text-blue-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Encounters ({encounters.length})
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Start Encounter
              </button>
            </div>

            {/* Start Encounter form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-4">Start New Encounter</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Encounter Type
                      </label>
                      <select
                        value={form.encounter_type}
                        onChange={(e) => updateField("encounter_type", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="OUTPATIENT">Outpatient</option>
                        <option value="INPATIENT">Inpatient</option>
                        <option value="EMERGENCY">Emergency</option>
                        <option value="TELEHEALTH">Telehealth</option>
                        <option value="HOME_VISIT">Home Visit</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Chief Complaint
                      </label>
                      <input
                        type="text"
                        value={form.chief_complaint}
                        onChange={(e) => updateField("chief_complaint", e.target.value)}
                        placeholder="e.g. Chest pain, headache"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={createEncounter.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {createEncounter.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Start Encounter
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

                  {createEncounter.isError && (
                    <p className="text-sm text-red-600">
                      Failed to create encounter. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Encounters table */}
            {encounters.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <ClipboardList className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No encounters recorded yet</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Start Date</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">End Date</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Chief Complaint</th>
                      </tr>
                    </thead>
                    <tbody>
                      {encounters.map((encounter) => {
                        const a = encounter.attributes;
                        return (
                          <tr
                            key={encounter.id}
                            className="border-b border-gray-100 hover:bg-gray-50 transition-colors cursor-pointer"
                            onClick={() =>
                              router.push(
                                `/ehr/${patientId}/encounter/${encounter.id}`,
                              )
                            }
                          >
                            <td className="px-4 py-3">
                              <Link
                                href={`/ehr/${patientId}/encounter/${encounter.id}`}
                                className="font-medium text-blue-600 hover:text-blue-800 hover:underline"
                              >
                                {a.encounterType.replace(/_/g, " ")}
                              </Link>
                            </td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  STATUS_BADGE[a.status] ?? "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {a.status.replace(/_/g, " ")}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {new Date(a.startedAt).toLocaleString()}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {a.closedAt
                                ? new Date(a.closedAt).toLocaleString()
                                : "—"}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {(a as Record<string, unknown>).chiefComplaint as string ?? (a as Record<string, unknown>).chief_complaint as string ?? "—"}
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
