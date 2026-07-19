"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoLanguageOptions } from "@/hooks/queries/useFundoLms";
import { useCreateFundoCourse } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioNewCoursePage() {
  const router = useRouter();
  const createCourse = useCreateFundoCourse();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [language, setLanguage] = useState("en");
  const [error, setError] = useState<string | null>(null);
  const { data: languageData } = useFundoLanguageOptions();
  const languages = languageData?.data?.items?.length
    ? languageData.data.items
    : [{ code: "en", label: "English", nativeLabel: "English" }];

  async function onCreate() {
    setError(null);
    try {
      const res = (await createCourse.mutateAsync({
        title,
        description,
        status: "DRAFT",
        category: "GENERAL",
        level: "FOUNDATION",
        language,
      })) as { data?: { course?: { id?: string } } };
      const id = res?.data?.course?.id;
      if (!id) {
        setError("The course was not created — the service returned no course id. Please retry.");
        return;
      }
      router.push(`/learning/studio/courses/${id}/builder`);
    } catch (e) {
      const err = e as { status?: number; error?: { message?: string } };
      setError(
        err?.error?.message ??
          (err?.status === 403
            ? "You do not have authoring permission for this learning space."
            : "Failed to create the draft course. Please retry."),
      );
    }
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
        <label className="block text-sm text-foreground">
          Language
          <select value={language} onChange={(e) => setLanguage(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm">
            {languages.map((option) => (
              <option key={option.code} value={option.code}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        {error ? (
          <div className="rounded-lg border border-warning/35 bg-warning-soft p-3 text-sm text-warning-foreground" data-testid="fundo-create-course-error">
            {error}
          </div>
        ) : null}
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
