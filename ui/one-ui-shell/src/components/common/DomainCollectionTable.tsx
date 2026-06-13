"use client";

import type { ReactNode } from "react";
import { Loader2, AlertCircle } from "lucide-react";

export interface DomainTableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  className?: string;
}

interface DomainCollectionTableProps<T> {
  rows: T[];
  columns: DomainTableColumn<T>[];
  rowKey: (row: T, index: number) => string;
  isLoading?: boolean;
  error?: Error | null;
  emptyTitle?: string;
  emptyHint?: string;
}

export function DomainCollectionTable<T>({
  rows,
  columns,
  rowKey,
  isLoading,
  error,
  emptyTitle = "No records",
  emptyHint,
}: DomainCollectionTableProps<T>) {
  if (error) {
    return (
      <div className="rounded-lg border border-danger/28 bg-danger-soft/60 p-6 text-center">
        <AlertCircle className="mx-auto mb-2 h-8 w-8 text-red-300" />
        <p className="text-sm text-danger">Failed to load data</p>
        <p className="mt-1 text-xs text-red-600/80">{error.message}</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        <span className="ml-2 text-sm text-muted-foreground">Loading…</span>
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-border bg-card p-8 text-center">
        <p className="text-sm text-muted-foreground">{emptyTitle}</p>
        {emptyHint ? <p className="mt-1 text-xs text-muted-foreground">{emptyHint}</p> : null}
      </div>
    );
  }

  return (
    <div className="overflow-auto rounded-lg border border-border bg-card">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            {columns.map((col) => (
              <th key={col.key} className={`px-3 py-2 font-semibold ${col.className ?? ""}`}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {rows.map((row, index) => (
            <tr key={rowKey(row, index)} className="hover:bg-background/80">
              {columns.map((col) => (
                <td key={col.key} className={`px-3 py-2.5 text-foreground ${col.className ?? ""}`}>
                  {col.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
