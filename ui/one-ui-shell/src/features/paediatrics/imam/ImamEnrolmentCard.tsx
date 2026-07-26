"use client";

import React from "react";
import { SegmentedControl } from "shared-ui";
import { useEnrolImam } from "@/hooks/queries/useImam";
import { useAuthStore } from "@/hooks/useAuthStore";
import { CLASSIFICATION_LABELS, classificationLabel } from "./imam-presentation";

/**
 * Enrolling a child into treatment.
 *
 * The IMNCI classification is the first control, and it is the one that matters: the assessment
 * has already made the clinical judgement, so passing the classification lets the governed pack
 * choose the programme, the review interval, the ration and the discharge criteria. Re-deriving
 * any of those here would create a second opinion about the same child that could disagree with
 * the one on the chart.
 *
 * Measurements are still collected because they are recorded as the admission criteria — what the
 * child looked like on the day they were admitted, kept as recorded so a later revision of a
 * cut-off cannot make the admission look unjustified in hindsight.
 */
export interface ImamEnrolmentCardProps {
  patientId: string;
  journeyId?: string | null;
  encounterId?: string | null;
  facilityId?: string | null;
  ageDays?: number | null;
  /** Pre-filled from the child's latest growth measurement where one exists. */
  suggestedMuacCm?: number | null;
  suggestedWeightKg?: number | null;
  onEnrolled?: (episodeId: string) => void;
}

export function ImamEnrolmentCard({
  patientId,
  journeyId,
  encounterId,
  facilityId,
  ageDays,
  suggestedMuacCm,
  suggestedWeightKg,
  onEnrolled,
}: ImamEnrolmentCardProps) {
  const enrol = useEnrolImam();
  const author = useAuthStore((state) => state.user?.id ?? null);
  const [classification, setClassification] = React.useState("");
  const [muac, setMuac] = React.useState(
    typeof suggestedMuacCm === "number" ? String(suggestedMuacCm) : "",
  );
  const [weight, setWeight] = React.useState(
    typeof suggestedWeightKg === "number" ? String(suggestedWeightKg) : "",
  );
  const [oedema, setOedema] = React.useState("NOT_ASSESSED");
  const [appetite, setAppetite] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);

  const numberOrNull = (value: string): number | null => {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : null;
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!classification && !muac.trim() && oedema === "NOT_ASSESSED") {
      setError(
        "Give the IMNCI classification, or an arm circumference or oedema finding. Acute malnutrition cannot be ruled in or out from an unmeasured child.",
      );
      return;
    }

    try {
      const episode = await enrol.mutateAsync({
        patientId,
        recordedBy: author,
        journeyId,
        encounterId,
        facilityId,
        sourceClassification: classification || null,
        admissionAgeDays: ageDays ?? null,
        admissionMuacCm: numberOrNull(muac),
        admissionWeightKg: numberOrNull(weight),
        admissionOedema: oedema,
        admissionAppetiteTest: appetite || null,
        medicalComplication: appetite === "FAILED" ? true : null,
      });
      onEnrolled?.(episode.id);
    } catch (caught) {
      const body = caught as { message?: string; status?: number };
      // 409 already-enrolled, 422 criteria-not-met and 503 protocol-unreachable each need a
      // different response from the clinician, so the upstream message is shown verbatim.
      setError(body?.message ?? "The child could not be enrolled. Nothing has been saved.");
    }
  };

  return (
    <form
      onSubmit={submit}
      className="rounded-lg border border-border bg-background p-4"
      data-testid="imam-enrolment-card"
    >
      <h3 className="text-sm font-semibold text-foreground">Enrol in nutrition treatment</h3>
      <p className="mt-0.5 text-xs text-muted-foreground">
        The programme, review interval, ration and discharge criteria come from the governed IMAM
        protocol — they are not chosen here.
      </p>

      <div className="mt-3">
        <span id="imam-classification-label" className="text-xs font-medium text-muted-foreground">
          IMNCI nutrition classification
        </span>
        <SegmentedControl
          aria-labelledby="imam-classification-label"
          data-testid="imam-classification"
          className="mt-1"
          allowDeselect
          maxSegments={3}
          options={Object.entries(CLASSIFICATION_LABELS).map(([value, label]) => ({
            value,
            label,
            tone: value === "COMPLICATED_SEVERE_ACUTE_MALNUTRITION" ? "danger" : "warning",
          }))}
          value={classification || null}
          onChange={setClassification}
        />
        {classification && (
          <p className="mt-1 text-xs text-muted-foreground" data-testid="imam-classification-echo">
            Admitting on {classificationLabel(classification)}.
          </p>
        )}
      </div>

      <fieldset className="mt-4 rounded-md border border-border p-3">
        <legend className="px-1 text-xs font-medium text-muted-foreground">
          Measurements at admission
        </legend>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-xs font-medium text-muted-foreground">
            Mid-upper-arm circumference (cm)
            <input
              type="number"
              step="0.1"
              inputMode="decimal"
              value={muac}
              onChange={(event) => setMuac(event.target.value)}
              data-testid="imam-enrol-muac"
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
              data-testid="imam-enrol-weight"
              className="mt-1 block min-h-[44px] w-full rounded-md border border-border bg-background px-3 text-sm text-foreground"
            />
          </label>
        </div>

        <div className="mt-3">
          <span id="imam-enrol-oedema-label" className="text-xs font-medium text-muted-foreground">
            Bilateral pitting oedema
          </span>
          <SegmentedControl
            aria-labelledby="imam-enrol-oedema-label"
            data-testid="imam-enrol-oedema"
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
            Oedema is severe acute malnutrition whatever the arm circumference says — a child with
            nutritional oedema can carry a deceptively normal weight.
          </p>
        </div>

        <div className="mt-3">
          <span id="imam-enrol-appetite-label" className="text-xs font-medium text-muted-foreground">
            Appetite test
          </span>
          <SegmentedControl
            aria-labelledby="imam-enrol-appetite-label"
            data-testid="imam-enrol-appetite"
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
          <p className="mt-1 text-xs text-muted-foreground">
            A failed appetite test means the child cannot be treated at home.
          </p>
        </div>
      </fieldset>

      {error && (
        <p
          className="mt-3 rounded-md border border-red-500 bg-red-50 p-2 text-xs text-red-900"
          role="alert"
          data-testid="imam-enrol-error"
        >
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={enrol.isPending}
        data-testid="imam-enrol-submit"
        className="mt-3 min-h-[44px] rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60"
      >
        {enrol.isPending ? "Enrolling…" : "Enrol"}
      </button>
    </form>
  );
}
