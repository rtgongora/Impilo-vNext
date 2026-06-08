import { apiClient } from "@impilo/mobile-api-client";

export type ClientRegistrySummary = {
  healthId: string;
  displayName: string;
  lifecycleStatus?: string;
  verificationStatus?: string;
  openMatches?: number;
};

export type DedupScoreResult = {
  score: number;
  conflictingFields?: string[];
};

function rows(value: unknown): ClientRegistrySummary[] {
  if (Array.isArray(value)) return value as ClientRegistrySummary[];
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    if (Array.isArray(record.items)) return record.items as ClientRegistrySummary[];
    if (Array.isArray(record.data)) return rows(record.data);
  }
  return [];
}

function unwrap<T>(value: unknown): T {
  if (value && typeof value === "object" && "data" in value) {
    return (value as { data: T }).data;
  }
  return value as T;
}

export async function searchClientRegistry(query: string): Promise<ClientRegistrySummary[]> {
  const response = await apiClient.get(
    `/internal/v1/client-registry/clients?query=${encodeURIComponent(query)}&page=0&size=5`,
  );
  const payload = unwrap<unknown>(response.data);
  return rows(payload);
}

export async function scoreClientDedup(sourceHealthId: string, targetHealthId: string): Promise<DedupScoreResult> {
  const response = await apiClient.post("/internal/v1/vito/dedup/score", {
    sourceHealthId,
    targetHealthId,
  });
  return unwrap<DedupScoreResult>(response.data);
}

export function dedupStatusLabel(score: number | null, matchCount: number): { label: string; tone: "clear" | "review" | "duplicate" } {
  if (score != null && score >= 0.85) return { label: "Likely duplicate", tone: "duplicate" };
  if (matchCount > 0 || (score != null && score >= 0.6)) return { label: "Review matches", tone: "review" };
  return { label: "No strong match", tone: "clear" };
}
