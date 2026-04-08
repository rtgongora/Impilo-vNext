"use client";

/**
 * Telemedicine Session — Active session view with join, vitals capture, notes, and end.
 * Route: /telemedicine/session/[sessionId] | pageTitle: "Telemedicine Session"
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  Video,
  Phone,
  PhoneOff,
  Clock,
  Activity,
  FileText,
  User,
  CheckCircle2,
  AlertCircle,
  Save,
  Calendar,
  ArrowRightLeft,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useTelemedicineSessions,
  useJoinTelemedicineSession,
  useEndTelemedicineSession,
} from "@/hooks/queries/useTelemedicine";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRespondReferral } from "@/hooks/queries/useReferrals";
import {
  buildTeleconsultCompletionBody,
  COORDINATION_COPY,
  formatTeleconsultFollowUpLabel,
  mapTeleconsultFollowUpToOutcome,
} from "@/lib/consult-workflows";

type FollowUpOption =
  | "NONE"
  | "REVIEW"
  | "REPEAT_TELECONSULT"
  | "ADMIT"
  | "REFER_FURTHER";

export default function TelemedicineSessionPage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const { sessionId } = params;
  const { user } = useAuthStore();

  const { data, isLoading } = useTelemedicineSessions();
  const joinSession = useJoinTelemedicineSession();
  const endSession = useEndTelemedicineSession();
  const respondReferral = useRespondReferral();

  const session = data?.data?.find((s) => s.id === sessionId);
  const attrs = session?.attributes;
  const isActive = attrs?.status === "IN_PROGRESS";
  const isJoinable = attrs?.status === "SCHEDULED" || attrs?.status === "IN_PROGRESS";

  // Session notes
  const [sessionNotes, setSessionNotes] = useState("");
  const [showEndConfirm, setShowEndConfirm] = useState(false);

  // Vitals state
  const [systolic, setSystolic] = useState("");
  const [diastolic, setDiastolic] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [temperature, setTemperature] = useState("");
  const [vitalsSaving, setVitalsSaving] = useState(false);
  const [vitalsSaved, setVitalsSaved] = useState(false);

  // Encounter note state
  const [noteBody, setNoteBody] = useState("");
  const [noteSaving, setNoteSaving] = useState(false);
  const [noteSaved, setNoteSaved] = useState(false);
  const [completionFindings, setCompletionFindings] = useState("");
  const [completionPlan, setCompletionPlan] = useState("");
  const [completionFollowUp, setCompletionFollowUp] = useState<FollowUpOption>("NONE");
  const [completionSaving, setCompletionSaving] = useState(false);
  const [completionSaved, setCompletionSaved] = useState(false);
  const [completionError, setCompletionError] = useState<string | null>(null);

  function handleJoin() {
    joinSession.mutate({ id: sessionId });
  }

  function handleEnd() {
    endSession.mutate(
      { id: sessionId, notes: sessionNotes || undefined },
      {
        onSuccess: () => {
          if (attrs?.referral_id && attrs.patient_id) {
            router.push(`/ehr/${attrs.patient_id}/consults?tab=referrals`);
            return;
          }
          router.push("/telemedicine");
        },
      }
    );
  }

  async function handleSaveVitals() {
    if (!attrs?.patient_id || !attrs?.encounter_id) return;
    setVitalsSaving(true);
    setVitalsSaved(false);
    try {
      await apiClient.post("/internal/v1/vitals", {
        patient_id: attrs.patient_id,
        encounter_id: attrs.encounter_id,
        recorded_by: user?.id ?? "system",
        systolic: systolic ? Number(systolic) : null,
        diastolic: diastolic ? Number(diastolic) : null,
        heart_rate: heartRate ? Number(heartRate) : null,
        temperature: temperature ? Number(temperature) : null,
      });
      setVitalsSaved(true);
    } catch {
      // Error handled by UI feedback
    } finally {
      setVitalsSaving(false);
    }
  }

  async function handleSaveNote() {
    if (!attrs?.patient_id || !attrs?.encounter_id) return;
    setNoteSaving(true);
    setNoteSaved(false);
    try {
      await apiClient.post("/internal/v1/clinical-notes", {
        patient_id: attrs.patient_id,
        encounter_id: attrs.encounter_id,
        note_type: "CONSULTATION",
        body: noteBody,
        author_id: user?.id ?? "system",
        author_name: user?.displayName ?? user?.email ?? "Provider",
      });
      setNoteSaved(true);
      setNoteBody("");
    } catch {
      // Error handled by UI feedback
    } finally {
      setNoteSaving(false);
    }
  }

  async function handleSaveCompletionNote() {
    if (!attrs?.patient_id || !attrs?.encounter_id) return;

    setCompletionSaving(true);
    setCompletionSaved(false);
    setCompletionError(null);

    const findings = completionFindings.trim();
    const plan = completionPlan.trim();
    const followUpLabel = formatTeleconsultFollowUpLabel(completionFollowUp);
    const body = buildTeleconsultCompletionBody({
      findings,
      plan,
      followUpLabel,
      referralId: attrs.referral_id,
      sessionType: attrs.session_type,
    });

    try {
      await apiClient.post("/internal/v1/clinical-notes", {
        patient_id: attrs.patient_id,
        encounter_id: attrs.encounter_id,
        note_type: "CONSULTATION",
        objective: findings || null,
        assessment: plan || null,
        plan: followUpLabel,
        body,
        author_id: user?.id ?? "system",
        author_name: user?.displayName ?? user?.email ?? "Provider",
      });

      if (attrs.referral_id) {
        await respondReferral.mutateAsync({
          id: attrs.referral_id,
          response_notes: body,
          outcome: mapTeleconsultFollowUpToOutcome(completionFollowUp),
        });
      }

      setCompletionSaved(true);
      setSessionNotes((current) => current || body);
      setCompletionFindings("");
      setCompletionPlan("");
      setCompletionFollowUp("NONE");
    } catch {
      setCompletionError(
        attrs.referral_id
          ? "The consultation note or referral response could not be saved."
          : "The consultation completion note could not be saved.",
      );
    } finally {
      setCompletionSaving(false);
    }
  }

  return (
    <AppLayout>
      <PageShell title="Telemedicine Session">
        <div className="mb-4">
          <Link
            href="/telemedicine"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to Telemedicine Hub
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading session...</span>
          </div>
        ) : !session || !attrs ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Session not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Session Header */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-full bg-blue-50 flex items-center justify-center">
                      <Video className="w-6 h-6 text-blue-600" />
                    </div>
                    <div>
                      <h2 className="text-lg font-semibold text-gray-900">
                        {attrs.session_type} Teleconsult
                      </h2>
                      <span
                        className={`inline-block px-2.5 py-0.5 text-xs font-medium rounded-full ${
                          isActive
                            ? "bg-green-100 text-green-700"
                            : attrs.status === "SCHEDULED"
                              ? "bg-blue-100 text-blue-700"
                              : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {attrs.status}
                      </span>
                    </div>
                  </div>
                  <div className="flex items-center gap-4 mt-3 text-sm text-gray-500">
                    {attrs.patient_id && (
                      <Link
                        href={`/ehr/${attrs.patient_id}`}
                        className="flex items-center gap-1 text-blue-600 hover:text-blue-800 transition-colors"
                      >
                        <User className="w-4 h-4" />
                        Patient: {attrs.patient_id.substring(0, 8)}...
                      </Link>
                    )}
                    {attrs.scheduled_at && (
                      <span className="flex items-center gap-1">
                        <Calendar className="w-4 h-4" />
                        {new Date(attrs.scheduled_at).toLocaleString()}
                      </span>
                    )}
                    {attrs.started_at && (
                      <span className="flex items-center gap-1">
                        <Clock className="w-4 h-4" />
                        Started: {new Date(attrs.started_at).toLocaleTimeString()}
                      </span>
                    )}
                    {attrs.referral_id && (
                      <span className="inline-flex items-center rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">
                        Referral-linked
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex gap-2">
                  {isJoinable && (
                    <button
                      onClick={handleJoin}
                      disabled={joinSession.isPending}
                      className="px-5 py-2.5 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors flex items-center gap-2"
                    >
                      <Phone className="w-4 h-4" />
                      {joinSession.isPending ? "Joining..." : isActive ? "Rejoin" : "Join Session"}
                    </button>
                  )}
                </div>
              </div>

              {/* Room/Channel info after join */}
              {joinSession.isSuccess && joinSession.data?.data?.attributes?.channel && (
                <div className="mt-4 p-3 bg-green-50 border border-green-200 rounded-lg">
                  <p className="text-sm text-green-800">
                    <strong>Connected.</strong> Channel:{" "}
                    {joinSession.data.data.attributes.channel}
                  </p>
                </div>
              )}
            </div>

            {attrs.referral_id && (
              <div className={`rounded-2xl border p-5 ${
                completionSaved
                  ? "border-emerald-200 bg-emerald-50"
                  : "border-amber-200 bg-amber-50"
              }`}>
                <div className="flex items-start gap-3">
                  <div className={`flex h-10 w-10 items-center justify-center rounded-2xl ${
                    completionSaved ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"
                  }`}>
                    <ArrowRightLeft className="h-4 w-4" />
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-semibold text-gray-900">{COORDINATION_COPY.loopClosureHandoff}</p>
                    <p className="mt-1 text-sm text-gray-700">
                      This teleconsult is feeding a live referral loop. Save the completion note to return specialist guidance, then end the session and continue in the patient consults workspace.
                    </p>
                    <div className="mt-3 grid gap-2 sm:grid-cols-3">
                      <div className={`rounded-xl px-3 py-2 text-xs font-medium ${
                        completionSaved ? "bg-white text-emerald-700" : "bg-white text-amber-700"
                      }`}>
                        1. Completion note {completionSaved ? "saved" : "pending"}
                      </div>
                      <div className={`rounded-xl px-3 py-2 text-xs font-medium ${
                        completionSaved ? "bg-white text-emerald-700" : "bg-white text-slate-600"
                      }`}>
                        2. Referral response {completionSaved ? "sent" : "waiting on note"}
                      </div>
                      <div className="rounded-xl bg-white px-3 py-2 text-xs font-medium text-slate-600">
                        3. Final loop closure happens in Consults
                      </div>
                    </div>
                    {completionSaved && attrs.patient_id && (
                      <div className="mt-3 flex flex-wrap gap-2">
                        <Link
                          href={`/ehr/${attrs.patient_id}/consults?tab=referrals`}
                          className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
                        >
                          Open Consults
                        </Link>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* In-session tools — only visible when session is active */}
            {isActive && attrs.encounter_id && (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Quick Vitals */}
                <div className="bg-white rounded-lg border border-gray-200 p-5">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      <Activity className="w-5 h-5 text-red-500" />
                      <h3 className="font-medium text-gray-900">Reported Vitals</h3>
                    </div>
                    {vitalsSaved && (
                      <span className="text-xs text-green-600 flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> Saved
                      </span>
                    )}
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Systolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={systolic}
                        onChange={(e) => setSystolic(e.target.value)}
                        placeholder="120"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Diastolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={diastolic}
                        onChange={(e) => setDiastolic(e.target.value)}
                        placeholder="80"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Heart Rate (bpm)
                      </label>
                      <input
                        type="number"
                        value={heartRate}
                        onChange={(e) => setHeartRate(e.target.value)}
                        placeholder="72"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Temp (C)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={temperature}
                        onChange={(e) => setTemperature(e.target.value)}
                        placeholder="36.5"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                  <button
                    onClick={handleSaveVitals}
                    disabled={vitalsSaving}
                    className="mt-4 w-full py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                  >
                    {vitalsSaving ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                      </>
                    ) : (
                      <>
                        <Save className="w-4 h-4" /> Save Vitals
                      </>
                    )}
                  </button>
                </div>

                {/* Consultation Note */}
                <div className="bg-white rounded-lg border border-gray-200 p-5">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      <FileText className="w-5 h-5 text-indigo-500" />
                      <h3 className="font-medium text-gray-900">Consultation Note</h3>
                    </div>
                    {noteSaved && (
                      <span className="text-xs text-green-600 flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> Saved
                      </span>
                    )}
                  </div>
                  <textarea
                    value={noteBody}
                    onChange={(e) => setNoteBody(e.target.value)}
                    rows={6}
                    placeholder="Document the teleconsultation findings, assessment, and plan..."
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                  />
                  <button
                    onClick={handleSaveNote}
                    disabled={noteSaving}
                    className="mt-4 w-full py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                  >
                    {noteSaving ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                      </>
                    ) : (
                      <>
                        <Save className="w-4 h-4" /> Save Note
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* Completion Note — Findings, Assessment, Plan */}
            {isActive && session?.attributes.encounter_id && (
              <div className="bg-white rounded-lg border border-indigo-200 p-5">
                <div className="flex items-center justify-between gap-3 mb-3">
                  <div className="flex items-center gap-2">
                    <FileText className="w-5 h-5 text-indigo-600" />
                    <h3 className="text-sm font-semibold text-gray-900">Consultation Completion Note</h3>
                  </div>
                  {completionSaved && (
                    <span className="text-xs text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" />
                      {attrs.referral_id
                        ? COORDINATION_COPY.noteAndReturnedResponseSaved
                        : COORDINATION_COPY.completionNoteSaved}
                    </span>
                  )}
                </div>
                <p className="text-xs text-gray-500 mb-3">
                  Document your findings, assessment, and recommended plan for the referring provider.
                </p>
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Findings</label>
                    <textarea
                      rows={2}
                      value={completionFindings}
                      onChange={(e) => setCompletionFindings(e.target.value)}
                      placeholder="Key findings from this consultation..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Assessment & Plan</label>
                    <textarea
                      rows={2}
                      value={completionPlan}
                      onChange={(e) => setCompletionPlan(e.target.value)}
                      placeholder="Clinical assessment and recommended management plan..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Follow-up Required</label>
                    <select
                      value={completionFollowUp}
                      onChange={(e) => setCompletionFollowUp(e.target.value as FollowUpOption)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    >
                      <option value="NONE">No follow-up needed</option>
                      <option value="REVIEW">Review in clinic</option>
                      <option value="REPEAT_TELECONSULT">Repeat teleconsult</option>
                      <option value="ADMIT">Recommend admission</option>
                      <option value="REFER_FURTHER">Refer to another specialist</option>
                    </select>
                  </div>
                  {attrs.referral_id && (
                    <p className="rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-800">
                      Saving this note will also send the specialist response back through the linked referral.
                    </p>
                  )}
                  {completionError && (
                    <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">
                      {completionError}
                    </p>
                  )}
                  <button
                    onClick={handleSaveCompletionNote}
                    disabled={
                      completionSaving ||
                      (!completionFindings.trim() && !completionPlan.trim())
                    }
                    className="w-full py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                  >
                    {completionSaving ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        Saving completion note...
                      </>
                    ) : (
                      <>
                        <Save className="w-4 h-4" />
                        Save Completion Note
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* End Session */}
            {isActive && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                {!showEndConfirm ? (
                  <div className="space-y-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Session Summary Notes
                      </label>
                      <textarea
                        value={sessionNotes}
                        onChange={(e) => setSessionNotes(e.target.value)}
                        rows={3}
                        placeholder="Summary of the teleconsultation session..."
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                      />
                    </div>
                    <button
                      onClick={() => setShowEndConfirm(true)}
                      className="w-full py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 transition-colors flex items-center justify-center gap-2"
                    >
                      <PhoneOff className="w-4 h-4" />
                      End Session
                    </button>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <p className="text-sm text-gray-700 text-center">
                      End this telemedicine session? This will record the duration and notes.
                    </p>
                    {attrs.referral_id && !completionSaved && (
                      <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
                        The linked referral handoff has not been saved from this session yet. You can still end the session, but the referral response will remain incomplete until the completion note is recorded.
                      </p>
                    )}
                    <div className="flex gap-3">
                      <button
                        onClick={() => setShowEndConfirm(false)}
                        className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={handleEnd}
                        disabled={endSession.isPending}
                        className="flex-1 py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                      >
                        {endSession.isPending ? (
                          <>
                            <Loader2 className="w-4 h-4 animate-spin" /> Ending...
                          </>
                        ) : (
                          <>
                            <CheckCircle2 className="w-4 h-4" /> Confirm End
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
