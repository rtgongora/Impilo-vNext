"use client";

import { FormEvent, ReactNode, useMemo, useState } from "react";
import {
  Activity,
  ArrowLeft,
  BarChart3,
  Bell,
  BookOpenCheck,
  Bot,
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  GraduationCap,
  Library,
  Loader2,
  Plus,
  Radio,
  Route,
  Search,
  Sparkles,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { asArray, asRecord, asText, type ModalKey, type Row, type SectionKey } from "@/components/learning/learningUtils";

export const learningSections: Array<{ key: SectionKey; label: string; icon: LucideIcon; detail: string }> = [
  { key: "overview", label: "Overview", icon: GraduationCap, detail: "Snapshot and next action" },
  { key: "catalog", label: "Catalogue", icon: BookOpenCheck, detail: "Courses, pathways, certificates" },
  { key: "learner", label: "My Learning", icon: Route, detail: "Enrolments and progress" },
  { key: "studio", label: "Studio", icon: Sparkles, detail: "Authoring and operations" },
  { key: "reports", label: "Reports", icon: BarChart3, detail: "Cohorts, overdue, outcomes" },
];

export function LearningWorkspaceHeader({
  section,
  busy,
  historyLength,
  onSectionChange,
  onBack,
  onRefresh,
}: {
  section: SectionKey;
  busy: boolean;
  historyLength: number;
  onSectionChange: (section: SectionKey) => void;
  onBack: () => void;
  onRefresh: () => void;
}) {
  return (
    <header className="relative rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex gap-2 px-2 py-2">
        <div className="flex w-[170px] shrink-0 items-center gap-2">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-teal-50 text-teal-700 ring-1 ring-teal-100">
            <GraduationCap className="h-4 w-4" />
          </span>
          <div className="min-w-0">
            <h1 className="truncate text-base font-semibold leading-5 text-slate-950">Impilo Fundo</h1>
            <p className="truncate text-[11px] leading-4 text-slate-500">Learning operations</p>
          </div>
        </div>

        <nav className="grid w-[610px] shrink-0 grid-cols-5 gap-1" aria-label="Learning sections">
          {learningSections.map((item) => {
            const Icon = item.icon;
            const active = section === item.key;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => onSectionChange(item.key)}
                className={[
                  "inline-flex h-9 min-w-0 items-center justify-center gap-1 rounded-md border px-2 text-xs font-semibold transition",
                  active
                    ? "border-teal-300 bg-teal-50 text-teal-900 shadow-sm"
                    : "border-slate-200 bg-slate-50 text-slate-700 hover:border-slate-300 hover:bg-white hover:text-slate-950",
                ].join(" ")}
              >
                <Icon className="h-3.5 w-3.5" />
                <span className="truncate">{item.label}</span>
              </button>
            );
          })}
        </nav>

        <div className="flex min-w-0 flex-1 items-center justify-end gap-1.5">
          {historyLength ? (
            <IconButton label="Back" onClick={onBack}>
              <ArrowLeft className="h-3.5 w-3.5" />
            </IconButton>
          ) : null}
          <IconButton label="Refresh" onClick={onRefresh}>
            {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Activity className="h-3.5 w-3.5" />}
          </IconButton>
        </div>
      </div>
    </header>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex h-8 items-center gap-1.5 rounded-md border border-slate-200 px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
      title={label}
      aria-label={label}
    >
      {children}
      <span className="hidden sm:inline">{label}</span>
    </button>
  );
}

export function LearningWorkspaceMain({
  section,
  data,
  openSection,
  setModal,
}: {
  section: SectionKey;
  data: Record<string, unknown>;
  openSection: (section: SectionKey) => void;
  setModal: (modal: ModalKey) => void;
}) {
  if (section === "catalog") return <Catalog data={data} setModal={setModal} />;
  if (section === "learner") return <Learner data={data} openSection={openSection} setModal={setModal} />;
  if (section === "studio") return <Studio data={data} setModal={setModal} />;
  if (section === "reports") return <Reports data={data} />;
  return <Overview data={data} openSection={openSection} setModal={setModal} />;
}

