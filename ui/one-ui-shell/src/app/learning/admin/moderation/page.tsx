"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoCourseAssessments } from "@/hooks/queries/useFundoLms";
import { useState } from "react";

const SEED_ASSESSMENT_ID = "dddddddd-0002-0000-0000-000000000001";
const SEED_COURSE_ID = "aaaaaaaa-0000-0000-0000-000000000002";

export default function LearningModerationPage() {
  const [courseId, setCourseId] = useState(SEED_COURSE_ID);
  const { data } = useFundoCourseAssessments(courseId || undefined);
  const items = (
    ((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ??
    (Array.isArray(data?.data) ? (data?.data as Array<Record<string, unknown>>) : [])
  );

  return (
    <AppLayout>
      <PageShell title="Assessment moderation" subtitle="Review pending free-text attempts and score objective items in authoring.">
        <div className="mb-4 rounded border border-warning/35 bg-warning-soft p-3 text-sm text-warning-foreground">
          Objective MCQ/TF attempts auto-score. Free-text responses appear in each assessment&apos;s edit screen pending-review queue.
        </div>
        <label className="mb-3 flex items-center gap-2 text-sm text-foreground">
          Course ID
          <input
            value={courseId}
            onChange={(e) => setCourseId(e.target.value)}
            className="rounded border border-border px-2 py-1 font-mono text-xs"
          />
        </label>
        <ul className="space-y-2" data-testid="fundo-moderation-queue">
          {items.map((assessment) => (
            <li key={String(assessment.id)} className="rounded border border-border bg-card p-3 text-sm">
              <p className="font-medium text-foreground">{String(assessment.title ?? assessment.id)}</p>
              <p className="text-xs text-muted-foreground">{String(assessment.assessmentType ?? "ASSESSMENT")}</p>
              <Link
                href={`/learning/admin/assessments/${String(assessment.id)}/edit`}
                className="mt-2 inline-block text-teal-700 hover:underline"
              >
                Open moderation queue
              </Link>
            </li>
          ))}
          {items.length === 0 ? (
            <li className="text-sm text-muted-foreground">
              No assessments for this course. Seed default:{" "}
              <button type="button" className="text-teal-700 underline" onClick={() => setCourseId(SEED_COURSE_ID)}>
                {SEED_COURSE_ID}
              </button>
            </li>
          ) : null}
        </ul>
        <p className="mt-4 text-xs text-muted-foreground">
          Quick link:{" "}
          <Link href={`/learning/admin/assessments/${SEED_ASSESSMENT_ID}/edit`} className="text-teal-700 hover:underline">
            EHR navigation quiz moderation
          </Link>
        </p>
      </PageShell>
    </AppLayout>
  );
}
