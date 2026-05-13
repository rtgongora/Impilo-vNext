"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, BookOpenCheck, GraduationCap, Clock, BadgeCheck, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFundoCatalog,
  type FundoCourseSummary,
} from "@/hooks/queries/useFundoCatalog";

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
  const [category, setCategory] = useState<string>("");
  const [cpdOnly, setCpdOnly] = useState<boolean>(false);
  const [mandatoryOnly, setMandatoryOnly] = useState<boolean>(false);

  const { data, isLoading, isError } = useFundoCatalog({
    category: category || undefined,
    cpdEligible: cpdOnly || undefined,
    mandatory: mandatoryOnly || undefined,
    limit: 50,
  });

  const items: FundoCourseSummary[] = data?.data?.items ?? [];

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

        <div className="mb-6 flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 bg-white p-4">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <span className="font-medium text-gray-800">Category</span>
            <input
              type="text"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="e.g. EHR_BASICS"
              className="rounded border border-gray-300 px-2 py-1 text-sm"
              aria-label="Filter by category"
            />
          </label>
          <label className="inline-flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={cpdOnly}
              onChange={(e) => setCpdOnly(e.target.checked)}
              aria-label="Filter to CPD-eligible courses only"
            />
            <span>CPD-eligible only</span>
          </label>
          <label className="inline-flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={mandatoryOnly}
              onChange={(e) => setMandatoryOnly(e.target.checked)}
              aria-label="Filter to mandatory courses only"
            />
            <span>Mandatory only</span>
          </label>
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

        {!isLoading && !isError && items.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-600">
            No catalogue items match your filters yet.
          </div>
        ) : null}

        {!isLoading && !isError && items.length > 0 ? (
          <ul className="grid gap-3 sm:grid-cols-2">
            {items.map((c) => (
              <li
                key={c.id}
                className="rounded-lg border border-gray-200 bg-white p-4 transition hover:border-teal-300 hover:shadow-sm"
              >
                <Link href={`/learning/courses/${c.id}`} className="block">
                  <div className="flex items-start gap-2">
                    <BookOpenCheck className="mt-0.5 h-5 w-5 flex-shrink-0 text-teal-700" />
                    <div className="flex-1">
                      <p className="font-semibold text-gray-900">{c.title}</p>
                      <p className="font-mono text-xs text-gray-500">{c.code}</p>
                      {c.description ? (
                        <p className="mt-1 line-clamp-2 text-xs text-gray-600">{c.description}</p>
                      ) : null}
                      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                        {c.category ? (
                          <span className="rounded bg-gray-100 px-1.5 py-0.5 text-gray-700">
                            {c.category}
                          </span>
                        ) : null}
                        {c.level ? (
                          <span className="rounded bg-gray-100 px-1.5 py-0.5 text-gray-700">
                            {c.level}
                          </span>
                        ) : null}
                        {c.cpdEligible ? (
                          <span className="inline-flex items-center gap-1 rounded bg-emerald-50 px-1.5 py-0.5 text-emerald-800">
                            <BadgeCheck className="h-3 w-3" /> CPD
                          </span>
                        ) : null}
                        {c.mandatory ? (
                          <span className="inline-flex items-center gap-1 rounded bg-blue-50 px-1.5 py-0.5 text-blue-800">
                            <CheckCircle2 className="h-3 w-3" /> Mandatory
                          </span>
                        ) : null}
                        {c.estimatedDurationMinutes ? (
                          <span className="inline-flex items-center gap-1 text-gray-500">
                            <Clock className="h-3 w-3" /> {c.estimatedDurationMinutes} min
                          </span>
                        ) : null}
                      </div>
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
