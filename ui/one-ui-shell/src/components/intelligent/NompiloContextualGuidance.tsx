"use client";

/**
 * Nompilo Contextual Guidance — the on-platform guide panel.
 *
 * Renders the route-aware guidance set resolved by the BFF NompiloGuidanceController from real
 * service signals (trust, profile, payments, provider check-in, facility setup). Every card ties to
 * a real owning service and routes there — there are no dead buttons and no demo-only guidance.
 * Locked-state items explain *why* something is restricted and the safe next step; next-best-action
 * items rank what to do next; orientation items welcome/explain. Cards can be dismissed, and where
 * the catalogue allows it a Khuluma follow-up can be requested (Nompilo requests; Khuluma sends).
 *
 * This generalises the wallet "What to do next" pattern (WalletOverviewService.nextActions) into a
 * reusable, config-driven surface usable on any route — it deepens that pattern rather than forking it.
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { Sparkles, ArrowUpRight, Lock, ListChecks, Info, AlertTriangle, X, BellRing, GraduationCap } from "lucide-react";
import {
  useNompiloContext,
  useDismissGuidance,
  useRequestFollowUp,
  type NompiloGuidanceItem,
  type NompiloSeverity,
} from "@/hooks/queries/useNompilo";

const SEVERITY_STYLE: Record<NompiloSeverity, { wrap: string; chip: string; icon: string }> = {
  INFO: { wrap: "border-border bg-card", chip: "text-muted-foreground", icon: "text-primary" },
  RECOMMEND: { wrap: "border-sky-200 bg-sky-50", chip: "text-sky-700", icon: "text-sky-600" },
  WARN: { wrap: "border-amber-200 bg-warning-soft", chip: "text-amber-700", icon: "text-amber-700" },
  CRITICAL: { wrap: "border-red-200 bg-red-50", chip: "text-red-700", icon: "text-red-600" },
};

function typeIcon(type: string) {
  switch (type) {
    case "LOCKED_STATE":
      return Lock;
    case "CHECKLIST_ITEM":
      return ListChecks;
    case "PROTOCOL":
      return GraduationCap;
    case "ORIENTATION":
      return Info;
    default:
      return AlertTriangle;
  }
}

function GuidanceCard({ item, routePath }: { item: NompiloGuidanceItem; routePath: string }) {
  const dismiss = useDismissGuidance();
  const followUp = useRequestFollowUp();
  const style = SEVERITY_STYLE[item.severity] ?? SEVERITY_STYLE.INFO;
  const Icon = typeIcon(item.type);

  return (
    <li
      className={`rounded-lg border p-3 ${style.wrap}`}
      data-testid={`nompilo-guidance-${item.key}`}
      aria-label={item.accessibilityNote ?? item.title}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${style.icon}`} aria-hidden />
          <div>
            <p className="text-sm font-medium text-foreground">{item.title}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">{item.body}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => dismiss.mutate(item.key)}
          className="rounded p-1 text-muted-foreground hover:bg-card hover:text-foreground"
          aria-label={`Dismiss ${item.title}`}
        >
          <X className="h-3.5 w-3.5" aria-hidden />
        </button>
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-2">
        {item.ctaRoute ? (
          <Link
            href={item.ctaRoute}
            className="inline-flex items-center gap-1 rounded-md border border-border bg-card px-2.5 py-1 text-xs font-medium text-foreground hover:bg-neutral-50"
          >
            {item.ctaLabel ?? "Open"}
            <ArrowUpRight className="h-3 w-3" aria-hidden />
          </Link>
        ) : null}

        {item.khulumaFollowUpAvailable ? (
          <button
            type="button"
            disabled={followUp.isPending || followUp.isSuccess}
            onClick={() =>
              followUp.mutate({ guidanceKey: item.key, reason: item.title, routePath })
            }
            className="inline-flex items-center gap-1 rounded-md border border-border bg-card px-2.5 py-1 text-xs font-medium text-foreground hover:bg-neutral-50 disabled:opacity-60"
          >
            <BellRing className="h-3 w-3" aria-hidden />
            {followUp.isSuccess ? "Follow-up requested" : "Remind me"}
          </button>
        ) : null}

        {item.ownerService ? (
          <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
            via {item.ownerService}
          </span>
        ) : null}
      </div>
    </li>
  );
}

export function NompiloContextualGuidance({
  routePath,
  title = "Nompilo guidance",
  className = "",
  announceMs = 5500,
}: {
  routePath: string;
  title?: string;
  className?: string;
  /** How long Nompilo "speaks" in full before fading to a resting whisper. */
  announceMs?: number;
}) {
  const { data, isLoading, isError } = useNompiloContext(routePath);
  const items = data?.data?.guidance ?? [];

  // Nompilo whispers: it announces in full, then fades to a compact resting state. Hover or
  // keyboard-focus the panel to bring it back. The detail always stays in the DOM (collapsed via
  // opacity/height, never removed) so it remains screen-reader + keyboard accessible.
  const [resting, setResting] = useState(false);
  useEffect(() => {
    if (items.length === 0) return;
    const t = setTimeout(() => setResting(true), announceMs);
    return () => clearTimeout(t);
  }, [items.length, announceMs]);

  // Empty / loading / error states are quiet — Nompilo never invents guidance to fill space.
  if (isLoading || isError || items.length === 0) {
    return null;
  }

  const top = items[0];
  const reveal = "group-hover:max-h-[40rem] group-hover:opacity-100 group-focus-within:max-h-[40rem] group-focus-within:opacity-100";

  return (
    <section
      className={`group relative rounded-lg border border-border bg-card p-4 transition-all duration-300 motion-reduce:transition-none ${
        resting ? "opacity-80 hover:opacity-100 focus-within:opacity-100" : "opacity-100"
      } ${className}`}
      data-testid="nompilo-contextual-guidance"
      data-state={resting ? "resting" : "announcing"}
      aria-label={title}
      tabIndex={0}
    >
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 shrink-0 text-primary" aria-hidden />
        <span className="text-sm font-semibold text-foreground">{title}</span>
        <span className="hidden text-[10px] uppercase tracking-wide text-muted-foreground sm:inline">
          On-platform guide
        </span>
        {data?.data?.degraded ? (
          <span className="text-[10px] text-muted-foreground">(some guidance unavailable)</span>
        ) : null}
        {/* Resting whisper: the top suggestion lingers as a one-line teaser; hover/focus reveals all. */}
        {resting ? (
          <span
            className="ml-auto flex min-w-0 items-center gap-1 text-xs text-muted-foreground group-hover:hidden group-focus-within:hidden"
            title={top.title}
          >
            <span className="truncate">{top.title}</span>
            {items.length > 1 ? (
              <span className="shrink-0 rounded-full bg-neutral-100 px-1.5 text-[10px] font-medium">
                +{items.length - 1}
              </span>
            ) : null}
          </span>
        ) : null}
      </div>

      <ul
        className={`space-y-2 overflow-hidden transition-all duration-300 motion-reduce:transition-none ${
          resting ? `mt-0 max-h-0 opacity-0 ${reveal} group-hover:mt-2 group-focus-within:mt-2` : "mt-2 max-h-[40rem] opacity-100"
        }`}
      >
        {items.map((item) => (
          <GuidanceCard key={item.key} item={item} routePath={routePath} />
        ))}
      </ul>
    </section>
  );
}
