"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { AdministrationGovernanceShell } from "./AdministrationGovernanceShell";
import { FriendlyBlockedState } from "./FriendlyBlockedState";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { OnboardReviewSubmit } from "./OnboardReviewSubmit";
import { ONBOARD_ACTOR_OPTIONS, ONBOARD_FLOW_STEPS, tileEnabled } from "@/lib/administration-governance";

interface OnboardFlowWizardProps {
  flow: string;
}

export function OnboardFlowWizard({ flow }: OnboardFlowWizardProps) {
  const { contract } = useSessionExperienceContract();
  const option = ONBOARD_ACTOR_OPTIONS.find((o) => o.id === flow);
  const steps = ONBOARD_FLOW_STEPS[flow];

  if (!option || !steps) {
    return (
      <AdministrationGovernanceShell title="Onboard New Actor" requireGovernanceEntry={false}>
        <FriendlyBlockedState state="policy_denied" />
      </AdministrationGovernanceShell>
    );
  }

  const allowed = contract ? tileEnabled(contract, option.requiredWorkspaces) : false;

  return (
    <AdministrationGovernanceShell title={steps.title} subtitle={option.outcome} requireGovernanceEntry={false}>
      <div className="space-y-6">
        <Link href="/work/administration-governance/onboard" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" />
          Onboard New Actor
        </Link>

        {!allowed ? (
          <FriendlyBlockedState state={contract?.friendlyResolutionState ?? "policy_denied"} />
        ) : (
          <>
            <div className="rounded-xl border border-border bg-card p-5">
              <p className="text-sm text-foreground">{option.description}</p>
              <p className="mt-2 text-sm font-medium text-primary-hover">Outcome: {option.outcome}</p>
            </div>

            <ol className="space-y-3">
              {steps.steps.map((step, index) => (
                <li key={step} className="flex gap-3 rounded-xl border border-border bg-card p-4">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-sm font-semibold text-primary-hover">
                    {index + 1}
                  </span>
                  <div>
                    <p className="font-medium text-foreground">{step}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Guided step — BFF workforce-governance APIs apply OPA precheck on submit.</p>
                  </div>
                </li>
              ))}
            </ol>

            <OnboardReviewSubmit flowId={flow} option={option} />

            <div className="flex flex-wrap gap-3">
              <Link
                href="/work/administration-governance/access-requests"
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800"
              >
                Continue to approvals
              </Link>
              <Link href="/work/administration-governance/access-requests" className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background">
                View pending approvals
              </Link>
            </div>
          </>
        )}
      </div>
    </AdministrationGovernanceShell>
  );
}
