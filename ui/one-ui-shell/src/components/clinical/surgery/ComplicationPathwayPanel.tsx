"use client";

/**
 * Complication pathway panel (SB-1): recognise → grade → update → disclose → close.
 * A failed list must never render as "no complications".
 */

import { useState } from "react";
import {
  useCloseComplication,
  useComplicationPathways,
  useDiscloseComplication,
  useGradeComplication,
  useRecogniseComplication,
  useUpdateComplication,
  type ClavienDindoGrade,
  type ComplicationType,
} from "@/hooks/queries/useSurgeryEpisodes";

const COMPLICATION_TYPES: ComplicationType[] = [
  "SURGICAL_SITE_INFECTION",
  "BLEEDING",
  "ANASTOMOTIC_LEAK",
  "DEEP_VEIN_THROMBOSIS",
  "PULMONARY_EMBOLISM",
  "WOUND_DEHISCENCE",
  "ORGAN_INJURY",
  "NERVE_INJURY",
  "ANAESTHESIA_COMPLICATION",
  "TRANSFUSION_REACTION",
  "DEVICE_MALFUNCTION",
  "RETAINED_FOREIGN_OBJECT",
  "WRONG_SITE_PROCEDURE",
  "READMISSION",
  "UNPLANNED_RETURN_TO_THEATRE",
  "SEPSIS",
  "RENAL_INJURY",
  "CARDIAC_EVENT",
  "DEATH",
];

const GRADES: ClavienDindoGrade[] = ["I", "II", "IIIA", "IIIB", "IVA", "IVB", "V"];

