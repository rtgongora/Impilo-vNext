"use client";

/**
 * Teleconsults — Patient-specific teleconsult sessions linked to encounters/referrals.
 * Route: /ehr/[patientId]/teleconsults | pageTitle: "Teleconsults"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Video,
  Loader2,
  Plus,
  Phone,
  Clock,
  CheckCircle2,
  Calendar,
  AlertCircle } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useReferrals } from "@/hooks/queries/useReferrals";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface TeleconsultSession {
  id: string;
  attributes: {
    encounter_id: string | null;
    patient_id: string | null;
    provider_id: string | null;
    referral_id: string | null;
    session_type: string;
    status: string;
    scheduled_at: string | null;
    started_at: string | null;
    ended_at: string | null;
    notes: string | null;
    [key: string]: unknown;
  };
}

const STATUS_STYLE: Record<string, string> = {
  SCHEDULED: "bg-blue-100 text-blue-700",
  IN_PROGRESS: "bg-green-100 text-green-700",
  COMPLETED: "bg-gray-100 text-gray-600",
  CANCELLED: "bg-red-100 text-red-600" };

export default function TeleconsultsPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);

  const { data: encountersData } = useEncounters(patientId);
  const { data: referralsData } = useReferrals(patientId);
  const encounters = encountersData?.data ?? [];
  const referrals = referralsData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );
  const pendingReferrals = referrals.filter(
    (r) => r.attributes.status === "PENDING" || r.attributes.status === "ACCEPTED"
  );

  const [sessions, setSessions] = useState<TeleconsultSession[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showSchedule, setShowSchedule] = useState(false);
  const [scheduleForm, setScheduleForm] = useState({
    session_type: "VIDEO",
    referral_id: "",
    scheduled_date: "",
    notes: "" });
  const [isCreating, setIsCreating] = useState(false);

  // Fetch teleconsult sessions for this patient
  useState(() => {
    apiClient
      .get<ApiResponse<TeleconsultSession[]>>(
        `/internal/v1/mobile/provider/telemedicine/sessions?patient_id=${patientId}`
      )
      .then((res) => {
        setSessions(res.data ?? []);
      })
      .catch(() => {})
      .finally(() => setIsLoading(false));
  });

  async function handleSchedule() {
    if (!facility) return;
    setIsCreating(true);
    try {
      const body: Record<string, unknown> = {
        patient_id: patientId,
        provider_id: user?.id,
        facility_id: facility.id,
        session_type: scheduleForm.session_type,
        scheduled_at: scheduleForm.scheduled_date
          ? new Date(scheduleForm.scheduled_date).toISOString()
          : new Date(Date.now() + 3600000).toISOString(),
        notes: scheduleForm.notes || null };
      if (activeEncounter) {
        body.encounter_id = activeEncounter.id;
      }
      if (scheduleForm.referral_id) {
        body.referral_id = scheduleForm.referral_id;
      }

      const res = await apiClient.post<ApiResponse<TeleconsultSession>>(
        "/internal/v1/mobile/provider/telemedicine/sessions",
        body
      );
      if (res.data) {
        setSessions((prev) => [res.data, ...prev]);
      }
      setShowSchedule(false);
      setScheduleForm({ session_type: "VIDEO", referral_id: "", scheduled_date: "", notes: "" });
    } catch {
      // Error handled by UI
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="Teleconsults">

        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Video className="w-5 h-5 text-blue-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                Teleconsult Sessions
              </h2>
            </div>
            <button
              onClick={() => setShowSchedule((v) => !v)}
              className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Plus className="w-4 h-4" />
              Schedule Teleconsult
            </button>
          </div>

          {/* Schedule form */}
          {showSchedule && (
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-4">Schedule New Teleconsult</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Session Type</label>
                  <select
                    value={scheduleForm.session_type}
                    onChange={(e) => setScheduleForm((f) => ({ ...f, session_type: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="VIDEO">Video Call</option>
                    <option value="AUDIO">Audio Call</option>
                    <option value="CHAT">Chat</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Scheduled Date/Time</label>
                  <input
                    type="datetime-local"
                    value={scheduleForm.scheduled_date}
                    onChange={(e) => setScheduleForm((f) => ({ ...f, scheduled_date: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                {pendingReferrals.length > 0 && (
                  <div className="md:col-span-2">
                    <label className="block text-xs font-medium text-gray-600 mb-1">Link to Referral (optional)</label>
                    <select
                      value={scheduleForm.referral_id}
                      onChange={(e) => setScheduleForm((f) => ({ ...f, referral_id: e.target.value }))}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">None</option>
                      {pendingReferrals.map((ref) => (
                        <option key={ref.id} value={ref.id}>
                          {ref.attributes.specialty} - {ref.attributes.referredToFacility} ({ref.attributes.urgency})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
                <div className="md:col-span-2">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
                  <textarea
                    value={scheduleForm.notes}
                    onChange={(e) => setScheduleForm((f) => ({ ...f, notes: e.target.value }))}
                    rows={2}
                    placeholder="Reason for teleconsult, preparation notes..."
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                  />
                </div>
              </div>
              <div className="flex gap-3 mt-4">
                <button
                  onClick={() => setShowSchedule(false)}
                  className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSchedule}
                  disabled={isCreating}
                  className="flex-1 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                >
                  {isCreating ? (
                    <><Loader2 className="w-4 h-4 animate-spin" /> Scheduling...</>
                  ) : (
                    <><Calendar className="w-4 h-4" /> Schedule</>
                  )}
                </button>
              </div>
            </div>
          )}

          {/* Sessions list */}
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
              <span className="ml-2 text-sm text-gray-500">Loading sessions...</span>
            </div>
          ) : sessions.length === 0 ? (
            <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
              <Video className="w-10 h-10 text-gray-300 mx-auto mb-3" />
              <p className="text-gray-400 text-sm">No teleconsult sessions for this patient</p>
            </div>
          ) : (
            <div className="space-y-3">
              {sessions.map((session) => {
                const a = session.attributes;
                const statusStyle = STATUS_STYLE[a.status] ?? "bg-gray-100 text-gray-600";
                const isJoinable = a.status === "SCHEDULED" || a.status === "IN_PROGRESS";

                return (
                  <div key={session.id} className="bg-white rounded-lg border border-gray-200 p-4 hover:bg-gray-50 transition-colors">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-blue-50 flex items-center justify-center">
                          <Video className="w-5 h-5 text-blue-600" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium text-gray-900">{a.session_type} Teleconsult</span>
                            <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>
                              {a.status}
                            </span>
                          </div>
                          <div className="flex items-center gap-3 mt-0.5 text-xs text-gray-500">
                            {a.scheduled_at && (
                              <span className="flex items-center gap-1">
                                <Clock className="w-3 h-3" />
                                {new Date(a.scheduled_at).toLocaleString()}
                              </span>
                            )}
                            {a.referral_id && (
                              <span className="text-blue-600">Linked to referral</span>
                            )}
                          </div>
                        </div>
                      </div>
                      <div className="flex gap-2">
                        {isJoinable && (
                          <Link
                            href={`/telemedicine/session/${session.id}`}
                            className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-1"
                          >
                            <Phone className="w-3 h-3" /> Join
                          </Link>
                        )}
                        {a.status === "COMPLETED" && a.notes && (
                          <span className="flex items-center gap-1 text-xs text-green-600">
                            <CheckCircle2 className="w-3 h-3" /> Has notes
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </PageShell>
    </EHRLayout>
  );
}
