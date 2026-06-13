"use client";

import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoAttempt } from "@/hooks/queries/useFundoLms";

export default function AttemptResultPage() {
  const params = useParams<{ attemptId: string }>();
  const attemptId = params?.attemptId;
  const { data } = useFundoAttempt(attemptId);
  const attempt = ((data?.data as Record<string, unknown>)?.attempt ?? {}) as Record<string, unknown>;
  const pendingManualReview = String(attempt.passed ?? "").length === 0 || attempt.passed === null;

  return (
    <AppLayout>
      <PageShell title="Attempt result" subtitle="Score and pass/fail outcome for submitted native assessment attempt.">
        <div className="rounded border border-border bg-card p-4 text-sm">
          <p>Attempt: {String(attempt.id ?? attemptId)}</p>
          <p>Score: {String(attempt.score ?? "PENDING")}</p>
          <p>Passed: {String(attempt.passed ?? "PENDING_MANUAL")}</p>
          <p>Submitted: {String(attempt.submittedAt ?? "-")}</p>
          {pendingManualReview ? (
            <p className="mt-2 text-xs text-warning-foreground">
              This attempt includes responses that require manual review before final grading.
            </p>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
