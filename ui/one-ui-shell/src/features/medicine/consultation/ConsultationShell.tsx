"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, Users } from "lucide-react";
import {
  useAcceptCareTransfer,
  useAnswerConsultation,
  useCareTransfers,
  useConsultations,
  useMdtDecisions,
  useRecordMdtDecision,
  useRequestCareTransfer,
  useRequestConsultation,
  MDT_MEETING_TYPES,
  MDT_TREATMENT_INTENTS,
  type CareTransfer,
  type Consultation,
} from "@/hooks/queries/useConsultations";

/**
 * Consultation and MDT (brief.md §14).
 *
 * <p>The one thing this screen must never let a reader assume: that the team who answered took the
 * patient. Every consultation shows who holds them, on the card, whether or not a takeover was
 * recommended — and a recommended takeover is shown as a recommendation that has not happened,
 * because that is what it is. §23.10's failure is not a decision anybody makes; it is what happens
 * when nobody says out loud who still holds the patient.</p>
 */

const STATUS_CLASS: Record<string, string> = {
  REQUESTED: "text-warning-foreground",
  ACCEPTED: "text-warning-foreground",
  ANSWERED: "text-success",
  DECLINED: "text-danger",
  WITHDRAWN: "text-muted-foreground",
};

function ConsultationCard({
  consultation,
  pendingTransfer,
  onAnswer,
  onRequestTransfer,
  onAcceptTransfer,
  answering,
  transferring,
}: {
  consultation: Consultation;
  pendingTransfer?: CareTransfer;
  onAnswer: (id: string, advice: string) => void;
  onRequestTransfer: (consultation: Consultation) => void;
  onAcceptTransfer: (transferId: string, acceptingRef: string) => void;
  answering: boolean;
  transferring: boolean;
}) {
  const [advice, setAdvice] = useState("");
  const [acceptingRef, setAcceptingRef] = useState("");
  const open = consultation.status === "REQUESTED" || consultation.status === "ACCEPTED";
  const canRequestTransfer =
    consultation.takeover_recommended &&
    consultation.status === "ANSWERED" &&
    !pendingTransfer &&
    consultation.owning_service === consultation.requesting_service;

  return (
    <div
      className="rounded-lg border border-border bg-card p-4 space-y-2"
      data-testid={`consultation-${consultation.consultation_id}`}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium">{consultation.question}</p>
        <span className={`text-xs ${STATUS_CLASS[consultation.status] ?? ""}`}>
          {consultation.status.toLowerCase()}
        </span>
      </div>
      <p className="text-xs text-muted-foreground">
        {consultation.requesting_service} → {consultation.asked_of_service} ·{" "}
        {consultation.urgency.toLowerCase()}
      </p>

      {/* Always shown, answered or not. This is the sentence that stops the record losing the
          patient, so it is never conditional on anything. */}
      <p className="text-sm font-medium" data-testid={`owner-${consultation.consultation_id}`}>
        {consultation.owning_service_note}
      </p>

      {consultation.takeover_recommended && (
        <p
          className="text-sm text-warning-foreground"
          data-testid={`takeover-${consultation.consultation_id}`}
        >
          {consultation.takeover_note ??
            "A takeover was recommended and has not happened."}
        </p>
      )}

      {consultation.advice && (
        <p className="text-sm text-muted-foreground" data-testid={`advice-${consultation.consultation_id}`}>
          {consultation.advice} — {consultation.responded_by}
        </p>
      )}
      {consultation.decline_reason && (
        <p className="text-sm text-danger" data-testid={`declined-${consultation.consultation_id}`}>
          Declined: {consultation.decline_reason}
        </p>
      )}

      {open && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="text"
            className="flex-1 min-w-48 rounded border border-border px-2 py-1 text-sm"
            placeholder="Your advice"
            value={advice}
            onChange={(e) => setAdvice(e.target.value)}
            data-testid={`advice-input-${consultation.consultation_id}`}
          />
          <button
            type="button"
            onClick={() => onAnswer(consultation.consultation_id, advice)}
            disabled={answering || advice.trim() === ""}
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
            data-testid={`answer-${consultation.consultation_id}`}
          >
            Answer
          </button>
        </div>
      )}

      {canRequestTransfer && (
        <button
          type="button"
          onClick={() => onRequestTransfer(consultation)}
          disabled={transferring}
          className="rounded border border-warning/40 bg-amber-50 px-3 py-1.5 text-sm text-warning-foreground disabled:opacity-60"
          data-testid={`request-transfer-${consultation.consultation_id}`}
        >
          Request transfer of care
        </button>
      )}

      {pendingTransfer && (
        <div
          className="space-y-2 rounded border border-border bg-background/60 p-3"
          data-testid={`pending-transfer-${pendingTransfer.transfer_id}`}
        >
          <p className="text-sm" data-testid={`transfer-note-${pendingTransfer.transfer_id}`}>
            {pendingTransfer.ownership_note}
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="text"
              className="flex-1 min-w-48 rounded border border-border px-2 py-1 text-sm"
              placeholder="Your record id (accepting_ref)"
              value={acceptingRef}
              onChange={(e) => setAcceptingRef(e.target.value)}
              data-testid={`accepting-ref-${pendingTransfer.transfer_id}`}
            />
            <button
              type="button"
              onClick={() => onAcceptTransfer(pendingTransfer.transfer_id, acceptingRef.trim())}
              disabled={transferring || acceptingRef.trim() === ""}
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
              data-testid={`accept-transfer-${pendingTransfer.transfer_id}`}
            >
              Accept transfer
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export function ConsultationShell({
  patientId,
  journeyId,
}: {
  patientId: string;
  journeyId?: string;
}) {
  const consultations = useConsultations(patientId);
  const transfers = useCareTransfers(patientId);
  const decisions = useMdtDecisions(patientId);
  const request = useRequestConsultation(patientId);
  const answer = useAnswerConsultation(patientId);
  const requestTransfer = useRequestCareTransfer(patientId);
  const acceptTransfer = useAcceptCareTransfer(patientId);
  const recordMdt = useRecordMdtDecision(patientId);

  const [askedOf, setAskedOf] = useState("");
  const [question, setQuestion] = useState("");
  const [mdtType, setMdtType] = useState<string>("COMPLEX_MEDICINE");
  const [mdtChair, setMdtChair] = useState("");
  const [mdtParticipants, setMdtParticipants] = useState("");
  const [mdtDecision, setMdtDecision] = useState("");
  const [mdtIntent, setMdtIntent] = useState<string>("");
  const [mdtNext, setMdtNext] = useState("");
  const [mdtAnchor, setMdtAnchor] = useState(journeyId ?? "");

  const pendingByConsultation = new Map(
    (transfers.data?.data ?? [])
      .filter((t) => t.status === "PENDING" && t.consultation_id)
      .map((t) => [t.consultation_id as string, t]),
  );

  const send = () => {
    if (askedOf.trim() === "" || question.trim() === "") return;
    request.mutate({
      subject_cpid: patientId,
      journey_id: journeyId,
      requesting_service: "MEDICINE",
      asked_of_service: askedOf.trim().toUpperCase(),
      question: question.trim(),
    });
  };

  const sendMdt = () => {
    if (
      mdtChair.trim() === "" ||
      mdtParticipants.trim() === "" ||
      mdtDecision.trim() === "" ||
      mdtAnchor.trim() === ""
    ) {
      return;
    }
    const anchor = mdtAnchor.trim();
    const looksUuid =
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(anchor);
    recordMdt.mutate(
      {
        subject_cpid: patientId,
        ...(looksUuid
          ? { journey_id: anchor }
          : { encounter_id: anchor }),
        meeting_type: mdtType,
        chaired_by: mdtChair.trim(),
        participants: mdtParticipants.trim(),
        decision: mdtDecision.trim(),
        treatment_intent: mdtIntent || null,
        next_action: mdtNext.trim() || undefined,
      },
      {
        onSuccess: () => {
          setMdtChair("");
          setMdtParticipants("");
          setMdtDecision("");
          setMdtIntent("");
          setMdtNext("");
        },
      },
    );
  };

  return (
    <div className="space-y-6" data-testid="consultations">
      <section className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="consultation-new">
        <h3 className="font-semibold text-sm">Ask another team a question</h3>
        <p className="text-sm text-muted-foreground">
          A consultation asks a question. It never transfers the patient — medicine keeps them unless
          a care transfer is separately accepted with the receiving service&apos;s own record id.
        </p>
        <div className="flex flex-wrap gap-2">
          <input
            type="text"
            className="rounded border border-border px-2 py-1 text-sm w-40"
            placeholder="Team (e.g. SURGERY)"
            value={askedOf}
            onChange={(e) => setAskedOf(e.target.value)}
            data-testid="consultation-asked-of"
          />
          <input
            type="text"
            className="flex-1 min-w-64 rounded border border-border px-2 py-1 text-sm"
            placeholder="Your question"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            data-testid="consultation-question"
          />
          <button
            type="button"
            onClick={send}
            disabled={request.isPending || askedOf.trim() === "" || question.trim() === ""}
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
            data-testid="consultation-send"
          >
            {request.isPending ? <Loader2 className="w-4 h-4 animate-spin inline" /> : "Ask"}
          </button>
        </div>
        {request.isError && (
          <p className="text-sm text-danger" data-testid="consultation-send-failed">
            The consultation was <strong>not</strong> sent. Nobody has been asked.
          </p>
        )}
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold">Consultations</h3>
        {consultations.isError ? (
          <p className="text-sm text-danger" data-testid="consultations-failed">
            Consultations could not be read. This is <strong>not</strong> a statement that none was
            requested.
          </p>
        ) : (consultations.data?.data ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="consultations-empty">
            No consultation has been requested for this patient. The list was read.
          </p>
        ) : (
          (consultations.data?.data ?? []).map((c) => (
            <ConsultationCard
              key={c.consultation_id}
              consultation={c}
              pendingTransfer={pendingByConsultation.get(c.consultation_id)}
              answering={answer.isPending}
              transferring={requestTransfer.isPending || acceptTransfer.isPending}
              onAnswer={(id, advice) => answer.mutate({ consultationId: id, advice })}
              onRequestTransfer={(consultation) =>
                requestTransfer.mutate({
                  subject_cpid: patientId,
                  journey_id: journeyId,
                  consultation_id: consultation.consultation_id,
                  from_service: consultation.owning_service,
                  to_service: consultation.asked_of_service,
                  reason: `Transfer following consultation ${consultation.consultation_id}`,
                })
              }
              onAcceptTransfer={(transferId, acceptingRef) =>
                acceptTransfer.mutate({ transferId, accepting_ref: acceptingRef })
              }
            />
          ))
        )}
      </section>

      <section className="space-y-3">
        <div className="flex items-center gap-2">
          <Users className="w-4 h-4 text-primary" />
          <h3 className="text-sm font-semibold">Multidisciplinary decisions</h3>
        </div>
        <p className="text-[11px] text-muted-foreground">
          This is the governed decision record — author, participants and treatment intent. A
          chaired board with its own agenda and consensus/dissent minutes is convened separately at{" "}
          <Link href="/work/telemedicine/mdt" className="text-primary hover:underline">
            MDT boards
          </Link>
          ; if one met, its minutes live there, not here.
        </p>

        <div
          className="rounded-lg border border-border bg-card p-4 space-y-2"
          data-testid="mdt-new"
        >
          <h4 className="text-sm font-medium">Record an MDT decision</h4>
          <p className="text-xs text-muted-foreground">
            Needs a visit or encounter anchor (care-continuum). Recording here never invents a
            board session — that lives on MDT boards.
          </p>
          <div className="flex flex-wrap gap-2">
            <select
              className="rounded border border-border px-2 py-1 text-sm"
              value={mdtType}
              onChange={(e) => setMdtType(e.target.value)}
              data-testid="mdt-meeting-type"
            >
              {MDT_MEETING_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replace(/_/g, " ")}
                </option>
              ))}
            </select>
            <input
              type="text"
              className="rounded border border-border px-2 py-1 text-sm min-w-48"
              placeholder="Journey or encounter id"
              value={mdtAnchor}
              onChange={(e) => setMdtAnchor(e.target.value)}
              data-testid="mdt-anchor"
            />
            <input
              type="text"
              className="rounded border border-border px-2 py-1 text-sm w-40"
              placeholder="Chaired by"
              value={mdtChair}
              onChange={(e) => setMdtChair(e.target.value)}
              data-testid="mdt-chaired-by"
            />
            <input
              type="text"
              className="flex-1 min-w-48 rounded border border-border px-2 py-1 text-sm"
              placeholder="Participants (who was in the room)"
              value={mdtParticipants}
              onChange={(e) => setMdtParticipants(e.target.value)}
              data-testid="mdt-participants"
            />
            <input
              type="text"
              className="flex-1 min-w-64 rounded border border-border px-2 py-1 text-sm"
              placeholder="The decision"
              value={mdtDecision}
              onChange={(e) => setMdtDecision(e.target.value)}
              data-testid="mdt-decision"
            />
            <select
              className="rounded border border-border px-2 py-1 text-sm"
              value={mdtIntent}
              onChange={(e) => setMdtIntent(e.target.value)}
              data-testid="mdt-intent"
            >
              <option value="">Intent not recorded</option>
              {MDT_TREATMENT_INTENTS.map((t) => (
                <option key={t} value={t}>
                  {t.replace(/_/g, " ")}
                </option>
              ))}
            </select>
            <input
              type="text"
              className="flex-1 min-w-48 rounded border border-border px-2 py-1 text-sm"
              placeholder="Next action (optional)"
              value={mdtNext}
              onChange={(e) => setMdtNext(e.target.value)}
              data-testid="mdt-next-action"
            />
            <button
              type="button"
              onClick={sendMdt}
              disabled={
                recordMdt.isPending ||
                mdtChair.trim() === "" ||
                mdtParticipants.trim() === "" ||
                mdtDecision.trim() === "" ||
                mdtAnchor.trim() === ""
              }
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
              data-testid="mdt-record"
            >
              {recordMdt.isPending ? (
                <Loader2 className="w-4 h-4 animate-spin inline" />
              ) : (
                "Record decision"
              )}
            </button>
          </div>
          {recordMdt.isError && (
            <p className="text-sm text-danger" data-testid="mdt-record-failed">
              The decision was <strong>not</strong> saved. Nothing has been recorded.
            </p>
          )}
        </div>

        {decisions.isError ? (
          <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-red-50 p-4" data-testid="mdt-failed">
            <AlertTriangle className="w-4 h-4 text-danger mt-0.5" />
            <p className="text-sm text-danger">
              MDT decisions could not be read. This is <strong>not</strong> a statement that no
              decision has been made — treatment intent may already be set.
            </p>
          </div>
        ) : (decisions.data?.data ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="mdt-empty">
            No MDT decision recorded. The list was read.
          </p>
        ) : (
          (decisions.data?.data ?? []).map((d) => (
            <div
              key={d.decision_id}
              className="rounded-lg border border-border bg-card p-4 space-y-1"
              data-testid={`mdt-${d.decision_id}`}
            >
              <p className="text-sm font-medium">{d.decision}</p>
              <p className="text-xs text-muted-foreground">
                {d.meeting_type.replace(/_/g, " ").toLowerCase()} · {d.met_on} · chaired by{" "}
                {d.chaired_by}
              </p>
              <p className="text-xs text-muted-foreground">{d.participants}</p>
              {/* Null intent is called out rather than left blank: a blank reads as "no decision on
                  intent was needed", and the record cannot support that reading. */}
              {d.treatment_intent ? (
                <p className="text-sm">
                  Intent: {d.treatment_intent.replace(/_/g, " ").toLowerCase()}
                </p>
              ) : (
                <p className="text-sm text-warning-foreground" data-testid={`mdt-no-intent-${d.decision_id}`}>
                  No treatment intent was recorded for this meeting.
                </p>
              )}
              {d.next_action && (
                <p className="text-sm text-muted-foreground">
                  Next: {d.next_action}
                  {d.responsible_service ? ` (${d.responsible_service})` : ""}
                </p>
              )}
            </div>
          ))
        )}
      </section>
    </div>
  );
}
