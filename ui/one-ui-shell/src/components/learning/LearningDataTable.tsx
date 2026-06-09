"use client";

import Link from "next/link";
import { Search } from "lucide-react";
import type { ReactNode } from "react";
import { useMemo, useState } from "react";

export interface LearningTableColumn<T> {
  key: string;
  label: string;
  className?: string;
  render: (row: T) => ReactNode;
  searchText?: (row: T) => string;
}

export interface LearningTableFilter<T> {
  key: string;
  label: string;
  options: Array<{ label: string; value: string }>;
  valueFor: (row: T) => string;
}

interface LearningDataTableProps<T> {
  rows: T[];
  columns: Array<LearningTableColumn<T>>;
  emptyText: string;
  searchPlaceholder?: string;
  filters?: Array<LearningTableFilter<T>>;
  getRowKey: (row: T, index: number) => string;
  getRowHref?: (row: T) => string | undefined;
  actionLabel?: string;
  maxRows?: number;
  dense?: boolean;
}

function searchableText<T>(row: T, columns: Array<LearningTableColumn<T>>) {
  return columns
    .map((column) => {
      if (column.searchText) return column.searchText(row);
      const rendered = column.render(row);
      return typeof rendered === "string" || typeof rendered === "number" ? String(rendered) : "";
    })
    .join(" ")
    .toLowerCase();
}

export function LearningDataTable<T>({
  rows,
  columns,
  emptyText,
  searchPlaceholder,
  filters = [],
  getRowKey,
  getRowHref,
  actionLabel = "Open",
  maxRows,
  dense = false,
}: LearningDataTableProps<T>) {
  const [query, setQuery] = useState("");
  const [filterValues, setFilterValues] = useState<Record<string, string>>({});
  const hasSearch = Boolean(searchPlaceholder);
  const hasControls = hasSearch || filters.length > 0;

  const filteredRows = useMemo(() => {
    const q = hasSearch ? query.trim().toLowerCase() : "";
    return rows.filter((row) => {
      const matchesSearch = !q || searchableText(row, columns).includes(q);
      const matchesFilters = filters.every((filter) => {
        const selected = filterValues[filter.key];
        return !selected || filter.valueFor(row) === selected;
      });
      return matchesSearch && matchesFilters;
    });
  }, [columns, filterValues, filters, hasSearch, query, rows]);

  const visibleRows = maxRows ? filteredRows.slice(0, maxRows) : filteredRows;

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
      {hasControls ? (
        <div className="flex flex-col gap-2 border-b border-gray-100 bg-gray-50/80 px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
          {hasSearch ? (
            <label className="relative min-w-0 flex-1">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={searchPlaceholder}
                className="h-9 w-full rounded-md border border-gray-300 bg-white pl-8 pr-3 text-sm text-gray-900 outline-none focus:border-impilo-500 focus:ring-2 focus:ring-impilo-100"
              />
            </label>
          ) : null}
          <div className="flex flex-wrap items-center gap-2">
            {filters.map((filter) => (
              <select
                key={filter.key}
                aria-label={filter.label}
                value={filterValues[filter.key] ?? ""}
                onChange={(event) => setFilterValues((current) => ({ ...current, [filter.key]: event.target.value }))}
                className="h-9 rounded-md border border-gray-300 bg-white px-2 text-sm text-gray-700"
              >
                <option value="">All {filter.label.toLowerCase()}</option>
                {filter.options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            ))}
            <span className="whitespace-nowrap text-xs font-medium text-gray-500">
              {filteredRows.length} item{filteredRows.length === 1 ? "" : "s"}
            </span>
          </div>
        </div>
      ) : null}
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-100 text-left text-sm">
          <thead className="bg-white">
            <tr>
              {columns.map((column) => (
                <th key={column.key} scope="col" className={`px-3 py-2 text-xs font-semibold uppercase tracking-normal text-gray-500 ${column.className ?? ""}`}>
                  {column.label}
                </th>
              ))}
              {getRowHref ? <th scope="col" className="w-24 px-3 py-2 text-right text-xs font-semibold uppercase tracking-normal text-gray-500">Action</th> : null}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {visibleRows.map((row, index) => {
              const href = getRowHref?.(row);
              return (
                <tr key={getRowKey(row, index)} className="hover:bg-gray-50">
                  {columns.map((column) => (
                    <td key={column.key} className={`${dense ? "px-3 py-2" : "px-3 py-3"} align-top text-gray-700 ${column.className ?? ""}`}>
                      {column.render(row)}
                    </td>
                  ))}
                  {getRowHref ? (
                    <td className={`${dense ? "px-3 py-2" : "px-3 py-3"} text-right align-top`}>
                      {href ? (
                        <Link href={href} className="font-medium text-impilo-700 hover:underline">
                          {actionLabel}
                        </Link>
                      ) : (
                        <span className="text-gray-400">-</span>
                      )}
                    </td>
                  ) : null}
                </tr>
              );
            })}
            {visibleRows.length === 0 ? (
              <tr>
                <td colSpan={columns.length + (getRowHref ? 1 : 0)} className="px-3 py-6 text-center text-sm text-gray-500">
                  {emptyText}
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      {maxRows && filteredRows.length > maxRows ? (
        <div className="border-t border-gray-100 px-3 py-2 text-xs text-gray-500">
          Showing {maxRows} of {filteredRows.length}. Open the focused view for the full table.
        </div>
      ) : null}
    </div>
  );
}
