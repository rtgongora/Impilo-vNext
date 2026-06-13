"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  type AnyRecord,
  type OperatorTelemetryRow,
  toDispatchTelemetryRow,
  toWorkflowTelemetryRow,
} from "@/lib/operator-telemetry";

type TelemetryKind = "workflow" | "dispatch";

interface ActiveFilter {
  key: string;
  value: string;
}

interface OperatorTelemetryPanelProps {
  title: string;
  kind: TelemetryKind;
  items: AnyRecord[];
  isLoading: boolean;
  isError: boolean;
  detail: string;
  emptyLabel: string;
  controls?: ReactNode;
  activeFilters?: ActiveFilter[];
  onResetFilters?: () => void;
  maxItems?: number;
  focusedRowId?: string | null;
  rowLinkBuilder?: (row: OperatorTelemetryRow) => string;
  rowLinkLabel?: string;
}

function severityClasses(severity: OperatorTelemetryRow["severity"]): string {
  switch (severity) {
    case "critical":
      return "border-danger/28 bg-danger-soft text-danger";
    case "warning":
      return "border-warning/35 bg-warning-soft text-warning-foreground";
    default:
      return "border-success/25 bg-success-soft text-primary-hover";
  }
}

export function OperatorTelemetryPanel({
  title,
  kind,
  items,
  isLoading,
  isError,
  detail,
  emptyLabel,
  controls,
  activeFilters,
  onResetFilters,
  maxItems = 6,
  focusedRowId,
  rowLinkBuilder,
  rowLinkLabel = "Open context",
}: OperatorTelemetryPanelProps) {
  const rows = items
    .slice(0, maxItems)
    .map((entry, index) =>
      kind === "workflow"
        ? toWorkflowTelemetryRow(entry, index)
        : toDispatchTelemetryRow(entry, index),
    );
  const hasActiveFilters = (activeFilters?.length ?? 0) > 0;

  return (
    <section className="impilo-surface-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        <FeatureMaturityBadge
          status={items.length > 0 ? "connected" : isError ? "partial" : "connected"}
          detail={detail}
        />
      </div>

      <p className="mt-2 text-sm text-foreground">
        {isLoading
          ? `Loading ${kind} telemetry...`
          : isError
            ? `${title} unavailable.`
            : `${items.length} ${kind} item(s) loaded.`}
      </p>

      {controls ? <div className="mt-3">{controls}</div> : null}

      {hasActiveFilters ? (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {activeFilters?.map((filter) => (
            <span
              key={`${filter.key}-${filter.value}`}
              className="rounded-full border border-border bg-background px-2 py-0.5 text-xs text-foreground"
            >
              {filter.key}: {filter.value}
            </span>
          ))}
          {onResetFilters ? (
            <button
              type="button"
              onClick={onResetFilters}
              className="rounded-full border border-border bg-card px-2 py-0.5 text-xs font-medium text-foreground hover:bg-background"
            >
              Reset
            </button>
          ) : null}
        </div>
      ) : null}

      {rows.length === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">
          {isLoading ? `Loading ${kind} timeline...` : isError ? `${title} timeline unavailable.` : emptyLabel}
        </p>
      ) : (
        <ol className="mt-3 space-y-2">
          {rows.map((row, index) => (
            <li
              key={`${row.id}-${index}`}
              className={`rounded-lg border p-2 ${
                focusedRowId && row.id === focusedRowId
                  ? "border-indigo-300 bg-info-soft"
                  : "border-border bg-background"
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{row.type}</p>
                  <p className="text-sm font-medium text-foreground">{row.id}</p>
                </div>
                <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${severityClasses(row.severity)}`}>
                  {row.severity}
                </span>
              </div>
              <p className="mt-1 text-sm text-foreground">State: {row.transition}</p>
              {row.assignee ? <p className="text-sm text-foreground">Assignee: {row.assignee}</p> : null}
              <p className="text-xs text-muted-foreground">{row.timestampLabel}</p>
              {rowLinkBuilder ? (
                <div className="mt-2">
                  <Link
                    href={rowLinkBuilder(row)}
                    className="inline-flex rounded-md border border-border bg-card px-2 py-1 text-xs font-medium text-foreground hover:bg-background"
                  >
                    {rowLinkLabel}
                  </Link>
                </div>
              ) : null}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
