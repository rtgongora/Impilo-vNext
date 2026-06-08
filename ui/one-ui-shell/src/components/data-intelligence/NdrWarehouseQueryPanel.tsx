"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, Database, Loader2, RefreshCw } from "lucide-react";
import {
  bronzeEventLabel,
  goldEncounterLabel,
  useNdrBronzeQuery,
  useNdrBuildGoldEncounters,
  useNdrGoldEncountersQuery,
} from "@/hooks/queries/useNdr";

export function NdrWarehouseQueryPanel() {
  const [eventType, setEventType] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [patientId, setPatientId] = useState("");

  const bronzeQ = useNdrBronzeQuery({
    eventType: eventType || undefined,
    subjectId: subjectId || undefined,
    cursor: 0,
    limit: 10,
  });
  const goldQ = useNdrGoldEncountersQuery({
    patientId: patientId || undefined,
    cursor: 0,
    limit: 10,
  });
  const buildGold = useNdrBuildGoldEncounters();

  const bronzeState = bronzeQ.isLoading
    ? "loading"
    : bronzeQ.isError
      ? "unavailable"
      : bronzeQ.data?.items.length
        ? "live"
        : "empty";
  const goldState = goldQ.isLoading
    ? "loading"
    : goldQ.isError
      ? "unavailable"
      : goldQ.data?.items.length
        ? "live"
        : "empty";

  return (
    <section
      className="rounded-xl border border-slate-200 bg-slate-50/80 p-4"
      data-testid="ndr-warehouse-query-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2 text-sm text-slate-900">
          <Database className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600" />
          <div>
            <p className="font-medium">NDR warehouse queries</p>
            <p className="text-xs text-slate-600">
              Governed bronze event browse and gold encounter projections via Experience BFF → ndr-service. No mocked
              warehouse rows — empty means the lake has no matching events yet.
            </p>
          </div>
        </div>
        <button
          type="button"
          disabled={buildGold.isPending}
          onClick={() => buildGold.mutate()}
          className="inline-flex items-center gap-1 rounded-lg border border-indigo-300 bg-white px-3 py-1.5 text-xs font-medium text-indigo-900 hover:bg-indigo-50 disabled:opacity-50"
        >
          {buildGold.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}
          Build gold encounters
        </button>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-white bg-white p-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Bronze lake</p>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            <input
              className="rounded border border-slate-300 px-2 py-1.5 text-xs"
              placeholder="Event type (optional)"
              value={eventType}
              onChange={(e) => setEventType(e.target.value)}
            />
            <input
              className="rounded border border-slate-300 px-2 py-1.5 text-xs"
              placeholder="Subject id (optional)"
              value={subjectId}
              onChange={(e) => setSubjectId(e.target.value)}
            />
          </div>
          <p className="mt-2 text-[11px] text-slate-600" data-testid="ndr-bronze-status">
            {bronzeState === "loading"
              ? "Querying bronze events…"
              : bronzeState === "unavailable"
                ? "Bronze query unavailable — ndr-service or governance may be down."
                : bronzeState === "empty"
                  ? "Bronze lake reachable but no events match the current filter."
                  : `${bronzeQ.data?.items.length ?? 0} bronze event(s) on this page`}
          </p>
          {bronzeQ.isLoading ? (
            <div className="mt-2 flex items-center gap-2 text-xs text-slate-500">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Loading…
            </div>
          ) : bronzeQ.isError ? null : (
            <ul className="mt-2 max-h-40 space-y-1 overflow-y-auto text-xs text-slate-800">
              {(bronzeQ.data?.items ?? []).map((row, index) => (
                <li key={String(row.event_id ?? row.eventId ?? index)} className="rounded bg-slate-50 px-2 py-1">
                  {bronzeEventLabel(row)}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-lg border border-white bg-white p-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Gold encounters</p>
          <input
            className="mt-2 w-full rounded border border-slate-300 px-2 py-1.5 text-xs"
            placeholder="Patient ref filter (optional)"
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
          />
          <p className="mt-2 text-[11px] text-slate-600" data-testid="ndr-gold-status">
            {goldState === "loading"
              ? "Querying gold encounters…"
              : goldState === "unavailable"
                ? "Gold query unavailable — run build after bronze ingest or check governance."
                : goldState === "empty"
                  ? "Gold projection reachable but no encounters match the current filter."
                  : `${goldQ.data?.items.length ?? 0} encounter(s) on this page`}
          </p>
          {goldQ.isLoading ? (
            <div className="mt-2 flex items-center gap-2 text-xs text-slate-500">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Loading…
            </div>
          ) : goldQ.isError ? null : (
            <ul className="mt-2 max-h-40 space-y-1 overflow-y-auto text-xs text-slate-800">
              {(goldQ.data?.items ?? []).map((row, index) => (
                <li
                  key={String(row.encounter_id ?? row.encounterId ?? index)}
                  className="rounded bg-slate-50 px-2 py-1"
                >
                  {goldEncounterLabel(row)}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {(bronzeQ.isError || goldQ.isError) && (
        <div className="mt-3 flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-950">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            NDR queries require live ndr-service and governed purpose-of-use headers. Retry after stack boot or open{" "}
            <Link href="/data-intelligence/audit" className="font-medium underline">
              audit intelligence
            </Link>{" "}
            for access traces.
          </p>
        </div>
      )}

      {buildGold.isSuccess ? (
        <p className="mt-2 text-xs text-emerald-800" data-testid="ndr-build-success">
          Gold build accepted — refresh gold query after materialization completes.
        </p>
      ) : null}
    </section>
  );
}
