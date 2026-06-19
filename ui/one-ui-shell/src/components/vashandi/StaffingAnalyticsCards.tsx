"use client";

import type { StaffingAnalyticsResponse } from "@/lib/vashandi/types";

interface StaffingAnalyticsCardsProps {
  analytics?: StaffingAnalyticsResponse;
  isLoading?: boolean;
}

export function StaffingAnalyticsCards({ analytics, isLoading }: StaffingAnalyticsCardsProps) {
  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading staffing analytics…</p>;
  }

  const counts = analytics?.counts ?? {};
  const entries = Object.entries(counts);

  if (entries.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
        No staffing analytics returned for your scope yet.
      </div>
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {entries.map(([key, value]) => (
        <div key={key} className="rounded-2xl border border-border bg-card p-4">
          <p className="text-sm text-muted-foreground capitalize">{key.replace(/_/g, " ")}</p>
          <p className="mt-2 text-2xl font-semibold text-foreground">{value}</p>
        </div>
      ))}
    </div>
  );
}
