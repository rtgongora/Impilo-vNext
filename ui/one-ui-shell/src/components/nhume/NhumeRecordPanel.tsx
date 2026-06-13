"use client";

type FieldSpec = { key: string; label: string };

function readField(record: Record<string, unknown>, key: string): string {
  const value = record[key];
  if (value == null || value === "") return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

interface NhumeRecordPanelProps {
  record: Record<string, unknown>;
  fields: FieldSpec[];
}

/** Renders nhume-service records as labelled rows (no debug JSON dumps). */
export function NhumeRecordPanel({ record, fields }: NhumeRecordPanelProps) {
  return (
    <dl className="mt-4 divide-y divide-gray-100 rounded-xl border border-border">
      {fields.map(({ key, label }) => (
        <div key={key} className="flex justify-between gap-4 px-3 py-2.5 text-sm">
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="max-w-[60%] text-right font-medium text-foreground">{readField(record, key)}</dd>
        </div>
      ))}
    </dl>
  );
}
