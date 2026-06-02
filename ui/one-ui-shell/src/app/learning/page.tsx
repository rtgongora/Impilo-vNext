"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  ArrowRight,
  Award,
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

function dateLabel(value: unknown) {
  const raw = text(value, "");
  if (!raw) return null;
  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? raw : date.toLocaleDateString();
}

function focusHref(key: FocusKey) {
  return `/learning?focus=${key}`;
}

function shellLinkClass(primary = false) {
  return primary
    ? "inline-flex items-center gap-2 rounded-lg bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
    : "inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50";
}

function LearningList({ items, emptyText }: { items: FundoRow[]; emptyText: string }) {
  if (items.length === 0) {
    return <p className="rounded-lg border border-dashed border-gray-200 p-4 text-sm text-gray-500">{emptyText}</p>;
  }

  return (
    <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
      {items.map((item, index) => {
        const id = text(item.id, "");
        const status = text(item.status, "ACTIVE").replace(/_/g, " ");
        const dueAt = dateLabel(item.dueAt);
        return (
          <li key={id || `${courseTitle(item)}-${index}`} className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <p className="font-medium text-gray-900">{courseTitle(item)}</p>
              <div className="mt-1 flex flex-wrap gap-2 text-xs text-gray-500">
                <span>{status}</span>
                {dueAt ? <span>Due {dueAt}</span> : null}
                {item.cpdEligible ? <span>CPD {String(item.cpdPoints ?? "")}</span> : null}
              </div>
            </div>
            {id ? (
              <Link href={`/learning/enrolments/${id}`} className="inline-flex items-center gap-1 text-sm font-medium text-impilo-700 hover:underline">
                Open <ArrowRight className="h-4 w-4" />
              </Link>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

function CatalogueGrid({ items }: { items: Array<FundoRow | FundoCourseSummary> }) {
  if (items.length === 0) {
    return <p className="rounded-lg border border-dashed border-gray-200 p-4 text-sm text-gray-500">No published catalogue items are available yet.</p>;
  }

  return (
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      {items.map((course) => {
        const id = String(course.id ?? "");
        return (
          <Link
            key={id || String(course.title)}
            href={id ? `/learning/courses/${id}` : "/learning/catalog"}
            className="rounded-lg border border-gray-200 bg-white p-4 transition hover:border-impilo-200 hover:bg-impilo-50/30"
          >
            <Library className="h-5 w-5 text-impilo-600" />
            <p className="mt-3 font-medium text-gray-900">{text(course.title, "Course")}</p>
            <p className="mt-1 font-mono text-xs text-gray-500">{text(course.code, "FUNDO")}</p>
            <div className="mt-3 flex flex-wrap gap-2 text-xs text-gray-500">
              {course.category ? <span>{String(course.category)}</span> : null}
              {minutesLabel(course.estimatedDurationMinutes) ? <span>{minutesLabel(course.estimatedDurationMinutes)}</span> : null}
              {course.cpdEligible ? <span>CPD</span> : null}
            </div>
          </Link>
        );
      })}
    </div>
  );
}

function PathwayList({ items }: { items: FundoRow[] }) {
  if (items.length === 0) {
    return <p className="rounded-lg border border-dashed border-gray-200 p-4 text-sm text-gray-500">No pathways are assigned or published yet.</p>;
  }

  return (
    <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
      {items.map((pathway, index) => {
        const id = text(pathway.id, "");
        return (
          <li key={id || index} className="px-4 py-3">
            <div className="flex items-start gap-3">
              <Layers className="mt-0.5 h-4 w-4 text-impilo-600" />
              <div className="min-w-0 flex-1">
                <p className="font-medium text-gray-900">{text(pathway.title ?? pathway.code, "Pathway")}</p>
                <p className="mt-1 text-xs text-gray-500">{text(pathway.status, "PUBLISHED")}</p>
              </div>
              {id ? (
                <Link href={`/learning/pathways/${id}`} className="text-sm font-medium text-impilo-700 hover:underline">
                  Open
                </Link>
              ) : null}
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function CollapsedGroup({ area, children, open = false }: { area: WorkArea; children: React.ReactNode; open?: boolean }) {
  const Icon = area.Icon;
  return (
    <details open={open} className="group rounded-lg border border-gray-200 bg-white">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-5 py-4">
        <span className="flex min-w-0 items-center gap-3">
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-impilo-50 text-impilo-700">
            <Icon className="h-4 w-4" />
          </span>
          <span className="min-w-0">
            <span className="block text-sm font-semibold text-gray-900">{area.title}</span>
            <span className="mt-1 block truncate text-xs text-gray-500">{area.summary}</span>
          </span>
        </span>
        <span className="flex items-center gap-3">
          <span className="rounded-full border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-700">
            {area.loading ? "..." : area.count}
          </span>
          <ChevronDown className="h-4 w-4 text-gray-500 transition group-open:rotate-180" />
        </span>
      </summary>
      <div className="border-t border-gray-100 p-5">
        <div className="mb-4 flex justify-end">
          <Link href={focusHref(area.key)} className="text-sm font-medium text-impilo-700 hover:underline">
            Open focused view
          </Link>
        </div>
        {children}
      </div>
    </details>
  );
}

export default function LearningHubPage() {
  const params = useSearchParams();
  const focus = params.get("focus") as FocusKey | null;
  const subject = useLearningSubject();
  const { data, isLoading, isError } = useFundoMyLearning(subject);
  const { data: catalogData, isLoading: catalogLoading } = useFundoCatalog({ status: "PUBLISHED", limit: 9 });
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
  const catalogue = catalogData?.data?.items ?? [];
  const pathways = listOf((pathwaysData?.data as FundoRow | undefined)?.items);
  const nextItems = [...overdue, ...inProgress, ...enrolled].slice(0, 8);
  const recommendedItems = (recommended.length > 0 ? recommended : catalogue).slice(0, 9);
  const pathwayItems = (assignedPathways.length > 0 ? assignedPathways : pathways).slice(0, 8);

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

  const activeArea = areas.find((area) => area.key === focus);

  const focusBody = (() => {
    switch (activeArea?.key) {
      case "learning":
        return <LearningList items={nextItems} emptyText="No active enrolments yet." />;
      case "catalogue":
        return <CatalogueGrid items={recommendedItems} />;
      case "pathways":
        return <PathwayList items={pathwayItems} />;
      case "evidence":
        return (
          <div className="grid gap-3 md:grid-cols-3">
            {[
              { label: "Transcript", value: completed.length, href: "/learning/record", Icon: FileText },
              { label: "Certificates", value: certificates.length, href: "/learning/certificates", Icon: Award },
              { label: "CPD evidence", value: cpdEligibleCompletions.length, href: "/learning/cpd", Icon: BadgeCheck },
            ].map((item) => {
              const Icon = item.Icon;
              return (
                <Link key={item.href} href={item.href} className="rounded-lg border border-gray-200 bg-white p-4 hover:border-impilo-200 hover:bg-impilo-50/30">
                  <Icon className="h-5 w-5 text-impilo-600" />
                  <p className="mt-3 text-sm font-semibold text-gray-900">{item.label}</p>
                  <p className="mt-2 text-2xl font-semibold text-gray-900">{isLoading ? "..." : item.value}</p>
                </Link>
              );
            })}
          </div>
        );
      case "reports":
        return (
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {[
                ["Published courses", reportOverview.publishedCourses],
                ["Total enrolments", reportOverview.totalEnrolments],
                ["In progress", reportOverview.inProgress],
                ["Completed", reportOverview.completed],
              ].map(([label, value]) => (
                <div key={String(label)} className="rounded-lg border border-gray-200 bg-white p-4">
                  <p className="text-xs text-gray-500">{String(label)}</p>
                  <p className="mt-2 text-xl font-semibold text-gray-900">{String(value ?? 0)}</p>
                </div>
              ))}
            </div>
            <div className="flex flex-wrap gap-2">
              {[
                ["/learning/reports/cohorts", "Cohort completions"],
                ["/learning/reports/courses", "Course completions"],
                ["/learning/reports/overdue", "Overdue learning"],
                ["/learning/reports/assessments", "Assessment performance"],
              ].map(([href, label]) => (
                <Link key={href} href={href} className={shellLinkClass()}>
                  {label}
                </Link>
              ))}
            </div>
          </div>
        );
      case "authoring":
        return (
          <div className="grid gap-3 md:grid-cols-3">
            {[
              ["/learning/admin/courses", "Courses", "Create, edit and publish course structures."],
              ["/learning/admin/pathways", "Pathways", "Group courses into role-aware learning routes."],
              ["/learning/admin/assessments", "Assessments", "Manage quizzes, attempts and review queues."],
            ].map(([href, label, copy]) => (
              <Link key={href} href={href} className="rounded-lg border border-gray-200 bg-white p-4 hover:border-impilo-200 hover:bg-impilo-50/30">
                <p className="text-sm font-semibold text-gray-900">{label}</p>
                <p className="mt-2 text-sm leading-6 text-gray-600">{copy}</p>
              </Link>
            ))}
          </div>
        );
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
                  : "Open one learning area at a time. Shell tools are collapsed above; the footer expands on hover or focus."}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
                {activeArea ? (
                  <>
                    <Link href={activeArea.href} className={shellLinkClass(true)}>
                      Open full page <ArrowRight className="h-4 w-4" />
                    </Link>
                    <Link href="/learning" className={shellLinkClass()}>
                      <X className="h-4 w-4" /> Back to Impilo Fundo
                    </Link>
                  </>
                ) : (
                  <>
                    <Link href={focusHref("learning")} className={shellLinkClass(true)}>
                      Continue learning <ArrowRight className="h-4 w-4" />
                    </Link>
                    <Link href={focusHref("catalogue")} className={shellLinkClass()}>
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

          {activeArea ? (
            <section className="rounded-lg border border-gray-200 bg-gray-50 p-4">
              <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-sm font-semibold text-gray-900">{activeArea.title}</p>
                  <p className="mt-1 text-xs text-gray-500">Focused view from the Fundo v1.1 service surface.</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {areas
                    .map((area) => (
                      <Link
                        key={area.key}
                        href={focusHref(area.key)}
                        aria-current={area.key === activeArea.key ? "page" : undefined}
                        className={
                          area.key === activeArea.key
                            ? "rounded-lg border border-impilo-600 bg-impilo-600 px-3 py-2 text-sm font-medium text-white"
                            : "rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
                        }
                      >
                        {area.title}
                      </Link>
                    ))}
                </div>
              </div>
              {activeArea.loading ? <p className="mb-3 text-sm text-gray-500">Loading {activeArea.title.toLowerCase()}...</p> : null}
              {focusBody}
            </section>
          ) : (
            <div className="grid gap-4 xl:grid-cols-2">
              <CollapsedGroup area={areas[0]} open>
                {isLoading ? <p className="text-sm text-gray-500">Loading learner journey...</p> : <LearningList items={nextItems.slice(0, 4)} emptyText="No active enrolments yet." />}
              </CollapsedGroup>
              <CollapsedGroup area={areas[1]}>
                {catalogLoading ? <p className="text-sm text-gray-500">Loading catalogue...</p> : <CatalogueGrid items={recommendedItems.slice(0, 4)} />}
              </CollapsedGroup>
              <CollapsedGroup area={areas[2]}>
                {pathwaysLoading ? <p className="text-sm text-gray-500">Loading pathways...</p> : <PathwayList items={pathwayItems.slice(0, 4)} />}
              </CollapsedGroup>
              <CollapsedGroup area={areas[3]}>
                <div className="flex flex-wrap gap-2">
                  {[
                    ["/learning/record", "Transcript"],
                    ["/learning/certificates", "Certificates"],
                    ["/learning/cpd", "CPD evidence"],
                  ].map(([href, label]) => (
                    <Link key={href} href={href} className={shellLinkClass()}>
                      {label}
                    </Link>
                  ))}
                </div>
              </CollapsedGroup>
              <CollapsedGroup area={areas[4]}>
                <div className="flex flex-wrap gap-2">
                  {[
                    ["/learning/reports/cohorts", "Cohorts"],
                    ["/learning/reports/courses", "Courses"],
                    ["/learning/reports/overdue", "Overdue"],
                    ["/learning/reports/assessments", "Assessments"],
                  ].map(([href, label]) => (
                    <Link key={href} href={href} className={shellLinkClass()}>
                      {label}
                    </Link>
                  ))}
                </div>
              </CollapsedGroup>
              <CollapsedGroup area={areas[5]}>
                <div className="flex flex-wrap gap-2">
                  {[
                    ["/learning/admin/courses", "Courses"],
                    ["/learning/admin/pathways", "Pathways"],
                    ["/learning/admin/assessments", "Assessments"],
                  ].map(([href, label]) => (
                    <Link key={href} href={href} className={shellLinkClass()}>
                      {label}
                    </Link>
                  ))}
                </div>
              </CollapsedGroup>
            </div>
          )}

        </div>
    </AppLayout>
  );
}
