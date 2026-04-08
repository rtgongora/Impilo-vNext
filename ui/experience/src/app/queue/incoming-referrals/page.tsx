"use client";

/**
 * Incoming Referrals - Receiving facility view of referrals sent to this facility.
 * Allows accept, respond with outcome, and schedule teleconsult.
 * Route: /queue/incoming-referrals | pageTitle: "Incoming Referrals"
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowLeft,
  CheckCircle2,
  Clock,
  Loader2,
  MessageSquare,
  User,
  Video,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

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

  async function handleAccept(referralId: string) {
    if (!facility) return;
    setIsSubmitting(true);
    try {
      await apiClient.post(`/internal/v1/referrals/${referralId}/accept`, {
        receiving_facility_id: facility.id,
        receiving_facility_name: facility.name,
      });
      setReferrals((prev) =>
        prev.map((referral) =>
          referral.id === referralId
            ? { ...referral, attributes: { ...referral.attributes, status: "ACCEPTED" } }
            : referral,
        ),
      );
      setActiveAction(null);
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
                },
              }
            : referral,
        ),
      );
      setActiveAction(null);
      setResponseNotes("");
      setResponseOutcome("");
    } finally {
      setIsSubmitting(false);
    }
  }

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
          <div className="space-y-4">
            {referrals.map((referral) => {
              const attrs = referral.attributes;
              const urgencyStyle = URGENCY_BADGE[attrs.urgency] ?? "bg-gray-100 text-gray-600";
              const statusStyle = STATUS_BADGE[attrs.status] ?? "bg-gray-100 text-gray-600";
              const isActionable = attrs.status === "PENDING" || attrs.status === "ACCEPTED";

              return (
                <div key={referral.id} className="rounded-lg border border-gray-200 bg-white p-5">
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

                      <div className="mt-2 flex items-center gap-3 text-xs text-gray-400">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {new Date(attrs.created_at).toLocaleString()}
                        </span>
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
                          <p className="text-xs font-medium text-purple-700">Response:</p>
                          <p className="text-sm text-purple-900">{attrs.response_notes}</p>
                        </div>
                      )}
                    </div>

                    {isActionable && (
                      <div className="flex shrink-0 flex-col gap-2">
                        {attrs.status === "PENDING" && (
                          <button
                            type="button"
                            onClick={() => handleAccept(referral.id)}
                            disabled={isSubmitting}
                            className="flex items-center gap-1 rounded-lg bg-blue-600 px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
                          >
                            <CheckCircle2 className="h-3 w-3" />
                            Accept
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => setActiveAction({ id: referral.id, type: "respond" })}
                          className="flex items-center gap-1 rounded-lg bg-purple-600 px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-purple-700"
                        >
                          <MessageSquare className="h-3 w-3" />
                          Respond
                        </button>
                        <Link
                          href={`/telemedicine?patientId=${encodeURIComponent(attrs.patient_id)}&referralId=${encodeURIComponent(referral.id)}`}
                          className="flex items-center justify-center gap-1 rounded-lg bg-green-600 px-4 py-2 text-center text-xs font-medium text-white transition-colors hover:bg-green-700"
                        >
                          <Video className="h-3 w-3" />
                          Teleconsult
                        </Link>
                      </div>
                    )}
                  </div>

                  {activeAction?.id === referral.id && activeAction.type === "respond" && (
                    <div className="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
                      <h4 className="mb-3 text-sm font-medium text-gray-900">Respond to Referral</h4>
                      <div className="space-y-3">
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
                        <div className="flex gap-3">
                          <button
                            type="button"
                            onClick={() => {
                              setActiveAction(null);
                              setResponseNotes("");
                              setResponseOutcome("");
                            }}
                            className="flex-1 rounded-lg bg-gray-100 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-200"
                          >
                            Cancel
                          </button>
                          <button
                            type="button"
                            onClick={() => handleRespond(referral.id)}
                            disabled={isSubmitting || !responseNotes.trim()}
                            className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-purple-600 py-2 text-sm font-medium text-white transition-colors hover:bg-purple-700 disabled:opacity-50"
                          >
                            {isSubmitting ? (
                              <>
                                <Loader2 className="h-4 w-4 animate-spin" />
                                Submitting...
                              </>
                            ) : (
                              <>
                                <CheckCircle2 className="h-4 w-4" />
                                Submit Response
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
        )}
      </PageShell>
    </AppLayout>
  );
}
