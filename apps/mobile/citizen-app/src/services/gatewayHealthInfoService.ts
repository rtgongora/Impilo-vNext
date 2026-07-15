/**
 * Guest health-information service — the public gateway pillar (no sign-in).
 *
 * Uses the anonymous {@link publicApiClient} against the gateway's permitAll guidance
 * lane (governed public disclosure only, no PII, no personalization):
 *
 *   GET /internal/v1/public/gateway/guidance/education?q=&category=&page=&size=
 *   GET /internal/v1/public/gateway/guidance/education/categories
 *   GET /internal/v1/public/gateway/guidance/education/{id}
 *
 * Distinct from the authenticated /mobile/citizen/public-health lane (PublicHealthScreen).
 * Contract owner: experience-bff PublicGatewayGuidanceBffController → guidance-service.
 */
import { publicApiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/public/gateway/guidance/education";

export interface PublicEducationTopic {
  id: string;
  title: string;
  summary: string;
  category: string;
  domain?: string | null;
}

export interface PublicEducationPage {
  data: PublicEducationTopic[];
  meta: { page: number; size: number; totalElements: number; totalPages: number };
}

export interface PublicEducationCategory {
  category: string;
  count: number;
}

export interface PublicEducationArticle {
  id: string;
  title: string;
  summary: string;
  body: string;
  category: string;
  domain?: string | null;
  updatedAt?: string | null;
}

export interface EducationQuery {
  q?: string;
  category?: string;
  page?: number;
  size?: number;
}

const EMPTY_PAGE: PublicEducationPage = {
  data: [],
  meta: { page: 0, size: 0, totalElements: 0, totalPages: 0 },
};

/** Browse / search published public education topics. */
export async function fetchPublicEducation(query: EducationQuery = {}): Promise<PublicEducationPage> {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q.trim());
  if (query.category) params.set("category", query.category);
  params.set("page", String(query.page ?? 0));
  params.set("size", String(query.size ?? 10));

  const res = await publicApiClient.get<PublicEducationPage>(`${BASE}?${params.toString()}`);
  const body = res.data as PublicEducationPage | undefined;
  if (!body || !Array.isArray(body.data)) return EMPTY_PAGE;
  return {
    data: body.data,
    meta: body.meta ?? { page: query.page ?? 0, size: query.size ?? 10, totalElements: body.data.length, totalPages: 1 },
  };
}

/** Published-topic categories with counts (an enhancement — callers tolerate an empty list). */
export async function fetchPublicEducationCategories(): Promise<PublicEducationCategory[]> {
  const res = await publicApiClient.get<{ data: PublicEducationCategory[] }>(`${BASE}/categories`);
  return Array.isArray(res.data?.data) ? res.data.data : [];
}

/** Read one published article, including its plain-language body. */
export async function fetchPublicEducationArticle(id: string): Promise<PublicEducationArticle> {
  const res = await publicApiClient.get<{ data: PublicEducationArticle }>(
    `${BASE}/${encodeURIComponent(id)}`,
  );
  const article = (res.data?.data ?? res.data) as PublicEducationArticle;
  return article;
}
