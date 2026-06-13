"use client";

import Link from "next/link";
import { ArrowRight, IdCard, Shield } from "lucide-react";

export function CitizenOnboardingOrchestrationRail() {
  return (
    <section
      className="rounded-xl border border-border bg-card p-4 shadow-sm"
      data-testid="citizen-onboarding-orchestration-rail"
    >
      <div className="flex items-start gap-3 text-sm text-foreground">
        <Shield className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
        <div className="space-y-2">
          <p className="font-medium text-foreground">Citizen onboarding → Health ID issuance</p>
          <p>
            Registration establishes person identity (CITIZEN). Issuance queue operators proof, approve, issue, and
            deliver cards; pickup verification closes the loop at facility handover.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Link
              href="/operations/vito/issuance"
              className="inline-flex items-center gap-1 rounded-lg bg-primary-hover px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700"
            >
              <IdCard className="h-3.5 w-3.5" />
              Issuance queue
            </Link>
            <Link
              href="/operations/vito/cards/pickup"
              className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background"
            >
              Card pickup verify
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
