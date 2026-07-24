"use client";

import { ArrowRight, ShieldCheck } from "lucide-react";
import type { GatewayPillar } from "@/lib/gateway-intent";
import { ContinueWithoutSignIn } from "./ContinueWithoutSignIn";
import { IntentLink } from "./IntentLink";

export function ReasonedSignInPrompt({
  pillar,
  goal,
  destination,
  publicReturn,
  reason,
  actionLabel = "Sign in and securely continue",
}: {
  pillar: GatewayPillar;
  goal: string;
  destination: string;
  publicReturn: string;
  reason: string;
  actionLabel?: string;
}) {
  return (
    <section
      aria-label="Your access choices"
      data-testid="reasoned-sign-in-prompt"
      className="mt-8 rounded-2xl border border-sky-200 bg-sky-50 p-5"
    >
      <div className="flex items-start gap-3">
        <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-sky-700" aria-hidden />
        <div>
          <h2 className="text-base font-semibold text-slate-900">Continue your way</h2>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-slate-700">{reason}</p>
          <p className="mt-1 text-sm text-slate-600">
            Your current progress is kept during sign-in, and you return to the next step.
          </p>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-3">
        <ContinueWithoutSignIn href={publicReturn} />
        <IntentLink
          pillar={pillar}
          goal={goal}
          dest={destination}
          from={publicReturn}
          href="/auth/login"
          className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-sky-700 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-800"
        >
          {actionLabel}
          <ArrowRight className="h-4 w-4" aria-hidden />
        </IntentLink>
      </div>
    </section>
  );
}
