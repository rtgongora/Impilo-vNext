"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, Loader2, Scissors } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { AnaesthesiaPreopPanel } from "@/components/clinical/AnaesthesiaPreopPanel";
import { ProcedureConsentCapture } from "@/components/clinical/ProcedureConsentCapture";
import { useProcedureEpisode, useProcedureEpisodeAction } from "@/hooks/queries/useProcedureEpisode";

const PHASES = ["SIGN_IN", "TIME_OUT", "SIGN_OUT"] as const;
const STEPS = ["Pre-op", "Checklist", "Theatre", "Intra-op", "PACU", "Complete"] as const;

export default function ProcedureEpisodePage() {
  const params = useParams<{ patientId: string; episodeId: string }>();
  const { data: episode, isLoading, isError, refetch } = useProcedureEpisode(params.episodeId);
  const actions = useProcedureEpisodeAction(params.episodeId);
  const [step, setStep] = useState(0);
  const [intraopNote, setIntraopNote] = useState("");
  const [hr, setHr] = useState("72");
  const [sbp, setSbp] = useState("120");
  const [spo2, setSpo2] = useState("98");
  const [aldrete, setAldrete] = useState("8");
  const [itemCode, setItemCode] = useState("");
  const [itemQty, setItemQty] = useState("1");
  const [facilityId, setFacilityId] = useState("00000000-0000-4000-8000-000000000010");
  const [storeId, setStoreId] = useState("00000000-0000-4000-8000-000000000011");

  if (isLoading) {
    return (
      <EHRLayout>
        <div className="flex justify-center py-20"><Loader2 className="h-8 w-8 animate-spin text-muted-foreground" /></div>
      </EHRLayout>
    );
  }

  if (isError || !episode) {
    return (
      <EHRLayout>
        <PageShell title="Procedure case">
          <p className="text-sm text-muted-foreground">Unable to load procedure episode.</p>
          <button type="button" onClick={() => void refetch()} className="mt-4 rounded-lg bg-primary px-4 py-2 text-white text-sm">Retry</button>
        </PageShell>
      </EHRLayout>
    );
  }

  const checklist = episode.checklist ?? [];
  const nursingDone = episode.preop_assessments?.some((a) => a.assessment_type === "NURSING");
  const anaesthesiaDone = episode.preop_assessments?.some((a) => a.assessment_type === "ANAESTHESIA");

  return (
    <EHRLayout>
      <PageShell
        title={episode.procedure_name}
        subtitle={`Status: ${episode.status}`}
      >
        <div className="mb-6 flex items-center gap-3">
          <Link href={`/ehr/${params.patientId}/procedures`} className="inline-flex items-center gap-1 text-sm text-primary hover:underline">
            <ArrowLeft className="h-4 w-4" /> Back to procedures
          </Link>
          <Scissors className="h-5 w-5 text-primary" />
        </div>

        <div className="mb-8 flex flex-wrap gap-2">
          {STEPS.map((label, i) => (
            <button
              key={label}
              type="button"
              onClick={() => setStep(i)}
              className={`rounded-full px-3 py-1 text-xs font-medium ${step === i ? "bg-primary text-white" : "bg-neutral-100 text-muted-foreground"}`}
            >
              {label}
            </button>
          ))}
        </div>

        {step === 0 && (
          <section className="space-y-4 rounded-2xl border p-6">
            <h2 className="text-lg font-semibold">Pre-operative assessments</h2>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="rounded-xl border p-4">
                <h3 className="font-medium">Nursing pre-op</h3>
                {nursingDone ? <p className="mt-2 text-sm text-green-700 flex items-center gap-1"><CheckCircle2 className="h-4 w-4" /> Complete</p> : (
                  <button
                    type="button"
                    disabled={actions.submitPreop.isPending}
                    onClick={() => actions.submitPreop.mutate({ assessmentType: "NURSING", fastingVerified: true, allergiesReviewed: true, assessedBy: "nurse-web" })}
                    className="mt-3 rounded-lg bg-primary px-4 py-2 text-sm text-white"
                  >
                    Submit nursing assessment
                  </button>
                )}
              </div>
              <AnaesthesiaPreopPanel
                episodeId={params.episodeId}
                procedureName={episode.procedure_name}
                suggestions={episode.anaesthesia_score_suggestions}
                recordedScores={episode.anaesthesia_scores}
                anaesthesiaDone={!!anaesthesiaDone}
              />
            </div>
            <ProcedureConsentCapture episodeId={params.episodeId} episode={episode} />
          </section>
        )}

        {step === 1 && (
          <section className="space-y-4 rounded-2xl border p-6">
            <h2 className="text-lg font-semibold">WHO surgical safety checklist</h2>
            {PHASES.map((phase) => {
              const items = checklist.filter((c) => c.phase === phase);
              const pending = items.filter((c) => !c.completed);
              return (
                <div key={phase} className="rounded-xl border p-4">
                  <h3 className="font-medium">{phase.replace("_", " ")}</h3>
                  <ul className="mt-2 space-y-1 text-sm text-muted-foreground">
                    {items.map((item) => (
                      <li key={item.id} className={item.completed ? "text-green-700" : ""}>
                        {item.completed ? "✓" : "○"} {item.item_label}
                      </li>
                    ))}
                  </ul>
                  {pending.length > 0 && (
                    <button
                      type="button"
                      disabled={actions.completeChecklist.isPending}
                      onClick={() => actions.completeChecklist.mutate({ phase, itemIds: pending.map((p) => p.id) })}
                      className="mt-3 rounded-lg bg-primary px-4 py-2 text-sm text-white"
                    >
                      Complete {phase.replace("_", " ")}
                    </button>
                  )}
                </div>
              );
            })}
          </section>
        )}

        {step === 2 && (
          <section className="rounded-2xl border p-6">
            <h2 className="text-lg font-semibold">Start procedure in theatre</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              MVUMO consent must be GRANTED before theatre start.
              {episode.consent_status !== "GRANTED" && " Complete consent in the Pre-op step first."}
            </p>
            <button
              type="button"
              disabled={actions.startProcedure.isPending || episode.status === "IN_PROGRESS" || episode.consent_status !== "GRANTED"}
              onClick={() => actions.startProcedure.mutate()}
              className="mt-4 rounded-lg bg-red-600 px-4 py-2 text-sm text-white"
            >
              {episode.status === "IN_PROGRESS" ? "Procedure in progress" : "Start procedure"}
            </button>
          </section>
        )}

        {step === 3 && (
          <section className="rounded-2xl border p-6 space-y-4">
            <h2 className="text-lg font-semibold">Intra-operative recording</h2>
            <textarea
              value={intraopNote}
              onChange={(e) => setIntraopNote(e.target.value)}
              placeholder="Operative note, vitals, medications, events…"
              className="w-full rounded-lg border p-3 text-sm min-h-[100px]"
            />
            <button
              type="button"
              disabled={!intraopNote.trim() || actions.recordIntraop.isPending}
              onClick={() => {
                actions.recordIntraop.mutate({ eventType: "NOTE", description: intraopNote, recordedBy: "surgeon-web" });
                setIntraopNote("");
              }}
              className="rounded-lg bg-primary px-4 py-2 text-sm text-white"
            >
              Record event
            </button>
            <div className="rounded-xl border p-4 space-y-3">
              <h3 className="font-medium text-sm">Intra-op anaesthetic vitals</h3>
              <div className="flex flex-wrap gap-2">
                <input value={hr} onChange={(e) => setHr(e.target.value)} placeholder="HR" className="w-20 rounded border px-2 py-1 text-sm" />
                <input value={sbp} onChange={(e) => setSbp(e.target.value)} placeholder="SBP" className="w-20 rounded border px-2 py-1 text-sm" />
                <input value={spo2} onChange={(e) => setSpo2(e.target.value)} placeholder="SpO₂" className="w-20 rounded border px-2 py-1 text-sm" />
                <button
                  type="button"
                  disabled={actions.recordIntraopVitals.isPending}
                  onClick={() =>
                    actions.recordIntraopVitals.mutate({
                      heartRate: Number(hr),
                      systolicBp: Number(sbp),
                      spo2: Number(spo2),
                      recordedBy: "anaesthetist-web",
                    })
                  }
                  className="rounded-lg bg-primary px-3 py-1 text-sm text-white"
                >
                  Record vitals snapshot
                </button>
              </div>
            </div>
            <ul className="text-sm text-muted-foreground space-y-1">
              {(episode.intraop_events ?? []).map((ev) => (
                <li key={String(ev.id)}>
                  {ev.event_type === "VITALS" ? "🫀 " : ""}{String(ev.description)}
                </li>
              ))}
            </ul>
            <div className="rounded-xl border p-4 space-y-3">
              <h3 className="font-medium text-sm">Operative document (document-service)</h3>
              <input
                type="file"
                accept=".pdf,.doc,.docx,.txt"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) actions.uploadOperativeDocument.mutate(file);
                }}
                className="text-sm"
              />
              <ul className="text-xs text-muted-foreground space-y-1">
                {(episode.documents ?? []).map((doc) => (
                  <li key={String(doc.id)}>{String(doc.title)} ({String(doc.document_type)})</li>
                ))}
              </ul>
            </div>
            <div className="rounded-xl border p-4 space-y-3">
              <h3 className="font-medium text-sm">Theatre consumables (inventory)</h3>
              <div className="flex flex-wrap gap-2">
                <input
                  value={itemCode}
                  onChange={(e) => setItemCode(e.target.value)}
                  placeholder="Item code"
                  className="rounded border px-2 py-1 text-sm"
                />
                <input
                  type="number"
                  min={1}
                  value={itemQty}
                  onChange={(e) => setItemQty(e.target.value)}
                  className="rounded border px-2 py-1 text-sm w-20"
                />
                <button
                  type="button"
                  disabled={!itemCode.trim() || actions.recordConsumable.isPending}
                  onClick={() => actions.recordConsumable.mutate({
                    itemCode: itemCode.trim(),
                    quantity: Number(itemQty),
                    facilityId,
                    storeId,
                    recordedBy: "theatre-nurse",
                  })}
                  className="rounded-lg bg-primary px-3 py-1 text-sm text-white"
                >
                  Issue consumable
                </button>
              </div>
              <ul className="text-xs text-muted-foreground space-y-1">
                {(episode.consumables ?? []).map((c) => (
                  <li key={String(c.id)}>{String(c.item_code)} × {String(c.quantity)}</li>
                ))}
              </ul>
            </div>
            <button
              type="button"
              onClick={() => actions.enterPacu.mutate()}
              disabled={actions.enterPacu.isPending}
              className="rounded-lg border border-impilo-500 px-4 py-2 text-sm text-primary"
            >
              Transfer to PACU
            </button>
          </section>
        )}

        {step === 4 && (
          <section className="rounded-2xl border p-6 space-y-4">
            <h2 className="text-lg font-semibold">Post-operative / PACU recovery</h2>
            <label className="block text-sm">Aldrete score
              <input type="number" min={0} max={10} value={aldrete} onChange={(e) => setAldrete(e.target.value)} className="mt-1 rounded border px-2 py-1 w-24" />
            </label>
            <button
              type="button"
              onClick={() => actions.completePostop.mutate({
                aldreteScore: Number(aldrete),
                painScore: 2,
                disposition: "WARD",
                recordedBy: "pacu-nurse",
              })}
              disabled={actions.completePostop.isPending}
              className="rounded-lg bg-primary px-4 py-2 text-sm text-white"
            >
              Complete PACU recovery
            </button>
          </section>
        )}

        {step === 5 && (
          <section className="rounded-2xl border p-6">
            <h2 className="text-lg font-semibold">Close procedure episode</h2>
            <button
              type="button"
              onClick={() => actions.completeEpisode.mutate()}
              disabled={actions.completeEpisode.isPending || episode.status === "COMPLETED"}
              className="mt-4 rounded-lg bg-green-600 px-4 py-2 text-sm text-white"
            >
              {episode.status === "COMPLETED" ? "Episode completed" : "Mark episode complete"}
            </button>
          </section>
        )}
      </PageShell>
    </EHRLayout>
  );
}
