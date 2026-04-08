"use client";

/**
 * Timeline — View the clinical timeline for a patient.
 * Route: /ehr/[patientId]/timeline | pageTitle: "Timeline"
 */

import Link from "next/link";
import { useMemo } from "react";
import { useParams } from "next/navigation";
import {
  Clock,
  Loader2,
  Stethoscope,
  HeartPulse,
  ClipboardList,
  FlaskConical,
  ArrowRightLeft,
  Pill,
  Syringe,
  Video,
  FileText } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useTimeline,
  type TimelineEntryResource } from "@/hooks/queries/useTimeline";
import { useReferrals } from "@/hooks/queries/useReferrals";
import { useClinicalNotes } from "@/hooks/queries/useClinicalNotes";
import { useTelemedicineSessions } from "@/hooks/queries/useTelemedicine";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { isReferralReceivingHere, parseConsultationCoordinationMeta } from "@/lib/consult-workflows";

/* ------------------------------------------------------------------ */
/*  Event type color mapping                                           */
/* ------------------------------------------------------------------ */

const EVENT_TYPE_CONFIG: Record<
  string,
  { color: string; bgColor: string; borderColor: string; icon: React.ElementType }
> = {
  ENCOUNTER: {
    color: "text-blue-700",
    bgColor: "bg-blue-100",
    borderColor: "border-blue-400",
    icon: Stethoscope },
  VITALS: {
    color: "text-red-700",
    bgColor: "bg-red-100",
    borderColor: "border-red-400",
    icon: HeartPulse },
  NOTE: {
    color: "text-indigo-700",
    bgColor: "bg-indigo-100",
    borderColor: "border-indigo-400",
    icon: FileText },
  ORDER: {
    color: "text-purple-700",
    bgColor: "bg-purple-100",
    borderColor: "border-purple-400",
    icon: ClipboardList },
  RESULT: {
    color: "text-green-700",
    bgColor: "bg-green-100",
    borderColor: "border-green-400",
    icon: FlaskConical },
  REFERRAL: {
    color: "text-orange-700",
    bgColor: "bg-orange-100",
    borderColor: "border-orange-400",
    icon: ArrowRightLeft },
  PRESCRIPTION: {
    color: "text-teal-700",
    bgColor: "bg-teal-100",
    borderColor: "border-teal-400",
    icon: Pill },
  IMMUNIZATION: {
    color: "text-cyan-700",
    bgColor: "bg-cyan-100",
    borderColor: "border-cyan-400",
    icon: Syringe } };

