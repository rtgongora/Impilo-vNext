"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FundoContentFormatsGuide } from "@/components/learning/FundoContentFormatsGuide";
import { useCreateLibraryResource } from "@/hooks/queries/useFundoStudio";

const RESOURCE_TO_LESSON: Record<string, string> = {
  PDF: "DOCUMENT",
  SOP: "DOCUMENT",
  JOB_AID: "DOCUMENT",
  VIDEO: "VIDEO",
  LINK: "LINK",
  IMAGE: "DOCUMENT",
  AUDIO: "DOCUMENT",
  WORD: "DOCUMENT",
  POWERPOINT: "DOCUMENT",
};

export default function LearningLibraryUploadsPage() {
  const upload = useCreateLibraryResource();
  const [title, setTitle] = useState("");
  const [resourceType, setResourceType] = useState("PDF");
  const [contentRef, setContentRef] = useState("");
  const [saved, setSaved] = useState(false);

  async function onUpload() {
    await upload.mutateAsync({
      title,
      resourceType,
      contentRef: contentRef.trim() || undefined,
      reviewStatus: "DRAFT",
      sourceAttribution: "MOHCC governed upload",
      metadata: {
        uploadMode: "reference-url",
        suggestedLessonContentType: RESOURCE_TO_LESSON[resourceType] ?? "DOCUMENT",
        mimeHint: resourceType,
      },
    });
    setTitle("");
    setContentRef("");
    setSaved(true);
  }

  return (
    <AppLayout>
      <PageShell title="Library Uploads" subtitle="Register governed learning assets by format — metadata and HTTPS reference URL.">
        <FundoContentFormatsGuide />
        <div className="rounded border border-border bg-card p-4" data-testid="fundo-library-upload-form">
          <div className="grid gap-2 md:grid-cols-2">
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Resource title (e.g. IPC refresher SOP 2026)"
              className="rounded border border-border px-3 py-2 text-sm"
              data-testid="fundo-upload-title"
            />
            <select
              value={resourceType}
              onChange={(e) => setResourceType(e.target.value)}
              className="rounded border border-border px-3 py-2 text-sm"
              data-testid="fundo-upload-type"
            >
              {["PDF", "WORD", "POWERPOINT", "IMAGE", "VIDEO", "AUDIO", "SOP", "JOB_AID", "LINK"].map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
            <input
              value={contentRef}
              onChange={(e) => setContentRef(e.target.value)}
              placeholder="HTTPS content URL (document store, video, or external link)"
              className="md:col-span-2 rounded border border-border px-3 py-2 text-sm"
              data-testid="fundo-upload-content-ref"
            />
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            Suggested lesson type: <span className="font-mono">{RESOURCE_TO_LESSON[resourceType] ?? "DOCUMENT"}</span>
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              onClick={onUpload}
              disabled={!title.trim() || upload.isPending}
              className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60"
              data-testid="fundo-upload-save"
            >
              Save draft metadata
            </button>
            <Link href="/learning/admin/courses" className="rounded border border-border px-3 py-2 text-sm text-foreground">
              Bind in course authoring
            </Link>
          </div>
          {saved ? (
            <p className="mt-2 text-sm text-primary-hover" data-testid="fundo-upload-success">
              Draft saved — bind this asset to a lesson in course authoring.
            </p>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
