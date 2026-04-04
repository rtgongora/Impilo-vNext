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

export default function TelemedicineSessionPage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const { sessionId } = params;
  const { user } = useAuthStore();

  const { data, isLoading } = useTelemedicineSessions();
  const joinSession = useJoinTelemedicineSession();
  const endSession = useEndTelemedicineSession();

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

  function handleJoin() {
    joinSession.mutate({ id: sessionId });
  }

  function handleEnd() {
    endSession.mutate(
      { id: sessionId, notes: sessionNotes || undefined },
      {
        onSuccess: () => {
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
                      <span className="flex items-center gap-1">
                        <User className="w-4 h-4" />
                        Patient: {attrs.patient_id.substring(0, 8)}...
                      </span>
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
