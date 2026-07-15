"use client";

/**
 * Trauma survey panel (WU3) — structured ATLS primary (ABCDE) and secondary
 * (head-to-toe) surveys with per-section checklists + vitals, plus amendment of a
 * prior survey. Writes the already-proxied ED endpoint
 * /internal/v1/ed/visits/{visitId}/trauma/survey. Recorded surveys read back from the
 * visit detail; draft / recorded / amended states are visually distinguished. No mocks.
 */

import { useMemo, useState } from "react";
import { FileEdit, Loader2, Stethoscope } from "lucide-react";
import { useEdVisitActions } from "@/hooks/queries/useEdVisit";

type SurveyType = "PRIMARY" | "SECONDARY";

const PRIMARY_SECTIONS: Record<string, { label: string; items: string[]; vitals: string[] }> = {
  A: { label: "A · Airway", items: ["Airway patent", "C-spine immobilised", "Definitive airway"], vitals: [] },
  B: { label: "B · Breathing", items: ["Bilateral air entry", "No tension pneumothorax", "Chest drain sited"], vitals: ["rr", "spo2"] },
  C: { label: "C · Circulation", items: ["Pulses present", "Haemorrhage controlled", "IV access x2", "Blood/fluids started"], vitals: ["hr", "sbp"] },
  D: { label: "D · Disability", items: ["Pupils equal + reactive", "No lateralising signs", "Glucose checked"], vitals: ["gcs"] },
  E: { label: "E · Exposure", items: ["Fully exposed", "Temperature managed", "Log-roll performed"], vitals: ["temp"] },
};

const SECONDARY_ITEMS = [
  "Head & face", "Neck", "Chest", "Abdomen", "Pelvis", "Perineum / PR", "Extremities", "Back / spine", "Neuro exam",
];

const VITAL_META: Record<string, { label: string; unit: string }> = {
  hr: { label: "HR", unit: "bpm" },
  sbp: { label: "SBP", unit: "mmHg" },
  rr: { label: "RR", unit: "/min" },
  spo2: { label: "SpO₂", unit: "%" },
  temp: { label: "Temp", unit: "°C" },
  gcs: { label: "GCS", unit: "/15" },
};

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

type SurveyRow = Record<string, unknown>;

