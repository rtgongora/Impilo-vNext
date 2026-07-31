"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { EdTriageDiscriminatorPanel } from "@/components/clinical/EdTriageDiscriminatorPanel";
import { TriageOfRecordPanel, type TriageAssessment } from "@/components/clinical/TriageOfRecordPanel";
import { Icd11SearchField, type Icd11Hit } from "@/components/clinical/Icd11SearchField";
import { ResuscitationWorkspace } from "@/components/clinical/ResuscitationWorkspace";
import { TraumaTeamPanel } from "@/components/clinical/TraumaTeamPanel";
import { TraumaSurveyPanel } from "@/components/clinical/TraumaSurveyPanel";
import { useEdVisit, useEdVisitActions } from "@/hooks/queries/useEdVisit";
import { useActivateEmergency, useEmergencyActivations } from "@/hooks/queries/useEmergency";
import {
  isBrowserOffline,
  NOT_TRIAGEABLE_OFFLINE,
  NOT_TRIAGEABLE_OFFLINE_MESSAGE,
} from "@/lib/offline";
import { OfflineIittPreviewPanel } from "@/features/emergency/triage/OfflineIittPreviewPanel";
import { EdZoneAndDiagnosticsPanel } from "@/features/emergency/ed/EdZoneAndDiagnosticsPanel";
import { BloodReadinessPanel } from "@/components/clinical/BloodReadinessPanel";
import { StalenessBadge } from "shared-ui";
import { stalenessFromTransport } from "@/lib/offline/staleness";
import { useAuthStore } from "@/hooks/useAuthStore";

const STEPS = ["Arrival", "Triage", "Treatment", "Trauma", "Resus", "Protocol", "Disposition"] as const;
const ACUITY = [
  { level: 1, label: "Red", desc: "Resuscitation" },
  { level: 2, label: "Orange", desc: "Emergency" },
  { level: 3, label: "Yellow", desc: "Urgent" },
  { level: 4, label: "Green", desc: "Standard" },
  { level: 5, label: "Blue", desc: "Non-urgent" },
];

