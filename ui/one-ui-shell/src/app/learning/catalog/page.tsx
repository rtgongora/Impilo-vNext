"use client";

import Link from "next/link";
import { ArrowLeft, GraduationCap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { LearningDataTable, type LearningTableColumn, type LearningTableFilter } from "@/components/learning/LearningDataTable";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import {
  useFundoCatalog,
  type FundoCourseSummary,
} from "@/hooks/queries/useFundoCatalog";
import { useFundoLearningRecord } from "@/hooks/queries/useFundoLms";

function minutesLabel(value: unknown) {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) && n > 0 ? `${n} min` : "-";
}

/**
 * Phase 6B — live Impilo Fundo catalogue browse page.
 *
 * Replaces the Phase 1 placeholder. Renders the Phase 5B
 * `/internal/v1/learning/v11/catalog` data via the Phase 6B BFF
 * passthrough. Filters live in local state and reuse the existing
 * react-query cache key; degrade-gracefully when learning-service is
 * unavailable (empty state, no hard error surfaced to the user).
 */
export default function LearningCataloguePage() {
  const subject = useLearningSubject();
  const { data, isLoading, isError } = useFundoCatalog({
    status: "ALL",
    limit: 500,
  });
  const { data: recordData } = useFundoLearningRecord(subject);

  const items: FundoCourseSummary[] = data?.data?.items ?? [];
  const record = ((recordData?.data as Record<string, unknown> | undefined)?.record as Record<string, unknown> | undefined) ?? {};
  const cancelledEnrolments = (((record.enrolments as Array<Record<string, unknown>>) ?? [])).filter((item) => String(item.status) === "CANCELLED");
  const columns: Array<LearningTableColumn<FundoCourseSummary>> = [
    {
      key: "title",
      label: "Course",
      render: (course) => <span className="font-medium text-gray-900">{course.title}</span>,
      searchText: (course) => `${course.title} ${course.code} ${course.description ?? ""}`,
    },
    { key: "code", label: "Code", render: (course) => <span className="font-mono text-xs">{course.code}</span> },
    { key: "category", label: "Category", render: (course) => course.category ?? "-" },
    { key: "level", label: "Level", render: (course) => course.level ?? "-" },
    { key: "status", label: "Status", render: (course) => course.status },
    { key: "language", label: "Language", render: (course) => course.language ?? "en" },
    { key: "duration", label: "Duration", render: (course) => minutesLabel(course.estimatedDurationMinutes) },
    { key: "mandatory", label: "Mandatory", render: (course) => (course.mandatory ? "Yes" : "-") },
    { key: "cpd", label: "CPD", render: (course) => (course.cpdEligible ? `Yes ${course.cpdPoints ?? ""}` : "-") },
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
    {
      key: "language",
      label: "Language",
      valueFor: (course) => course.language ?? "en",
      options: Array.from(new Set(items.map((course) => course.language ?? "en"))).map((value) => ({ label: value, value })),
    },
  ];

  return (
    <AppLayout>
      <PageShell
        title="Impilo Fundo Catalogue"
        subtitle="Browse native Impilo Fundo learning resources, pathways and CPD-eligible courses."
        icon={<GraduationCap className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/learning"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to Impilo Fundo
          </Link>
        </div>

        {isLoading ? (
          <div className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-600">
            Loading catalogue…
          </div>
        ) : null}

        {!isLoading && isError ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-900">
            Learning catalogue is currently unavailable. Please retry shortly.
          </div>
        ) : null}

        {!isLoading && !isError ? (
          <div className="space-y-4">
            {cancelledEnrolments.length > 0 ? (
              <section className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <p className="text-sm font-semibold text-amber-950">Recover cancelled enrolments</p>
                <p className="mt-1 text-sm text-amber-900">These courses were cancelled previously. Open a course to enrol again.</p>
                <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
                  {cancelledEnrolments.map((enrolment) => {
                    const courseId = String(enrolment.courseId ?? "");
                    return (
                      <Link
                        key={String(enrolment.id ?? courseId)}
                        href={courseId ? `/learning/courses/${courseId}` : "/learning/my-learning"}
                        className="rounded-lg border border-amber-200 bg-white px-3 py-2 text-sm hover:bg-amber-50"
                      >
                        <span className="block font-medium text-gray-900">{String(enrolment.courseTitle ?? enrolment.courseCode ?? "Cancelled course")}</span>
                        <span className="mt-1 block text-xs text-gray-500">Cancelled enrolment • Re-enrol available</span>
                      </Link>
                    );
                  })}
                </div>
              </section>
            ) : null}
            <LearningDataTable
              rows={items}
              columns={columns}
              filters={filters}
              emptyText="No catalogue items match your filters yet."
              searchPlaceholder="Search title, code or description"
              getRowKey={(course) => course.id}
              getRowHref={(course) => `/learning/courses/${course.id}`}
              actionLabel="Open"
            />
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
