"use client";

/**
 * Consults & Referrals — Unified in-encounter workspace with 3 tabs:
 *   Consultations, Referrals, Teleconsults
 *
 * Lovable-aligned layout from ConsultsSection with real API integration
 * from existing Wave 2–5 hooks. This is the primary in-encounter surface
 * for all consultation, referral, and teleconsult activities.
 *
 * Route: /ehr/[patientId]/consults
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  Users,
  Send,
  Video,
  Phone,
  MessageSquare,
  Plus,
  Clock,
  CheckCircle2,
  Calendar,
  Loader2,
  User,
  Building2,
  ArrowUpRight,
  Activity,
  FileText } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import {
  useReferrals,
  useCreateReferral,
  useCompleteReferral,
  type ReferralResource } from "@/hooks/queries/useReferrals";
import {
  useTelemedicineSessions,
  useJoinTelemedicineSession,
  useCreateTelemedicineSession,
  type TelemedicineSession } from "@/hooks/queries/useTelemedicine";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { ReferralPackageBuilder } from "@/components/ReferralPackageBuilder";
import { useQuery } from "@tanstack/react-query";

// ────────────────────────────────────────────────────────────
// Status / Priority badge helpers
// ────────────────────────────────────────────────────────────

const STATUS_STYLE: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  ACCEPTED: "bg-blue-100 text-blue-700",
  RESPONDED: "bg-purple-100 text-purple-700",
  COMPLETED: "bg-green-100 text-green-700",
  CANCELLED: "bg-gray-100 text-gray-600",
  IN_PROGRESS: "bg-green-100 text-green-700",
  SCHEDULED: "bg-blue-100 text-blue-700" };

const URGENCY_STYLE: Record<string, string> = {
  EMERGENCY: "bg-red-100 text-red-700",
  URGENT: "bg-orange-100 text-orange-700",
  ROUTINE: "bg-blue-100 text-blue-700" };

type ActiveTab = "consultations" | "referrals" | "teleconsults";

// ────────────────────────────────────────────────────────────
// Main component
// ────────────────────────────────────────────────────────────

export default function ConsultsPage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const { patientId } = params;
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);

  const { data: patientData } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);
  const { data: referralsData, isLoading: loadingReferrals } = useReferrals(patientId);
  const { data: teleData, isLoading: loadingTele } = useTelemedicineSessions();

  // Clinical data for ReferralPackageBuilder
  const { data: allergiesData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["consult-allergies", patientId],
    queryFn: () => apiClient.get(`/internal/v1/allergies?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: conditionsData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["consult-conditions", patientId],
    queryFn: () => apiClient.get(`/internal/v1/conditions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: medsData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["consult-meds", patientId],
    queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const patientAllergies = (allergiesData?.data ?? []).map((a) => (a.attributes.allergen as string) ?? "").filter(Boolean);
  const patientConditions = (conditionsData?.data ?? []).map((c) => (c.attributes.condition_name as string) ?? "").filter(Boolean);
  const patientMedications = (medsData?.data ?? []).map((m) => (m.attributes.medication_name as string) ?? "").filter(Boolean);

  const patient = patientData?.data;
  const patientName = (patient?.attributes as Record<string, unknown>)?.displayName as string
    ?? (patient?.attributes as Record<string, unknown>)?.givenName as string ?? "Patient";
  const patientDob = (patient?.attributes as Record<string, unknown>)?.dateOfBirth as string | undefined;
  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );
  const allReferrals = referralsData?.data ?? [];
  const allSessions: TelemedicineSession[] = teleData?.data ?? [];

  // Filter by patient
  const patientSessions = allSessions.filter(
    (s) => s.attributes.patient_id === patientId
  );

  // Split referrals: consultations (type=CONSULTATION) vs referrals (everything else)
  const consultations = allReferrals.filter(
    (r) =>
      r.attributes.referralType === "CONSULTATION" ||
      (r.attributes as Record<string, unknown>).referral_type === "CONSULTATION"
  );
  const referrals = allReferrals.filter(
    (r) =>
      r.attributes.referralType !== "CONSULTATION" &&
      (r.attributes as Record<string, unknown>).referral_type !== "CONSULTATION"
  );

  const createReferral = useCreateReferral();
  const completeReferral = useCompleteReferral();
  const joinSession = useJoinTelemedicineSession();
  const createSession = useCreateTelemedicineSession();

  const [activeTab, setActiveTab] = useState<ActiveTab>("referrals");
  const [showNewReferral, setShowNewReferral] = useState(false);
  const [showNewConsult, setShowNewConsult] = useState(false);

  function handleScheduleTeleconsult(mode: string) {
    if (!facility) return;
    createSession.mutate({
      patient_id: patientId,
      provider_id: user?.id,
      facility_id: facility.id,
      session_type: mode,
      encounter_id: activeEncounter?.id });
  }

  // Tabs definition
  const tabs: { key: ActiveTab; label: string; icon: React.ReactNode; count: number }[] = [
    { key: "consultations", label: "Consultations", icon: <Users className="w-4 h-4" />, count: consultations.length },
    { key: "referrals", label: "Referrals", icon: <Send className="w-4 h-4" />, count: referrals.length },
    { key: "teleconsults", label: "Teleconsults", icon: <Video className="w-4 h-4" />, count: patientSessions.length },
  ];

  const isLoading = loadingReferrals || loadingTele;

  return (
    <EHRLayout>
      <PageShell title="Consults & Referrals">

        <div className="space-y-5">
          {/* Patient Context Header — Lovable-aligned */}
          {patient && (
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-blue-200 flex items-center justify-center">
                    <Users className="w-5 h-5 text-blue-700" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">{patient.attributes.displayName}</h3>
                    <p className="text-sm text-gray-600">
                      Consults & Referrals for this encounter
                    </p>
                  </div>
                </div>
                {activeEncounter && (
                  <span className="px-3 py-1 bg-green-100 text-green-700 text-xs font-medium rounded-full">
                    Active Encounter: {activeEncounter.attributes.encounterType}
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Quick Access — Lovable: Worklist + Telemedicine Hub */}
          <div className="flex justify-end gap-2">
            <Link
              href="/queue/incoming-referrals"
              className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <Activity className="w-4 h-4" />
              Incoming Worklist
            </Link>
            <Link
              href="/telemedicine"
              className="inline-flex items-center gap-2 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Video className="w-4 h-4" />
              Telemedicine Hub
            </Link>
          </div>

          {/* Tab Navigation — Lovable 3-tab layout */}
          <div className="border-b border-gray-200">
            <div className="grid grid-cols-3 gap-0">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  className={`flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                    activeTab === tab.key
                      ? "border-blue-600 text-blue-600"
                      : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                  }`}
                >
                  {tab.icon}
                  {tab.label}
                  {tab.count > 0 && (
                    <span className={`px-1.5 py-0.5 text-xs rounded-full ${
                      activeTab === tab.key ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-600"
                    }`}>
                      {tab.count}
                    </span>
                  )}
                </button>
              ))}
            </div>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
              <span className="ml-2 text-sm text-gray-500">Loading...</span>
            </div>
          ) : (
            <>
              {/* ═══════════ CONSULTATIONS TAB ═══════════ */}
              {activeTab === "consultations" && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-semibold text-gray-900">Consultation Requests</h3>
                    <button
                      onClick={() => setShowNewConsult((v) => !v)}
                      className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                      Request Consult
                    </button>
                  </div>

                  {showNewConsult && (
                    <ReferralPackageBuilder
                      patientName={patientName}
                      patientDob={patientDob}
                      conditions={patientConditions}
                      allergies={patientAllergies}
                      medications={patientMedications}
                      referralType="CONSULTATION"
                      onSubmit={(data) => {
                        createReferral.mutate({
                          patientId,
                          encounterId: activeEncounter?.id ?? "",
                          referralType: "CONSULTATION",
                          specialty: data.specialty,
                          referredTo: data.referred_to,
                          referredToFacility: data.referred_to_facility,
                          reason: data.reason,
                          urgency: data.urgency,
                          clinicalSummary: data.clinical_summary,
                        }, { onSuccess: () => setShowNewConsult(false) });
                      }}
                      onCancel={() => setShowNewConsult(false)}
                      isPending={createReferral.isPending}
                    />
                  )}

                  {consultations.length === 0 ? (
                    <EmptyState icon={<Users className="w-12 h-12" />} label="No consultations for this patient" actionLabel="Request first consultation" onAction={() => setShowNewConsult(true)} />
                  ) : (
                    consultations.map((c) => (
                      <ReferralCard
                        key={c.id}
                        referral={c}
                        patientId={patientId}
                        onJoin={(id) => router.push(`/telemedicine/session/${id}`)}
                        onComplete={(id) => completeReferral.mutate({ id })}
                      />
                    ))
                  )}
                </div>
              )}

              {/* ═══════════ REFERRALS TAB ═══════════ */}
              {activeTab === "referrals" && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-semibold text-gray-900">Referral Tracking</h3>
                    <button
                      onClick={() => setShowNewReferral((v) => !v)}
                      className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                      New Referral
                    </button>
                  </div>

                  {showNewReferral && (
                    <ReferralPackageBuilder
                      patientName={patientName}
                      patientDob={patientDob}
                      conditions={patientConditions}
                      allergies={patientAllergies}
                      medications={patientMedications}
                      referralType="SPECIALIST"
                      onSubmit={(data) => {
                        createReferral.mutate({
                          patientId,
                          encounterId: activeEncounter?.id ?? "",
                          referralType: data.referral_type,
                          specialty: data.specialty,
                          referredTo: data.referred_to,
                          referredToFacility: data.referred_to_facility,
                          reason: data.reason,
                          urgency: data.urgency,
                          clinicalSummary: data.clinical_summary,
                        }, { onSuccess: () => setShowNewReferral(false) });
                      }}
                      onCancel={() => setShowNewReferral(false)}
                      isPending={createReferral.isPending}
                    />
                  )}

                  {referrals.length === 0 ? (
                    <EmptyState icon={<Building2 className="w-12 h-12" />} label="No referrals found" actionLabel="Create first referral" onAction={() => setShowNewReferral(true)} />
                  ) : (
                    referrals.map((r) => (
                      <ReferralCard
                        key={r.id}
                        referral={r}
                        patientId={patientId}
                        onJoin={(id) => router.push(`/telemedicine/session/${id}`)}
                        onComplete={(id) => completeReferral.mutate({ id })}
                        showWorkflowStage
                      />
                    ))
                  )}

                  {/* Create new dashed card — Lovable pattern */}
                  <button
                    onClick={() => setShowNewReferral(true)}
                    className="w-full p-4 border-2 border-dashed border-gray-300 rounded-lg text-center text-gray-400 hover:bg-gray-50 hover:border-gray-400 transition-colors"
                  >
                    <Plus className="w-5 h-5 mx-auto mb-1" />
                    <span className="text-sm">Create new referral</span>
                  </button>
                </div>
              )}

              {/* ═══════════ TELECONSULTS TAB ═══════════ */}
              {activeTab === "teleconsults" && (
                <div className="space-y-4">
                  {/* Active Session Alert — Lovable pattern */}
                  {patientSessions.some((s) => s.attributes.status === "IN_PROGRESS") && (
                    <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                      {patientSessions
                        .filter((s) => s.attributes.status === "IN_PROGRESS")
                        .map((s) => (
                          <div key={s.id} className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <div className="p-3 rounded-full bg-green-200 animate-pulse">
                                <Video className="w-4 h-4 text-green-700" />
                              </div>
                              <div>
                                <p className="font-semibold text-green-800">Active Teleconsultation</p>
                                <p className="text-sm text-gray-600">
                                  {s.attributes.session_type} session
                                  {s.attributes.started_at && ` — started ${new Date(s.attributes.started_at).toLocaleTimeString()}`}
                                </p>
                              </div>
                            </div>
                            <Link
                              href={`/telemedicine/session/${s.id}`}
                              className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-2"
                            >
                              <Video className="w-4 h-4" /> Rejoin Session
                            </Link>
                          </div>
                        ))}
                    </div>
                  )}

                  {/* Upcoming Sessions — Lovable pattern */}
                  {patientSessions.filter((s) => s.attributes.status === "SCHEDULED").length > 0 && (
                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
                      <h4 className="text-sm font-medium text-gray-900 flex items-center gap-2">
                        <Clock className="w-4 h-4 text-blue-600" />
                        Upcoming Sessions
                      </h4>
                      {patientSessions
                        .filter((s) => s.attributes.status === "SCHEDULED")
                        .map((s) => (
                          <div key={s.id} className="flex items-center justify-between p-3 bg-white rounded-lg border">
                            <div className="flex items-center gap-3">
                              <div className="p-2 rounded-full bg-blue-100">
                                <Video className="w-4 h-4 text-blue-600" />
                              </div>
                              <div>
                                <p className="text-sm font-medium">{s.attributes.session_type} Teleconsult</p>
                                <p className="text-xs text-gray-500">
                                  {s.attributes.scheduled_at && new Date(s.attributes.scheduled_at).toLocaleString()}
                                </p>
                              </div>
                            </div>
                            <Link
                              href={`/telemedicine/session/${s.id}`}
                              className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors"
                            >
                              Join
                            </Link>
                          </div>
                        ))}
                    </div>
                  )}

                  {/* All Teleconsultations */}
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-semibold text-gray-900">Teleconsultation Sessions</h3>
                    <button
                      onClick={() => handleScheduleTeleconsult("VIDEO")}
                      disabled={createSession.isPending || !facility}
                      className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                    >
                      <Video className="w-4 h-4" />
                      Schedule Teleconsult
                    </button>
                  </div>

                  {patientSessions.length === 0 ? (
                    <EmptyState icon={<Video className="w-12 h-12" />} label="No teleconsultations for this patient" actionLabel="Schedule first teleconsult" onAction={() => handleScheduleTeleconsult("VIDEO")} />
                  ) : (
                    patientSessions.map((s) => (
                      <TeleconsultCard key={s.id} session={s} />
                    ))
                  )}

                  {/* Quick Connect — Lovable pattern */}
                  <div className="pt-4 border-t border-gray-200">
                    <p className="text-sm font-medium text-gray-900 mb-3">Quick Connect</p>
                    <div className="grid grid-cols-5 gap-2">
                      <button onClick={() => handleScheduleTeleconsult("VIDEO")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <Video className="w-5 h-5" />
                        <span className="text-xs">Video</span>
                      </button>
                      <button onClick={() => handleScheduleTeleconsult("AUDIO")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <Phone className="w-5 h-5" />
                        <span className="text-xs">Audio</span>
                      </button>
                      <button onClick={() => handleScheduleTeleconsult("CHAT")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <MessageSquare className="w-5 h-5" />
                        <span className="text-xs">Chat</span>
                      </button>
                      <button onClick={() => handleScheduleTeleconsult("ASYNC_REVIEW")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <FileText className="w-5 h-5" />
                        <span className="text-xs">Async</span>
                      </button>
                      <button onClick={() => handleScheduleTeleconsult("CASE_REVIEW")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <Users className="w-5 h-5" />
                        <span className="text-xs">Board</span>
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </PageShell>
    </EHRLayout>
  );
}

// ────────────────────────────────────────────────────────────
// Sub-components
// ────────────────────────────────────────────────────────────

function EmptyState({ icon, label, actionLabel, onAction }: {
  icon: React.ReactNode; label: string; actionLabel: string; onAction: () => void;
}) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-10 text-center">
      <div className="text-gray-300 mx-auto mb-4 w-12 h-12 flex items-center justify-center">{icon}</div>
      <p className="text-gray-400 text-sm">{label}</p>
      <button onClick={onAction} className="mt-3 inline-flex items-center gap-1 px-3 py-1.5 border border-gray-300 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
        <Plus className="w-4 h-4" /> {actionLabel}
      </button>
    </div>
  );
}

function getWorkflowStage(status: string): { stage: number; label: string } {
  const map: Record<string, { stage: number; label: string }> = {
    PENDING: { stage: 2, label: "Consent & Submit" },
    ACCEPTED: { stage: 4, label: "Under Review" },
    RESPONDED: { stage: 6, label: "Actions Documented" },
    COMPLETED: { stage: 7, label: "Loop Closed" } };
  return map[status] ?? { stage: 1, label: "Building Package" };
}

function ReferralCard({ referral, patientId, onJoin, onComplete, showWorkflowStage }: {
  referral: ReferralResource; patientId: string;
  onJoin: (id: string) => void; onComplete: (id: string) => void;
  showWorkflowStage?: boolean;
}) {
  const a = referral.attributes;
  const statusStyle = STATUS_STYLE[a.status] ?? "bg-gray-100 text-gray-600";
  const urgencyStyle = URGENCY_STYLE[a.urgency] ?? "bg-gray-100 text-gray-600";
  const wf = getWorkflowStage(a.status);
  const hasResponse = a.response_notes || a.responseNotes;

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 hover:bg-gray-50 transition-colors">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 space-y-2">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-full bg-blue-50">
              <Building2 className="w-4 h-4 text-blue-600" />
            </div>
            <div className="flex-1">
              <h4 className="font-semibold text-sm text-gray-900">{a.specialty || a.referralType}</h4>
              <p className="text-xs text-gray-500">{a.referredTo || "Pending assignment"}</p>
            </div>
            <div className="flex items-center gap-2">
              <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${urgencyStyle}`}>{a.urgency}</span>
              <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>{a.status}</span>
            </div>
          </div>

          {/* Workflow Stage — Lovable 7-stage bar */}
          {showWorkflowStage && (
            <div className="pl-11">
              <div className="flex items-center gap-2 mb-1">
                <span className="text-xs text-gray-500">Workflow:</span>
                <span className="text-xs font-medium text-gray-700">{wf.stage}/7 — {wf.label}</span>
              </div>
              <div className="flex gap-0.5">
                {[1, 2, 3, 4, 5, 6, 7].map((s) => (
                  <div key={s} className={`h-1.5 flex-1 rounded-full ${
                    s <= wf.stage ? (s === wf.stage ? "bg-blue-600" : "bg-green-500") : "bg-gray-200"
                  }`} />
                ))}
              </div>
            </div>
          )}

          <div className="pl-11 space-y-1">
            <p className="text-sm text-gray-600">{a.reason}</p>
            {a.clinicalSummary && (
              <div className="p-2.5 bg-gray-50 rounded-md border border-gray-100">
                <p className="text-xs text-gray-500 mb-0.5">Clinical Summary:</p>
                <p className="text-sm italic text-gray-700">{a.clinicalSummary}</p>
              </div>
            )}
            {hasResponse && (
              <div className="p-2.5 bg-purple-50 rounded-md border border-purple-200">
                <p className="text-xs font-semibold text-purple-700 mb-0.5">
                  Specialist Response
                  {(a.receivingFacilityName || a.receiving_facility_name) && (
                    <span className="font-normal"> ({a.receivingFacilityName ?? a.receiving_facility_name})</span>
                  )}
                </p>
                <p className="text-sm text-purple-900">{a.response_notes ?? a.responseNotes}</p>
                {a.outcome && <p className="text-xs text-purple-700 mt-1">Outcome: {a.outcome}</p>}
              </div>
            )}
            <div className="flex items-center gap-4 text-xs text-gray-400">
              <span>Created: {new Date(a.createdAt).toLocaleDateString()}</span>
              {(a.acceptedAt || a.accepted_at) && (
                <span className="text-blue-600">Accepted: {new Date((a.acceptedAt ?? a.accepted_at)!).toLocaleDateString()}</span>
              )}
            </div>
          </div>
        </div>
      </div>

      {a.status !== "COMPLETED" && a.status !== "CANCELLED" && (
        <div className="flex items-center gap-2 mt-3 pt-3 border-t border-gray-100">
          {(a.status === "ACCEPTED" || a.status === "PENDING") && (
            <button onClick={() => onJoin(referral.id)}
              className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-1">
              <Video className="w-3 h-3" /> Teleconsult
            </button>
          )}
          {a.status === "RESPONDED" && (
            <button onClick={() => onComplete(referral.id)}
              className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-1">
              <CheckCircle2 className="w-3 h-3" /> Complete Loop
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function TeleconsultCard({ session }: { session: TelemedicineSession }) {
  const a = session.attributes;
  const statusStyle = STATUS_STYLE[a.status] ?? "bg-gray-100 text-gray-600";
  const isJoinable = a.status === "SCHEDULED" || a.status === "IN_PROGRESS";

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 hover:bg-gray-50 transition-colors">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3 flex-1">
          <div className={`p-2 rounded-full ${a.status === "IN_PROGRESS" ? "bg-green-100" : "bg-blue-50"}`}>
            <Video className={`w-4 h-4 ${a.status === "IN_PROGRESS" ? "text-green-600" : "text-blue-600"}`} />
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-900">{a.session_type} Teleconsult</span>
              <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600">{a.session_type}</span>
              <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>{a.status}</span>
            </div>
            <div className="flex items-center gap-3 mt-0.5 text-xs text-gray-500">
              {a.scheduled_at && (
                <span className="flex items-center gap-1">
                  <Calendar className="w-3 h-3" />
                  {new Date(a.scheduled_at).toLocaleString()}
                </span>
              )}
              {a.referral_id && <span className="text-blue-600">Linked to referral</span>}
              {a.duration_seconds != null && a.duration_seconds > 0 && (
                <span>{Math.round(a.duration_seconds / 60)} min</span>
              )}
            </div>
            {a.notes && <p className="text-xs text-gray-500 mt-1">{a.notes}</p>}
          </div>
        </div>
        {isJoinable && (
          <Link href={`/telemedicine/session/${session.id}`}
            className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-1">
            <Video className="w-3 h-3" /> {a.status === "IN_PROGRESS" ? "Rejoin" : "Join"}
          </Link>
        )}
      </div>
    </div>
  );
}

