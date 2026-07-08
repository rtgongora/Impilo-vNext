"use client";

import { ChevronLeft, ChevronRight, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { asArray, asRecord, asText, type Row } from "@/components/learning/learningUtils";
import { Panel } from "@/components/learning/SharedComponents";

export function Reports({ data }: { data: Record<string, unknown> }) {
  return (
    <div className="grid gap-3 md:grid-cols-[280px_minmax(0,1fr)_minmax(0,1fr)] xl:grid-cols-[320px_minmax(0,1fr)_minmax(0,1fr)]">
      <Panel title="Overview">
        <KeyValues row={asRecord(data.reportOverview)} />
      </Panel>
      <Panel title="Cohort completions">
        <CompactRows rows={asArray(asRecord(data.cohortCompletions).items)} empty="No cohort report rows." />
      </Panel>
      <Panel title="Course completions">
        <CompactRows rows={asArray(asRecord(data.courseCompletions).items)} empty="No course completion rows." />
      </Panel>
      <Panel title="Overdue learning">
        <CompactRows rows={asArray(asRecord(data.overdue).items)} empty="No overdue learning rows." />
      </Panel>
      <Panel title="Assessment performance" className="md:col-span-2">
        <CompactRows rows={asArray(asRecord(data.assessmentPerformance).items)} empty="No assessment performance rows." />
      </Panel>
    </div>
  );
}

export function KeyValues({ row, compact = false }: { row: Row; compact?: boolean }) {
  const entries = Object.entries(row).filter(([, value]) => typeof value !== "object").slice(0, compact ? 6 : 12);
  if (!entries.length) return <p className="text-sm text-slate-500">No summary values returned.</p>;
  return (
    <dl className={compact ? "grid gap-1.5" : "grid gap-2 sm:grid-cols-2"}>
      {entries.map(([key, value]) => (
        <div key={key} className="rounded-md bg-slate-50 px-3 py-2">
          <dt className="truncate text-[11px] text-slate-500">{key}</dt>
          <dd className="truncate text-sm font-medium text-slate-900">{asText(value)}</dd>
        </div>
      ))}
    </dl>
  );
}

export function CompactRows({ rows, empty }: { rows: Row[]; empty: string }) {
  if (!rows.length) return <p className="rounded-md border border-dashed border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">{empty}</p>;
  return (
    <div className="max-h-[54vh] divide-y divide-slate-100 overflow-auto rounded-md border border-slate-200">
      {rows.map((row, index) => (
        <div key={String(row.id ?? row.code ?? index)} className="grid min-h-12 gap-2 px-3 py-2 md:grid-cols-[minmax(0,1fr)_150px]">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium leading-5 text-slate-950">{asText(row.title ?? row.name ?? row.courseTitle ?? row.code ?? row.id, `Item ${index + 1}`)}</p>
            <p className="truncate text-xs leading-4 text-slate-500">{asText(row.description ?? row.category ?? row.status ?? row.subjectId, "No detail supplied")}</p>
          </div>
          <div className="flex flex-wrap items-center gap-1 md:justify-end">
            {["status", "level", "type", "courseStatus"].map((key) =>
              row[key] ? (
                <span key={key} className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium uppercase text-slate-600">
                  {asText(row[key])}
                </span>
              ) : null,
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
