"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoCatalog } from "@/hooks/queries/useFundoCatalog";
import { FundoCourseForm, type FundoCourseFormValues } from "@/components/learning/FundoCourseForm";
import { LearningOverlayForm } from "@/components/learning/LearningOverlayForm";
import { useCreateFundoCourse } from "@/hooks/queries/useFundoLms";

export default function AdminCoursesPage() {
  const { data, refetch } = useFundoCatalog({ status: "ALL", limit: 100 });
  const createCourse = useCreateFundoCourse();
  const [formOpen, setFormOpen] = useState(false);
  const [createdCourseId, setCreatedCourseId] = useState("");
  const items = data?.data?.items ?? [];

  async function create(values: FundoCourseFormValues) {
    const res = (await createCourse.mutateAsync({ ...values })) as Record<string, unknown>;
    const envelope = res.data as Record<string, unknown> | undefined;
    const course = envelope?.course as Record<string, unknown> | undefined;
    const id = String(course?.id ?? "");
    setCreatedCourseId(id);
    void refetch();
  }

  return (
    <AppLayout>
      <PageShell
        title="Admin courses"
        subtitle="Create/edit/publish native Fundo courses."
        actions={
          <button
            type="button"
            onClick={() => {
              setCreatedCourseId("");
              setFormOpen(true);
            }}
            className="rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-impilo-700"
          >
            New course
          </button>
        }
      >
        <ul className="mt-3 space-y-2">
          {items.map((c) => (
            <li key={c.id} className="rounded border border-gray-200 bg-white p-3 text-sm">
              <p className="font-medium text-gray-900">{c.title}</p>
              <Link href={`/learning/admin/courses/${c.id}/edit`} className="text-teal-700 hover:underline">
                Edit
              </Link>
            </li>
          ))}
        </ul>
        <LearningOverlayForm
          open={formOpen}
          onClose={() => setFormOpen(false)}
          title="New Fundo course"
          subtitle="Create the course shell. Modules, lessons and assessments are added after the course exists."
          footer={
            createdCourseId ? (
              <Link href={`/learning/admin/courses/${createdCourseId}/edit`} className="text-sm font-medium text-impilo-700 hover:underline">
                Continue to modules and lessons
              </Link>
            ) : null
          }
        >
          <FundoCourseForm submitLabel="Create course" isSubmitting={createCourse.isPending} onSubmit={create} />
          {createCourse.isError ? <p className="mt-3 text-xs text-rose-700">Failed to create course. Check fields and try again.</p> : null}
        </LearningOverlayForm>
      </PageShell>
    </AppLayout>
  );
}
