"use client";

import { Loader2, AlertCircle } from "lucide-react";

type FieldSpec = { key: string; label: string };

function formatLabel(key: string): string {
  return key
    .replace(/([A-Z])/g, " $1")
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim();
}

function formatValue(value: unknown): string {
  if (value == null || value === "") return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "object") {
    const text = JSON.stringify(value);
    return text.length > 120 ? `${text.slice(0, 117)}…` : text;
  }
  return String(value);
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function asRows(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) {
    return value.filter((x): x is Record<string, unknown> => typeof x === "object" && x !== null);
  }
  const record = asRecord(value);
  if (!record) return [];
  if (Array.isArray(record.items)) return asRows(record.items);
  if (Array.isArray(record.content)) return asRows(record.content);
  if (Array.isArray(record.data)) return asRows(record.data);
  return [record];
}

function inferFields(record: Record<string, unknown>, limit = 10): FieldSpec[] {
  const priority = ["id", "name", "title", "status", "type", "code", "createdAt", "updatedAt"];
  const keys = Object.keys(record);
  const ordered = [
    ...priority.filter((k) => keys.includes(k)),
    ...keys.filter((k) => !priority.includes(k)),
  ].slice(0, limit);
  return ordered.map((key) => ({ key, label: formatLabel(key) }));
}

export function RecordSummary({ record }: { record: Record<string, unknown> }) {
  return <RecordRows record={record} fields={inferFields(record, 8)} />;
}

function RecordRows({ record, fields }: { record: Record<string, unknown>; fields: FieldSpec[] }) {
  return (
    <dl className="divide-y divide-slate-100">
      {fields.map(({ key, label }) => (
        <div key={key} className="flex justify-between gap-3 py-1.5 text-xs">
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="max-w-[65%] text-right font-medium text-foreground">{formatValue(record[key])}</dd>
        </div>
      ))}
    </dl>
  );
}

export interface QueryResultPanelProps {
  title: string;
  /** @deprecated Prefer isPending for mutations (React Query v5). */
  isLoading?: boolean;
  isPending?: boolean;
  isError?: boolean;
  error?: unknown;
  data?: unknown;
  maxRows?: number;
  fields?: FieldSpec[];
}

/** Renders BFF/query payloads as structured rows — replaces debug JSON pre blocks. */
export function QueryResultPanel({
  title,
  isLoading,
  isPending,
  isError,
  error,
  data,
  maxRows = 8,
  fields,
}: QueryResultPanelProps) {
  const busy = isLoading ?? isPending;
  return (
    <section className="rounded-lg border border-border bg-card p-4">
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      <div className="mt-2">
        {busy ? (
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Loading…
          </div>
        ) : isError ? (
          <div className="flex items-start gap-2 text-xs text-danger">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{error instanceof Error ? error.message : "Request failed"}</span>
          </div>
        ) : (
          (() => {
            const rows = asRows(data);
            if (rows.length === 0) {
              return <p className="text-xs text-muted-foreground">No records returned.</p>;
            }
            return (
              <ul className="max-h-64 space-y-3 overflow-y-auto">
                {rows.slice(0, maxRows).map((row, index) => {
                  const rowFields = fields ?? inferFields(row);
                  const label =
                    String(row.name ?? row.title ?? row.id ?? row.code ?? `Record ${index + 1}`);
                  return (
                    <li key={String(row.id ?? index)} className="rounded-lg border border-border bg-background p-3">
                      <p className="mb-1 text-xs font-semibold text-foreground">{label}</p>
                      <RecordRows record={row} fields={rowFields} />
                    </li>
                  );
                })}
              </ul>
            );
          })()
        )}
      </div>
    </section>
  );
}
