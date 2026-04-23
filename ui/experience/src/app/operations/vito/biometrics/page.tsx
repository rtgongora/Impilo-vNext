"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useEvaluateBiometricPolicy } from "@/hooks/queries/useBiometricPolicy";
import { Fingerprint, ShieldAlert, ShieldCheck } from "lucide-react";

const SUBJECTS = ["CLIENT", "PROVIDER"] as const;
const INTENTS = ["ENROLL", "VERIFY", "IDENTIFY", "DEDUP_SUPPORT", "STEP_UP"] as const;
const CONTEXTS = ["FACILITY", "COMMUNITY", "VIRTUAL", "LOGISTICS"] as const;

export default function VitoBiometricsGovernancePage() {
  const evaluate = useEvaluateBiometricPolicy();
  const [subjectType, setSubjectType] = useState<(typeof SUBJECTS)[number]>("CLIENT");
  const [workflowType, setWorkflowType] = useState("POINT_OF_CARE");
  const [contextType, setContextType] = useState<(typeof CONTEXTS)[number]>("FACILITY");
  const [modality, setModality] = useState("FINGERPRINT");
  const [biometricIntent, setBiometricIntent] = useState<(typeof INTENTS)[number]>("VERIFY");

  const outcomeBadge = useMemo(() => {
    const o = evaluate.data?.policyOutcome?.toUpperCase() ?? "";
    if (o === "PROHIBITED") return "bg-red-50 text-red-800 border-red-100";
    if (o === "REQUIRED") return "bg-amber-50 text-amber-900 border-amber-100";
    if (o === "RESTRICTED") return "bg-orange-50 text-orange-900 border-orange-100";
    return "bg-emerald-50 text-emerald-900 border-emerald-100";
  }, [evaluate.data?.policyOutcome]);

  return (
    <AppLayout>
      <PageShell
        title="Biometric governance"
        subtitle="Policy preview for client and provider workflows (Tshepo evaluation via BFF). This page does not capture or display biometric samples."
        icon={<Fingerprint className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <h2 className="text-sm font-semibold text-gray-900">Evaluate policy</h2>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Subject type
                  <select
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={subjectType}
                    onChange={(e) => setSubjectType(e.target.value as (typeof SUBJECTS)[number])}
                  >
                    {SUBJECTS.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Context
                  <select
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={contextType}
                    onChange={(e) => setContextType(e.target.value as (typeof CONTEXTS)[number])}
                  >
                    {CONTEXTS.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1 sm:col-span-2">
                  Workflow type
                  <input
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={workflowType}
                    onChange={(e) => setWorkflowType(e.target.value)}
                    placeholder="e.g. POINT_OF_CARE, CLIENT_REGISTRATION, PROVIDER_STEP_UP"
                  />
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Modality (optional)
                  <input
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={modality}
                    onChange={(e) => setModality(e.target.value)}
                  />
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Intent
                  <select
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={biometricIntent}
                    onChange={(e) => setBiometricIntent(e.target.value as (typeof INTENTS)[number])}
                  >
                    {INTENTS.map((i) => (
                      <option key={i} value={i}>
                        {i}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                disabled={evaluate.isPending}
                onClick={() =>
                  evaluate.mutate({
                    subjectType,
                    workflowType,
                    contextType,
                    modality: modality.trim() || null,
                    biometricIntent,
                  })
                }
              >
                {evaluate.isPending ? "Evaluating…" : "Evaluate"}
              </button>
              {evaluate.isError && (
                <p className="text-sm text-red-600">
                  Request failed. Ensure the Experience BFF and Tshepo service are reachable for your session.
                </p>
              )}
            </div>

            <div className={`rounded-2xl border p-5 space-y-3 ${outcomeBadge}`}>
              <div className="flex items-center gap-2 text-sm font-semibold">
                {evaluate.data?.policyOutcome === "PROHIBITED" ? (
                  <ShieldAlert className="h-5 w-5" />
                ) : (
                  <ShieldCheck className="h-5 w-5" />
                )}
                Policy outcome
              </div>
              {evaluate.data ? (
                <>
                  <p className="text-2xl font-bold tracking-tight">{evaluate.data.policyOutcome}</p>
                  <ul className="text-xs space-y-1 text-gray-700">
                    <li>Enrollment: {String(evaluate.data.enrollmentAllowed)}</li>
                    <li>Verification / step-up lane: {String(evaluate.data.verificationAllowed)}</li>
                    <li>Identification (1:N): {String(evaluate.data.identificationAllowed)}</li>
                    <li>Dedup assist: {String(evaluate.data.dedupSupportAllowed)}</li>
                    <li>Fallback allowed: {String(evaluate.data.fallbackAllowed)}</li>
                    {evaluate.data.matchedRuleId != null && <li>Matched rule id: {evaluate.data.matchedRuleId}</li>}
                  </ul>
                  {evaluate.data.reasons?.length ? (
                    <div className="text-xs text-gray-600 border-t border-black/5 pt-2 mt-2">
                      {evaluate.data.reasons.map((r) => (
                        <p key={r}>{r}</p>
                      ))}
                    </div>
                  ) : null}
                </>
              ) : (
                <p className="text-sm text-gray-600">Run an evaluation to see Tshepo&apos;s decision for this slice.</p>
              )}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
