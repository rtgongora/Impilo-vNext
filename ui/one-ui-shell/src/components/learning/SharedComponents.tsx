"use client";

import { ReactNode } from "react";
import { asText, type Row } from "@/components/learning/learningUtils";

// ===== PANEL & CONTAINERS =====

export function Panel({ title, action, children, className = "" }: { title: string; action?: ReactNode; children: ReactNode; className?: string }) {
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

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
      <p className="text-sm font-semibold text-slate-900">{title}</p>
      {detail && <p className="text-xs text-slate-500 mt-1">{detail}</p>}
    </div>
  );
}

// ===== STATUS & PILLS =====

export function StatusPill({ children }: { children: ReactNode | string }) {
  return (
    <span className="inline-flex w-fit max-w-full items-center rounded-full bg-slate-100 px-2 py-0.5 text[10px] font-semibold uppercase text-slate-600">
      <span className="truncate">{children as ReactNode}</span>
    </span>
  );
}

// ===== COURSE CARD =====

export function CourseCard({ row, onEnroll, onDetails }: { row: Row; onEnroll: () => void; onDetails: () => void }) {
  const progress = learningProgressPercent(row);
  const isEnrolled = isLearningRowEnrolled(row);
  const required = row.mandatory === true || row.mandatoryIncomplete === true || row.mandatory === "true";

  return (
    <div className={["flex flex-col rounded-lg border bg-white shadow-sm overflow-hidden hover:shadow-md transition", required ? "border-amber-300" : "border-slate-200"].join(" ")}>
      {/* Status pills */}
      <div className="flex flex-wrap items-center gap-1 px-3 pt-2">
        <StatusPill>{asText(row.status ?? row.courseStatus, "Open")}</StatusPill>
        {row.level ? <StatusPill>{asText(row.level as string)}</StatusPill> : null}
        {required ? <span className="inline-flex rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold uppercase text-amber-800">Required</span> : null}
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col px-3 py-2 min-w-0">
        <p className="font-semibold text-slate-950 text-sm truncate">{rowTitle(row, "Course")}</p>
        <p className="text-xs text-slate-500 mt-1 line-clamp-2">{rowDetail(row)}</p>
        {required ? <p className="mt-2 text-xs font-semibold text-amber-700">{asText(row.recommendationNotice, "Required learning")}</p> : null}

        {/* Progress bar if enrolled */}
        {isEnrolled && (
          <div className="mt-2 space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-700">{progress}%</span>
            </div>
            <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
              <div className="h-full bg-teal-600" style={{ width: `${progress}%` }} />
            </div>
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex gap-1 border-t border-slate-100 p-2">
        <button
          onClick={onEnroll}
          className="flex-1 h-8 rounded-md bg-teal-50 text-teal-700 text-xs font-semibold hover:bg-teal-100 transition"
        >
          {isEnrolled ? "Continue" : "Enroll"}
        </button>
        <button
          onClick={onDetails}
          className="flex-1 h-8 rounded-md border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition"
        >
          Details
        </button>
      </div>
    </div>
  );
}

// ===== QUICK ACTION CARDS =====

export function QuickActionCard({ icon, title, detail, teal, onClick }: { icon: ReactNode; title: string; detail: string; teal?: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`flex items-start gap-3 rounded-lg border p-3 text-left transition ${
        teal ? "border-teal-200 bg-teal-50 hover:bg-teal-100" : "border-slate-200 bg-white hover:border-teal-300 hover:bg-teal-50/40"
      }`}
    >
      <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md [&>svg]:h-5 [&>svg]:w-5 ${teal ? "bg-teal-700 text-white" : "bg-slate-100 text-teal-700"}`}>
        {icon}
      </span>
      <div className="min-w-0">
        <p className={`font-semibold text-sm ${teal ? "text-teal-900" : "text-slate-900"}`}>{title}</p>
        <p className={`text-xs mt-0.5 ${teal ? "text-teal-700" : "text-slate-600"}`}>{detail}</p>
      </div>
    </button>
  );
}

// ===== CREATION CARDS =====

export function CreationCard({ icon, label, onClick }: { icon: ReactNode; label: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="flex flex-col items-center justify-center gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-center hover:border-teal-300 hover:bg-teal-50 transition"
    >
      <span className="flex h-8 w-8 items-center justify-center rounded-md bg-white text-teal-700 [&>svg]:h-4 [&>svg]:w-4">
        {icon}
      </span>
      <p className="text-xs font-semibold text-slate-800">{label}</p>
    </button>
  );
}

export function CreationTemplate({
  icon,
  title,
  detail,
  count,
  label,
  action,
}: {
  icon: ReactNode;
  title: string;
  detail: string;
  count: number;
  label: string;
  action: () => void;
}) {
  return (
    <button
      onClick={action}
      className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-3 text-left hover:shadow-md hover:border-teal-300 transition"
    >
      <div className="flex items-start justify-between gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-md bg-slate-50 text-slate-700 [&>svg]:h-4 [&>svg]:w-4">
          {icon}
        </span>
        <span className="inline-flex rounded-full bg-slate-100 text-slate-700 text-[10px] font-bold px-2 py-0.5">
          {count}
        </span>
      </div>
      <div className="min-w-0">
        <p className="font-semibold text-sm text-slate-900">{title}</p>
        <p className="text-xs text-slate-500 mt-0.5 line-clamp-2">{detail}</p>
        <p className="text-xs text-slate-400 font-medium mt-2">{count} {label}</p>
      </div>
    </button>
  );
}

// ===== STAT & METRIC CARDS =====

export function StatCard({
  label,
  value,
  icon,
  tone = "neutral",
  onClick,
}: {
  label: string;
  value: number;
  icon: ReactNode;
  tone?: "neutral" | "warn";
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex flex-col items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white p-3 text-center shadow-sm hover:border-teal-300 hover:bg-teal-50/40 transition"
    >
      <span className={`flex h-7 w-7 items-center justify-center rounded-md text-slate-700 [&>svg]:h-4 [&>svg]:w-4 ${tone === "warn" ? "bg-amber-50 text-amber-700" : "bg-slate-50"}`}>
        {icon}
      </span>
      <div className="min-w-0">
        <p className={`text-lg font-bold ${tone === "warn" ? "text-amber-700" : "text-slate-950"}`}>{value}</p>
        <p className="text-xs font-medium text-slate-500 leading-3">{label}</p>
      </div>
    </button>
  );
}

export function MetricTile({
  label,
  value,
  icon,
  tone = "neutral",
  onClick,
}: {
  label: string;
  value: number;
  icon: ReactNode;
  tone?: "neutral" | "ok" | "warn";
  onClick: () => void;
}) {
  const valueClass = tone === "warn" ? "text-amber-700" : tone === "ok" ? "text-emerald-700" : "text-slate-950";
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-16 items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-left shadow-sm transition hover:border-teal-300 hover:bg-teal-50/40"
    >
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-slate-50 text-teal-700 ring-1 ring-slate-100 [&>svg]:h-4 [&>svg]:w-4">{icon}</span>
      <span className="min-w-0">
        <span className={["block text-lg font-semibold leading-6", valueClass].join(" ")}>{value}</span>
        <span className="block truncate text-xs font-medium text-slate-500">{label}</span>
      </span>
    </button>
  );
}

export function PrimaryTaskCard({
  icon,
  title,
  metric,
  detail,
  action,
  onClick,
}: {
  icon: ReactNode;
  title: string;
  metric: string;
  detail: string;
  action: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="grid min-h-[138px] grid-rows-[auto_1fr_auto] rounded-lg border border-slate-200 bg-white p-4 text-left shadow-sm transition hover:border-teal-300 hover:bg-teal-50/40"
    >
      <span className="flex items-center justify-between gap-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-teal-50 text-teal-700 ring-1 ring-teal-100 [&>svg]:h-5 [&>svg]:w-5">
          {icon}
        </span>
        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold uppercase text-slate-600">{action}</span>
      </span>
      <span className="mt-3 min-w-0">
        <span className="block truncate text-sm font-semibold uppercase tracking-wide text-slate-500">{title}</span>
        <span className="mt-1 block truncate text-xl font-semibold leading-7 text-slate-950">{metric}</span>
        <span className="mt-1 block line-clamp-2 text-xs leading-5 text-slate-500">{detail}</span>
      </span>
    </button>
  );
}

// ===== PATHWAY & CERTIFICATE CARDS =====

export function PathwayCard({ row }: { row: Row }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-slate-200 bg-white p-3 hover:shadow-md transition">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-blue-50 text-blue-700">
        {/* Route icon from lucide - we'll import if needed */}
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 17h8m0 0V9m0 8l-8-8-4 4m0 0l-8-8" />
        </svg>
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-slate-950 text-sm truncate">{asText(row.title ?? row.name ?? row.code, "Pathway")}</p>
        <p className="text-xs text-slate-500 mt-1">{asText(row.description, "No description")}</p>
        <p className="text-xs font-medium text-blue-700 mt-2">Learn more →</p>
      </div>
    </div>
  );
}

export function CertificateCard({ row }: { row: Row }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 hover:shadow-md transition">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-amber-100 text-amber-700">
        <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 24 24">
          <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" />
        </svg>
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-slate-950 text-sm truncate">{asText(row.title ?? row.name, "Certificate")}</p>
        <p className="text-xs text-slate-600 mt-1">{asText(row.description, "Professional certification")}</p>
      </div>
    </div>
  );
}

// ===== ACTION BUTTONS =====

export function ActionText({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="text-xs font-semibold text-teal-700 hover:text-teal-900">
      {children}
    </button>
  );
}

export function ActionButton({ icon, label, onClick }: { icon: ReactNode; label: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-slate-800 hover:border-teal-300 hover:bg-teal-50">
      <span className="[&>svg]:h-4 [&>svg]:w-4 [&>svg]:text-teal-700">{icon}</span>
      <span className="truncate">{label}</span>
    </button>
  );
}

export function RowAction({ children, onClick, muted = false }: { children: ReactNode; onClick: () => void; muted?: boolean }) {
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

// ===== UTILITY FUNCTIONS =====

export function rowTitle(row: Row, fallback: string) {
  return asText(row.title ?? row.name ?? row.courseTitle ?? row.code ?? row.id, fallback);
}

export function rowDetail(row: Row) {
  return asText(row.description ?? row.category ?? row.type ?? row.subjectId, "No detail supplied");
}

export function learningProgressPercent(row: Row) {
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

export function isLearningRowEnrolled(row: Row) {
  if (typeof row.__learningEnrolled === "boolean") return row.__learningEnrolled;
  if (row.enrolmentId || row.enrollmentId || row.enrolledAt || row.startedAt) return true;
  const status = asText(row.status ?? row.courseStatus ?? row.enrolmentStatus ?? row.enrollmentStatus, "").toLowerCase();
  return ["active", "enrolled", "in_progress", "in progress", "started", "complete", "completed"].includes(status);
}

export function countOf(primary: unknown, fallback: number) {
  if (typeof primary === "number") return primary;
  const parsed = Number(primary);
  return Number.isFinite(parsed) ? parsed : fallback;
}
