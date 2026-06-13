"use client";

import Link from "next/link";
import { useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoCatalog } from "@/hooks/queries/useFundoCatalog";
import { useCreateFundoAssessment } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioAssessmentsPage() {
  const { data } = useFundoCatalog({ limit: 50 });
  const createAssessment = useCreateFundoAssessment();
  const [courseId, setCourseId] = useState("");
  const [assessmentType, setAssessmentType] = useState("PRE_TEST");
  const [title, setTitle] = useState("Initial knowledge check");
  const items = data?.data?.items ?? [];

  async function onCreateAssessment() {
    if (!courseId) return;
    await createAssessment.mutateAsync({
      courseId,
      title,
      assessmentType,
      status: "DRAFT",
      passMark: assessmentType === "SURVEY" || assessmentType === "FEEDBACK" ? 0 : 70,
      maxAttempts: 3,
    });
  }

  return (
    <FundoStudioWorkspace title="Studio Assessments" subtitle="Pre-tests, post-tests, quizzes, final assessments and practical/case reviews.">
      <div className="mb-3 rounded border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Create assessment draft</p>
        <div className="mt-2 grid gap-2 md:grid-cols-3">
          <select value={courseId} onChange={(e) => setCourseId(e.target.value)} className="rounded border border-border px-3 py-2 text-sm">
            <option value="">Select course</option>
            {items.map((c) => (
              <option key={c.id} value={c.id}>{c.title}</option>
            ))}
          </select>
          <select value={assessmentType} onChange={(e) => setAssessmentType(e.target.value)} className="rounded border border-border px-3 py-2 text-sm">
            {["PRE_TEST", "POST_TEST", "QUIZ", "FINAL_ASSESSMENT", "SURVEY", "FEEDBACK", "PRACTICAL_CHECKLIST", "CASE_REVIEW"].map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className="rounded border border-border px-3 py-2 text-sm" />
        </div>
        <button onClick={onCreateAssessment} className="mt-2 rounded bg-teal-600 px-3 py-2 text-sm text-white">Create draft assessment</button>
      </div>
      <ul className="space-y-2">
        {items.map((c) => (
          <li key={c.id} className="rounded border border-border bg-card p-3 text-sm">
            <p className="font-medium text-foreground">{c.title}</p>
            <div className="mt-2 flex gap-2 text-xs">
              <Link href={`/learning/admin/assessments/new?courseId=${encodeURIComponent(c.id)}`} className="rounded border border-border px-2 py-1 text-foreground">Create assessment</Link>
              <Link href={`/learning/studio/courses/${c.id}/builder`} className="rounded border border-border px-2 py-1 text-foreground">Attach in builder</Link>
            </div>
          </li>
        ))}
      </ul>
    </FundoStudioWorkspace>
  );
}
