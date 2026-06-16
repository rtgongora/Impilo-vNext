"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Award, BarChart3, Bell, BookOpenCheck, ClipboardList, FolderOpen, GraduationCap, Library, PenTool, Route } from "lucide-react";
import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { PageShell } from "@/components/PageShell";
import { summarizeFundoMyLearning, useFundoMyLearning } from "@/hooks/queries/useFundoLms";
import { FundoLearningOrchestrationRail } from "@/components/learning/FundoLearningOrchestrationRail";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";

export default function LearningHubPage() {
  const searchParams = useSearchParams();
  const focus = searchParams.get("focus");
  const subject = useLearningSubject();
  const { data, isLoading } = useFundoMyLearning(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const fundoKpis = summarizeFundoMyLearning(payload);
  const recommended = Array.isArray(payload.recommended) ? payload.recommended.length : 0;
  const certs = Array.isArray(payload.certificates) ? payload.certificates.length : 0;
  const metricCards = [
    { label: "Continue learning", value: fundoKpis.inProgress, href: "/learning?focus=in-progress", focusKey: "in-progress" },
    { label: "Required training", value: fundoKpis.required, href: "/learning?focus=required", focusKey: "required" },
    { label: "Overdue modules", value: fundoKpis.overdue, href: "/learning?focus=overdue", focusKey: "overdue" },
    { label: "Recommended courses", value: recommended, href: "/learning/catalog", focusKey: null },
    { label: "Certificates", value: certs, href: "/learning/certificates", focusKey: null },
    { label: "CPD eligible", value: fundoKpis.cpdEligible, href: "/learning?focus=cpd", focusKey: "cpd" },
    { label: "Pathways", value: "View", href: "/learning/pathways", focusKey: null },
  ];
  const destinations = [
    { href: "/learning/catalog", label: "Catalogue", detail: "Find available courses and CPD content", icon: BookOpenCheck },
    { href: "/learning/my-learning", label: "My learning", detail: "Assigned, active, and completed enrolments", icon: GraduationCap },
    { href: "/learning/record", label: "Transcript", detail: "Learning record and completion history", icon: ClipboardList },
    { href: "/learning/certificates", label: "Certificates", detail: "Issued certificates and evidence", icon: Award },
    { href: "/learning/pathways", label: "Pathways", detail: "Structured learning sequences", icon: Route },
    { href: "/learning/reports", label: "Reports", detail: "Cohorts, courses, overdue, and assessment views", icon: BarChart3 },
    { href: "/learning/library", label: "Library", detail: "Reusable learning resources and uploads", icon: Library },
    { href: "/learning/notifications", label: "Notifications", detail: "Reminder and outcome messaging", icon: Bell },
    { href: "/learning/studio", label: "Studio", detail: "Author and publish Fundo content", icon: PenTool },
    { href: "/learning/admin", label: "Admin", detail: "Govern courses, pathways, and assessments", icon: FolderOpen },
  ];
  const focusState = metricCards.find((card) => card.focusKey === focus);
  const focusCount = typeof focusState?.value === "number" ? focusState.value : null;
  const emptyFocusMessages: Record<string, { title: string; body: string; actionHref: string; actionLabel: string }> = {
    "in-progress": {
      title: "No active courses",
      body: "You do not have any courses in progress right now. Browse the catalogue or open My learning to start a new enrolment.",
      actionHref: "/learning/catalog",
      actionLabel: "Browse catalogue",
    },
    required: {
      title: "No required training assigned",
      body: "There are no mandatory learning items assigned to you at the moment. Check My learning for optional or recommended activity.",
      actionHref: "/learning/my-learning",
      actionLabel: "Open My learning",
    },
    overdue: {
      title: "No overdue modules",
      body: "You are clear on overdue learning. Review required or recommended courses to stay ahead of future obligations.",
      actionHref: "/learning/catalog",
      actionLabel: "View catalogue",
    },
    cpd: {
      title: "No CPD-eligible items pending",
      body: "There are no CPD-eligible items waiting in this snapshot. Open certificates or the catalogue for CPD evidence and opportunities.",
      actionHref: "/learning/certificates",
      actionLabel: "View certificates",
    },
  };
  const emptyFocusMessage = focus ? emptyFocusMessages[focus] : undefined;

  return (
      <PageShell
        title="Impilo Fundo"
        hideHeader
      >
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-card px-3 py-2">
          <div className="flex min-w-0 items-center gap-2">
            <ServiceLogo slug="fundo" size="compact" />
            <div className="min-w-0">
              <h1 className="truncate text-lg font-semibold text-foreground">Impilo Fundo</h1>
              <p className="truncate text-xs text-muted-foreground">
                Learning, certification, in-service training and CPD support.
              </p>
            </div>
          </div>
          <p className="rounded-full border border-teal-200 bg-teal-50 px-2.5 py-1 text-xs font-medium text-teal-800">
            {isLoading ? "Loading snapshot" : `${fundoKpis.required} required · ${fundoKpis.overdue} overdue`}
          </p>
        </div>

        <div className="space-y-3">
          <FundoLearningOrchestrationRail />

          <section className="grid gap-3 xl:grid-cols-[minmax(0,1fr)_360px]">
            <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
              {metricCards.map((card) => (
                <Link
                  key={card.label}
                  href={card.href}
                  className={`rounded-lg border bg-card px-3 py-2 transition hover:border-teal-300 ${
                    card.focusKey && focus === card.focusKey ? "border-teal-400 ring-2 ring-teal-100" : "border-border"
                  }`}
                >
                  <p className="text-xs text-muted-foreground">{card.label}</p>
                  <p className="mt-0.5 text-lg font-semibold text-foreground">{isLoading ? "..." : card.value}</p>
                </Link>
              ))}
            </div>

            <div className="rounded-lg border border-amber-400 bg-gradient-to-br from-amber-400 to-amber-500 p-3 text-[color:var(--on-yellow)] shadow-sm">
              <div className="rounded-lg bg-white/90 px-2.5 py-2 text-foreground">
                <h2 className="text-sm font-semibold">Start here</h2>
                <p className="text-xs text-muted-foreground">Most-used Fundo paths for day-to-day learning.</p>
              </div>
              <div className="mt-2 grid grid-cols-2 gap-2">
                {destinations.slice(0, 4).map((item) => {
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className="group flex min-h-[64px] items-start gap-2 rounded-lg border border-amber-200 bg-white/90 px-2.5 py-2 text-foreground transition hover:border-amber-300 hover:bg-white"
                    >
                      <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-800 group-hover:bg-amber-50">
                        <Icon className="h-3.5 w-3.5" />
                      </span>
                      <span className="min-w-0">
                        <span className="block text-sm font-medium text-foreground">{item.label}</span>
                        <span className="mt-0.5 block truncate text-xs text-muted-foreground">{item.detail}</span>
                      </span>
                    </Link>
                  );
                })}
              </div>
            </div>
          </section>

          {focusState ? (
            <div
              className={[
                "flex flex-wrap items-center justify-between gap-3 rounded-lg border px-3 py-2 text-xs",
                focusCount === 0 ? "border-emerald-200 bg-emerald-50 text-emerald-900" : "border-teal-200 bg-teal-50 text-teal-800",
              ].join(" ")}
            >
              <div>
                <p className="font-semibold">
                  {focusCount === 0 && emptyFocusMessage ? emptyFocusMessage.title : `${focusState.label}: ${focusState.value}`}
                </p>
                <p className="mt-0.5">
                  {focusCount === 0 && emptyFocusMessage
                    ? emptyFocusMessage.body
                    : `These ${focusState.label.toLowerCase()} items are prioritized from your current Fundo learning snapshot.`}
                </p>
              </div>
              {focusCount === 0 && emptyFocusMessage ? (
                <Link
                  href={emptyFocusMessage.actionHref}
                  className="shrink-0 rounded border border-emerald-200 bg-card px-2.5 py-1 font-medium text-emerald-900 hover:bg-emerald-50"
                >
                  {emptyFocusMessage.actionLabel}
                </Link>
              ) : null}
            </div>
          ) : null}

          <section className="rounded-lg border border-border bg-card p-3">
            <div className="mb-2 flex flex-wrap items-end justify-between gap-2">
              <div>
                <h2 className="text-sm font-semibold text-foreground">Learning workspaces</h2>
                <p className="text-xs text-muted-foreground">Grouped Fundo actions.</p>
              </div>
            </div>
            <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-5">
              {destinations.slice(4).map((item) => {
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className="group flex min-h-[76px] items-start gap-2 rounded-lg border border-border px-3 py-2 transition hover:border-teal-200 hover:bg-teal-50"
                  >
                    <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-teal-50 text-teal-700 group-hover:bg-card">
                      <Icon className="h-3.5 w-3.5" />
                    </span>
                    <span className="min-w-0">
                      <span className="block text-sm font-medium text-foreground">{item.label}</span>
                      <span className="mt-0.5 block text-xs leading-4 text-muted-foreground">{item.detail}</span>
                    </span>
                  </Link>
                );
              })}
            </div>
          </section>
        </div>
      </PageShell>
  );
}
