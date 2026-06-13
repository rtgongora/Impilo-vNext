"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useCreateFundoCourse } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioNewCoursePage() {
  const router = useRouter();
  const createCourse = useCreateFundoCourse();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");

  async function onCreate() {
    const res = (await createCourse.mutateAsync({
      title,
      description,
      status: "DRAFT",
      category: "GENERAL",
      level: "FOUNDATION",
      language: "en",
    })) as { data?: { course?: { id?: string } } };
    const id = res?.data?.course?.id;
    if (id) router.push(`/learning/studio/courses/${id}/builder`);
  }

  return (
    <FundoStudioWorkspace title="New Studio Course" subtitle="Create a draft course first, then enrich with blocks, assessments, surveys and media.">
      <div className="space-y-3 rounded-lg border border-border bg-card p-4">
        <label className="block text-sm text-foreground">
          Course title
          <input value={title} onChange={(e) => setTitle(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-sm text-foreground">
          Description
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm" rows={4} />
        </label>
        <button
          type="button"
          disabled={!title.trim() || createCourse.isPending}
          onClick={onCreate}
          className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60"
        >
          {createCourse.isPending ? "Creating..." : "Create draft course"}
        </button>
      </div>
    </FundoStudioWorkspace>
  );
}
