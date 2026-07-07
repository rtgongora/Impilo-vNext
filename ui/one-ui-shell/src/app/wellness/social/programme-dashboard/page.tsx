"use client";

import { BarChart3, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useProgrammeEngagement } from "@/hooks/queries/useSimbaSocial";

const METRICS: { key: string; label: string }[] = [
  { key: "groups", label: "Groups" },
  { key: "communities", label: "Communities" },
  { key: "active_members", label: "Active members" },
  { key: "posts", label: "Posts" },
  { key: "official_posts", label: "Official posts" },
  { key: "challenge_enrolment", label: "Challenge enrolment" },
  { key: "challenge_completions", label: "Challenge completions" },
  { key: "challenge_completion_pct", label: "Completion rate (%)" },
  { key: "reported_content", label: "Reported content" },
  { key: "moderation_backlog", label: "Moderation backlog" },
  { key: "safety_escalations", label: "Safety escalations" },
];

/**
 * Programme-engagement dashboard — aggregate-only (no per-person content). Gated to PROGRAMME_ADMIN
 * server-side; a non-eligible actor receives a 403 which surfaces as the access-denied state.
 */
export default function ProgrammeDashboardPage() {
  const engagementQ = useProgrammeEngagement();
  const data = engagementQ.data?.data as Record<string, unknown> | undefined;

  return (
    <AppLayout>
      <PageShell
        title="Programme engagement"
        subtitle="Aggregate wellness-social participation for your programme. No individual content is shown."
        icon={<BarChart3 className="h-6 w-6" />}
      >
        {engagementQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading metrics…
          </div>
        ) : engagementQ.isError ? (
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800">
            You do not have access to the programme-engagement dashboard, or no programme context is set.
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {METRICS.map((m) => (
              <div key={m.key} className="rounded-lg border border-border bg-card p-4">
                <p className="text-xs text-muted-foreground">{m.label}</p>
                <p className="mt-1 text-2xl font-semibold tabular-nums">
                  {data ? String(data[m.key] ?? 0) : "—"}
                </p>
              </div>
            ))}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