export default function EdVisitPage({ params }: { params: { visitId: string } }) {
  const { data: visit, isLoading } = useEdVisit(params.visitId);
  const actions = useEdVisitActions(params.visitId);
  const { user } = useAuthStore();
  const actor = user?.providerId ?? user?.staffId ?? user?.id ?? "";
  const emergencyActivations = useEmergencyActivations();
  const activateResus = useActivateEmergency();
  const [resusActivationId, setResusActivationId] = useState<string | undefined>();
  const [step, setStep] = useState(1);
  const [acuity, setAcuity] = useState(3);
  const [triageSystem, setTriageSystem] = useState<"ESI" | "MTS" | "IMPILO_5">("ESI");
  const [complaint, setComplaint] = useState("");
  const [pain, setPain] = useState("5");
  const [discriminators, setDiscriminators] = useState<Record<string, boolean | number>>({ resources_needed: 1 });
  const [dx, setDx] = useState<Icd11Hit | null>(null);
  const [disposition, setDisposition] = useState("DISCHARGE");
  const [traumaLevel, setTraumaLevel] = useState(2);
  const [mechanism, setMechanism] = useState("BLUNT");
  const [teamLeader, setTeamLeader] = useState("");
  const [pathwayRedFlags, setPathwayRedFlags] = useState(false);
  const [pathwayNotes, setPathwayNotes] = useState("");
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    const sync = () => setOffline(isBrowserOffline());
    sync();
    window.addEventListener("online", sync);
    window.addEventListener("offline", sync);
    return () => {
      window.removeEventListener("online", sync);
      window.removeEventListener("offline", sync);
    };
  }, []);

  const handleRecommendedAcuity = useCallback((level: number) => setAcuity(level), []);
  const handleDiscriminatorChange = useCallback((id: string, value: boolean | number) => {
    setDiscriminators((prev) => ({ ...prev, [id]: value }));
  }, []);

  const suggestions = useMemo(
    () => (visit?.protocol_suggestions as Array<Record<string, unknown>>) ?? [],
    [visit?.protocol_suggestions],
  );

  const bloodOrderId = useMemo(() => {
    const fromVisit = visit?.blood_order_id ?? visit?.madi_order_id ?? visit?.blood_order_ref;
    if (fromVisit) return String(fromVisit);
    const orders = (visit?.diagnostic_orders as Array<Record<string, unknown>> | undefined) ?? [];
    const bloodish = orders.find((o) =>
      /BLOOD|XM|CROSS|T_AND_S|TYPE/i.test(String(o.test_code ?? o.order_type ?? "")),
    );
    return bloodish?.oros_order_id ? String(bloodish.oros_order_id) : undefined;
  }, [visit]);

  // The triage of record is the newest assessment; pct returns triage_assessments newest-first.
  const latestTriage = useMemo(
    () => ((visit?.triage_assessments as TriageAssessment[] | undefined) ?? [])[0] ?? null,
    [visit?.triage_assessments],
  );

  // A 422 from triage means NOT_TRIAGEABLE / incomplete — surface the message, do not treat it as
  // a generic failure or an outage (the BFF now passes the pct 422 through instead of a 502).
  // Offline Tier B uses the same channel with code NOT_TRIAGEABLE_OFFLINE — never a fabricated acuity.
  const triageError = actions.triage.error as {
    status?: number;
    error?: { code?: string; message?: string };
  } | null;
  const triageErrorMessage = offline
    ? NOT_TRIAGEABLE_OFFLINE_MESSAGE
    : triageError?.status === 422
      ? triageError.error?.message ??
        "Assessment incomplete — this is not triageable yet. Complete the assessment and repeat."
      : triageError
        ? "Triage could not be recorded. Try again or check connectivity."
        : null;
  const triageErrorCode = offline
    ? NOT_TRIAGEABLE_OFFLINE
    : triageError?.error?.code ?? (triageError?.status === 422 ? "NOT_TRIAGEABLE" : null);

  const visitStaleness = stalenessFromTransport({
    asOf: typeof visit?.updated_at === "string" ? visit.updated_at : null,
    thresholdSeconds: 120,
    fromOfflineCache: offline,
    sourceReachable: !offline,
  });

  if (isLoading || !visit) {
    return (
      <AppLayout>
        <PageShell title="ED visit" subtitle="Loading…" />
      </AppLayout>
    );
  }

  const status = String(visit.status ?? "");
  const patientId = String(visit.patient_cpid ?? "");
  const traumaEpisodeId = visit.trauma_episode_id ? String(visit.trauma_episode_id) : undefined;

  // Reconnect an in-flight resuscitation for this patient across reloads: prefer the
  // activation we just started, else the latest non-ended tenant activation for the CPID.
  const activationRows = (emergencyActivations.data?.data ?? []) as Array<Record<string, unknown>>;
  const patientActivation = activationRows.find(
    (r) => String(r.patient_id ?? "") === patientId && String(r.status ?? "").toUpperCase() !== "ENDED",
  );
  const resolvedActivationId = resusActivationId ?? (patientActivation?.id ? String(patientActivation.id) : undefined);

  return (
    <AppLayout>
      <PageShell
        title="ED patient journey"
        subtitle={`${patientId} — ${status} — zone ${String(visit.zone ?? "—")}`}
      >
        <div className="mb-3">
          <StalenessBadge
            state={visitStaleness}
            asOf={typeof visit.updated_at === "string" ? visit.updated_at : null}
            subject="ED visit"
            data-testid="ed-visit-staleness"
          />
        </div>
        <div className="mb-4 flex flex-wrap gap-2 text-xs">
          {STEPS.map((label, i) => (
            <button
              key={label}
              type="button"
              onClick={() => setStep(i)}
              className={`rounded-full px-3 py-1 ${step === i ? "bg-red-600 text-white" : "bg-neutral-100"}`}
            >
              {i + 1}. {label}
            </button>
          ))}
          <Link href="/clinical/emergency" className="ml-auto text-primary underline text-sm">
            ← ED trackboard
          </Link>
        </div>

        {step === 0 && (
          <section className="rounded-xl border p-4 space-y-2 text-sm">
            <p>Arrival: <strong>{String(visit.arrival_mode)}</strong></p>
            <p>Journey: <code>{String(visit.journey_id)}</code></p>
            <p>Chief complaint: {String(visit.chief_complaint ?? "—")}</p>
          </section>
        )}

        {step === 1 && latestTriage && <TriageOfRecordPanel triage={latestTriage} />}

        {step === 1 && status === "REGISTERED" && (
          <section className="rounded-xl border p-4 space-y-4">
            <h2 className="font-semibold">Structured triage</h2>
            <p className="text-xs text-muted-foreground">
              WHO IITT is the triage authority. ESI/MTS are advisory and never set the acuity of record;
              an acuity entered here is recorded as a manual entry.
            </p>
            <textarea
              value={complaint}
              onChange={(e) => setComplaint(e.target.value)}
              placeholder="Chief complaint"
              className="w-full rounded border p-2 text-sm min-h-[60px]"
            />
            <div className="flex flex-wrap gap-2 text-sm">
              {(["ESI", "MTS", "IMPILO_5"] as const).map((sys) => (
                <button
                  key={sys}
                  type="button"
                  onClick={() => setTriageSystem(sys)}
                  className={`rounded-full px-3 py-1 ${triageSystem === sys ? "bg-neutral-900 text-white" : "bg-neutral-100"}`}
                >
                  {sys === "IMPILO_5" ? "Impilo 5-level" : sys}
                </button>
              ))}
            </div>
            <div className="flex flex-wrap gap-2">
              {ACUITY.map((a) => (
                <button
                  key={a.level}
                  type="button"
                  onClick={() => setAcuity(a.level)}
                  className={`rounded-lg border px-3 py-2 text-sm ${acuity === a.level ? "ring-2 ring-red-400" : ""}`}
                >
                  {a.label} — {a.desc}
                </button>
              ))}
            </div>
            <label className="text-sm">Pain 0–10
              <input type="number" min={0} max={10} value={pain} onChange={(e) => setPain(e.target.value)} className="ml-2 w-16 rounded border px-2" />
            </label>
            <EdTriageDiscriminatorPanel
              triageSystem={triageSystem}
              painScore={Number(pain)}
              discriminators={discriminators}
              vitals={{}}
              onDiscriminatorChange={handleDiscriminatorChange}
              onRecommendedAcuity={handleRecommendedAcuity}
            />
            <button
              type="button"
              data-testid="complete-triage"
              disabled={actions.triage.isPending || offline}
              onClick={() => {
                if (offline) return;
                actions.triage.mutate({
                acuity,
                triageSystem,
                autoAcuity: triageSystem !== "IMPILO_5",
                applyDiscriminators: triageSystem !== "IMPILO_5",
                chiefComplaint: complaint || visit.chief_complaint,
                painScore: Number(pain),
                discriminators,
                dangerSigns: acuity <= 2 ? ["High acuity presentation"] : [],
              });
              }}
              className="rounded-lg bg-red-600 px-4 py-2 text-sm text-white disabled:opacity-50"
            >
              Complete triage
            </button>
            {(triageErrorMessage || offline) && (
              <p
                className="rounded-lg border border-amber-500 bg-amber-50 p-3 text-sm text-amber-900"
                role="alert"
                data-testid="triage-error"
                data-error-code={triageErrorCode ?? undefined}
              >
                {offline ? (
                  <>
                    <span data-testid="not-triageable-offline">{NOT_TRIAGEABLE_OFFLINE}</span>
                    {": "}
                    {NOT_TRIAGEABLE_OFFLINE_MESSAGE}{" "}
                    You can still run a WHO IITT advisory preview offline (Tier A); it does not
                    replace the of-record write.
                  </>
                ) : (
                  triageErrorMessage
                )}
              </p>
            )}
            {offline ? <OfflineIittPreviewPanel /> : null}
          </section>
        )}

        {step === 1 && status !== "REGISTERED" && !latestTriage && (
          <section className="rounded-xl border p-4 text-sm text-muted-foreground">
            Triage is captured at registration. This visit is <strong>{status || "—"}</strong>; open the Treatment or Trauma steps to continue care.
          </section>
        )}

        {step === 2 && (
          <section className="space-y-4">
            <div className="rounded-xl border p-4 space-y-3">
              <h2 className="font-semibold">Treatment / encounter</h2>
              <button
                type="button"
                disabled={actions.startEncounter.isPending || status === "IN_TREATMENT"}
                onClick={() => actions.startEncounter.mutate()}
                className="rounded-lg bg-primary px-4 py-2 text-sm text-white"
              >
                {status === "IN_TREATMENT" ? "Encounter active" : "Start ED encounter"}
              </button>
              <button
                type="button"
                disabled={actions.pageTeam.isPending}
                onClick={() =>
                  actions.pageTeam.mutate({
                    pageType: "STAT_CONSULT",
                    message: "Urgent consult to ED",
                    recipientRole: "ON_CALL",
                  })
                }
                className="rounded-lg border px-4 py-2 text-sm"
              >
                Page on-call team
              </button>
            </div>
            <EdZoneAndDiagnosticsPanel
              currentZone={visit?.zone ? String(visit.zone) : null}
              diagnosticOrders={
                Array.isArray(visit?.diagnostic_orders)
                  ? (visit.diagnostic_orders as Array<Record<string, unknown>>)
                  : undefined
              }
              actor={actor}
              isMutating={
                actions.assignZone.isPending ||
                actions.orderDiagnostic.isPending ||
                actions.actOnDiagnostic.isPending ||
                actions.closeDiagnostic.isPending ||
                actions.reconcileCritical.isPending
              }
              onAssignZone={(body) => actions.assignZone.mutateAsync(body)}
              onOrderDiagnostic={(body) => actions.orderDiagnostic.mutateAsync(body)}
              onAct={(body) => actions.actOnDiagnostic.mutateAsync(body)}
              onClose={(body) => actions.closeDiagnostic.mutateAsync(body)}
              onReconcile={(body) => actions.reconcileCritical.mutateAsync(body)}
            />
            <BloodReadinessPanel orderId={bloodOrderId} />
          </section>
        )}

        {step === 3 && (
          <section className="space-y-4">
            <h2 className="font-semibold">Trauma activation &amp; team</h2>
            {!visit.active_trauma_id ? (
              <form
                className="rounded-xl border p-4 space-y-3"
                onSubmit={(e) => {
                  e.preventDefault();
                  actions.activateTrauma.mutate({ traumaLevel, mechanism, teamLeader: teamLeader.trim() || undefined });
                }}
              >
                <p className="text-xs text-muted-foreground">
                  Activation resolves the on-call trauma panel from the facility roster and pages every member.
                </p>
                <div className="flex flex-wrap gap-4">
                  <label className="text-sm">
                    <span className="mb-1 block font-medium">Trauma level</span>
                    <div className="flex gap-2">
                      {[1, 2, 3].map((l) => (
                        <button
                          key={l}
                          type="button"
                          onClick={() => setTraumaLevel(l)}
                          className={`rounded-lg px-3 py-2 text-sm font-medium ${traumaLevel === l ? "bg-orange-600 text-white" : "bg-neutral-100 hover:bg-neutral-200"}`}
                        >
                          Level {l}
                        </button>
                      ))}
                    </div>
                  </label>
                  <label className="text-sm">
                    <span className="mb-1 block font-medium">Mechanism</span>
                    <select value={mechanism} onChange={(e) => setMechanism(e.target.value)} className="rounded-lg border px-3 py-2 text-sm">
                      {["BLUNT", "PENETRATING", "RTA", "FALL", "BURN", "CRUSH", "BLAST", "OTHER"].map((m) => (
                        <option key={m} value={m}>{m}</option>
                      ))}
                    </select>
                  </label>
                  <label className="text-sm">
                    <span className="mb-1 block font-medium">Team leader (optional)</span>
                    <input value={teamLeader} onChange={(e) => setTeamLeader(e.target.value)} placeholder="e.g. EM consultant" className="rounded-lg border px-3 py-2 text-sm" />
                  </label>
                </div>
                <button
                  type="submit"
                  disabled={actions.activateTrauma.isPending}
                  className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                >
                  {actions.activateTrauma.isPending ? "Activating…" : `Activate trauma team (Level ${traumaLevel})`}
                </button>
                {actions.activateTrauma.isError && (
                  <p className="text-xs text-danger">Activation failed — check facility context and retry.</p>
                )}
              </form>
            ) : (
              <>
                <div className="flex flex-wrap items-center gap-2 rounded-lg border border-green-200 bg-success-soft px-3 py-2 text-sm text-primary-hover">
                  <span>Trauma active</span>
                  <code className="font-mono text-xs">{String(visit.active_trauma_id)}</code>
                  <button
                    type="button"
                    onClick={() => actions.endTrauma.mutate({ outcome: "STABILISED" })}
                    disabled={actions.endTrauma.isPending}
                    className="ml-auto rounded-lg border border-red-300 px-3 py-1 text-xs text-danger hover:bg-danger-soft disabled:opacity-50"
                  >
                    End activation
                  </button>
                </div>
                <TraumaTeamPanel traumaId={String(visit.active_trauma_id)} visitId={params.visitId} />
                <TraumaSurveyPanel
                  visitId={params.visitId}
                  traumaId={String(visit.active_trauma_id)}
                  surveys={(visit.trauma_surveys as Array<Record<string, unknown>>) ?? []}
                />
              </>
            )}
          </section>
        )}

        {step === 4 && (
          <section className="space-y-3">
            <h2 className="font-semibold">Resuscitation</h2>
            {resolvedActivationId ? (
              <ResuscitationWorkspace
                activationId={resolvedActivationId}
                traumaEpisodeId={traumaEpisodeId}
                patientLabel={patientId}
                traumaId={visit.active_trauma_id ? String(visit.active_trauma_id) : undefined}
                mechanism={visit.chief_complaint ? String(visit.chief_complaint) : undefined}
                // clinical-logic-guard: labels the acuity pct already decided; the ternary asks whether one exists, it does not derive one
                triageCategory={visit.current_acuity ? `Acuity ${String(visit.current_acuity)}` : undefined}
                location={visit.zone ? String(visit.zone) : "ED"}
                onEnded={() => void emergencyActivations.refetch()}
              />
            ) : (
              <div className="rounded-xl border p-4 space-y-3 text-sm">
                <p className="text-muted-foreground">
                  No active resuscitation for this patient. Start one to open the ABCDE workspace —
                  CPR cycles, medications, and phase timings stream onto the trauma episode.
                </p>
                <button
                  type="button"
                  disabled={activateResus.isPending}
                  onClick={() =>
                    activateResus.mutate(
                      { patientId, protocolType: "TRAUMA", location: String(visit.zone ?? "RESUS") },
                      {
                        onSuccess: (res) => {
                          const id = String((res as { data?: { id?: string } }).data?.id ?? "");
                          if (id) setResusActivationId(id);
                          void emergencyActivations.refetch();
                        },
                      },
                    )
                  }
                  className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                >
                  {activateResus.isPending ? "Starting…" : "Start resuscitation"}
                </button>
                {activateResus.isError && (
                  <p className="text-xs text-danger">Could not start resuscitation — check facility context and retry.</p>
                )}
              </div>
            )}
          </section>
        )}

        {step === 5 && (
          <section className="rounded-xl border p-4 space-y-3">
            <h2 className="font-semibold">Assessment protocol (CKP)</h2>
            {suggestions.length === 0 && (
              <p className="text-sm text-muted-foreground">
                No protocol suggestions yet. Complete triage (or activate trauma) to generate CKP pathway suggestions.
              </p>
            )}
            <ul className="space-y-2 text-sm">
              {suggestions.map((s) => (
                <li key={String(s.protocol_code)} className="flex items-center justify-between rounded border p-2">
                  <span>
                    {String(s.label)}
                    {s.recommended ? " ★" : ""}
                    <span className="block text-xs text-muted-foreground">{String(s.rationale)}</span>
                  </span>
                  <button
                    type="button"
                    onClick={() => actions.bindProtocol.mutate({
                      protocolRef: s.protocol_code,
                      pathwayRef: s.pathway_ref,
                      patientCpid: patientId,
                      encounterId: visit.encounter_id,
                    })}
                    className="rounded bg-primary px-2 py-1 text-xs text-white"
                  >
                    Apply
                  </button>
                </li>
              ))}
            </ul>
            {Boolean(visit.pathway_session_id) && (
              <div className="space-y-2 rounded border p-3">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={pathwayRedFlags}
                    onChange={(e) => setPathwayRedFlags(e.target.checked)}
                    data-testid="pathway-red-flags"
                  />
                  Red flags present
                </label>
                <input
                  className="w-full rounded border px-2 py-1 text-sm"
                  placeholder="Pathway step notes (optional)"
                  value={pathwayNotes}
                  onChange={(e) => setPathwayNotes(e.target.value)}
                  data-testid="pathway-notes"
                />
                <button
                  type="button"
                  onClick={() =>
                    actions.advancePathway.mutate({
                      sessionId: String(visit.pathway_session_id),
                      answers: {
                        red_flags: pathwayRedFlags,
                        notes: pathwayNotes.trim() || undefined,
                      },
                    })
                  }
                  className="rounded-lg border px-4 py-2 text-sm"
                  data-testid="pathway-advance"
                >
                  Advance pathway step
                </button>
              </div>
            )}
          </section>
        )}

        {step === 6 && !visit.disposition && (
          <section className="rounded-xl border p-4 space-y-3">
            <h2 className="font-semibold">Disposition & coding</h2>
            <select value={disposition} onChange={(e) => setDisposition(e.target.value)} className="rounded border px-2 py-1 text-sm">
              {["DISCHARGE", "ADMIT", "TRANSFER", "REFER", "LAMA", "LWBS", "DEATH"].map((d) => (
                <option key={d} value={d}>{d}</option>
              ))}
            </select>
            <Icd11SearchField value={dx} onSelect={setDx} />
            <button
              type="button"
              disabled={actions.disposition.isPending || !dx?.code}
              onClick={() => actions.disposition.mutate({
                dispositionType: disposition,
                primaryDiagnosisCode: dx?.code,
                primaryDiagnosisDisplay: dx?.description,
              })}
              className="rounded-lg bg-green-600 px-4 py-2 text-sm text-white"
            >
              Complete disposition
            </button>
          </section>
        )}

        {step === 6 && Boolean(visit.disposition) && (
          <section className="space-y-3">
            <div className="rounded-xl border border-green-200 bg-success-soft p-4 text-sm text-primary-hover">
              Disposition recorded:{" "}
              <strong>
                {typeof visit.disposition === "object"
                  ? String(
                      (visit.disposition as Record<string, unknown>).disposition_type ??
                        "recorded",
                    )
                  : String(visit.disposition)}
              </strong>
              . The ED visit row is closed.
            </div>
            {visit.emergency_episode_id ? (
              <div
                className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950"
                data-testid="episode-disposition-reconcile"
              >
                <p className="font-medium">Episode disposition may still be required</p>
                <p className="mt-1 text-xs">
                  When the ED disposition cannot satisfy the episode R12 map, the continuum episode
                  stays open (silent-open). Complete episode disposition on the spine — do not invent
                  destination fields.
                </p>
                <Link
                  href={`/clinical/emergency/spine/${String(visit.emergency_episode_id)}/disposition`}
                  className="mt-2 inline-block text-primary underline"
                >
                  Open episode disposition
                </Link>
              </div>
            ) : null}
          </section>
        )}

        {Boolean(visit.disposition) && step !== 6 && (
          <div className="mt-3 space-y-2">
            <p className="text-sm text-green-700">Disposition recorded — visit complete.</p>
            {visit.emergency_episode_id ? (
              <Link
                href={`/clinical/emergency/spine/${String(visit.emergency_episode_id)}/disposition`}
                className="text-sm text-primary underline"
                data-testid="episode-disposition-reconcile-inline"
              >
                Reconcile episode disposition if still open
              </Link>
            ) : null}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
