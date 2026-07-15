"use client";

import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { EdTriageDiscriminatorPanel } from "@/components/clinical/EdTriageDiscriminatorPanel";
import { Icd11SearchField, type Icd11Hit } from "@/components/clinical/Icd11SearchField";
import { ResuscitationWorkspace } from "@/components/clinical/ResuscitationWorkspace";
import { TraumaTeamPanel } from "@/components/clinical/TraumaTeamPanel";
import { TraumaSurveyPanel } from "@/components/clinical/TraumaSurveyPanel";
import { useEdVisit, useEdVisitActions } from "@/hooks/queries/useEdVisit";
import { useActivateEmergency, useEmergencyActivations } from "@/hooks/queries/useEmergency";

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

  const handleRecommendedAcuity = useCallback((level: number) => setAcuity(level), []);
  const handleDiscriminatorChange = useCallback((id: string, value: boolean | number) => {
    setDiscriminators((prev) => ({ ...prev, [id]: value }));
  }, []);

  const suggestions = useMemo(
    () => (visit?.protocol_suggestions as Array<Record<string, unknown>>) ?? [],
    [visit?.protocol_suggestions],
  );

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

        {step === 1 && status === "REGISTERED" && (
          <section className="rounded-xl border p-4 space-y-4">
            <h2 className="font-semibold">Structured triage</h2>
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
              disabled={actions.triage.isPending}
              onClick={() => actions.triage.mutate({
                acuity,
                triageSystem,
                autoAcuity: triageSystem !== "IMPILO_5",
                applyDiscriminators: triageSystem !== "IMPILO_5",
                chiefComplaint: complaint || visit.chief_complaint,
                painScore: Number(pain),
                discriminators,
                dangerSigns: acuity <= 2 ? ["High acuity presentation"] : [],
              })}
              className="rounded-lg bg-red-600 px-4 py-2 text-sm text-white"
            >
              Complete triage
            </button>
          </section>
        )}

        {step === 2 && (
          <section className="rounded-xl border p-4 space-y-3">
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
              onClick={() => actions.pageTeam.mutate({ pageType: "STAT_CONSULT", message: "Urgent consult to ED", recipientRole: "ON_CALL" })}
              className="rounded-lg border px-4 py-2 text-sm"
            >
              Page on-call team
            </button>
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
              <button
                type="button"
                onClick={() => actions.advancePathway.mutate({
                  sessionId: String(visit.pathway_session_id),
                  answers: { red_flags: false },
                })}
                className="rounded-lg border px-4 py-2 text-sm"
              >
                Advance pathway step
              </button>
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

        {Boolean(visit.disposition) && (
          <p className="text-sm text-green-700">Disposition recorded — visit complete.</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
