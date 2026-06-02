"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LearningOverlayForm } from "@/components/learning/LearningOverlayForm";
import { useAddFundoQuestion, useCreateFundoAssessment } from "@/hooks/queries/useFundoLms";

export default function NewAssessmentPage() {
  const [courseId, setCourseId] = useState("");
  const [title, setTitle] = useState("");
  const [assessmentId, setAssessmentId] = useState("");
  const [prompt, setPrompt] = useState("");
  const [questionType, setQuestionType] = useState("TRUE_FALSE");
  const [optionsCsv, setOptionsCsv] = useState("true,false");
  const [correctAnswer, setCorrectAnswer] = useState("true");
  const [rubricJson, setRubricJson] = useState('[{"criterion":"Accuracy","weight":50},{"criterion":"Completeness","weight":50}]');
  const [error, setError] = useState("");
  const createAssessmentMutation = useCreateFundoAssessment();
  const addQuestionMutation = useAddFundoQuestion();

  async function createAssessment() {
    setError("");
    if (!courseId.trim() || !title.trim()) {
      setError("Course ID and assessment title are required.");
      return;
    }
    const res = (await createAssessmentMutation.mutateAsync({
      courseId: courseId.trim(),
      title: title.trim(),
      assessmentType: "QUIZ",
      passMark: 50,
      maxAttempts: 3,
      status: "DRAFT",
    })) as Record<string, unknown>;
    const data = (res.data as Record<string, unknown> | undefined) ?? {};
    const assessment = (data.assessment as Record<string, unknown> | undefined) ?? {};
    setAssessmentId((assessment.id as string | undefined) ?? "");
  }

  async function addQuestion() {
    if (!assessmentId) return;
    await addQuestionMutation.mutateAsync({
      assessmentId,
      body: {
        questionType,
        prompt,
        optionsJson: JSON.stringify(optionsCsv.split(",").map((v) => v.trim()).filter(Boolean)),
        correctAnswerJson: JSON.stringify(correctAnswer),
        rubricJson,
        points: 1,
      },
    });
  }

  return (
    <AppLayout>
      <PageShell title="New assessment" subtitle="Create assessment and add basic questions.">
        <LearningOverlayForm
          open
          title="New Fundo assessment"
          subtitle="Create the assessment shell, then add objective or manual-review questions."
          footer={
            <div className="flex flex-wrap gap-3">
              <Link href="/learning/admin/assessments" className="text-sm font-medium text-gray-700 hover:underline">
                Back to Admin assessments
              </Link>
              {assessmentId ? (
                <Link href={`/learning/admin/assessments/${assessmentId}/edit`} className="text-sm font-medium text-impilo-700 hover:underline">
                  Continue to edit assessment
                </Link>
              ) : null}
            </div>
          }
        >
        <div className="space-y-3">
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Course ID" value={courseId} onChange={(e) => setCourseId(e.target.value)} />
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Assessment title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <button onClick={createAssessment} className="rounded bg-impilo-600 px-3 py-1.5 text-sm text-white hover:bg-impilo-700 disabled:opacity-50" disabled={createAssessmentMutation.isPending}>Create assessment</button>
          {error ? <p className="text-xs text-rose-700">{error}</p> : null}
          {assessmentId ? (
            <>
              <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Question prompt" value={prompt} onChange={(e) => setPrompt(e.target.value)} />
              <select className="w-full rounded border border-gray-300 px-2 py-1 text-sm" value={questionType} onChange={(e) => setQuestionType(e.target.value)}>
                <option value="TRUE_FALSE">TRUE_FALSE</option>
                <option value="MULTIPLE_CHOICE">MULTIPLE_CHOICE</option>
                <option value="SHORT_ANSWER">SHORT_ANSWER</option>
              </select>
              <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Options CSV (for objective questions)" value={optionsCsv} onChange={(e) => setOptionsCsv(e.target.value)} />
              <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Correct answer (for objective)" value={correctAnswer} onChange={(e) => setCorrectAnswer(e.target.value)} />
              <textarea className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Rubric JSON (for short-answer/manual marking)" value={rubricJson} onChange={(e) => setRubricJson(e.target.value)} />
              <button onClick={addQuestion} className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">Add question</button>
            </>
          ) : null}
        </div>
        </LearningOverlayForm>
      </PageShell>
    </AppLayout>
  );
}
