"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * Phase 6B — One Experience Shell read hooks for the native Impilo Fundo
 * catalogue. Each hook hits the experience-bff passthrough that forwards
 * to the Phase 5B `/internal/v1/learning/v11/...` endpoints in
 * `learning-service`.
 */
export interface FundoCourseSummary {
  id: string;
  code: string;
  title: string;
  description?: string | null;
  category?: string | null;
  level?: string | null;
  status: string;
  language?: string | null;
  estimatedDurationMinutes?: number | null;
  mandatory: boolean;
  cpdEligible: boolean;
  cpdPoints?: number | null;
}

export interface FundoCatalogPayload {
  limit: number;
  items: FundoCourseSummary[];
}

export interface FundoCatalogResponse {
  data: FundoCatalogPayload;
}

export interface FundoCourseLesson {
  id: string;
  title: string;
  contentType: string;
  contentBody?: string | null;
  contentRef?: string | null;
  estimatedDurationMinutes?: number | null;
  sequence: number;
  required: boolean;
  status: string;
}

export interface FundoCourseModule {
  id: string;
  title: string;
  description?: string | null;
  sequence: number;
  status: string;
  lessons: FundoCourseLesson[];
}

export type FundoCourseStructure = FundoCourseSummary & {
  modules: FundoCourseModule[];
};

export interface FundoCourseStructureResponse {
  data: { structure: FundoCourseStructure };
}

export interface FundoCatalogFilters {
  status?: string;
  category?: string;
  level?: string;
  cpdEligible?: boolean;
  mandatory?: boolean;
  language?: string;
  limit?: number;
}

function buildQuery(f: FundoCatalogFilters): string {
  const sp = new URLSearchParams();
  if (f.status) sp.set("status", f.status);
  if (f.category) sp.set("category", f.category);
  if (f.level) sp.set("level", f.level);
  if (f.cpdEligible !== undefined) sp.set("cpdEligible", String(f.cpdEligible));
  if (f.mandatory !== undefined) sp.set("mandatory", String(f.mandatory));
  if (f.language) sp.set("language", f.language);
  if (f.limit !== undefined) sp.set("limit", String(f.limit));
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function useFundoCatalog(filters: FundoCatalogFilters = {}) {
  return useQuery<FundoCatalogResponse>({
    queryKey: ["fundo", "catalog", filters],
    queryFn: () =>
      apiClient.get<FundoCatalogResponse>(
        `/internal/v1/learning/v11/catalog${buildQuery(filters)}`,
      ),
  });
}

export function useFundoCourseStructure(courseId: string | undefined) {
  return useQuery<FundoCourseStructureResponse>({
    queryKey: ["fundo", "course-structure", courseId],
    enabled: Boolean(courseId),
    queryFn: () =>
      apiClient.get<FundoCourseStructureResponse>(
        `/internal/v1/learning/v11/courses/${encodeURIComponent(courseId!)}/structure`,
      ),
  });
}
