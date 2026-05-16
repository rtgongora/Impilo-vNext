/**
 * Citizen wellness — TanStack Query over BFF `/internal/v1/mobile/citizen/wellness/*`.
 * Uses Health ID (`useAuthStore` user.id) as `patientId`, matching mobile CPID usage.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchWellnessActivities,
  logWellnessActivity,
  fetchWellnessChallenges,
  joinWellnessChallenge,
} from "@/lib/citizen-wellness-api";

const qk = {
  activities: (patientId: string, days: number) => ["citizen-wellness", "activities", patientId, days] as const,
  challenges: () => ["citizen-wellness", "challenges"] as const,
};

export function useWellnessActivities(patientId: string | undefined, days = 14) {
  return useQuery({
    queryKey: qk.activities(patientId ?? "", days),
    queryFn: () => fetchWellnessActivities(patientId!, days),
    enabled: !!patientId,
  });
}

export function useLogWellnessActivity(patientId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: logWellnessActivity,
    onSuccess: async () => {
      if (patientId) {
        await qc.invalidateQueries({
          queryKey: qk.activities(patientId, 14),
        });
      }
      await qc.invalidateQueries({ queryKey: ["citizen-wellness", "activities"] });
    },
  });
}

export function useWellnessChallenges() {
  return useQuery({
    queryKey: qk.challenges(),
    queryFn: fetchWellnessChallenges,
  });
}

export function useJoinWellnessChallenge(patientId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ challengeId }: { challengeId: string }) => {
      if (!patientId) throw new Error("patientId required");
      return joinWellnessChallenge(challengeId, patientId);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.challenges() });
      await qc.invalidateQueries({ queryKey: ["citizen-wellness", "activities"] });
    },
  });
}
