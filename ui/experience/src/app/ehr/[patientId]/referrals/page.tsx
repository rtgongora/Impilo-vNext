"use client";

/**
 * Referrals — View existing referrals and create new ones.
 * Route: /ehr/[patientId]/referrals | pageTitle: "Referrals"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowUpRight,
  Plus,
  Loader2,
  CheckCircle2 } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useReferrals,
  useCreateReferral } from "@/hooks/queries/useReferrals";
import { useAuthStore } from "@/hooks/useAuthStore";

const URGENCY_BADGE: Record<string, string> = {
  ROUTINE: "bg-blue-100 text-blue-700",
  URGENT: "bg-orange-100 text-orange-700",
  EMERGENCY: "bg-red-100 text-red-700" };

const STATUS_BADGE: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  ACCEPTED: "bg-blue-100 text-blue-700",
  RESPONDED: "bg-purple-100 text-purple-700",
  COMPLETED: "bg-green-100 text-green-700",
  DECLINED: "bg-gray-100 text-gray-600" };

const EMPTY_FORM = {
  referral_type: "SPECIALIST",
  specialty: "",
  referred_to: "",
  referred_to_facility: "",
  reason: "",
  urgency: "ROUTINE",
  clinical_summary: "",
  referred_by: "",
  referred_by_name: "" };

export default function ReferralsPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;
  const { user } = useAuthStore();

  const { data: referralsData, isLoading } = useReferrals(patientId);
  const createReferral = useCreateReferral();

  const currentUserId = user?.id ?? "";
  const currentUserName = user?.displayName ?? user?.email ?? "";

  const referrals = referralsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    createReferral.mutate(
      {
        patientId,
        encounterId: "",
        referralType: form.referral_type,
        specialty: form.specialty,
        referredTo: form.referred_to,
        referredToFacility: form.referred_to_facility,
        reason: form.reason,
        urgency: form.urgency,
        clinicalSummary: form.clinical_summary || null },
      {
        onSuccess: () => {
          setForm({ ...EMPTY_FORM });
          setShowForm(false);
        } },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Referrals">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading referrals...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header with Create Referral button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ArrowUpRight className="w-5 h-5 text-indigo-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Referrals ({referrals.length})
                </h2>
              </div>
              <button
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Create Referral
              </button>
            </div>

            {/* Create Referral Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <ArrowUpRight className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">New Referral</h3>
                </div>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Referral Type
                      </label>
                      <select
                        value={form.referral_type}
                        onChange={(e) => updateField("referral_type", e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                      >
                        <option value="SPECIALIST">Specialist</option>
                        <option value="FACILITY">Facility</option>
                        <option value="EMERGENCY">Emergency</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Specialty
                      </label>
                      <input
                        type="text"
                        value={form.specialty}
                        onChange={(e) => updateField("specialty", e.target.value)}
                        placeholder="e.g. Cardiology"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Referred To (Provider)
                      </label>
                      <input
                        type="text"
                        value={form.referred_to}
                        onChange={(e) => updateField("referred_to", e.target.value)}
                        placeholder="e.g. Dr. Specialist Name"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Referred To (Facility)
                      </label>
                      <input
                        type="text"
                        value={form.referred_to_facility}
                        onChange={(e) => updateField("referred_to_facility", e.target.value)}
                        placeholder="e.g. City General Hospital"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Urgency
                      </label>
                      <select
                        value={form.urgency}
                        onChange={(e) => updateField("urgency", e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="EMERGENCY">Emergency</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Referred By (ID)
                      </label>
                      <input
                        type="text"
                        value={form.referred_by}
                        onChange={(e) => updateField("referred_by", e.target.value)}
                        placeholder="Practitioner ID"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Referred By (Name)
                    </label>
                    <input
                      type="text"
                      value={form.referred_by_name}
                      onChange={(e) => updateField("referred_by_name", e.target.value)}
                      placeholder="Dr. Jane Smith"
                      required
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Reason
                    </label>
                    <textarea
                      value={form.reason}
                      onChange={(e) => updateField("reason", e.target.value)}
                      placeholder="Reason for referral..."
                      rows={3}
                      required
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Clinical Summary
                    </label>
                    <textarea
                      value={form.clinical_summary}
                      onChange={(e) => updateField("clinical_summary", e.target.value)}
                      placeholder="Relevant clinical context and history..."
                      rows={3}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setShowForm(false);
                        setForm({ ...EMPTY_FORM });
                      }}
                      className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={createReferral.isPending}
                      className="flex-1 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
                    >
                      {createReferral.isPending ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Submitting...
                        </>
                      ) : (
                        <>
                          <CheckCircle2 className="w-4 h-4" />
                          Submit Referral
                        </>
                      )}
                    </button>
                  </div>
                  {createReferral.isError && (
                    <p className="text-sm text-red-600 text-center">
                      Failed to create referral. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Referrals Card List */}
            {referrals.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <ArrowUpRight className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No referrals found</p>
              </div>
            ) : (
              <div className="space-y-3">
                {referrals.map((referral) => (
                  <div
                    key={referral.id}
                    className="bg-white rounded-lg border border-gray-200 p-4 hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <h3 className="text-sm font-semibold text-gray-900">
                            {referral.attributes.referralType}
                          </h3>
                          <span className="text-sm text-gray-500">
                            &middot; {referral.attributes.specialty}
                          </span>
                        </div>
                        <p className="text-sm text-gray-700 mb-1">
                          <span className="font-medium">Referred to:</span>{" "}
                          {referral.attributes.referredTo}
                          {referral.attributes.referredToFacility && (
                            <span className="text-gray-500">
                              {" "}
                              at {referral.attributes.referredToFacility}
                            </span>
                          )}
                        </p>
                        <p className="text-sm text-gray-500 line-clamp-2">
                          {referral.attributes.reason}
                        </p>
                        {referral.attributes.outcome && (
                          <p className="text-sm text-green-700 mt-1">
                            <span className="font-medium">Outcome:</span> {referral.attributes.outcome}
                          </p>
                        )}
                        {/* Response return loop — show receiving facility response */}
                        {(referral.attributes.status === "RESPONDED" || referral.attributes.status === "COMPLETED") &&
                          (referral.attributes.response_notes || referral.attributes.responseNotes) && (
                          <div className="mt-3 p-3 bg-purple-50 border border-purple-200 rounded-lg">
                            <p className="text-xs font-semibold text-purple-700 mb-1">
                              Response from receiving facility
                              {(referral.attributes.receivingFacilityName || referral.attributes.receiving_facility_name) && (
                                <span className="font-normal"> ({referral.attributes.receivingFacilityName ?? referral.attributes.receiving_facility_name})</span>
                              )}
                            </p>
                            <p className="text-sm text-purple-900">
                              {referral.attributes.response_notes ?? referral.attributes.responseNotes}
                            </p>
                            {(referral.attributes.responded_at || referral.attributes.respondedAt) && (
                              <p className="text-xs text-purple-500 mt-1">
                                Responded: {new Date(referral.attributes.responded_at ?? referral.attributes.respondedAt ?? "").toLocaleString()}
                              </p>
                            )}
                          </div>
                        )}
                      </div>
                      <div className="flex flex-col items-end gap-2 shrink-0">
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                              URGENCY_BADGE[referral.attributes.urgency] ??
                              "bg-gray-100 text-gray-600"
                            }`}
                          >
                            {referral.attributes.urgency}
                          </span>
                          <span
                            className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                              STATUS_BADGE[referral.attributes.status] ??
                              "bg-gray-100 text-gray-600"
                            }`}
                          >
                            {referral.attributes.status}
                          </span>
                        </div>
                        <span className="text-xs text-gray-400">
                          {new Date(referral.attributes.createdAt).toLocaleDateString()}
                        </span>
                        {referral.attributes.status === "RESPONDED" && (
                          <Link
                            href={`/ehr/${patientId}/referrals`}
                            className="text-xs text-purple-600 font-medium"
                          >
                            Response received
                          </Link>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
