"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAddFundoQuestion,
  useFundoAssessment,
  useFundoPendingReviews,
  useManualReviewFundoAttempt,
  useUpdateFundoAssessment,
  useUpdateFundoQuestion,
} from "@/hooks/queries/useFundoLms";

export default function EditAssessmentPage() {
  const params = useParams<{ assessmentId: string }>();
  const assessmentId = params?.assessmentId;
  const { data, refetch } = useFundoAssessment(assessmentId);
  const [title, setTitle] = useState("");
  const [status, setStatus] = useState("PUBLISHED");
  const [prompt, setPrompt] = useState("");
  const [questionType, setQuestionType] = useState("TRUE_FALSE");
  const [optionsCsv, setOptionsCsv] = useState("true,false");
  const [correctAnswer, setCorrectAnswer] = useState("true");
  const [rubricJson, setRubricJson] = useState('[{"criterion":"Accuracy","weight":50},{"criterion":"Completeness","weight":50}]');
  const [reviewScore, setReviewScore] = useState("0");
  const [reviewPassed, setReviewPassed] = useState("false");
  const [reviewFeedback, setReviewFeedback] = useState("");
  const [message, setMessage] = useState("");
  const [saved, setSaved] = useState(false);
  const updateAssessment = useUpdateFundoAssessment();
  const addQuestionMutation = useAddFundoQuestion();
  const updateQuestionMutation = useUpdateFundoQuestion();
  const manualReviewMutation = useManualReviewFundoAttempt();
  const assessment = ((data?.data as Record<string, unknown>)?.assessment as Record<string, unknown>) ?? {};
  const questions = ((assessment.questions as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const { data: pendingData, refetch: refetchPending } = useFundoPendingReviews(assessmentId, 100);
  const pendingReviews = ((((pendingData?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean));

  async function save() {
    if (!assessmentId) return;
    setMessage("");
    await updateAssessment.mutateAsync({ assessmentId, body: { title: title.trim() || undefined, status } });
    setSaved(true);
    setMessage("Assessment metadata updated.");
    void refetch();
  }

  async function addQuestion() {
    if (!assessmentId || !prompt.trim()) {
      setMessage("Question prompt is required.");
      return;
    }
    await addQuestionMutation.mutateAsync({
      assessmentId,
      body: {
        questionType,
        prompt: prompt.trim(),
        optionsJson: JSON.stringify(optionsCsv.split(",").map((v) => v.trim()).filter(Boolean)),
        correctAnswerJson: JSON.stringify(correctAnswer),
        rubricJson,
        points: 1,
      },
    });
    setPrompt("");
    setMessage("Question added.");
    void refetch();
  }

  async function updateQuestion(questionId: string) {
    if (!assessmentId) return;
    await updateQuestionMutation.mutateAsync({
      assessmentId,
      questionId,
      body: {
        questionType,
        prompt: prompt.trim(),
        optionsJson: JSON.stringify(optionsCsv.split(",").map((v) => v.trim()).filter(Boolean)),
        correctAnswerJson: JSON.stringify(correctAnswer),
        rubricJson,
      },
    });
    setMessage("Question updated.");
    void refetch();
  }

  async function submitManualReview(attemptId: string) {
    await manualReviewMutation.mutateAsync({
      attemptId,
      body: {
        score: Number(reviewScore),
        passed: reviewPassed === "true",
        reviewerId: "one-ui-admin",
        rubricAppliedJson: rubricJson,
        feedbackText: reviewFeedback,
      },
    });
    setMessage(`Manual review saved for attempt ${attemptId}.`);
    void refetchPending();
  }

  return (
    <AppLayout>
      <PageShell title="Edit assessment" subtitle="Update native assessment metadata.">
        <div className="max-w-xl space-y-2 rounded border border-gray-200 bg-white p-4">
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Assessment title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <select className="w-full rounded border border-gray-300 px-2 py-1 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
          <button onClick={save} className="rounded bg-impilo-600 px-3 py-1.5 text-sm text-white hover:bg-impilo-700">Save metadata</button>
          {saved ? <p className="text-xs text-emerald-700">Saved.</p> : null}
          {message ? <p className="text-xs text-gray-600">{message}</p> : null}
        </div>
        <div className="mt-4 rounded border border-gray-200 bg-white p-4">
          <p className="text-sm font-medium text-gray-900">Add or edit question</p>
          <input className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Prompt" value={prompt} onChange={(e) => setPrompt(e.target.value)} />
          <select className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" value={questionType} onChange={(e) => setQuestionType(e.target.value)}>
            <option value="TRUE_FALSE">TRUE_FALSE</option>
            <option value="MULTIPLE_CHOICE">MULTIPLE_CHOICE</option>
            <option value="SHORT_ANSWER">SHORT_ANSWER</option>
          </select>
          <input className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Options CSV" value={optionsCsv} onChange={(e) => setOptionsCsv(e.target.value)} />
          <input className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Correct answer" value={correctAnswer} onChange={(e) => setCorrectAnswer(e.target.value)} />
          <textarea className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Rubric JSON (for manual marking guidance)" value={rubricJson} onChange={(e) => setRubricJson(e.target.value)} />
          <button onClick={addQuestion} className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">Add question</button>
          <ul className="mt-3 space-y-1 text-xs text-gray-600">
            {questions.map((q) => (
              <li key={String(q.id)} className="flex items-center justify-between gap-2 rounded border border-gray-100 px-2 py-1">
                <span>{String(q.prompt ?? q.id)} ({String(q.type ?? "-")})</span>
                <button onClick={() => updateQuestion(String(q.id))} className="text-teal-700 hover:underline">Update using form values</button>
              </li>
            ))}
          </ul>
        </div>
        <div className="mt-4 rounded border border-gray-200 bg-white p-4">
          <p className="text-sm font-medium text-gray-900">Manual marking and moderation</p>
          <div className="mt-2 grid gap-2 sm:grid-cols-3">
            <input className="rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Review score" value={reviewScore} onChange={(e) => setReviewScore(e.target.value)} />
            <select className="rounded border border-gray-300 px-2 py-1 text-sm" value={reviewPassed} onChange={(e) => setReviewPassed(e.target.value)}>
              <option value="false">Failed</option>
              <option value="true">Passed</option>
            </select>
            <input className="rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Reviewer feedback" value={reviewFeedback} onChange={(e) => setReviewFeedback(e.target.value)} />
          </div>
          <ul className="mt-3 space-y-1 text-xs text-gray-600">
            {pendingReviews.map((attempt) => (
              <li key={String(attempt.id)} className="flex items-center justify-between gap-2 rounded border border-gray-100 px-2 py-1">
                <span>
                  Attempt {String(attempt.attemptNo ?? "-")} • subject {String(attempt.subjectType ?? "-")}/{String(attempt.subjectId ?? "-")}
                </span>
                <button onClick={() => submitManualReview(String(attempt.id))} className="text-teal-700 hover:underline">
                  Apply manual review
                </button>
              </li>
            ))}
            {pendingReviews.length === 0 ? <li className="text-gray-500">No pending manual-review attempts.</li> : null}
          </ul>
        </div>
      </PageShell>
    </AppLayout>
  );
}
