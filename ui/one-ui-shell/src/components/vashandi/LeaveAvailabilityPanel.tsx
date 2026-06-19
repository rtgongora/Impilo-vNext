"use client";

import type { LeaveAvailability } from "@/lib/vashandi/types";

interface LeaveAvailabilityPanelProps {
  records: LeaveAvailability[];
  isLoading?: boolean;
}

export function LeaveAvailabilityPanel({ records, isLoading }: LeaveAvailabilityPanelProps) {
  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading leave and availability…</p>;
  }

  if (records.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
        No leave or availability records returned for your scope yet.
      </div>
    );
  }

  return (
    <ul className="space-y-2">
      {records.map((record) => (
        <li key={record.id} className="rounded-xl border border-border bg-card px-4 py-3 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-medium capitalize">{(record.leaveType ?? "leave").replace(/_/g, " ")}</span>
            <span className="rounded-full border border-border px-2 py-0.5 text-xs capitalize">{record.status}</span>
          </div>
          <p className="mt-1 text-muted-foreground">
            {record.startDate} → {record.endDate}
          </p>
        </li>
      ))}
    </ul>
  );
}
