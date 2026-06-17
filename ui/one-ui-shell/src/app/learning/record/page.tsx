"use client";

import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoLearningRecord } from "@/hooks/queries/useFundoLms";

export default function LearningRecordPage() {
  const subject = useLearningSubject();
  const { data } = useFundoLearningRecord(subject);
  const record = ((data?.data as Record<string, unknown>)?.record as Record<string, unknown>) ?? {};
  const courseCompletions = ((record.courseCompletions as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const assessmentAttempts = ((record.assessmentAttempts as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const certificates = ((record.certificates as Array<Record<string, unknown>>) ?? []).filter(Boolean);

  return (
      <PageShell title="Learning record / transcript" subtitle="Native Fundo learner transcript including completions, assessments and certificates.">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded border border-border bg-card p-3">
            <p className="text-xs text-muted-foreground">Completed courses</p>
            <p className="text-xl font-semibold text-foreground">{courseCompletions.length}</p>
          </div>
          <div className="rounded border border-border bg-card p-3">
            <p className="text-xs text-muted-foreground">Assessment attempts</p>
            <p className="text-xl font-semibold text-foreground">{assessmentAttempts.length}</p>
          </div>
          <div className="rounded border border-border bg-card p-3">
            <p className="text-xs text-muted-foreground">Certificates</p>
            <p className="text-xl font-semibold text-foreground">{certificates.length}</p>
          </div>
        </div>
        <div className="mt-4 rounded border border-border bg-card p-4">
          <p className="text-sm font-medium text-foreground">Recent course completions</p>
          <ul className="mt-2 space-y-1 text-sm">
            {courseCompletions.slice(0, 20).map((c) => (
              <li key={String(c.id ?? c.courseId)}>{String(c.courseTitle ?? c.courseId ?? "-")} - {String(c.completedAt ?? "-")}</li>
            ))}
            {courseCompletions.length === 0 ? <li className="text-muted-foreground">No completions yet.</li> : null}
          </ul>
        </div>
      </PageShell>
  );
}