export function TraumaSurveyPanel({
  visitId,
  traumaId,
  surveys,
}: {
  visitId: string;
  traumaId?: string;
  surveys?: SurveyRow[];
}) {
  const actions = useEdVisitActions(visitId);
  const [surveyType, setSurveyType] = useState<SurveyType>("PRIMARY");
  const [sectionKey, setSectionKey] = useState("A");
  const [checklist, setChecklist] = useState<Record<string, boolean>>({});
  const [vitals, setVitals] = useState<Record<string, string>>({});
  const [notes, setNotes] = useState("");
  const [amendsId, setAmendsId] = useState<string | undefined>();

  const section = PRIMARY_SECTIONS[sectionKey];
  const items = surveyType === "PRIMARY" ? section.items : SECONDARY_ITEMS;
  const vitalKeys = surveyType === "PRIMARY" ? section.vitals : [];

  const recorded = useMemo(() => surveys ?? [], [surveys]);

  const reset = () => {
    setChecklist({});
    setVitals({});
    setNotes("");
    setAmendsId(undefined);
  };

  const submit = () => {
    const numericVitals: Record<string, number> = {};
    for (const [k, v] of Object.entries(vitals)) {
      const n = Number(v);
      if (v !== "" && !Number.isNaN(n)) numericVitals[k] = n;
    }
    actions.recordTraumaSurvey.mutate(
      {
        traumaId,
        surveyType,
        sectionKey: surveyType === "PRIMARY" ? sectionKey : "SECONDARY",
        checklist,
        vitals: numericVitals,
        notes: notes.trim() || undefined,
        amendsSurveyId: amendsId,
      },
      { onSuccess: reset },
    );
  };

  const startAmend = (row: SurveyRow) => {
    const id = str(row.id ?? row.survey_id);
    setAmendsId(id);
    const t = (str(row.survey_type ?? row.surveyType).toUpperCase() as SurveyType) || "PRIMARY";
    setSurveyType(t === "SECONDARY" ? "SECONDARY" : "PRIMARY");
    if (t !== "SECONDARY") setSectionKey(str(row.section_key ?? row.sectionKey) || "A");
    const cl = (row.checklist ?? {}) as Record<string, boolean>;
    setChecklist(typeof cl === "object" && cl ? { ...cl } : {});
    setNotes(str(row.notes));
  };

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm" data-testid="trauma-survey-panel">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Stethoscope className="h-4 w-4 text-primary" /> Trauma survey
      </h3>

      {/* Recorded surveys */}
      {recorded.length > 0 ? (
        <ul className="mt-3 space-y-1.5">
          {recorded.map((s, i) => {
            const st = str(s.status).toUpperCase();
            const amended = !!(s.amends_survey_id ?? s.amendsSurveyId) || st === "AMENDED";
            return (
              <li key={str(s.id ?? s.survey_id) || i} className="flex items-center justify-between gap-2 rounded-lg border border-border bg-background px-3 py-1.5 text-xs">
                <span className="flex items-center gap-2">
                  <span className="font-medium text-foreground">
                    {str(s.survey_type ?? s.surveyType) || "PRIMARY"} · {str(s.section_key ?? s.sectionKey) || "—"}
                  </span>
                  <span className={`rounded-full px-1.5 py-0.5 font-medium ${amended ? "bg-blue-100 text-blue-800" : st === "SIGNED" ? "bg-green-100 text-green-800" : "bg-neutral-100 text-muted-foreground"}`}>
                    {amended ? "AMENDED" : st || "RECORDED"}
                  </span>
                </span>
                <button type="button" onClick={() => startAmend(s)} className="inline-flex items-center gap-1 text-primary hover:underline">
                  <FileEdit className="h-3 w-3" /> Amend
                </button>
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="mt-3 rounded-lg border border-dashed border-border bg-background px-3 py-3 text-center text-xs text-muted-foreground">
          No surveys recorded yet. Complete the primary (ABCDE) survey first, then the secondary head-to-toe.
        </p>
      )}

      {/* Entry form */}
      <div className="mt-4 space-y-3">
        {amendsId && (
          <div className="flex items-center justify-between rounded-lg bg-blue-50 px-3 py-1.5 text-xs text-blue-900">
            Amending an earlier survey.
            <button type="button" onClick={reset} className="underline">Cancel amend</button>
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          {(["PRIMARY", "SECONDARY"] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setSurveyType(t)}
              className={`rounded-full px-3 py-1 text-sm ${surveyType === t ? "bg-neutral-900 text-white" : "bg-neutral-100"}`}
            >
              {t === "PRIMARY" ? "Primary (ABCDE)" : "Secondary (head-to-toe)"}
            </button>
          ))}
          {surveyType === "PRIMARY" &&
            Object.keys(PRIMARY_SECTIONS).map((k) => (
              <button
                key={k}
                type="button"
                onClick={() => setSectionKey(k)}
                className={`rounded-lg px-2.5 py-1 text-sm font-medium ${sectionKey === k ? "ring-2 ring-primary" : "bg-neutral-100"}`}
              >
                {k}
              </button>
            ))}
        </div>

        <div className="grid gap-1.5 sm:grid-cols-2">
          {items.map((item) => (
            <label key={item} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={!!checklist[item]}
                onChange={(e) => setChecklist((prev) => ({ ...prev, [item]: e.target.checked }))}
                className="h-4 w-4"
              />
              {item}
            </label>
          ))}
        </div>

        {vitalKeys.length > 0 && (
          <div className="flex flex-wrap gap-3">
            {vitalKeys.map((vk) => (
              <label key={vk} className="text-xs font-medium text-muted-foreground">
                {VITAL_META[vk]?.label ?? vk} ({VITAL_META[vk]?.unit})
                <input
                  type="number"
                  value={vitals[vk] ?? ""}
                  onChange={(e) => setVitals((prev) => ({ ...prev, [vk]: e.target.value }))}
                  className="ml-2 w-20 rounded border px-2 py-1 text-sm"
                />
              </label>
            ))}
          </div>
        )}

        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Findings / notes"
          className="w-full rounded-lg border border-border p-2 text-sm"
          rows={2}
        />

        <button
          type="button"
          disabled={actions.recordTraumaSurvey.isPending}
          onClick={submit}
          className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
        >
          {actions.recordTraumaSurvey.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          {amendsId ? "Save amendment" : `Record ${surveyType === "PRIMARY" ? `primary ${sectionKey}` : "secondary"} survey`}
        </button>
        {actions.recordTraumaSurvey.isError && (
          <p className="text-xs text-danger">Could not record the survey — retry.</p>
        )}
      </div>
    </section>
  );
}
