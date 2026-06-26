"use client";

import { PageShell } from "@/components/PageShell";
import {
  useAdmitFundoStudent,
  useDecideFundoApplication,
  useFundoApplications,
  useFundoStudents,
} from "@/hooks/queries/useFundoStudents";

type Application = { id: string; applicantName: string; applicantSubjectId?: string; status?: string; programId?: string };
type Student = { id: string; studentNumber: string; subjectId?: string; status?: string };

export default function AdmissionsPage() {
  const { data: appData } = useFundoApplications();
  const { data: studentData } = useFundoStudents();
  const decide = useDecideFundoApplication();
  const admit = useAdmitFundoStudent();

  const apps = (((appData?.data as { items?: Application[] } | undefined)?.items) ?? []);
  const studs = (((studentData?.data as { items?: Student[] } | undefined)?.items) ?? []);

  return (
    <PageShell title="Admissions" subtitle="Review applications, decide, and admit students.">
      <section className="rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Applications ({apps.length})</p>
        <ul className="mt-2 space-y-2 text-sm" data-testid="application-list">
          {apps.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded border border-border px-3 py-2">
              <span>{a.applicantName}{a.applicantSubjectId ? ` · ${a.applicantSubjectId}` : ""} · {a.status ?? "SUBMITTED"}</span>
              <span className="flex gap-2">
                <button onClick={() => decide.mutate({ applicationId: a.id, body: { status: "UNDER_REVIEW" } })} className="text-teal-700 hover:underline">Review</button>
                <button onClick={() => decide.mutate({ applicationId: a.id, body: { status: "REJECTED" } })} className="text-rose-700 hover:underline">Reject</button>
                <button onClick={() => admit.mutate({ applicationId: a.id })} disabled={admit.isPending} className="rounded bg-emerald-600 px-2 py-0.5 text-xs text-white disabled:opacity-60">Admit</button>
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-4 rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Students ({studs.length})</p>
        <ul className="mt-2 space-y-1 text-sm" data-testid="student-list">
          {studs.map((s) => (
            <li key={s.id} className="rounded border border-border px-3 py-1.5">{s.studentNumber}{s.subjectId ? ` · ${s.subjectId}` : ""} · {s.status ?? "ADMITTED"}</li>
          ))}
        </ul>
      </section>
    </PageShell>
  );
}
