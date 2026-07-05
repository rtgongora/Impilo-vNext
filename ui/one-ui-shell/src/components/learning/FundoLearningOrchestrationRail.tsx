"use client";

import { GraduationCap, Loader2 } from "lucide-react";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { summarizeFundoMyLearning, useFundoEnrolments, useFundoMyLearning } from "@/hooks/queries/useFundoLms";
import { useFundoCatalog } from "@/hooks/queries/useFundoCatalog";

export function FundoLearningOrchestrationRail() {
  const subject = useLearningSubject();
  const myLearningQ = useFundoMyLearning(subject);
  const enrolmentsQ = useFundoEnrolments(subject);
  const catalogQ = useFundoCatalog({ limit: 5, status: "PUBLISHED" });

  const payload = (myLearningQ.data?.data ?? {}) as Record<string, unknown>;
  const kpis = summarizeFundoMyLearning(payload);
  // The v11 enrolments endpoint returns { data: { items: [...] } }.
  const enrolmentData = enrolmentsQ.data?.data as Record<string, unknown> | unknown[] | undefined;
  const enrolmentRows = Array.isArray(enrolmentData)
    ? enrolmentData
    : Array.isArray((enrolmentData as Record<string, unknown> | undefined)?.items)
      ? ((enrolmentData as Record<string, unknown>).items as unknown[])
      : [];
  const enrolmentCount = enrolmentRows.length;
  const catalogCount = catalogQ.data?.data?.items?.length ?? 0;

  return (
    <section
      className="mb-3 rounded-lg border border-teal-200 bg-teal-50/80 px-3 py-2.5"
      data-testid="fundo-learning-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-teal-900">
            <GraduationCap className="h-4 w-4" />
            Fundo learning orchestration
          </p>
          <p className="mt-1 text-xs text-teal-800" data-testid="fundo-kpi-strip">
            {myLearningQ.isLoading
              ? "Loading my-learning snapshot…"
              : `${kpis.inProgress} in progress · ${kpis.required} required · ${kpis.overdue} overdue · ${enrolmentCount} enrolment(s)`}
          </p>
          <p className="mt-1 text-xs text-teal-700" data-testid="fundo-catalog-probe">
            {catalogQ.isLoading
              ? "Catalog: probing native LMS…"
              : `${catalogCount} published course(s) ready for enrolment`}
          </p>
        </div>
      </div>
      {(myLearningQ.isLoading || catalogQ.isLoading) && (
        <div className="mt-2 flex items-center gap-2 text-xs text-teal-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Binding catalogue → enrolment → progress chain…
        </div>
      )}
    </section>
  );
}
