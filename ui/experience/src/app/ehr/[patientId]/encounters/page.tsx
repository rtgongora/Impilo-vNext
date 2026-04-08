"use client";

/**
 * Encounters — View encounter history and start new encounters.
 * Route: /ehr/[patientId]/encounters | pageTitle: "Encounters"
 */

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ClipboardList, Plus, Loader2, Receipt, ArrowRightLeft, Video, FileText } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useEncounters,
  useCreateEncounter,
  type EncounterResource } from "@/hooks/queries/useEncounters";
import { useReferrals } from "@/hooks/queries/useReferrals";
import { useClinicalNotes } from "@/hooks/queries/useClinicalNotes";
import { useTelemedicineSessions } from "@/hooks/queries/useTelemedicine";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { isReferralReceivingHere, parseConsultationCoordinationMeta } from "@/lib/consult-workflows";

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
  const { isClinical } = useRoleGroup();
  const { data: encountersData, isLoading } = useEncounters(patientId);
  const { data: referralsData } = useReferrals(patientId);
  const { data: notesData } = useClinicalNotes(patientId);
  const { data: telemedicineData } = useTelemedicineSessions({ patientId, facilityId: facility?.id });
  const createEncounter = useCreateEncounter();

  const encounters: EncounterResource[] = encountersData?.data ?? [];
  const referrals = referralsData?.data ?? [];
  const clinicalNotes = notesData?.data ?? [];
  const telemedicineSessions = telemedicineData?.data ?? [];
  const activeEncounter = encounters.find(
    (encounter) => encounter.attributes.status === "ACTIVE" || encounter.attributes.status === "IN_PROGRESS",
  );
  const coordinationPulse = useMemo(() => {
    const openReferrals = referrals.filter(
      (referral) => referral.attributes.status !== "COMPLETED" && referral.attributes.status !== "CANCELLED",
    ).length;
    const receivingHere = referrals.filter((referral) => isReferralReceivingHere(referral, facility)).length;
    const teleconsultActivity = telemedicineSessions.filter(
      (session) => session.attributes.status === "SCHEDULED" || session.attributes.status === "IN_PROGRESS",
    ).length;
    const returnedGuidance = clinicalNotes.filter(
      (note) =>
        note.attributes.noteType === "CONSULTATION" &&
        parseConsultationCoordinationMeta(note.attributes.body).hasReferralLoopUpdate,
    ).length;

    return { openReferrals, receivingHere, teleconsultActivity, returnedGuidance };
  }, [clinicalNotes, facility, referrals, telemedicineSessions]);

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
            <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_45%,#fffaf5_100%)] p-5 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                  <div className="inline-flex items-center gap-2 rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-slate-600">
                    <ArrowRightLeft className="h-3.5 w-3.5 text-blue-600" />
                    Encounter coordination
                  </div>
                  <div>
                    <h3 className="text-lg font-semibold text-slate-900">Encounter history now carries consult and teleconsult context</h3>
                    <p className="mt-1 max-w-2xl text-sm text-slate-600">
                      Past and active encounters stay connected to open referrals, returned specialist guidance, and teleconsult activity so handoffs remain visible during chart review.
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2 pt-1">
                    <Link
                      href={`/ehr/${patientId}/consults?tab=referrals`}
                      className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
                    >
                      <ArrowRightLeft className="h-4 w-4" />
                      Consults
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/consults?tab=teleconsults`}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                    >
                      <Video className="h-4 w-4" />
                      Teleconsults
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/notes`}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                    >
                      <FileText className="h-4 w-4" />
                      Notes Evidence
                    </Link>
                  </div>
                </div>

                <div className="grid gap-3 sm:grid-cols-2 xl:w-[28rem]">
                  <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Open referrals</p>
                    <p className="mt-2 text-2xl font-semibold text-purple-700">{coordinationPulse.openReferrals}</p>
                    <p className="mt-1 text-xs text-slate-500">Referrals still moving across encounter history.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Receiving here</p>
                    <p className="mt-2 text-2xl font-semibold text-blue-700">{coordinationPulse.receivingHere}</p>
                    <p className="mt-1 text-xs text-slate-500">Handoffs where this facility is the current receiver.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Teleconsult activity</p>
                    <p className="mt-2 text-2xl font-semibold text-emerald-700">{coordinationPulse.teleconsultActivity}</p>
                    <p className="mt-1 text-xs text-slate-500">Scheduled or live virtual sessions tied to this chart.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Returned guidance</p>
                    <p className="mt-2 text-2xl font-semibold text-indigo-700">{coordinationPulse.returnedGuidance}</p>
                    <p className="mt-1 text-xs text-slate-500">Consultation notes with structured referral-loop updates.</p>
                  </div>
                </div>
              </div>

              {activeEncounter && (
                <div className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                  Active encounter in progress: open the live encounter workspace for immediate charting, or stay here to review historical encounter context.
                </div>
              )}
            </div>

            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="w-5 h-5 text-blue-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Encounters ({encounters.length})
                </h2>
              </div>
              {isClinical && (
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Start Encounter
              </button>
              )}
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
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Bill</th>
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
                            <td className="px-4 py-3">
                              {a.costa_bill_id ? (
                                  <Link
                                  href={`/finance/billing/${a.costa_bill_id}?patientId=${patientId}&encounterId=${encounter.id}&source=encounters`}
                                    onClick={(e) => e.stopPropagation()}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors"
                                >
                                  <Receipt className="w-3 h-3" />
                                  View
                                </Link>
                              ) : (
                                <span className="text-xs text-gray-400">—</span>
                              )}
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
