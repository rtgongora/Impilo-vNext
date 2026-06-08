"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoCpdEvidence } from "@/hooks/queries/useFundoLms";
import { useAuthStore } from "@/hooks/useAuthStore";

export default function CpdEvidencePage() {
  const subject = useLearningSubject();
  const user = useAuthStore((state) => state.user);
  const { data } = useFundoCpdEvidence(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const evidence = (
    (payload.items as Array<Record<string, unknown>> | undefined) ??
    (payload.evidence as Array<Record<string, unknown>> | undefined) ??
    []
  );
  const providerId = user?.providerId ?? "";

  return (
    <AppLayout>
      <PageShell
        title="CPD evidence"
        subtitle="Fundo provides evidence and completion artifacts; council acceptance and ledger authority remain in Varapi."
      >
        <div className="mb-4 rounded-lg border border-indigo-200 bg-indigo-50 p-3 text-sm text-indigo-900">
          <p className="font-semibold">Council handoff</p>
          <p className="mt-1">
            Submit evidence packages to your regulator from Provider Council Self-Service and track acceptance/rejection there.
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Link
              href={providerId ? `/registry/provider-council/self-service?providerId=${encodeURIComponent(providerId)}` : "/registry/provider-council/self-service"}
              className="rounded border border-indigo-300 bg-white px-3 py-1.5 text-xs font-medium text-indigo-900 hover:bg-indigo-100"
            >
              Open council self-service
            </Link>
            <Link href="/home/credentials" className="rounded border border-indigo-300 bg-white px-3 py-1.5 text-xs font-medium text-indigo-900 hover:bg-indigo-100">
              Open credential status
            </Link>
          </div>
        </div>

        <ul className="space-y-2" data-testid="fundo-cpd-evidence-list">
          {evidence.map((e, index) => (
            <li
              key={String(e.certificateId ?? e.completionId ?? e.enrolmentId ?? index)}
              className="rounded border border-gray-200 bg-white p-3 text-sm"
            >
              <p className="font-medium text-gray-900">
                Course: {String(e.courseTitle ?? e.courseId ?? e.resourceId ?? "Course")}
              </p>
              <p>Certificate: {String(e.certificateId ?? "-")}</p>
              <p>Completed: {String(e.completedAt ?? e.issuedAt ?? "-")}</p>
              <p>Verification: {String(e.verifiedState ?? e.reviewState ?? "PENDING_REVIEW")}</p>
            </li>
          ))}
          {evidence.length === 0 ? <p className="text-sm text-gray-500">No CPD evidence yet.</p> : null}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
