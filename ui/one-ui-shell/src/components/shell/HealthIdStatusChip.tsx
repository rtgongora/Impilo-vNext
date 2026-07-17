"use client";

/**
 * Health-ID Status Chip — always-visible assurance tier in the shell chrome.
 *
 * A compact, unobtrusive chip showing the citizen's Health-ID tier (Basic / Temporary /
 * Verified) and, when relevant, their next action. Tapping it opens the assurance
 * status / upgrade surface. The tier is the canonical three-level assuranceLevel from the
 * auth store; the next-best-step text is driven by the consolidated `useAssuranceStatus`
 * (typed AssuranceStatus with permissions / nextBestStep) — the same identity contract the
 * banner and explainers use.
 *
 * Shown only for authenticated citizens (not while a Provider ID is activated for work).
 */

import Link from "next/link";
import { ShieldCheck, ShieldAlert, Clock } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useAssuranceStatus } from "@/hooks/queries/useIdentity";
import { ASSURANCE_LABELS, type AssuranceLevel } from "@/lib/auth/action-trust-matrix";

const TIER_ICON: Record<AssuranceLevel, typeof ShieldCheck> = {
  UNVERIFIED: ShieldAlert,
  TEMPORARY: Clock,
  VERIFIED: ShieldCheck,
};

const TIER_STYLE: Record<AssuranceLevel, string> = {
  UNVERIFIED: "border-amber-300 bg-warning-soft text-warning-foreground",
  TEMPORARY: "border-amber-300 bg-warning-soft text-warning-foreground",
  VERIFIED: "border-emerald-300 bg-success-soft text-primary-hover",
};

export function HealthIdStatusChip() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);
  const providerActivated = user?.providerActivated ?? false;

  // Only citizens (person context). Suppressed during activated professional work.
  const enabled = isAuthenticated && !!user && !providerActivated;
  const { data } = useAssuranceStatus();

  if (!enabled) return null;

  const level: AssuranceLevel = user?.assuranceLevel ?? "UNVERIFIED";
  const Icon = TIER_ICON[level];
  const nextBestStep = data?.data?.nextBestStep;

  // Verified people don't need an upgrade CTA; everyone else routes into the tier chooser.
  const href = level === "VERIFIED" ? "/citizen/wallet/identity" : "/auth/register/assurance";
  const showNext = level !== "VERIFIED" && !!nextBestStep;

  return (
    <Link
      href={href}
      data-testid="health-id-status-chip"
      aria-label={`Health ID status: ${ASSURANCE_LABELS[level]}${showNext ? `. Next: ${nextBestStep}` : ""}`}
      title={showNext ? `${ASSURANCE_LABELS[level]} — ${nextBestStep}` : `Health ID: ${ASSURANCE_LABELS[level]}`}
      className={[
        "fixed right-2 top-2 z-[9000] inline-flex max-w-[70vw] items-center gap-1.5 rounded-full border px-2.5 py-1",
        "text-xs font-medium shadow-sm backdrop-blur transition-colors hover:shadow",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:ring-offset-1",
        TIER_STYLE[level],
      ].join(" ")}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden />
      <span className="shrink-0">{ASSURANCE_LABELS[level]}</span>
      {showNext ? (
        <span className="hidden truncate text-[11px] font-normal opacity-80 sm:inline">
          · {nextBestStep}
        </span>
      ) : null}
    </Link>
  );
}
