"use client";

import Link from "next/link";
import { BookOpen, Clock, FileText, Layers, TrendingUp } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { LearningDataTable, type LearningTableColumn } from "@/components/learning/LearningDataTable";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoLearningRecord, useFundoMyLearning } from "@/hooks/queries/useFundoLms";

function listOf(value: unknown): Array<Record<string, unknown>> {
  return Array.isArray(value) ? (value as Array<Record<string, unknown>>) : [];
}

function itemTitle(item: Record<string, unknown>) {
  return String(item.courseTitle ?? item.title ?? item.courseCode ?? item.code ?? "Course");
}

function itemHref(title: string, item: Record<string, unknown>) {
  const id = String(item.id ?? "");
  const courseId = String(item.courseId ?? "");
  if (title.includes("Cancelled")) return courseId ? `/learning/courses/${courseId}` : undefined;
  if (!id) return undefined;
  if (title.includes("Recommended")) return `/learning/courses/${id}`;
  if (title.includes("pathway")) return `/learning/pathways/${id}`;
  if (title.includes("Certificates")) return `/learning/certificates/${id}`;
  return `/learning/enrolments/${id}`;
}

function evidenceHref(item: Record<string, unknown>) {
  const id = String(item.id ?? "");
  const certificateId = String(item.certificateId ?? "");
  if (certificateId) return `/learning/certificates/${certificateId}`;
  if (id && (item.issuedAt || item.certificateNumber || item.serialNumber)) return `/learning/certificates/${id}`;
  if (item.cpdEligible || item.cpdPoints) return "/learning/cpd";
  return id ? `/learning/certificates/${id}` : "/learning/record";
}

function itemStatus(item: Record<string, unknown>, fallback = "-") {
  return String(item.status ?? item.verifiedState ?? fallback).replace(/_/g, " ");
}

function learningColumns(): Array<LearningTableColumn<Record<string, unknown>>> {
  return [
    {
      key: "title",
      label: "Course",
      render: (item) => <span className="font-medium text-gray-900">{itemTitle(item)}</span>,
      searchText: itemTitle,
    },
    { key: "status", label: "Status", render: (item) => itemStatus(item, "PUBLISHED") },
    { key: "cpd", label: "CPD", render: (item) => (item.cpdEligible ? `Yes ${String(item.cpdPoints ?? "")}` : "-") },
  ];
}

function NextUp({
  item,
  recommendedCount,
}: {
  item?: Record<string, unknown>;
  recommendedCount: number;
}) {
  const href = item ? itemHref("Priority learning", item) : "/learning/catalog";
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-normal text-gray-500">Next up</p>
          <p className="mt-1 text-lg font-semibold text-gray-900">{item ? itemTitle(item) : "Browse available learning"}</p>
          <p className="mt-1 text-sm text-gray-600">
            {item ? itemStatus(item, "ENROLLED") : `${recommendedCount} recommended course${recommendedCount === 1 ? "" : "s"} available.`}
          </p>
        </div>
        <Link href={href ?? "/learning/catalog"} className="inline-flex items-center justify-center rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700">
          {item ? "Continue" : "Open catalogue"}
        </Link>
      </div>
    </section>
  );
}

function EmptyPanel({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="rounded-lg border border-dashed border-gray-200 bg-white px-4 py-5">
      <p className="text-sm font-medium text-gray-900">{title}</p>
      <p className="mt-1 text-sm text-gray-500">{detail}</p>
    </div>
  );
}

function LearningSection({
  title,
  detail,
  items,
  action,
  getRowHref,
  emptyTitle,
  emptyDetail,
}: {
  title: string;
  detail: string;
  items: Array<Record<string, unknown>>;
  action?: React.ReactNode;
  getRowHref?: (item: Record<string, unknown>) => string | undefined;
  emptyTitle: string;
  emptyDetail: string;
}) {
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-sm font-semibold text-gray-900">{title}</p>
          <p className="mt-1 text-xs text-gray-500">{detail}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="rounded-full border border-gray-200 bg-white px-2 py-0.5 text-xs font-medium text-gray-500">
            {items.length} item{items.length === 1 ? "" : "s"}
          </span>
          {action}
        </div>
      </div>
      {items.length > 0 ? (
        <LearningDataTable
          rows={items}
          columns={learningColumns()}
          emptyText={emptyTitle}
          getRowKey={(item, index) => String(item.id ?? `${title}-${index}`)}
          getRowHref={getRowHref ?? ((item) => itemHref(title, item))}
          dense
        />
      ) : (
        <EmptyPanel title={emptyTitle} detail={emptyDetail} />
      )}
    </section>
  );
}

