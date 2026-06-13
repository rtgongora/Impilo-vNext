"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoInteractiveActivities, useSubmitInteractiveResponse } from "@/hooks/queries/useFundoStudio";

export default function LearningSurveyRespondPage({ params }: { params: { surveyId: string } }) {
  const [text, setText] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const subject = useLearningSubject();
  const activitiesQ = useFundoInteractiveActivities(undefined, undefined, 100);
  const submitResponse = useSubmitInteractiveResponse(params.surveyId);

  const activity = useMemo(() => {
    const items = (activitiesQ.data?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? [];
    return items.find((row) => String(row.id) === params.surveyId) ?? null;
  }, [activitiesQ.data, params.surveyId]);

  const prompt =
    String(activity?.title ?? activity?.prompt ?? "") ||
    "Share your feedback on this learning experience.";

  async function onSubmit() {
    await submitResponse.mutateAsync({
      subjectType: subject.subjectType,
      subjectId: subject.subjectId,
      response: { text, submittedFrom: "survey-respond-page" },
    });
    setSubmitted(true);
  }

  return (
    <AppLayout>
      <PageShell title="Respond to Survey" subtitle="Non-scored survey and feedback capture via Fundo interactive activities.">
        <div className="mb-3 rounded-lg border border-info/25 bg-info-soft p-3 text-sm text-primary-hover">
          Responses are stored as learning feedback evidence. They do not auto-award CPD credits.
        </div>
        <div className="rounded border border-border bg-card p-4" data-testid="fundo-survey-respond">
          {activitiesQ.isLoading ? <p className="text-sm text-muted-foreground">Loading survey…</p> : null}
          {!activitiesQ.isLoading && !activity ? (
            <p className="text-sm text-warning-foreground">Survey activity {params.surveyId} was not found. You can still submit a free-text response.</p>
          ) : null}
          <p className="text-sm font-medium text-foreground">{prompt}</p>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="mt-3 h-32 w-full rounded border border-border px-3 py-2 text-sm"
            placeholder="Your feedback"
            data-testid="fundo-survey-response"
          />
          <div className="mt-2 flex flex-wrap gap-2">
            <button
              onClick={onSubmit}
              disabled={!text.trim() || submitResponse.isPending}
              className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-50"
              data-testid="fundo-survey-submit"
            >
              Submit response
            </button>
            <Link href="/learning" className="rounded border border-border px-3 py-2 text-sm text-foreground">
              Back to Fundo
            </Link>
          </div>
          {submitted ? (
            <p className="mt-3 text-sm text-primary-hover" data-testid="fundo-survey-success">
              Thank you — your response was recorded.
            </p>
          ) : null}
          {submitResponse.isError ? (
            <p className="mt-2 text-xs text-rose-600">Submit failed. Retry when learning-service is available.</p>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
