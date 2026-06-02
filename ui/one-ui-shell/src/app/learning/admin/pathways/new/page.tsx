"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LearningOverlayForm } from "@/components/learning/LearningOverlayForm";
import { useCreateFundoPathway } from "@/hooks/queries/useFundoLms";

export default function NewPathwayPage() {
  const [code, setCode] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState("DRAFT");
  const [pathwayId, setPathwayId] = useState("");
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const createPathway = useCreateFundoPathway();

  async function save() {
    setError("");
    if (!code.trim() || !title.trim()) {
      setError("Pathway code and title are required.");
      return;
    }
    const res = (await createPathway.mutateAsync({
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
    <AppLayout>
      <PageShell title="New pathway" subtitle="Basic native pathway authoring.">
        <LearningOverlayForm
          open
          title="New Fundo pathway"
          subtitle="Create the pathway shell. Ordered courses are added after the pathway exists."
          footer={
            <div className="flex flex-wrap gap-3">
              <Link href="/learning/admin/pathways" className="text-sm font-medium text-gray-700 hover:underline">
                Back to Admin pathways
              </Link>
              {pathwayId ? (
                <Link href={`/learning/admin/pathways/${pathwayId}/edit`} className="text-sm font-medium text-impilo-700 hover:underline">
                  Continue to pathway item authoring
                </Link>
              ) : null}
            </div>
          }
        >
          <div className="space-y-3">
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Pathway code" value={code} onChange={(e) => setCode(e.target.value)} />
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Pathway title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <textarea className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
          <select className="w-full rounded border border-gray-300 px-2 py-1 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
          <button onClick={save} className="rounded bg-impilo-600 px-3 py-1.5 text-sm text-white hover:bg-impilo-700 disabled:opacity-50" disabled={createPathway.isPending}>Create pathway</button>
          {error ? <p className="text-xs text-rose-700">{error}</p> : null}
          {saved ? <p className="text-xs text-emerald-700">Saved.</p> : null}
          </div>
        </LearningOverlayForm>
      </PageShell>
    </AppLayout>
  );
}