function JourneyStatus({
  enrolled,
  inProgress,
  overdue,
  completed,
  recommended,
  evidence,
}: {
  enrolled: number;
  inProgress: number;
  overdue: number;
  completed: number;
  recommended: number;
  evidence: number;
}) {
  const rows = [
    ["Enrolled", enrolled],
    ["In progress", inProgress],
    ["Overdue", overdue],
    ["Completed", completed],
    ["Recommended", recommended],
    ["Evidence", evidence],
  ];
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-gray-900">Journey status</p>
          <p className="mt-1 text-xs text-gray-500">Current learner pipeline.</p>
        </div>
        <Clock className="h-4 w-4 text-gray-400" />
      </div>
      <div className="divide-y divide-gray-100 text-sm">
        {rows.map(([label, value]) => (
          <div key={String(label)} className="flex items-center justify-between py-2">
            <span className="text-gray-600">{String(label)}</span>
            <span className="font-semibold text-gray-900">{String(value)}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

export default function MyLearningPage() {
  const subject = useLearningSubject();
  const { data, isLoading } = useFundoMyLearning(subject);
  const { data: recordData } = useFundoLearningRecord(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const record = ((recordData?.data as Record<string, unknown> | undefined)?.record as Record<string, unknown> | undefined) ?? {};
  const enrolled = listOf(payload.enrolled);
  const inProgress = listOf(payload.inProgress);
  const completed = listOf(payload.completed);
  const overdue = listOf(payload.overdue);
  const recordCancelled = listOf(record.enrolments).filter((item) => String(item.status) === "CANCELLED");
  const cancelled = listOf(payload.cancelled);
  const cancelledItems = cancelled.length > 0 ? cancelled : recordCancelled;
  const recommended = listOf(payload.recommended);
  const assignedPathways = listOf(payload.assignedPathways);
  const certificates = listOf(payload.certificates);
  const cpdEligibleCompletions = listOf(payload.cpdEligibleCompletions);
  const priorityLearning = [...overdue, ...inProgress, ...enrolled];
  const evidenceItems = [...certificates, ...cpdEligibleCompletions];

  return (
    <AppLayout>
      <PageShell
        title="My learning"
        subtitle="A single view of assigned work, recommendations, completions, certificates and CPD evidence."
        actions={
          <Link href="/learning/record" className="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
            <FileText className="h-4 w-4" />
            Transcript
          </Link>
        }
      >
        {isLoading ? <p className="text-sm text-gray-600">Loading…</p> : null}
        <div className="space-y-5">
          <NextUp item={priorityLearning[0]} recommendedCount={recommended.length} />

          <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
            <div className="space-y-5">
              <LearningSection
                title="Continue learning"
                detail="Active enrolments and courses that need attention."
                items={priorityLearning}
                emptyTitle="No active learning assigned"
                emptyDetail="There are no overdue, in-progress or enrolled items for this learner."
              />
              <LearningSection
                title="Recommended"
                detail="Suggested and re-opened courses from the Fundo catalogue."
                items={recommended}
                action={
                  <Link href="/learning/catalog" className="text-xs font-medium text-impilo-700 hover:underline">
                    Browse catalogue
                  </Link>
                }
                emptyTitle="No recommendations yet"
                emptyDetail="Recommendations will appear here when the catalogue has matching courses."
              />
              {cancelledItems.length > 0 ? (
                <LearningSection
                  title="Recover cancelled enrolments"
                  detail="Cancelled courses can be opened and enrolled again."
                  items={cancelledItems}
                  emptyTitle="No cancelled enrolments"
                  emptyDetail="Cancelled courses will appear here so they can be recovered."
                />
              ) : null}
            </div>

            <div className="space-y-5">
              <JourneyStatus
                enrolled={enrolled.length}
                inProgress={inProgress.length}
                overdue={overdue.length}
                completed={completed.length}
                recommended={recommended.length}
                evidence={evidenceItems.length}
              />

              {assignedPathways.length > 0 ? (
                <LearningSection
                  title="Assigned pathways"
                  detail="Structured learning routes assigned to the learner."
                  items={assignedPathways}
                  action={<Layers className="h-4 w-4 text-gray-400" />}
                  emptyTitle="No pathways assigned"
                  emptyDetail="Assigned pathways will appear here when a role or programme requires them."
                />
              ) : null}

              {evidenceItems.length > 0 ? (
                <LearningSection
                  title="Learning evidence"
                  detail="Certificates and CPD evidence ready for review."
                  items={evidenceItems}
                  action={<TrendingUp className="h-4 w-4 text-gray-400" />}
                  getRowHref={evidenceHref}
                  emptyTitle="No evidence yet"
                  emptyDetail="Certificates and CPD evidence will appear after completion."
                />
              ) : (
                <section className="rounded-lg border border-gray-200 bg-white p-4">
                  <p className="text-sm font-semibold text-gray-900">Shortcuts</p>
                  <div className="mt-3 grid gap-2 text-sm">
                    <Link href="/learning/catalog" className="inline-flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-gray-700 hover:bg-gray-50">
                      <BookOpen className="h-4 w-4 text-gray-400" />
                      Browse catalogue
                    </Link>
                    <Link href="/learning/record" className="inline-flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-gray-700 hover:bg-gray-50">
                      <FileText className="h-4 w-4 text-gray-400" />
                      Open transcript
                    </Link>
                  </div>
                </section>
              )}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