export function ComplicationPathwayPanel({ episodeId }: { episodeId: string }) {
  const listQ = useComplicationPathways(episodeId);
  const recognise = useRecogniseComplication(episodeId);
  const grade = useGradeComplication(episodeId);
  const update = useUpdateComplication(episodeId);
  const disclose = useDiscloseComplication(episodeId);
  const close = useCloseComplication(episodeId);

  const [type, setType] = useState<ComplicationType | "">("");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [gradeCode, setGradeCode] = useState<ClavienDindoGrade | "">("");
  const [ownedBy, setOwnedBy] = useState("");
  const [investigation, setInvestigation] = useState("");
  const [treatment, setTreatment] = useState("");
  const [disclosureNotes, setDisclosureNotes] = useState("");
  const [outcome, setOutcome] = useState("");

  const writeFailed =
    recognise.isError || grade.isError || update.isError || disclose.isError || close.isError;

  return (
    <div
      className="mt-4 rounded-lg border border-gray-100 p-3"
      data-testid="surgery-complications-panel"
    >
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">
        Complication pathways (course of care)
      </h4>

      {listQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading pathways…</p>
      ) : listQ.isError ? (
        <p
          className="mt-2 text-sm text-danger"
          role="alert"
          data-testid="surgery-complications-unavailable"
        >
          Could not read complication pathways. This is not the same as there being none.
        </p>
      ) : listQ.data && listQ.data.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-complications-empty">
          No complication pathways recognised for this episode.
        </p>
      ) : (
        <ul className="mt-2 space-y-2" data-testid="surgery-complications-list">
          {listQ.data?.map((p) => (
            <li
              key={p.id}
              className={`rounded border px-2 py-1.5 text-sm ${
                activeId === p.id ? "border-indigo-200 bg-indigo-50/40" : "border-gray-100"
              }`}
            >
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium">{p.complicationType}</span>
                <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gray-600">
                  {p.status}
                </span>
                {p.gradeCode && (
                  <span className="text-xs text-muted-foreground">CD {p.gradeCode}</span>
                )}
                {p.ownedBy && (
                  <span className="text-xs text-muted-foreground">owned by {p.ownedBy}</span>
                )}
                {p.disclosedAt && (
                  <span className="text-xs text-muted-foreground">disclosed</span>
                )}
                {p.status === "OPEN" && (
                  <button
                    type="button"
                    onClick={() => setActiveId(p.id)}
                    className="rounded-md border border-gray-200 px-2 py-0.5 text-xs"
                    data-testid="surgery-complication-select"
                  >
                    Work pathway
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Recognise</span>
          <select
            value={type}
            onChange={(e) => setType(e.target.value as ComplicationType | "")}
            className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-complication-type"
          >
            <option value="">Choose type…</option>
            {COMPLICATION_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          disabled={!type || recognise.isPending}
          onClick={() =>
            recognise.mutate(
              { complicationType: type as ComplicationType },
              {
                onSuccess: (created) => {
                  setType("");
                  setActiveId(created.id);
                },
              },
            )
          }
          className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
          data-testid="surgery-complication-recognise"
        >
          {recognise.isPending ? "Recognising…" : "Recognise"}
        </button>
      </div>

      {activeId && (
        <div className="mt-3 space-y-2 rounded border border-gray-100 p-2" data-testid="surgery-complication-work">
          <p className="text-xs text-muted-foreground">Working pathway {activeId}</p>
          <div className="flex flex-wrap items-end gap-2">
            <label className="block text-xs">
              <span className="font-semibold uppercase text-muted-foreground">Grade (CD)</span>
              <select
                value={gradeCode}
                onChange={(e) => setGradeCode(e.target.value as ClavienDindoGrade | "")}
                className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                data-testid="surgery-complication-grade"
              >
                <option value="">Grade…</option>
                {GRADES.map((g) => (
                  <option key={g} value={g}>
                    {g}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              disabled={!gradeCode || grade.isPending}
              onClick={() =>
                grade.mutate({ pathwayId: activeId, gradeCode: gradeCode as ClavienDindoGrade })
              }
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
              data-testid="surgery-complication-grade-submit"
            >
              Grade
            </button>
          </div>
          <div className="flex flex-wrap items-end gap-2">
            <label className="block text-xs">
              <span className="font-semibold uppercase text-muted-foreground">Owned by</span>
              <input
                type="text"
                value={ownedBy}
                onChange={(e) => setOwnedBy(e.target.value)}
                className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                data-testid="surgery-complication-owned-by"
              />
            </label>
            <label className="block flex-1 text-xs min-w-[120px]">
              <span className="font-semibold uppercase text-muted-foreground">Investigation</span>
              <input
                type="text"
                value={investigation}
                onChange={(e) => setInvestigation(e.target.value)}
                className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                data-testid="surgery-complication-investigation"
              />
            </label>
            <label className="block flex-1 text-xs min-w-[120px]">
              <span className="font-semibold uppercase text-muted-foreground">Treatment</span>
              <input
                type="text"
                value={treatment}
                onChange={(e) => setTreatment(e.target.value)}
                className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                data-testid="surgery-complication-treatment"
              />
            </label>
            <button
              type="button"
              disabled={update.isPending}
              onClick={() =>
                update.mutate({
                  pathwayId: activeId,
                  ownedBy: ownedBy.trim() || null,
                  investigation: investigation.trim() || null,
                  treatment: treatment.trim() || null,
                })
              }
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
              data-testid="surgery-complication-update"
            >
              Update
            </button>
          </div>
          <div className="flex flex-wrap items-end gap-2">
            <label className="block flex-1 text-xs min-w-[160px]">
              <span className="font-semibold uppercase text-muted-foreground">Disclosure notes</span>
              <input
                type="text"
                value={disclosureNotes}
                onChange={(e) => setDisclosureNotes(e.target.value)}
                className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                data-testid="surgery-complication-disclosure-notes"
              />
            </label>
            <button
              type="button"
              disabled={disclose.isPending}
              onClick={() =>
                disclose.mutate({
                  pathwayId: activeId,
                  disclosureNotes: disclosureNotes.trim() || null,
                })
              }
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
              data-testid="surgery-complication-disclose"
            >
              Disclose
            </button>
          </div>
          <div className="flex flex-wrap items-end gap-2">
            <label className="block flex-1 text-xs min-w-[160px]">
              <span className="font-semibold uppercase text-muted-foreground">Outcome (required)</span>
              <input
                type="text"
                value={outcome}
                onChange={(e) => setOutcome(e.target.value)}
                className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                data-testid="surgery-complication-outcome"
              />
            </label>
            <button
              type="button"
              disabled={!outcome.trim() || close.isPending}
              onClick={() => close.mutate({ pathwayId: activeId, outcome: outcome.trim() })}
              className="rounded-md border border-rose-200 px-3 py-1.5 text-sm text-rose-800 disabled:opacity-50"
              data-testid="surgery-complication-close"
            >
              Close pathway
            </button>
          </div>
        </div>
      )}

      {writeFailed && (
        <p
          className="mt-2 text-sm text-danger"
          role="alert"
          data-testid="surgery-complications-write-failed"
        >
          That change was NOT saved — the pathway is unchanged.
        </p>
      )}
    </div>
  );
}
