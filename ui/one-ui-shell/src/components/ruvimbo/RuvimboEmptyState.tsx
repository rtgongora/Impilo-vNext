"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Sparkles } from "lucide-react";

export interface RuvimboAction {
  label: string;
  href?: string;
  onClick?: () => void;
  /** Primary = filled; secondary = outline. */
  variant?: "primary" | "secondary";
}

/**
 * The Ruvimbo empty-state standard. A dead "No X" string is never acceptable — every
 * empty section must (1) explain what the feature does, (2) say when data will appear,
 * (3) offer at least one working next action, and optionally (4) surface Nompilo guidance.
 */
export function RuvimboEmptyState({
  icon,
  title,
  explanation,
  when,
  actions,
  guidance,
}: {
  icon?: ReactNode;
  title: string;
  explanation: string;
  /** Plain-language note on when/how information appears here. */
  when?: string;
  actions?: RuvimboAction[];
  /** Optional Nompilo guidance line. */
  guidance?: string;
}) {
  return (
    <div className="rounded-lg border border-dashed border-border bg-muted/30 px-4 py-6 text-center">
      {icon ? <div className="mx-auto mb-2 flex justify-center text-muted-foreground">{icon}</div> : null}
      <p className="text-sm font-semibold text-foreground">{title}</p>
      <p className="mx-auto mt-1 max-w-md text-sm text-muted-foreground">{explanation}</p>
      {when ? <p className="mx-auto mt-1 max-w-md text-xs text-muted-foreground">{when}</p> : null}

      {actions && actions.length > 0 ? (
        <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
          {actions.map((a) => {
            const cls =
              a.variant === "secondary"
                ? "rounded-lg border border-border bg-background px-3 py-1.5 text-sm font-medium text-foreground hover:bg-muted"
                : "rounded-lg bg-violet-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-violet-700";
            return a.href ? (
              <Link key={a.label} href={a.href} className={cls}>
                {a.label}
              </Link>
            ) : (
              <button key={a.label} type="button" onClick={a.onClick} className={cls}>
                {a.label}
              </button>
            );
          })}
        </div>
      ) : null}

      {guidance ? (
        <p className="mx-auto mt-4 flex max-w-md items-start gap-1.5 rounded-lg bg-violet-50 px-3 py-2 text-left text-xs text-violet-800">
          <Sparkles className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
          <span>{guidance}</span>
        </p>
      ) : null}
    </div>
  );
}
