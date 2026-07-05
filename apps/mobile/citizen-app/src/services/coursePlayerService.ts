import { apiClient } from "@impilo/mobile-api-client";

type AnyRecord = Record<string, unknown>;
const V11 = "/internal/v1/learning/v11";

/** Governed media asset view (LEARNING_RECORDING W4). */
export interface CourseMediaAsset {
  id: string;
  title?: string | null;
  mediaType?: string | null;
  storageKind?: string | null;
  durationSeconds?: number | null;
  courseId?: string | null;
  lessonId?: string | null;
  transcript?: string | null;
  transcriptAvailable?: boolean;
}

export interface CourseMediaPlayback {
  asset: CourseMediaAsset | null;
  playbackUrl: string | null;
  playbackUnavailableReason?: string | null;
}

export interface CourseWatchProgress {
  watchedSeconds: number;
  durationSeconds: number | null;
  percent: number;
  furthestPositionSeconds: number;
  lastPositionSeconds: number;
  completedAt: string | null;
  thresholdPercent: number;
}

export interface CourseMediaChapter {
  id: string;
  title: string;
  startSeconds: number;
  order: number;
}

export interface CourseVideoLesson {
  id: string;
  title: string;
}

/** VIDEO-type lessons of a course, in module/lesson sequence order. */
export async function fetchCourseVideoLessons(courseId: string): Promise<CourseVideoLesson[]> {
  const r = await apiClient.get<{ data: AnyRecord }>(
    `${V11}/courses/${encodeURIComponent(courseId)}/structure`,
  );
  const structure = (r.data.data?.structure ?? r.data.data ?? {}) as AnyRecord;
  const modules = Array.isArray(structure.modules) ? (structure.modules as AnyRecord[]) : [];
  return modules
    .flatMap((m) => (Array.isArray(m.lessons) ? (m.lessons as AnyRecord[]) : []))
    .filter((l) => String(l.contentType ?? "") === "VIDEO")
    .map((l) => ({ id: String(l.id ?? ""), title: String(l.title ?? "Video lesson") }))
    .filter((l) => l.id);
}

/** Newest governed media asset attached to a lesson (null when legacy-only). */
export async function fetchLessonMediaAsset(lessonId: string): Promise<CourseMediaAsset | null> {
  const r = await apiClient.get<{ data: { asset: CourseMediaAsset | null } }>(
    `${V11}/lessons/${encodeURIComponent(lessonId)}/media`,
  );
  return r.data.data?.asset ?? null;
}

/** Enrolment-gated signed playback URL for an asset. */
export async function fetchMediaPlayback(assetId: string, enrolmentId: string): Promise<CourseMediaPlayback> {
  const r = await apiClient.get<{ data: CourseMediaPlayback }>(
    `${V11}/media/${encodeURIComponent(assetId)}/playback?enrolmentId=${encodeURIComponent(enrolmentId)}`,
  );
  return r.data.data ?? { asset: null, playbackUrl: null };
}

export async function fetchWatchProgress(assetId: string, enrolmentId: string): Promise<CourseWatchProgress | null> {
  const r = await apiClient.get<{ data: { watchProgress: CourseWatchProgress } }>(
    `${V11}/media/${encodeURIComponent(assetId)}/watch-progress?enrolmentId=${encodeURIComponent(enrolmentId)}`,
  );
  return r.data.data?.watchProgress ?? null;
}

export async function upsertWatchProgress(
  assetId: string,
  body: { enrolmentId: string; positionSeconds: number; watchedSeconds: number; durationSeconds?: number },
): Promise<CourseWatchProgress | null> {
  const r = await apiClient.post<{ data: { watchProgress: CourseWatchProgress } }>(
    `${V11}/media/${encodeURIComponent(assetId)}/watch-progress`,
    body,
  );
  return r.data.data?.watchProgress ?? null;
}

export async function fetchMediaChapters(assetId: string): Promise<CourseMediaChapter[]> {
  const r = await apiClient.get<{ data: { items: CourseMediaChapter[] } }>(
    `${V11}/media/${encodeURIComponent(assetId)}/chapters`,
  );
  return r.data.data?.items ?? [];
}
