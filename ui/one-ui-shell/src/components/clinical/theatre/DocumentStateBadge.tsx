"use client";

/**
 * Wave 6 §19 — a clear, colour-AND-icon distinct badge for a clinical document's lifecycle
 * state, so draft / signed / amended / final are never confused at a glance (a signed
 * operative note must look unmistakably different from a draft). Colour is never the only
 * signal — each state also carries a distinct icon and label — so it reads for colour-vision
 * deficiency and in greyscale.
 */

import { FilePen, FileCheck2, FileClock, FileLock2, type LucideIcon } from "lucide-react";

export type DocumentState = "DRAFT" | "SIGNED" | "AMENDED" | "FINAL";

interface StateStyle {
  label: string;
  icon: LucideIcon;
  className: string;
  dot: string;
}

const STATES: Record<DocumentState, StateStyle> = {
  DRAFT: {
    label: "Draft",
    icon: FilePen,
    className: "border-dashed border-amber-400 bg-amber-50 text-amber-800",
    dot: "bg-amber-500",
  },
  SIGNED: {
    label: "Signed",
    icon: FileCheck2,
    className: "border-emerald-400 bg-emerald-50 text-emerald-800",
    dot: "bg-emerald-500",
  },
  AMENDED: {
    label: "Amended",
    icon: FileClock,
    className: "border-violet-400 bg-violet-50 text-violet-800",
    dot: "bg-violet-500",
  },
  FINAL: {
    label: "Final",
    icon: FileLock2,
    className: "border-slate-700 bg-slate-800 text-white",
    dot: "bg-slate-300",
  },
};

export function DocumentStateBadge({ state, className = "" }: { state: DocumentState; className?: string }) {
  const s = STATES[state] ?? STATES.DRAFT;
  const Icon = s.icon;
  return (
    <span
      data-testid="doc-state-badge"
      data-state={state}
      role="status"
      aria-label={`Document state: ${s.label}`}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${s.className} ${className}`}
    >
      <span className={`inline-block h-1.5 w-1.5 rounded-full ${s.dot}`} aria-hidden="true" />
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {s.label}
    </span>
  );
}

export default DocumentStateBadge;
