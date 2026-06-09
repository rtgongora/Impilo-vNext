"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  ArrowRight,
  BadgeCheck,
  BookOpen,
  ChevronDown,
  Clock,
  FileText,
  GraduationCap,
  Layers,
  Library,
  ShieldCheck,
  X,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { LearningDataTable, type LearningTableColumn, type LearningTableFilter } from "@/components/learning/LearningDataTable";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoCatalog, type FundoCourseSummary } from "@/hooks/queries/useFundoCatalog";
import { useFundoMyLearning, useFundoPathways, useFundoReportsOverview } from "@/hooks/queries/useFundoLms";

type FundoRow = Record<string, unknown>;
type LucideIcon = typeof FileText;
type FocusKey = "learning" | "catalogue" | "pathways" | "evidence" | "reports" | "authoring";

interface WorkArea {
  key: FocusKey;
  title: string;
  summary: string;
  href: string;
  Icon: LucideIcon;
  count: number;
  loading?: boolean;
}

interface ActionRow {
  id: string;
  title: string;
  detail: string;
  value: string | number;
  href: string;
  Icon: LucideIcon;
}

function listOf(value: unknown): FundoRow[] {
  return Array.isArray(value) ? (value as FundoRow[]) : [];
}

function text(value: unknown, fallback: string) {
  return typeof value === "string" && value.trim().length > 0 ? value : fallback;
}

function minutesLabel(value: unknown) {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) && n > 0 ? `${n} min` : null;
}

function courseTitle(row: FundoRow) {
  return text(row.courseTitle ?? row.title ?? row.courseCode ?? row.code, "Learning item");
}

function rowText(row: FundoRow, key: string, fallback = "-") {
  return text(row[key], fallback);
}

function dateLabel(value: unknown) {
  const raw = text(value, "");
  if (!raw) return null;
  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? raw : date.toLocaleDateString();
}

function cardHref(key: FocusKey) {
  return `/learning?area=${key}`;
}

function shellLinkClass(primary = false) {
  return primary
    ? "inline-flex items-center gap-2 rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
    : "inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50";
}

function LearningList({ items, emptyText, searchable = true }: { items: FundoRow[]; emptyText: string; searchable?: boolean }) {
  const columns: Array<LearningTableColumn<FundoRow>> = [
    {
      key: "title",
      label: "Learning item",
      render: (row) => <span className="font-medium text-gray-900">{courseTitle(row)}</span>,
      searchText: courseTitle,
    },
    { key: "status", label: "Status", render: (row) => rowText(row, "status", "ACTIVE").replace(/_/g, " ") },
    { key: "dueAt", label: "Due", render: (row) => dateLabel(row.dueAt) ?? "-" },
    {
      key: "cpd",
      label: "CPD",
      render: (row) => (row.cpdEligible ? `Yes ${String(row.cpdPoints ?? "")}` : "-"),
      searchText: (row) => (row.cpdEligible ? "cpd eligible" : "not cpd"),
    },
  ];

  return (
    <LearningDataTable
      rows={items}
      columns={columns}
      emptyText={emptyText}
      searchPlaceholder={searchable ? "Search learning items" : undefined}
      getRowKey={(row, index) => text(row.id, "") || `${courseTitle(row)}-${index}`}
      getRowHref={(row) => {
        const id = text(row.id, "");
        return id ? `/learning/enrolments/${id}` : undefined;
      }}
      dense
    />
  );
}

