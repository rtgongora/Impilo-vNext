"use client";

import { BookOpen, ExternalLink, Sparkles } from "lucide-react";
import Link from "next/link";
import { useLearningWorkflowContext } from "@/hooks/queries/useLearningWorkflowContext";
import { useAuthStore } from "@/hooks/useAuthStore";
import { recordLearningResourceOpened } from "@/lib/learning-resource-opened";
import { LearnerExternalIdentityLink } from "@/components/learning/LearnerExternalIdentityLink";

type ResourceView = {
  id?: string;
  title?: string;
  resourceCode?: string;
  sourceType?: string;
  experienceHref?: string;
  fundoLaunchUrl?: string;
  deepLink?: string;
};

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : null;
}

function resourceFrom(v: unknown): ResourceView | null {
  const o = asRecord(v);
  if (!o) return null;
  return {
    id: typeof o.id === "string" ? o.id : undefined,
    title: typeof o.title === "string" ? o.title : undefined,
    resourceCode: typeof o.resourceCode === "string" ? o.resourceCode : undefined,
    sourceType: typeof o.sourceType === "string" ? o.sourceType : undefined,
    experienceHref: typeof o.experienceHref === "string" ? o.experienceHref : undefined,
    fundoLaunchUrl: typeof o.fundoLaunchUrl === "string" ? o.fundoLaunchUrl : undefined,
    deepLink: typeof o.deepLink === "string" ? o.deepLink : undefined,
  };
}

