"use client";

/**
 * Incoming Referrals - Receiving facility view of referrals sent to this facility.
 * Allows accept, respond with outcome, and schedule teleconsult.
 * Route: /queue/incoming-referrals | pageTitle: "Incoming Referrals"
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowLeft,
  ArrowRightLeft,
  CheckCircle2,
  Clock,
  Loader2,
  MessageSquare,
  User,
  Video,
  ClipboardCheck,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import {
  COORDINATION_COPY,
  getReferralFacilityName,
  getReferralStageCopy,
  parseConsultationCoordinationMeta,
  toDateTimeLocalValue,
} from "@/lib/consult-workflows";

interface IncomingReferral {
  id: string;
  type: string;
  attributes: {
    patient_id: string;
    referral_type: string;
    specialty: string;
    referred_to: string;
    referred_to_facility: string;
    reason: string;
    urgency: string;
    status: string;
    clinical_summary: string | null;
    referred_by: string;
    referred_by_name: string;
    response_notes: string | null;
    responded_at: string | null;
    accepted_at: string | null;
    created_at: string;
    [key: string]: unknown;
  };
}

const URGENCY_BADGE: Record<string, string> = {
  ROUTINE: "bg-blue-100 text-blue-700",
  URGENT: "bg-orange-100 text-orange-700",
  EMERGENCY: "bg-red-100 text-red-700",
};

const STATUS_BADGE: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  ACCEPTED: "bg-blue-100 text-blue-700",
  RESPONDED: "bg-purple-100 text-purple-700",
  COMPLETED: "bg-green-100 text-green-700",
};

export default function IncomingReferralsPage() {
  const facility = useFacilityStore((s) => s.facility);

  const [referrals, setReferrals] = useState<IncomingReferral[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [activeAction, setActiveAction] = useState<{ id: string; type: "accept" | "respond" } | null>(null);
  const [scheduledAt, setScheduledAt] = useState("");
  const [handoffNote, setHandoffNote] = useState("");
  const [responseNotes, setResponseNotes] = useState("");
  const [responseOutcome, setResponseOutcome] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!facility) {
      setReferrals([]);
      setIsLoading(false);
      return;
    }

    let isCancelled = false;
    setIsLoading(true);

    apiClient
      .get<ApiResponse<IncomingReferral[]>>(
        `/internal/v1/referrals/incoming?facility_id=${encodeURIComponent(facility.id)}`,
      )
      .then((res) => {
        if (!isCancelled) {
          setReferrals(res.data ?? []);
        }
      })
      .catch(() => {
        if (!isCancelled) {
          setReferrals([]);
        }
      })
      .finally(() => {
        if (!isCancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [facility]);

  function resetActionState() {
    setActiveAction(null);
    setScheduledAt("");
    setHandoffNote("");
    setResponseNotes("");
    setResponseOutcome("");
  }

  function openAction(referral: IncomingReferral, type: "accept" | "respond") {
    if (activeAction?.id === referral.id && activeAction.type === type) {
      resetActionState();
      return;
    }

    setActiveAction({ id: referral.id, type });
    setScheduledAt(toDateTimeLocalValue((referral.attributes.scheduled_at as string | null | undefined) ?? null));
    setHandoffNote("");
    setResponseNotes(referral.attributes.response_notes ?? "");
    setResponseOutcome((referral.attributes.outcome as string | undefined) ?? "");
  }

  async function handleAccept(referralId: string) {
    if (!facility) return;
    setIsSubmitting(true);
    try {
      await apiClient.post(`/internal/v1/referrals/${referralId}/accept`, {
        receiving_facility_id: facility.id,
        receiving_facility_name: facility.name,
        scheduled_at: scheduledAt || undefined,
        notes: handoffNote.trim() || undefined,
      });
      setReferrals((prev) =>
        prev.map((referral) =>
          referral.id === referralId
            ? {
                ...referral,
                attributes: {
                  ...referral.attributes,
                  status: "ACCEPTED",
                  accepted_at: new Date().toISOString(),
                  receiving_facility_name: facility.name,
                  scheduled_at: scheduledAt || null,
                },
              }
            : referral,
        ),
      );
      resetActionState();
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRespond(referralId: string) {
    setIsSubmitting(true);
    try {
      await apiClient.post(`/internal/v1/referrals/${referralId}/respond`, {
        response_notes: responseNotes,
        outcome: responseOutcome || null,
      });
      setReferrals((prev) =>
        prev.map((referral) =>
          referral.id === referralId
            ? {
                ...referral,
                attributes: {
                  ...referral.attributes,
                  status: "RESPONDED",
                  response_notes: responseNotes,
                  responded_at: new Date().toISOString(),
                  outcome: responseOutcome || null,
                },
              }
            : referral,
        ),
      );
      resetActionState();
    } finally {
      setIsSubmitting(false);
    }
  }

  const pendingReferrals = useMemo(
    () => referrals.filter((referral) => referral.attributes.status === "PENDING"),
    [referrals],
  );
  const acceptedReferrals = useMemo(
    () => referrals.filter((referral) => referral.attributes.status === "ACCEPTED"),
    [referrals],
  );
  const respondedReferrals = useMemo(
    () => referrals.filter((referral) => referral.attributes.status === "RESPONDED"),
    [referrals],
  );
  const closedReferrals = useMemo(
    () => referrals.filter((referral) => referral.attributes.status === "COMPLETED"),
    [referrals],
  );
  const sections = useMemo(
    () =>
      [
        {
          key: "receive",
          title: COORDINATION_COPY.needsActionNow,
          description: "Accept these incoming referrals into the facility context before specialist review begins.",
          referrals: pendingReferrals,
        },
        {
          key: "respond",
          title: COORDINATION_COPY.trackingInProgress,
          description: "These referrals have been accepted and are waiting for findings, treatment recommendations, or disposition.",
          referrals: acceptedReferrals,
        },
        {
          key: "returned",
          title: COORDINATION_COPY.responseReturned,
          description: "The response is back with the referring team. The final loop closure now happens from the patient consults workspace.",
          referrals: respondedReferrals,
        },
        {
          key: "closed",
          title: COORDINATION_COPY.closedLoops,
          description: "Completed facility handoffs retained for tracking and audit visibility.",
          referrals: closedReferrals,
        },
      ].filter((section) => section.referrals.length > 0),
    [acceptedReferrals, closedReferrals, pendingReferrals, respondedReferrals],
  );

  return (
    <AppLayout>
      <PageShell
        title="Incoming Referrals"
        subtitle={facility ? `Referrals sent to ${facility.name}` : "Select a facility first"}
      >
        <div className="mb-4">
          <Link
            href="/queue"
            className="inline-flex items-center gap-1 text-sm text-gray-500 transition-colors hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to queue
          </Link>
        </div>

        {!facility ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <AlertCircle className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">Please select a facility first</p>
            <Link
              href="/facility"
              className="mt-2 inline-block text-sm text-blue-600 hover:text-blue-800"
            >
              Select Facility
            </Link>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading incoming referrals...</span>
          </div>
        ) : referrals.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <ArrowDownLeft className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No incoming referrals</p>
          </div>
        ) : (
          <div className="space-y-5">
            <div className="grid gap-3 lg:grid-cols-[minmax(0,1.5fr)_repeat(3,minmax(0,1fr))]">
              <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#fffaf0_0%,#ffffff_55%,#eff6ff_100%)] p-5 shadow-sm">
                <div className="flex items-start gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-100 text-amber-700">
                    <ArrowRightLeft className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-slate-900">Receiving facility orchestration</p>
                    <p className="mt-1 text-sm text-slate-600">
                      This worklist now mirrors the in-chart referral flow: accept the handoff, return specialist guidance, then let the referring team close the loop inside the patient consults workspace.
                    </p>
                  </div>
                </div>
              </div>
              {[
                { label: COORDINATION_COPY.needsActionNow, value: pendingReferrals.length, tone: "amber" },
                { label: COORDINATION_COPY.trackingInProgress, value: acceptedReferrals.length, tone: "blue" },
                { label: COORDINATION_COPY.responseReturned, value: respondedReferrals.length, tone: "purple" },
              ].map((card) => (
                <div key={card.label} className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">{card.label}</p>
                  <p className={`mt-2 text-2xl font-semibold ${
                    card.tone === "amber"
                      ? "text-amber-700"
                      : card.tone === "blue"
                        ? "text-blue-700"
                        : "text-purple-700"
                  }`}>{card.value}</p>
                </div>
              ))}
            </div>

            {sections.map((section) => (
              <div key={section.key} className="space-y-3">
                <div className="rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-sm font-semibold text-slate-900">{section.title}</h3>
                    <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600">
                      {section.referrals.length}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-slate-600">{section.description}</p>
                </div>

                {section.referrals.map((referral) => {
              const attrs = referral.attributes;
              const urgencyStyle = URGENCY_BADGE[attrs.urgency] ?? "bg-gray-100 text-gray-600";
              const statusStyle = STATUS_BADGE[attrs.status] ?? "bg-gray-100 text-gray-600";
              const isActionable = attrs.status === "PENDING" || attrs.status === "ACCEPTED";
              const coordinationMeta = parseConsultationCoordinationMeta(attrs.response_notes);
              const stageCopy = getReferralStageCopy(attrs.status, true);
              const patientConsultsHref = `/ehr/${attrs.patient_id}/consults?tab=referrals`;
              const patientTeleconsultHref = `/ehr/${attrs.patient_id}/consults?tab=teleconsults`;

              return (
                <div key={referral.id} className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1">
                      <div className="mb-1 flex items-center gap-2">
                        <h3 className="text-sm font-semibold text-gray-900">{attrs.specialty}</h3>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${urgencyStyle}`}>
                          {attrs.urgency}
                        </span>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                          {attrs.status}
                        </span>
                      </div>

                      <div className={`mb-3 rounded-2xl border px-3 py-3 ${
                        stageCopy.tone === "amber"
                          ? "border-amber-200 bg-amber-50"
                          : stageCopy.tone === "blue"
                            ? "border-blue-200 bg-blue-50"
                            : stageCopy.tone === "purple"
                              ? "border-purple-200 bg-purple-50"
                              : "border-emerald-200 bg-emerald-50"
                      }`}>
                        <p className="text-sm font-semibold text-slate-900">{stageCopy.title}</p>
                        <p className="mt-1 text-sm text-slate-600">{stageCopy.detail}</p>
                      </div>

                      <p className="mb-1 text-sm text-gray-700">
                        <span className="font-medium">From:</span> {attrs.referred_by_name}
                        {attrs.referred_to_facility && (
                          <span className="text-gray-500"> at {attrs.referred_to_facility}</span>
                        )}
                      </p>

                      <p className="text-sm text-gray-600">{attrs.reason}</p>

                      {attrs.clinical_summary && (
                        <p className="mt-1 line-clamp-2 text-xs text-gray-500">
                          {attrs.clinical_summary}
                        </p>
                      )}

                      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 font-medium text-slate-700">
                          Destination: {getReferralFacilityName(referral)}
                        </span>
                        {coordinationMeta.responseSentFromTeleconsult && (
                          <span className="rounded-full bg-purple-100 px-2.5 py-1 font-medium text-purple-700">
                            {COORDINATION_COPY.returnedFromTeleconsult}
                          </span>
                        )}
                      </div>

                      <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-gray-400">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {new Date(attrs.created_at).toLocaleString()}
                        </span>
                        {attrs.accepted_at && (
                          <span className="text-blue-600">Accepted {new Date(attrs.accepted_at).toLocaleString()}</span>
                        )}
                        {attrs.responded_at && (
                          <span className="text-purple-600">Responded {new Date(attrs.responded_at).toLocaleString()}</span>
                        )}
                        {attrs.patient_id && (
                          <Link
                            href={`/ehr/${attrs.patient_id}`}
                            className="flex items-center gap-1 text-blue-600 hover:text-blue-800"
                          >
                            <User className="h-3 w-3" />
                            View Patient
                          </Link>
                        )}
                      </div>

                      {attrs.response_notes && (
                        <div className="mt-3 rounded-lg border border-purple-200 bg-purple-50 p-3">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="text-xs font-medium text-purple-700">Response:</p>
                            {coordinationMeta.nextWorkspaceAction && (
                              <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-purple-700">
                                {coordinationMeta.nextWorkspaceAction}
                              </span>
                            )}
                          </div>
                          <p className="text-sm text-purple-900">{attrs.response_notes}</p>
                        </div>
                      )}
                    </div>

                    {isActionable && (
                      <div className="flex shrink-0 flex-col gap-2">
                        {attrs.status === "PENDING" && (
                          <button
                            type="button"
                            onClick={() => openAction(referral, "accept")}
                            disabled={isSubmitting}
                            className="flex items-center gap-1 rounded-lg bg-amber-500 px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-amber-600 disabled:opacity-50"
                          >
                            <ArrowRightLeft className="h-3 w-3" />
                            Accept Handoff
                          </button>
                        )}
                        {attrs.status === "ACCEPTED" && (
                          <button
                            type="button"
                            onClick={() => openAction(referral, "respond")}
                            className="flex items-center gap-1 rounded-lg bg-purple-600 px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-purple-700"
                          >
                            <MessageSquare className="h-3 w-3" />
                            Respond
                          </button>
                        )}
                        <Link
                          href={patientTeleconsultHref}
                          className="flex items-center justify-center gap-1 rounded-lg bg-green-600 px-4 py-2 text-center text-xs font-medium text-white transition-colors hover:bg-green-700"
                        >
                          <Video className="h-3 w-3" />
                          Open Teleconsults
                        </Link>
                        <Link
                          href={patientConsultsHref}
                          className="flex items-center justify-center gap-1 rounded-lg border border-slate-200 px-4 py-2 text-center text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50"
                        >
                          <ClipboardCheck className="h-3 w-3" />
                          Patient Consults
                        </Link>
                      </div>
                    )}
                  </div>

                  {activeAction?.id === referral.id && (
                    <div className="mt-4 rounded-2xl border border-gray-200 bg-gray-50 p-4">
                      <h4 className="mb-3 text-sm font-medium text-gray-900">
                        {activeAction.type === "accept" ? "Receive Referral Handoff" : "Respond to Referral"}
                      </h4>
                      <div className="space-y-3">
                        {activeAction.type === "accept" ? (
                          <>
                            <div className="grid gap-3 md:grid-cols-2">
                              <div>
                                <label className="mb-1 block text-xs font-medium text-gray-600">
                                  Planned review time
                                </label>
                                <input
                                  value={scheduledAt}
                                  onChange={(event) => setScheduledAt(event.target.value)}
                                  type="datetime-local"
                                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-500"
                                />
                              </div>
                              <div>
                                <label className="mb-1 block text-xs font-medium text-gray-600">
                                  Receiving note
                                </label>
                                <textarea
                                  value={handoffNote}
                                  onChange={(event) => setHandoffNote(event.target.value)}
                                  rows={3}
                                  placeholder="Triage note, expected specialist, or preparation steps..."
                                  className="w-full resize-none rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-500"
                                />
                              </div>
                            </div>
                            <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
                              Accepting here updates the receiving facility context while preserving the loop closure for the referring team in Consults.
                            </div>
                          </>
                        ) : (
                          <>
                            <div>
                              <label className="mb-1 block text-xs font-medium text-gray-600">
                                Response Notes
                              </label>
                              <textarea
                                value={responseNotes}
                                onChange={(event) => setResponseNotes(event.target.value)}
                                rows={3}
                                required
                                placeholder="Assessment findings, recommendations, treatment provided..."
                                className="w-full resize-none rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
                              />
                            </div>
                            <div>
                              <label className="mb-1 block text-xs font-medium text-gray-600">
                                Outcome
                              </label>
                              <select
                                value={responseOutcome}
                                onChange={(event) => setResponseOutcome(event.target.value)}
                                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
                              >
                                <option value="">Select outcome...</option>
                                <option value="TREATED">Treated - Patient managed</option>
                                <option value="ADMITTED">Admitted - Ongoing care</option>
                                <option value="FURTHER_REFERRAL">Further referral needed</option>
                                <option value="RETURNED">Returned to referring facility</option>
                                <option value="FOLLOW_UP_REQUIRED">Follow-up required</option>
                              </select>
                            </div>
                          </>
                        )}
                        <div className="flex gap-3">
                          <button
                            type="button"
                            onClick={resetActionState}
                            className="flex-1 rounded-lg bg-gray-100 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-200"
                          >
                            Cancel
                          </button>
                          <button
                            type="button"
                            onClick={() => activeAction.type === "accept" ? handleAccept(referral.id) : handleRespond(referral.id)}
                            disabled={isSubmitting || (activeAction.type === "respond" && !responseNotes.trim())}
                            className={`flex flex-1 items-center justify-center gap-2 rounded-lg py-2 text-sm font-medium text-white transition-colors disabled:opacity-50 ${
                              activeAction.type === "accept"
                                ? "bg-amber-500 hover:bg-amber-600"
                                : "bg-purple-600 hover:bg-purple-700"
                            }`}
                          >
                            {isSubmitting ? (
                              <>
                                <Loader2 className="h-4 w-4 animate-spin" />
                                Submitting...
                              </>
                            ) : (
                              <>
                                <CheckCircle2 className="h-4 w-4" />
                                {activeAction.type === "accept" ? "Accept Handoff" : "Submit Response"}
                              </>
                            )}
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              );
                })}
              </div>
            ))}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
