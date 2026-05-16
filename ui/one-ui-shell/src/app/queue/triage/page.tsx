"use client";

/**
 * Triage Queue — Patients awaiting triage assessment.
 * Route: /queue/triage
 *
 * Uses the real BFF triage endpoint (POST /internal/v1/queue/entries/{id}/triage)
 * which creates a triage record and updates queue priority from acuity.
 */

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  AlertTriangle,
  ArrowRightLeft,
  CheckCircle2,
  ClipboardCheck,
  Clock,
  Loader2,
  Save,
  Stethoscope,
  UserPlus,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueueWorkspaceHeader } from "@/components/queue/QueueWorkspaceHeader";
import { useCallPatient, useQueueEntries, type QueueEntryResource } from "@/hooks/queries/useQueue";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { COORDINATION_COPY } from "@/lib/consult-workflows";
import {
  formatQueueDateTime,
  formatQueueWaitTime,
  getQueuePatientId,
  getQueuePatientName,
  getQueueQueuedAt,
  getQueueReason,
  getQueueTriageCategory,
  QUEUE_TRIAGE_STYLES,
} from "@/lib/queue-workflows";

const ACUITY_LEVELS = [
  {
    level: 1,
    label: "Red",
    desc: "Resuscitation",
    color: "border-red-400 bg-red-50 text-red-700",
    active: "border-red-500 bg-red-100 ring-2 ring-red-300",
  },
  {
    level: 2,
    label: "Orange",
    desc: "Emergency",
    color: "border-orange-400 bg-orange-50 text-orange-700",
    active: "border-orange-500 bg-orange-100 ring-2 ring-orange-300",
  },
  {
    level: 3,
    label: "Yellow",
    desc: "Urgent",
    color: "border-yellow-400 bg-yellow-50 text-yellow-700",
    active: "border-yellow-500 bg-yellow-100 ring-2 ring-yellow-300",
  },
  {
    level: 4,
    label: "Green",
    desc: "Standard",
    color: "border-green-400 bg-green-50 text-green-700",
    active: "border-green-500 bg-green-100 ring-2 ring-green-300",
  },
  {
    level: 5,
    label: "Blue",
    desc: "Non-urgent",
    color: "border-impilo-400 bg-impilo-50 text-impilo-600",
    active: "border-impilo-400 bg-blue-100 ring-2 ring-impilo-300",
  },
];

const DANGER_SIGNS = [
  "Airway compromise", "Breathing difficulty", "Circulation failure",
  "Altered consciousness", "Severe pain", "Active bleeding",
  "Convulsions", "Dehydration (severe)", "High fever (>39°C)", "Shock",
];

