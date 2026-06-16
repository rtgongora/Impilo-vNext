"use client";

import { useState } from "react";
import { PageShell } from "@/components/PageShell";
import { useCreateInteractiveActivity, useSubmitInteractiveResponse } from "@/hooks/queries/useFundoStudio";

export default function LearningCourseFeedbackPage({ params }: { params: { courseId: string } }) {
  const create = useCreateInteractiveActivity();
  const submit = useSubmitInteractiveResponse(params.courseId);
  const [feedback, setFeedback] = useState("");

  async function onCreateFeedbackForm() {
    await create.mutateAsync({
      courseId: params.courseId,
      activityType: "FEEDBACK",
      title: "Course feedback form",
      config: { fields: ["rating", "comment", "improvements"] },
    });
  }

  async function onSubmitFeedback() {
    await submit.mutateAsync({
      subjectType: "USER_HEALTH_ID",
      subjectId: "self",
      response: { feedback, rating: 4 },
    });
    setFeedback("");
  }

  return (
      <PageShell title="Course Feedback" subtitle="Feedback, reflections and trainer/course evaluations.">
        <div className="rounded border border-border bg-card p-4">
          <textarea value={feedback} onChange={(e) => setFeedback(e.target.value)} className="h-28 w-full rounded border border-border px-3 py-2 text-sm" />
          <div className="mt-2 flex gap-2">
            <button onClick={onSubmitFeedback} className="rounded bg-teal-600 px-3 py-2 text-sm text-white">Submit feedback</button>
            <button onClick={onCreateFeedbackForm} className="rounded border border-border px-3 py-2 text-sm text-foreground">Create feedback template</button>
          </div>
        </div>
      </PageShell>
  );
}