function Overview({ data, openSection, setModal }: { data: Record<string, unknown>; openSection: (s: SectionKey) => void; setModal: (m: ModalKey) => void }) {
  const catalog = asRecord(data.catalog);
  const studio = asRecord(data.studio);
  const courseCount = countOf(catalog.totalElements, asArray(catalog.items).length);
  const pathwayCount = asArray(asRecord(data.pathways).items).length;
  const enrolmentCount = asArray(asRecord(data.enrolments).items).length;
  const certificateCount = asArray(asRecord(data.certificates).items).length;
  const libraryCount = countOf(studio.libraryResources, asArray(asRecord(data.library).items).length);
  const mediaCount = asArray(asRecord(data.media).items).length;
  const activityCount = asArray(asRecord(data.activities).items).length;
  const cohortCount = asArray(asRecord(data.cohorts).items).length;
  const sessionCount = asArray(asRecord(data.sessions).items).length;
  const overdueCount = asArray(asRecord(data.overdue).items).length;
  const draftCount = countOf(studio.draftCourses, 0);

  return (
    <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_260px] xl:grid-cols-[minmax(0,1fr)_280px]">
      <section className="min-w-0">
        <div className="grid gap-2 md:grid-cols-3">
          <OverviewCard
            icon={<BookOpenCheck />}
            title="Catalogue"
            metric={`${courseCount} courses`}
            detail={`${pathwayCount} pathways and governed certificates`}
            action="Browse catalogue"
            onClick={() => openSection("catalog")}
          />
          <OverviewCard
            icon={<Route />}
            title="My Learning"
            metric={`${enrolmentCount} active`}
            detail={`${certificateCount} certificates issued for this subject`}
            action="Open learning"
            onClick={() => openSection("learner")}
          />
          <OverviewCard
            icon={<Sparkles />}
            title="Studio"
            metric={`${draftCount} drafts`}
            detail="Author courses, activities, media, and AI-assisted content"
            action="Open studio"
            onClick={() => openSection("studio")}
          />
        </div>

        <div className="mt-2 grid gap-2 md:grid-cols-3">
          <OverviewCard
            icon={<Library />}
            title="Library and media"
            metric={`${libraryCount} resources`}
            detail={`${mediaCount} media assets and ${activityCount} interactive activities`}
            action="Manage assets"
            onClick={() => openSection("studio")}
          />
          <OverviewCard
            icon={<Users />}
            title="Cohorts and sessions"
            metric={`${cohortCount} cohorts`}
            detail={`${sessionCount} scheduled learning sessions`}
            action="Coordinate"
            onClick={() => setModal("cohort")}
          />
          <OverviewCard
            icon={<BarChart3 />}
            title="Reports"
            metric={`${overdueCount} overdue`}
            detail="Completion, cohort, assessment, and overdue reporting"
            action="View reports"
            onClick={() => openSection("reports")}
          />
        </div>

        <Panel title="Fundo actions" className="mt-3">
          <div className="grid gap-2 md:grid-cols-4">
            <ActionButton icon={<Plus />} label="Draft course" onClick={() => setModal("course")} />
            <ActionButton icon={<Users />} label="Create cohort" onClick={() => setModal("cohort")} />
            <ActionButton icon={<Bot />} label="AI draft" onClick={() => setModal("ai")} />
            <ActionButton icon={<Bell />} label="Notify learner" onClick={() => setModal("notification")} />
          </div>
        </Panel>

      </section>

      <div className="grid gap-3">
        <Panel title="Status">
          <div className="grid gap-1.5 text-xs">
            <StatusNavRow label="Courses" value={courseCount} onClick={() => openSection("catalog")} />
            <StatusNavRow label="Enrolments" value={enrolmentCount} onClick={() => openSection("learner")} />
            <StatusNavRow label="Certificates" value={certificateCount} onClick={() => openSection("catalog")} />
            <StatusNavRow label="Overdue" value={overdueCount} tone={overdueCount ? "warn" : "ok"} onClick={() => openSection("reports")} />
            <StatusNavRow label="Drafts" value={draftCount} tone={draftCount ? "warn" : "ok"} onClick={() => openSection("studio")} />
            <StatusNavRow label="Assets" value={libraryCount + mediaCount} onClick={() => openSection("studio")} />
            <StatusNavRow label="Sessions" value={sessionCount} onClick={() => setModal("session")} />
          </div>
        </Panel>
      </div>
    </div>
  );
}

