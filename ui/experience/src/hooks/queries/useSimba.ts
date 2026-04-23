/**
 * Experience UI — SIMBA wellness (Clinical Plane) Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type WellnessProfileResponse = ApiResponse<unknown>;
type WellnessListResponse = ApiResponse<unknown>;
type WellnessGoalResponse = ApiResponse<unknown>;
type WellnessClubResponse = ApiResponse<unknown>;
type WellnessChallengeResponse = ApiResponse<unknown>;

function withPersonCpidQuery(path: string, cpid?: string | null) {
  const searchParams = new URLSearchParams();
  if (cpid) searchParams.set("person_cpid", cpid);
  const qs = searchParams.toString();
  return `${path}${qs ? `?${qs}` : ""}`;
}

export function useWellnessProfile(cpid?: string | null) {
  return useQuery<WellnessProfileResponse>({
    queryKey: ["wellness-profile", cpid],
    queryFn: () =>
      apiClient.get<WellnessProfileResponse>(`/internal/v1/wellness/profiles/${cpid}`),
    enabled: !!cpid,
  });
}

export function useActivities(cpid?: string | null) {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-activities", cpid ?? null],
    queryFn: () =>
      apiClient.get<WellnessListResponse>(withPersonCpidQuery("/internal/v1/wellness/activities", cpid)),
    enabled: !!cpid,
  });
}

export function useSleepSegments(cpid?: string | null) {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-sleep", cpid ?? null],
    queryFn: () =>
      apiClient.get<WellnessListResponse>(withPersonCpidQuery("/internal/v1/wellness/sleep", cpid)),
    enabled: !!cpid,
  });
}

export function useExerciseSessions(cpid?: string | null) {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-exercise", cpid ?? null],
    queryFn: () =>
      apiClient.get<WellnessListResponse>(withPersonCpidQuery("/internal/v1/wellness/exercise", cpid)),
  });
}

export function useDietEntries(cpid?: string | null) {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-diet", cpid ?? null],
    queryFn: () =>
      apiClient.get<WellnessListResponse>(withPersonCpidQuery("/internal/v1/wellness/diet", cpid)),
    enabled: !!cpid,
  });
}

export function useMoodLog(cpid?: string | null) {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-mood", cpid ?? null],
    queryFn: () =>
      apiClient.get<WellnessListResponse>(withPersonCpidQuery("/internal/v1/wellness/mood", cpid)),
    enabled: !!cpid,
  });
}

export function useWellnessGoals(cpid?: string | null) {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-goals", cpid ?? null],
    queryFn: () =>
      apiClient.get<WellnessListResponse>(withPersonCpidQuery("/internal/v1/wellness/goals", cpid)),
    enabled: !!cpid,
  });
}

export function useWellnessClubs() {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-clubs"],
    queryFn: () => apiClient.get<WellnessListResponse>("/internal/v1/wellness/clubs"),
  });
}

export function useWellnessChallenges() {
  return useQuery<WellnessListResponse>({
    queryKey: ["wellness-challenges"],
    queryFn: () => apiClient.get<WellnessListResponse>("/internal/v1/wellness/challenges"),
  });
}

export function useRecordActivity() {
  const queryClient = useQueryClient();
  return useMutation<WellnessListResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<WellnessListResponse>("/internal/v1/wellness/activities", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-activities"] });
    },
  });
}

export function useRecordSleep() {
  const queryClient = useQueryClient();
  return useMutation<WellnessListResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) => apiClient.post<WellnessListResponse>("/internal/v1/wellness/sleep", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-sleep"] });
    },
  });
}

export function useRecordExercise() {
  const queryClient = useQueryClient();
  return useMutation<WellnessListResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<WellnessListResponse>("/internal/v1/wellness/exercise", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-exercise"] });
    },
  });
}

export function useRecordDietEntry() {
  const queryClient = useQueryClient();
  return useMutation<WellnessListResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) => apiClient.post<WellnessListResponse>("/internal/v1/wellness/diet", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-diet"] });
    },
  });
}

export function useRecordMood() {
  const queryClient = useQueryClient();
  return useMutation<WellnessListResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) => apiClient.post<WellnessListResponse>("/internal/v1/wellness/mood", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-mood"] });
    },
  });
}

export function useCreateGoal() {
  const queryClient = useQueryClient();
  return useMutation<WellnessGoalResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) => apiClient.post<WellnessGoalResponse>("/internal/v1/wellness/goals", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-goals"] });
    },
  });
}

export function useUpdateGoalProgress() {
  const queryClient = useQueryClient();
  return useMutation<WellnessGoalResponse, unknown, { id: string | number; body: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.put<WellnessGoalResponse>(`/internal/v1/wellness/goals/${id}/progress`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-goals"] });
    },
  });
}

export function useJoinClub() {
  const queryClient = useQueryClient();
  return useMutation<WellnessClubResponse, unknown, { id: string | number; body?: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.post<WellnessClubResponse>(`/internal/v1/wellness/clubs/${id}/join`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-clubs"] });
    },
  });
}

export function useLeaveClub() {
  const queryClient = useQueryClient();
  return useMutation<WellnessClubResponse, unknown, { id: string | number }>({
    mutationFn: ({ id }) =>
      apiClient.delete<WellnessClubResponse>(`/internal/v1/wellness/clubs/${id}/leave`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-clubs"] });
    },
  });
}

export function useJoinChallenge() {
  const queryClient = useQueryClient();
  return useMutation<WellnessChallengeResponse, unknown, { id: string | number; body?: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.post<WellnessChallengeResponse>(
        `/internal/v1/wellness/challenges/${id}/join`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-challenges"] });
    },
  });
}

export function useUpdateChallengeProgress() {
  const queryClient = useQueryClient();
  return useMutation<WellnessChallengeResponse, unknown, { id: string | number; body: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.put<WellnessChallengeResponse>(
        `/internal/v1/wellness/challenges/${id}/progress`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wellness-challenges"] });
    },
  });
}
