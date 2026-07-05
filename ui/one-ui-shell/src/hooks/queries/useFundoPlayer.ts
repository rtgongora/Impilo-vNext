"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type GenericData<T = unknown> = { data: T };
type AnyRecord = Record<string, unknown>;

/** Typed view of a Fundo media asset (LEARNING_RECORDING W4). */
export interface FundoMediaAsset {
  id: string;
  title?: string | null;
  mediaType?: string | null;
  status?: string | null;
  mimeType?: string | null;
  storageKind?: "DOCUMENT_OBJECT" | "EXTERNAL_URL" | null;
  storageRef?: string | null;
  recordingType?: string | null;
  durationSeconds?: number | null;
  courseId?: string | null;
  lessonId?: string | null;
  liveEventId?: string | null;
  unattached?: boolean;
  transcriptAvailable?: boolean;
  transcript?: string | null;
  captionRef?: string | null;
}

export interface FundoMediaPlayback {
  asset: FundoMediaAsset;
  playbackUrl: string | null;
  expiresAt?: string | null;
  playbackUnavailableReason?: string | null;
}

export interface FundoWatchProgress {
  id: string | null;
  enrolmentId: string;
  assetId: string;
  watchedSeconds: number;
  durationSeconds: number | null;
  percent: number;
  furthestPositionSeconds: number;
  lastPositionSeconds: number;
  completedAt: string | null;
  thresholdPercent: number;
}

export interface FundoMediaChapter {
  id: string;
  assetId: string;
  title: string;
  startSeconds: number;
  order: number;
}

export interface FundoMediaBookmark {
  id: string;
  enrolmentId: string;
  assetId: string;
  positionSeconds: number;
  note: string | null;
  createdAt?: string | null;
}

const BASE = "/internal/v1/learning/v11";

/** Newest media asset attached to a lesson (null when the lesson has none). */
export function useLessonMedia(lessonId?: string) {
  return useQuery<GenericData<{ lessonId: string; asset: FundoMediaAsset | null }>>({
    queryKey: ["fundo", "lesson-media", lessonId],
    enabled: Boolean(lessonId),
    queryFn: () => apiClient.get(`${BASE}/lessons/${encodeURIComponent(lessonId!)}/media`),
  });
}

/** Enrolment-gated playback URL (signed per-request for DOCUMENT_OBJECT assets). */
export function useMediaPlayback(assetId?: string, enrolmentId?: string) {
  return useQuery<GenericData<FundoMediaPlayback>>({
    queryKey: ["fundo", "media-playback", assetId, enrolmentId],
    enabled: Boolean(assetId && enrolmentId),
    // Signed URLs are time-limited — refetch on remount rather than caching stale URLs.
    staleTime: 60_000,
    queryFn: () =>
      apiClient.get(
        `${BASE}/media/${encodeURIComponent(assetId!)}/playback?enrolmentId=${encodeURIComponent(enrolmentId!)}`,
      ),
  });
}

export function useMediaWatchProgress(assetId?: string, enrolmentId?: string) {
  return useQuery<GenericData<{ watchProgress: FundoWatchProgress }>>({
    queryKey: ["fundo", "media-watch-progress", assetId, enrolmentId],
    enabled: Boolean(assetId && enrolmentId),
    queryFn: () =>
      apiClient.get(
        `${BASE}/media/${encodeURIComponent(assetId!)}/watch-progress?enrolmentId=${encodeURIComponent(enrolmentId!)}`,
      ),
  });
}

export function useUpsertWatchProgress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ assetId, body }: { assetId: string; body: AnyRecord }) =>
      apiClient.post<GenericData<{ watchProgress: FundoWatchProgress }>>(
        `${BASE}/media/${encodeURIComponent(assetId)}/watch-progress`,
        body,
      ),
    onSuccess: (_data, { assetId, body }) => {
      void queryClient.invalidateQueries({
        queryKey: ["fundo", "media-watch-progress", assetId, String(body.enrolmentId ?? "")],
      });
    },
  });
}

export function useMediaChapters(assetId?: string) {
  return useQuery<GenericData<{ assetId: string; items: FundoMediaChapter[] }>>({
    queryKey: ["fundo", "media-chapters", assetId],
    enabled: Boolean(assetId),
    queryFn: () => apiClient.get(`${BASE}/media/${encodeURIComponent(assetId!)}/chapters`),
  });
}

export function useMediaBookmarks(assetId?: string, enrolmentId?: string) {
  return useQuery<GenericData<{ assetId: string; enrolmentId: string; items: FundoMediaBookmark[] }>>({
    queryKey: ["fundo", "media-bookmarks", assetId, enrolmentId],
    enabled: Boolean(assetId && enrolmentId),
    queryFn: () =>
      apiClient.get(
        `${BASE}/media/${encodeURIComponent(assetId!)}/bookmarks?enrolmentId=${encodeURIComponent(enrolmentId!)}`,
      ),
  });
}

export function useCreateMediaBookmark() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ assetId, body }: { assetId: string; body: AnyRecord }) =>
      apiClient.post(`${BASE}/media/${encodeURIComponent(assetId)}/bookmarks`, body),
    onSuccess: (_data, { assetId, body }) => {
      void queryClient.invalidateQueries({
        queryKey: ["fundo", "media-bookmarks", assetId, String(body.enrolmentId ?? "")],
      });
    },
  });
}

export function useDeleteMediaBookmark() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bookmarkId, enrolmentId }: { bookmarkId: string; enrolmentId: string; assetId: string }) =>
      apiClient.delete(
        `${BASE}/media/bookmarks/${encodeURIComponent(bookmarkId)}?enrolmentId=${encodeURIComponent(enrolmentId)}`,
      ),
    onSuccess: (_data, { assetId, enrolmentId }) => {
      void queryClient.invalidateQueries({
        queryKey: ["fundo", "media-bookmarks", assetId, enrolmentId],
      });
    },
  });
}

/** Completion rules for a course — the QuizInterrupt only mounts when QUIZ_REQUIRED exists. */
export function useCourseCompletionRules(courseId?: string) {
  return useQuery<GenericData<{ courseId: string; items: AnyRecord[] }>>({
    queryKey: ["fundo", "completion-rules", courseId],
    enabled: Boolean(courseId),
    queryFn: () => apiClient.get(`${BASE}/courses/${encodeURIComponent(courseId!)}/completion-rules`),
  });
}
