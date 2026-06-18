"use client";

import { useState } from "react";
import { Loader2, MessageSquarePlus } from "lucide-react";
import { useCreateImagingAnnotation, useImagingAnnotations } from "@/hooks/queries/useImaging";

interface Props {
  governedStudyId: string;
  chartPatientCpid: string;
}

type AnnotationRow = {
  id?: number;
  annotationType?: string;
  note?: string;
  createdAt?: string;
  actorId?: string;
};

function asAnnotationRows(data: unknown): AnnotationRow[] {
  if (!Array.isArray(data)) return [];
  return data as AnnotationRow[];
}

export function ImagingAnnotationPanel({ governedStudyId, chartPatientCpid }: Props) {
  const [note, setNote] = useState("");
  const annotationsQ = useImagingAnnotations(governedStudyId, chartPatientCpid);
  const createAnnotation = useCreateImagingAnnotation(chartPatientCpid);
  const rows = asAnnotationRows(annotationsQ.data?.data);

  async function handleSave() {
    const trimmed = note.trim();
    if (!trimmed) return;
    await createAnnotation.mutateAsync({
      studyId: governedStudyId,
      body: {
        annotationType: "REVIEW_NOTE",
        note: trimmed,
      },
    });
    setNote("");
  }

  return (
    <section className="mt-6 rounded-xl border border-border bg-card p-4">
      <div className="mb-3 flex items-center gap-2">
        <MessageSquarePlus className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-medium text-foreground">Imaging review notes (PACS persisted)</h3>
      </div>
      <p className="mb-3 text-xs text-muted-foreground">
        Notes are stored in the PACS adapter and read back through the governed imaging API.
      </p>
      <label className="block text-xs font-medium text-muted-foreground mb-1">Review note</label>
      <textarea
        value={note}
        onChange={(e) => setNote(e.target.value)}
        rows={3}
        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
        placeholder="Clinical review note for this governed study…"
      />
      <button
        type="button"
        onClick={() => void handleSave()}
        disabled={createAnnotation.isPending || !note.trim()}
        className="mt-2 inline-flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
      >
        {createAnnotation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Save review note
      </button>
      {createAnnotation.isError ? (
        <p className="mt-2 text-xs text-danger">Failed to save annotation. Check governed study context.</p>
      ) : null}
      <div className="mt-4 space-y-2">
        {annotationsQ.isLoading ? (
          <p className="text-xs text-muted-foreground">Loading saved notes…</p>
        ) : rows.length === 0 ? (
          <p className="text-xs text-muted-foreground">No saved review notes yet.</p>
        ) : (
          rows.map((row) => (
            <div key={row.id ?? row.note} className="rounded-lg border border-border bg-background p-3 text-sm">
              <p className="text-foreground">{row.note}</p>
              <p className="mt-1 text-[10px] text-muted-foreground">
                {row.annotationType ?? "REVIEW_NOTE"}
                {row.createdAt ? ` · ${row.createdAt}` : ""}
              </p>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
