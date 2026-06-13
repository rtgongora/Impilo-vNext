"use client";

import { ExternalLink, GraduationCap } from "lucide-react";
import Link from "next/link";
import { useLearningHelpdesk } from "@/hooks/queries/useLearningWorkflowContext";
import { useAuthStore } from "@/hooks/useAuthStore";
import { recordLearningResourceOpened } from "@/lib/learning-resource-opened";

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

export function HelpdeskLearningSuggestions(props: { issueType?: string; title?: string }) {
  const issueType = props.issueType ?? "GENERAL";
  const user = useAuthStore((s) => s.user);
  const subjectType = user?.providerId ? "PROVIDER_PUBLIC_ID" : "USER_HEALTH_ID";
  const subjectId = user?.providerId ?? user?.id;

  const { data, isLoading, isError } = useLearningHelpdesk(issueType, subjectType, subjectId);

  const root = asRecord(data);
  const inner = root ? asRecord(root.data) : null;
  const items = inner && Array.isArray(inner.items) ? inner.items : [];

  const wfCtx = { surface: "helpdesk", issueType };

  const track = (resourceId: string | undefined) => {
    if (resourceId) recordLearningResourceOpened(resourceId, wfCtx);
  };

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">Loading suggested training…</div>
    );
  }
  if (isError || items.length === 0) {
    return null;
  }

  return (
    <section className="rounded-lg border border-violet-200/80 bg-violet-50/50 p-4 dark:border-violet-900/40 dark:bg-violet-950/20">
      <div className="mb-2 flex items-center gap-2">
        <GraduationCap className="h-5 w-5 text-violet-600" />
        <h3 className="text-sm font-semibold text-foreground dark:text-foreground">
          {props.title ?? "Resolve with learning"}
        </h3>
      </div>
      <ul className="space-y-2">
        {items.map((row, i) => {
          const r = asRecord(row);
          const res = r?.resource && typeof r.resource === "object" ? asRecord(r.resource) : null;
          const title = typeof res?.title === "string" ? res.title : "Learning resource";
          const rid = typeof res?.id === "string" ? res.id : undefined;
          const exp = typeof res?.experienceHref === "string" ? res.experienceHref : undefined;
          const fundo = typeof res?.fundoLaunchUrl === "string" ? res.fundoLaunchUrl : undefined;
          return (
            <li key={i} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-card px-3 py-2 text-sm dark:border-border dark:bg-card/60">
              <span className="text-foreground dark:text-foreground">{title}</span>
              <div className="flex gap-2">
                {exp ? (
                  <Link
                    href={exp}
                    onClick={() => track(rid)}
                    className="text-xs font-medium text-violet-700 hover:underline dark:text-violet-300"
                  >
                    Learning hub
                  </Link>
                ) : null}
                {fundo ? (
                  <a
                    href={fundo}
                    target="_blank"
                    rel="noreferrer"
                    onClick={() => track(rid)}
                    className="inline-flex items-center gap-1 text-xs font-medium text-foreground hover:underline dark:text-foreground"
                  >
                    <ExternalLink className="h-3.5 w-3.5" /> Fundo
                  </a>
                ) : null}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
