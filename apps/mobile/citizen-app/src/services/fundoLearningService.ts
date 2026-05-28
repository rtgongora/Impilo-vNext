import * as SecureStore from "expo-secure-store";
import { apiClient } from "@impilo/mobile-api-client";
export { normalizeCitizenLearningSnapshot } from "./fundoLearningSummary";

type AnyRecord = Record<string, unknown>;
const V11 = "/internal/v1/learning/v11";

const CACHE_KEYS = {
  myLearning: (subjectType: string, subjectId: string) => `citizen:fundo:myLearning:${subjectType}:${subjectId}`,
  catalog: "citizen:fundo:catalog",
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
    // best-effort cache only
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

export async function fetchCitizenLearningSnapshot(subjectType: string, subjectId: string): Promise<AnyRecord> {
  return onlineFirst(CACHE_KEYS.myLearning(subjectType, subjectId), async () => {
    const r = await apiClient.get<{ data: AnyRecord }>(
      `${V11}/my-learning?subjectType=${encodeURIComponent(subjectType)}&subjectId=${encodeURIComponent(subjectId)}`,
    );
    return r.data.data ?? {};
  });
}

export async function fetchCitizenLearningCatalog(): Promise<Array<AnyRecord>> {
  return onlineFirst(CACHE_KEYS.catalog, async () => {
    const r = await apiClient.get<{ data: { items: Array<AnyRecord> } }>(
      `${V11}/catalog?status=PUBLISHED&limit=25`,
    );
    return r.data.data?.items ?? [];
  });
}
