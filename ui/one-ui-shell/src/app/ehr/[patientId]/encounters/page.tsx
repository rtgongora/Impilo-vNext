"use client";

/**
 * Encounters — View encounter history and start new encounters.
 * Route: /ehr/[patientId]/encounters | pageTitle: "Encounters"
 */

import { useMemo, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
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
import { JourneyOrchestrationRail } from "@/components/queue/JourneyOrchestrationRail";

/* ------------------------------------------------------------------ */
/*  Badge helpers                                                      */
/* ------------------------------------------------------------------ */

const STATUS_BADGE: Record<string, string> = {
  IN_PROGRESS: "bg-green-100 text-green-700",
  ACTIVE: "bg-green-100 text-green-700",
  COMPLETED: "bg-neutral-100 text-muted-foreground",
  DISCHARGED: "bg-primary-soft text-primary",
  ADMITTED: "bg-purple-100 text-warning-foreground",
  TRANSFERRED: "bg-amber-100 text-warning-foreground",
  REFERRED: "bg-indigo-100 text-primary-hover",
  DECEASED: "bg-red-100 text-danger",
  LAMA: "bg-neutral-100 text-foreground",
  CANCELLED: "bg-red-100 text-danger" };

/* ------------------------------------------------------------------ */
/*  Empty form state                                                   */
/* ------------------------------------------------------------------ */

// Values are the canonical vocabulary the PCT encounter service accepts (it lower-cases them):
// context ∈ ALLOWED_CONTEXTS, entry_point ∈ ALLOWED_ENTRY_POINTS, care_setting ∈ ALLOWED_CARE_SETTINGS.
const EMPTY_FORM = {
  encounter_type: "OUTPATIENT",
  chief_complaint: "",
  journey_id: "",
  encounter_context: "OUTPATIENT",
  entry_point: "WALK_IN",
  modality: "IN_PERSON",
  care_setting: "FACILITY",
  priority: "ROUTINE" };

// Clinical contexts a provider can choose for an encounter (canonical PCT contexts).
const ENCOUNTER_CONTEXT_OPTIONS: { value: string; label: string }[] = [
  { value: "OUTPATIENT", label: "Outpatient" },
  { value: "EMERGENCY", label: "Emergency" },
  { value: "INPATIENT", label: "Inpatient" },
  { value: "COMMUNITY", label: "Community" },
  { value: "VIRTUAL", label: "Virtual / Telehealth" },
  { value: "PROCEDURE", label: "Procedure" },
];

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function EncountersPage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const patientId = params.patientId;
  const journeyFromUrl = searchParams.get("journey_id") ?? "";
  const transactionFromUrl = searchParams.get("transaction_id") ?? "";

  const facility = useFacilityStore((s) => s.facility);
  const { isClinical } = useRoleGroup();
  const { data: encountersData, isLoading } = useEncounters(patientId);
  const { data: referralsData } = useReferrals(patientId);
  const { data: notesData } = useClinicalNotes(patientId);
  const { data: telemedicineData } = useTelemedicineSessions({ patientId, facilityId: facility?.id });
  const createEncounter = useCreateEncounter();

  const encounters: EncounterResource[] = useMemo(() => encountersData?.data ?? [], [encountersData?.data]);
  const referrals = useMemo(() => referralsData?.data ?? [], [referralsData?.data]);
  const clinicalNotes = useMemo(() => notesData?.data ?? [], [notesData?.data]);
  const telemedicineSessions = useMemo(() => telemedicineData?.data ?? [], [telemedicineData?.data]);
  const activeEncounter = encounters.find(
    (encounter) => encounter.attributes.isOpen,
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

  const [showForm, setShowForm] = useState(Boolean(journeyFromUrl));
  const [form, setForm] = useState({
    ...EMPTY_FORM,
    journey_id: journeyFromUrl,
    entry_point: journeyFromUrl ? "WALK_IN" : EMPTY_FORM.entry_point,
  });

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
        journeyId: form.journey_id.trim(),
        encounterContext: form.encounter_context,
        entryPoint: form.entry_point,
        modality: form.modality,
        careSetting: form.care_setting,
        priority: form.priority,
        chiefComplaint: form.chief_complaint.trim() || undefined },
      {
        onSuccess: (data) => {
          setForm({ ...EMPTY_FORM });
          setShowForm(false);
          const newEncounterId = data?.data?.id;
          const coreTransactionId =
            data?.meta?.core_transaction_id ?? transactionFromUrl ?? undefined;
          if (newEncounterId) {
            const params = new URLSearchParams();
            if (coreTransactionId) params.set("transaction_id", coreTransactionId);
            if (data?.meta?.journey_id) params.set("journey_id", data.meta.journey_id);
            const query = params.toString();
            router.push(
              query
                ? `/ehr/${patientId}/encounter/${newEncounterId}?${query}`
                : `/ehr/${patientId}/encounter/${newEncounterId}`,
            );
          }
        } },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Encounters" subtitle="Patient encounter history">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading encounters...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {transactionFromUrl.startsWith("journey-") ? (
              <JourneyOrchestrationRail
                transactionId={transactionFromUrl}
                patientId={patientId}
                journeyId={journeyFromUrl || undefined}
              />
            ) : null}

            <div className="rounded-3xl border border-border bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_45%,#fffaf5_100%)] p-5 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                  <div className="inline-flex items-center gap-2 rounded-full bg-card/80 px-3 py-1 text-xs font-medium text-muted-foreground">
                    <ArrowRightLeft className="h-3.5 w-3.5 text-primary" />
                    Encounter coordination
                  </div>
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">Encounter history now carries consult and teleconsult context</h3>
                    <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
                      Past and active encounters stay connected to open referrals, returned specialist guidance, and teleconsult activity so handoffs remain visible during chart review.
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2 pt-1">
                    <Link
                      href={`/ehr/${patientId}/consults?tab=referrals`}
                      className="inline-flex items-center gap-1.5 rounded-xl bg-neutral-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
                    >
                      <ArrowRightLeft className="h-4 w-4" />
                      Consults
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/consults?tab=teleconsults`}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-border bg-card px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-background"
                    >
                      <Video className="h-4 w-4" />
                      Teleconsults
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/notes`}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-border bg-card px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-background"
                    >
                      <FileText className="h-4 w-4" />
                      Notes Evidence
                    </Link>
                  </div>
                </div>

                <div className="grid gap-3 sm:grid-cols-2 xl:w-[28rem]">
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Open referrals</p>
                    <p className="mt-2 text-2xl font-semibold text-warning-foreground">{coordinationPulse.openReferrals}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Referrals still moving across encounter history.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Receiving here</p>
                    <p className="mt-2 text-2xl font-semibold text-primary">{coordinationPulse.receivingHere}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Handoffs where this facility is the current receiver.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Teleconsult activity</p>
                    <p className="mt-2 text-2xl font-semibold text-primary-hover">{coordinationPulse.teleconsultActivity}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Scheduled or live virtual sessions tied to this chart.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Returned guidance</p>
                    <p className="mt-2 text-2xl font-semibold text-primary-hover">{coordinationPulse.returnedGuidance}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Consultation notes with structured referral-loop updates.</p>
                  </div>
                </div>
              </div>

              {activeEncounter && (
                <div className="mt-4 rounded-2xl border border-success/25 bg-success-soft px-4 py-3 text-sm text-primary-hover">
                  Active encounter in progress: open the live encounter workspace for immediate charting, or stay here to review historical encounter context.
                </div>
              )}
            </div>

            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="w-5 h-5 text-impilo-400" />
                <h2 className="text-lg font-semibold text-foreground">
                  Encounters ({encounters.length})
                </h2>
              </div>
              {isClinical && (
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors"
              >
                <Plus className="w-4 h-4" />
                Start Encounter
              </button>
              )}
            </div>

            {/* Start Encounter form */}
            {showForm && (
              <div className="bg-card rounded-lg border border-border p-5">
                <h3 className="font-medium text-foreground mb-4">Start New Encounter</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        PCT Journey ID
                      </label>
                      <input
                        type="text"
                        value={form.journey_id}
                        onChange={(e) => updateField("journey_id", e.target.value)}
                        placeholder="Required canonical journey id"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                      />
                      <p className="mt-1 text-xs text-muted-foreground">
                        Encounters start through PCT journey orchestration; direct patient-only encounter creation is not supported.
                      </p>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Encounter Type
                      </label>
                      <select
                        value={form.encounter_type}
                        onChange={(e) => updateField("encounter_type", e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                      >
                        <option value="OUTPATIENT">Outpatient</option>
                        <option value="INPATIENT">Inpatient</option>
                        <option value="EMERGENCY">Emergency</option>
                        <option value="TELEHEALTH">Telehealth</option>
                        <option value="HOME_VISIT">Home Visit</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Chief Complaint
                      </label>
                      <input
                        type="text"
                        value={form.chief_complaint}
                        onChange={(e) => updateField("chief_complaint", e.target.value)}
                        placeholder="e.g. Chest pain, headache"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Context
                      </label>
                      <select
                        value={form.encounter_context}
                        onChange={(e) => updateField("encounter_context", e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                      >
                        {ENCOUNTER_CONTEXT_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Priority
                      </label>
                      <select
                        value={form.priority}
                        onChange={(e) => updateField("priority", e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="EMERGENCY">Emergency</option>
                      </select>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={createEncounter.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
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
                      className="px-4 py-2 text-sm font-medium text-foreground rounded-lg border border-border hover:bg-background transition-colors"
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
              <div className="bg-card rounded-lg border border-border p-12 text-center">
                <ClipboardList className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
                <p className="text-muted-foreground text-sm">No encounters recorded yet</p>
              </div>
            ) : (
              <div className="bg-card rounded-lg border border-border overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background">
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Type</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Start Date</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">End Date</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Chief Complaint</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Bill</th>
                      </tr>
                    </thead>
                    <tbody>
                      {encounters.map((encounter) => {
                        const a = encounter.attributes;
                        return (
                          <tr
                            key={encounter.id}
                            className="border-b border-border hover:bg-background transition-colors cursor-pointer"
                            onClick={() =>
                              router.push(
                                `/ehr/${patientId}/encounter/${encounter.id}`,
                              )
                            }
                          >
                            <td className="px-4 py-3">
                              <Link
                                href={`/ehr/${patientId}/encounter/${encounter.id}`}
                                className="font-medium text-primary hover:text-primary-hover hover:underline"
                              >
                                {a.encounterType.replace(/_/g, " ")}
                              </Link>
                            </td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  STATUS_BADGE[a.status] ?? "bg-neutral-100 text-muted-foreground"
                                }`}
                              >
                                {a.status.replace(/_/g, " ")}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-foreground">
                              {new Date(a.startedAt).toLocaleString()}
                            </td>
                            <td className="px-4 py-3 text-foreground">
                              {a.closedAt
                                ? new Date(a.closedAt).toLocaleString()
                                : "—"}
                            </td>
                            <td className="px-4 py-3 text-foreground">
                              {(a as Record<string, unknown>).chiefComplaint as string ?? (a as Record<string, unknown>).chief_complaint as string ?? "—"}
                            </td>
                            <td className="px-4 py-3">
                              {a.costa_bill_id ? (
                                  <Link
                                  href={`/finance/billing/${a.costa_bill_id}?patientId=${patientId}&encounterId=${encounter.id}&source=encounters`}
                                    onClick={(e) => e.stopPropagation()}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full bg-success-soft text-primary-hover hover:bg-emerald-100 transition-colors"
                                >
                                  <Receipt className="w-3 h-3" />
                                  View
                                </Link>
                              ) : (
                                <span className="text-xs text-muted-foreground">—</span>
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
