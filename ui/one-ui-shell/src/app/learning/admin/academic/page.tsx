"use client";

import { useState } from "react";
import { PageShell } from "@/components/PageShell";
import {
  useCreateFundoProgram,
  useCreateFundoTerm,
  useFundoPrograms,
  useFundoTerms,
} from "@/hooks/queries/useFundoAcademic";

type Program = { id: string; code: string; title: string; qualificationLevel?: string; durationTerms?: number };
type Term = { id: string; name: string; academicYear?: string; status?: string; startsOn?: string; endsOn?: string };

export default function AcademicAdminPage() {
  const { data: programData } = useFundoPrograms();
  const createProgram = useCreateFundoProgram();
  const createTerm = useCreateFundoTerm();
  const [selectedProgram, setSelectedProgram] = useState<string | undefined>(undefined);
  const { data: termData } = useFundoTerms(selectedProgram);

  const [code, setCode] = useState("");
  const [title, setTitle] = useState("");
  const [termName, setTermName] = useState("");

  const programs = (((programData?.data as { items?: Program[] } | undefined)?.items) ?? []);
  const terms = (((termData?.data as { items?: Term[] } | undefined)?.items) ?? []);

  return (
    <PageShell title="Academic administration" subtitle="Programs (qualifications) and academic terms for pre-service education.">
      <section className="rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Create program</p>
        <div className="mt-2 grid gap-2 md:grid-cols-3">
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Code (e.g. DIP-NURSING)" className="rounded border border-border px-3 py-2 text-sm" />
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title" className="rounded border border-border px-3 py-2 text-sm" />
          <button
            onClick={async () => { await createProgram.mutateAsync({ code, title, qualificationLevel: "DIPLOMA" }); setCode(""); setTitle(""); }}
            disabled={!code.trim() || !title.trim() || createProgram.isPending}
            className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60"
          >Add program</button>
        </div>
        <ul className="mt-3 space-y-2 text-sm" data-testid="program-list">
          {programs.map((p) => (
            <li key={p.id} className="flex items-center justify-between rounded border border-border px-3 py-2">
              <span>{p.code} · {p.title}{p.durationTerms ? ` · ${p.durationTerms} terms` : ""}</span>
              <button onClick={() => setSelectedProgram(p.id)} className="text-teal-700 hover:underline">Terms</button>
            </li>
          ))}
        </ul>
      </section>

      {selectedProgram && (
        <section className="mt-4 rounded-lg border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Terms for selected program</p>
          <div className="mt-2 flex gap-2">
            <input value={termName} onChange={(e) => setTermName(e.target.value)} placeholder="Term name (e.g. Term 1 2026)" className="flex-1 rounded border border-border px-3 py-2 text-sm" />
            <button
              onClick={async () => { await createTerm.mutateAsync({ programId: selectedProgram, name: termName }); setTermName(""); }}
              disabled={!termName.trim() || createTerm.isPending}
              className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60"
            >Add term</button>
          </div>
          <ul className="mt-3 space-y-1 text-sm">
            {terms.map((t) => (
              <li key={t.id} className="rounded border border-border px-3 py-1.5">{t.name}{t.academicYear ? ` · ${t.academicYear}` : ""} · {t.status ?? "PLANNED"}</li>
            ))}
          </ul>
        </section>
      )}
    </PageShell>
  );
}
