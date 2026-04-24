"use client";

import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ExternalLink, GraduationCap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningResourceDetail } from "@/hooks/queries/useLearningSubjectCompletions";
import { recordLearningResourceOpened } from "@/lib/learning-resource-opened";
import { LearnerExternalIdentityLink } from "@/components/learning/LearnerExternalIdentityLink";

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

export default function LearningHubPage() {
  const sp = useSearchParams();
  const focus = sp.get("focus");
  const { data, isLoading } = useLearningResourceDetail(focus ?? undefined);

  const root = asRecord(data);
  const inner = root ? asRecord(root.data) : null;
  const title = inner && typeof inner.title === "string" ? inner.title : null;
  const desc = inner && typeof inner.description === "string" ? inner.description : null;
  const fundo = inner && typeof inner.fundoLaunchUrl === "string" ? inner.fundoLaunchUrl : null;
  const hub = inner && typeof inner.experienceHref === "string" ? inner.experienceHref : null;
  const rid = focus ?? undefined;

  return (
    <AppLayout>
      <PageShell
        title="Learning hub"
        subtitle="Impilo Fundo, SOPs, and role-aware paths — surfaced across workflows and helpdesk."
        icon={<GraduationCap className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/home" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Back to Home
          </Link>
        </div>
        <div className="mb-6">
          <LearnerExternalIdentityLink />
        </div>
        <div className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-700">
          <p>
            Use shell search (Ctrl+K) to find catalogued learning resources, or open contextual panels on registry intake,
            credentials, marketplace cart, and support screens.
          </p>
          {focus ? (
            <div className="mt-4 space-y-3">
              <p className="font-mono text-xs text-gray-500">
                Resource id: <span className="text-gray-800">{focus}</span>
              </p>
              {isLoading ? <p className="text-gray-500">Loading resource…</p> : null}
              {!isLoading && title ? (
                <div>
                  <h2 className="text-base font-semibold text-gray-900">{title}</h2>
                  {desc ? <p className="mt-1 text-sm text-gray-600">{desc}</p> : null}
                  <div className="mt-3 flex flex-wrap gap-3">
                    {hub ? (
                      <Link href={hub} className="text-sm font-medium text-teal-700 hover:underline">
                        Open in hub route
                      </Link>
                    ) : null}
                    {fundo ? (
                      <a
                        href={fundo}
                        target="_blank"
                        rel="noreferrer"
                        onClick={() => rid && recordLearningResourceOpened(rid, { surface: "learning_hub" })}
                        className="inline-flex items-center gap-1 text-sm font-medium text-gray-800 hover:underline"
                      >
                        <ExternalLink className="h-4 w-4" /> Impilo Fundo
                      </a>
                    ) : null}
                  </div>
                </div>
              ) : null}
              {!isLoading && !title ? (
                <p className="text-sm text-amber-800">
                  Resource not found or learning-service unavailable — check tenant headers and BFF configuration.
                </p>
              ) : null}
            </div>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
