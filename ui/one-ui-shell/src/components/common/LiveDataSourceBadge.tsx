"use client";

type DataSource = "live" | "demo" | "mixed";

interface LiveDataSourceBadgeProps {
  source: DataSource;
  className?: string;
}

const LABELS: Record<DataSource, { text: string; classes: string }> = {
  live: { text: "Live data", classes: "bg-emerald-100 text-emerald-800 border-emerald-200" },
  demo: { text: "Demo data", classes: "bg-amber-100 text-amber-800 border-amber-200" },
  mixed: { text: "Live + demo", classes: "bg-sky-100 text-sky-800 border-sky-200" },
};

export function LiveDataSourceBadge({ source, className = "" }: LiveDataSourceBadgeProps) {
  const cfg = LABELS[source];
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${cfg.classes} ${className}`}
      title="Data source indicator for surfacing maturity"
    >
      {cfg.text}
    </span>
  );
}
