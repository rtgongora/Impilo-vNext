"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { Loader2, Medal, Share2, Trophy } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useChallengeLeaderboard,
  useChallenges,
  useJoinChallenge,
  useShareChallengeToFeed,
  type LeaderboardRow,
  type SocialChallenge,
} from "@/hooks/queries/useSimbaSocial";

function asChallenges(payload: unknown): SocialChallenge[] {
  if (Array.isArray(payload)) return payload as SocialChallenge[];
  const data = (payload as { data?: unknown })?.data;
  return Array.isArray(data) ? (data as SocialChallenge[]) : [];
}

/** Challenge detail — join, share progress to the feed (sanitised), and view the ranked leaderboard. */
export default function SocialChallengeDetailPage() {
  const params = useParams<{ id: string }>();
  const challengeId = params?.id;
  const cpid = useAuthStore((s) => s.user?.id ?? null);
  const challengesQ = useChallenges();
  const leaderboardQ = useChallengeLeaderboard(challengeId);
  const join = useJoinChallenge();
  const share = useShareChallengeToFeed();
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);

  const challenge = useMemo(
    () => asChallenges(challengesQ.data).find((c) => c.challengeId === challengeId),
    [challengesQ.data, challengeId],
  );
  const rows: LeaderboardRow[] = leaderboardQ.data?.data ?? [];

  function doShare() {
    if (!challengeId) return;
    setError(null);
    share.mutate(
      { challengeId, message: message.trim() || undefined, visibility: "PUBLIC_WITHIN_IMPILO" },
      { onSuccess: () => setMessage(""), onError: (e) => setError(e.message) },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title={challenge?.title ?? "Challenge"}
        subtitle={challenge?.description ?? "Wellness challenge"}
        icon={<Trophy className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() => cpid && join.mutate({ challengeId: challengeId!, personCpid: cpid })}
              disabled={!cpid || join.isPending}
              className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
            >
              Join challenge
            </button>
          </div>

          <section className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold">
              <Share2 className="h-4 w-4" /> Share your progress
            </h3>
            <p className="mb-2 text-xs text-muted-foreground">
              Celebrate your milestone with the community. Clinical readings are never shared.
            </p>
            <textarea
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              rows={2}
              placeholder="e.g. Halfway to my goal — feeling great!"
              className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            />
            {error && <p className="mt-1 text-sm text-destructive">{error}</p>}
            <button
              onClick={doShare}
              disabled={share.isPending}
              className="mt-2 rounded-lg border border-border px-3 py-2 text-sm hover:bg-accent disabled:opacity-60"
            >
              {share.isPending ? "Sharing…" : "Share to feed"}
            </button>
          </section>

          <section>
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold">
              <Medal className="h-4 w-4" /> Leaderboard
            </h3>
            {leaderboardQ.isLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </div>
            ) : rows.length === 0 ? (
              <p className="text-sm text-muted-foreground">No participants yet — be the first!</p>
            ) : (
              <ol className="space-y-1">
                {rows.map((r) => (
                  <li
                    key={r.personCpid}
                    className={`flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm ${
                      r.personCpid === cpid ? "bg-primary/5" : "bg-card"
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <span className="w-6 text-right font-semibold">{r.rank}</span>
                      <span className="font-mono text-xs text-muted-foreground">
                        {r.personCpid === cpid ? "You" : r.personCpid}
                      </span>
                    </span>
                    <span className="tabular-nums">{r.progressValue}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