const DEFAULT_CONFIG = {
  color: "text-gray-700",
  bgColor: "bg-gray-100",
  borderColor: "border-gray-400",
  icon: Clock };

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function TimelinePage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((s) => s.facility);

  const { data: timelineData, isLoading } = useTimeline(patientId);
  const { data: referralsData } = useReferrals(patientId);
  const { data: notesData } = useClinicalNotes(patientId);
  const { data: telemedicineData } = useTelemedicineSessions({ patientId, facilityId: facility?.id });
  const entries: TimelineEntryResource[] = timelineData?.data ?? [];
  const referrals = referralsData?.data ?? [];
  const clinicalNotes = notesData?.data ?? [];
  const telemedicineSessions = telemedicineData?.data ?? [];
  const coordinationPulse = useMemo(() => {
    const referralEvents = entries.filter((entry) => entry.attributes.eventType === "REFERRAL").length;
    const receivingHere = referrals.filter((referral) => isReferralReceivingHere(referral, facility)).length;
    const returnedGuidance = clinicalNotes.filter(
      (note) =>
        note.attributes.noteType === "CONSULTATION" &&
        parseConsultationCoordinationMeta(note.attributes.body).hasReferralLoopUpdate,
    ).length;
    const teleconsultActivity = telemedicineSessions.filter(
      (session) => session.attributes.status === "SCHEDULED" || session.attributes.status === "IN_PROGRESS",
    ).length;

    return { referralEvents, receivingHere, returnedGuidance, teleconsultActivity };
  }, [clinicalNotes, entries, facility, referrals, telemedicineSessions]);

  return (
    <EHRLayout>
      <PageShell title="Timeline" subtitle="Clinical event timeline">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading timeline...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_45%,#fffaf5_100%)] p-5 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                  <div className="inline-flex items-center gap-2 rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-slate-600">
                    <ArrowRightLeft className="h-3.5 w-3.5 text-blue-600" />
                    Coordination timeline
                  </div>
                  <div>
                    <h3 className="text-lg font-semibold text-slate-900">Referral and teleconsult movement stays visible in the timeline</h3>
                    <p className="mt-1 max-w-2xl text-sm text-slate-600">
                      The clinical timeline now sits beside live coordination counts so chart review still shows where consult loops are active, returned, or waiting on the current facility.
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
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Referral events</p>
                    <p className="mt-2 text-2xl font-semibold text-orange-700">{coordinationPulse.referralEvents}</p>
                    <p className="mt-1 text-xs text-slate-500">Referral milestones already represented in the timeline.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Receiving here</p>
                    <p className="mt-2 text-2xl font-semibold text-blue-700">{coordinationPulse.receivingHere}</p>
                    <p className="mt-1 text-xs text-slate-500">Open handoffs where this facility is the receiver.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Teleconsult activity</p>
                    <p className="mt-2 text-2xl font-semibold text-emerald-700">{coordinationPulse.teleconsultActivity}</p>
                    <p className="mt-1 text-xs text-slate-500">Scheduled or live virtual consult sessions tied to the patient.</p>
                  </div>
                  <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Returned guidance</p>
                    <p className="mt-2 text-2xl font-semibold text-indigo-700">{coordinationPulse.returnedGuidance}</p>
                    <p className="mt-1 text-xs text-slate-500">Consultation notes that include structured loop updates.</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Header */}
            <div className="flex items-center gap-2">
              <Clock className="w-5 h-5 text-blue-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                Timeline ({entries.length} events)
              </h2>
            </div>

            {entries.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Clock className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No timeline entries yet</p>
              </div>
            ) : (
              <div className="relative">
                {/* Vertical timeline line */}
                <div className="absolute left-[19px] top-0 bottom-0 w-0.5 bg-gray-200" />

                <div className="space-y-6">
                  {entries.map((entry) => {
                    const a = entry.attributes;
                    const config = EVENT_TYPE_CONFIG[a.eventType] ?? DEFAULT_CONFIG;
                    const IconComponent = config.icon;

                    return (
                      <div key={entry.id} className="relative flex gap-4">
                        {/* Timeline dot */}
                        <div
                          className={`relative z-10 flex-shrink-0 w-10 h-10 rounded-full ${config.bgColor} flex items-center justify-center border-2 ${config.borderColor}`}
                        >
                          <IconComponent className={`w-4 h-4 ${config.color}`} />
                        </div>

                        {/* Timeline card */}
                        <div className="flex-1 bg-white rounded-lg border border-gray-200 p-4 hover:shadow-sm transition-shadow">
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 mb-1">
                                <span
                                  className={`px-2 py-0.5 text-xs font-medium rounded-full ${config.bgColor} ${config.color}`}
                                >
                                  {a.eventType}
                                </span>
                                <span className="text-sm font-semibold text-gray-900 truncate">
                                  {a.title}
                                </span>
                              </div>
                              {a.description && (
                                <p className="text-sm text-gray-600 mt-1">{a.description}</p>
                              )}
                              {a.actorName && (
                                <p className="text-xs text-gray-500 mt-2">
                                  By: <span className="font-medium">{a.actorName}</span>
                                </p>
                              )}
                            </div>
                            <span className="text-xs text-gray-400 whitespace-nowrap flex-shrink-0">
                              {new Date(a.occurredAt).toLocaleString()}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
