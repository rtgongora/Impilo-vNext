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
  DRAFT:                  { bg: "bg-gray-50",     text: "text-gray-700",     border: "border-gray-200" },
  SUBMITTED:              { bg: "bg-impilo-50",   text: "text-impilo-700",   border: "border-impilo-200" },
  VALIDATION_REQUIRED:    { bg: "bg-amber-50",    text: "text-amber-800",    border: "border-amber-200", label: "Validation Required" },
  AWAITING_APPROVAL:      { bg: "bg-amber-50",    text: "text-amber-800",    border: "border-amber-200", label: "Awaiting Approval" },
  APPROVED:               { bg: "bg-emerald-50",  text: "text-emerald-700",  border: "border-emerald-200" },
  REJECTED:               { bg: "bg-rose-50",     text: "text-rose-700",     border: "border-rose-200" },
  AWAITING_PAYMENT:       { bg: "bg-amber-50",    text: "text-amber-800",    border: "border-amber-200", label: "Awaiting Payment" },
  AWAITING_STOCK:         { bg: "bg-amber-50",    text: "text-amber-800",    border: "border-amber-200", label: "Awaiting Stock" },
  AWAITING_PICKUP:        { bg: "bg-sky-50",      text: "text-sky-700",      border: "border-sky-200", label: "Awaiting Pickup" },
  DISPATCH_PENDING:       { bg: "bg-sky-50",      text: "text-sky-700",      border: "border-sky-200", label: "Dispatch Pending" },
  ASSIGNED:               { bg: "bg-indigo-50",   text: "text-indigo-700",   border: "border-indigo-200" },
  ACCEPTED:               { bg: "bg-indigo-50",   text: "text-indigo-700",   border: "border-indigo-200" },
  EN_ROUTE_TO_PICKUP:     { bg: "bg-violet-50",   text: "text-violet-700",   border: "border-violet-200", label: "En route to pickup" },
  PICKED_UP:              { bg: "bg-violet-50",   text: "text-violet-700",   border: "border-violet-200", label: "Picked up" },
  IN_TRANSIT:             { bg: "bg-violet-50",   text: "text-violet-800",   border: "border-violet-200", label: "In transit" },
  AT_DESTINATION:         { bg: "bg-teal-50",     text: "text-teal-700",     border: "border-teal-200", label: "At destination" },
  ATTEMPTED:              { bg: "bg-orange-50",   text: "text-orange-700",   border: "border-orange-200" },
  DELIVERED:              { bg: "bg-emerald-50",  text: "text-emerald-800",  border: "border-emerald-200" },
  PARTIALLY_DELIVERED:    { bg: "bg-emerald-50",  text: "text-emerald-700",  border: "border-emerald-200", label: "Partially delivered" },
  FAILED:                 { bg: "bg-rose-50",     text: "text-rose-700",     border: "border-rose-200" },
  RETURNED:               { bg: "bg-orange-50",   text: "text-orange-700",   border: "border-orange-200" },
  CANCELLED:              { bg: "bg-gray-100",    text: "text-gray-600",     border: "border-gray-200" },
  DISPUTED:               { bg: "bg-rose-50",     text: "text-rose-700",     border: "border-rose-200" },
  CLOSED:                 { bg: "bg-gray-100",    text: "text-gray-700",     border: "border-gray-200" },
};

const PRIORITY_THEMES: Record<string, string> = {
  ROUTINE:   "bg-gray-50 text-gray-700 border-gray-200",
  STANDARD:  "bg-sky-50 text-sky-700 border-sky-200",
  URGENT:    "bg-amber-50 text-amber-800 border-amber-200",
  EMERGENCY: "bg-rose-50 text-rose-700 border-rose-200 font-semibold",
};

interface NhumeStatusChipProps {
  status: string;
  className?: string;
}

export function NhumeStatusChip({ status, className }: NhumeStatusChipProps) {
  const key = String(status ?? "").toUpperCase();
  const theme = STATUS_THEMES[key] ?? { bg: "bg-gray-50", text: "text-gray-700", border: "border-gray-200" };
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
