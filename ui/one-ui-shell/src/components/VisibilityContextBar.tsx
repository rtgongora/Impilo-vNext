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
  if (source === "obligations-or-headers") {
    return "Active session view";
  }
  return "Visibility";
}

export function VisibilityContextBar() {
  const { data, isLoading, isError } = useVisibilityProfile();

  if (isLoading || isError || !data || data.source === "headers-absent") {
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
      className="pointer-events-none fixed left-0 right-0 z-[9975] border-t border-border/80 bg-background/95 px-4 py-1.5 text-[11px] text-muted-foreground backdrop-blur-sm dark:border-border/80 dark:bg-neutral-900/90 dark:text-muted-foreground"
      style={{ bottom: "var(--shell-taskbar-height, 0px)" }}
      role="status"
      aria-live="polite"
    >
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-1">
        <span className="font-medium text-foreground dark:text-foreground">{headline}</span>
        <span className="font-mono text-muted-foreground dark:text-muted-foreground">
          tier={tier} · pii={pii} · clinical={clinical} · export={exportPol}
          {aggregate ? " · aggregateOnly" : ""}
        </span>
      </div>
      {emphasis ? (
        <div className="mx-auto mt-0.5 max-w-6xl text-[10px] text-warning-foreground/90 dark:text-warning-foreground/90">
          {emphasis}{" "}
          <Link
            href="/workspace/aggregate"
            className="pointer-events-auto font-medium text-warning-foreground underline underline-offset-2 hover:no-underline dark:text-amber-100"
          >
            Open aggregate workspace
          </Link>
        </div>
      ) : null}
    </div>
  );
}
