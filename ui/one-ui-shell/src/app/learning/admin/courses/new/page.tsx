"use client";

import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FundoCourseForm, type FundoCourseFormValues } from "@/components/learning/FundoCourseForm";
import { LearningOverlayForm } from "@/components/learning/LearningOverlayForm";
import { useCreateFundoCourse } from "@/hooks/queries/useFundoLms";

export default function NewCoursePage() {
  const createCourse = useCreateFundoCourse();
  const [savedCourseId, setSavedCourseId] = useState("");

  async function save(values: FundoCourseFormValues) {
    const res = (await createCourse.mutateAsync({ ...values })) as Record<string, unknown>;
    const envelope = res.data as Record<string, unknown> | undefined;
    const course = envelope?.course as Record<string, unknown> | undefined;
    setSavedCourseId(String(course?.id ?? ""));
  }

  return (
    <AppLayout>
      <PageShell title="New course" subtitle="Create a native Fundo course shell.">
        <LearningOverlayForm
          open
          title="New Fundo course"
          subtitle="Create the course shell. Modules, lessons and assessments are added after the course exists."
          footer={
            <div className="flex flex-wrap gap-3">
              <Link href="/learning/admin/courses" className="text-sm font-medium text-gray-700 hover:underline">
                Back to Admin courses
              </Link>
              {savedCourseId ? (
                <Link href={`/learning/admin/courses/${savedCourseId}/edit`} className="text-sm font-medium text-impilo-700 hover:underline">
                  Continue to modules and lessons
                </Link>
              ) : null}
            </div>
          }
        >
          <FundoCourseForm submitLabel="Create course" isSubmitting={createCourse.isPending} onSubmit={save} />
          {createCourse.isError ? <p className="mt-3 text-xs text-rose-700">Failed to create course. Check fields and try again.</p> : null}
        </LearningOverlayForm>
      </PageShell>
    </AppLayout>
  );
}