export function ContextualLearningPanel(props: {
  appCode: string;
  routeRef: string;
  workflowCode?: string;
  title?: string;
}) {
  const user = useAuthStore((s) => s.user);
  const roles = user?.roles?.length ? user.roles.join(",") : undefined;
  const subjectType = user?.providerId ? "PROVIDER_PUBLIC_ID" : "USER_HEALTH_ID";
  const subjectId = user?.providerId ?? user?.id;

  const { data, isLoading, isError } = useLearningWorkflowContext({
    appCode: props.appCode,
    routeRef: props.routeRef,
    workflowCode: props.workflowCode,
    roles,
    subjectType,
    subjectId,
  });

  const wfCtx = {
    appCode: props.appCode,
    routeRef: props.routeRef,
    workflowCode: props.workflowCode ?? null,
    surface: "contextual_panel",
  };

  const track = (resourceId: string | undefined) => {
    if (resourceId) recordLearningResourceOpened(resourceId, wfCtx);
  };

  const root = asRecord(data);
  const inner = root ? asRecord(root.data) : null;
  const hints = inner && Array.isArray(inner.hints) ? inner.hints : [];
  const links = inner && Array.isArray(inner.workflowLinks) ? inner.workflowLinks : [];
  const roleRecs = inner && Array.isArray(inner.roleRecommendations) ? inner.roleRecommendations : [];

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border bg-background/80 p-4 text-sm text-muted-foreground dark:border-border dark:bg-neutral-900/40 dark:text-muted-foreground">
        Loading contextual learning…
      </div>
    );
  }
  if (isError || !inner) {
    return null;
  }

  const hasContent = hints.length > 0 || links.length > 0 || roleRecs.length > 0;
  if (!hasContent && !user?.id) {
    return null;
  }

  if (!hasContent) {
    return (
      <section className="rounded-lg border border-teal-200/80 bg-teal-50/40 p-4 dark:border-teal-900/50 dark:bg-teal-950/30">
        <LearnerExternalIdentityLink variant="compact" />
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-teal-200/80 bg-teal-50/40 p-4 dark:border-teal-900/50 dark:bg-teal-950/30">
      <div className="mb-3 flex items-center gap-2">
        <Sparkles className="h-5 w-5 text-teal-600 dark:text-teal-400" />
        <div>
          <h3 className="text-sm font-semibold text-foreground dark:text-foreground">
            {props.title ?? "Learning & guidance"}
          </h3>
          <p className="text-xs text-muted-foreground dark:text-muted-foreground">
            Role-aware resources from Impilo Fundo and internal SOPs — CPD credit stays governed in Varapi.
          </p>
        </div>
      </div>

      {hints.length > 0 ? (
        <div className="mb-3 space-y-2">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Hints</p>
          <ul className="space-y-2">
            {hints.map((h, i) => {
              const hr = asRecord(h);
              const res = resourceFrom(hr?.resource);
              return (
                <li key={i} className="rounded-md border border-border bg-card/90 p-3 text-sm dark:border-border dark:bg-card/60">
                  <p className="font-medium text-foreground dark:text-foreground">
                    {typeof hr?.hintType === "string" ? hr.hintType : "Hint"}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground dark:text-muted-foreground">
                    {typeof hr?.textSummary === "string" ? hr.textSummary : ""}
                  </p>
                  {res?.experienceHref ? (
                    <div className="mt-2 flex flex-wrap gap-2">
                      <Link
                        href={res.experienceHref}
                        onClick={() => track(res.id)}
                        className="inline-flex items-center gap-1 text-xs font-medium text-teal-700 hover:underline dark:text-teal-300"
                      >
                        <BookOpen className="h-3.5 w-3.5" /> Open in Learning hub
                      </Link>
                      {res.fundoLaunchUrl ? (
                        <a
                          href={res.fundoLaunchUrl}
                          target="_blank"
                          rel="noreferrer"
                          onClick={() => track(res.id)}
                          className="inline-flex items-center gap-1 text-xs font-medium text-foreground hover:underline dark:text-foreground"
                        >
                          <ExternalLink className="h-3.5 w-3.5" /> Impilo Fundo
                        </a>
                      ) : null}
                    </div>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}

      {links.length > 0 ? (
        <div className="mb-3 space-y-2">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Workflow links</p>
          <ul className="space-y-2">
            {links.map((l, i) => {
              const lr = asRecord(l);
              const res = resourceFrom(lr?.resource);
              return (
                <li key={i} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-card/90 px-3 py-2 text-sm dark:border-border dark:bg-card/60">
                  <span className="text-foreground dark:text-foreground">{res?.title ?? "Resource"}</span>
                  <div className="flex gap-2">
                    {res?.experienceHref ? (
                      <Link
                        href={res.experienceHref}
                        onClick={() => track(res.id)}
                        className="text-xs font-medium text-teal-700 hover:underline dark:text-teal-300"
                      >
                        Hub
                      </Link>
                    ) : null}
                    {res?.fundoLaunchUrl ? (
                      <a
                        href={res.fundoLaunchUrl}
                        target="_blank"
                        rel="noreferrer"
                        onClick={() => track(res.id)}
                        className="text-xs font-medium text-foreground hover:underline dark:text-foreground"
                      >
                        Fundo
                      </a>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}

      {roleRecs.length > 0 ? (
        <div className="space-y-2">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">For your role</p>
          <ul className="space-y-2">
            {roleRecs.map((r, i) => {
              const rr = asRecord(r);
              const res = resourceFrom(rr?.resource);
              return (
                <li key={i} className="rounded-md border border-border bg-card/90 p-2 text-xs dark:border-border dark:bg-card/60">
                  <span className="font-semibold text-foreground dark:text-foreground">
                    {typeof rr?.requirementType === "string" ? rr.requirementType : "LEARNING"}
                  </span>
                  {res?.title ? <span className="ml-2 text-foreground dark:text-muted-foreground">· {res.title}</span> : null}
                  {res?.experienceHref ? (
                    <Link
                      href={res.experienceHref}
                      onClick={() => track(res.id)}
                      className="ml-2 font-medium text-teal-700 hover:underline dark:text-teal-300"
                    >
                      Open
                    </Link>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}

      {user?.id ? (
        <div className="mt-4 border-t border-teal-200/60 pt-3 dark:border-teal-900/40">
          <LearnerExternalIdentityLink variant="compact" />
        </div>
      ) : null}
    </section>
  );
}
