"use client";

/**
 * Patient Chart — Overview of patient demographics and navigation to sub-pages.
 * Route: /ehr/[patientId] | pageTitle: "Patient Chart"
 */

import { useEffect, useMemo } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  User,
  Loader2,
  Activity,
  FileText,
  Pill,
  AlertCircle,
  ClipboardList,
  TestTube2,
  Syringe,
  Clock,
  ArrowLeft,
  PlayCircle,
  ArrowRightLeft,
  Video,
} from "lucide-react";
import { PatientSearchOrchestrationRail } from "@/components/encounter/PatientSearchOrchestrationRail";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useReferrals } from "@/hooks/queries/useReferrals";
import { useClinicalNotes } from "@/hooks/queries/useClinicalNotes";
import { useTelemedicineSessions } from "@/hooks/queries/useTelemedicine";
import { useAdmissions } from "@/hooks/queries/useInpatient";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { isReferralReceivingHere, parseConsultationCoordinationMeta } from "@/lib/consult-workflows";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, maskDob, displayCpid } from "@/lib/pii-mask";

const CHART_SECTIONS = [
  { label: "Vitals", href: "vitals", icon: Activity, color: "bg-red-100 text-red-600" },
  { label: "Conditions", href: "conditions", icon: AlertCircle, color: "bg-orange-100 text-orange-600" },
  { label: "Medications", href: "medications", icon: Pill, color: "bg-green-100 text-green-600" },
  { label: "Allergies", href: "allergies", icon: AlertCircle, color: "bg-yellow-100 text-yellow-600" },
  { label: "Orders", href: "orders", icon: ClipboardList, color: "bg-primary-soft text-primary" },
  { label: "Results", href: "results", icon: TestTube2, color: "bg-purple-100 text-purple-600" },
  { label: "Notes", href: "notes", icon: FileText, color: "bg-indigo-100 text-indigo-600" },
  { label: "Immunizations", href: "immunizations", icon: Syringe, color: "bg-teal-100 text-teal-600" },
  { label: "Encounters", href: "encounters", icon: Clock, color: "bg-cyan-100 text-cyan-600" },
  { label: "Consults", href: "consults", icon: FileText, color: "bg-rose-100 text-rose-600" },
  { label: "Teleconsults", href: "consults?tab=teleconsults", icon: Clock, color: "bg-violet-100 text-violet-600" },
  { label: "Timeline", href: "timeline", icon: Clock, color: "bg-neutral-100 text-muted-foreground" },
  { label: "Discharge", href: "discharge", icon: Clock, color: "bg-amber-100 text-amber-600" },
] as const;