function CatalogueGrid({ items, searchable = true }: { items: Array<FundoRow | FundoCourseSummary>; searchable?: boolean }) {
  const rows = items.map((item) => item as FundoRow);
  const columns: Array<LearningTableColumn<FundoRow>> = [
    {
      key: "title",
      label: "Course",
      render: (row) => <span className="font-medium text-gray-900">{rowText(row, "title", "Course")}</span>,
      searchText: (row) => `${rowText(row, "title", "")} ${rowText(row, "description", "")}`,
    },
    { key: "code", label: "Code", render: (row) => <span className="font-mono text-xs">{rowText(row, "code", "FUNDO")}</span> },
    { key: "category", label: "Category", render: (row) => rowText(row, "category") },
    { key: "level", label: "Level", render: (row) => rowText(row, "level") },
    { key: "status", label: "Status", render: (row) => rowText(row, "status", "PUBLISHED") },
    { key: "language", label: "Language", render: (row) => rowText(row, "language", "en") },
    { key: "duration", label: "Duration", render: (row) => minutesLabel(row.estimatedDurationMinutes) ?? "-" },
    { key: "cpd", label: "CPD", render: (row) => (row.cpdEligible ? `Yes ${String(row.cpdPoints ?? "")}` : "-") },
  ];
  const filters: Array<LearningTableFilter<FundoRow>> = [
    {
      key: "status",
      label: "Status",
      valueFor: (row) => rowText(row, "status", "PUBLISHED"),
      options: ["DRAFT", "PUBLISHED", "ARCHIVED"].map((value) => ({ label: value, value })),
    },
    {
      key: "category",
      label: "Category",
      valueFor: (row) => rowText(row, "category", ""),
      options: Array.from(new Set(rows.map((row) => rowText(row, "category", "")).filter(Boolean))).map((value) => ({ label: value, value })),
    },
    {
      key: "level",
      label: "Level",
      valueFor: (row) => rowText(row, "level", ""),
      options: Array.from(new Set(rows.map((row) => rowText(row, "level", "")).filter(Boolean))).map((value) => ({ label: value, value })),
    },
  ];

  return (
    <LearningDataTable
      rows={rows}
      columns={columns}
      filters={searchable ? filters : []}
      emptyText="No catalogue items match this view."
      searchPlaceholder={searchable ? "Search courses, codes or descriptions" : undefined}
      getRowKey={(row, index) => rowText(row, "id", "") || `${rowText(row, "title", "course")}-${index}`}
      getRowHref={(row) => {
        const id = rowText(row, "id", "");
        return id ? `/learning/courses/${id}` : undefined;
      }}
      actionLabel="Open"
      dense
    />
  );
}

function PathwayList({ items, searchable = true }: { items: FundoRow[]; searchable?: boolean }) {
  const columns: Array<LearningTableColumn<FundoRow>> = [
    {
      key: "title",
      label: "Pathway",
      render: (row) => <span className="font-medium text-gray-900">{text(row.title ?? row.code, "Pathway")}</span>,
      searchText: (row) => `${text(row.title, "")} ${text(row.code, "")} ${text(row.description, "")}`,
    },
    { key: "code", label: "Code", render: (row) => rowText(row, "code") },
    { key: "status", label: "Status", render: (row) => rowText(row, "status", "PUBLISHED") },
    { key: "targetRoles", label: "Roles", render: (row) => rowText(row, "targetRoles") },
  ];

  return (
    <LearningDataTable
      rows={items}
      columns={columns}
      filters={
        searchable
          ? [
              {
                key: "status",
                label: "Status",
                valueFor: (row) => rowText(row, "status", "PUBLISHED"),
                options: ["DRAFT", "PUBLISHED", "ARCHIVED"].map((value) => ({ label: value, value })),
              },
            ]
          : []
      }
      emptyText="No pathways are assigned or published yet."
      searchPlaceholder={searchable ? "Search pathways" : undefined}
      getRowKey={(row, index) => rowText(row, "id", "") || `pathway-${index}`}
      getRowHref={(row) => {
        const id = rowText(row, "id", "");
        return id ? `/learning/pathways/${id}` : undefined;
      }}
      dense
    />
  );
}

function ActionButtonGrid({ rows, emptyText }: { rows: ActionRow[]; emptyText: string }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {rows.map((row) => {
        const Icon = row.Icon;
        return (
          <Link
            key={row.id}
            href={row.href}
            className="group rounded-lg border border-gray-200 bg-white p-4 transition hover:border-impilo-200 hover:bg-impilo-50/30 hover:shadow-sm"
          >
            <div className="flex items-start justify-between gap-3">
              <span className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-impilo-50 text-impilo-700">
                <Icon className="h-4 w-4" />
              </span>
              <span className="rounded-full border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-700">
                {row.value}
              </span>
            </div>
            <p className="mt-3 text-sm font-semibold text-gray-900">{row.title}</p>
            <p className="mt-2 min-h-10 text-sm leading-5 text-gray-600">{row.detail}</p>
            <span className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-impilo-700 group-hover:underline">
              Open <ArrowRight className="h-4 w-4" />
            </span>
          </Link>
        );
      })}
      {rows.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-200 bg-white p-5 text-sm text-gray-500">
          {emptyText}
        </div>
      ) : null}
    </div>
  );
}

