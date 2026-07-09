"use client";

import { useMemo, useState } from "react";
import { Trophy, Target, Loader2 } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useJoinChallenge, useWellnessChallenges } from "@/hooks/queries/useSimba";

const GRADIENTS = [
  "from-blue-500 to-cyan-500",
  "from-sky-500 to-blue-500",
  "from-orange-400 to-amber-500",
  "from-purple-500 to-indigo-500",
  "from-red-500 to-pink-500",
  "from-green-500 to-emerald-500",
];

function gradientForId(id: string): string {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h + id.charCodeAt(i) * (i + 1)) % GRADIENTS.length;
  return GRADIENTS[h] ?? GRADIENTS[0];
}

type ChallengeRow = {
  id: string;
  title: string;
  description?: string;
  targetValue?: number;
  targetUnit?: string;
  challengeType?: string;
  participantCount?: number;
  startDate?: string;
  endDate?: string;
};

/** Challenges — canonical Simba SOR via BFF proxy. */
export default function ChallengesPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const challengesQ = useWellnessChallenges();
  const join = useJoinChallenge();
  const [joinedLocal, setJoinedLocal] = useState<Set<string>>(new Set());
  const [joiningId, setJoiningId] = useState<string | null>(null);

  const challenges = useMemo(() => {
    const payload = challengesQ.data;
    const rows = Array.isArray(payload)
      ? (payload as Array<Record<string, unknown>>)
      : Array.isArray((payload as { data?: unknown })?.data)
        ? ((payload as { data: Array<Record<string, unknown>> }).data)
        : [];
    return rows.map((row) => ({
      id: String(row.challengeId ?? row.challenge_id ?? row.id ?? ""),
      title: String(row.title ?? "Challenge"),
      description: row.description ? String(row.description) : undefined,
      targetValue: row.targetValue != null ? Number(row.targetValue) : row.target_value != null ? Number(row.target_value) : undefined,
      targetUnit: String(row.unit ?? row.targetUnit ?? ""),
      challengeType: String(row.challengeType ?? row.challenge_type ?? ""),
      participantCount: Number(row.participantCount ?? row.participant_count ?? 0),
      startDate: String(row.startDate ?? row.start_date ?? ""),
      endDate: String(row.endDate ?? row.end_date ?? ""),
    })) satisfies ChallengeRow[];
  }, [challengesQ.data]);

  const onJoin = (challengeId: string) => {
    if (!patientId) return;
    setJoiningId(challengeId);
    setJoinedLocal((prev) => new Set(prev).add(challengeId));
    join.mutate(
      { id: challengeId, body: { person_cpid: patientId } },
      {
        onError: () => {
          setJoinedLocal((prev) => {
            const next = new Set(prev);
            next.delete(challengeId);
            return next;
          });
        },
        onSettled: () => setJoiningId(null),
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Challenges"
        subtitle="Join wellness challenges from Simba — national programme catalogue"
        icon={<Trophy className="h-6 w-6" />}
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        {!patientId && (
          <p className="text-sm text-warning-foreground bg-warning-soft border border-warning/35 rounded-lg px-4 py-3 mb-4">
            Sign in with your Health ID to join challenges.
          </p>
        )}

        {challengesQ.isLoading && (
          <div className="flex items-center gap-2 text-muted-foreground py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading challenges…
          </div>
        )}

        {challengesQ.isError && (
          <p className="text-sm text-danger bg-danger-soft border border-danger/28 rounded-lg px-4 py-3">
            {challengesQ.error instanceof Error ? challengesQ.error.message : "Could not load challenges."}
          </p>
        )}

        {!challengesQ.isLoading && !challengesQ.isError && challenges.length === 0 && (
          <p className="text-sm text-muted-foreground py-6">No active challenges in the directory yet. Check back soon.</p>
        )}

        {!challengesQ.isLoading && !challengesQ.isError && challenges.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {challenges.map((c) => {
              const g = gradientForId(c.id);
              const joined = joinedLocal.has(c.id);
              return (
                <GlassSurface key={c.id} className="overflow-hidden p-0 flex flex-col">
                  <div className={`bg-gradient-to-r ${g} p-4 text-white`}>
                    <h3 className="font-bold">{c.title}</h3>
                    {c.description && <p className="text-xs text-white/85 mt-1 line-clamp-3">{c.description}</p>}
                  </div>
                  <div className="p-4 flex-1 flex flex-col">
                    <p className="text-xs text-muted-foreground mb-2">
                      Target: {c.targetValue} {c.targetUnit} · {c.challengeType} · {c.participantCount} participants
                    </p>
                    <p className="text-xs text-muted-foreground mb-3">
                      {c.startDate?.slice(0, 10)} → {c.endDate?.slice(0, 10)}
                    </p>
                    <div className="mt-auto">
                      {joined ? (
                        <span className="text-sm font-medium text-primary">Joined</span>
                      ) : (
                        <button
                          type="button"
                          data-testid="wellness-challenge-join"
                          disabled={!patientId || joiningId !== null}
                          onClick={() => onJoin(c.id)}
                          className="inline-flex items-center gap-2 rounded-lg bg-primary text-white px-4 py-2 text-sm font-medium hover:bg-primary-hover disabled:opacity-50"
                        >
                          {joiningId === c.id && <Loader2 className="h-4 w-4 animate-spin" />}
                          Join challenge
                        </button>
                      )}
                    </div>
                  </div>
                </GlassSurface>
              );
            })}
          </div>
        )}

        {!challengesQ.isLoading && !challengesQ.isError && challenges.length > 0 && (
          <p className="text-xs text-muted-foreground mt-6 flex items-center gap-1">
            <Target className="h-3.5 w-3.5" />
            Progress tracking for challenges may be extended in a later slice; enrollment is live on Simba.
          </p>
        )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
