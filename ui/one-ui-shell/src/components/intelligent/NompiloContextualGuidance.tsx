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
}: {
  routePath: string;
  title?: string;
  className?: string;
}) {
  const { data, isLoading, isError } = useNompiloContext(routePath);
  const items = data?.data?.guidance ?? [];

  // Empty / loading / error states are quiet — Nompilo never invents guidance to fill space.
  if (isLoading || isError || items.length === 0) {
    return null;
  }

  return (
    <section
      className={`rounded-lg border border-border bg-card p-4 ${className}`}
      data-testid="nompilo-contextual-guidance"
      aria-label={title}
    >
      <div className="mb-2 flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-primary" aria-hidden />
        <span className="text-sm font-semibold text-foreground">{title}</span>
        <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
          On-platform guide
        </span>
        {data?.data?.degraded ? (
          <span className="text-[10px] text-muted-foreground">(some guidance unavailable)</span>
        ) : null}
      </div>
      <ul className="space-y-2">
        {items.map((item) => (
          <GuidanceCard key={item.key} item={item} routePath={routePath} />
        ))}
      </ul>
    </section>
  );
}
