"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";

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
  const modules = ((((structureData?.data as Record<string, unknown>)?.structure as Record<string, unknown>)?.modules as Array<Record<string, unknown>>) ?? []).filter(Boolean);

  async function save() {
    if (!courseId) return;
    setMessage("");
    await apiClient.put(`/internal/v1/learning/v11/catalog/${encodeURIComponent(courseId)}`, {
      title,
      status,
    });
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
    await apiClient.post(`/internal/v1/learning/v11/courses/${encodeURIComponent(courseId)}/modules`, {
      title: moduleTitle.trim(),
      status: "DRAFT",
    });
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
    await apiClient.post(`/internal/v1/learning/v11/modules/${encodeURIComponent(moduleIdForLesson)}/lessons`, {
      title: lessonTitle.trim(),
      contentType: lessonContentType,
      contentBody: lessonBody.trim(),
      contentRef: lessonContentRef.trim() || undefined,
      contentFormat: lessonContentFormat,
      contentBlocksJson: lessonBlocksText.trim() || undefined,
      status: "DRAFT",
      required: true,
    });
    setLessonTitle("");
    setLessonBody("");
    setLessonContentRef("");
    setMessage("Lesson created.");
    void refetch();
  }

  return (
    <AppLayout>
      <PageShell title="Edit course" subtitle="Update course metadata and publish/archive status.">
        <div className="max-w-xl space-y-2 rounded border border-gray-200 bg-white p-4">
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Updated title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <select className="w-full rounded border border-gray-300 px-2 py-1 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
          <button onClick={save} className="rounded bg-teal-700 px-3 py-1.5 text-sm text-white">Save metadata</button>
          {saved ? <p className="text-xs text-emerald-700">Saved.</p> : null}
          {message ? <p className="text-xs text-gray-600">{message}</p> : null}
        </div>
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <div className="rounded border border-gray-200 bg-white p-4">
            <p className="text-sm font-medium text-gray-900">Add module</p>
            <input className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Module title" value={moduleTitle} onChange={(e) => setModuleTitle(e.target.value)} />
            <button onClick={addModule} className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">Add module</button>
            <ul className="mt-3 space-y-1 text-xs text-gray-600">
              {modules.map((m) => (
                <li key={String(m.id)}>{String(m.title ?? "Module")} ({String(m.id)})</li>
              ))}
            </ul>
          </div>
          <div className="rounded border border-gray-200 bg-white p-4">
            <p className="text-sm font-medium text-gray-900">Add lesson</p>
            <select className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" value={moduleIdForLesson} onChange={(e) => setModuleIdForLesson(e.target.value)}>
              <option value="">Select module</option>
              {modules.map((m) => (
                <option key={String(m.id)} value={String(m.id)}>{String(m.title ?? m.id)}</option>
              ))}
            </select>
            <input className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Lesson title" value={lessonTitle} onChange={(e) => setLessonTitle(e.target.value)} />
            <select className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" value={lessonContentType} onChange={(e) => setLessonContentType(e.target.value)}>
              <option value="TEXT">TEXT</option>
              <option value="DOCUMENT">DOCUMENT</option>
              <option value="LINK">LINK</option>
              <option value="VIDEO">VIDEO</option>
              <option value="PRACTICAL_TASK">PRACTICAL_TASK</option>
            </select>
            <select className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" value={lessonContentFormat} onChange={(e) => setLessonContentFormat(e.target.value)}>
              <option value="PLAIN_TEXT">PLAIN_TEXT</option>
              <option value="MARKDOWN">MARKDOWN</option>
              <option value="STRUCTURED_BLOCKS">STRUCTURED_BLOCKS</option>
            </select>
            <textarea className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Lesson text content" value={lessonBody} onChange={(e) => setLessonBody(e.target.value)} />
            <input className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Reference URL (video/document/link)" value={lessonContentRef} onChange={(e) => setLessonContentRef(e.target.value)} />
            <textarea className="mt-2 w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Structured blocks JSON" value={lessonBlocksText} onChange={(e) => setLessonBlocksText(e.target.value)} />
            <button onClick={addLesson} className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">Add lesson</button>
            <ul className="mt-3 space-y-1 text-xs text-gray-600">
              {modules.flatMap((m) => ((m.lessons as Array<Record<string, unknown>>) ?? [])).slice(0, 8).map((lesson) => (
                <li key={String(lesson.id)}>
                  {String(lesson.title ?? lesson.id)} • {String(lesson.contentType ?? "TEXT")} • {String(lesson.contentFormat ?? "PLAIN_TEXT")}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
