"use client";

/**
 * Incoming Referrals - Receiving facility view of referrals sent to this facility.
 * Allows accept, respond with outcome, and schedule teleconsult.
 * Route: /queue/incoming-referrals | pageTitle: "Incoming Referrals"
 */

import { useMemo, useState } from "react";
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
import {
  buildReferralConsultHandoffRoute,
  useAcceptReferral,
  useIncomingReferrals,
  useRespondReferral,
} from "@/hooks/queries/useReferrals";
import { useAcceptTeleconsultSession } from "@/hooks/queries/useTelemedicine";
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
  ROUTINE: "bg-primary-soft text-primary",
  URGENT: "bg-orange-100 text-orange-700",
  EMERGENCY: "bg-red-100 text-danger",
};

const STATUS_BADGE: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  ACCEPTED: "bg-primary-soft text-primary",
  RESPONDED: "bg-purple-100 text-warning-foreground",
  COMPLETED: "bg-green-100 text-green-700",
};

/** A teleconsult referral (modality "virtual") must be accepted through the GOVERNED
 * teleconsult route — the plain referral accept skips the telemedicine governance
 * assert, audit, and referrer notification. */
function isTeleconsult(attrs: Record<string, unknown>): boolean {
  const modality = String(attrs.modality ?? attrs.virtual_mode ?? attrs.virtualMode ?? "").toLowerCase();
  return modality === "virtual" || attrs.virtualMode === true || attrs.virtual_mode === true;
}

