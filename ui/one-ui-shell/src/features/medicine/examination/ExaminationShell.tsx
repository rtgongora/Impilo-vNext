"use client";

import { useState } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import { BodyMapField } from "@/features/body-map/BodyMapField";
import type { MarkedRegionFinding } from "@/features/body-map/core/types";
import {
  useExamination,
  useExaminationVocabulary,
  useExaminations,
  useRecordExamination,
  type ExaminationState,
} from "@/hooks/queries/useExaminations";
import {
  REGION_GRAPHICS,
  findingToMarks,
  marksToGraphicFields,
} from "./v113-graphics";

/**
 * The structured examination form and read (brief.md §7).
 *
 * <p>The design decision the whole surface turns on: <strong>no region defaults to NORMAL, and no
 * region defaults to anything.</strong> A form pre-set to "normal" produces a full normal
 * examination from a clinician who touched three fields, and it does it silently. Every region
 * starts unset, and a region left unset is simply not submitted — which the record distinguishes
 * from NOT_EXAMINED, where the examiner looked at the list and chose.</p>
 *
 * <p>The eleven V113 graphics ride the same finding row as {@code graphic}/{@code site}/
 * {@code laterality}. Diagrams are body-map instruments keyed by those codes; marks are collapsed
 * to the three columns on submit and never stored as a parallel JSON shape.</p>
 */

const STATE_LABEL: Record<ExaminationState, string> = {
  NORMAL: "Normal",
  ABNORMAL: "Abnormal",
  NOT_EXAMINED: "Not examined",
  UNABLE_TO_EXAMINE: "Unable to examine",
  DEFERRED: "Deferred",
  UNCERTAIN: "Uncertain",
};

const REGION_LABEL = (region: string) =>
  region.charAt(0) + region.slice(1).toLowerCase().replace(/_/g, " ");

const GRAPHIC_LABEL = (graphic: string) =>
  graphic.charAt(0) + graphic.slice(1).toLowerCase().replace(/_/g, " ");

