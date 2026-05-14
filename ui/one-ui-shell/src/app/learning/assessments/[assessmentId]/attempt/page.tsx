"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoAssessment, useSubmitFundoAttempt } from "@/hooks/queries/useFundoLms";

type AnyRecord = Record<string, unknown>;

function parseOptions(raw: unknown): string[] {
  if (typeof raw !== "string" || !raw.trim()) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.map((v) => String(v));
  } catch {
    // fall back to delimiter split
  }
  return raw.split("|").map((s) => s.trim()).filter(Boolean);
}

export default function AssessmentAttemptPage() {
  const params = useParams<{ assessmentId: string }>();
  const assessmentId = params?.assessmentId;
  const subject = useLearningSubject();
  const { data } = useFundoAssessment(assessmentId);
  const submitMutation = useSubmitFundoAttempt();
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [attemptId, setAttemptId] = useState<string | null>(null);
  const assessment = ((data?.data as AnyRecord)?.assessment ?? {}) as AnyRecord;
  const questions = ((assessment.questions as AnyRecord[]) ?? []).filter(Boolean);
  const attemptLimit = Number(assessment.maxAttempts ?? 0);

  const objectiveCount = useMemo(
    () => questions.filter((q) => ["MULTIPLE_CHOICE", "TRUE_FALSE"].includes(String(q.type))).length,
    [questions],
  );

  async function submit() {
    if (!assessmentId) return;
    const res = (await submitMutation.mutateAsync({
      assessmentId,
      body: { ...subject, answers },
    })) as AnyRecord;
    const id = String(((res.data as AnyRecord)?.attempt as AnyRecord)?.id ?? "");
    if (id) setAttemptId(id);
  }

  return (
    <AppLayout>
      <PageShell title="Assessment attempt" subtitle={`Objective questions: ${objectiveCount} • non-objective answers remain pending/manual.`}>
        {attemptLimit > 0 ? (
          <p className="mb-3 text-xs text-gray-500">Maximum attempts allowed: {attemptLimit}</p>
        ) : null}
        <div className="space-y-3">
          {questions.map((q) => (
            <div key={String(q.id)} className="rounded border border-gray-200 bg-white p-3">
              <p className="text-sm font-medium text-gray-900">{String(q.prompt)}</p>
              {String(q.type) === "TRUE_FALSE" ? (
                <select
                  className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm"
                  onChange={(e) => setAnswers((prev) => ({ ...prev, [String(q.id)]: e.target.value }))}
                  defaultValue=""
                >
                  <option value="" disabled>Select answer</option>
                  <option value="true">True</option>
                  <option value="false">False</option>
                </select>
              ) : String(q.type) === "MULTIPLE_CHOICE" ? (
                <select
                  className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm"
                  onChange={(e) => setAnswers((prev) => ({ ...prev, [String(q.id)]: e.target.value }))}
                  defaultValue=""
                >
                  <option value="" disabled>Select option</option>
                  {parseOptions(q.optionsJson).map((option) => (
                    <option key={option} value={option}>{option}</option>
                  ))}
                </select>
              ) : (
                <textarea
                  className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm"
                  placeholder="Response (manual review)"
                  onChange={(e) => setAnswers((prev) => ({ ...prev, [String(q.id)]: e.target.value }))}
                />
              )}
            </div>
          ))}
        </div>
        <button onClick={submit} className="mt-4 rounded bg-teal-700 px-3 py-1.5 text-sm text-white disabled:opacity-50" disabled={submitMutation.isPending}>
          Submit attempt
        </button>
        {submitMutation.isError ? (
          <p className="mt-2 text-xs text-rose-600">Submit failed. Review required fields and try again.</p>
        ) : null}
        {attemptId ? (
          <Link href={`/learning/attempts/${attemptId}`} className="ml-3 text-sm text-teal-700 hover:underline">
            View attempt result
          </Link>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