export default function PatientChartPage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const patientId = params.patientId;
  const facility = useFacilityStore((s) => s.facility);

  const { data: patientData, isLoading: isLoadingPatient, isError: patientUnavailable } = usePatient(patientId);
  const { data: encountersData, isError: encountersUnavailable } = useEncounters(patientId);
  const { data: referralsData, isError: referralsUnavailable } = useReferrals(patientId);
  const { data: notesData, isError: notesUnavailable } = useClinicalNotes(patientId);
  const { data: telemedicineData, isError: telemedicineUnavailable } = useTelemedicineSessions({ patientId, facilityId: facility?.id });
  const { data: admissionsData, isError: admissionsUnavailable } = useAdmissions(patientId);

  // Every tile in the coordination pulse is a count, and a count of zero is read as "nothing
  // outstanding" — no referral waiting for closure, no returned specialist guidance. When the
  // source could not be read the honest answer is that the number is unknown.
  const coordinationUnavailable =
    encountersUnavailable ||
    referralsUnavailable ||
    notesUnavailable ||
    telemedicineUnavailable ||
    admissionsUnavailable;

  const patient = patientData?.data;
  const admissions = (admissionsData as { data?: { id: string; attributes: Record<string, unknown> }[] } | undefined)?.data ?? [];
  const activeAdmission = admissions.find(
    (a) => a.attributes.status === "ACTIVE" || a.attributes.status === "ADMITTED",
  );
  const encounters = useMemo(() => encountersData?.data ?? [], [encountersData?.data]);
  const referrals = useMemo(() => referralsData?.data ?? [], [referralsData?.data]);
  const clinicalNotes = useMemo(() => notesData?.data ?? [], [notesData?.data]);
  const telemedicineSessions = useMemo(() => telemedicineData?.data ?? [], [telemedicineData?.data]);
  const activeEncounter = encounters.find(
    (e) => e.attributes.isOpen,
  );
  const queueEntry = searchParams.get("entry") === "queue";
  const searchEntry = searchParams.get("entry") === "search";
  const journeyFromSearch = searchParams.get("journey_id");
  const transactionFromSearch = searchParams.get("transaction_id");

  const coordinationPulse = useMemo(() => {
    const closureReady = referrals.filter((referral) => referral.attributes.status === "RESPONDED").length;
    const receivingHere = referrals.filter((referral) => isReferralReceivingHere(referral, facility)).length;
    const activeTeleconsults = telemedicineSessions.filter(
      (session) => session.attributes.status === "IN_PROGRESS" || session.attributes.status === "SCHEDULED",
    ).length;
    const referralLoopUpdates = clinicalNotes.filter(
      (note) =>
        note.attributes.noteType === "CONSULTATION" &&
        parseConsultationCoordinationMeta(note.attributes.body).hasReferralLoopUpdate,
    ).length;

    return { closureReady, receivingHere, activeTeleconsults, referralLoopUpdates };
  }, [clinicalNotes, facility, referrals, telemedicineSessions]);

  useEffect(() => {
    if (!queueEntry || !activeEncounter) return;
    router.replace(`/ehr/${patientId}/encounter/${activeEncounter.id}?entry=queue`);
  }, [activeEncounter, patientId, queueEntry, router]);

  return (
    <EHRLayout>
      <PageShell title="Patient Chart">
        <div className="mb-4">
          <Link
            href="/queue"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to queue
          </Link>
        </div>

        {isLoadingPatient ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading patient data...</span>
          </div>
        ) : patientUnavailable ? (
          /* "Patient not found" is a statement about the registry's contents. A failed read is a
             statement about the registry's reachability, and conflating them invites a clinician
             to re-register someone who already exists. */
          <div className="bg-red-50 rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-3" />
            <p className="text-sm font-medium text-red-700">
              The patient record could not be loaded. This is not a statement that no such patient
              exists — do not register a duplicate on the strength of this screen.
            </p>
          </div>
        ) : !patient ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <User className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">Patient not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {searchEntry && transactionFromSearch ? (
              <PatientSearchOrchestrationRail
                transactionId={transactionFromSearch}
                journeyId={journeyFromSearch}
                selectedPatientId={patientId}
                selectedPatientLabel={patient.attributes.displayName}
                compact
              />
            ) : null}

            {activeEncounter && (
              <div className="bg-gradient-to-r from-green-50 via-white to-blue-50 border border-green-200 rounded-xl p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-start gap-3">
                    <div className="w-12 h-12 rounded-full bg-green-100 flex items-center justify-center shrink-0">
                      <PlayCircle className="w-6 h-6 text-green-600" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-green-800">
                        Active encounter workspace ready
                      </p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        {activeEncounter.attributes.encounterType} is already in progress for this patient.
                        Continue in the live encounter workspace or stay in the longitudinal chart.
                      </p>
                      <div className="mt-3 flex flex-wrap gap-2 text-xs">
                        <span className="rounded-full bg-green-100 px-2.5 py-1 font-medium text-green-700">
                          {activeEncounter.attributes.status}
                        </span>
                        <span className="rounded-full bg-card px-2.5 py-1 text-muted-foreground border border-border">
                          Started {new Date(activeEncounter.attributes.startedAt).toLocaleString()}
                        </span>
                        {queueEntry && (
                          <span className="rounded-full bg-primary-soft px-2.5 py-1 font-medium text-primary">
                            Entered from queue
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2 shrink-0">
                    <Link
                      href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                      className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors"
                    >
                      Resume Encounter
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/summary`}
                      className="px-4 py-2 bg-card text-foreground text-sm font-medium rounded-lg border border-border hover:bg-background transition-colors"
                    >
                      Open Summary
                    </Link>
                  </div>
                </div>
              </div>
            )}

            {activeAdmission && (
              <div className="bg-gradient-to-r from-purple-50 via-white to-indigo-50 border border-warning/35 rounded-xl p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-start gap-3">
                    <div className="w-12 h-12 rounded-full bg-purple-100 flex items-center justify-center shrink-0">
                      <Activity className="w-6 h-6 text-purple-600" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-purple-800">
                        Inpatient admission active
                      </p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        Ward: {String(activeAdmission.attributes.wardName ?? activeAdmission.attributes.ward_name ?? "Unassigned")}
                      </p>
                      <div className="mt-2 flex flex-wrap gap-2 text-xs">
                        <span className="rounded-full bg-purple-100 px-2.5 py-1 font-medium text-warning-foreground">
                          {String(activeAdmission.attributes.status)}
                        </span>
                        {Boolean(activeAdmission.attributes.admittedAt) && (
                          <span className="rounded-full bg-card px-2.5 py-1 text-muted-foreground border border-border">
                            Admitted {new Date(String(activeAdmission.attributes.admittedAt ?? activeAdmission.attributes.admitted_at)).toLocaleString()}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  <Link
                    href={`/beds?patientId=${patientId}`}
                    className="px-4 py-2 bg-purple-600 text-white text-sm font-medium rounded-lg hover:bg-purple-700 transition-colors shrink-0"
                  >
                    View Bed
                  </Link>
                </div>
              </div>
            )}

            <div className="rounded-3xl border border-border bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_45%,#fffaf5_100%)] p-5 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                  <div className="inline-flex items-center gap-2 rounded-full bg-card/80 px-3 py-1 text-xs font-medium text-muted-foreground">
                    <ArrowRightLeft className="h-3.5 w-3.5 text-primary" />
                    Care coordination pulse
                  </div>
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">Consult and teleconsult outcomes are visible from the chart</h3>
                    <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
                      Referral responses, teleconsult activity, and returned specialist guidance are now surfaced here so the chart can signal the next coordination action before you open the deeper workspace.
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2 pt-1">
                    <Link
                      href={`/ehr/${patientId}/consults?tab=referrals`}
                      className="inline-flex items-center gap-1.5 rounded-xl bg-neutral-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
                    >
                      <ArrowRightLeft className="h-4 w-4" />
                      Open Consults
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
                  {coordinationUnavailable && (
                    <div className="sm:col-span-2 rounded-2xl border border-red-200 bg-red-50 p-3 text-xs font-medium text-red-700">
                      One or more coordination sources could not be read. The counts below are
                      unknown, not zero — an outstanding referral response or teleconsult may exist.
                    </div>
                  )}
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Loop closures ready</p>
                    <p className="mt-2 text-2xl font-semibold text-warning-foreground">
                      {coordinationUnavailable ? "—" : coordinationPulse.closureReady}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">Referral responses waiting for final review or closure.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Receiving context</p>
                    <p className="mt-2 text-2xl font-semibold text-primary">
                      {coordinationUnavailable ? "—" : coordinationPulse.receivingHere}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">Referrals where this facility is currently on the receiving side.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Teleconsult activity</p>
                    <p className="mt-2 text-2xl font-semibold text-primary-hover">
                      {coordinationUnavailable ? "—" : coordinationPulse.activeTeleconsults}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">Scheduled or active virtual consult sessions linked to this patient.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-card/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Returned guidance</p>
                    {/* This count is derived from clinical reads. When any of them fails it
                        would collapse to 0, which reads as "no specialist guidance has come back". */}
                    <p className="mt-2 text-2xl font-semibold text-primary-hover">
                      {coordinationUnavailable ? "—" : coordinationPulse.referralLoopUpdates}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {coordinationUnavailable
                        ? "Clinical sources could not be read — not a record that no guidance has returned."
                        : "Consultation notes that include referral-loop updates from teleconsult or specialist review."}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* Patient Summary Card */}
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-start gap-4">
                <div className="w-14 h-14 rounded-full bg-primary-soft flex items-center justify-center shrink-0">
                  <User className="w-7 h-7 text-primary" />
                </div>
                <div className="flex-1 min-w-0">
                  <h2 className="text-lg font-semibold text-foreground">
                    {maskName(patient.attributes.displayName, usePrivacyDisplayStore.getState().level)}
                  </h2>
                  <div className="mt-1 flex flex-wrap gap-x-6 gap-y-1 text-sm text-muted-foreground">
                    <span>{maskDob(patient.attributes.dateOfBirth, usePrivacyDisplayStore.getState().level)}</span>
                    <span>Gender: {patient.attributes.gender}</span>
                    <span>CPID: {displayCpid(patient.attributes.cpid)}</span>
                  </div>
                </div>
                {activeEncounter ? (
                  <Link
                    href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                    className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors shrink-0"
                  >
                    Resume Encounter
                  </Link>
                ) : (
                  <button
                    onClick={() => router.push(`/ehr/${patientId}/encounters`)}
                    disabled={!facility}
                    className="px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors shrink-0 flex items-center gap-1.5"
                  >
                    <><Activity className="w-4 h-4" /> Start Encounter</>
                  </button>
                )}
              </div>
            </div>

            {/* Chart Sections Grid */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
              {CHART_SECTIONS.map((section) => {
                const Icon = section.icon;
                return (
                  <Link
                    key={section.href}
                    href={`/ehr/${patientId}/${section.href}`}
                    className="bg-card rounded-lg border border-border p-4 text-center hover:border-primary/25 hover:shadow-sm transition-all group"
                  >
                    <div
                      className={`w-10 h-10 rounded-lg ${section.color} flex items-center justify-center mx-auto mb-2`}
                    >
                      <Icon className="w-5 h-5" />
                    </div>
                    <p className="text-sm font-medium text-foreground group-hover:text-foreground">
                      {section.label}
                    </p>
                  </Link>
                );
              })}
            </div>

            {/* Recent Encounters. An absent list reads as "this patient has never been seen here",
                and an absent active-encounter banner invites a second encounter to be opened on
                top of a live one. */}
            {encountersUnavailable && (
              <div className="bg-red-50 rounded-lg border border-red-200 p-5">
                <p className="text-sm font-medium text-red-700">
                  Encounters could not be loaded. This is not a record that the patient has none —
                  an encounter may already be active, so check before starting a new one.
                </p>
              </div>
            )}
            {encounters.length > 0 && (
              <div className="bg-card rounded-lg border border-border p-5">
                <h3 className="font-medium text-foreground mb-3">Recent Encounters</h3>
                <div className="space-y-2">
                  {encounters.slice(0, 5).map((enc) => (
                    <Link
                      key={enc.id}
                      href={`/ehr/${patientId}/encounter/${enc.id}`}
                      className="flex items-center justify-between p-3 rounded-lg border border-border hover:bg-background transition-colors"
                    >
                      <div>
                        <p className="text-sm font-medium text-foreground">
                          {enc.attributes.encounterType}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {new Date(enc.attributes.startedAt).toLocaleString()}
                        </p>
                      </div>
                      <span
                        className={`px-2 py-0.5 text-xs rounded-full ${
                          enc.attributes.isOpen
                            ? "bg-green-100 text-green-700"
                            : "bg-neutral-100 text-muted-foreground"
                        }`}
                      >
                        {enc.attributes.status}
                      </span>
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
