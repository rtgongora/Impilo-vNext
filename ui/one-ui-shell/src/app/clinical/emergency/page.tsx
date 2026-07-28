"use client";

/**
 * ED / Casualty — Bounded shell on existing BFF emergency + queue surfaces.
 * Route: /clinical/emergency
 *
 * Data: GET/POST /internal/v1/emergency/* (no new persistence). Triage remains on /queue/triage.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  Ambulance,
  ClipboardList,
  Loader2,
  MapPin,
  Stethoscope,
  User,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useActivateEmergency,
  useEmergencyActivations,
  useEndEmergency,
  useLogEmergencyAction,
  type EmergencyActivationRow,
} from "@/hooks/queries/useEmergency";
import { BreakGlassRequestPanel } from "@/components/trust/BreakGlassRequestPanel";
import { EdPreArrivalBoard } from "@/components/clinical/EdPreArrivalBoard";
import { BloodReadinessPanel } from "@/components/clinical/BloodReadinessPanel";
import { PatientSearchField, type PatientHit } from "@/components/clinical/PatientSearchField";
import { CriticalResultsPanel } from "@/components/clinical/CriticalResultsPanel";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useEdVisits, useOpenEdVisit } from "@/hooks/queries/useEdVisit";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const PROTOCOL_OPTIONS = ["CODE_BLUE", "TRAUMA", "RSI", "MATERNITY", "TOXICOLOGY", "OTHER"] as const;

function asString(v: unknown): string {
  return v == null ? "" : String(v);
}

function formatWhen(iso: unknown): string {
  const s = asString(iso);
  if (!s) return "—";
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? s : d.toLocaleString();
}

export default function EmergencyDepartmentPage() {
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const performedBy = user?.id ?? "";
  const performedByName = user?.displayName ?? performedBy;

  // isError is load-bearing here, not decoration. This list IS the ED trackboard: with only
  // `data: edVisits = []`, a failed read rendered "No active ED visits." — telling a coordinator
  // the department is empty. An empty board is the one claim on this screen that stops someone
  // looking, and it is exactly the claim a broken read produced.
  const { data: edVisits = [], isError: edVisitsUnavailable, refetch: refetchEd } = useEdVisits(facility?.id);
  const openEdVisit = useOpenEdVisit();
  const [newPatient, setNewPatient] = useState<PatientHit | null>(null);
  const [newComplaint, setNewComplaint] = useState("");

  const { data, isLoading, error, refetch } = useEmergencyActivations();
  const activate = useActivateEmergency();
  const logAction = useLogEmergencyAction();
  const endEmergency = useEndEmergency();

  const rows = useMemo(
    () => ((data?.data ?? []) as EmergencyActivationRow[]),
    [data?.data],
  );

  const [protocolType, setProtocolType] = useState<string>(PROTOCOL_OPTIONS[0]);
  const [activatePatient, setActivatePatient] = useState<PatientHit | null>(null);
  const [encounterId, setEncounterId] = useState("");
  const [teamLeader, setTeamLeader] = useState("");
  const [location, setLocation] = useState("");

  const [actionFor, setActionFor] = useState<EmergencyActivationRow | null>(null);
  const [actionType, setActionType] = useState("NOTE");
  const [actionDescription, setActionDescription] = useState("");

  const [endFor, setEndFor] = useState<EmergencyActivationRow | null>(null);
  const [endOutcome, setEndOutcome] = useState("STABILISED");
  const [endNotes, setEndNotes] = useState("");

  const activeRows = useMemo(
    () => rows.filter((r) => asString(r.status).toUpperCase() === "ACTIVE"),
    [rows],
  );

  function submitActivate(e: React.FormEvent) {
    e.preventDefault();
    activate.mutate({
      protocolType,
      patientId: activatePatient?.healthId || null,
      encounterId: encounterId.trim() || null,
      teamLeader: teamLeader.trim(),
      location: location.trim(),
    });
  }

  function submitAction(e: React.FormEvent) {
    e.preventDefault();
    if (!actionFor?.id) return;
    logAction.mutate(
      {
        id: actionFor.id,
        actionType,
        description: actionDescription.trim(),
        performedBy: performedByName || performedBy,
      },
      {
        onSuccess: () => {
          setActionFor(null);
          setActionDescription("");
        },
      },
    );
  }

  function submitEnd(e: React.FormEvent) {
    e.preventDefault();
    if (!endFor?.id) return;
    endEmergency.mutate(
      { id: endFor.id, outcome: endOutcome, notes: endNotes.trim() },
      {
        onSuccess: () => {
          setEndFor(null);
          setEndNotes("");
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="ED / Casualty"
        subtitle="ED trackboard, full patient journey, critical event activations"
      >
        <div className="space-y-6">
          <div className="flex justify-end">
            <Link href="/clinical/emergency/board" className="text-sm text-primary underline">
              Emergency episode board →
            </Link>
          </div>

          <CriticalResultsPanel />

          <EdPreArrivalBoard facilityId={facility?.id} />

          <section className="rounded-xl border border-red-100 bg-danger-soft/40 p-5">
            <h2 className="flex items-center gap-2 font-semibold text-red-900">
              <Ambulance className="h-4 w-4" /> ED trackboard
            </h2>
            <p className="mt-1 text-xs text-red-800/80">
              Sovereign ED visits via <code>/internal/v1/ed/*</code> — arrival through triage, trauma, protocols, disposition.
            </p>
            <form
              className="mt-4 flex flex-wrap items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                if (!newPatient?.healthId) return;
                openEdVisit.mutate({
                  facilityId: facility?.id,
                  patientCpid: newPatient.healthId,
                  chiefComplaint: newComplaint.trim(),
                  arrivalMode: "WALK_IN",
                }, {
                  onSuccess: (res) => {
                    const id = String((res as { data?: { visit_id?: string } }).data?.visit_id ?? "");
                    if (id) window.location.href = `/clinical/emergency/${id}`;
                  },
                });
              }}
            >
              <div className="min-w-[220px]">
                <PatientSearchField label="Patient" value={newPatient} onSelect={setNewPatient} />
              </div>
              <input
                value={newComplaint}
                onChange={(e) => setNewComplaint(e.target.value)}
                placeholder="Chief complaint"
                className="min-w-[200px] flex-1 rounded border px-2 py-1 text-sm"
              />
              <button type="submit" disabled={openEdVisit.isPending || !newPatient?.healthId} className="rounded-lg bg-red-600 px-3 py-1.5 text-sm text-white disabled:opacity-50">
                Register ED arrival
              </button>
            </form>
            <ul className="mt-4 space-y-2 text-sm">
              {edVisitsUnavailable ? (
                <li className="font-medium text-red-700" data-testid="ed-visits-unavailable">
                  ED trackboard unavailable — this is not an empty department. Patients may be
                  waiting who are not shown. Retry, or work from the paper board.
                </li>
              ) : edVisits.length === 0 ? (
                <li className="text-muted-foreground">No active ED visits.</li>
              ) : edVisits.map((v) => (
                <li key={String(v.visit_id)} className="flex items-center justify-between rounded border bg-card px-3 py-2">
                  <span>
                    <strong>{String(v.patient_cpid)}</strong> — {String(v.chief_complaint ?? "—")}
                    <span className="ml-2 text-xs text-muted-foreground">{String(v.zone ?? "")} acuity {String(v.current_acuity ?? "—")}</span>
                  </span>
                  <Link href={`/clinical/emergency/${String(v.visit_id)}`} className="text-primary underline text-xs">
                    Open journey →
                  </Link>
                </li>
              ))}
            </ul>
            <button type="button" onClick={() => void refetchEd()} className="mt-2 text-xs text-muted-foreground underline">
              Refresh trackboard
            </button>
          </section>

          <div className="flex flex-wrap gap-3">
            <Link
              href="/queue/triage"
              className="inline-flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:border-primary/25"
            >
              <Stethoscope className="h-4 w-4 text-primary" />
              Triage queue
            </Link>
            <Link
              href="/queue"
              className="inline-flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:border-primary/25"
            >
              <Users className="h-4 w-4 text-orange-600" />
              Patient queue
            </Link>
            <Link
              href="/queue/search"
              className="inline-flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:border-primary/25"
            >
              <ClipboardList className="h-4 w-4 text-muted-foreground" />
              Patient search
            </Link>
          </div>

          <BloodReadinessPanel />

          <BreakGlassRequestPanel
            resourceType="EMERGENCY_DEPARTMENT"
            title="ED break-glass override"
            description="Use when an emergency protocol requires immediate access outside normal policy. Supervisor review is mandatory."
          />

          {error && (
            <div className="flex items-start gap-2 rounded-xl border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">Could not load activations</p>
                <p className="mt-1 text-xs">Check BFF availability and tenant context, then retry.</p>
                <button
                  type="button"
                  onClick={() => void refetch()}
                  className="mt-2 text-xs font-medium text-warning-foreground underline"
                >
                  Retry
                </button>
              </div>
            </div>
          )}

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-base font-semibold text-foreground">
              <Activity className="h-4 w-4 text-red-600" />
              Start protocol activation
            </h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Creates a row via <code className="rounded bg-neutral-100 px-1">POST /internal/v1/emergency/activate</code>. Patient and encounter UUIDs are optional if the event is not yet charted.
            </p>
            <form onSubmit={submitActivate} className="mt-4 grid gap-3 md:grid-cols-2">
              <label className="text-xs font-medium text-muted-foreground md:col-span-2">
                Protocol type
                <select
                  value={protocolType}
                  onChange={(e) => setProtocolType(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                >
                  {PROTOCOL_OPTIONS.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <div>
                <PatientSearchField label="Patient (optional)" value={activatePatient} onSelect={setActivatePatient} />
              </div>
              <label className="text-xs font-medium text-muted-foreground">
                Encounter ID (UUID, optional)
                <input
                  value={encounterId}
                  onChange={(e) => setEncounterId(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm font-mono"
                />
              </label>
              <label className="text-xs font-medium text-muted-foreground">
                Team leader
                <input
                  value={teamLeader}
                  onChange={(e) => setTeamLeader(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
              <label className="text-xs font-medium text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <MapPin className="h-3 w-3" aria-hidden />
                  Location
                </span>
                <input
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  placeholder="ED bay / resus"
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
              <div className="md:col-span-2 flex items-center gap-3">
                <button
                  type="submit"
                  disabled={activate.isPending}
                  className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                >
                  {activate.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  Activate
                </button>
                {activate.isError && (
                  <span className="text-xs text-red-600">Activation failed — check UUIDs and BFF logs.</span>
                )}
              </div>
            </form>
          </section>

          <section className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
            <div className="border-b border-border bg-background px-5 py-3">
              <h2 className="text-base font-semibold text-foreground">Recent activations (tenant)</h2>
              <p className="text-xs text-muted-foreground">Last 20 from BFF. {activeRows.length} active in this view.</p>
            </div>
            {isLoading ? (
              <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" />
                Loading…
              </div>
            ) : rows.length === 0 ? (
              <div className="px-5 py-12 text-center text-sm text-muted-foreground">
                No activations returned for this tenant. When a code or trauma protocol starts, it will appear here.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-left text-sm">
                  <thead className="border-b border-border bg-card text-xs uppercase tracking-wide text-muted-foreground">
                    <tr>
                      <th className="px-4 py-2">When</th>
                      <th className="px-4 py-2">Protocol</th>
                      <th className="px-4 py-2">Status</th>
                      <th className="px-4 py-2">Location</th>
                      <th className="px-4 py-2">Patient</th>
                      <th className="px-4 py-2">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {rows.map((row, idx) => {
                      const id = asString(row.id);
                      const pid = row.patient_id ? asString(row.patient_id) : "";
                      const eid = row.encounter_id ? asString(row.encounter_id) : "";
                      const active = asString(row.status).toUpperCase() === "ACTIVE";
                      return (
                        <tr key={id || `activation-${idx}`} className="hover:bg-background">
                          <td className="whitespace-nowrap px-4 py-2 text-foreground">{formatWhen(row.activation_time)}</td>
                          <td className="px-4 py-2 font-medium text-foreground">{asString(row.protocol_type)}</td>
                          <td className="px-4 py-2">
                            <span
                              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                                active ? "bg-red-100 text-red-800" : "bg-neutral-100 text-foreground"
                              }`}
                            >
                              {asString(row.status) || "—"}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-muted-foreground">{asString(row.location) || "—"}</td>
                          <td className="px-4 py-2">
                            {pid ? (
                              <div className="flex flex-col gap-1">
                                <Link
                                  href={`/ehr/${pid}/summary`}
                                  className="inline-flex items-center gap-1 font-medium text-primary hover:underline"
                                >
                                  <User className="h-3.5 w-3.5" aria-hidden />
                                  Chart
                                </Link>
                                {eid ? (
                                  <Link
                                    href={`/ehr/${pid}/encounter/${eid}`}
                                    className="text-xs font-medium text-muted-foreground hover:underline"
                                  >
                                    Encounter
                                  </Link>
                                ) : null}
                                <Link
                                  href={`/ehr/${pid}/emergency`}
                                  className="text-xs font-medium text-danger hover:underline"
                                >
                                  ED workspace
                                </Link>
                              </div>
                            ) : (
                              <span className="text-muted-foreground">—</span>
                            )}
                          </td>
                          <td className="space-x-2 whitespace-nowrap px-4 py-2">
                            {active && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => setActionFor(row)}
                                  className="text-xs font-medium text-primary hover:underline"
                                >
                                  Log action
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setEndFor(row)}
                                  className="text-xs font-medium text-warning-foreground hover:underline"
                                >
                                  End
                                </button>
                              </>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>

        {actionFor && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
            <div className="w-full max-w-md rounded-xl bg-card p-5 shadow-xl">
              <h3 className="text-lg font-semibold text-foreground">Log emergency action</h3>
              <p className="mt-1 text-xs text-muted-foreground">Posts to BFF; no read-back list in this shell.</p>
              <form onSubmit={submitAction} className="mt-4 space-y-3">
                <label className="block text-xs font-medium text-muted-foreground">
                  Action type
                  <input
                    value={actionType}
                    onChange={(e) => setActionType(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </label>
                <label className="block text-xs font-medium text-muted-foreground">
                  Description
                  <textarea
                    required
                    rows={3}
                    value={actionDescription}
                    onChange={(e) => setActionDescription(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </label>
                <div className="flex justify-end gap-2 pt-2">
                  <button type="button" className="rounded-lg px-3 py-2 text-sm text-muted-foreground" onClick={() => setActionFor(null)}>
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={logAction.isPending}
                    className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {logAction.isPending ? "Saving…" : "Submit"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {endFor && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
            <div className="w-full max-w-md rounded-xl bg-card p-5 shadow-xl">
              <h3 className="text-lg font-semibold text-foreground">End activation</h3>
              <form onSubmit={submitEnd} className="mt-4 space-y-3">
                <label className="block text-xs font-medium text-muted-foreground">
                  Outcome
                  <select
                    value={endOutcome}
                    onChange={(e) => setEndOutcome(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                  >
                    <option value="STABILISED">STABILISED</option>
                    <option value="TRANSFERRED">TRANSFERRED</option>
                    <option value="DECEASED">DECEASED</option>
                    <option value="LEFT_AMA">LEFT_AMA</option>
                  </select>
                </label>
                <label className="block text-xs font-medium text-muted-foreground">
                  Notes
                  <textarea rows={3} value={endNotes} onChange={(e) => setEndNotes(e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" />
                </label>
                <div className="flex justify-end gap-2 pt-2">
                  <button type="button" className="rounded-lg px-3 py-2 text-sm text-muted-foreground" onClick={() => setEndFor(null)}>
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={endEmergency.isPending}
                    className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {endEmergency.isPending ? "Ending…" : "End protocol"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
