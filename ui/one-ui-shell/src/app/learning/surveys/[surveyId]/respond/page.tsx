"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateInteractiveActivity, useSubmitInteractiveResponse } from "@/hooks/queries/useFundoStudio";

export default function LearningSurveyRespondPage({ params }: { params: { surveyId: string } }) {
  const [text, setText] = useState("");
  const createActivity = useCreateInteractiveActivity();
  const submitResponse = useSubmitInteractiveResponse(params.surveyId);

  async function onSubmit() {
    await submitResponse.mutateAsync({
      subjectType: "USER_HEALTH_ID",
      subjectId: "self",
      response: { text, submittedFrom: "survey-respond-page" },
    });
  }

  async function onCreateAsyncSurveyPlaceholder() {
    await createActivity.mutateAsync({
      activityType: "SURVEY",
      title: "Fallback survey placeholder",
      config: { linkedSurveyId: params.surveyId },
    });
  }

  return (
    <AppLayout>
      <PageShell title="Respond to Survey" subtitle="Non-scored survey/feedback response flow.">
        <div className="rounded border border-gray-200 bg-white p-4">
          <textarea value={text} onChange={(e) => setText(e.target.value)} className="h-32 w-full rounded border border-gray-300 px-3 py-2 text-sm" />
          <div className="mt-2 flex gap-2">
            <button onClick={onSubmit} className="rounded bg-teal-600 px-3 py-2 text-sm text-white">Submit response</button>
            <button onClick={onCreateAsyncSurveyPlaceholder} className="rounded border border-gray-300 px-3 py-2 text-sm text-gray-700">Create async survey placeholder</button>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
