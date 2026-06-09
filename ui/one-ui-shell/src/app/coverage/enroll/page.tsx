"use client";

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, CheckCircle2, Loader2, Shield, UserCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useCoveragePlans,
  useEnrollCoverageMember,
  useRunCoverageCommand,
} from "@/hooks/queries/useCoverage";

export default function CoverageEnrollPage() {
  const clientId = useAuthStore((s) => s.user?.id) ?? "";
  const plansQ = useCoveragePlans();
  const enroll = useEnrollCoverageMember();
  const eligibility = useRunCoverageCommand();

  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [memberNumber, setMemberNumber] = useState("");
  const [eligibilityResult, setEligibilityResult] = useState<Record<string, unknown> | null>(null);
  const [enrolled, setEnrolled] = useState<Record<string, unknown> | null>(null);

  const plans = plansQ.data ?? [];

  const eligibilityPassed =
    eligibilityResult &&
    (eligibilityResult.eligible === true ||
      eligibilityResult.resultCode === "ELIGIBLE" ||
      eligibilityResult.result_code === "ELIGIBLE");

  const runEligibility = () => {
    if (!clientId || !selectedPlanId) return;
    eligibility.mutate(
      {
        command: "enrollment-eligibility",
        payload: {
          clientId,
          planId: selectedPlanId,
        },
      },
      {
        onSuccess: (data) => {
          const raw = data && typeof data === "object" ? (data as Record<string, unknown>) : {};
          const inner =
            raw.data && typeof raw.data === "object" ? (raw.data as Record<string, unknown>) : raw;
          setEligibilityResult(inner);
        },
      },
    );
  };

  const submitEnrollment = () => {
    if (!clientId || !selectedPlanId || !memberNumber.trim()) return;
    enroll.mutate(
      {
        clientId,
        planId: selectedPlanId,
        memberNumber: memberNumber.trim(),
        relationship: "SELF",
        status: "ACTIVE",
      },
      {
        onSuccess: (data) => {
          const raw = data && typeof data === "object" ? (data as Record<string, unknown>) : {};
          const inner =
            raw.data && typeof raw.data === "object" ? (raw.data as Record<string, unknown>) : raw;
          setEnrolled(inner);
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Enroll in coverage"
        subtitle="Citizen enrollment — eligibility check, plan selection, and member confirmation"
        icon={<Shield className="h-6 w-6 text-impilo-600" />}
      >
        <Link
          href="/coverage"
          className="mb-4 inline-flex items-center gap-2 text-sm font-medium text-impilo-700 hover:text-impilo-900"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to coverage operations
        </Link>

        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-2xl border border-gray-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-gray-900">1. Choose a plan</h2>
            <p className="mt-1 text-sm text-gray-600">Select an active coverage plan for enrollment.</p>
            {plansQ.isLoading ? (
              <div className="mt-4 flex items-center gap-2 text-sm text-gray-500">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading plans…
              </div>
            ) : (
              <select
                data-testid="coverage-enroll-plan-select"
                className="mt-4 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                value={selectedPlanId}
                onChange={(e) => {
                  setSelectedPlanId(e.target.value);
                  setEligibilityResult(null);
                  setEnrolled(null);
                }}
              >
                <option value="">Select plan</option>
                {plans.map((plan) => (
                  <option key={plan.id} value={plan.id}>
                    {plan.planName} ({plan.planCode})
                  </option>
                ))}
              </select>
            )}
          </section>

          <section className="rounded-2xl border border-gray-200 bg-white p-5">
            <h2 className="text-lg font-semibold text-gray-900">2. Eligibility check</h2>
            <p className="mt-1 text-sm text-gray-600">Verify coverage eligibility before enrolling.</p>
            <button
              type="button"
              data-testid="coverage-run-eligibility"
              onClick={runEligibility}
              disabled={!selectedPlanId || eligibility.isPending}
              className="mt-4 inline-flex items-center gap-2 rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {eligibility.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserCheck className="h-4 w-4" />}
              Run eligibility
            </button>
            {eligibilityResult && (
              <div
                data-testid="coverage-eligibility-result"
                className={`mt-3 rounded-lg border p-3 text-sm ${
                  eligibilityPassed
                    ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                    : "border-amber-200 bg-amber-50 text-amber-900"
                }`}
              >
                <p className="font-medium">
                  {(eligibilityResult.resultCode as string) ||
                    (eligibilityResult.result_code as string) ||
                    (eligibilityPassed ? "ELIGIBLE" : "INELIGIBLE")}
                </p>
                <p className="mt-1 text-xs">
                  {(eligibilityResult.resultMessage as string) ||
                    (eligibilityResult.result_message as string) ||
                    "Enrollment eligibility evaluated via coverage-service."}
                </p>
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-gray-200 bg-white p-5 lg:col-span-2">
            <h2 className="text-lg font-semibold text-gray-900">3. Confirm enrollment</h2>
            <p className="mt-1 text-sm text-gray-600">Create the coverage member record after eligibility passes.</p>
            <div className="mt-4 flex flex-col gap-3 sm:flex-row">
              <input
                type="text"
                data-testid="coverage-member-number"
                placeholder="Member number"
                value={memberNumber}
                onChange={(e) => setMemberNumber(e.target.value)}
                className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
              />
              <button
                type="button"
                data-testid="coverage-enroll-submit"
                onClick={submitEnrollment}
                disabled={!selectedPlanId || !memberNumber.trim() || !eligibilityPassed || enroll.isPending}
                className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {enroll.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                Enroll member
              </button>
            </div>
            {enrolled && (
              <div
                data-testid="coverage-enroll-success"
                className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800"
              >
                Enrollment submitted. Member record created via coverage-service.
                <Link
                  href="/coverage/member"
                  className="mt-2 inline-block text-sm font-medium text-emerald-900 underline"
                >
                  View member dashboard
                </Link>
              </div>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
