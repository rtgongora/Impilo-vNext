"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { BookOpen, Layers, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";
import {
  useCreateFundoLesson,
  useCreateFundoModule,
  useUpdateFundoCourse,
  useUpdateFundoLesson,
  useUpdateFundoModule,
} from "@/hooks/queries/useFundoLms";

type LessonRow = Record<string, unknown> & { moduleTitle: unknown };

export default function EditCoursePage() {
  const params = useParams<{ courseId: string }>();
  const courseId = params?.courseId;
  const { data: structureData, refetch } = useFundoCourseStructure(courseId);
  const [title, setTitle] = useState("");
  const [status, setStatus] = useState("PUBLISHED");
  const [moduleTitle, setModuleTitle] = useState("");
  const [lessonTitle, setLessonTitle] = useState("");
  const [lessonBody, setLessonBody] = useState("");
  const [lessonContentType, setLessonContentType] = useState("TEXT");
  const [lessonContentRef, setLessonContentRef] = useState("");
  const [lessonContentFormat, setLessonContentFormat] = useState("PLAIN_TEXT");
  const [lessonBlocksText, setLessonBlocksText] = useState('[{"type":"heading","text":"Section title"},{"type":"paragraph","text":"Section body"}]');
  const [moduleIdForLesson, setModuleIdForLesson] = useState("");
  const [message, setMessage] = useState("");
  const [saved, setSaved] = useState(false);
  const updateCourse = useUpdateFundoCourse();
  const createModule = useCreateFundoModule();
  const updateModule = useUpdateFundoModule();
  const createLesson = useCreateFundoLesson();
  const updateLesson = useUpdateFundoLesson();
  const structure = ((structureData?.data as Record<string, unknown>)?.structure as Record<string, unknown> | undefined) ?? {};
  const modules = ((structure.modules as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const lessons: LessonRow[] = modules.flatMap((m) =>
    ((m.lessons as Array<Record<string, unknown>>) ?? []).map((lesson) => ({ ...lesson, moduleTitle: m.title })),
  );

  useEffect(() => {
    if (!structure.id) return;
    setTitle(String(structure.title ?? ""));
    setStatus(String(structure.status ?? "PUBLISHED"));
  }, [structure.id, structure.status, structure.title]);

  async function save() {
    if (!courseId) return;
    setMessage("");
    await updateCourse.mutateAsync({ courseId, body: { title: title.trim() || undefined, status } });
    setSaved(true);
    setMessage("Course metadata updated.");
    void refetch();
  }

  async function addModule() {
    if (!courseId || !moduleTitle.trim()) {
      setMessage("Module title is required.");
      return;
    }
    setMessage("");
    await createModule.mutateAsync({ courseId, body: { title: moduleTitle.trim(), status: "DRAFT" } });
    setModuleTitle("");
    setMessage("Module created.");
    void refetch();
  }

  async function addLesson() {
    if (!moduleIdForLesson || !lessonTitle.trim()) {
      setMessage("Module and lesson title are required.");
      return;
    }
    setMessage("");
    await createLesson.mutateAsync({
      moduleId: moduleIdForLesson,
      body: {
        title: lessonTitle.trim(),
        contentType: lessonContentType,
        contentBody: lessonBody.trim(),
        contentRef: lessonContentRef.trim() || undefined,
        contentFormat: lessonContentFormat,
        contentBlocksJson: lessonBlocksText.trim() || undefined,
        status: "DRAFT",
        required: true,
      },
    });
    setLessonTitle("");
    setLessonBody("");
    setLessonContentRef("");
    setMessage("Lesson created.");
    void refetch();
  }

  async function renameModule(moduleId: string, currentTitle: unknown) {
    const nextTitle = window.prompt("Module title", String(currentTitle ?? "").trim());
    if (!nextTitle) {
      return;
    }
    await updateModule.mutateAsync({ moduleId, body: { title: nextTitle.trim(), status: "DRAFT" } });
    setMessage("Module updated.");
    void refetch();
  }

  async function updateExistingLesson(lessonId: string) {
    if (!lessonTitle.trim()) {
      setMessage("Enter the replacement lesson title before updating.");
      return;
    }
    await updateLesson.mutateAsync({
      lessonId,
      body: {
        title: lessonTitle.trim(),
        contentType: lessonContentType,
        contentBody: lessonBody.trim(),
        contentRef: lessonContentRef.trim() || undefined,
        contentFormat: lessonContentFormat,
        contentBlocksJson: lessonBlocksText.trim() || undefined,
        status: "DRAFT",
        required: true,
      },
    });
    setMessage("Lesson updated.");
    void refetch();
  }

  return (
    <AppLayout>
      <PageShell
        title="Edit course"
        subtitle={structure.code ? `${String(structure.title ?? "Course")} • ${String(structure.code)}` : "Update course metadata, modules and lessons."}
        actions={
          <Link href="/learning/admin/courses" className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
            Back to courses
          </Link>
        }
      >
        <div className="grid gap-5 xl:grid-cols-[minmax(0,0.9fr)_minmax(420px,1.1fr)]">
          <div className="space-y-5">
            <section className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="mb-3 flex items-center gap-2">
                <Save className="h-4 w-4 text-impilo-700" />
                <p className="text-sm font-semibold text-gray-900">Course metadata</p>
              </div>
              <label className="block text-xs font-medium text-gray-500">Title</label>
              <input className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="Course title" value={title} onChange={(e) => setTitle(e.target.value)} />
              <label className="mt-3 block text-xs font-medium text-gray-500">Status</label>
              <select className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="DRAFT">DRAFT</option>
                <option value="PUBLISHED">PUBLISHED</option>
                <option value="ARCHIVED">ARCHIVED</option>
              </select>
              <button onClick={save} className="mt-4 inline-flex items-center gap-2 rounded-lg bg-impilo-600 px-3 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50" disabled={updateCourse.isPending}>
                <Save className="h-4 w-4" /> Save metadata
              </button>
              {saved ? <p className="mt-2 text-xs text-emerald-700">Saved.</p> : null}
              {message ? <p className="mt-2 text-xs text-gray-600">{message}</p> : null}
            </section>

            <section className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="mb-3 flex items-center gap-2">
                <Layers className="h-4 w-4 text-impilo-700" />
                <p className="text-sm font-semibold text-gray-900">Modules</p>
              </div>
              <div className="flex gap-2">
                <input className="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="New module title" value={moduleTitle} onChange={(e) => setModuleTitle(e.target.value)} />
                <button onClick={addModule} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50" disabled={createModule.isPending}>Add</button>
              </div>
              <div className="mt-4 space-y-2">
                {modules.map((m, index) => (
                  <div
                    key={String(m.id)}
                    className={`flex items-start justify-between gap-3 rounded-lg border px-3 py-2 text-sm transition ${moduleIdForLesson === String(m.id) ? "border-impilo-500 bg-impilo-50" : "border-gray-200 bg-gray-50"}`}
                  >
                    <button type="button" onClick={() => setModuleIdForLesson(String(m.id))} className="min-w-0 flex-1 text-left">
                        <span className="block font-medium text-gray-900">{index + 1}. {String(m.title ?? "Module")}</span>
                        <span className="mt-1 block text-xs text-gray-500">{((m.lessons as Array<Record<string, unknown>>) ?? []).length} lessons</span>
                    </button>
                    <button type="button" onClick={() => renameModule(String(m.id), m.title)} className="shrink-0 text-xs font-medium text-impilo-700 hover:underline">
                      Rename
                    </button>
                  </div>
                ))}
                {modules.length === 0 ? <p className="rounded-lg border border-dashed border-gray-200 px-3 py-4 text-sm text-gray-500">No modules yet.</p> : null}
              </div>
            </section>
          </div>

          <section className="rounded-lg border border-gray-200 bg-white p-4">
            <div className="mb-3 flex items-center gap-2">
              <BookOpen className="h-4 w-4 text-impilo-700" />
              <p className="text-sm font-semibold text-gray-900">Lessons</p>
            </div>
            <div className="grid gap-3 lg:grid-cols-2">
              <select className="rounded-lg border border-gray-300 px-3 py-2 text-sm lg:col-span-2" value={moduleIdForLesson} onChange={(e) => setModuleIdForLesson(e.target.value)}>
                <option value="">Select module</option>
                {modules.map((m) => (
                  <option key={String(m.id)} value={String(m.id)}>{String(m.title ?? m.id)}</option>
                ))}
              </select>
              <input className="rounded-lg border border-gray-300 px-3 py-2 text-sm lg:col-span-2" placeholder="Lesson title" value={lessonTitle} onChange={(e) => setLessonTitle(e.target.value)} />
              <select className="rounded-lg border border-gray-300 px-3 py-2 text-sm" value={lessonContentType} onChange={(e) => setLessonContentType(e.target.value)}>
                <option value="TEXT">TEXT</option>
                <option value="DOCUMENT">DOCUMENT</option>
                <option value="LINK">LINK</option>
                <option value="VIDEO">VIDEO</option>
                <option value="PRACTICAL_TASK">PRACTICAL_TASK</option>
              </select>
              <select className="rounded-lg border border-gray-300 px-3 py-2 text-sm" value={lessonContentFormat} onChange={(e) => setLessonContentFormat(e.target.value)}>
                <option value="PLAIN_TEXT">PLAIN_TEXT</option>
                <option value="MARKDOWN">MARKDOWN</option>
                <option value="STRUCTURED_BLOCKS">STRUCTURED_BLOCKS</option>
              </select>
              <textarea className="min-h-24 rounded-lg border border-gray-300 px-3 py-2 text-sm lg:col-span-2" placeholder="Lesson text content" value={lessonBody} onChange={(e) => setLessonBody(e.target.value)} />
              <input className="rounded-lg border border-gray-300 px-3 py-2 text-sm lg:col-span-2" placeholder="Reference URL (video/document/link)" value={lessonContentRef} onChange={(e) => setLessonContentRef(e.target.value)} />
              <textarea className="min-h-20 rounded-lg border border-gray-300 px-3 py-2 text-sm lg:col-span-2" placeholder="Structured blocks JSON" value={lessonBlocksText} onChange={(e) => setLessonBlocksText(e.target.value)} />
            </div>
            <button onClick={addLesson} className="mt-3 rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50" disabled={createLesson.isPending}>Add lesson</button>

            <div className="mt-5">
              <p className="mb-2 text-xs font-semibold uppercase tracking-normal text-gray-500">Existing lessons</p>
              <div className="space-y-2">
                {lessons.map((lesson) => (
                  <div key={String(lesson.id)} className="flex items-start justify-between gap-3 rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 text-sm">
                    <span className="min-w-0">
                      <span className="block font-medium text-gray-900">{String(lesson.title ?? lesson.id)}</span>
                      <span className="mt-1 block text-xs text-gray-500">
                        {String(lesson.moduleTitle ?? "Module")} • {String(lesson.contentType ?? "TEXT")} • {String(lesson.contentFormat ?? "PLAIN_TEXT")}
                      </span>
                    </span>
                    <button onClick={() => updateExistingLesson(String(lesson.id))} className="shrink-0 text-xs font-medium text-impilo-700 hover:underline">
                      Update from form
                    </button>
                  </div>
                ))}
                {lessons.length === 0 ? <p className="rounded-lg border border-dashed border-gray-200 px-3 py-4 text-sm text-gray-500">No lessons yet.</p> : null}
              </div>
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
