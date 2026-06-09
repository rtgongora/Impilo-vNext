"use client";

import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { EdTriageDiscriminatorPanel } from "@/components/clinical/EdTriageDiscriminatorPanel";
import { Icd11SearchField, type Icd11Hit } from "@/components/clinical/Icd11SearchField";
import { useEdVisit, useEdVisitActions } from "@/hooks/queries/useEdVisit";

const STEPS = ["Arrival", "Triage", "Treatment", "Trauma", "Protocol", "Disposition"] as const;
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
  const [step, setStep] = useState(1);
  const [acuity, setAcuity] = useState(3);
  const [triageSystem, setTriageSystem] = useState<"ESI" | "MTS" | "IMPILO_5">("ESI");
  const [complaint, setComplaint] = useState("");
  const [pain, setPain] = useState("5");
  const [discriminators, setDiscriminators] = useState<Record<string, boolean | number>>({ resources_needed: 1 });
  const [dx, setDx] = useState<Icd11Hit | null>(null);
  const [disposition, setDisposition] = useState("DISCHARGE");

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
              className={`rounded-full px-3 py-1 ${step === i ? "bg-red-600 text-white" : "bg-gray-100"}`}
            >
              {i + 1}. {label}
            </button>
          ))}
          <Link href="/clinical/emergency" className="ml-auto text-impilo-600 underline text-sm">
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
                  className={`rounded-full px-3 py-1 ${triageSystem === sys ? "bg-gray-900 text-white" : "bg-gray-100"}`}
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
              className="rounded-lg bg-impilo-500 px-4 py-2 text-sm text-white"
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
          <section className="rounded-xl border p-4 space-y-3">
            <h2 className="font-semibold">Trauma activation & survey</h2>
            {!visit.active_trauma_id ? (
              <button
                type="button"
                onClick={() => actions.activateTrauma.mutate({ traumaLevel: 2, mechanism: "RTA Driver", teamLeader: "ED lead" })}
                className="rounded-lg bg-orange-600 px-4 py-2 text-sm text-white"
              >
                Activate trauma team
              </button>
            ) : (
              <>
                <p className="text-sm text-green-700">Trauma active: {String(visit.active_trauma_id)}</p>
                <button
                  type="button"
                  onClick={() => actions.recordTraumaSurvey.mutate({
                    surveyType: "PRIMARY",
                    sectionKey: "A",
                    checklist: { "Airway patent": true, "C-spine maintained": true },
                    vitals: { hr: 100, sbp: 110, spo2: 96 },
                  })}
                  className="rounded-lg border px-4 py-2 text-sm"
                >
                  Record primary survey (A)
                </button>
                <button
                  type="button"
                  onClick={() => actions.endTrauma.mutate({ outcome: "STABILISED" })}
                  className="rounded-lg border border-red-300 px-4 py-2 text-sm text-red-700"
                >
                  End trauma activation
                </button>
              </>
            )}
          </section>
        )}

        {step === 4 && (
          <section className="rounded-xl border p-4 space-y-3">
            <h2 className="font-semibold">Assessment protocol (CKP)</h2>
            <ul className="space-y-2 text-sm">
              {suggestions.map((s) => (
                <li key={String(s.protocol_code)} className="flex items-center justify-between rounded border p-2">
                  <span>
                    {String(s.label)}
                    {s.recommended ? " ★" : ""}
                    <span className="block text-xs text-gray-500">{String(s.rationale)}</span>
                  </span>
                  <button
                    type="button"
                    onClick={() => actions.bindProtocol.mutate({
                      protocolRef: s.protocol_code,
                      pathwayRef: s.pathway_ref,
                      patientCpid: patientId,
                      encounterId: visit.encounter_id,
                    })}
                    className="rounded bg-impilo-500 px-2 py-1 text-xs text-white"
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

        {step === 5 && !visit.disposition && (
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