function LearningAreaCard({ area }: { area: WorkArea }) {
  const Icon = area.Icon;
  return (
    <Link
      href={cardHref(area.key)}
      aria-label={`Open ${area.title}`}
      className="group flex min-h-[112px] flex-col justify-between rounded-lg border border-gray-200 bg-white px-5 py-4 transition hover:border-impilo-200 hover:bg-impilo-50/30 hover:shadow-sm"
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-3">
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-impilo-50 text-impilo-700">
            <Icon className="h-4 w-4" />
          </span>
          <span className="min-w-0">
            <span className="block text-sm font-semibold text-gray-900">{area.title}</span>
            <span className="mt-1 block truncate text-xs text-gray-500">{area.summary}</span>
          </span>
        </div>
        <span className="flex items-center gap-3">
          <span className="rounded-full border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-700">
            {area.loading ? "..." : area.count}
          </span>
          <ChevronDown className="h-4 w-4 text-gray-500 transition group-hover:translate-y-0.5" />
        </span>
      </div>
      <p className="mt-4 text-xs font-medium text-impilo-700">Expand card</p>
    </Link>
  );
}

function ExpandedCardDialog({
  activeArea,
  areas,
  loading,
  children,
}: {
  activeArea: WorkArea;
  areas: WorkArea[];
  loading?: boolean;
  children: React.ReactNode;
}) {
  const Icon = activeArea.Icon;
  return (
    <div className="fixed inset-0 z-50 bg-gray-950/45 px-2 py-2 backdrop-blur-sm sm:px-3 sm:py-3">
      <section className="mx-auto flex h-full w-full max-w-[min(98vw,1680px)] flex-col overflow-hidden rounded-lg border border-gray-200 bg-white shadow-2xl">
        <div className="border-b border-gray-200 bg-white">
          <div className="flex flex-col gap-3 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex min-w-0 items-start gap-3">
              <span className="inline-flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-impilo-50 text-impilo-700">
                <Icon className="h-5 w-5" />
              </span>
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-normal text-impilo-700">Expanded card</p>
                <h2 className="text-lg font-bold text-gray-900">{activeArea.title}</h2>
                <p className="mt-1 text-sm text-gray-600">{activeArea.summary}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Link href={activeArea.href} className={shellLinkClass(true)}>
                Open focused view <ArrowRight className="h-4 w-4" />
              </Link>
              <Link href="/learning" className={shellLinkClass()}>
                <X className="h-4 w-4" /> Close
              </Link>
            </div>
          </div>
          <div className="flex gap-2 overflow-x-auto border-t border-gray-100 bg-gray-50 px-4 py-2">
            {areas.map((area) => {
              const AreaIcon = area.Icon;
              const active = area.key === activeArea.key;
              return (
                <Link
                  key={area.key}
                  href={cardHref(area.key)}
                  aria-current={active ? "page" : undefined}
                  className={
                    active
                      ? "inline-flex -translate-y-1 items-center gap-2 rounded-lg border border-impilo-600 bg-impilo-600 px-3 py-2 text-sm font-medium text-white shadow-sm transition"
                      : "inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 transition hover:-translate-y-0.5 hover:bg-gray-50"
                  }
                >
                  <AreaIcon className="h-4 w-4" />
                  <span>{area.title}</span>
                  <span className={active ? "text-impilo-100" : "text-gray-400"}>{area.loading ? "..." : area.count}</span>
                </Link>
              );
            })}
          </div>
        </div>
        <div className="min-h-0 flex-1 overflow-auto bg-gray-50 p-3 sm:p-4">
          {loading ? <p className="mb-3 text-sm text-gray-500">Loading {activeArea.title.toLowerCase()}...</p> : null}
          {children}
        </div>
      </section>
    </div>
  );
}

