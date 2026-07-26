"use client";

import React from "react";
import { SegmentedControl } from "shared-ui";
import type { ImamAssessment, ImamEpisode } from "@/hooks/queries/useImam";
import { useRecordImamVisit } from "@/hooks/queries/useImam";

/**
 * Recording one scheduled review.
 *
 * Two things this form does that a plainer one would not:
 *
 *  - **A missed review is something you record**, not something you leave blank. The first
 *    control is attended / did not attend, and choosing "did not attend" collapses the
 *    measurements rather than asking for numbers nobody took.
 *  - **Oedema and danger signs are tri-state.** "Not assessed" is a selectable answer and the
 *    default, because a form that only offers yes and no makes a busy clinician pick "no" to
 *    submit, and a recorded "no oedema" that nobody checked can discharge a child.
 */
export interface ImamReviewFormProps {
  episode: ImamEpisode;
  assessment: ImamAssessment | null;
  onRecorded?: () => void;
}

type Attendance = "ATTENDED" | "MISSED";

export function ImamReviewForm({ episode, assessment, onRecorded }: ImamReviewFormProps) {
  const record = useRecordImamVisit();

  const [attendance, setAttendance] = React.useState<Attendance>("ATTENDED");
  const [muac, setMuac] = React.useState("");
  const [weight, setWeight] = React.useState("");
  const [oedema, setOedema] = React.useState("NOT_ASSESSED");
  const [appetite, setAppetite] = React.useState("");
  const [dangerSigns, setDangerSigns] = React.useState<"UNKNOWN" | "YES" | "NO">("UNKNOWN");
  const [sachets, setSachets] = React.useState("");
  const [note, setNote] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);

  const recommended = assessment?.recommendedSachetsPerWeek ?? null;
  const attended = attendance === "ATTENDED";

  const numberOrNull = (value: string): number | null => {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : null;
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    if (attended && !muac.trim() && !weight.trim()) {
      setError("Record at least an arm circumference or a weight for a review the child attended.");
      return;
    }

    try {
      await record.mutateAsync({
        episodeId: episode.id,
        patientId: episode.patientId,
        attended,
        visitDate: attended ? new Date().toISOString() : null,
        scheduledFor: attended ? null : episode.nextReviewDue,
        encounterId: episode.encounterId,
        muacCm: attended ? numberOrNull(muac) : null,
        weightKg: attended ? numberOrNull(weight) : null,
        oedema: attended ? oedema : null,
        appetiteTest: attended && appetite ? appetite : null,
        dangerSignsPresent: attended && dangerSigns !== "UNKNOWN" ? dangerSigns === "YES" : null,
        rutfSachetsIssued: attended ? numberOrNull(sachets) : null,
        clinicalNote: note.trim() || null,
      });
      setMuac("");
      setWeight("");
      setOedema("NOT_ASSESSED");
      setAppetite("");
      setDangerSigns("UNKNOWN");
      setSachets("");
      setNote("");
      onRecorded?.();
    } catch (caught) {
      const body = caught as { message?: string; status?: number };
      setError(body?.message ?? "The review could not be recorded. It has not been saved.");
    }
  };

  return (
    <form
      onSubmit={submit}
      className="rounded-lg border border-border bg-background p-4"
      data-testid="imam-review-form"
    >
      <h3 className="text-sm font-semibold text-foreground">Record this review</h3>

      <div className="mt-3">
        <span id="imam-attendance-label" className="text-xs font-medium text-muted-foreground">
          Did the child attend?
        </span>
        <SegmentedControl
          aria-labelledby="imam-attendance-label"
          data-testid="imam-attendance"
          className="mt-1"
          options={[
            { value: "ATTENDED", label: "Attended" },
            { value: "MISSED", label: "Did not attend", tone: "warning" },
          ]}
          value={attendance}
          onChange={(value) => setAttendance(value as Attendance)}
        />
      </div>

      {!attended ? (
        <p className="mt-3 rounded-md border border-amber-500 bg-amber-50 p-2 text-xs text-amber-900">
          Recording the miss is what starts tracing. A review left unrecorded looks the same as one that
          is not due yet.
        </p>
      ) : (
        <>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="block text-xs font-medium text-muted-foreground">
              Mid-upper-arm circumference (cm)
              <input
                type="number"
                step="0.1"
                inputMode="decimal"
                value={muac}
                onChange={(event) => setMuac(event.target.value)}
                data-testid="imam-muac"
                className="mt-1 block min-h-[44px] w-full rounded-md border border-border bg-background px-3 text-sm text-foreground"
              />
            </label>
            <label className="block text-xs font-medium text-muted-foreground">
              Weight (kg)
              <input
                type="number"
                step="0.01"
                inputMode="decimal"
                value={weight}
                onChange={(event) => setWeight(event.target.value)}
                data-testid="imam-weight"
                className="mt-1 block min-h-[44px] w-full rounded-md border border-border bg-background px-3 text-sm text-foreground"
              />
            </label>
          </div>

          <div className="mt-3">
            <span id="imam-oedema-label" className="text-xs font-medium text-muted-foreground">
              Bilateral pitting oedema
            </span>
            <SegmentedControl
              aria-labelledby="imam-oedema-label"
              data-testid="imam-oedema"
              className="mt-1"
              options={[
                { value: "PRESENT", label: "Present", tone: "danger" },
                { value: "ABSENT", label: "Absent", tone: "positive" },
                { value: "NOT_ASSESSED", label: "Not assessed", tone: "muted" },
              ]}
              value={oedema}
              onChange={setOedema}
            />
            <p className="mt-1 text-xs text-muted-foreground">
              Oedema recorded as not assessed cannot count towards discharge. That is deliberate — an
              oedema-free discharge has to rest on someone having looked.
            </p>
          </div>

          <div className="mt-3">
            <span id="imam-danger-label" className="text-xs font-medium text-muted-foreground">
              Any danger sign?
            </span>
            <SegmentedControl
              aria-labelledby="imam-danger-label"
              data-testid="imam-danger-signs"
              className="mt-1"
              options={[
                { value: "YES", label: "Yes", tone: "danger" },
                { value: "NO", label: "No", tone: "positive" },
                { value: "UNKNOWN", label: "Not assessed", tone: "muted" },
              ]}
              value={dangerSigns}
              onChange={(value) => setDangerSigns(value as "UNKNOWN" | "YES" | "NO")}
            />
          </div>

          <div className="mt-3">
            <span id="imam-appetite-label" className="text-xs font-medium text-muted-foreground">
              Appetite test
            </span>
            <SegmentedControl
              aria-labelledby="imam-appetite-label"
              data-testid="imam-appetite"
              className="mt-1"
              allowDeselect
              options={[
                { value: "PASSED", label: "Passed", tone: "positive" },
                { value: "FAILED", label: "Failed", tone: "danger" },
                { value: "NOT_DONE", label: "Not done", tone: "muted" },
              ]}
              value={appetite || null}
              onChange={setAppetite}
            />
          </div>

          <label className="mt-3 block text-xs font-medium text-muted-foreground">
            {episode.programme === "SFP" ? "RUSF" : "RUTF"} sachets issued
            {recommended !== null && (
              <span className="ml-1 font-normal">
                — {recommended} recommended for this weight band
              </span>
            )}
            <input
              type="number"
              step="1"
              inputMode="numeric"
              value={sachets}
              onChange={(event) => setSachets(event.target.value)}
              placeholder={recommended !== null ? String(recommended) : undefined}
              data-testid="imam-sachets"
              className="mt-1 block min-h-[44px] w-full rounded-md border border-border bg-background px-3 text-sm text-foreground"
            />
          </label>
        </>
      )}

      <label className="mt-3 block text-xs font-medium text-muted-foreground">
        Note
        <textarea
          value={note}
          onChange={(event) => setNote(event.target.value)}
          rows={2}
          data-testid="imam-note"
          className="mt-1 block w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
        />
      </label>

      {error && (
        <p className="mt-3 rounded-md border border-red-500 bg-red-50 p-2 text-xs text-red-900" role="alert">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={record.isPending}
        data-testid="imam-review-submit"
        className="mt-3 min-h-[44px] rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60"
      >
        {record.isPending ? "Recording…" : attended ? "Record review" : "Record missed review"}
      </button>
    </form>
  );
}
