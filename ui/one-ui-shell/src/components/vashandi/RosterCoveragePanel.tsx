"use client";

import type { RosterCoverageResponse } from "@/lib/vashandi/types";

interface RosterCoveragePanelProps {
  coverage?: RosterCoverageResponse;
  isLoading?: boolean;
}

export function RosterCoveragePanel({ coverage, isLoading }: RosterCoveragePanelProps) {
  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading roster coverage…</p>;
  }

  if (!coverage) {
    return (
      <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
        No roster coverage analytics returned for your scope yet.
      </div>
    );
  }

  const metrics = [
    { label: "Approved rosters", value: coverage.approvedRosters },
    { label: "Scheduled shifts", value: coverage.scheduledShifts },
    { label: "Checked-in shifts", value: coverage.checkedInShifts },
    { label: "Facility scheduled shifts", value: coverage.facilityScheduledShifts },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {metrics.map((metric) => (
        <div key={metric.label} className="rounded-2xl border border-border bg-card p-4">
          <p className="text-sm text-muted-foreground">{metric.label}</p>
          <p className="mt-2 text-2xl font-semibold text-foreground">{metric.value ?? 0}</p>
        </div>
      ))}
    </div>
  );
}