export default function LearningHubPage() {
  const params = useSearchParams();
  const selectedArea = (params.get("area") ?? params.get("focus")) as FocusKey | null;
  const subject = useLearningSubject();
  const { data, isLoading, isError } = useFundoMyLearning(subject);
  const { data: catalogData, isLoading: catalogLoading } = useFundoCatalog({
    status: "PUBLISHED",
    limit: selectedArea === "catalogue" ? 100 : 9,
  });
  const { data: pathwaysData, isLoading: pathwaysLoading } = useFundoPathways();
  const { data: reportsData, isLoading: reportsLoading } = useFundoReportsOverview();

  const payload = (data?.data ?? {}) as FundoRow;
  const reportOverview = (reportsData?.data ?? {}) as FundoRow;
  const enrolled = listOf(payload.enrolled);
  const inProgress = listOf(payload.inProgress);
  const completed = listOf(payload.completed);
  const overdue = listOf(payload.overdue);
  const recommended = listOf(payload.recommended);
  const assignedPathways = listOf(payload.assignedPathways);
  const certificates = listOf(payload.certificates);
  const cpdEligibleCompletions = listOf(payload.cpdEligibleCompletions);
  const catalogueItems = catalogData?.data?.items ?? [];
  const pathways = listOf((pathwaysData?.data as FundoRow | undefined)?.items);
  const nextItems = [...overdue, ...inProgress, ...enrolled].slice(0, 8);
  const recommendedItems = recommended.length > 0 ? recommended : catalogueItems;
  const pathwayItems = assignedPathways.length > 0 ? assignedPathways : pathways;
  const evidenceRows: ActionRow[] = [
    {
      id: "transcript",
      title: "Transcript",
      detail: "Completions, progress and learning record evidence.",
      value: isLoading ? "..." : completed.length,
      href: "/learning/record",
      Icon: FileText,
    },
    {
      id: "certificates",
      title: "Certificates",
      detail: "Issued certificates and certificate detail views.",
      value: isLoading ? "..." : certificates.length,
      href: "/learning/certificates",
      Icon: BadgeCheck,
    },
    {
      id: "cpd",
      title: "CPD evidence",
      detail: "CPD-eligible completions for council workflows.",
      value: isLoading ? "..." : cpdEligibleCompletions.length,
      href: "/learning/cpd",
      Icon: BadgeCheck,
    },
  ];
  const reportRows: ActionRow[] = [
    {
      id: "cohorts",
      title: "Cohort completions",
      detail: "Pathway and course cohort completion tables.",
      value: String(reportOverview.totalEnrolments ?? 0),
      href: "/learning/reports/cohorts",
      Icon: Library,
    },
    {
      id: "courses",
      title: "Course completions",
      detail: "Course-level completion and status reporting.",
      value: String(reportOverview.completed ?? 0),
      href: "/learning/reports/courses",
      Icon: BookOpen,
    },
    {
      id: "overdue",
      title: "Overdue learning",
      detail: "Overdue enrolments by course, subject type and status.",
      value: String(reportOverview.overdue ?? 0),
      href: "/learning/reports/overdue",
      Icon: Clock,
    },
    {
      id: "assessments",
      title: "Assessment performance",
      detail: "Assessment performance and pending manual reviews.",
      value: String(reportOverview.assessmentAttempts ?? 0),
      href: "/learning/reports/assessments",
      Icon: FileText,
    },
  ];
  const authoringRows: ActionRow[] = [
    {
      id: "courses",
      title: "Courses",
      detail: "Create, edit and publish course structures.",
      value: "Setup",
      href: "/learning/admin/courses",
      Icon: BookOpen,
    },
    {
      id: "pathways",
      title: "Pathways",
      detail: "Group courses into role-aware learning routes.",
      value: "Setup",
      href: "/learning/admin/pathways",
      Icon: Layers,
    },
    {
      id: "assessments",
      title: "Assessments",
      detail: "Manage quizzes, attempts and review queues.",
      value: "Setup",
      href: "/learning/admin/assessments",
      Icon: FileText,
    },
  ];

  const areas: WorkArea[] = [
    {
      key: "learning",
      title: "My learning",
      summary: "Assigned, overdue and active enrolments.",
      href: "/learning/my-learning",
      Icon: Clock,
      count: nextItems.length,
      loading: isLoading,
    },
    {
      key: "catalogue",
      title: "Catalogue",
      summary: "Published courses and recommendations.",
      href: "/learning/catalog",
      Icon: BookOpen,
      count: recommendedItems.length,
      loading: catalogLoading,
    },
    {
      key: "pathways",
      title: "Pathways",
      summary: "Assigned pathways and published learning routes.",
      href: "/learning/pathways",
      Icon: Layers,
      count: pathwayItems.length,
      loading: pathwaysLoading,
    },
    {
      key: "evidence",
      title: "Evidence",
      summary: "Transcript, certificates and CPD evidence.",
      href: "/learning/record",
      Icon: BadgeCheck,
      count: completed.length + certificates.length + cpdEligibleCompletions.length,
      loading: isLoading,
    },
    {
      key: "reports",
      title: "Reports",
      summary: "Supervisor reporting and assurance views.",
      href: "/learning/reports",
      Icon: Library,
      count: Number(reportOverview.totalEnrolments ?? 0),
      loading: reportsLoading,
    },
    {
      key: "authoring",
      title: "Authoring",
      summary: "Course, pathway and assessment setup.",
      href: "/learning/admin",
      Icon: GraduationCap,
      count: 3,
    },
  ];

  const activeArea = areas.find((area) => area.key === selectedArea);

  const focusBody = (() => {
    switch (activeArea?.key) {
      case "learning":
        return <LearningList items={nextItems} emptyText="No active enrolments yet." />;
      case "catalogue":
        return <CatalogueGrid items={recommendedItems} />;
      case "pathways":
        return <PathwayList items={pathwayItems} />;
      case "evidence":
        return <ActionButtonGrid rows={evidenceRows} emptyText="No evidence views are available." />;
      case "reports":
        return <ActionButtonGrid rows={reportRows} emptyText="No report views are available." />;
      case "authoring":
        return <ActionButtonGrid rows={authoringRows} emptyText="No authoring views are available." />;
      default:
        return null;
    }
  })();

  return (
    <AppLayout chrome="learning-workbench">
      <div className="space-y-3">
        <section className="rounded-lg border border-gray-200 bg-white px-4 py-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <GraduationCap className="h-5 w-5 text-impilo-600" />
                <h1 className="text-xl font-bold text-gray-900">{activeArea ? activeArea.title : "Impilo Fundo"}</h1>
                <span className="inline-flex items-center gap-1 rounded-full bg-impilo-50 px-2 py-0.5 text-xs font-medium text-impilo-700">
                  <ShieldCheck className="h-3 w-3" />
                  {subject.subjectType}
                </span>
              </div>
              <p className="mt-1 max-w-4xl text-sm leading-5 text-gray-600">
                {activeArea
                  ? activeArea.summary
                  : "Expand a learning card to work in a wide table without leaving this page."}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
                {activeArea ? (
                  <>
                    <Link href={activeArea.href} className={shellLinkClass(true)}>
                      Open focused view <ArrowRight className="h-4 w-4" />
                    </Link>
                    <Link href="/learning" className={shellLinkClass()}>
                      <X className="h-4 w-4" /> Back to Impilo Fundo
                    </Link>
                  </>
                ) : (
                  <>
                    <Link href={cardHref("learning")} className={shellLinkClass(true)}>
                      Continue learning <ArrowRight className="h-4 w-4" />
                    </Link>
                    <Link href={cardHref("catalogue")} className={shellLinkClass()}>
                      Browse catalogue
                    </Link>
                  </>
                )}
            </div>
          </div>
        </section>

          {isError ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              Fundo learner data is unavailable right now. Catalogue, pathway and report links remain available.
            </div>
          ) : null}

          <div className="grid gap-4 md:grid-cols-2">
            {areas.map((area) => (
              <LearningAreaCard key={area.key} area={area} />
            ))}
          </div>

          {activeArea ? (
            <ExpandedCardDialog activeArea={activeArea} areas={areas} loading={activeArea.loading}>
              {focusBody}
            </ExpandedCardDialog>
          ) : null}

        </div>
    </AppLayout>
  );
}
