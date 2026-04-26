"use client";

/**
 * Surfaces Tshepo-derived visibility posture propagated through the gateway into the BFF.
 * Helps supervisory and aggregate-only users see why row-level or clinical views may be absent.
 */

import Link from "next/link";
import { useVisibilityProfile } from "@/hooks/queries/useVisibilityProfile";

function labelForSource(source: string | undefined): string {
  if (source === "headers") {
    return "Active session view";
  }
  if (source === "headers-absent") {
    return "Gateway did not attach visibility headers";
  }
  return "Visibility";
}

export function VisibilityContextBar() {
  const { data, isLoading, isError } = useVisibilityProfile();

  if (isLoading || isError || !data) {
    return null;
  }

  const tier = data.visibilityTier ?? "—";
  const pii = data.piiAccess ?? "—";
  const clinical = data.clinicalAccess ?? "—";
  const exportPol = data.exportPolicy ?? "—";
  const aggregate = data.aggregateOnly === true;
  const headline = labelForSource(data.source);

  const emphasis =
    aggregate || tier === "AGGREGATE_ONLY"
      ? "You are in an aggregate-only or restricted data view. Person-level and export actions may be limited."
      : null;

  return (
    <div
      className="pointer-events-none fixed left-0 right-0 z-[9975] border-t border-slate-200/80 bg-slate-50/95 px-4 py-1.5 text-[11px] text-slate-600 backdrop-blur-sm dark:border-slate-700/80 dark:bg-slate-900/90 dark:text-slate-300"
      style={{ bottom: "var(--shell-taskbar-height, 0px)" }}
      role="status"
      aria-live="polite"
    >
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-1">
        <span className="font-medium text-slate-700 dark:text-slate-200">{headline}</span>
        <span className="font-mono text-slate-500 dark:text-slate-400">
          tier={tier} · pii={pii} · clinical={clinical} · export={exportPol}
          {aggregate ? " · aggregateOnly" : ""}
        </span>
      </div>
      {emphasis ? (
        <div className="mx-auto mt-0.5 max-w-6xl text-[10px] text-amber-800/90 dark:text-amber-200/90">
          {emphasis}{" "}
          <Link
            href="/workspace/aggregate"
            className="pointer-events-auto font-medium text-amber-900 underline underline-offset-2 hover:no-underline dark:text-amber-100"
          >
            Open aggregate workspace
          </Link>
        </div>
      ) : null}
    </div>
  );
}
