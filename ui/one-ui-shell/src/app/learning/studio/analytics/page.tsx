"use client";

import { useMemo, useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoReportFiltered, useFundoReportsOverview } from "@/hooks/queries/useFundoLms";

export default function FundoStudioAnalyticsPage() {
  const [roleView, setRoleView] = useState<"LEARNER" | "TRAINER" | "ADMIN" | "LEADERSHIP">("LEARNER");
  const { data } = useFundoReportsOverview();
  const learnerReport = useFundoReportFiltered("overdue-learning", { limit: 20 });
  const trainerReport = useFundoReportFiltered("assessment-performance", { limit: 20 });
  const adminReport = useFundoReportFiltered("course-completions", { limit: 40 });
  const leadershipReport = useFundoReportFiltered("cohort-completions", { limit: 40 });

  const payload = (data?.data ?? {}) as Record<string, number>;
  const kpis = [
    ["Courses", payload.courses ?? 0],
    ["Enrolments", payload.enrolments ?? 0],
    ["Completed", payload.completed ?? 0],
    ["Certificates", payload.certificates ?? 0],
  ];

  const roleCards = useMemo(() => {
    if (roleView === "LEARNER") {
      const items = (((learnerReport.data?.data ?? {}) as { items?: Array<Record<string, unknown>> }).items ?? []);
      return [
        { label: "Courses due", value: items.length, pct: Math.min(100, items.length * 10) },
        { label: "Overdue", value: items.filter((i) => i.status !== "COMPLETED").length, pct: Math.min(100, items.length * 8) },
      ];
    }
    if (roleView === "TRAINER") {
      const items = (((trainerReport.data?.data ?? {}) as { items?: Array<Record<string, unknown>> }).items ?? []);
      const passed = items.filter((i) => i.passed === true).length;
      return [
        { label: "Assessment attempts", value: items.length, pct: Math.min(100, items.length * 8) },
        { label: "Pass trend", value: passed, pct: items.length > 0 ? Math.round((passed / items.length) * 100) : 0 },
      ];
    }
    if (roleView === "ADMIN") {
      const items = (((adminReport.data?.data ?? {}) as { items?: Array<Record<string, unknown>> }).items ?? []);
      const completed = items.filter((i) => i.status === "COMPLETED").length;
      return [
        { label: "Completion rows", value: items.length, pct: Math.min(100, items.length * 5) },
        { label: "Completed", value: completed, pct: items.length > 0 ? Math.round((completed / items.length) * 100) : 0 },
      ];
    }
    const items = (((leadershipReport.data?.data ?? {}) as { items?: Array<Record<string, unknown>> }).items ?? []);
    const completed = items.filter((i) => i.status === "COMPLETED").length;
    return [
      { label: "Cohort coverage", value: items.length, pct: Math.min(100, items.length * 6) },
      { label: "Completion health", value: completed, pct: items.length > 0 ? Math.round((completed / items.length) * 100) : 0 },
    ];
  }, [adminReport.data?.data, leadershipReport.data?.data, learnerReport.data?.data, roleView, trainerReport.data?.data]);

  return (
    <FundoStudioWorkspace title="Studio Analytics" subtitle="Learner/trainer/admin/leadership operational analytics in a unified native dashboard surface.">
      <div className="mb-3 flex flex-wrap gap-2">
        {(["LEARNER", "TRAINER", "ADMIN", "LEADERSHIP"] as const).map((role) => (
          <button
            key={role}
            type="button"
            onClick={() => setRoleView(role)}
            className={`rounded border px-3 py-1 text-xs ${roleView === role ? "border-teal-600 bg-teal-50 text-teal-700" : "border-border text-foreground"}`}
          >
            {role}
          </button>
        ))}
      </div>
      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
        {kpis.map(([label, value]) => (
          <div key={label} className="rounded border border-border bg-card p-4">
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{value}</p>
          </div>
        ))}
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        {roleCards.map((metric) => (
          <div key={metric.label} className="rounded border border-border bg-card p-4">
            <div className="flex items-center justify-between">
              <p className="text-sm font-medium text-foreground">{metric.label}</p>
              <p className="text-sm font-semibold text-foreground">{metric.value}</p>
            </div>
            <div className="mt-2 h-2 rounded bg-neutral-100">
              <div className="h-2 rounded bg-teal-500" style={{ width: `${metric.pct}%` }} />
            </div>
          </div>
        ))}
      </div>
    </FundoStudioWorkspace>
  );
}
