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
  live: "border-emerald-200 bg-emerald-50 text-emerald-700",
  connected: "border-blue-200 bg-blue-50 text-blue-700",
  partial: "border-amber-200 bg-amber-50 text-amber-700",
  fixture: "border-fuchsia-200 bg-fuchsia-50 text-fuchsia-700",
  prototype: "border-purple-200 bg-purple-50 text-purple-700",
  not_wired: "border-rose-200 bg-rose-50 text-rose-700",
  requires_backend: "border-slate-200 bg-slate-50 text-slate-700",
  blocked: "border-slate-300 bg-slate-100 text-slate-800",
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
