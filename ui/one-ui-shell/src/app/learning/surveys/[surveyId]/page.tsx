"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useFundoInteractiveActivities } from "@/hooks/queries/useFundoStudio";

export default function LearningSurveyPage({ params }: { params: { surveyId: string } }) {
  const { data, isLoading } = useFundoInteractiveActivities(undefined, undefined, 50);
  const activities =
    (data?.data as { items?: Array<{ id?: string; title?: string; activityType?: string }> } | undefined)?.items ?? [];
  const survey = activities.find((item) => item.id === params.surveyId);

  return (
    <PageShell title="Learning Survey" subtitle="Survey details, purpose and response options.">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading survey…</p> : null}
      <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
        Survey id: <span className="font-mono">{params.surveyId}</span>
        {survey ? (
          <div className="mt-2">
            <div className="font-medium">{survey.title ?? 'Survey activity'}</div>
            <div className="text-muted-foreground">{survey.activityType ?? 'interactive'}</div>
          </div>
        ) : null}
        <div className="mt-2">
          <Link href={`/learning/surveys/${params.surveyId}/respond`} className="text-teal-700 hover:underline">
            Respond now
          </Link>
        </div>
      </div>
    </PageShell>
  );
}
