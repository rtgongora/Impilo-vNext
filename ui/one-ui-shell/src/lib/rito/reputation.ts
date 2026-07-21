/**
 * Shared client types + helpers for the Rito Experience & Reputation surfaces (RW12-14).
 * These bind to the BFF persona lanes (RitoPersonaController) which proxy rito-quality-safety.
 */

/** The four non-blendable reputation domains (provider-reputation-doctrine §2). */
export const DOMAIN_LABELS: Record<string, string> = {
  CLIENT_EXPERIENCE: "Client experience",
  ACCESS_PROCESS: "Access & process",
  PROFESSIONAL_QUALITY: "Professional quality",
  SAFETY_ACCOUNTABILITY: "Safety & accountability",
};

export interface ManagementDomain {
  domain: string;
  meanScore: number | null;
  ratingCount: number;
  verifiedMeanScore: number | null;
  verifiedCount: number;
  distribution?: string | null;
}

export interface ManagementView {
  providerPublicId: string;
  reportingPeriod?: string | null;
  domains: ManagementDomain[];
  moderationStateCounts?: Record<string, number>;
}

export interface DomainScoreView {
  domain: string;
  measure: string;
  score: number | null;
  scale?: string | null;
}

export interface RatingSummary {
  id: string;
  providerPublicId: string;
  facilityId?: string | null;
  encounterRef?: string | null;
  providerRole?: string | null;
  modality?: string | null;
  specialty?: string | null;
  verifiedInteraction: boolean;
  respondentClass?: string | null;
  reportingPeriod?: string | null;
  overallScore?: number | null;
  moderationState: string;
  submittedAt?: string | null;
  domainScores?: DomainScoreView[];
  hasProviderResponse?: boolean;
}

/** BFF responses are pass-through JSON; some lanes wrap in `{ data }`, most do not. */
export function unwrapObject<T>(payload: unknown): T | null {
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (inner && typeof inner === "object" && !Array.isArray(inner)) return inner as T;
  }
  if (payload && typeof payload === "object" && !Array.isArray(payload)) return payload as T;
  return null;
}

export function unwrapList<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as T[];
  }
  return [];
}
