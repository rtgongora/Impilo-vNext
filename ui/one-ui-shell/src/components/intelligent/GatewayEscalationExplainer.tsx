"use client";

/**
 * Gateway Escalation Explainer — Nompilo as trust-escalation mediator (gateway
 * doctrine §9). Every meaningful trust escalation is explained, never announced as a
 * bare "authentication required": why (what is being protected), what level of
 * verification is needed, what happens next (progress saved — you return to this
 * exact step), and how to get help.
 *
 * Content is governed seed data served over the PUBLIC guidance lane
 * (/internal/v1/public/gateway/guidance/**) so it works while unauthenticated. If the
 * lane is unreachable the component falls back to minimal safe copy — the mediation
 * never degrades to a bare auth wall, and it never exposes security internals.
 */

import Link from "next/link";
import { ArrowRight, HelpCircle, Route, ShieldCheck, Sparkles } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface GatewayExplainStep {
  stepKey: string;
  pillar: string | null;
  rungFrom: string;
  rungTo: string;
  title: string;
  why: string;
  levelLabel: string;
  whatNext: string;
  howToGetHelp: string;
  continueWithoutLabel: string | null;
}

/** Safe fallback when the public guidance lane is unreachable (doctrine §9: still mediate). */
const FALLBACK_STEP: Omit<GatewayExplainStep, "stepKey"> = {
  pillar: null,
  rungFrom: "",
  rungTo: "",
  title: "Sign in to continue",
  why: "The next step works with information that belongs to you personally, so we need to be sure it is really you.",
  levelLabel: "Sign in with your account or Health ID — nothing more is needed for this step.",
  whatNext: "Your progress is saved. After you sign in you will return to this exact step.",
  howToGetHelp:
    "Use the recovery options on this page, or contact the helpdesk for assisted access — you will not have to start over.",
  continueWithoutLabel: "Continue without signing in",
};

function useExplainStep(stepKey: string) {
  return useQuery({
    queryKey: ["gateway-explain-step", stepKey],
    queryFn: async () => {
      const res = await apiClient.get<{ data: GatewayExplainStep }>(
        `/internal/v1/public/gateway/guidance/explain-steps/${encodeURIComponent(stepKey)}`,
      );
      return res.data;
    },
    staleTime: 5 * 60_000,
    retry: false,
  });
}

function ExplainRow({
  icon: Icon,
  label,
  text,
}: {
  icon: typeof ShieldCheck;
  label: string;
  text: string;
}) {
  return (
    <div className="flex items-start gap-2.5">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden />
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-foreground">{text}</p>
      </div>
    </div>
  );
}

export function GatewayEscalationExplainer({
  stepKey,
  continueWithoutHref = null,
  className = "",
}: {
  /** Escalation-point id, e.g. "signin-to-book", "verify-contact", "step-up-results". */
  stepKey: string;
  /**
   * Public page to return to when the activity genuinely permits staying anonymous
   * (doctrine §9: "Continue without signing in" wherever public access is possible).
   * Null hides the option.
   */
  continueWithoutHref?: string | null;
  className?: string;
}) {
  const { data, isLoading, isError } = useExplainStep(stepKey);

  // Quiet while loading — never flash placeholder mediation copy.
  if (isLoading) return null;

  const step: Omit<GatewayExplainStep, "stepKey"> = !isError && data ? data : FALLBACK_STEP;

  return (
    <section
      className={`rounded-xl border border-primary/20 bg-primary-soft/60 p-4 ${className}`}
      data-testid={`gateway-escalation-explainer-${stepKey}`}
      aria-label={step.title}
    >
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 shrink-0 text-primary" aria-hidden />
        <span className="text-sm font-semibold text-foreground">{step.title}</span>
        <span className="hidden text-[10px] uppercase tracking-wide text-muted-foreground sm:inline">
          Nompilo
        </span>
      </div>

      <div className="mt-3 space-y-3">
        <ExplainRow icon={ShieldCheck} label="Why we ask" text={step.why} />
        <ExplainRow icon={HelpCircle} label="What you need" text={step.levelLabel} />
        <ExplainRow icon={Route} label="What happens next" text={step.whatNext} />
        <ExplainRow icon={HelpCircle} label="If you get stuck" text={step.howToGetHelp} />
      </div>

      {continueWithoutHref ? (
        <Link
          href={continueWithoutHref}
          data-testid="gateway-continue-without"
          className="mt-3 inline-flex items-center gap-1.5 rounded-md border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-neutral-50"
        >
          {step.continueWithoutLabel ?? "Continue without signing in"}
          <ArrowRight className="h-3 w-3" aria-hidden />
        </Link>
      ) : null}
    </section>
  );
}
