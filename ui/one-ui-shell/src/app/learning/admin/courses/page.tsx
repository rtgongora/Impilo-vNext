"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LearningDataTable, type LearningTableColumn, type LearningTableFilter } from "@/components/learning/LearningDataTable";
import { useFundoCatalog, type FundoCourseSummary } from "@/hooks/queries/useFundoCatalog";
import { FundoCourseForm, type FundoCourseFormValues } from "@/components/learning/FundoCourseForm";
import { LearningOverlayForm } from "@/components/learning/LearningOverlayForm";
import { useCreateFundoCourse } from "@/hooks/queries/useFundoLms";

export default function AdminCoursesPage() {
  const { data, refetch } = useFundoCatalog({ status: "ALL", limit: 100 });
  const createCourse = useCreateFundoCourse();
  const [formOpen, setFormOpen] = useState(false);
  const [createdCourseId, setCreatedCourseId] = useState("");
  const items = data?.data?.items ?? [];
  const columns: Array<LearningTableColumn<FundoCourseSummary>> = [
    {
      key: "title",
      label: "Course",
      render: (course) => (
        <span>
          <span className="block font-medium text-gray-900">{course.title}</span>
          <span className="mt-0.5 block font-mono text-xs text-gray-500">{course.code}</span>
        </span>
      ),
      searchText: (course) => `${course.title} ${course.code} ${course.description ?? ""}`,
    },
    { key: "status", label: "Status", render: (course) => course.status },
    { key: "category", label: "Category", render: (course) => course.category ?? "-" },
    { key: "level", label: "Level", render: (course) => course.level ?? "-" },
    { key: "language", label: "Lang", render: (course) => course.language ?? "en" },
    { key: "duration", label: "Min", render: (course) => String(course.estimatedDurationMinutes ?? "-") },
    { key: "cpd", label: "CPD", render: (course) => (course.cpdEligible ? `Yes ${course.cpdPoints ?? ""}` : "-") },
    {
      key: "actions",
      label: "Actions",
      render: (course) => (
        <span className="flex items-center justify-end gap-3 whitespace-nowrap">
          <Link href={`/learning/admin/courses/${course.id}/edit`} className="font-medium text-impilo-700 hover:underline">Edit</Link>
          <Link href={`/learning/courses/${course.id}`} className="text-gray-600 hover:underline">View</Link>
        </span>
      ),
    },
  ];
  const filters: Array<LearningTableFilter<FundoCourseSummary>> = [
    {
      key: "status",
      label: "Status",
      valueFor: (course) => course.status,
      options: ["DRAFT", "PUBLISHED", "ARCHIVED"].map((value) => ({ label: value, value })),
    },
    {
      key: "category",
      label: "Category",
      valueFor: (course) => course.category ?? "",
      options: Array.from(new Set(items.map((course) => course.category).filter(Boolean))).map((value) => ({ label: value!, value: value! })),
    },
    {
      key: "level",
      label: "Level",
      valueFor: (course) => course.level ?? "",
      options: Array.from(new Set(items.map((course) => course.level).filter(Boolean))).map((value) => ({ label: value!, value: value! })),
    },
  ];

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
        <LearningDataTable
          rows={items}
          columns={columns}
          filters={filters}
          emptyText="No courses have been created yet."
          searchPlaceholder="Search courses, codes or descriptions"
          getRowKey={(course) => course.id}
          dense
        />
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