function Catalog({ data, setModal }: { data: Record<string, unknown>; setModal: (m: ModalKey) => void }) {
  return (
    <div className="grid gap-3 md:grid-cols-[minmax(0,1.15fr)_minmax(280px,0.85fr)]">
      <Panel title="Courses" action={<ActionText onClick={() => setModal("course")}>New course</ActionText>}>
        <CompactRows rows={asArray(asRecord(data.catalog).items)} empty="No courses found." />
      </Panel>
      <Panel title="Pathways">
        <CompactRows rows={asArray(asRecord(data.pathways).items)} empty="No published pathways found." />
      </Panel>
      <Panel title="Certificates">
        <CompactRows rows={asArray(asRecord(data.certificates).items)} empty="No certificates issued for this subject." />
      </Panel>
      <Panel title="Learning record">
        <KeyValues row={asRecord(asRecord(data.record).record ?? data.record)} />
      </Panel>
    </div>
  );
}

function Learner({ data, openSection, setModal }: { data: Record<string, unknown>; openSection: (s: SectionKey) => void; setModal: (m: ModalKey) => void }) {
  const myLearning = asRecord(data.myLearning);
  const recommended = asArray(myLearning.recommended);
  const inProgress = asArray(myLearning.inProgress);
  const queue = [
    ...inProgress.map((row) => ({ ...row, __learningEnrolled: true })),
    ...recommended.map((row) => ({ ...row, __learningEnrolled: false })),
  ].slice(0, 8);

  return (
    <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_300px] xl:grid-cols-[minmax(0,1fr)_320px]">
      <Panel title="Course worklist" action={<span className="text-xs font-medium text-slate-500">{queue.length} rows</span>}>
        <WorklistRows
          rows={queue}
          empty="No active or recommended learning returned for this subject."
          onEnroll={() => setModal("enrolment")}
          onContinue={() => openSection("learner")}
          onDetails={() => openSection("catalog")}
        />
      </Panel>
      <Panel title="Enrolments" action={<ActionText onClick={() => setModal("enrolment")}>Add enrolment</ActionText>}>
        <CompactRows rows={asArray(asRecord(data.enrolments).items)} empty="No enrolments found for this subject." />
      </Panel>
      <Panel title="Notifications" action={<ActionText onClick={() => setModal("notification")}>Schedule</ActionText>}>
        <CompactRows rows={asArray(asRecord(data.notifications).items)} empty="No notifications scheduled." />
      </Panel>
      <Panel title="CPD and progress" className="md:col-span-2">
        <CompactRows rows={asArray(asRecord(data.myLearning).cpdEligibleCompletions)} empty="No CPD-eligible completions found." />
      </Panel>
    </div>
  );
}

