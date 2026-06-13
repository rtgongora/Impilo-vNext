import React from "react";

export type StatusBadgeVariant =
  | "success"
  | "info"
  | "pending"
  | "opportunity"
  | "tip"
  | "urgent"
  | "failed"
  | "blocked"
  | "default";

export interface StatusBadgeProps {
  children: React.ReactNode;
  variant?: StatusBadgeVariant;
  className?: string;
}

const variantStyles: Record<StatusBadgeVariant, string> = {
  success: "bg-success-soft text-primary-hover border-success/25",
  info: "bg-info-soft text-primary-hover border-info/25",
  pending: "bg-warning-soft text-warning-foreground border-warning/35",
  opportunity: "bg-warning-soft text-warning-foreground border-warning/35",
  tip: "bg-warning-soft text-warning-foreground border-warning/35",
  urgent: "bg-danger-soft text-danger border-danger/28",
  failed: "bg-danger-soft text-danger border-danger/28",
  blocked: "bg-danger-soft text-danger border-danger/28",
  default: "bg-neutral-100 text-muted-foreground border-border",
};

export function StatusBadge({ children, variant = "default", className = "" }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium
        ${variantStyles[variant]} ${className}`}
    >
      {children}
    </span>
  );
}
