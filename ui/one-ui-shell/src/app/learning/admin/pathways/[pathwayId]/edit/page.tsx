"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useFundoPathway } from "@/hooks/queries/useFundoLms";

export default function EditPathwayPage() {
  const params = useParams<{ pathwayId: string }>();
  const pathwayId = params?.pathwayId;
  const { data: pathwayData, refetch } = useFundoPathway(pathwayId);
  const [title, setTitle] = useState("");
  const [status, setStatus] = useState("PUBLISHED");
  const [courseId, setCourseId] = useState("");
  const [sequence, setSequence] = useState("1");
  const [required, setRequired] = useState(true);
  const [message, setMessage] = useState("");
  const [saved, setSaved] = useState(false);
  const pathway = ((pathwayData?.data as Record<string, unknown>)?.pathway as Record<string, unknown>) ?? {};
  const items = ((pathway.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);

  async function save() {
    if (!pathwayId) return;
    await apiClient.put(`/internal/v1/learning/v11/pathways/${encodeURIComponent(pathwayId)}`, { title, status });
    setSaved(true);
    setMessage("Pathway metadata updated.");
    void refetch();
  }

  async function addItem() {
    if (!pathwayId || !courseId.trim()) {
      setMessage("Course ID is required to add a pathway item.");
      return;
    }
    await apiClient.post(`/internal/v1/learning/v11/pathways/${encodeURIComponent(pathwayId)}/items`, {
      courseId: courseId.trim(),
      sequence: Number(sequence || 1),
      required,
    });
    setCourseId("");
    setMessage("Pathway item added.");
    void refetch();
  }

  return (
      <PageShell title="Edit pathway" subtitle="Update pathway metadata and publish state.">
        <div className="max-w-xl space-y-2 rounded border border-border bg-card p-4">
          <input className="w-full rounded border border-border px-2 py-1 text-sm" placeholder="Pathway title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <select className="w-full rounded border border-border px-2 py-1 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
          <button onClick={save} className="rounded bg-primary px-3 py-1.5 text-sm text-white">Save metadata</button>
          {saved ? <p className="text-xs text-primary-hover">Saved.</p> : null}
          {message ? <p className="text-xs text-muted-foreground">{message}</p> : null}
        </div>
        <div className="mt-4 rounded border border-border bg-card p-4">
          <p className="text-sm font-medium text-foreground">Add ordered course</p>
          <div className="mt-2 grid gap-2 sm:grid-cols-3">
            <input className="rounded border border-border px-2 py-1 text-sm" placeholder="Course ID" value={courseId} onChange={(e) => setCourseId(e.target.value)} />
            <input className="rounded border border-border px-2 py-1 text-sm" placeholder="Sequence" value={sequence} onChange={(e) => setSequence(e.target.value)} />
            <label className="inline-flex items-center gap-2 text-xs text-foreground">
              <input type="checkbox" checked={required} onChange={(e) => setRequired(e.target.checked)} />
              Required
            </label>
          </div>
          <button onClick={addItem} className="mt-2 rounded border border-border px-3 py-1.5 text-sm text-foreground">Add item</button>
          <ul className="mt-3 space-y-1 text-xs text-muted-foreground">
            {items.map((item) => (
              <li key={String(item.id)}>
                {String(item.sequence)}. {String(item.courseId)} {Boolean(item.required) ? "(required)" : "(optional)"}
              </li>
            ))}
          </ul>
        </div>
      </PageShell>
  );
}
