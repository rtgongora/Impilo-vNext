"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, FileText, Loader2, Plus, Stethoscope, Syringe } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { usePatient } from "@/hooks/queries/usePatients";
import { ImmunisationForecastPanel } from "@/features/paediatrics/immunisation/ImmunisationForecastPanel";
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
  NOT_DONE: "bg-neutral-100 text-muted-foreground",
  ENTERED_IN_ERROR: "bg-red-100 text-danger",
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
  const { data: immunizationsData, isLoading, isError: immunizationsError } = useImmunizations(patientId);
  const patient = usePatient(patientId);
  const recordImmunization = useRecordImmunization();

  const immunizations: ImmunizationResource[] = immunizationsData?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.isOpen
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
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading immunizations...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/*
              What is due, from the national EPI schedule.
              Placed above the record because "what should this child get today" is the question
              the contact exists to answer, and the dose list below is the evidence for it. The
              engine decides; this surface only refuses to make a withheld dose look giveable.
            */}
            <ImmunisationForecastPanel
              patientId={patientId}
              dateOfBirth={
                typeof patient.data?.data?.attributes?.dateOfBirth === "string"
                  ? patient.data.data.attributes.dateOfBirth
                  : null
              }
              sex={
                typeof patient.data?.data?.attributes?.sex === "string"
                  ? patient.data.data.attributes.sex
                  : null
              }
              doses={immunizations.map((entry) => ({
                vaccineCode: entry.attributes.vaccineCode ?? entry.attributes.vaccineName ?? null,
                doseNumber: entry.attributes.doseNumber ?? null,
                administeredAt: entry.attributes.administeredAt ?? null,
                status: entry.attributes.status ?? null,
              }))}
              historyUnavailable={immunizationsError}
            />

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

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Immunization continuity
              </p>
              <p className="mt-2 text-sm text-foreground">
                {activeEncounter
                  ? "New immunizations recorded here are now sent with the active encounter and facility context so the vaccine update stays inside the current clinical episode."
                  : "You can still review and record immunizations here, but there is no active encounter in scope to bind the update to the current visit."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use History for narrative review, then return here to record the dose without leaving the patient workspace.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Syringe className="h-5 w-5 text-cyan-600" />
                <h2 className="text-lg font-semibold text-foreground">Immunizations ({immunizations.length})</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
              >
                <Plus className="h-4 w-4" />
                Record Immunization
              </button>
            </div>

            {showForm && (
              <div className="rounded-lg border border-border bg-card p-5">
                <h3 className="mb-4 font-medium text-foreground">Record Immunization</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Vaccine Name</label>
                      <input
                        type="text"
                        value={form.vaccine_name}
                        onChange={(e) => updateField("vaccine_name", e.target.value)}
                        placeholder="e.g. Influenza"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Vaccine Code</label>
                      <input
                        type="text"
                        value={form.vaccine_code}
                        onChange={(e) => updateField("vaccine_code", e.target.value)}
                        placeholder="e.g. CVX-141"
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Dose Number</label>
                      <input
                        type="number"
                        min="1"
                        value={form.dose_number}
                        onChange={(e) => updateField("dose_number", e.target.value)}
                        placeholder="1"
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Dose Sequence</label>
                      <input
                        type="text"
                        value={form.dose_sequence}
                        onChange={(e) => updateField("dose_sequence", e.target.value)}
                        placeholder="e.g. 1 of 3"
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Lot Number</label>
                      <input
                        type="text"
                        value={form.lot_number}
                        onChange={(e) => updateField("lot_number", e.target.value)}
                        placeholder="e.g. AB1234"
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Site</label>
                      <input
                        type="text"
                        value={form.site}
                        onChange={(e) => updateField("site", e.target.value)}
                        placeholder="e.g. Left deltoid"
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Route</label>
                      <input
                        type="text"
                        value={form.route}
                        onChange={(e) => updateField("route", e.target.value)}
                        placeholder="e.g. Intramuscular"
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Administered By</label>
                      <input
                        type="text"
                        value={form.administered_by}
                        onChange={(e) => updateField("administered_by", e.target.value)}
                        placeholder="Nurse Jane"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div className="md:col-span-2 lg:col-span-3">
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Notes</label>
                      <textarea
                        value={form.notes}
                        onChange={(e) => updateField("notes", e.target.value)}
                        rows={2}
                        placeholder="Additional notes..."
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={recordImmunization.isPending}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50"
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
                      className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-background"
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
              <div className="rounded-lg border border-border bg-card p-12 text-center">
                <Syringe className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No immunizations recorded yet</p>
              </div>
            ) : (
              <div className="overflow-hidden rounded-lg border border-border bg-card">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background">
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Vaccine</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Dose #</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Sequence</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Lot Number</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Site</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Route</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Status</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Administered By</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {immunizations.map((immunization) => {
                        const attrs = immunization.attributes;
                        return (
                          <tr key={immunization.id} className="border-b border-border transition-colors hover:bg-background">
                            <td className="px-4 py-3 font-medium text-foreground">{attrs.vaccineName}</td>
                            <td className="px-4 py-3 text-foreground">{attrs.doseNumber ?? "-"}</td>
                            <td className="px-4 py-3 text-foreground">{attrs.doseSequence ?? "-"}</td>
                            <td className="px-4 py-3 font-mono text-xs text-foreground">{attrs.lotNumber ?? "-"}</td>
                            <td className="px-4 py-3 text-foreground">{attrs.site ?? "-"}</td>
                            <td className="px-4 py-3 text-foreground">{attrs.route ?? "-"}</td>
                            <td className="px-4 py-3">
                              <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGE[attrs.status] ?? "bg-neutral-100 text-muted-foreground"}`}>
                                {attrs.status}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-foreground">{attrs.administeredBy}</td>
                            <td className="px-4 py-3 text-foreground">{new Date(attrs.administeredAt).toLocaleDateString()}</td>
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
