"use client";

import { useState } from "react";
import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

export default function NewPathwayPage() {
  const [code, setCode] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState("DRAFT");
  const [pathwayId, setPathwayId] = useState("");
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  async function save() {
    setError("");
    if (!code.trim() || !title.trim()) {
      setError("Pathway code and title are required.");
      return;
    }
    const res = (await apiClient.post("/internal/v1/learning/v11/pathways", {
      code: code.trim(),
      title: title.trim(),
      description: description.trim(),
      status,
    })) as Record<string, unknown>;
    const data = (res.data as Record<string, unknown> | undefined) ?? {};
    const pathway = (data.pathway as Record<string, unknown> | undefined) ?? {};
    setPathwayId(String(pathway.id ?? ""));
    setSaved(true);
  }

  return (
      <PageShell title="New pathway" subtitle="Basic native pathway authoring.">
        <div className="max-w-xl space-y-2 rounded border border-border bg-card p-4">
          <input className="w-full rounded border border-border px-2 py-1 text-sm" placeholder="Pathway code" value={code} onChange={(e) => setCode(e.target.value)} />
          <input className="w-full rounded border border-border px-2 py-1 text-sm" placeholder="Pathway title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <textarea className="w-full rounded border border-border px-2 py-1 text-sm" placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
          <select className="w-full rounded border border-border px-2 py-1 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
          <button onClick={save} className="rounded bg-primary px-3 py-1.5 text-sm text-white">Create pathway</button>
          {error ? <p className="text-xs text-danger">{error}</p> : null}
          {saved ? <p className="text-xs text-primary-hover">Saved.</p> : null}
          {pathwayId ? (
            <Link href={`/learning/admin/pathways/${pathwayId}/edit`} className="text-xs text-teal-700 hover:underline">
              Continue to pathway item authoring
            </Link>
          ) : null}
        </div>
      </PageShell>
  );
}
