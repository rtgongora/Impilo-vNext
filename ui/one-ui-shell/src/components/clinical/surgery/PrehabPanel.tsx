"use client";

/**
 * Prehabilitation / optimisation items on a surgical episode (SB-1).
 * List + PUT upsert on (episode, domain). A failed list must never render as empty.
 */

import { useState } from "react";
import {
  usePrehabItems,
  useRecordPrehabItem,
  type PrehabDomain,
  type PrehabStatus,
} from "@/hooks/queries/useSurgeryEpisodes";

const PREHAB_DOMAINS: PrehabDomain[] = [
  "ANAEMIA",
  "NUTRITION",
  "GLYCAEMIC_CONTROL",
  "SMOKING_CESSATION",
  "ALCOHOL_REDUCTION",
  "VTE_PLAN",
  "ANTIBIOTIC_PROPHYLAXIS_PLAN",
  "BLOOD_PREPARATION",
  "CARDIORESPIRATORY_FITNESS",
  "PHYSIOTHERAPY",
  "PSYCHOLOGICAL_PREPARATION",
  "MEDICATION_OPTIMISATION",
  "DENTAL",
  "WEIGHT_MANAGEMENT",
  "FRAILTY_INTERVENTION",
  "SOCIAL_DISCHARGE_PREPARATION",
];

const PREHAB_STATUSES: PrehabStatus[] = [
  "PLANNED",
  "IN_PROGRESS",
  "COMPLETED",
  "NOT_REQUIRED",
  "DECLINED",
];

export function PrehabPanel({ episodeId }: { episodeId: string }) {
  const listQ = usePrehabItems(episodeId);
  const record = useRecordPrehabItem(episodeId);
  const [domain, setDomain] = useState<PrehabDomain | "">("");
  const [status, setStatus] = useState<PrehabStatus | "">("");
  const [target, setTarget] = useState("");
  const [notes, setNotes] = useState("");

  const reset = () => {
    setDomain("");
    setStatus("");
    setTarget("");
    setNotes("");
  };

  return (
    <div className="mt-4 rounded-lg border border-gray-100 p-3" data-testid="surgery-prehab-panel">
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">
        Prehabilitation / optimisation (course of care)
      </h4>

      {listQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading optimisation items…</p>
      ) : listQ.isError ? (
        <p className="mt-2 text-sm text-danger" role="alert" data-testid="surgery-prehab-unavailable">
          Could not read optimisation items. This is not the same as there being none.
        </p>
      ) : listQ.data && listQ.data.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-prehab-empty">
          No optimisation items recorded for this episode yet.
        </p>
      ) : (
        <ul className="mt-2 space-y-1" data-testid="surgery-prehab-list">
          {listQ.data?.map((item) => (
            <li key={item.id} className="text-sm">
              <span className="font-medium">{item.domain}</span>
              {item.status && (
                <span className="ml-2 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gray-600">
                  {item.status}
                </span>
              )}
              {item.target && (
                <span className="ml-2 text-xs text-muted-foreground">{item.target}</span>
              )}
              {item.plannedBy && (
                <span className="ml-2 text-xs text-muted-foreground">by {item.plannedBy}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Domain</span>
          <select
            value={domain}
            onChange={(e) => setDomain(e.target.value as PrehabDomain | "")}
            className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-prehab-domain"
          >
            <option value="">Choose domain…</option>
            {PREHAB_DOMAINS.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Status</span>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as PrehabStatus | "")}
            className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-prehab-status"
          >
            <option value="">Optional…</option>
            {PREHAB_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label className="block flex-1 text-xs min-w-[140px]">
          <span className="font-semibold uppercase text-muted-foreground">Target</span>
          <input
            type="text"
            value={target}
            onChange={(e) => setTarget(e.target.value)}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-prehab-target"
          />
        </label>
        <label className="block flex-1 text-xs min-w-[140px]">
          <span className="font-semibold uppercase text-muted-foreground">Notes</span>
          <input
            type="text"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-prehab-notes"
          />
        </label>
        <button
          type="button"
          disabled={!domain || record.isPending}
          onClick={() =>
            record.mutate(
              {
                domain: domain as PrehabDomain,
                status: status || null,
                target: target.trim() || null,
                notes: notes.trim() || null,
              },
              { onSuccess: reset },
            )
          }
          className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
          data-testid="surgery-prehab-save"
        >
          {record.isPending ? "Saving…" : "Record / refine"}
        </button>
      </div>
      {record.isError && (
        <p className="mt-2 text-sm text-danger" role="alert" data-testid="surgery-prehab-write-failed">
          That change was NOT saved — the optimisation items are unchanged.
        </p>
      )}
    </div>
  );
}
