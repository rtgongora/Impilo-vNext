"use client";

import { useState } from "react";
import { Trophy, Target, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWellnessChallenges, useJoinWellnessChallenge } from "@/hooks/queries/useCitizenWellness";

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

/** Challenges — enrolled via Experience BFF (same as citizen mobile). */
export default function ChallengesPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const { data: challenges = [], isLoading, isError, error } = useWellnessChallenges();
  const join = useJoinWellnessChallenge(patientId);
  const [joinedLocal, setJoinedLocal] = useState<Set<string>>(new Set());
  const [joiningId, setJoiningId] = useState<string | null>(null);

  const onJoin = (challengeId: string) => {
    if (!patientId) return;
    setJoiningId(challengeId);
    join.mutate(
      { challengeId },
      {
        onSuccess: () => {
          setJoinedLocal((prev) => new Set(prev).add(challengeId));
        },
        onSettled: () => setJoiningId(null),
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Challenges"
        subtitle="Join wellness challenges from your national programme — synced with mobile"
        icon={<Trophy className="h-6 w-6" />}
      >
        {!patientId && (
          <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 mb-4">
            Sign in with your Health ID to join challenges.
          </p>
        )}

        {isLoading && (
          <div className="flex items-center gap-2 text-gray-600 py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading challenges…
          </div>
        )}

        {isError && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-4 py-3">
            {error instanceof Error ? error.message : "Could not load challenges."}
          </p>
        )}

        {!isLoading && !isError && challenges.length === 0 && (
          <p className="text-sm text-gray-600 py-6">No active challenges in the directory yet. Check back soon.</p>
        )}

        {!isLoading && !isError && challenges.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {challenges.map((c) => {
              const g = gradientForId(c.id);
              const joined = joinedLocal.has(c.id);
              return (
                <div key={c.id} className="rounded-xl overflow-hidden shadow-sm border border-gray-200 bg-white flex flex-col">
                  <div className={`bg-gradient-to-r ${g} p-4 text-white`}>
                    <h3 className="font-bold">{c.title}</h3>
                    {c.description && <p className="text-xs text-white/85 mt-1 line-clamp-3">{c.description}</p>}
                  </div>
                  <div className="p-4 flex-1 flex flex-col">
                    <p className="text-xs text-gray-500 mb-2">
                      Target: {c.targetValue} {c.targetUnit} · {c.challengeType} · {c.participantCount} participants
                    </p>
                    <p className="text-xs text-gray-400 mb-3">
                      {c.startDate?.slice(0, 10)} → {c.endDate?.slice(0, 10)}
                    </p>
                    <div className="mt-auto">
                      {joined ? (
                        <span className="text-sm font-medium text-emerald-600">Joined</span>
                      ) : (
                        <button
                          type="button"
                          disabled={!patientId || joiningId !== null}
                          onClick={() => onJoin(c.id)}
                          className="inline-flex items-center gap-2 rounded-lg bg-impilo-500 text-white px-4 py-2 text-sm font-medium hover:bg-impilo-600 disabled:opacity-50"
                        >
                          {joiningId === c.id && <Loader2 className="h-4 w-4 animate-spin" />}
                          Join challenge
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {!isLoading && !isError && challenges.length > 0 && (
          <p className="text-xs text-gray-400 mt-6 flex items-center gap-1">
            <Target className="h-3.5 w-3.5" />
            Progress tracking for challenges may be extended in a later slice; enrollment is live.
          </p>
        )}
      </PageShell>
    </AppLayout>
  );
}
