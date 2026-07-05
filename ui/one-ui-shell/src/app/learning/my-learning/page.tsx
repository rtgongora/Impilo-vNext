"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoMyLearning } from "@/hooks/queries/useFundoLms";

function listOf(value: unknown): Array<Record<string, unknown>> {
  return Array.isArray(value) ? (value as Array<Record<string, unknown>>) : [];
}

function EnrolmentList({ items, emptyLabel }: { items: Array<Record<string, unknown>>; emptyLabel: string }) {
  if (items.length === 0) {
    return <p className="mt-2 text-sm text-muted-foreground">{emptyLabel}</p>;
  }
  return (
    <ul className="mt-2 space-y-1 text-sm">
      {items.slice(0, 8).map((item) => (
        <li key={String(item.id)} className="flex items-center justify-between gap-2">
          <span className="flex items-center gap-2">
            {String(item.courseTitle ?? item.courseId ?? "Course")}
            {item.mandatory === true ? (
              <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-800">
                Mandatory
              </span>
            ) : null}
            {typeof item.enrolmentType === "string" && item.enrolmentType !== "SELF" ? (
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-700">
                {String(item.enrolmentType)}
              </span>
            ) : null}
          </span>
          {item.id ? (
            <Link href={`/learning/enrolments/${item.id}`} className="text-teal-700 hover:underline">
              Open
            </Link>
          ) : null}
        </li>
      ))}
    </ul>
  );
}

export default function MyLearningPage() {
  const subject = useLearningSubject();
  const { data, isLoading } = useFundoMyLearning(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const enrolmentSections = [
    ["Required", listOf(payload.required), "No mandatory or assigned learning outstanding"],
    ["Enrolled", listOf(payload.enrolled), "None"],
    ["In progress", listOf(payload.inProgress), "None"],
    ["Completed", listOf(payload.completed), "None"],
    ["Overdue", listOf(payload.overdue), "Nothing overdue"],
    ["Cancelled", listOf(payload.cancelled), "None"],
  ] as const;
  const recommended = listOf(payload.recommended);
  const assignedPathways = listOf(payload.assignedPathways);
  const cpdEligible = listOf(payload.cpdEligibleCompletions);

  return (
      <PageShell title="My learning" subtitle="Learner journey dashboard for enrolments, progress, certificates and CPD evidence.">
        <div className="mb-3 flex flex-wrap gap-4">
          <Link href="/learning/record" className="text-sm text-teal-700 hover:underline">
            Open full learning record / transcript
          </Link>
          <Link href="/learning/cpd" className="text-sm text-teal-700 hover:underline">
            CPD evidence
          </Link>
        </div>
        {isLoading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
        <div className="grid gap-4 md:grid-cols-2">
          {enrolmentSections.map(([title, items, emptyLabel]) => (
            <div key={title} className="rounded-lg border border-border bg-card p-4">
              <p className="text-sm font-semibold text-foreground">{title}</p>
              <EnrolmentList items={items} emptyLabel={emptyLabel} />
            </div>
          ))}
          <div className="rounded-lg border border-border bg-card p-4">
            <p className="text-sm font-semibold text-foreground">Assigned pathways</p>
            {assignedPathways.length === 0 ? (
              <p className="mt-2 text-sm text-muted-foreground">No pathways assigned</p>
            ) : (
              <ul className="mt-2 space-y-1 text-sm">
                {assignedPathways.slice(0, 8).map((p) => (
                  <li key={String(p.id)} className="flex items-center justify-between gap-2">
                    <span>{String(p.title ?? p.code ?? "Pathway")}</span>
                    {p.id ? (
                      <Link href={`/learning/pathways/${p.id}`} className="text-teal-700 hover:underline">
                        Open
                      </Link>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="rounded-lg border border-border bg-card p-4">
            <p className="text-sm font-semibold text-foreground">CPD-eligible completions</p>
            {cpdEligible.length === 0 ? (
              <p className="mt-2 text-sm text-muted-foreground">
                No CPD-eligible completions yet — CPD points are governed by the council via Varapi.
              </p>
            ) : (
              <ul className="mt-2 space-y-1 text-sm">
                {cpdEligible.slice(0, 8).map((item, idx) => (
                  <li key={String(item.enrolmentId ?? item.courseId ?? idx)}>
                    {String(item.courseTitle ?? item.courseCode ?? "Completion")}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
        <div className="mt-4 rounded-lg border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Recommended for you</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Published courses you are not enrolled in. Not yet ranked by role or cadre eligibility.
          </p>
          {recommended.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">No recommendations right now</p>
          ) : (
            <ul className="mt-2 grid gap-1 text-sm md:grid-cols-2">
              {recommended.slice(0, 12).map((c) => (
                <li key={String(c.id)} className="flex items-center justify-between gap-2">
                  <span>{String(c.title ?? c.code ?? "Course")}</span>
                  {c.id ? (
                    <Link href={`/learning/courses/${c.id}`} className="text-teal-700 hover:underline">
                      View
                    </Link>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </div>
      </PageShell>
  );
}
