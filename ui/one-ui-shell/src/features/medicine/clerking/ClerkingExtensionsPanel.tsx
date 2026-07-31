"use client";

import { useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import {
  useCognitionMoodScreens,
  useGeneticRisks,
  useInfectionExposures,
  usePresentingConcerns,
  usePriorIcuHistory,
  useRecordCognitionMood,
  useRecordGeneticRisk,
  useRecordInfectionExposure,
  useRecordPresentingConcern,
  useRecordPriorIcu,
  useRecordSafeguarding,
  useRecordSystemsReview,
  useSafeguardingDisclosures,
  useSystemsReviews,
} from "@/hooks/queries/useClerkingExtensions";
import { useEncounters } from "@/hooks/queries/useEncounters";

const ROS_SYSTEMS = [
  "CONSTITUTIONAL",
  "CARDIOVASCULAR",
  "RESPIRATORY",
  "GI",
  "GU",
  "NEURO",
  "MSK",
  "SKIN",
  "PSYCH",
] as const;

function Block({
  title,
  unavailable,
  children,
}: {
  title: string;
  unavailable?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4 space-y-2">
      <h3 className="text-sm font-medium">{title}</h3>
      {unavailable ? (
        <p className="text-sm text-warning">
          {title} unavailable — this is not a record that there is none.
        </p>
      ) : (
        children
      )}
    </div>
  );
}

export function ClerkingExtensionsPanel({ patientId }: { patientId: string }) {
  const encounters = useEncounters(patientId);
  const active = (encounters.data?.data ?? []).find((e) => e.attributes.isOpen);
  const encounterId = active?.id ?? "";

  const presenting = usePresentingConcerns(patientId);
  const ros = useSystemsReviews(patientId);
  const icu = usePriorIcuHistory(patientId);
  const exposure = useInfectionExposures(patientId);
  const cognition = useCognitionMoodScreens(patientId);
  const genetic = useGeneticRisks(patientId);
  const safeguarding = useSafeguardingDisclosures(patientId);

  const recordPresenting = useRecordPresentingConcern(patientId);
  const recordRos = useRecordSystemsReview(patientId);
  const recordIcu = useRecordPriorIcu(patientId);
  const recordExposure = useRecordInfectionExposure(patientId);
  const recordCognition = useRecordCognitionMood(patientId);
  const recordGenetic = useRecordGeneticRisk(patientId);
  const recordSafeguarding = useRecordSafeguarding(patientId);

  const [concern, setConcern] = useState("");
  const [timeline, setTimeline] = useState("");
  const [rosNotes, setRosNotes] = useState("");
  const [icuYear, setIcuYear] = useState("");
  const [icuReason, setIcuReason] = useState("");
  const [exposureType, setExposureType] = useState("");
  const [instrument, setInstrument] = useState("PHQ2");
  const [score, setScore] = useState("");
  const [scoreAbsent, setScoreAbsent] = useState("");
  const [riskFactor, setRiskFactor] = useState("");
  const [safeguardText, setSafeguardText] = useState("");

  return (
    <div className="space-y-4" data-testid="clerking-extensions-panel">
      <p className="text-xs text-muted-foreground">
        NEW_PCT structured clerking (V117). Systems review replaces static clerkingTemplates as SoR.
        Safeguarding is confidential — not social history.{" "}
        <Link href={`/ehr/${patientId}/social-history`} className="text-primary">
          Social history
        </Link>{" "}
        remains separate.
        {!encounterId && (
          <span className="block mt-1 text-warning">
            Open an encounter for visit-anchored items (presenting concern, ROS, exposure, screens,
            safeguarding). Prior ICU and genetic risk remain writable without a visit.
          </span>
        )}
      </p>

      <Block title="Presenting concern + timeline" unavailable={presenting.isError}>
        <ul className="text-sm space-y-1">
          {(presenting.data?.data ?? []).slice(0, 3).map((row) => (
            <li key={String(row.concern_id)}>{String(row.concern_text)}</li>
          ))}
        </ul>
        <textarea
          value={concern}
          onChange={(e) => setConcern(e.target.value)}
          rows={2}
          placeholder="Presenting concern"
          className="w-full rounded border border-border px-2 py-1 text-sm"
        />
        <input
          value={timeline}
          onChange={(e) => setTimeline(e.target.value)}
          placeholder="Symptom timeline (free text / JSON)"
          className="w-full rounded border border-border px-2 py-1 text-sm"
        />
        <button
          type="button"
          disabled={!concern.trim() || !encounterId || recordPresenting.isPending}
          onClick={() =>
            recordPresenting.mutate(
              {
                encounter_id: encounterId,
                concern_text: concern.trim(),
                timeline_json: timeline.trim() || undefined,
                source: "AMBULATORY",
              },
              { onSuccess: () => { setConcern(""); setTimeline(""); } }
            )
          }
          className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
        >
          {recordPresenting.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : "Save concern"}
        </button>
      </Block>

      <Block title="Systems review (ROS)" unavailable={ros.isError}>
        <p className="text-xs text-muted-foreground">Systems: {ROS_SYSTEMS.join(", ")}</p>
        <textarea
          value={rosNotes}
          onChange={(e) => setRosNotes(e.target.value)}
          rows={3}
          placeholder='JSON map of system → finding, e.g. {"CARDIOVASCULAR":"denies chest pain"}'
          className="w-full rounded border border-border px-2 py-1 text-sm font-mono"
        />
        <button
          type="button"
          disabled={!rosNotes.trim() || !encounterId || recordRos.isPending}
          onClick={() =>
            recordRos.mutate(
              { encounter_id: encounterId, systems_json: rosNotes.trim() },
              { onSuccess: () => setRosNotes("") }
            )
          }
          className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
        >
          Save ROS
        </button>
      </Block>

      <Block title="Prior ICU history" unavailable={icu.isError}>
        <ul className="text-sm space-y-1">
          {(icu.data?.data ?? []).slice(0, 3).map((row) => (
            <li key={String(row.entry_id)}>
              {row.approx_year ? `${row.approx_year}: ` : ""}
              {String(row.reason ?? row.facility_note ?? "ICU admission")}
            </li>
          ))}
        </ul>
        <div className="flex gap-2">
          <input
            value={icuYear}
            onChange={(e) => setIcuYear(e.target.value)}
            placeholder="Year"
            className="w-24 rounded border border-border px-2 py-1 text-sm"
          />
          <input
            value={icuReason}
            onChange={(e) => setIcuReason(e.target.value)}
            placeholder="Reason"
            className="flex-1 rounded border border-border px-2 py-1 text-sm"
          />
          <button
            type="button"
            disabled={!icuReason.trim() || recordIcu.isPending}
            onClick={() =>
              recordIcu.mutate(
                {
                  approx_year: icuYear.trim() || undefined,
                  reason: icuReason.trim(),
                },
                { onSuccess: () => { setIcuYear(""); setIcuReason(""); } }
              )
            }
            className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
          >
            Save
          </button>
        </div>
      </Block>

      <Block title="Infection exposure (clinical)" unavailable={exposure.isError}>
        <input
          value={exposureType}
          onChange={(e) => setExposureType(e.target.value)}
          placeholder="Exposure type"
          className="w-full rounded border border-border px-2 py-1 text-sm"
        />
        <button
          type="button"
          disabled={!exposureType.trim() || !encounterId || recordExposure.isPending}
          onClick={() =>
            recordExposure.mutate(
              { encounter_id: encounterId, exposure_type: exposureType.trim() },
              { onSuccess: () => setExposureType("") }
            )
          }
          className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
        >
          Save exposure
        </button>
      </Block>

      <Block title="Cognition / mood screen" unavailable={cognition.isError}>
        <p className="text-xs text-muted-foreground">
          Score XOR absent reason — never invent a zero. Null score means unassessed.
        </p>
        <div className="flex flex-wrap gap-2">
          <select
            value={instrument}
            onChange={(e) => setInstrument(e.target.value)}
            className="rounded border border-border px-2 py-1 text-sm"
          >
            <option value="PHQ2">PHQ-2</option>
            <option value="GAD2">GAD-2</option>
            <option value="MINI_COG">Mini-Cog</option>
          </select>
          <input
            value={score}
            onChange={(e) => setScore(e.target.value)}
            placeholder="Score"
            className="w-24 rounded border border-border px-2 py-1 text-sm"
          />
          <input
            value={scoreAbsent}
            onChange={(e) => setScoreAbsent(e.target.value)}
            placeholder="Absent reason"
            className="flex-1 rounded border border-border px-2 py-1 text-sm"
          />
          <button
            type="button"
            disabled={!encounterId || recordCognition.isPending || (!!score && !!scoreAbsent)}
            onClick={() =>
              recordCognition.mutate(
                {
                  encounter_id: encounterId,
                  instrument,
                  score: score.trim() || undefined,
                  score_absent_reason: scoreAbsent.trim() || undefined,
                },
                { onSuccess: () => { setScore(""); setScoreAbsent(""); } }
              )
            }
            className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
          >
            Save screen
          </button>
        </div>
      </Block>

      <Block title="Genetic risk (general adult)" unavailable={genetic.isError}>
        <input
          value={riskFactor}
          onChange={(e) => setRiskFactor(e.target.value)}
          placeholder="Risk factor"
          className="w-full rounded border border-border px-2 py-1 text-sm"
        />
        <button
          type="button"
          disabled={!riskFactor.trim() || recordGenetic.isPending}
          onClick={() =>
            recordGenetic.mutate(
              { risk_factor: riskFactor.trim() },
              { onSuccess: () => setRiskFactor("") }
            )
          }
          className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
        >
          Save genetic risk
        </button>
      </Block>

      <Block title="Safeguarding disclosure (confidential)" unavailable={safeguarding.isError}>
        <p className="text-xs text-warning">
          Confidential lane SAFEGUARDING — never written to social-history.
        </p>
        <textarea
          value={safeguardText}
          onChange={(e) => setSafeguardText(e.target.value)}
          rows={2}
          className="w-full rounded border border-border px-2 py-1 text-sm"
        />
        <button
          type="button"
          disabled={!safeguardText.trim() || !encounterId || recordSafeguarding.isPending}
          onClick={() =>
            recordSafeguarding.mutate(
              {
                encounter_id: encounterId,
                disclosure_summary: safeguardText.trim(),
              },
              { onSuccess: () => setSafeguardText("") }
            )
          }
          className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
        >
          Record disclosure
        </button>
      </Block>
    </div>
  );
}
