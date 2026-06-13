"use client";

import { DomainCollectionTable } from "@/components/common/DomainCollectionTable";

type WorkflowInstanceRow = {
  id: string;
  definitionId: string;
  status: string;
  subjectRef: string;
  updatedAt: string;
};

function normalizeInstances(payload: unknown): WorkflowInstanceRow[] {
  const raw = Array.isArray(payload)
    ? payload
    : payload && typeof payload === "object"
      ? (Array.isArray((payload as { items?: unknown }).items)
          ? (payload as { items: unknown[] }).items
          : Array.isArray((payload as { data?: unknown }).data)
            ? (payload as { data: unknown[] }).data
            : [])
      : [];

  return raw.map((row, index) => {
    const r = row as Record<string, unknown>;
    const attrs = (r.attributes as Record<string, unknown> | undefined) ?? r;
    return {
      id: String(r.id ?? index),
      definitionId: String(attrs.definitionId ?? attrs.definition_id ?? "—"),
      status: String(attrs.status ?? attrs.state ?? "UNKNOWN"),
      subjectRef: String(attrs.subjectRef ?? attrs.subject_ref ?? "—"),
      updatedAt: String(attrs.updatedAt ?? attrs.updated_at ?? attrs.lastTransitionAt ?? "—"),
    };
  });
}

interface WorkflowInstancesTableProps {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
  selectedId?: string;
  onSelect?: (id: string) => void;
}

export function WorkflowInstancesTable({
  data,
  isLoading,
  error,
  selectedId,
  onSelect,
}: WorkflowInstancesTableProps) {
  const rows = normalizeInstances(data);

  return (
    <DomainCollectionTable<WorkflowInstanceRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No workflow instances"
      emptyHint="Start an instance from a definition or adjust filters."
      rowKey={(row) => row.id}
      columns={[
        {
          key: "id",
          header: "Instance",
          render: (row) => (
            <button
              type="button"
              className={`text-left font-mono text-xs ${selectedId === row.id ? "text-primary-hover font-semibold" : "text-foreground"}`}
              onClick={() => onSelect?.(row.id)}
            >
              {row.id}
            </button>
          ),
        },
        { key: "definition", header: "Definition", render: (row) => row.definitionId },
        { key: "status", header: "Status", render: (row) => row.status },
        { key: "subject", header: "Subject", render: (row) => row.subjectRef },
        { key: "updated", header: "Updated", render: (row) => row.updatedAt },
      ]}
    />
  );
}
