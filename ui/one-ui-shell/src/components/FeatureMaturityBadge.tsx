"use client";

type FeatureMaturity =
  | "live"
  | "connected"
  | "partial"
  | "fixture"
  | "prototype"
  | "not_wired"
  | "requires_backend"
  | "blocked";

const STYLE_MAP: Record<FeatureMaturity, string> = {
  live: "border-success/25 bg-success-soft text-primary-hover",
  connected: "border-info/25 bg-info-soft text-primary-hover",
  partial: "border-warning/35 bg-warning-soft text-warning-foreground",
  fixture: "border-border bg-neutral-100 text-muted-foreground",
  prototype: "border-warning/35 bg-warning-soft text-warning-foreground",
  not_wired: "border-border bg-neutral-100 text-muted-foreground",
  requires_backend: "border-warning/35 bg-warning-soft text-warning-foreground",
  blocked: "border-danger/28 bg-danger-soft text-danger",
};

const LABEL_MAP: Record<FeatureMaturity, string> = {
  live: "Live",
  connected: "Connected",
  partial: "Partial",
  fixture: "Fixture",
  prototype: "Prototype",
  not_wired: "Not wired",
  requires_backend: "Requires backend",
  blocked: "Blocked",
};

interface FeatureMaturityBadgeProps {
  status: FeatureMaturity;
  detail?: string;
}

export function FeatureMaturityBadge({ status, detail }: FeatureMaturityBadgeProps) {
  return (
    <div
      className={`inline-flex items-center gap-2 rounded-full border px-2.5 py-1 text-xs font-medium ${STYLE_MAP[status]}`}
      title={detail}
      aria-label={detail ? `${LABEL_MAP[status]}: ${detail}` : LABEL_MAP[status]}
    >
      {LABEL_MAP[status]}
    </div>
  );
}
