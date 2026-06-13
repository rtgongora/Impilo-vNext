"use client";

import { useMemo, useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";
import { useCreateFundoLesson, useCreateFundoModule } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioCourseBuilderPage({ params }: { params: { courseId: string } }) {
  const { courseId } = params;
  const { data, refetch } = useFundoCourseStructure(courseId);
  const structure = data?.data?.structure;
  const modules = structure?.modules ?? [];
  const [moduleTitle, setModuleTitle] = useState("");
  const [lessonTitle, setLessonTitle] = useState("");
  const [selectedModuleId, setSelectedModuleId] = useState<string>("");
  const [previewMode, setPreviewMode] = useState<"CREATOR" | "LEARNER">("CREATOR");
  const [contentBlocksText, setContentBlocksText] = useState(
    JSON.stringify(
      [
        { type: "heading", text: "Lesson heading" },
        { type: "text", text: "Draft lesson body text." },
        { type: "quiz_block", title: "Quick check", questions: [] },
      ],
      null,
      2,
    ),
  );

  const createModule = useCreateFundoModule(courseId);
  const createLesson = useCreateFundoLesson(selectedModuleId || "");

  const moduleOptions = useMemo(() => modules.map((m) => ({ id: m.id, title: m.title })), [modules]);

  async function onAddModule() {
    await createModule.mutateAsync({ title: moduleTitle, status: "DRAFT" });
    setModuleTitle("");
    void refetch();
  }

  async function onAddLesson() {
    let blocks: unknown = [];
    try {
      blocks = JSON.parse(contentBlocksText);
    } catch {
      blocks = [{ type: "text", text: contentBlocksText }];
    }
    await createLesson.mutateAsync({
      title: lessonTitle,
      status: "DRAFT",
      contentType: "INTERACTIVE",
      contentBody: "Draft lesson",
      contentBlocks: blocks,
    });
    setLessonTitle("");
    void refetch();
  }

  return (
    <FundoStudioWorkspace
      title="Course Builder"
      subtitle="Add modules/lessons, author block-based lesson content, and prepare learner preview."
    >
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-3 rounded-lg border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Add module</p>
          <input value={moduleTitle} onChange={(e) => setModuleTitle(e.target.value)} className="w-full rounded border border-border px-3 py-2 text-sm" placeholder="Module title" />
          <button onClick={onAddModule} disabled={!moduleTitle.trim() || createModule.isPending} className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60">
            Add module
          </button>
        </div>
        <div className="space-y-3 rounded-lg border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Add lesson</p>
          <select value={selectedModuleId} onChange={(e) => setSelectedModuleId(e.target.value)} className="w-full rounded border border-border px-3 py-2 text-sm">
            <option value="">Select module</option>
            {moduleOptions.map((m) => (
              <option key={m.id} value={m.id}>{m.title}</option>
            ))}
          </select>
          <input value={lessonTitle} onChange={(e) => setLessonTitle(e.target.value)} className="w-full rounded border border-border px-3 py-2 text-sm" placeholder="Lesson title" />
          <textarea value={contentBlocksText} onChange={(e) => setContentBlocksText(e.target.value)} className="h-40 w-full rounded border border-border px-3 py-2 text-xs" />
          <button onClick={onAddLesson} disabled={!selectedModuleId || !lessonTitle.trim() || createLesson.isPending} className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60">
            Add lesson
          </button>
        </div>
      </div>
      <div className="mt-4 rounded-lg border border-border bg-card p-4">
        <div className="mb-2 flex items-center justify-between">
          <p className="text-sm font-semibold text-foreground">Current structure</p>
          <div className="flex gap-2 text-xs">
            <button
              type="button"
              onClick={() => setPreviewMode("CREATOR")}
              className={`rounded border px-2 py-1 ${previewMode === "CREATOR" ? "border-teal-600 bg-teal-50 text-teal-700" : "border-border text-foreground"}`}
            >
              Creator preview
            </button>
            <button
              type="button"
              onClick={() => setPreviewMode("LEARNER")}
              className={`rounded border px-2 py-1 ${previewMode === "LEARNER" ? "border-teal-600 bg-teal-50 text-teal-700" : "border-border text-foreground"}`}
            >
              Learner preview
            </button>
          </div>
        </div>
        <ul className="mt-2 space-y-2 text-sm text-foreground">
          {modules.map((m) => (
            <li key={m.id}>
              <p className="font-medium">{m.title}</p>
              <p className="text-xs text-muted-foreground">{m.lessons?.length ?? 0} lessons · {previewMode === "LEARNER" ? "learner" : "author"} view</p>
              {Array.isArray(m.lessons) && m.lessons.length > 0 ? (
                <ol className="mt-1 list-decimal pl-5 text-xs text-muted-foreground">
                  {m.lessons.map((lesson) => (
                    <li key={lesson.id}>{lesson.title}</li>
                  ))}
                </ol>
              ) : null}
            </li>
          ))}
        </ul>
      </div>
    </FundoStudioWorkspace>
  );
}