export default function TriageQueuePage() {
  const router = useRouter();
  const facility = useFacilityStore((state) => state.facility);
  const { isQueueManager } = useRoleGroup();
  const { data, isLoading, error } = useQueueEntries({ facilityId: facility?.id, status: "WAITING" });
  const callPatient = useCallPatient();
  const { user } = useAuthStore();
  const queryClient = useQueryClient();

  const [triagingEntry, setTriagingEntry] = useState<QueueEntryResource | null>(null);
  const [acuity, setAcuity] = useState<number | null>(null);
  const [dangerSigns, setDangerSigns] = useState<Record<string, boolean>>({});
  const [notes, setNotes] = useState("");
  const [vSystolic, setVSystolic] = useState("");
  const [vDiastolic, setVDiastolic] = useState("");
  const [vHR, setVHR] = useState("");
  const [vTemp, setVTemp] = useState("");
  const [vSpO2, setVSpO2] = useState("");
  const [vRR, setVRR] = useState("");

  const triageMutation = useMutation({
    mutationFn: (payload: {
      id: string;
      acuity: number;
      danger_signs: Array<{ name: string; present: boolean }>;
      vitals: Record<string, number | null> | null;
      notes: string | null;
      triaged_by: string;
      triaged_by_name: string;
    }) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/queue/entries/${payload.id}/triage`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
      resetComposer();
    },
  });

  const entries = useMemo(() => data?.data ?? [], [data?.data]);
  const awaitingAssessment = useMemo(
    () => entries.filter((entry) => !getQueueTriageCategory(entry)),
    [entries],
  );
  const handoffReady = useMemo(
    () => entries.filter((entry) => Boolean(getQueueTriageCategory(entry))),
    [entries],
  );

  function resetComposer() {
    setTriagingEntry(null);
    setAcuity(null);
    setDangerSigns({});
    setNotes("");
    setVSystolic("");
    setVDiastolic("");
    setVHR("");
    setVTemp("");
    setVSpO2("");
    setVRR("");
  }

  function handleSubmitTriage() {
    if (!triagingEntry || acuity == null) return;
    const activeDangerSigns = Object.entries(dangerSigns).map(([name, present]) => ({ name, present }));
    const toNum = (value: string) => (value.trim() === "" ? null : Number(value));
    const vitals: Record<string, number | null> = {
      systolic: toNum(vSystolic),
      diastolic: toNum(vDiastolic),
      heart_rate: toNum(vHR),
      temperature: toNum(vTemp),
      oxygen_saturation: toNum(vSpO2),
      respiratory_rate: toNum(vRR),
    };
    triageMutation.mutate({
      id: triagingEntry.id,
      acuity,
      danger_signs: activeDangerSigns,
      vitals: Object.values(vitals).some((value) => value !== null) ? vitals : null,
      notes: notes.trim() || null,
      triaged_by: user?.id ?? "system",
      triaged_by_name: user?.displayName ?? user?.email ?? "",
    });
  }

  function handleStartHandoff(entry: QueueEntryResource) {
    const patientId = getQueuePatientId(entry);
    if (!patientId) return;

    callPatient.mutate(
      { id: entry.id },
      {
        onSuccess: () => {
          router.push(`/ehr/${patientId}?entry=queue`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Triage Queue" subtitle={facility ? `${facility.name}` : "Assess and prioritize patients"}>
        <div className="space-y-6">
          <QueueWorkspaceHeader
            badge="Queue triage"
            badgeIcon={Stethoscope}
            title="Assess acuity, document red flags, then hand the encounter into chart"
            description="This workspace keeps facility context, urgency, and the next queue-to-chart action visible in one place."
            facilityName={facility?.name}
            actions={[
              { href: "/queue", label: "Queue Workboard", icon: ArrowRightLeft },
              { href: "/queue/waiting", label: "Waiting Room", icon: ClipboardCheck, tone: "secondary" },
              ...(isQueueManager
                ? [{ href: "/queue/walk-in", label: "Walk-in Registration", icon: UserPlus, tone: "secondary" as const }]
                : []),
            ]}
            metrics={[
              {
                label: "Awaiting triage",
                value: String(awaitingAssessment.length),
                detail: "Patients who still need an acuity decision before service handoff.",
              },
              {
                label: "Ready for handoff",
                value: String(handoffReady.length),
                detail: "Patients already triaged and ready to enter the encounter workflow.",
              },
            ]}
          />

          {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
          </div>
        ) : error ? (
          <div className="rounded-3xl border border-red-200 bg-red-50 p-6 text-center">
            <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
            <p className="text-sm text-red-700">Failed to load the triage queue.</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Triage Form — when assessing a patient */}
            {triagingEntry && (
              <div className="rounded-3xl border-2 border-amber-300 bg-white p-5 shadow-sm">
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Stethoscope className="w-5 h-5 text-amber-600" />
                    <h3 className="font-medium text-gray-900">
                      Triage: {getQueuePatientName(triagingEntry)}
                    </h3>
                  </div>
                  <button
                    onClick={resetComposer}
                    className="text-xs text-gray-500 hover:text-gray-700"
                  >
                    Cancel
                  </button>
                </div>

                {/* Acuity */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Triage Category</label>
                  <div className="grid grid-cols-5 gap-2">
                    {ACUITY_LEVELS.map((t) => (
                      <button
                        key={t.level}
                        onClick={() => setAcuity(t.level)}
                        className={`p-3 rounded-lg border-2 text-center transition-all ${
                          acuity === t.level ? t.active : t.color + " hover:opacity-80"
                        }`}
                      >
                        <div className="text-lg font-bold">P{t.level}</div>
                        <div className="text-xs font-medium">{t.label}</div>
                        <div className="text-[10px] opacity-75">{t.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Danger Signs */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Danger Signs</label>
                  <div className="grid grid-cols-2 gap-2">
                    {DANGER_SIGNS.map((sign) => {
                      const present = dangerSigns[sign] ?? false;
                      return (
                        <button
                          key={sign}
                          onClick={() => setDangerSigns((prev) => ({ ...prev, [sign]: !prev[sign] }))}
                          className={`flex items-center gap-2 p-2 rounded-lg text-left transition-colors text-xs ${
                            present ? "bg-red-50 border border-red-200 text-red-700" : "bg-gray-50 border border-gray-200 text-gray-600 hover:bg-gray-100"
                          }`}
                        >
                          {present ? <AlertTriangle className="w-3.5 h-3.5 text-red-500 shrink-0" /> : <CheckCircle2 className="w-3.5 h-3.5 text-green-500 shrink-0" />}
                          {sign}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Quick Triage Vitals */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Triage Vitals</label>
                  <div className="grid grid-cols-3 gap-2">
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Systolic</label>
                      <input type="number" value={vSystolic} onChange={(e) => setVSystolic(e.target.value)} placeholder="mmHg"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Diastolic</label>
                      <input type="number" value={vDiastolic} onChange={(e) => setVDiastolic(e.target.value)} placeholder="mmHg"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Heart Rate</label>
                      <input type="number" value={vHR} onChange={(e) => setVHR(e.target.value)} placeholder="bpm"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Temperature</label>
                      <input type="number" step="0.1" value={vTemp} onChange={(e) => setVTemp(e.target.value)} placeholder="°C"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">SpO₂</label>
                      <input type="number" value={vSpO2} onChange={(e) => setVSpO2(e.target.value)} placeholder="%"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Resp Rate</label>
                      <input type="number" value={vRR} onChange={(e) => setVRR(e.target.value)} placeholder="/min"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                  </div>
                </div>

                {/* Notes */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
                  <textarea
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    rows={2}
                    placeholder="Clinical observations..."
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-amber-500 resize-none"
                  />
                </div>

                <button
                  onClick={handleSubmitTriage}
                  disabled={triageMutation.isPending || acuity == null}
                  className="w-full py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                >
                  {triageMutation.isPending ? (
                    <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</>
                  ) : (
                    <><Save className="w-4 h-4" /> Complete Triage</>
                  )}
                </button>
              </div>
            )}

            {/* Queue List */}
            {entries.length === 0 ? (
              <div className="rounded-3xl border border-slate-200 bg-white p-12 text-center shadow-sm">
                <Stethoscope className="mx-auto mb-3 h-10 w-10 text-slate-300" />
                <p className="text-sm text-slate-500">No patients waiting for triage at this facility.</p>
              </div>
            ) : (
              <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Arrival</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Wait</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Triage</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((entry) => {
                      const triageCat = getQueueTriageCategory(entry);
                      const queuedAt = getQueueQueuedAt(entry);
                      const patientName = getQueuePatientName(entry);
                      const catColor = triageCat
                        ? QUEUE_TRIAGE_STYLES[triageCat] ?? "bg-slate-200 text-slate-700"
                        : "";

                      return (
                        <tr key={entry.id} className="border-b last:border-b-0 hover:bg-gray-50">
                          <td className="px-4 py-3 font-medium text-gray-900">
                            <div>
                              <p>{patientName}</p>
                              {getQueueReason(entry) ? (
                                <p className="mt-1 text-xs font-normal text-slate-500">{getQueueReason(entry)}</p>
                              ) : null}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-gray-600 text-xs">
                            {formatQueueDateTime(queuedAt, { hour: "2-digit", minute: "2-digit" })}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            <span className="inline-flex items-center gap-1 text-xs">
                              <Clock className="w-3 h-3" />
                              {formatQueueWaitTime(queuedAt)}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            {triageCat ? (
                              <span className={`inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${catColor}`}>
                                {triageCat.charAt(0)}
                              </span>
                            ) : (
                              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                                Needs triage
                              </span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-right">
                            <div className="flex justify-end gap-2">
                              <button
                                onClick={() => setTriagingEntry(entry)}
                                disabled={triagingEntry?.id === entry.id}
                                className="rounded-xl bg-amber-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-amber-700 disabled:opacity-50"
                              >
                                {triageCat ? "Re-triage" : "Start Triage"}
                              </button>
                              {triageCat ? (
                                <button
                                  type="button"
                                  onClick={() => handleStartHandoff(entry)}
                                  disabled={callPatient.isPending}
                                  className="rounded-xl bg-impilo-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-impilo-600 disabled:opacity-50"
                                >
                                  {COORDINATION_COPY.startEncounterHandoff}
                                </button>
                              ) : null}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
