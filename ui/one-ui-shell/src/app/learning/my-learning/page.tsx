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
    ["Recommended", listOf(payload.recommended)],
    ["Assigned pathways", listOf(payload.assignedPathways)],
    ["Certificates", listOf(payload.certificates)],
    ["CPD eligible completions", listOf(payload.cpdEligibleCompletions)],
  ] as const;

  return (
    <AppLayout>
      <PageShell title="My learning" subtitle="Learner journey dashboard for enrolments, progress, certificates and CPD evidence.">
        <div className="mb-3">
          <Link href="/learning/record" className="text-sm text-teal-700 hover:underline">
            Open full learning record / transcript
          </Link>
        </div>
        {isLoading ? <p className="text-sm text-gray-600">Loading…</p> : null}
        <div className="grid gap-4 md:grid-cols-2">
          {sections.map(([title, items]) => (
            <div key={title} className="rounded-lg border border-gray-200 bg-white p-4">
              <p className="text-sm font-semibold text-gray-900">{title}</p>
              {items.length === 0 ? (
                <p className="mt-2 text-sm text-gray-500">None</p>
              ) : (
                <ul className="mt-2 space-y-1 text-sm">
                  {items.slice(0, 8).map((item) => (
                    <li key={String(item.id)} className="flex items-center justify-between gap-2">
                      <span>{String(item.courseTitle ?? item.courseId ?? "Course")}</span>
                      {item.id && !title.includes("Recommended") && !title.includes("pathway") && !title.includes("Certificates") ? (
                        <Link href={`/learning/enrolments/${item.id}`} className="text-impilo-700 hover:underline">
                          Open
                        </Link>
                      ) : null}
                      {item.id && title.includes("Recommended") ? (
                        <Link href={`/learning/courses/${item.id}`} className="text-impilo-700 hover:underline">
                          Open
                        </Link>
                      ) : null}
                      {item.id && title.includes("pathway") ? (
                        <Link href={`/learning/pathways/${item.id}`} className="text-impilo-700 hover:underline">
                          Open
                        </Link>
                      ) : null}
                      {item.id && title.includes("Certificates") ? (
                        <Link href={`/learning/certificates/${item.id}`} className="text-impilo-700 hover:underline">
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
