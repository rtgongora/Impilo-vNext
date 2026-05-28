"use client";

import { useState } from "react";
import Link from "next/link";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useCreateLibraryResource, useFundoLibraryResources } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioLibraryPage() {
  const { data, refetch } = useFundoLibraryResources();
  const createResource = useCreateLibraryResource();
  const [title, setTitle] = useState("");
  const [resourceType, setResourceType] = useState("DOCUMENT");
  const resources = ((data?.data as { items?: Array<{ id: string; title: string; resourceType?: string; reviewStatus?: string }> } | undefined)?.items ?? []);

  async function onCreate() {
    await createResource.mutateAsync({
      title,
      resourceType,
      reviewStatus: "DRAFT",
      tags: ["studio", "draft"],
    });
    setTitle("");
    void refetch();
  }

  return (
    <FundoStudioWorkspace title="Studio Library" subtitle="Upload and curate reusable resources for courses, lessons, assessments and AI source material.">
      <div className="rounded-lg border border-gray-200 bg-white p-4">
        <p className="text-sm font-semibold text-gray-900">Add library resource metadata</p>
        <div className="mt-2 grid gap-2 md:grid-cols-3">
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Resource title" className="rounded border border-gray-300 px-3 py-2 text-sm" />
          <select value={resourceType} onChange={(e) => setResourceType(e.target.value)} className="rounded border border-gray-300 px-3 py-2 text-sm">
            {["DOCUMENT", "PDF", "VIDEO", "AUDIO", "SOP", "JOB_AID", "GUIDE", "LINK"].map((t) => <option key={t}>{t}</option>)}
          </select>
          <button onClick={onCreate} disabled={!title.trim() || createResource.isPending} className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60">
            Save draft resource
          </button>
        </div>
      </div>
      <div className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
        <p className="text-sm font-semibold text-gray-900">Resources</p>
        <ul className="mt-2 space-y-2 text-sm">
          {resources.map((r) => (
            <li key={r.id} className="flex items-center justify-between rounded border border-gray-100 px-3 py-2">
              <span>{r.title} · {r.resourceType ?? "DOCUMENT"} · {r.reviewStatus ?? "DRAFT"}</span>
              <Link href={`/learning/library/${r.id}`} className="text-teal-700 hover:underline">Open</Link>
            </li>
          ))}
        </ul>
      </div>
    </FundoStudioWorkspace>
  );
}
