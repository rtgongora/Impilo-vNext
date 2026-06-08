"use client";

import Link from "next/link";
import { ArrowRight, IdCard, Shield } from "lucide-react";

export function CitizenOnboardingOrchestrationRail() {
  return (
    <section
      className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
      data-testid="citizen-onboarding-orchestration-rail"
    >
      <div className="flex items-start gap-3 text-sm text-slate-700">
        <Shield className="mt-0.5 h-5 w-5 shrink-0 text-impilo-600" />
        <div className="space-y-2">
          <p className="font-medium text-slate-900">Citizen onboarding → Health ID issuance</p>
          <p>
            Registration establishes person identity (CITIZEN). Issuance queue operators proof, approve, issue, and
            deliver cards; pickup verification closes the loop at facility handover.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Link
              href="/operations/vito/issuance"
              className="inline-flex items-center gap-1 rounded-lg bg-impilo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700"
            >
              <IdCard className="h-3.5 w-3.5" />
              Issuance queue
            </Link>
            <Link
              href="/operations/vito/cards/pickup"
              className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
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
