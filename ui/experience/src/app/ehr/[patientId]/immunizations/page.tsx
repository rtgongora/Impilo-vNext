"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, FileText, Loader2, Plus, Stethoscope, Syringe } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useImmunizations,
  useRecordImmunization,
  type ImmunizationResource,
} from "@/hooks/queries/useImmunizations";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const STATUS_BADGE: Record<string, string> = {
  COMPLETED: "bg-green-100 text-green-700",
  NOT_DONE: "bg-gray-100 text-gray-600",
  ENTERED_IN_ERROR: "bg-red-100 text-red-700",
};

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

export default function ImmunizationsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);
  const { data: immunizationsData, isLoading } = useImmunizations(patientId);
  const recordImmunization = useRecordImmunization();

  const immunizations: ImmunizationResource[] = immunizationsData?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const completedCount = immunizations.filter((immunization) => immunization.attributes.status === "COMPLETED").length;
  const uniqueVaccines = new Set(immunizations.map((immunization) => immunization.attributes.vaccineName)).size;
  const recentDoses = immunizations.filter((immunization) => {
    const administeredAt = new Date(immunization.attributes.administeredAt).getTime();
    return administeredAt >= Date.now() - 1000 * 60 * 60 * 24 * 365;
  }).length;
  const latestDose = [...immunizations].sort(
    (left, right) =>
      new Date(right.attributes.administeredAt).getTime() -
      new Date(left.attributes.administeredAt).getTime()
  )[0];

  const buildFormState = () => ({
    ...EMPTY_FORM,
    administered_by: user?.displayName ?? user?.email ?? "",
  });

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(buildFormState);

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
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
        encounter_id: activeEncounter?.id ?? null,
        facility_id: facility?.id ?? null,
      },
      {
        onSuccess: () => {
          setForm(buildFormState());
          setShowForm(false);
        },
      }
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Immunizations" subtitle="Patient immunization records">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading immunizations...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Immunization review"
              badgeIcon={Syringe}
              title="Confirm vaccine history inside the same review flow before the encounter moves on"
              description="Immunizations now carry the same facility and encounter context as the rest of longitudinal review: confirm what is documented, record new doses in place, and jump back to history, notes, or summary without breaking continuity."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/history`, label: "History", icon: FileText },
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity, tone: "secondary" },
                { href: `/ehr/${patientId}/conditions`, label: "Conditions", icon: Stethoscope, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Documented doses",
                  value: String(completedCount),
                  detail: "Completed immunization records on file for this patient.",
                },
                {
                  label: "Vaccine series",
                  value: String(uniqueVaccines),
                  detail: "Distinct vaccines represented in the longitudinal record.",
                },
                {
                  label: "Recent doses",
                  value: String(recentDoses),
                  detail: latestDose
                    ? `Most recent dose recorded ${new Date(latestDose.attributes.administeredAt).toLocaleDateString()}.`
                    : "No recent administration date is available yet.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Immunization continuity
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {activeEncounter
                  ? "New immunizations recorded here are now sent with the active encounter and facility context so the vaccine update stays inside the current clinical episode."
                  : "You can still review and record immunizations here, but there is no active encounter in scope to bind the update to the current visit."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use History for narrative review, then return here to record the dose without leaving the patient workspace.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Syringe className="h-5 w-5 text-cyan-600" />
                <h2 className="text-lg font-semibold text-gray-900">Immunizations ({immunizations.length})</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700"
              >
                <Plus className="h-4 w-4" />
                Record Immunization
              </button>
            </div>

            {showForm && (
              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <h3 className="mb-4 font-medium text-gray-900">Record Immunization</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Vaccine Name</label>
                      <input
                        type="text"
                        value={form.vaccine_name}
                        onChange={(e) => updateField("vaccine_name", e.target.value)}
                        placeholder="e.g. Influenza"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Vaccine Code</label>
                      <input
                        type="text"
                        value={form.vaccine_code}
                        onChange={(e) => updateField("vaccine_code", e.target.value)}
                        placeholder="e.g. CVX-141"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Dose Number</label>
                      <input
                        type="number"
                        min="1"
                        value={form.dose_number}
                        onChange={(e) => updateField("dose_number", e.target.value)}
                        placeholder="1"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Dose Sequence</label>
                      <input
                        type="text"
                        value={form.dose_sequence}
                        onChange={(e) => updateField("dose_sequence", e.target.value)}
                        placeholder="e.g. 1 of 3"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Lot Number</label>
                      <input
                        type="text"
                        value={form.lot_number}
                        onChange={(e) => updateField("lot_number", e.target.value)}
                        placeholder="e.g. AB1234"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Site</label>
                      <input
                        type="text"
                        value={form.site}
                        onChange={(e) => updateField("site", e.target.value)}
                        placeholder="e.g. Left deltoid"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Route</label>
                      <input
                        type="text"
                        value={form.route}
                        onChange={(e) => updateField("route", e.target.value)}
                        placeholder="e.g. Intramuscular"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Administered By</label>
                      <input
                        type="text"
                        value={form.administered_by}
                        onChange={(e) => updateField("administered_by", e.target.value)}
                        placeholder="Nurse Jane"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div className="md:col-span-2 lg:col-span-3">
                      <label className="mb-1 block text-xs font-medium text-gray-600">Notes</label>
                      <textarea
                        value={form.notes}
                        onChange={(e) => updateField("notes", e.target.value)}
                        rows={2}
                        placeholder="Additional notes..."
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={recordImmunization.isPending}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {recordImmunization.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                      Save Immunization
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setForm(buildFormState());
                        setShowForm(false);
                      }}
                      className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
                    >
                      Cancel
                    </button>
                  </div>

                  {recordImmunization.isError && (
                    <p className="text-sm text-red-600">Failed to record immunization. Please try again.</p>
                  )}
                </form>
              </div>
            )}

            {immunizations.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <Syringe className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No immunizations recorded yet</p>
              </div>
            ) : (
              <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Vaccine</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Dose #</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Sequence</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Lot Number</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Site</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Route</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Status</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Administered By</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {immunizations.map((immunization) => {
                        const attrs = immunization.attributes;
                        return (
                          <tr key={immunization.id} className="border-b border-gray-100 transition-colors hover:bg-gray-50">
                            <td className="px-4 py-3 font-medium text-gray-900">{attrs.vaccineName}</td>
                            <td className="px-4 py-3 text-gray-700">{attrs.doseNumber ?? "-"}</td>
                            <td className="px-4 py-3 text-gray-700">{attrs.doseSequence ?? "-"}</td>
                            <td className="px-4 py-3 font-mono text-xs text-gray-700">{attrs.lotNumber ?? "-"}</td>
                            <td className="px-4 py-3 text-gray-700">{attrs.site ?? "-"}</td>
                            <td className="px-4 py-3 text-gray-700">{attrs.route ?? "-"}</td>
                            <td className="px-4 py-3">
                              <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGE[attrs.status] ?? "bg-gray-100 text-gray-600"}`}>
                                {attrs.status}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-gray-700">{attrs.administeredBy}</td>
                            <td className="px-4 py-3 text-gray-700">{new Date(attrs.administeredAt).toLocaleDateString()}</td>
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
