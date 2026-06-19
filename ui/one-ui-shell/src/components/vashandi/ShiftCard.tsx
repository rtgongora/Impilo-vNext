"use client";

import type { Shift } from "@/lib/vashandi/types";
import { AssignmentStatusBadge } from "./AssignmentStatusBadge";

interface ShiftCardProps {
  shift: Shift;
}

export function ShiftCard({ shift }: ShiftCardProps) {
  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-medium text-foreground">{shift.shiftType ?? "Shift"}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {shift.startTime} → {shift.endTime}
          </p>
          {shift.locationType ? <p className="mt-1 text-xs text-muted-foreground">{shift.locationType}</p> : null}
        </div>
        <AssignmentStatusBadge status={shift.status} />
      </div>
      {shift.checkInRequired ? (
        <p className="mt-3 text-xs font-medium text-primary-hover">Check-in required</p>
      ) : null}
    </div>
  );
}
