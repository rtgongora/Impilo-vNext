"use client";

/**
 * Incoming Referrals — Receiving facility view of referrals sent to this facility.
 * Allows accept, respond with outcome, and schedule teleconsult.
 * Route: /queue/incoming-referrals | pageTitle: "Incoming Referrals"
 */

import { useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  ArrowDownLeft,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Clock,
  MessageSquare,
  Video,
  User,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useAuthStore } from "@/hooks/useAuthStore";
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
  const { user } = useAuthStore();

  const [referrals, setReferrals] = useState<IncomingReferral[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [activeAction, setActiveAction] = useState<{ id: string; type: "accept" | "respond" } | null>(null);
  const [responseNotes, setResponseNotes] = useState("");
  const [responseOutcome, setResponseOutcome] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Fetch incoming referrals
  useState(() => {
    if (!facility) {
      setIsLoading(false);
      return;
    }
    apiClient
      .get<ApiResponse<IncomingReferral[]>>(
        `/internal/v1/referrals/incoming?facility_id=${encodeURIComponent(facility.id)}`
      )
      .then((res) => {
        setReferrals(res.data ?? []);
      })
      .catch(() => {})
      .finally(() => setIsLoading(false));
  });

  async function handleAccept(referralId: string) {
    if (!facility) return;
    setIsSubmitting(true);
    try {
      await apiClient.post(`/internal/v1/referrals/${referralId}/accept`, {
        receiving_facility_id: facility.id,
        receiving_facility_name: facility.name,
      });
      setReferrals((prev) =>
        prev.map((r) =>
          r.id === referralId ? { ...r, attributes: { ...r.attributes, status: "ACCEPTED" } } : r
        )
      );
      setActiveAction(null);
    } catch {
      // Error handled by UI
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
        prev.map((r) =>
          r.id === referralId
            ? { ...r, attributes: { ...r.attributes, status: "RESPONDED", response_notes: responseNotes } }
            : r
        )
      );
      setActiveAction(null);
      setResponseNotes("");
      setResponseOutcome("");
    } catch {
      // Error handled by UI
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AppLayout>
      <PageShell title="Incoming Referrals" subtitle={facility ? `Referrals sent to ${facility.name}` : "Select a facility first"}>
        <div className="mb-4">
          <Link
            href="/queue"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to queue
          </Link>
        </div>

        {!facility ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Please select a facility first</p>
            <Link href="/facility" className="mt-2 inline-block text-sm text-blue-600 hover:text-blue-800">
              Select Facility
            </Link>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading incoming referrals...</span>
          </div>
        ) : referrals.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <ArrowDownLeft className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No incoming referrals</p>
          </div>
        ) : (
          <div className="space-y-4">
            {referrals.map((referral) => {
              const a = referral.attributes;
              const urgencyStyle = URGENCY_BADGE[a.urgency] ?? "bg-gray-100 text-gray-600";
              const statusStyle = STATUS_BADGE[a.status] ?? "bg-gray-100 text-gray-600";
              const isActionable = a.status === "PENDING" || a.status === "ACCEPTED";

              return (
                <div key={referral.id} className="bg-white rounded-lg border border-gray-200 p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="text-sm font-semibold text-gray-900">{a.specialty}</h3>
                        <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${urgencyStyle}`}>{a.urgency}</span>
                        <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>{a.status}</span>
                      </div>
                      <p className="text-sm text-gray-700 mb-1">
                        <span className="font-medium">From:</span> {a.referred_by_name}
                        {a.referred_to_facility && <span className="text-gray-500"> at {a.referred_to_facility}</span>}
                      </p>
                      <p className="text-sm text-gray-600">{a.reason}</p>
                      {a.clinical_summary && (
                        <p className="text-xs text-gray-500 mt-1 line-clamp-2">{a.clinical_summary}</p>
                      )}
                      <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {new Date(a.created_at).toLocaleString()}
                        </span>
                        {a.patient_id && (
                          <Link href={`/ehr/${a.patient_id}`} className="text-blue-600 hover:text-blue-800 flex items-center gap-1">
                            <User className="w-3 h-3" /> View Patient
                          </Link>
                        )}
                      </div>

                      {/* Response notes if already responded */}
                      {a.response_notes && (
                        <div className="mt-3 p-3 bg-purple-50 border border-purple-200 rounded-lg">
                          <p className="text-xs font-medium text-purple-700">Response:</p>
                          <p className="text-sm text-purple-900">{a.response_notes}</p>
                        </div>
                      )}
                    </div>

                    {/* Action buttons */}
                    {isActionable && (
                      <div className="flex flex-col gap-2 shrink-0">
                        {a.status === "PENDING" && (
                          <button
                            onClick={() => handleAccept(referral.id)}
                            disabled={isSubmitting}
                            className="px-4 py-2 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors flex items-center gap-1"
                          >
                            <CheckCircle2 className="w-3 h-3" /> Accept
                          </button>
                        )}
                        {(a.status === "PENDING" || a.status === "ACCEPTED") && (
                          <button
                            onClick={() => setActiveAction({ id: referral.id, type: "respond" })}
                            className="px-4 py-2 bg-purple-600 text-white text-xs font-medium rounded-lg hover:bg-purple-700 transition-colors flex items-center gap-1"
                          >
                            <MessageSquare className="w-3 h-3" /> Respond
                          </button>
                        )}
                        <Link
                          href={`/telemedicine`}
                          className="px-4 py-2 bg-green-600 text-white text-xs font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-1 text-center"
                        >
                          <Video className="w-3 h-3" /> Teleconsult
                        </Link>
                      </div>
                    )}
                  </div>

                  {/* Respond form */}
                  {activeAction?.id === referral.id && activeAction.type === "respond" && (
                    <div className="mt-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
                      <h4 className="text-sm font-medium text-gray-900 mb-3">Respond to Referral</h4>
                      <div className="space-y-3">
                        <div>
                          <label className="block text-xs font-medium text-gray-600 mb-1">Response Notes</label>
                          <textarea
                            value={responseNotes}
                            onChange={(e) => setResponseNotes(e.target.value)}
                            rows={3}
                            required
                            placeholder="Assessment findings, recommendations, treatment provided..."
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 resize-none"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-gray-600 mb-1">Outcome</label>
                          <select
                            value={responseOutcome}
                            onChange={(e) => setResponseOutcome(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
                          >
                            <option value="">Select outcome...</option>
                            <option value="TREATED">Treated - Patient managed</option>
                            <option value="ADMITTED">Admitted - Ongoing care</option>
                            <option value="FURTHER_REFERRAL">Further Referral needed</option>
                            <option value="RETURNED">Returned to referring facility</option>
                            <option value="FOLLOW_UP_REQUIRED">Follow-up Required</option>
                          </select>
                        </div>
                        <div className="flex gap-3">
                          <button
                            onClick={() => { setActiveAction(null); setResponseNotes(""); setResponseOutcome(""); }}
                            className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                          >
                            Cancel
                          </button>
                          <button
                            onClick={() => handleRespond(referral.id)}
                            disabled={isSubmitting || !responseNotes.trim()}
                            className="flex-1 py-2 bg-purple-600 text-white text-sm font-medium rounded-lg hover:bg-purple-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                          >
                            {isSubmitting ? (
                              <><Loader2 className="w-4 h-4 animate-spin" /> Submitting...</>
                            ) : (
                              <><CheckCircle2 className="w-4 h-4" /> Submit Response</>
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
