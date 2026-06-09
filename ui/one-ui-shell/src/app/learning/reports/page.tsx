"use client";

import { AppLayout } from "@/components/AppLayout";
import { LearningDataTable, type LearningTableColumn } from "@/components/learning/LearningDataTable";
import { PageShell } from "@/components/PageShell";
import { useFundoReportsOverview } from "@/hooks/queries/useFundoLms";

interface ReportRow {
  id: string;
  title: string;
  detail: string;
  value: string;
  href: string;
}

export default function LearningReportsHomePage() {
  const { data } = useFundoReportsOverview();
  const overview = (data?.data ?? {}) as Record<string, unknown>;
  const rows: ReportRow[] = [
    {
      id: "cohorts",
      title: "Cohort completions",
      detail: "Pathway and course cohort completion tables.",
      value: String(overview.totalEnrolments ?? 0),
      href: "/learning/reports/cohorts",
    },
    {
      id: "courses",
      title: "Course completions",
      detail: "Course-level completion and status reporting.",
      value: String(overview.completed ?? 0),
      href: "/learning/reports/courses",
    },
    {
      id: "overdue",
      title: "Overdue learning",
      detail: "Overdue enrolments by course, subject type and status.",
      value: String(overview.overdue ?? 0),
      href: "/learning/reports/overdue",
    },
    {
      id: "assessments",
      title: "Assessment performance",
      detail: "Assessment performance and pending manual reviews.",
      value: String(overview.assessmentAttempts ?? 0),
      href: "/learning/reports/assessments",
    },
  ];
  const columns: Array<LearningTableColumn<ReportRow>> = [
    {
      key: "title",
      label: "Report",
      render: (row) => <span className="font-medium text-gray-900">{row.title}</span>,
      searchText: (row) => `${row.title} ${row.detail}`,
    },
    { key: "detail", label: "Detail", render: (row) => row.detail },
    { key: "value", label: "Count", render: (row) => row.value },
  ];

  return (
    <AppLayout>
      <PageShell title="Learning reports" subtitle="Trainer and supervisor reporting workspace.">
        <LearningDataTable
          rows={rows}
          columns={columns}
          emptyText="No report views are available."
          getRowKey={(row) => row.id}
          getRowHref={(row) => row.href}
          actionLabel="Open report"
        />
      </PageShell>
    </AppLayout>
  );
}
