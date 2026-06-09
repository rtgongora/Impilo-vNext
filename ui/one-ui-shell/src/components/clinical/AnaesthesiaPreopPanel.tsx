"use client";

import { useMemo, useState } from "react";
import { CheckCircle2, Stethoscope } from "lucide-react";
import { SCORE_TOOL_LABELS, type AnaesthesiaScore, type ScoreSuggestion } from "@/lib/anaesthesia-scoring";
import { useProcedureEpisodeAction } from "@/hooks/queries/useProcedureEpisode";

type Props = {
  episodeId: string;
  procedureName: string;
  suggestions?: ScoreSuggestion[];
  recordedScores?: AnaesthesiaScore[];
  anaesthesiaDone: boolean;
};

export function AnaesthesiaPreopPanel({
  episodeId,
  procedureName,
  suggestions = [],
  recordedScores = [],
  anaesthesiaDone,
}: Props) {
  const actions = useProcedureEpisodeAction(episodeId);
  const [asaClass, setAsaClass] = useState("II");
  const [mallampati, setMallampati] = useState("II");
  const [cormack, setCormack] = useState("");
  const [notes, setNotes] = useState("");
  const [cleared, setCleared] = useState(true);
  const [selectedTools, setSelectedTools] = useState<string[]>(["ASA", "MALLAMPATI"]);

  const [stopBang, setStopBang] = useState({
    snoring: false, tired: false, observedApnea: false, pressure: false,
    bmiGreaterThan35: false, ageGreaterThan50: false, neckCircumference: false, genderMale: false,
  });
  const [apfel, setApfel] = useState({
    female: false, nonSmoker: false, historyPonv: false, postopOpioids: true,
  });
  const [rcri, setRcri] = useState({
    highRiskSurgery: false, ischemicHeartDisease: false, congestiveHeartFailure: false,
    cerebrovascularDisease: false, diabetesInsulin: false, renalInsufficiency: false,
  });

  const recommended = useMemo(
    () => suggestions.filter((s) => s.recommended).map((s) => s.score_type),
    [suggestions],
  );

  const toggleTool = (tool: string) => {
    setSelectedTools((prev) =>
      prev.includes(tool) ? prev.filter((t) => t !== tool) : [...prev, tool],
    );
  };

  const buildExtraScores = () => {
    const scores: Record<string, unknown>[] = [];
    if (selectedTools.includes("STOP_BANG")) {
      scores.push({ scoreType: "STOP_BANG", components: stopBang });
    }
    if (selectedTools.includes("APFEL")) {
      scores.push({ scoreType: "APFEL", components: apfel });
    }
    if (selectedTools.includes("RCRI")) {
      scores.push({ scoreType: "RCRI", components: rcri });
    }
    if (selectedTools.includes("CORMACK_LEHANE") && cormack) {
      scores.push({ scoreType: "CORMACK_LEHANE", components: { cormackLehaneGrade: cormack } });
    }
    return scores;
  };

  const anaesthesiaAssessment = episodeId && !anaesthesiaDone ? (
    <div className="space-y-4">
      <p className="text-xs text-gray-500">
        Procedure: <strong>{procedureName}</strong> — choose which scoring tools to apply. Suggested tools are highlighted.
      </p>

      <div className="flex flex-wrap gap-2">
        {Object.entries(SCORE_TOOL_LABELS)
          .filter(([k]) => ["ASA", "MALLAMPATI", "CORMACK_LEHANE", "STOP_BANG", "APFEL", "RCRI"].includes(k))
          .map(([key, label]) => {
            const isSuggested = recommended.includes(key);
            const active = selectedTools.includes(key);
            return (
              <button
                key={key}
                type="button"
                onClick={() => toggleTool(key)}
                className={`rounded-full px-3 py-1 text-xs border ${
                  active ? "bg-impilo-500 text-white border-impilo-500"
                    : isSuggested ? "border-amber-400 bg-amber-50 text-amber-900"
                    : "border-gray-200 text-gray-600"
                }`}
              >
                {label}{isSuggested ? " ★" : ""}
              </button>
            );
          })}
      </div>

      {suggestions.length > 0 && (
        <ul className="text-xs text-gray-600 space-y-1">
          {suggestions.filter((s) => s.recommended).map((s) => (
            <li key={s.score_type}>★ {s.reason}</li>
          ))}
        </ul>
      )}

      {selectedTools.includes("ASA") && (
        <label className="block text-sm">
          ASA class
          <select value={asaClass} onChange={(e) => setAsaClass(e.target.value)} className="mt-1 w-full rounded border px-2 py-1">
            {["I", "II", "III", "IV", "V"].map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
      )}

      {selectedTools.includes("MALLAMPATI") && (
        <label className="block text-sm">
          Mallampati class
          <select value={mallampati} onChange={(e) => setMallampati(e.target.value)} className="mt-1 w-full rounded border px-2 py-1">
            {["I", "II", "III", "IV"].map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
      )}

      {selectedTools.includes("CORMACK_LEHANE") && (
        <label className="block text-sm">
          Cormack-Lehane grade (if known / anticipated)
          <select value={cormack} onChange={(e) => setCormack(e.target.value)} className="mt-1 w-full rounded border px-2 py-1">
            <option value="">Not assessed pre-op</option>
            {["I", "II", "III", "IV"].map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
      )}

      {selectedTools.includes("STOP_BANG") && (
        <fieldset className="rounded border p-3 text-sm space-y-1">
          <legend className="px-1 text-xs font-medium">STOP-BANG components</legend>
          {Object.entries(stopBang).map(([k, v]) => (
            <label key={k} className="flex items-center gap-2">
              <input type="checkbox" checked={v} onChange={(e) => setStopBang({ ...stopBang, [k]: e.target.checked })} />
              <span className="text-xs">{k.replace(/([A-Z])/g, " $1")}</span>
            </label>
          ))}
        </fieldset>
      )}

      {selectedTools.includes("APFEL") && (
        <fieldset className="rounded border p-3 text-sm space-y-1">
          <legend className="px-1 text-xs font-medium">Apfel PONV risk factors</legend>
          {Object.entries(apfel).map(([k, v]) => (
            <label key={k} className="flex items-center gap-2">
              <input type="checkbox" checked={v} onChange={(e) => setApfel({ ...apfel, [k]: e.target.checked })} />
              <span className="text-xs">{k.replace(/([A-Z])/g, " $1")}</span>
            </label>
          ))}
        </fieldset>
      )}

      {selectedTools.includes("RCRI") && (
        <fieldset className="rounded border p-3 text-sm space-y-1">
          <legend className="px-1 text-xs font-medium">Revised Cardiac Risk Index</legend>
          {Object.entries(rcri).map(([k, v]) => (
            <label key={k} className="flex items-center gap-2">
              <input type="checkbox" checked={v} onChange={(e) => setRcri({ ...rcri, [k]: e.target.checked })} />
              <span className="text-xs">{k.replace(/([A-Z])/g, " $1")}</span>
            </label>
          ))}
        </fieldset>
      )}

      <label className="block text-sm">
        Anaesthetist notes
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Airway plan, induction agents, regional technique, concerns…"
          className="mt-1 w-full rounded border p-2 text-sm min-h-[72px]"
        />
      </label>

      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={cleared} onChange={(e) => setCleared(e.target.checked)} />
        Cleared for theatre
      </label>

      <button
        type="button"
        disabled={actions.submitPreop.isPending}
        onClick={() =>
          actions.submitPreop.mutate({
            assessmentType: "ANAESTHESIA",
            asaClass: selectedTools.includes("ASA") ? asaClass : undefined,
            mallampatiClass: selectedTools.includes("MALLAMPATI") ? mallampati : undefined,
            cormackLehaneGrade: cormack || undefined,
            anaesthetistNotes: notes || undefined,
            clearedForTheatre: cleared,
            assessedBy: "anaesthetist-web",
            scores: buildExtraScores(),
          })
        }
        className="rounded-lg bg-impilo-500 px-4 py-2 text-sm text-white disabled:opacity-50"
      >
        Submit anaesthesia assessment & scores
      </button>
    </div>
  ) : null;

  return (
    <div className="rounded-xl border p-4 space-y-3">
      <h3 className="font-medium flex items-center gap-2">
        <Stethoscope className="h-4 w-4 text-impilo-500" />
        Anaesthesia pre-op scoring
      </h3>

      {anaesthesiaDone ? (
        <p className="text-sm text-green-700 flex items-center gap-1">
          <CheckCircle2 className="h-4 w-4" /> Cleared for theatre
        </p>
      ) : (
        anaesthesiaAssessment
      )}

      {recordedScores.length > 0 && (
        <ul className="text-xs text-gray-600 space-y-1 border-t pt-2">
          {recordedScores.map((s) => (
            <li key={s.id}>
              <span className="font-medium">{SCORE_TOOL_LABELS[s.score_type] ?? s.score_type}</span>
              {s.total_score != null ? ` — ${s.total_score}` : ""}
              {s.risk_band ? ` (${s.risk_band})` : ""}: {s.interpretation}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
