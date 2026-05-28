"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateLibraryResource } from "@/hooks/queries/useFundoStudio";

export default function LearningLibraryUploadsPage() {
  const upload = useCreateLibraryResource();
  const [title, setTitle] = useState("");
  const [resourceType, setResourceType] = useState("PDF");

  async function onUpload() {
    await upload.mutateAsync({
      title,
      resourceType,
      reviewStatus: "DRAFT",
      sourceAttribution: "Uploaded via One UI",
      metadata: { uploadMode: "metadata-only" },
    });
    setTitle("");
  }

  return (
    <AppLayout>
      <PageShell title="Library Uploads" subtitle="Metadata-first upload flow that safely stores references and governance tags.">
        <div className="rounded border border-gray-200 bg-white p-4">
          <div className="grid gap-2 md:grid-cols-3">
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Resource title" className="rounded border border-gray-300 px-3 py-2 text-sm" />
            <select value={resourceType} onChange={(e) => setResourceType(e.target.value)} className="rounded border border-gray-300 px-3 py-2 text-sm">
              {["PDF", "WORD", "POWERPOINT", "IMAGE", "VIDEO", "AUDIO", "SOP", "JOB_AID", "LINK"].map((t) => <option key={t}>{t}</option>)}
            </select>
            <button onClick={onUpload} disabled={!title.trim() || upload.isPending} className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60">
              Save draft metadata
            </button>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
