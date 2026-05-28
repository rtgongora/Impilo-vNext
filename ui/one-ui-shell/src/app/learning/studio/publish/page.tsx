"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoCatalog } from "@/hooks/queries/useFundoCatalog";
import { useFundoStudioReadiness, useScheduleLearningNotification, useUpdateFundoCourse } from "@/hooks/queries/useFundoStudio";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";

export default function FundoStudioPublishPage() {
  const { data, refetch } = useFundoCatalog({ limit: 100 });
  const [selectedCourseId, setSelectedCourseId] = useState<string>("");
  const subject = useLearningSubject();
  const items = data?.data?.items ?? [];
  const readiness = useFundoStudioReadiness(selectedCourseId || undefined);
  const schedule = useScheduleLearningNotification();
  const selectedCourse = useMemo(() => items.find((c) => c.id === selectedCourseId), [items, selectedCourseId]);
  const publishMutation = useUpdateFundoCourse(selectedCourseId || "");

  async function updateStatus(status: "DRAFT" | "PUBLISHED" | "ARCHIVED") {
    if (!selectedCourseId) return;
    await publishMutation.mutateAsync({ status });
    if (status === "PUBLISHED" && subject?.subjectType && subject?.subjectId) {
      await schedule.mutateAsync({
        subjectType: subject.subjectType,
        subjectId: subject.subjectId,
        eventCode: "course.published",
        title: "New Fundo course published",
        message: `${selectedCourse?.title ?? "A course"} is now published.`,
        channelPreference: "IN_APP",
        status: "PENDING",
      });
    }
    void refetch();
  }

  return (
    <FundoStudioWorkspace title="Studio Publish" subtitle="Draft/review/publish/archive controls with readiness checks and learner preview links.">
      <div className="mb-3 rounded border border-gray-200 bg-white p-3">
        <label className="text-xs text-gray-600">Select course for publish controls</label>
        <select
          value={selectedCourseId}
          onChange={(e) => setSelectedCourseId(e.target.value)}
          className="mt-1 w-full rounded border border-gray-300 px-3 py-2 text-sm"
        >
          <option value="">Choose a course...</option>
          {items.map((c) => (
            <option key={c.id} value={c.id}>
              {c.title} ({c.status})
            </option>
          ))}
        </select>
        {selectedCourseId ? (
          <div className="mt-2 flex flex-wrap gap-2 text-xs">
            <button type="button" onClick={() => updateStatus("DRAFT")} className="rounded border border-gray-300 px-2 py-1 text-gray-700">Move to Draft</button>
            <button type="button" onClick={() => updateStatus("PUBLISHED")} className="rounded border border-teal-400 px-2 py-1 text-teal-700">Publish</button>
            <button type="button" onClick={() => updateStatus("ARCHIVED")} className="rounded border border-red-300 px-2 py-1 text-red-700">Archive</button>
          </div>
        ) : null}
      </div>
      {selectedCourseId ? (
        <div className="mb-3 rounded border border-gray-200 bg-white p-3 text-xs text-gray-700">
          <p className="font-semibold text-gray-900">Readiness</p>
          <ul className="mt-1 list-disc pl-4">
            {(((readiness.data?.data ?? {}) as { readinessChecklist?: Array<{ item: string; ok: boolean }> }).readinessChecklist ?? []).map((item) => (
              <li key={item.item}>
                {item.ok ? "✓" : "•"} {item.item}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <ul className="space-y-2">
        {items.map((c) => (
          <li key={c.id} className="rounded border border-gray-200 bg-white p-3 text-sm">
            <p className="font-medium text-gray-900">{c.title}</p>
            <p className="text-xs text-gray-500">Current state: {c.status}</p>
            <div className="mt-2 flex gap-2 text-xs">
              <Link href={`/learning/studio/courses/${c.id}`} className="rounded border border-gray-300 px-2 py-1 text-gray-700">Readiness</Link>
              <Link href={`/learning/studio/courses/${c.id}/builder`} className="rounded border border-gray-300 px-2 py-1 text-gray-700">Revise builder</Link>
            </div>
          </li>
        ))}
      </ul>
    </FundoStudioWorkspace>
  );
}
