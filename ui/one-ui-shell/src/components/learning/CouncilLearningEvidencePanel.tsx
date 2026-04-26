"use client";

import { BookOpen, Loader2 } from "lucide-react";
import { useLearningSubjectCompletions } from "@/hooks/queries/useLearningSubjectCompletions";

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

/** Council reviewer: learning-layer completions for a provider public id (Fundo sync + catalog). */
export function CouncilLearningEvidencePanel(props: { providerPublicId: string | undefined }) {
  const pid = props.providerPublicId;
  const { data, isLoading, isError } = useLearningSubjectCompletions(
    pid ? "PROVIDER_PUBLIC_ID" : undefined,
    pid,
    30,
  );

  const root = asRecord(data);
  const inner = root ? asRecord(root.data) : null;
  const items = inner && Array.isArray(inner.items) ? inner.items : [];

  if (!pid) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
        Pass <strong>providerPublicId</strong> in the query string to load learning completions from the platform layer.
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-gray-500">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading learning evidence…
      </div>
    );
  }
  if (isError || items.length === 0) {
    return (
      <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600">
        No learning completions recorded in the learning layer for this provider yet (Fundo webhooks must sync into
        learning-service).
      </div>
    );
  }

  return (
    <section className="rounded-lg border border-teal-200/80 bg-teal-50/30 p-4 dark:border-teal-900/40 dark:bg-teal-950/20">
      <div className="mb-2 flex items-center gap-2">
        <BookOpen className="h-5 w-5 text-teal-600" />
        <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Learning evidence (platform layer)</h2>
      </div>
      <p className="text-xs text-gray-600 dark:text-gray-400 mb-3">
        Governed completions ingested into learning-service — CPD acceptance remains in Varapi council workflows.
      </p>
      <ul className="space-y-2 max-h-64 overflow-auto text-sm">
        {items.map((row, i) => {
          const r = asRecord(row);
          const title = typeof r?.resourceTitle === "string" ? r.resourceTitle : "Resource";
          const when = typeof r?.completionDate === "string" ? r.completionDate : "";
          const src = typeof r?.sourceType === "string" ? r.sourceType : "";
          return (
            <li
              key={i}
              className="rounded-md border border-gray-200 bg-white px-3 py-2 dark:border-gray-700 dark:bg-gray-950/60"
            >
              <span className="font-medium text-gray-900 dark:text-gray-100">{title}</span>
              <span className="ml-2 text-xs text-gray-500">
                {when} · {src}
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