function Studio({ data, setModal }: { data: Record<string, unknown>; setModal: (m: ModalKey) => void }) {
  return (
    <div className="grid gap-3 md:grid-cols-[260px_minmax(0,1fr)_minmax(0,1fr)] xl:grid-cols-[300px_minmax(0,1fr)_minmax(0,1fr)]">
      <Panel title="Authoring">
        <div className="grid gap-1.5">
          <ActionButton icon={<BookOpenCheck />} label="Course" onClick={() => setModal("course")} />
          <ActionButton icon={<Library />} label="Library" onClick={() => setModal("library")} />
          <ActionButton icon={<Radio />} label="Media" onClick={() => setModal("media")} />
          <ActionButton icon={<Activity />} label="Activity" onClick={() => setModal("activity")} />
          <ActionButton icon={<Users />} label="Cohort" onClick={() => setModal("cohort")} />
          <ActionButton icon={<CalendarClock />} label="Session" onClick={() => setModal("session")} />
        </div>
      </Panel>
      <Panel title="Library resources">
        <CompactRows rows={asArray(asRecord(data.library).items)} empty="No library resources available." />
      </Panel>
      <Panel title="Media assets">
        <CompactRows rows={asArray(asRecord(data.media).items)} empty="No media assets available." />
      </Panel>
      <Panel title="Interactive activities">
        <CompactRows rows={asArray(asRecord(data.activities).items)} empty="No activities available." />
      </Panel>
      <Panel title="Cohorts">
        <CompactRows rows={asArray(asRecord(data.cohorts).items)} empty="No cohorts available." />
      </Panel>
      <Panel title="Sessions">
        <CompactRows rows={asArray(asRecord(data.sessions).items)} empty="No scheduled sessions available." />
      </Panel>
    </div>
  );
}

function Reports({ data }: { data: Record<string, unknown> }) {
  return (
    <div className="grid gap-3 md:grid-cols-[280px_minmax(0,1fr)_minmax(0,1fr)] xl:grid-cols-[320px_minmax(0,1fr)_minmax(0,1fr)]">
      <Panel title="Overview">
        <KeyValues row={asRecord(data.reportOverview)} />
      </Panel>
      <Panel title="Cohort completions">
        <CompactRows rows={asArray(asRecord(data.cohortCompletions).items)} empty="No cohort report rows." />
      </Panel>
      <Panel title="Course completions">
        <CompactRows rows={asArray(asRecord(data.courseCompletions).items)} empty="No course completion rows." />
      </Panel>
      <Panel title="Overdue learning">
        <CompactRows rows={asArray(asRecord(data.overdue).items)} empty="No overdue learning rows." />
      </Panel>
      <Panel title="Assessment performance" className="md:col-span-2">
        <CompactRows rows={asArray(asRecord(data.assessmentPerformance).items)} empty="No assessment performance rows." />
      </Panel>
    </div>
  );
}

function OverviewCard({
  icon,
  title,
  metric,
  detail,
  action,
  priority = false,
  onClick,
}: {
  icon: ReactNode;
  title: string;
  metric: string;
  detail: string;
  action: string;
  priority?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "grid grid-rows-[auto_1fr_auto] rounded-lg border border-slate-200 bg-white p-3 text-left shadow-sm transition hover:border-teal-300 hover:bg-teal-50/40",
        priority ? "min-h-[132px]" : "min-h-[96px]",
      ].join(" ")}
    >
      <span className="mb-2 flex items-center justify-between gap-2">
        <span className="flex min-w-0 items-center gap-2">
          <span
            className={[
              "flex shrink-0 items-center justify-center rounded-md bg-teal-50 text-teal-700 ring-1 ring-teal-100",
              priority ? "h-9 w-9 [&>svg]:h-5 [&>svg]:w-5" : "h-8 w-8 [&>svg]:h-4 [&>svg]:w-4",
            ].join(" ")}
          >
            {icon}
          </span>
          <span className={priority ? "truncate text-base font-semibold text-slate-950" : "truncate text-sm font-semibold text-slate-950"}>{title}</span>
        </span>
        <span className="shrink-0 text-xs font-semibold text-teal-700">{action}</span>
      </span>
      <span className="min-w-0">
        <span className={priority ? "block truncate text-2xl font-semibold leading-7 text-slate-950" : "block truncate text-lg font-semibold leading-6 text-slate-950"}>{metric}</span>
        <span className="mt-1 block line-clamp-2 text-xs leading-5 text-slate-500">{detail}</span>
      </span>
    </button>
  );
}

