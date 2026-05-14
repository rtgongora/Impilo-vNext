import * as SecureStore from "expo-secure-store";
import { apiClient } from "@impilo/mobile-api-client";

type AnyRecord = Record<string, unknown>;

const V11 = "/internal/v1/learning/v11";

const CACHE_KEYS = {
  myLearning: "provider:fundo:myLearning",
  catalog: "provider:fundo:catalog",
  course: (courseId: string) => `provider:fundo:course:${courseId}`,
};

async function readCache<T>(key: string): Promise<T | null> {
  try {
    const raw = await SecureStore.getItemAsync(key);
    if (!raw) return null;
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

async function writeCache(key: string, value: unknown) {
  try {
    await SecureStore.setItemAsync(key, JSON.stringify(value));
  } catch {
    // best effort cache only
  }
}

async function onlineFirst<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  try {
    const fresh = await fetcher();
    await writeCache(key, fresh);
    return fresh;
  } catch (err) {
    const cached = await readCache<T>(key);
    if (cached) return cached;
    throw err;
  }
}

export async function fetchMyLearning(subjectType: string, subjectId: string): Promise<AnyRecord> {
  return onlineFirst(CACHE_KEYS.myLearning, async () => {
    const r = await apiClient.get<{ data: AnyRecord }>(
      `${V11}/my-learning?subjectType=${encodeURIComponent(subjectType)}&subjectId=${encodeURIComponent(subjectId)}`,
    );
    return r.data.data ?? {};
  });
}

export async function fetchCatalog(): Promise<Array<AnyRecord>> {
  const key = CACHE_KEYS.catalog;
  return onlineFirst(key, async () => {
    const r = await apiClient.get<{ data: { items: Array<AnyRecord> } }>(`${V11}/catalog?status=PUBLISHED&limit=50`);
    return r.data.data?.items ?? [];
  });
}

export async function fetchCourseStructure(courseId: string): Promise<AnyRecord> {
  return onlineFirst(CACHE_KEYS.course(courseId), async () => {
    const r = await apiClient.get<{ data: { structure: AnyRecord } }>(`${V11}/courses/${encodeURIComponent(courseId)}/structure`);
    return r.data.data?.structure ?? {};
  });
}

export async function createEnrolment(body: AnyRecord): Promise<AnyRecord> {
  const r = await apiClient.post<{ data: { enrolment: AnyRecord } }>(`${V11}/enrolments`, body);
  return r.data.data?.enrolment ?? {};
}

export async function startEnrolment(enrolmentId: string): Promise<void> {
  await apiClient.post(`${V11}/enrolments/${encodeURIComponent(enrolmentId)}/start`, {});
}

export async function openLesson(lessonId: string, enrolmentId: string): Promise<void> {
  await apiClient.post(`${V11}/lessons/${encodeURIComponent(lessonId)}/open`, { enrolmentId });
}

export async function markLessonComplete(enrolmentId: string, lessonId: string): Promise<void> {
  await apiClient.post(`${V11}/progress`, { enrolmentId, lessonId, progressPercent: 100 });
}
