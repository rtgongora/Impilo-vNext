"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { OpaPrecheckPanel, submitDisabledFromPrecheck } from "./OpaPrecheckPanel";
import { useOnboardingPrecheck, useSubmitOnboardingFlow } from "@/hooks/useAdminGovernance";
import type { OnboardActorOption } from "@/lib/administration-governance";
import type { AdminGovernanceActionResponse, OnboardingFlowSubmission } from "@/lib/admin-governance/types";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";

interface OnboardReviewSubmitProps {
  flowId: string;
  option: OnboardActorOption;
}

const FLOW_FIELDS: Record<string, Array<{ key: keyof OnboardingFlowSubmission | "profileKey"; label: string; placeholder?: string; type?: string }>> = {
  citizen: [
    { key: "targetHealthId", label: "Health ID / Impilo ID", placeholder: "H-..." },
    { key: "requestedScope", label: "Registration path", placeholder: "self_registered | assisted_registered" },
  ],
  "provider-worker": [
    { key: "targetHealthId", label: "Health ID", placeholder: "H-..." },
    { key: "targetProviderWorkerId", label: "Provider / Worker ID", placeholder: "P-..." },
    { key: "roleTemplate", label: "Profession / cadre", placeholder: "registered_nurse" },
  ],
  "external-partner-user": [
    { key: "organisationId", label: "Partner organisation ID" },
    { key: "country", label: "Country", placeholder: "ZW" },
    { key: "requestedDataScope", label: "Data scope", placeholder: "aggregate_only | deidentified_only" },
    { key: "requestedEnvironment", label: "Environment", placeholder: "sandbox | production_limited" },
    { key: "requestedDurationDays", label: "Duration (days)", type: "number" },
  ],
  "marketplace-user": [
    { key: "organisationId", label: "Vendor organisation ID" },
    { key: "requestedEnvironment", label: "Access environment", placeholder: "sandbox | production_limited" },
    { key: "roleTemplate", label: "Marketplace role", placeholder: "api_developer" },
  ],
};

function defaultFields(flowId: string) {
  return (
    FLOW_FIELDS[flowId] ?? [
      { key: "organisationId", label: "Organisation ID" },
      { key: "targetProviderWorkerId", label: "Target provider/worker or user ID" },
      { key: "roleTemplate", label: "Role template" },
      { key: "requestedScope", label: "Scope" },
    ]
  );
}

export function OnboardReviewSubmit({ flowId, option }: OnboardReviewSubmitProps) {
  const router = useRouter();
  const { contract } = useSessionExperienceContract();
  const [form, setForm] = useState<OnboardingFlowSubmission>({
    organisationId: contract?.organisation?.organisationId,
    requestedEnvironment:
      (contract?.organisation as { organisationAccessEnvironment?: string } | undefined)?.organisationAccessEnvironment,
  });
  const [submitResult, setSubmitResult] = useState<AdminGovernanceActionResponse | null>(null);
  const precheckMutation = useOnboardingPrecheck(flowId);
  const submitMutation = useSubmitOnboardingFlow(flowId);

  const fields = useMemo(() => defaultFields(flowId), [flowId]);

  useEffect(() => {
    precheckMutation.mutate(form);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const precheckResult = precheckMutation.data ?? null;
  const submitDisabled = submitDisabledFromPrecheck(precheckResult) || submitMutation.isPending;

  async function handlePrecheckRefresh() {
    const result = await precheckMutation.mutateAsync(form);
    return result;
  }

  async function handleSubmit() {
    const latest = await handlePrecheckRefresh();
    if (submitDisabledFromPrecheck(latest)) return;
    const result = await submitMutation.mutateAsync(form);
    setSubmitResult(result);
    if (result.requestId) {
      router.push(`/work/administration-governance/access-requests?focus=${result.requestId}`);
    }
  }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div>
        <h3 className="text-base font-semibold text-foreground">Review & Submit</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Actor type: <strong>{option.title}</strong>. Expected outcome: {option.outcome}
        </p>
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        {fields.map((field) => (
          <label key={String(field.key)} className="text-sm text-foreground">
            {field.label}
            <input
              className="mt-1 w-full rounded-lg border border-border px-3 py-2"
              type={field.type ?? "text"}
              placeholder={field.placeholder}
              value={String(form[field.key as keyof OnboardingFlowSubmission] ?? "")}
              onChange={(event) => {
                const value =
                  field.type === "number" ? Number(event.target.value || 0) : event.target.value;
                setForm((prev) => ({ ...prev, [field.key]: value }));
              }}
            />
          </label>
        ))}
        {flowId === "external-partner-user" ? (
          <label className="flex items-center gap-2 text-sm text-foreground md:col-span-2">
            <input
              type="checkbox"
              checked={!!form.crossBorderFlag}
              onChange={(event) => setForm((prev) => ({ ...prev, crossBorderFlag: event.target.checked }))}
            />
            Cross-border identified data requested
          </label>
        ) : null}
      </div>

      <div className="rounded-xl border border-border bg-background px-4 py-3 text-xs text-foreground">
        <p>
          Organisation: {contract?.organisation?.organisationName ?? contract?.organisation?.organisationId ?? "current scope"}
        </p>
        <p className="mt-1">Policy version: {contract?.policyMetadata?.contractVersion ?? "1.x"}</p>
        <p className="mt-1">Audit sensitivity: {flowId === "system-admin" || flowId === "external-partner-user" ? "HIGH" : "STANDARD"}</p>
      </div>

      <OpaPrecheckPanel
        result={precheckResult}
        isLoading={precheckMutation.isPending}
        errorMessage={precheckMutation.isError ? "Precheck failed" : undefined}
      />

      {submitResult ? <GovernanceActionResult result={submitResult} /> : null}

      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          onClick={() => void handlePrecheckRefresh()}
          className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background"
        >
          Refresh precheck
        </button>
        <button
          type="button"
          disabled={submitDisabled}
          onClick={() => void handleSubmit()}
          className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitMutation.isPending ? "Submitting…" : "Submit onboarding"}
        </button>
        <Link href="/work/administration-governance/access-requests" className="rounded-lg px-4 py-2 text-sm font-medium text-primary-hover hover:underline">
          View access requests
        </Link>
      </div>
    </section>
  );
}