export default function IncomingReferralsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const { data: incomingData, isLoading } = useIncomingReferrals(facility?.id);
  const acceptReferral = useAcceptReferral();
  const acceptTeleconsult = useAcceptTeleconsultSession();
  const respondReferral = useRespondReferral();

  const referrals = (incomingData?.data ?? []) as unknown as IncomingReferral[];
  const [activeAction, setActiveAction] = useState<{ id: string; type: "accept" | "respond" } | null>(null);
  const [scheduledAt, setScheduledAt] = useState("");
  const [handoffNote, setHandoffNote] = useState("");
  const [responseNotes, setResponseNotes] = useState("");
  const [responseOutcome, setResponseOutcome] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

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

  async function handleAccept(referral: IncomingReferral) {
    if (!facility) return;
    setIsSubmitting(true);
    try {
      if (isTeleconsult(referral.attributes)) {
        // Governed teleconsult route: telemedicine governance assert + audit + referrer notification.
        await acceptTeleconsult.mutateAsync({
          id: referral.id,
          receivingFacilityId: facility.id,
          receivingFacilityName: facility.name,
          scheduledAt: scheduledAt || undefined,
          notes: handoffNote.trim() || undefined,
        });
      } else {
        await acceptReferral.mutateAsync({
          id: referral.id,
          receiving_facility_id: facility.id,
          receiving_facility_name: facility.name,
          scheduled_at: scheduledAt || undefined,
          notes: handoffNote.trim() || undefined,
        });
      }
      resetActionState();
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRespond(referralId: string) {
    setIsSubmitting(true);
    try {
      await respondReferral.mutateAsync({
        id: referralId,
        response_notes: responseNotes,
        outcome: responseOutcome || undefined,
      });
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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to queue
          </Link>
        </div>

        {!facility ? (
          <div className="rounded-lg border border-border bg-card p-12 text-center">
            <AlertCircle className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">Please select a facility first</p>
            <Link
              href="/facility"
              className="mt-2 inline-block text-sm text-primary hover:text-primary-hover"
            >
              Select Facility
            </Link>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading incoming referrals...</span>
          </div>
        ) : referrals.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-12 text-center">
            <ArrowDownLeft className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No incoming referrals</p>
          </div>
        ) : (
          <div className="space-y-5">
            <div className="grid gap-3 lg:grid-cols-[minmax(0,1.5fr)_repeat(3,minmax(0,1fr))]">
              <div className="rounded-3xl border border-border bg-[linear-gradient(135deg,#fffaf0_0%,#ffffff_55%,#eff6ff_100%)] p-5 shadow-sm">
                <div className="flex items-start gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-100 text-warning-foreground">
                    <ArrowRightLeft className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-foreground">Receiving facility orchestration</p>
                    <p className="mt-1 text-sm text-muted-foreground">
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
                <div key={card.label} className="rounded-3xl border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">{card.label}</p>
                  <p className={`mt-2 text-2xl font-semibold ${
                    card.tone === "amber"
                      ? "text-warning-foreground"
                      : card.tone === "blue"
                        ? "text-primary"
                        : "text-warning-foreground"
                  }`}>{card.value}</p>
                </div>
              ))}
            </div>

            {sections.map((section) => (
              <div key={section.key} className="space-y-3">
                <div className="rounded-2xl border border-border bg-background/80 px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-sm font-semibold text-foreground">{section.title}</h3>
                    <span className="rounded-full bg-card px-3 py-1 text-xs font-medium text-muted-foreground">
                      {section.referrals.length}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-muted-foreground">{section.description}</p>
                </div>

                {section.referrals.map((referral) => {
              const attrs = referral.attributes;
              const urgencyStyle = URGENCY_BADGE[attrs.urgency] ?? "bg-neutral-100 text-muted-foreground";
              const statusStyle = STATUS_BADGE[attrs.status] ?? "bg-neutral-100 text-muted-foreground";
              const isActionable = attrs.status === "PENDING" || attrs.status === "ACCEPTED";
              const coordinationMeta = parseConsultationCoordinationMeta(attrs.response_notes);
              const stageCopy = getReferralStageCopy(attrs.status, true);
              const referralTransactionId = `referral-${referral.id}`;
              const patientConsultsHref = buildReferralConsultHandoffRoute(
                attrs.patient_id,
                referral.id,
                referralTransactionId,
              );
              const patientTeleconsultHref = `/ehr/${attrs.patient_id}/consults?tab=teleconsults`;

              return (
                <div key={referral.id} className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1">
                      <div className="mb-1 flex items-center gap-2">
                        <h3 className="text-sm font-semibold text-foreground">{attrs.specialty}</h3>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${urgencyStyle}`}>
                          {attrs.urgency}
                        </span>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                          {attrs.status}
                        </span>
                        {isTeleconsult(attrs) && (
                          <span className="flex items-center gap-1 rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-700">
                            <Video className="h-3 w-3" /> Teleconsult
                          </span>
                        )}
                      </div>

                      <div className={`mb-3 rounded-2xl border px-3 py-3 ${
                        stageCopy.tone === "amber"
                          ? "border-warning/35 bg-warning-soft"
                          : stageCopy.tone === "blue"
                            ? "border-primary/25 bg-primary-soft"
                            : stageCopy.tone === "purple"
                              ? "border-warning/35 bg-warning-soft"
                              : "border-success/25 bg-success-soft"
                      }`}>
                        <p className="text-sm font-semibold text-foreground">{stageCopy.title}</p>
                        <p className="mt-1 text-sm text-muted-foreground">{stageCopy.detail}</p>
                      </div>

                      <p className="mb-1 text-sm text-foreground">
                        <span className="font-medium">From:</span> {attrs.referred_by_name}
                        {attrs.referred_to_facility && (
                          <span className="text-muted-foreground"> at {attrs.referred_to_facility}</span>
                        )}
                      </p>

                      <p className="text-sm text-muted-foreground">{attrs.reason}</p>

                      {attrs.clinical_summary && (
                        <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">
                          {attrs.clinical_summary}
                        </p>
                      )}

                      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <span className="rounded-full bg-neutral-100 px-2.5 py-1 font-medium text-foreground">
                          Destination: {getReferralFacilityName(referral)}
                        </span>
                        {coordinationMeta.responseSentFromTeleconsult && (
                          <span className="rounded-full bg-purple-100 px-2.5 py-1 font-medium text-warning-foreground">
                            {COORDINATION_COPY.returnedFromTeleconsult}
                          </span>
                        )}
                      </div>

                      <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {new Date(attrs.created_at).toLocaleString()}
                        </span>
                        {attrs.accepted_at && (
                          <span className="text-primary">Accepted {new Date(attrs.accepted_at).toLocaleString()}</span>
                        )}
                        {attrs.responded_at && (
                          <span className="text-purple-600">Responded {new Date(attrs.responded_at).toLocaleString()}</span>
                        )}
                        {attrs.patient_id && (
                          <Link
                            href={`/ehr/${attrs.patient_id}`}
                            className="flex items-center gap-1 text-primary hover:text-primary-hover"
                          >
                            <User className="h-3 w-3" />
                            View Patient
                          </Link>
                        )}
                      </div>

                      {attrs.response_notes && (
                        <div className="mt-3 rounded-lg border border-warning/35 bg-warning-soft p-3">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="text-xs font-medium text-warning-foreground">Response:</p>
                            {coordinationMeta.nextWorkspaceAction && (
                              <span className="rounded-full bg-card px-2 py-0.5 text-[11px] font-medium text-warning-foreground">
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
                          className="flex items-center justify-center gap-1 rounded-lg border border-border px-4 py-2 text-center text-xs font-medium text-muted-foreground transition-colors hover:bg-background"
                        >
                          <ClipboardCheck className="h-3 w-3" />
                          Patient Consults
                        </Link>
                      </div>
                    )}
                  </div>

                  {activeAction?.id === referral.id && (
                    <div className="mt-4 rounded-2xl border border-border bg-background p-4">
                      <h4 className="mb-3 text-sm font-medium text-foreground">
                        {activeAction.type === "accept" ? "Receive Referral Handoff" : "Respond to Referral"}
                      </h4>
                      <div className="space-y-3">
                        {activeAction.type === "accept" ? (
                          <>
                            <div className="grid gap-3 md:grid-cols-2">
                              <div>
                                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                                  Planned review time
                                </label>
                                <input
                                  value={scheduledAt}
                                  onChange={(event) => setScheduledAt(event.target.value)}
                                  type="datetime-local"
                                  className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-500"
                                />
                              </div>
                              <div>
                                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                                  Receiving note
                                </label>
                                <textarea
                                  value={handoffNote}
                                  onChange={(event) => setHandoffNote(event.target.value)}
                                  rows={3}
                                  placeholder="Triage note, expected specialist, or preparation steps..."
                                  className="w-full resize-none rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-500"
                                />
                              </div>
                            </div>
                            <div className="rounded-lg bg-warning-soft px-3 py-2 text-xs text-warning-foreground">
                              Accepting here updates the receiving facility context while preserving the loop closure for the referring team in Consults.
                            </div>
                          </>
                        ) : (
                          <>
                            <div>
                              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                                Response Notes
                              </label>
                              <textarea
                                value={responseNotes}
                                onChange={(event) => setResponseNotes(event.target.value)}
                                rows={3}
                                required
                                placeholder="Assessment findings, recommendations, treatment provided..."
                                className="w-full resize-none rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
                              />
                            </div>
                            <div>
                              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                                Outcome
                              </label>
                              <select
                                value={responseOutcome}
                                onChange={(event) => setResponseOutcome(event.target.value)}
                                className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
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
                            className="flex-1 rounded-lg bg-neutral-100 py-2 text-sm font-medium text-foreground transition-colors hover:bg-neutral-100"
                          >
                            Cancel
                          </button>
                          <button
                            type="button"
                            onClick={() => activeAction.type === "accept" ? handleAccept(referral) : handleRespond(referral.id)}
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