export function ExaminationShell({ patientId, journeyId }: { patientId: string; journeyId?: string }) {
  const vocabulary = useExaminationVocabulary();
  const history = useExaminations(patientId);
  const [openId, setOpenId] = useState<string | null>(null);
  const detail = useExamination(openId);
  const record = useRecordExamination(patientId);

  const [states, setStates] = useState<Record<string, ExaminationState | "">>({});
  const [details, setDetails] = useState<Record<string, string>>({});
  /** Selected V113 graphic per examination region (defaults to the region's first option). */
  const [graphics, setGraphics] = useState<Record<string, string>>({});
  /** Interactive marks keyed by examination region — collapsed to site/laterality on submit. */
  const [marksByRegion, setMarksByRegion] = useState<Record<string, MarkedRegionFinding[]>>({});

  const vocab = vocabulary.data?.data;
  const needsDetail = (s: ExaminationState | "") =>
    !!s && (vocab?.states_needing_detail ?? []).includes(s as ExaminationState);
  const wasExamined = (s: ExaminationState | "") =>
    !!s && (vocab?.examined_states ?? []).includes(s as ExaminationState);

  const graphicFor = (region: string): string | undefined => {
    const options = REGION_GRAPHICS[region];
    if (!options?.length) return undefined;
    return graphics[region] ?? options[0];
  };

  const submit = () => {
    const findings = Object.entries(states)
      // A region left unset is not submitted. It is not NORMAL, and it is not NOT_EXAMINED either —
      // the examiner never reached it, and the record says so by its absence.
      .filter(([, s]) => s !== "")
      .map(([region, s]) => {
        const graphic = graphicFor(region);
        const marks = marksByRegion[region] ?? [];
        const graphicFields =
          graphic && marks.length > 0 ? marksToGraphicFields(graphic, marks) : {};
        return {
          region,
          state: s as ExaminationState,
          detail: details[region]?.trim() || undefined,
          ...graphicFields,
        };
      });
    if (findings.length === 0) return;
    record.mutate({
      subject_cpid: patientId,
      journey_id: journeyId,
      findings,
    });
  };

  const incomplete = Object.entries(states).some(
    ([region, s]) => needsDetail(s) && !details[region]?.trim(),
  );

  if (vocabulary.isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground" data-testid="examination-loading">
        <Loader2 className="w-4 h-4 animate-spin" /> Loading the examination vocabulary…
      </div>
    );
  }

  if (vocabulary.isError || !vocab) {
    // Rendering the form with a guessed set of states is how a five-state form becomes a two-state
    // one, so it is not offered at all.
    return (
      <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-red-50 p-4" data-testid="examination-vocab-failed">
        <AlertTriangle className="w-4 h-4 text-danger mt-0.5" />
        <p className="text-sm text-danger">
          The examination vocabulary could not be read, so the form is not shown. It is{" "}
          <strong>not</strong> offered with a guessed set of states.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="examination">
      <div className="rounded-lg border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
        {vocab.not_examined_is_not_normal} Nothing is pre-selected: a region you do not touch is not
        recorded at all.
      </div>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">Record an examination</h3>
        <div className="grid gap-2">
          {vocab.regions.map((region) => {
            const options = REGION_GRAPHICS[region];
            const selectedGraphic = graphicFor(region);
            const showDiagram = wasExamined(states[region] ?? "") && !!selectedGraphic;

            return (
              <div key={region} className="space-y-2 rounded-md border border-transparent p-1" data-testid={`region-${region}`}>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="w-48 text-sm">{REGION_LABEL(region)}</span>
                  <select
                    className="rounded border border-border px-2 py-1 text-sm"
                    value={states[region] ?? ""}
                    onChange={(e) =>
                      setStates((s) => ({ ...s, [region]: e.target.value as ExaminationState | "" }))
                    }
                    data-testid={`state-${region}`}
                  >
                    <option value="">Not recorded</option>
                    {vocab.states.map((s) => (
                      <option key={s} value={s}>
                        {STATE_LABEL[s] ?? s}
                      </option>
                    ))}
                  </select>
                  {needsDetail(states[region] ?? "") && (
                    <input
                      type="text"
                      className="flex-1 min-w-48 rounded border border-border px-2 py-1 text-sm"
                      placeholder={
                        (vocab.examined_states ?? []).includes(states[region] as ExaminationState)
                          ? "What did you find?"
                          : "Why not?"
                      }
                      value={details[region] ?? ""}
                      onChange={(e) => setDetails((d) => ({ ...d, [region]: e.target.value }))}
                      data-testid={`detail-${region}`}
                    />
                  )}
                </div>

                {showDiagram && options && (
                  <div className="ml-0 sm:ml-48 space-y-2" data-testid={`graphic-panel-${region}`}>
                    {options.length > 1 && (
                      <select
                        className="rounded border border-border px-2 py-1 text-sm"
                        value={selectedGraphic}
                        onChange={(e) => {
                          const next = e.target.value;
                          setGraphics((g) => ({ ...g, [region]: next }));
                          // Switching diagram clears pins that belong to the previous map.
                          setMarksByRegion((m) => ({ ...m, [region]: [] }));
                        }}
                        data-testid={`graphic-select-${region}`}
                      >
                        {options.map((g) => (
                          <option key={g} value={g}>
                            {GRAPHIC_LABEL(g)}
                          </option>
                        ))}
                      </select>
                    )}
                    <BodyMapField
                      instrumentId={selectedGraphic!}
                      value={marksByRegion[region] ?? []}
                      onChange={(findings) =>
                        setMarksByRegion((m) => ({ ...m, [region]: findings }))
                      }
                      data-testid={`body-map-${region}`}
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {incomplete && (
          <p className="text-sm text-warning-foreground" data-testid="examination-detail-missing">
            Every abnormal, uncertain, deferred or unable-to-examine region needs a line of detail
            before this can be saved.
          </p>
        )}

        <button
          type="button"
          onClick={submit}
          disabled={record.isPending || incomplete}
          className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
          data-testid="save-examination"
        >
          {record.isPending ? <Loader2 className="w-4 h-4 animate-spin inline" /> : "Save examination"}
        </button>

        {record.isError && (
          <p className="text-sm text-danger" data-testid="examination-save-failed">
            The examination was <strong>not</strong> saved. Nothing has been recorded.
          </p>
        )}
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">Previous examinations</h3>
        {history.isError ? (
          <p className="text-sm text-danger" data-testid="examination-history-failed">
            Previous examinations could not be read. This is <strong>not</strong> a statement that
            the patient has never been examined.
          </p>
        ) : (history.data?.data ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="examination-history-empty">
            No examinations recorded. This list was read.
          </p>
        ) : (
          <ul className="space-y-1">
            {(history.data?.data ?? []).map((e) => (
              <li key={e.examination_id}>
                <button
                  type="button"
                  className="text-sm text-primary hover:underline"
                  onClick={() => setOpenId(e.examination_id)}
                  data-testid={`open-${e.examination_id}`}
                >
                  {e.examined_at} · {e.examined_by}
                </button>
              </li>
            ))}
          </ul>
        )}

        {detail.data?.data && (
          <div className="rounded-lg border border-border bg-card p-4 space-y-2" data-testid="examination-detail">
            <ul className="space-y-1">
              {detail.data.data.findings.map((f) => (
                <li key={f.region} className="space-y-2 text-sm" data-testid={`finding-${f.region}`}>
                  <div>
                    <span className="font-medium">{REGION_LABEL(f.region)}</span>{" "}
                    <span className={f.was_examined ? "" : "text-warning-foreground"}>
                      {STATE_LABEL[f.state] ?? f.state}
                    </span>
                    {f.detail ? ` — ${f.detail}` : ""}
                    {f.graphic ? (
                      <span className="text-muted-foreground" data-testid={`finding-graphic-${f.region}`}>
                        {" "}
                        · {GRAPHIC_LABEL(f.graphic)}
                        {f.site ? ` @ ${f.site}` : ""}
                        {f.laterality ? ` (${f.laterality.toLowerCase()})` : ""}
                      </span>
                    ) : null}
                  </div>
                  {f.graphic && f.site ? (
                    <BodyMapField
                      instrumentId={f.graphic}
                      value={findingToMarks(f)}
                      onChange={() => undefined}
                      readOnly
                      data-testid={`body-map-read-${f.region}`}
                    />
                  ) : null}
                </li>
              ))}
            </ul>
            {/* Coverage is the read-side half of the framework: what was NOT looked at has no row
                of its own, so it is invisible unless it is said out loud. */}
            <p className="text-sm text-warning-foreground" data-testid="examination-coverage">
              {detail.data.data.coverage.note}
            </p>
            {detail.data.data.coverage.never_considered.length > 0 && (
              <p className="text-xs text-muted-foreground" data-testid="examination-never-considered">
                Never considered: {detail.data.data.coverage.never_considered.map(REGION_LABEL).join(", ")}.
              </p>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
