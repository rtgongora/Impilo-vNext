"use client";

import type { ReactNode } from "react";
import { AlertTriangle } from "lucide-react";

type PlaneVariant = "registry" | "organization";

const STYLES: Record<
  PlaneVariant,
  { shell: string; eyebrow: string; title: string; body: string }
> = {
  registry: {
    shell: "border-amber-300/80 bg-gradient-to-r from-amber-50 via-amber-50/90 to-amber-100/50",
    eyebrow: "text-warning-foreground/90",
    title: "text-warning-foreground",
    body: "text-warning-foreground/85",
  },
  organization: {
    shell: "border-violet-300/80 bg-gradient-to-r from-violet-50 via-violet-50/90 to-slate-50",
    eyebrow: "text-violet-800/90",
    title: "text-violet-950",
    body: "text-violet-900/85",
  },
};

interface PlaneTrustBannerProps {
  variant: PlaneVariant;
  eyebrow: string;
  title: string;
  children: ReactNode;
}

export function PlaneTrustBanner({ variant, eyebrow, title, children }: PlaneTrustBannerProps) {
  const t = STYLES[variant];
  return (
    <div className={`rounded-2xl border p-5 shadow-sm ${t.shell}`}>
      <p className={`text-[10px] font-semibold uppercase tracking-[0.22em] ${t.eyebrow}`}>{eyebrow}</p>
      <h2 className={`mt-2 text-lg font-semibold tracking-tight ${t.title}`}>{title}</h2>
      <div className={`mt-2 text-sm leading-relaxed ${t.body}`}>{children}</div>
      {variant === "registry" && (
        <p className="mt-4 flex items-start gap-2 rounded-xl border border-warning/35/80 bg-card/60 px-3 py-2 text-xs text-warning-foreground">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-foreground" aria-hidden />
          <span>
            Actions on this plane may be audited under elevated trust rules. This is not the same as day-to-day
            Facility Work — keep registry changes deliberate and traceable.
          </span>
        </p>
      )}
    </div>
  );
}
