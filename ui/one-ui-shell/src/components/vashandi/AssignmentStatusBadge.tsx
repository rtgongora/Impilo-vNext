"use client";

import type { AssignmentStatus } from "@/lib/vashandi/types";

const STATUS_STYLES: Record<string, string> = {
  active: "bg-success-soft text-success-foreground border-success/30",
  approved: "bg-info-soft text-primary-hover border-info/30",
  pending_approval: "bg-warning-soft text-warning-foreground border-warning/30",
  draft: "bg-muted text-muted-foreground border-border",
  suspended: "bg-destructive/10 text-destructive border-destructive/30",
  ended: "bg-muted text-muted-foreground border-border",
  rejected: "bg-destructive/10 text-destructive border-destructive/30",
};

interface AssignmentStatusBadgeProps {
  status?: AssignmentStatus;
}

export function AssignmentStatusBadge({ status }: AssignmentStatusBadgeProps) {
  const normalized = (status ?? "unknown").replace(/_/g, " ");
  const style = STATUS_STYLES[status ?? ""] ?? "bg-muted text-muted-foreground border-border";

  return (
    <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium capitalize ${style}`}>
      {normalized}
    </span>
  );
}
