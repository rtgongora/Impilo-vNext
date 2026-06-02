"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateFundoCourse } from "@/hooks/queries/useFundoLms";

export default function NewCoursePage() {
  const [title, setTitle] = useState("");
  const [code, setCode] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("GENERAL");
  const [level, setLevel] = useState("INTRODUCTORY");
  const [status, setStatus] = useState("DRAFT");
  const [saved, setSaved] = useState<string>("");
  const [error, setError] = useState("");
  const createCourse = useCreateFundoCourse();

  async function save() {
    setError("");
    if (!code.trim() || !title.trim()) {
      setError("Course code and title are required.");
      return;
    }
    try {
      const res = (await createCourse.mutateAsync({
        code: code.trim(),
        title: title.trim(),
        description: description.trim(),
        category,
        level,
        language: "en",
        mandatory: false,
        cpdEligible: false,
        status,
      })) as Record<string, unknown>;
      const data = (res.data as Record<string, unknown> | undefined) ?? {};
      const course = (data.course as Record<string, unknown> | undefined) ?? {};
      const id = (course.id as string | undefined) ?? "";
      setSaved(id);
    } catch {
      setError("Failed to create course. Check fields and try again.");
    }
  }

  return (
    <AppLayout>
      <PageShell title="New course" subtitle="Basic native course authoring form.">
        <div className="max-w-xl space-y-2 rounded border border-gray-200 bg-white p-4">
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Course code" value={code} onChange={(e) => setCode(e.target.value)} />
          <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Course title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <textarea className="w-full rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
          <div className="grid grid-cols-3 gap-2">
            <input className="rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Category" value={category} onChange={(e) => setCategory(e.target.value)} />
            <input className="rounded border border-gray-300 px-2 py-1 text-sm" placeholder="Level" value={level} onChange={(e) => setLevel(e.target.value)} />
            <select className="rounded border border-gray-300 px-2 py-1 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="DRAFT">DRAFT</option>
              <option value="PUBLISHED">PUBLISHED</option>
              <option value="ARCHIVED">ARCHIVED</option>
            </select>
          </div>
          <button onClick={save} disabled={createCourse.isPending} className="rounded bg-impilo-600 px-3 py-1.5 text-sm text-white hover:bg-impilo-700 disabled:opacity-50">Create course</button>
          {error ? <p className="text-xs text-rose-700">{error}</p> : null}
          {saved ? <p className="text-xs text-emerald-700">Created: {saved}</p> : null}
          {saved ? (
            <Link className="text-xs text-teal-700 hover:underline" href={`/learning/admin/courses/${saved}/edit`}>
              Continue to modules and lessons
            </Link>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
