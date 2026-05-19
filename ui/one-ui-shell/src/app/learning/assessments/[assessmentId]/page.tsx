"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoAssessment, useFundoAssessmentAttempts } from "@/hooks/queries/useFundoLms";

export default function AssessmentPage() {
  const params = useParams<{ assessmentId: string }>();
  const assessmentId = params?.assessmentId;
  const subject = useLearningSubject();
  const { data } = useFundoAssessment(assessmentId);
  const { data: attemptsData } = useFundoAssessmentAttempts(assessmentId, subject);
  const assessment = ((data?.data as Record<string, unknown>)?.assessment ?? {}) as Record<string, unknown>;
  const questions = (assessment.questions as Array<Record<string, unknown>> | undefined) ?? [];
  const maxAttempts = Number(assessment.maxAttempts ?? 0);
  const attempts = ((((attemptsData?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean));
  const remaining = maxAttempts > 0 ? Math.max(0, maxAttempts - attempts.length) : null;
  const canAttempt = remaining === null || remaining > 0;

  return (
    <AppLayout>
      <PageShell title={String(assessment.title ?? "Assessment")} subtitle="Assessment detail and question preview.">
        <div className="mb-3 flex items-center justify-between gap-4 rounded border border-gray-200 bg-white p-3">
          <div className="text-sm">
            <p>Attempts used: {attempts.length}{maxAttempts > 0 ? ` / ${maxAttempts}` : ""}</p>
            <p className="text-xs text-gray-500">{canAttempt ? "You can start a new attempt." : "Maximum attempts reached."}</p>
          </div>
          {canAttempt ? (
            <Link href={`/learning/assessments/${assessmentId}/attempt`} className="inline-block text-sm text-teal-700 hover:underline">
              Start attempt
            </Link>
          ) : null}
        </div>
        <ul className="space-y-2">
          {questions.map((q) => (
            <li key={String(q.id)} className="rounded border border-gray-200 bg-white p-3 text-sm">
              <p className="font-medium text-gray-900">{String(q.prompt)}</p>
              <p className="text-xs text-gray-500">{String(q.type)}</p>
            </li>
          ))}
        </ul>
        {attempts.length > 0 ? (
          <div className="mt-4 rounded border border-gray-200 bg-white p-3">
            <p className="text-sm font-medium text-gray-900">Attempt history</p>
            <ul className="mt-2 space-y-1 text-sm">
              {attempts.map((attempt) => (
                <li key={String(attempt.id)} className="flex items-center justify-between">
                  <span>Attempt {String(attempt.attemptNo ?? "-")} - score {String(attempt.score ?? "PENDING")}</span>
                  <Link className="text-teal-700 hover:underline" href={`/learning/attempts/${attempt.id}`}>
                    View
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
