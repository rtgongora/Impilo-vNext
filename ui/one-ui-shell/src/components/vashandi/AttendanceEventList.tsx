"use client";

import type { AttendanceEvent } from "@/lib/vashandi/types";

interface AttendanceEventListProps {
  events: AttendanceEvent[];
  isLoading?: boolean;
}

export function AttendanceEventList({ events, isLoading }: AttendanceEventListProps) {
  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading attendance events…</p>;
  }

  if (events.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
        No attendance events returned for your scope yet.
      </div>
    );
  }

  return (
    <ul className="space-y-2">
      {events.map((event) => (
        <li key={event.id} className="rounded-xl border border-border bg-card px-4 py-3 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-medium capitalize">{(event.eventType ?? "event").replace(/_/g, " ")}</span>
            <span className="text-muted-foreground">{event.eventTime}</span>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            Profile {event.workforceProfileId}
            {event.checkInMode ? ` · ${event.checkInMode}` : ""}
            {event.offline ? " · offline" : ""}
          </p>
        </li>
      ))}
    </ul>
  );
}
