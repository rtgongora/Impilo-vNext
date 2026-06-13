"use client";

import type { ReactNode } from "react";
import { DomainCollectionTable, type DomainTableColumn } from "@/components/common/DomainCollectionTable";
import { pickField, rowId, unwrapCollection } from "@/lib/json-api/normalize-collection";

export type JsonApiColumnDef = {
  key: string;
  header: string;
  fields: string[];
  render?: (row: Record<string, unknown>) => ReactNode;
  className?: string;
};

interface JsonApiDataTableProps {
  data: unknown;
  columns: JsonApiColumnDef[];
  isLoading?: boolean;
  error?: Error | null;
  emptyTitle?: string;
  emptyHint?: string;
  idFields?: string[];
}

export function JsonApiDataTable({
  data,
  columns,
  isLoading,
  error,
  emptyTitle = "No records",
  emptyHint,
  idFields = ["id"],
}: JsonApiDataTableProps) {
  const rows = unwrapCollection(data);
  const tableColumns: DomainTableColumn<Record<string, unknown>>[] = columns.map((col) => ({
    key: col.key,
    header: col.header,
    className: col.className,
    render: (row) =>
      col.render ? col.render(row) : <span className="text-sm text-foreground">{pickField(row, ...col.fields)}</span>,
  }));

  return (
    <DomainCollectionTable<Record<string, unknown>>
      rows={rows}
      columns={tableColumns}
      rowKey={(row, index) => rowId(row, index, ...idFields)}
      isLoading={isLoading}
      error={error}
      emptyTitle={emptyTitle}
      emptyHint={emptyHint}
    />
  );
}
