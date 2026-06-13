"use client";

/**
 * NhumeStatusChip — visual treatment for delivery statuses.
 *
 * Maps the full delivery lifecycle to colour families so dispatchers and
 * citizens can scan lists quickly. Falls back to a neutral chip for unknown
 * values so new backend statuses do not break the UI.
 */

function cx(...parts: Array<string | undefined | false | null>): string {
  return parts.filter(Boolean).join(" ");
}

const STATUS_THEMES: Record<string, { bg: string; text: string; border: string; label?: string }> = {
  DRAFT:                  { bg: "bg-background",     text: "text-foreground",     border: "border-border" },
  SUBMITTED:              { bg: "bg-primary-soft",   text: "text-primary-hover",   border: "border-primary/25" },
  VALIDATION_REQUIRED:    { bg: "bg-warning-soft",    text: "text-warning-foreground",    border: "border-warning/35", label: "Validation Required" },
  AWAITING_APPROVAL:      { bg: "bg-warning-soft",    text: "text-warning-foreground",    border: "border-warning/35", label: "Awaiting Approval" },
  APPROVED:               { bg: "bg-success-soft",  text: "text-primary-hover",  border: "border-success/25" },
  REJECTED:               { bg: "bg-danger-soft",     text: "text-danger",     border: "border-danger/28" },
  AWAITING_PAYMENT:       { bg: "bg-warning-soft",    text: "text-warning-foreground",    border: "border-warning/35", label: "Awaiting Payment" },
  AWAITING_STOCK:         { bg: "bg-warning-soft",    text: "text-warning-foreground",    border: "border-warning/35", label: "Awaiting Stock" },
  AWAITING_PICKUP:        { bg: "bg-sky-50",      text: "text-sky-700",      border: "border-sky-200", label: "Awaiting Pickup" },
  DISPATCH_PENDING:       { bg: "bg-sky-50",      text: "text-sky-700",      border: "border-sky-200", label: "Dispatch Pending" },
  ASSIGNED:               { bg: "bg-info-soft",   text: "text-primary-hover",   border: "border-info/25" },
  ACCEPTED:               { bg: "bg-info-soft",   text: "text-primary-hover",   border: "border-info/25" },
  EN_ROUTE_TO_PICKUP:     { bg: "bg-violet-50",   text: "text-violet-700",   border: "border-violet-200", label: "En route to pickup" },
  PICKED_UP:              { bg: "bg-violet-50",   text: "text-violet-700",   border: "border-violet-200", label: "Picked up" },
  IN_TRANSIT:             { bg: "bg-violet-50",   text: "text-violet-800",   border: "border-violet-200", label: "In transit" },
  AT_DESTINATION:         { bg: "bg-teal-50",     text: "text-teal-700",     border: "border-teal-200", label: "At destination" },
  ATTEMPTED:              { bg: "bg-orange-50",   text: "text-orange-700",   border: "border-orange-200" },
  DELIVERED:              { bg: "bg-success-soft",  text: "text-primary-hover",  border: "border-success/25" },
  PARTIALLY_DELIVERED:    { bg: "bg-success-soft",  text: "text-primary-hover",  border: "border-success/25", label: "Partially delivered" },
  FAILED:                 { bg: "bg-danger-soft",     text: "text-danger",     border: "border-danger/28" },
  RETURNED:               { bg: "bg-orange-50",   text: "text-orange-700",   border: "border-orange-200" },
  CANCELLED:              { bg: "bg-neutral-100",    text: "text-muted-foreground",     border: "border-border" },
  DISPUTED:               { bg: "bg-danger-soft",     text: "text-danger",     border: "border-danger/28" },
  CLOSED:                 { bg: "bg-neutral-100",    text: "text-foreground",     border: "border-border" },
};

const PRIORITY_THEMES: Record<string, string> = {
  ROUTINE:   "bg-background text-foreground border-border",
  STANDARD:  "bg-sky-50 text-sky-700 border-sky-200",
  URGENT:    "bg-warning-soft text-warning-foreground border-warning/35",
  EMERGENCY: "bg-danger-soft text-danger border-danger/28 font-semibold",
};

interface NhumeStatusChipProps {
  status: string;
  className?: string;
}

export function NhumeStatusChip({ status, className }: NhumeStatusChipProps) {
  const key = String(status ?? "").toUpperCase();
  const theme = STATUS_THEMES[key] ?? { bg: "bg-background", text: "text-foreground", border: "border-border" };
  const label = theme.label ?? key.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
  return (
    <span className={cx(
      "inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium",
      theme.bg, theme.text, theme.border, className,
    )}>
      {label}
    </span>
  );
}

export function NhumePriorityChip({ priority, className }: { priority?: string; className?: string }) {
  const key = String(priority ?? "STANDARD").toUpperCase();
  const theme = PRIORITY_THEMES[key] ?? PRIORITY_THEMES.STANDARD;
  return (
    <span className={cx("inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] uppercase tracking-wide", theme, className)}>
      {key.toLowerCase()}
    </span>
  );
}
