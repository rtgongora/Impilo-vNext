"use client";

/**
 * Assurance Required Notice — the honest "why is this locked?" explainer.
 *
 * Presented when the action-trust matrix (lib/auth/action-trust-matrix.ts) blocks a
 * surface or action for the current assurance level. Per Health OS §10–§11 a locked
 * state must never be a dead end: it states WHY sign-in / verification is needed, WHICH
 * information is protected, the LEVEL required, WHAT the citizen can do AFTER upgrading,
 * and HOW to get help — with a clear CTA into the upgrade flow.
 *
 * It composes IdentityAssuranceBanner (the assurance contract: state, reason, available
 * permissions, upgrade pathways, next best step) so the explanation is driven by the same
 * server-side identity truth the rest of the shell uses — never re-invented here.
 */

import Link from "next/link";
import { ShieldAlert, ArrowUpRight, LifeBuoy } from "lucide-react";
import { IdentityAssuranceBanner } from "@/components/citizen/IdentityAssuranceBanner";
import {
  ASSURANCE_LABELS,
  type TrustDecision,
} from "@/lib/auth/action-trust-matrix";

interface AssuranceRequiredNoticeProps {
  decision: TrustDecision;
  /** Optional override for the upgrade CTA target (defaults to the decision's upgradePath). */
  upgradeHref?: string;
  /** Where to route the person if they choose to get help. */
  helpHref?: string;
  className?: string;
}

export function AssuranceRequiredNotice({
  decision,
  upgradeHref,
  helpHref = "/support",
  className = "",
}: AssuranceRequiredNoticeProps) {
  const requiredLabel = ASSURANCE_LABELS[decision.requires];
  const currentLabel = ASSURANCE_LABELS[decision.current];
  const cta = upgradeHref ?? decision.upgradePath ?? "/auth/register/assurance";

  return (
    <div
      role="status"
      className={`rounded-xl border border-warning/35 bg-warning-soft p-4 ${className}`}
    >
      <div className="flex items-start gap-3">
        <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" aria-hidden />
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-foreground">
            {requiredLabel} identity needed to continue
          </h3>

          {/* WHY + WHAT is protected */}
          <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
            {decision.label} protects {decision.protects}. To keep this safe, Impilo asks
            for a <span className="font-medium text-foreground">{requiredLabel}</span>{" "}
            identity before continuing. Your account is currently{" "}
            <span className="font-medium text-foreground">{currentLabel}</span>.
          </p>

          {/* AFTER — what unlocks once upgraded */}
          <p className="mt-2 text-xs text-muted-foreground leading-relaxed">
            Once your identity is {requiredLabel}, you can {decision.label.toLowerCase()} and
            it stays available — you will not need to start over.
          </p>

          {/* The assurance contract detail (state / permissions / next best step). */}
          <div className="mt-3">
            <IdentityAssuranceBanner />
          </div>

          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Link
              href={cta}
              data-testid="assurance-upgrade-cta"
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-xs font-medium text-white hover:bg-primary-hover transition-colors"
            >
              Verify my identity
              <ArrowUpRight className="h-3.5 w-3.5" aria-hidden />
            </Link>
            <Link
              href={helpHref}
              className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-2 text-xs font-medium text-foreground hover:bg-muted transition-colors"
            >
              <LifeBuoy className="h-3.5 w-3.5" aria-hidden />
              Get help
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
