"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoMyLearning } from "@/hooks/queries/useFundoLms";

function listOf(value: unknown): Array<Record<string, unknown>> {
  return Array.isArray(value) ? (value as Array<Record<string, unknown>>) : [];
}

export default function MyLearningPage() {
  const subject = useLearningSubject();
  const { data, isLoading } = useFundoMyLearning(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const sections = [
    ["Enrolled", listOf(payload.enrolled)],
    ["In progress", listOf(payload.inProgress)],
    ["Completed", listOf(payload.completed)],
    ["Overdue", listOf(payload.overdue)],
  ] as const;

  return (
    <AppLayout>
      <PageShell title="My learning" subtitle="Learner journey dashboard for enrolments, progress, certificates and CPD evidence.">
        <div className="mb-3">
          <Link href="/learning/record" className="text-sm text-teal-700 hover:underline">
            Open full learning record / transcript
          </Link>
        </div>
        {isLoading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
        <div className="grid gap-4 md:grid-cols-2">
          {sections.map(([title, items]) => (
            <div key={title} className="rounded-lg border border-border bg-card p-4">
              <p className="text-sm font-semibold text-foreground">{title}</p>
              {items.length === 0 ? (
                <p className="mt-2 text-sm text-muted-foreground">None</p>
              ) : (
                <ul className="mt-2 space-y-1 text-sm">
                  {items.slice(0, 8).map((item) => (
                    <li key={String(item.id)} className="flex items-center justify-between gap-2">
                      <span>{String(item.courseTitle ?? item.courseId ?? "Course")}</span>
                      {item.id ? (
                        <Link href={`/learning/enrolments/${item.id}`} className="text-teal-700 hover:underline">
                          Open
                        </Link>
                      ) : null}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
