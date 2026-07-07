"use client";

import { useMemo } from "react";
import Link from "next/link";
import { ChevronRight, Loader2, Trophy } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useChallenges, type SocialChallenge } from "@/hooks/queries/useSimbaSocial";

function asChallenges(payload: unknown): SocialChallenge[] {
  if (Array.isArray(payload)) return payload as SocialChallenge[];
  const data = (payload as { data?: unknown })?.data;
  return Array.isArray(data) ? (data as SocialChallenge[]) : [];
}

/** Wellness-social challenges — join, track progress, share milestones, climb the leaderboard. */
export default function SocialChallengesPage() {
  const challengesQ = useChallenges();
  const challenges = useMemo(() => asChallenges(challengesQ.data), [challengesQ.data]);

  return (
    <AppLayout>
      <PageShell
        title="Challenges"
        subtitle="Take on a wellness challenge, share your progress, and cheer each other on."
        icon={<Trophy className="h-6 w-6" />}
      >
        {challengesQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading challenges…
          </div>
        ) : challenges.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-8 text-center text-muted-foreground">
            No active challenges right now.
          </div>
        ) : (
          <ul className="space-y-2">
            {challenges.map((c) => (
              <li key={c.challengeId} className="rounded-lg border border-border bg-card p-4">
                <Link
                  href={`/wellness/social/challenges/${c.challengeId}`}
                  className="flex items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{c.title}</span>
                      {c.campaignFlag && (
                        <span className="rounded bg-primary/10 px-1.5 py-0.5 text-xs text-primary">
                          Campaign
                        </span>
                      )}
                    </div>
                    {c.description && (
                      <p className="truncate text-sm text-muted-foreground">{c.description}</p>
                    )}
                    <p className="mt-1 text-xs text-muted-foreground">
                      {c.challengeType}
                      {c.targetValue ? ` · target ${c.targetValue} ${c.unit ?? ""}` : ""}
                    </p>
                  </div>
                  <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
