"use client";

/**
 * Telemedicine Hub - Referral-aware scheduling and session orchestration.
 * Route: /telemedicine | pageTitle: "Telemedicine"
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowUpRight,
  Calendar,
  CheckCircle2,
  Clock,
  Loader2,
  Phone,
  Plus,
  User,
  Video,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { usePatients, type PatientResource } from "@/hooks/queries/usePatients";
import {
  DEMO_PATIENT_ID,
  DEMO_PATIENT_LABEL,
  resolveTelemedicinePatientId,
} from "@/lib/webrtc/dev-test-facility";
import {
  useCreateTelemedicineSession,
  useJoinTelemedicineSession,
  useTelemedicineSessions,
  type TelemedicineSession,
} from "@/hooks/queries/useTelemedicine";

const STATUS_STYLES: Record<string, { label: string; className: string }> = {
  SCHEDULED: { label: "Scheduled", className: "bg-impilo-100 text-impilo-600" },
  IN_PROGRESS: { label: "In Progress", className: "bg-green-100 text-green-700" },
  COMPLETED: { label: "Completed", className: "bg-gray-100 text-gray-600" },
  CANCELLED: { label: "Cancelled", className: "bg-red-100 text-red-600" },
};

type TabFilter = "all" | "SCHEDULED" | "IN_PROGRESS" | "COMPLETED";

interface ComposerState {
  patientId: string;
  referralId: string;
  sessionType: string;
  scheduledAt: string;
  notes: string;
}

function toLocalDateTimeValue(date: Date) {
  const offset = date.getTimezoneOffset();
  const adjusted = new Date(date.getTime() - offset * 60_000);
  return adjusted.toISOString().slice(0, 16);
}

function extractMutationError(error: unknown): string {
  if (error && typeof error === "object") {
    const err = error as { error?: { message?: string }; status?: number; message?: string };
    if (err.error?.message) return err.error.message;
    if (err.status === 403) {
      return "Access denied. Confirm you are signed in with a clinical role and have selected a facility.";
    }
    if (err.message) return err.message;
  }
  return "The session could not be created. Confirm the workplace and patient context, then try again.";
}

function createDefaultComposer(): ComposerState {
  return {
    patientId: DEMO_PATIENT_ID,
    referralId: "",
    sessionType: "VIDEO",
    scheduledAt: toLocalDateTimeValue(new Date(Date.now() + 60 * 60 * 1000)),
    notes: "",
  };
}

function buildPatientOptions(rows: PatientResource[]) {
  const options: { id: string; label: string }[] = [
    { id: DEMO_PATIENT_ID, label: DEMO_PATIENT_LABEL },
  ];
  const seen = new Set<string>([DEMO_PATIENT_ID]);

  for (const row of rows) {
    const id = resolveTelemedicinePatientId(row.id);
    if (!id || seen.has(id)) continue;
    seen.add(id);
    options.push({
      id,
      label: row.attributes.displayName?.trim() || id,
    });
  }

  return options;
}

export default function TelemedicinePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const [activeTab, setActiveTab] = useState<TabFilter>("all");
  const [showComposer, setShowComposer] = useState(false);
  const [composer, setComposer] = useState<ComposerState>(() => createDefaultComposer());
  const { data: patientsData, isLoading: patientsLoading } = usePatients();
  const patientOptions = useMemo(
    () => buildPatientOptions(patientsData?.data ?? []),
    [patientsData?.data],
  );

  const statusParam = activeTab === "all" ? undefined : activeTab;
  const patientIdFilter = searchParams.get("patientId") ?? "";
  const referralIdFilter = searchParams.get("referralId") ?? "";
  const modeFilter = searchParams.get("mode") ?? "VIDEO";

  const { data, isLoading } = useTelemedicineSessions({
    status: statusParam,
    facilityId: facility?.id,
    patientId: patientIdFilter || undefined,
    referralId: referralIdFilter || undefined,
  });
  const joinSession = useJoinTelemedicineSession();
  const createSession = useCreateTelemedicineSession();

  const sessions: TelemedicineSession[] = data?.data ?? [];
  const incomingSessions = useMemo(
    () => sessions.filter((session) => session.attributes.provider_id !== user?.id),
    [sessions, user?.id],
  );
  const outgoingSessions = useMemo(
    () => sessions.filter((session) => session.attributes.provider_id === user?.id),
    [sessions, user?.id],
  );
  const activeSessions = useMemo(
    () => sessions.filter((session) => session.attributes.status === "IN_PROGRESS"),
    [sessions],
  );
  const isScoped = Boolean(patientIdFilter || referralIdFilter);

  const tabs: { key: TabFilter; label: string }[] = [
    { key: "all", label: "All Sessions" },
    { key: "SCHEDULED", label: "Upcoming" },
    { key: "IN_PROGRESS", label: "Active" },
    { key: "COMPLETED", label: "Completed" },
  ];

  useEffect(() => {
    if (!patientIdFilter && !referralIdFilter) return;

    setShowComposer(true);
    setComposer((current) => ({
      ...current,
      patientId: patientIdFilter,
      referralId: referralIdFilter,
      sessionType: modeFilter,
    }));
  }, [modeFilter, patientIdFilter, referralIdFilter]);

  function updateComposer(field: keyof ComposerState, value: string) {
    setComposer((current) => ({ ...current, [field]: value }));
  }

  function resetComposer() {
    setComposer(createDefaultComposer());
    setShowComposer(false);
  }

  function handleJoin(sessionId: string) {
    joinSession.mutate(
      { id: sessionId },
      {
        onSuccess: () => {
          router.push(`/telemedicine/session/${sessionId}`);
        },
      },
    );
  }

  function handleCreateSession() {
    if (!facility || !composer.patientId.trim()) return;

    const providerId =
      user?.linkedIds?.providerId ?? user?.providerId ?? user?.id;

    createSession.mutate(
      {
        patient_id: resolveTelemedicinePatientId(composer.patientId.trim()),
        provider_id: providerId,
        provider_user_id: user?.id,
        facility_id: facility.id,
        referral_id: composer.referralId.trim() || undefined,
        session_type: composer.sessionType,
        scheduled_at: composer.scheduledAt ? new Date(composer.scheduledAt).toISOString() : undefined,
        notes: composer.notes.trim() || undefined,
      },
      {
        onSuccess: (response) => {
          resetComposer();
          createSession.reset();
          router.push(`/telemedicine/session/${response.data.id}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Telemedicine Hub"
        subtitle={
          facility
            ? `Coordinate teleconsults for ${facility.name}`
            : "Select a facility to start or join telemedicine sessions"
        }
      >
        {!facility ? (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-8 text-center">
            <AlertCircle className="mx-auto mb-3 h-10 w-10 text-amber-600" />
            <h2 className="text-lg font-semibold text-gray-900">Facility context is required</h2>
            <p className="mt-2 text-sm text-gray-600">
              Select a workplace first so teleconsult routing and downstream headers stay aligned.
            </p>
            <Link
              href="/facility"
              className="mt-4 inline-flex items-center gap-2 rounded-lg bg-gray-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-gray-800"
            >
              Select Facility
            </Link>
          </div>
        ) : (
          <>
            <div className="mb-6 grid gap-4 lg:grid-cols-3">
              <div className="rounded-2xl border border-impilo-200 bg-impilo-50 p-5">
                <div className="mb-3 flex items-center gap-2">
                  <ArrowDownLeft className="h-5 w-5 text-impilo-500" />
                  <h3 className="text-sm font-semibold text-impilo-800">Incoming Consults</h3>
                </div>
                <p className="text-3xl font-semibold text-gray-900">{incomingSessions.length}</p>
                <p className="mt-1 text-sm text-impilo-700">
                  Sessions initiated by another clinician and waiting on your team.
                </p>
                <Link
                  href="/queue/incoming-referrals"
                  className="mt-4 inline-flex text-sm font-medium text-impilo-600 transition-colors hover:text-impilo-800"
                >
                  Review incoming referrals
                </Link>
              </div>

              <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
                <div className="mb-3 flex items-center gap-2">
                  <ArrowUpRight className="h-5 w-5 text-emerald-600" />
                  <h3 className="text-sm font-semibold text-emerald-900">Outgoing Consults</h3>
                </div>
                <p className="text-3xl font-semibold text-gray-900">{outgoingSessions.length}</p>
                <p className="mt-1 text-sm text-emerald-800">
                  Sessions being coordinated from your currently selected workplace.
                </p>
                <button
                  type="button"
                  onClick={() => setShowComposer((current) => !current)}
                  className="mt-4 inline-flex items-center gap-2 text-sm font-medium text-emerald-700 transition-colors hover:text-emerald-900"
                >
                  <Plus className="h-4 w-4" />
                  Schedule a teleconsult
                </button>
              </div>

              <div className="rounded-2xl border border-gray-200 bg-white p-5">
                <div className="mb-3 flex items-center gap-2">
                  <CheckCircle2 className="h-5 w-5 text-gray-700" />
                  <h3 className="text-sm font-semibold text-gray-900">Operational Context</h3>
                </div>
                <p className="text-3xl font-semibold text-gray-900">{activeSessions.length}</p>
                <p className="mt-1 text-sm text-gray-600">
                  Active sessions in the current facility context right now.
                </p>
                {isScoped ? (
                  <p className="mt-4 rounded-lg bg-gray-50 px-3 py-2 text-xs text-gray-600">
                    Scoped to
                    {patientIdFilter ? ` patient ${patientIdFilter.slice(0, 8)}...` : ""}
                    {patientIdFilter && referralIdFilter ? " and" : ""}
                    {referralIdFilter ? ` referral ${referralIdFilter.slice(0, 8)}...` : ""}.
                  </p>
                ) : (
                  <Link
                    href="/queue"
                    className="mt-4 inline-flex text-sm font-medium text-gray-700 transition-colors hover:text-gray-900"
                  >
                    Open the broader work queue
                  </Link>
                )}
              </div>
            </div>

            <div className="mb-6 rounded-2xl border border-gray-200 bg-white p-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">Schedule Teleconsult</h3>
                  <p className="mt-1 text-sm text-gray-600">
                    Start from a patient, a referral, or a direct provider handoff without leaving
                    the workplace context.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowComposer((current) => !current)}
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50"
                >
                  {showComposer ? "Hide" : "Open"}
                </button>
              </div>

              {showComposer && (
                <div className="mt-4 space-y-4">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-gray-600">Patient</span>
                      <select
                        value={composer.patientId}
                        onChange={(event) => updateComposer("patientId", event.target.value)}
                        disabled={patientsLoading}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50"
                      >
                        {patientOptions.map((option) => (
                          <option key={option.id} value={option.id}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    </label>

                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-gray-600">Referral ID</span>
                      <input
                        type="text"
                        value={composer.referralId}
                        onChange={(event) => updateComposer("referralId", event.target.value)}
                        placeholder="Optional referral link"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      />
                    </label>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-gray-600">Session Type</span>
                      <select
                        value={composer.sessionType}
                        onChange={(event) => updateComposer("sessionType", event.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      >
                        <option value="VIDEO">Video</option>
                        <option value="AUDIO">Audio</option>
                        <option value="CHAT">Chat</option>
                        <option value="ASYNC_REVIEW">Async Review</option>
                        <option value="CASE_REVIEW">Case Review</option>
                      </select>
                    </label>

                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-gray-600">Scheduled For</span>
                      <input
                        type="datetime-local"
                        value={composer.scheduledAt}
                        onChange={(event) => updateComposer("scheduledAt", event.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      />
                    </label>
                  </div>

                  <label className="block">
                    <span className="mb-1 block text-xs font-medium text-gray-600">Notes</span>
                    <textarea
                      value={composer.notes}
                      onChange={(event) => updateComposer("notes", event.target.value)}
                      rows={4}
                      placeholder="Reason for consult, prep requested, or coordination notes"
                      className="w-full resize-none rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                    />
                  </label>

                  {createSession.isError && (
                    <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
                      {extractMutationError(createSession.error)}
                    </p>
                  )}

                  <div className="flex flex-wrap gap-3">
                    {patientIdFilter && (
                      <Link
                        href={`/ehr/${patientIdFilter}/consults?tab=teleconsults`}
                        className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50"
                      >
                        <User className="h-3.5 w-3.5" />
                        Open patient consults
                      </Link>
                    )}
                    <Link
                      href="/queue/incoming-referrals"
                      className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50"
                    >
                      <ArrowDownLeft className="h-3.5 w-3.5" />
                      Incoming referrals
                    </Link>
                  </div>

                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={resetComposer}
                      className="flex-1 rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-200"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={handleCreateSession}
                      disabled={createSession.isPending || !composer.patientId.trim()}
                      className="flex-1 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {createSession.isPending ? "Scheduling..." : "Create Session"}
                    </button>
                  </div>
                </div>
              )}
            </div>

            <div className="mb-6 flex gap-1 border-b border-gray-200">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setActiveTab(tab.key)}
                  className={`border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
                    activeTab === tab.key
                      ? "border-impilo-500 text-impilo-500"
                      : "border-transparent text-gray-500 hover:text-gray-700"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
            {isLoading ? (
              <div className="flex items-center justify-center rounded-2xl border border-gray-200 bg-white py-16">
                <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
                <span className="ml-2 text-sm text-gray-500">Loading sessions...</span>
              </div>
            ) : sessions.length === 0 ? (
              <div className="rounded-2xl border border-gray-200 bg-white p-12 text-center">
                <Video className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-500">
                  {isScoped
                    ? "No teleconsult sessions match this referral context yet."
                    : "No telemedicine sessions found for the selected facility."}
                </p>
                <button
                  type="button"
                  onClick={() => setShowComposer(true)}
                  className="mt-4 inline-flex items-center gap-2 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600"
                >
                  <Plus className="h-4 w-4" />
                  Schedule first session
                </button>
              </div>
            ) : (
              <div className="space-y-4">
                {sessions.map((session) => {
                  const attrs = session.attributes;
                  const status = STATUS_STYLES[attrs.status] ?? {
                    label: attrs.status,
                    className: "bg-gray-100 text-gray-600",
                  };
                  const isJoinable = attrs.status === "SCHEDULED" || attrs.status === "IN_PROGRESS";
                  const isOutgoing = attrs.provider_id === user?.id;

                  return (
                    <div
                      key={session.id}
                      className="rounded-2xl border border-gray-200 bg-white p-5 transition-colors hover:border-gray-300"
                    >
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div className="min-w-0 flex-1">
                          <div className="mb-3 flex items-start gap-3">
                            <div
                              className={`flex h-11 w-11 items-center justify-center rounded-full ${
                                isOutgoing ? "bg-emerald-50" : "bg-impilo-50"
                              }`}
                            >
                              {isOutgoing ? (
                                <ArrowUpRight className="h-5 w-5 text-emerald-600" />
                              ) : (
                                <ArrowDownLeft className="h-5 w-5 text-impilo-500" />
                              )}
                            </div>
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <h3 className="text-sm font-semibold text-gray-900">
                                  {attrs.session_type} Teleconsult
                                </h3>
                                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${status.className}`}>
                                  {status.label}
                                </span>
                                <span
                                  className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                                    isOutgoing ? "bg-emerald-100 text-emerald-700" : "bg-impilo-100 text-impilo-600"
                                  }`}
                                >
                                  {isOutgoing ? "Outgoing" : "Incoming"}
                                </span>
                              </div>
                              <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-gray-500">
                                {attrs.patient_id && (
                                  <span className="flex items-center gap-1">
                                    <User className="h-3 w-3" />
                                    Patient {attrs.patient_id.slice(0, 8)}...
                                  </span>
                                )}
                                {attrs.scheduled_at && (
                                  <span className="flex items-center gap-1">
                                    <Calendar className="h-3 w-3" />
                                    {new Date(attrs.scheduled_at).toLocaleString()}
                                  </span>
                                )}
                                {attrs.started_at && (
                                  <span className="flex items-center gap-1">
                                    <Clock className="h-3 w-3" />
                                    Started {new Date(attrs.started_at).toLocaleTimeString()}
                                  </span>
                                )}
                                {attrs.duration_seconds != null && attrs.duration_seconds > 0 && (
                                  <span className="flex items-center gap-1">
                                    <CheckCircle2 className="h-3 w-3" />
                                    {Math.round(attrs.duration_seconds / 60)} min
                                  </span>
                                )}
                              </div>
                            </div>
                          </div>

                          {(attrs.referral_id || attrs.notes) && (
                            <div className="space-y-2 pl-14">
                              {attrs.referral_id && (
                                <p className="text-xs font-medium text-impilo-600">
                                  Referral-linked orchestration is enabled for this session.
                                </p>
                              )}
                              {attrs.notes && <p className="text-sm text-gray-600">{attrs.notes}</p>}
                            </div>
                          )}
                        </div>

                        <div className="flex flex-wrap gap-2">
                          {isJoinable && (
                            <button
                              type="button"
                              onClick={() => handleJoin(session.id)}
                              disabled={joinSession.isPending}
                              className="inline-flex items-center gap-2 rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              <Phone className="h-4 w-4" />
                              {attrs.status === "IN_PROGRESS" ? "Rejoin" : "Join"}
                            </button>
                          )}
                          <Link
                            href={`/telemedicine/session/${session.id}`}
                            className="inline-flex items-center gap-2 rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-200"
                          >
                            <Video className="h-4 w-4" />
                            Details
                          </Link>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}