function StatusNavRow({ label, value, tone = "neutral", onClick }: { label: string; value: number; tone?: "neutral" | "ok" | "warn"; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-left transition hover:bg-teal-50 hover:text-teal-900"
    >
      <span className="font-medium text-slate-600">{label}</span>
      <span className={["font-semibold", tone === "warn" ? "text-amber-700" : tone === "ok" ? "text-emerald-700" : "text-slate-950"].join(" ")}>
        {value}
      </span>
    </button>
  );
}

function Panel({ title, action, children, className = "" }: { title: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={["rounded-lg border border-slate-200 bg-white p-3 shadow-sm", className].join(" ")}>
      <div className="mb-2 flex min-h-6 items-center justify-between gap-3">
        <h2 className="truncate text-sm font-semibold text-slate-950">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function countOf(primary: unknown, fallback: number) {
  if (typeof primary === "number") return primary;
  const parsed = Number(primary);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function WorklistRows({
  rows,
  empty,
  onEnroll,
  onContinue,
  onDetails,
}: {
  rows: Row[];
  empty: string;
  onEnroll: (row: Row) => void;
  onContinue: (row: Row) => void;
  onDetails: (row: Row) => void;
}) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);
  const pageSize = 5;

  const statusOptions = useMemo(() => {
    const values = new Set<string>();
    rows.forEach((row) => values.add(asText(row.status ?? row.courseStatus, "Open")));
    return Array.from(values).sort((a, b) => a.localeCompare(b));
  }, [rows]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return rows.filter((row) => {
      const rowStatus = asText(row.status ?? row.courseStatus, "Open");
      const haystack = [
        row.title,
        row.name,
        row.courseTitle,
        row.code,
        row.id,
        row.description,
        row.category,
        row.type,
        row.level,
        rowStatus,
      ]
        .map((value) => asText(value, ""))
        .join(" ")
        .toLowerCase();
      return (status === "all" || rowStatus === status) && (!needle || haystack.includes(needle));
    });
  }, [query, rows, status]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const visibleRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  function updateQuery(value: string) {
    setQuery(value);
    setPage(1);
  }

  function updateStatus(value: string) {
    setStatus(value);
    setPage(1);
  }

  if (!rows.length) return <p className="rounded-md border border-dashed border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">{empty}</p>;

  return (
    <div className="rounded-md border border-slate-200">
      <div className="grid gap-2 border-b border-slate-200 bg-slate-50 p-2 md:grid-cols-[minmax(220px,1fr)_150px_auto] md:items-center">
        <label className="flex h-9 min-w-0 items-center gap-2 rounded-md border border-slate-200 bg-white px-2">
          <Search className="h-3.5 w-3.5 shrink-0 text-slate-400" />
          <input
            value={query}
            onChange={(event) => updateQuery(event.target.value)}
            placeholder="Search courses"
            className="min-w-0 flex-1 bg-transparent text-sm text-slate-800 outline-none placeholder:text-slate-400"
            aria-label="Search courses"
          />
        </label>
        <select
          value={status}
          onChange={(event) => updateStatus(event.target.value)}
          className="h-9 rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-700 outline-none"
          aria-label="Filter by status"
        >
          <option value="all">All statuses</option>
          {statusOptions.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <div className="flex items-center justify-end gap-1 text-xs text-slate-500">
          <span className="mr-1 whitespace-nowrap">
            {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}-{Math.min(currentPage * pageSize, filtered.length)} of {filtered.length}
          </span>
          <button
            type="button"
            onClick={() => setPage((value) => Math.max(1, value - 1))}
            disabled={currentPage === 1}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40"
            aria-label="Previous page"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
            disabled={currentPage === totalPages}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40"
            aria-label="Next page"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <div className="min-w-[1020px]">
          <div className="grid grid-cols-[minmax(300px,1fr)_130px_110px_120px_120px_150px] border-b border-slate-200 bg-white px-3 py-1.5 text-[10px] font-semibold uppercase text-slate-500">
            <span>Course</span>
            <span>Scope</span>
            <span>Status</span>
            <span>Level</span>
            <span>Progress</span>
            <span className="text-right">Operations</span>
          </div>
          {visibleRows.length ? (
            visibleRows.map((row, index) => (
              <div
                key={String(row.id ?? row.code ?? index)}
                className="grid min-h-12 grid-cols-[minmax(300px,1fr)_130px_110px_120px_120px_150px] items-center gap-3 border-b border-slate-100 px-3 py-2 last:border-b-0"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium leading-5 text-slate-950">{asText(row.title ?? row.name ?? row.courseTitle ?? row.code ?? row.id, `Item ${index + 1}`)}</p>
                  <p className="truncate text-xs leading-4 text-slate-500">{asText(row.description ?? row.subjectId, "No detail supplied")}</p>
                </div>
                <span className="truncate text-xs font-medium uppercase text-slate-600">{asText(row.category ?? row.type, "General")}</span>
                <StatusPill>{asText(row.status ?? row.courseStatus, "Open")}</StatusPill>
                <span className="truncate text-xs font-medium uppercase text-slate-700">{asText(row.level, "-")}</span>
                <ProgressCell row={row} />
                <div className="flex justify-end gap-1">
                  {isLearningRowEnrolled(row) ? (
                    <RowAction onClick={() => onContinue(row)}>Continue</RowAction>
                  ) : (
                    <RowAction onClick={() => onEnroll(row)}>Enroll</RowAction>
                  )}
                  <RowAction onClick={() => onDetails(row)} muted>Details</RowAction>
                </div>
              </div>
            ))
          ) : (
            <p className="px-3 py-5 text-sm text-slate-500">No courses match the current search or filter.</p>
          )}
        </div>
      </div>
    </div>
  );
}

function CompactRows({ rows, empty }: { rows: Row[]; empty: string }) {
  if (!rows.length) return <p className="rounded-md border border-dashed border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">{empty}</p>;
  return (
    <div className="max-h-[54vh] divide-y divide-slate-100 overflow-auto rounded-md border border-slate-200">
      {rows.map((row, index) => (
        <div key={String(row.id ?? row.code ?? index)} className="grid min-h-12 gap-2 px-3 py-2 md:grid-cols-[minmax(0,1fr)_150px]">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium leading-5 text-slate-950">{asText(row.title ?? row.name ?? row.courseTitle ?? row.code ?? row.id, `Item ${index + 1}`)}</p>
            <p className="truncate text-xs leading-4 text-slate-500">{asText(row.description ?? row.category ?? row.status ?? row.subjectId, "No detail supplied")}</p>
          </div>
          <div className="flex flex-wrap items-center gap-1 md:justify-end">
            {["status", "level", "type", "courseStatus"].map((key) =>
              row[key] ? (
                <span key={key} className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium uppercase text-slate-600">
                  {asText(row[key])}
                </span>
              ) : null,
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

function KeyValues({ row, compact = false }: { row: Row; compact?: boolean }) {
  const entries = Object.entries(row).filter(([, value]) => typeof value !== "object").slice(0, compact ? 6 : 12);
  if (!entries.length) return <p className="text-sm text-slate-500">No summary values returned.</p>;
  return (
    <dl className={compact ? "grid gap-1.5" : "grid gap-2 sm:grid-cols-2"}>
      {entries.map(([key, value]) => (
        <div key={key} className="rounded-md bg-slate-50 px-3 py-2">
          <dt className="truncate text-[11px] text-slate-500">{key}</dt>
          <dd className="truncate text-sm font-medium text-slate-900">{asText(value)}</dd>
        </div>
      ))}
    </dl>
  );
}

export function LearningModal({ title, busy, onClose, onSubmit, children }: { title: string; busy: boolean; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4">
      <form onSubmit={onSubmit} className="w-full max-w-2xl overflow-hidden rounded-lg bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <h2 className="text-base font-semibold text-slate-950">{title}</h2>
          <button type="button" onClick={onClose} className="rounded-md p-1 text-slate-500 hover:bg-slate-100" aria-label="Close">
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="max-h-[70vh] overflow-y-auto p-4">
          <div className="grid gap-3 sm:grid-cols-2">{children}</div>
        </div>
        <div className="flex justify-end gap-2 border-t border-slate-200 px-4 py-3">
          <button type="button" onClick={onClose} className="rounded-md border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
            Cancel
          </button>
          <button type="submit" disabled={busy} className="inline-flex items-center gap-2 rounded-md bg-teal-700 px-4 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60">
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />} Save
          </button>
        </div>
      </form>
    </div>
  );
}

export function ModalFields({ kind }: { kind: ModalKey }) {
  if (kind === "course") return <><Field name="code" label="Code" required /><Field name="title" label="Title" required /><Field name="category" label="Category" /><Field name="level" label="Level" /><Field name="language" label="Language" defaultValue="en" /><Field name="estimatedDurationMinutes" label="Duration minutes" type="number" defaultValue="30" /><Field name="description" label="Description" area wide /></>;
  if (kind === "enrolment") return <><Field name="courseId" label="Course ID" required /><Field name="pathwayId" label="Pathway ID" /><Field name="enrolmentType" label="Type" defaultValue="SELF" /><Field name="dueAt" label="Due at" placeholder="2026-07-31T12:00:00Z" /></>;
  if (kind === "ai") return <><Field name="prompt" label="Prompt" area wide required /><Field name="targetCourseId" label="Target course ID" /><Field name="generationType" label="Generation type" defaultValue="course-outline" /><Field name="language" label="Language" defaultValue="en" /></>;
  if (kind === "library") return <><Field name="title" label="Title" required /><Field name="resourceType" label="Resource type" defaultValue="DOCUMENT" /><Field name="storageRef" label="Storage reference or URL" wide /><Field name="description" label="Description" area wide /></>;
  if (kind === "media") return <><Field name="title" label="Title" required /><Field name="mediaType" label="Media type" defaultValue="VIDEO" /><Field name="storageRef" label="Storage reference or URL" wide /><Field name="transcript" label="Transcript" area wide /></>;
  if (kind === "notification") return <><Field name="title" label="Title" required /><Field name="channelPreference" label="Channel" defaultValue="IN_APP" /><Field name="message" label="Message" area wide required /></>;
  if (kind === "activity") return <><Field name="title" label="Title" required /><Field name="activityType" label="Activity type" defaultValue="QUIZ" /><Field name="courseId" label="Course ID" /><Field name="lessonId" label="Lesson ID" /><Field name="instructions" label="Instructions" area wide /></>;
  if (kind === "cohort") return <><Field name="courseId" label="Course ID" required /><Field name="code" label="Code" /><Field name="title" label="Title" required /><Field name="status" label="Status" defaultValue="ACTIVE" /><Field name="startsAt" label="Starts at" placeholder="2026-07-31T12:00:00Z" /><Field name="endsAt" label="Ends at" placeholder="2026-08-31T12:00:00Z" /></>;
  return <><Field name="courseId" label="Course ID" required /><Field name="title" label="Title" required /><Field name="sessionType" label="Session type" defaultValue="VIRTUAL" /><Field name="startsAt" label="Starts at" placeholder="2026-07-31T12:00:00Z" /><Field name="endsAt" label="Ends at" placeholder="2026-07-31T13:00:00Z" /><Field name="facilitator" label="Facilitator ID" /><Field name="description" label="Description" area wide /></>;
}

function Field({ name, label, type = "text", area = false, wide = false, required = false, defaultValue, placeholder }: { name: string; label: string; type?: string; area?: boolean; wide?: boolean; required?: boolean; defaultValue?: string; placeholder?: string }) {
  const className = "mt-1 w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100";
  return (
    <label className={wide ? "sm:col-span-2" : ""}>
      <span className="text-xs font-medium text-slate-600">{label}</span>
      {area ? <textarea name={name} required={required} defaultValue={defaultValue} placeholder={placeholder} rows={4} className={className} /> : <input name={name} type={type} required={required} defaultValue={defaultValue} placeholder={placeholder} className={className} />}
    </label>
  );
}

export function modalTitle(kind: ModalKey) {
  return {
    course: "Draft course",
    enrolment: "Create enrolment",
    ai: "Generate AI learning draft",
    library: "Add library resource",
    media: "Add media asset",
    notification: "Schedule notification",
    activity: "Create interactive activity",
    cohort: "Create cohort",
    session: "Schedule session",
  }[kind];
}

function ActionText({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="text-xs font-semibold text-teal-700 hover:text-teal-900">
      {children}
    </button>
  );
}

function ActionButton({ icon, label, onClick }: { icon: ReactNode; label: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-slate-800 hover:border-teal-300 hover:bg-teal-50">
      <span className="[&>svg]:h-4 [&>svg]:w-4 [&>svg]:text-teal-700">{icon}</span>
      <span className="truncate">{label}</span>
    </button>
  );
}

function RowAction({ children, onClick, muted = false }: { children: ReactNode; onClick: () => void; muted?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "inline-flex h-7 items-center justify-center rounded-md border px-2 text-xs font-semibold transition",
        muted ? "border-slate-200 bg-white text-slate-600 hover:bg-slate-50" : "border-teal-200 bg-teal-50 text-teal-800 hover:border-teal-300 hover:bg-teal-100",
      ].join(" ")}
    >
      {children}
    </button>
  );
}

function ProgressCell({ row }: { row: Row }) {
  if (!isLearningRowEnrolled(row)) {
    return <span className="text-xs font-medium text-slate-400">Not enrolled</span>;
  }
  const progress = learningProgressPercent(row);
  return (
    <div className="min-w-0">
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-slate-800">{progress}%</span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
        <div className="h-full rounded-full bg-teal-600" style={{ width: `${progress}%` }} />
      </div>
    </div>
  );
}

function isLearningRowEnrolled(row: Row) {
  if (typeof row.__learningEnrolled === "boolean") return row.__learningEnrolled;
  if (row.enrolmentId || row.enrollmentId || row.enrolledAt || row.startedAt) return true;
  const status = asText(row.status ?? row.courseStatus ?? row.enrolmentStatus ?? row.enrollmentStatus, "").toLowerCase();
  return ["active", "enrolled", "in_progress", "in progress", "started", "complete", "completed"].includes(status);
}

function learningProgressPercent(row: Row) {
  const raw =
    row.progressPercent ??
    row.progressPercentage ??
    row.completionPercent ??
    row.completionPercentage ??
    row.percentComplete ??
    row.progress ??
    0;
  const parsed = typeof raw === "number" ? raw : Number(raw);
  if (!Number.isFinite(parsed)) return 0;
  const percent = parsed > 0 && parsed <= 1 ? parsed * 100 : parsed;
  return Math.max(0, Math.min(100, Math.round(percent)));
}

function StatusPill({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex w-fit max-w-full items-center rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold uppercase text-slate-600">
      <span className="truncate">{children}</span>
    </span>
  );
}

export function Banner({ tone, children }: { tone: "error" | "success"; children: ReactNode }) {
  return (
    <div className={["rounded-md border px-3 py-2 text-sm", tone === "error" ? "border-red-200 bg-red-50 text-red-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"].join(" ")}>
      {children}
    </div>
  );
}
