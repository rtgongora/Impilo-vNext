"use client";

import type { Roster, Shift } from "@/lib/vashandi/types";

interface RosterCalendarProps {
  rosters: Roster[];
  shifts?: Shift[];
}

export function RosterCalendar({ rosters, shifts = [] }: RosterCalendarProps) {
  if (rosters.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
        No rosters returned for your scope yet.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {rosters.map((roster) => {
        const rosterShifts = shifts.filter((shift) => shift.rosterId === roster.id);
        return (
          <div key={roster.id} className="rounded-2xl border border-border bg-card p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="font-medium text-foreground">{roster.rosterType ?? "Roster"}</p>
                <p className="text-sm text-muted-foreground">
                  {roster.periodStart} → {roster.periodEnd}
                </p>
              </div>
              <span className="rounded-full border border-border px-2 py-0.5 text-xs capitalize">{roster.status}</span>
            </div>
            {rosterShifts.length > 0 ? (
              <ul className="mt-3 space-y-2 border-t border-border pt-3">
                {rosterShifts.map((shift) => (
                  <li key={shift.id} className="flex justify-between text-sm">
                    <span>{shift.shiftType ?? "Shift"}</span>
                    <span className="text-muted-foreground">
                      {shift.startTime} – {shift.endTime}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-xs text-muted-foreground">No shifts scheduled on this roster yet.</p>
            )}
          </div>
        );
      })}
    </div>
  );
}
