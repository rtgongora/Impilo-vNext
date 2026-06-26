"use client";

import { useParams } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import {
  useFundoAcademicRecord,
  useGraduateFundoStudent,
  useSignoffFundoPlacement,
} from "@/hooks/queries/useFundoStudents";

type Registration = { id: string; courseId: string; status?: string };
type Placement = { id: string; title?: string; signoffStatus?: string; signedOffBy?: string };
type Graduation = { id: string; qualificationTitle?: string; registryNumber?: string; status?: string };

export default function StudentDetailPage() {
  const params = useParams();
  const studentId = String(params?.studentId ?? "");
  const { data } = useFundoAcademicRecord(studentId);
  const signoff = useSignoffFundoPlacement(studentId);
  const graduate = useGraduateFundoStudent(studentId);

  const record = (data?.data as Record<string, unknown> | undefined) ?? {};
  const registrations = ((record.registrations as Registration[]) ?? []);
  const placements = ((record.placements as Placement[]) ?? []);
  const graduation = ((record.graduation as Graduation[]) ?? []);

  return (
    <PageShell title={`Student ${record.studentNumber ?? studentId}`} subtitle={`Status: ${record.status ?? "—"} · Academic record`}>
      <section className="rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Registrations ({registrations.length})</p>
        <ul className="mt-2 space-y-1 text-sm">
          {registrations.map((r) => <li key={r.id} className="rounded border border-border px-3 py-1.5">{r.courseId} · {r.status ?? "REGISTERED"}</li>)}
        </ul>
      </section>

      <section className="mt-4 rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Placements ({placements.length})</p>
        <ul className="mt-2 space-y-2 text-sm">
          {placements.map((p) => (
            <li key={p.id} className="flex items-center justify-between rounded border border-border px-3 py-2">
              <span>{p.title ?? "Placement"} · {p.signoffStatus ?? "PENDING"}{p.signedOffBy ? ` · ${p.signedOffBy}` : ""}</span>
              {p.signoffStatus !== "SIGNED_OFF" && (
                <button onClick={() => signoff.mutate({ placementId: p.id, body: { signoffStatus: "SIGNED_OFF" } })} className="text-emerald-700 hover:underline">Sign off</button>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-4 rounded-lg border border-border bg-card p-4">
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold text-foreground">Graduation ({graduation.length})</p>
          {graduation.length === 0 && (
            <button onClick={() => graduate.mutate({})} disabled={graduate.isPending} className="rounded bg-teal-600 px-3 py-1 text-sm text-white disabled:opacity-60">Confer qualification</button>
          )}
        </div>
        <ul className="mt-2 space-y-1 text-sm">
          {graduation.map((g) => <li key={g.id} className="rounded border border-border px-3 py-1.5">{g.qualificationTitle} · {g.registryNumber} · {g.status ?? "CONFERRED"}</li>)}
        </ul>
      </section>
    </PageShell>
  );
}
