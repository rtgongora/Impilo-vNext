"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import { useFundoFacilitators } from "@/hooks/queries/useFundoDelivery";
import {
  useCreateFundoPlacement,
  useFundoAcademicRecord,
  useGraduateFundoStudent,
  useSignoffFundoPlacement,
} from "@/hooks/queries/useFundoStudents";

type Registration = { id: string; courseId: string; status?: string };
type Placement = {
  id: string;
  title?: string;
  signoffStatus?: string;
  signedOffBy?: string;
  preceptorFacilitatorId?: string | null;
  startsOn?: string | null;
  endsOn?: string | null;
};
type Graduation = { id: string; qualificationTitle?: string; registryNumber?: string; status?: string };

export default function StudentDetailPage() {
  const params = useParams();
  const studentId = String(params?.studentId ?? "");
  const { data } = useFundoAcademicRecord(studentId);
  const { data: facilitatorsData } = useFundoFacilitators();
  const signoff = useSignoffFundoPlacement(studentId);
  const createPlacement = useCreateFundoPlacement(studentId);
  const graduate = useGraduateFundoStudent(studentId);

  const [placementTitle, setPlacementTitle] = useState("");
  const [preceptorId, setPreceptorId] = useState("");
  const [placementMessage, setPlacementMessage] = useState<string | null>(null);
  const [signoffMessage, setSignoffMessage] = useState<string | null>(null);

  const record = (data?.data as Record<string, unknown> | undefined) ?? {};
  const registrations = ((record.registrations as Registration[]) ?? []);
  const placements = ((record.placements as Placement[]) ?? []);
  const graduation = ((record.graduation as Graduation[]) ?? []);
  const facilitators =
    ((facilitatorsData?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []);

  const facilitatorName = (id?: string | null) => {
    if (!id) return null;
    const f = facilitators.find((x) => String(x.id) === String(id));
    return f ? String(f.displayName ?? id) : id;
  };

  async function onCreatePlacement() {
    setPlacementMessage(null);
    try {
      await createPlacement.mutateAsync({
        title: placementTitle.trim(),
        preceptorFacilitatorId: preceptorId || undefined,
      });
      setPlacementTitle("");
      setPreceptorId("");
      setPlacementMessage("Placement created.");
    } catch {
      setPlacementMessage("Failed to create placement.");
    }
  }

  async function onSignoff(placementId: string) {
    setSignoffMessage(null);
    try {
      await signoff.mutateAsync({ placementId, body: { signoffStatus: "SIGNED_OFF" } });
      setSignoffMessage("Placement signed off.");
    } catch {
      setSignoffMessage(
        "Sign-off denied — only the placement's assigned preceptor can sign off, and the placement must have a preceptor assigned."
      );
    }
  }

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
        {signoffMessage ? (
          <p className="mt-2 rounded border border-border bg-muted/40 px-2 py-1 text-xs text-muted-foreground" data-testid="placement-signoff-message">
            {signoffMessage}
          </p>
        ) : null}
        <ul className="mt-2 space-y-2 text-sm">
          {placements.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 rounded border border-border px-3 py-2">
              <span className="min-w-0">
                {p.title ?? "Placement"} · {p.signoffStatus ?? "PENDING"}
                {p.signedOffBy ? ` · signed off by ${p.signedOffBy}` : ""}
                <span className="block text-xs text-muted-foreground">
                  {p.preceptorFacilitatorId
                    ? `Preceptor: ${facilitatorName(p.preceptorFacilitatorId)}`
                    : "No preceptor assigned — sign-off unavailable"}
                </span>
              </span>
              {p.signoffStatus !== "SIGNED_OFF" && p.preceptorFacilitatorId ? (
                <button
                  onClick={() => onSignoff(p.id)}
                  disabled={signoff.isPending}
                  className="text-emerald-700 hover:underline disabled:opacity-60"
                >
                  Sign off (preceptor only)
                </button>
              ) : null}
            </li>
          ))}
        </ul>

        <div className="mt-4 rounded border border-dashed border-border p-3" data-testid="placement-create-form">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">New placement</p>
          <div className="mt-2 grid gap-2 md:grid-cols-3">
            <input
              value={placementTitle}
              onChange={(e) => setPlacementTitle(e.target.value)}
              placeholder="Placement title (e.g. District rotation)"
              className="rounded border border-border px-3 py-2 text-sm"
            />
            <select
              value={preceptorId}
              onChange={(e) => setPreceptorId(e.target.value)}
              className="rounded border border-border px-3 py-2 text-sm"
              data-testid="placement-preceptor-select"
            >
              <option value="">Assign preceptor (required for sign-off)</option>
              {facilitators.map((f) => (
                <option key={String(f.id)} value={String(f.id)}>
                  {String(f.displayName ?? f.id)}{f.facilitatorKind ? ` · ${String(f.facilitatorKind)}` : ""}
                </option>
              ))}
            </select>
            <button
              onClick={onCreatePlacement}
              disabled={!placementTitle.trim() || createPlacement.isPending}
              className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60"
            >
              Create placement
            </button>
          </div>
          {placementMessage ? (
            <p className="mt-2 text-xs text-muted-foreground" data-testid="placement-create-message">{placementMessage}</p>
          ) : null}
        </div>
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
