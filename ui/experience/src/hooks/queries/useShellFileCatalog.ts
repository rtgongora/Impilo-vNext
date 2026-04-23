"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { FileResource, FileResourceType } from "@/lib/shell/types";

export interface ShellFileCatalogPayload {
  reportRuns: CatalogRow[];
  facilityCertificates: CatalogRow[];
}

interface CatalogRow {
  id: string;
  resourceType: string;
  title: string;
  associatedApp?: string;
  category?: string;
  createdAt?: string;
  href?: string;
  downloadAction?: string;
}

const KNOWN_TYPES: FileResourceType[] = [
  "generated_output",
  "registry_certificate",
  "personal_document",
  "clinical_document",
  "indexed_entity",
  "link",
  "other",
];

function asFileResource(row: CatalogRow): FileResource {
  const resourceType = KNOWN_TYPES.includes(row.resourceType as FileResourceType)
    ? (row.resourceType as FileResourceType)
    : "other";
  return {
    id: row.id,
    resourceType,
    title: row.title,
    associatedApp: row.associatedApp,
    category: row.category,
    createdAt: row.createdAt,
    href: row.href,
    downloadAction: row.downloadAction === "prepared_url" ? "prepared_url" : row.href ? "navigate" : "none",
  };
}

export function shellFileCatalogQueryKey(facilityId: string | undefined) {
  return ["shell", "file-catalog", facilityId ?? ""] as const;
}

export function useShellFileCatalog(facilityId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: shellFileCatalogQueryKey(facilityId),
    enabled,
    staleTime: 45_000,
    queryFn: async () => {
      const sp = new URLSearchParams();
      if (facilityId) sp.set("facility_id", facilityId);
      const q = sp.toString();
      const res = await apiClient.get<{ data: ShellFileCatalogPayload }>(
        `/internal/v1/shell/file-catalog${q ? `?${q}` : ""}`,
      );
      const data = res?.data;
      if (!data) return { reportRuns: [] as FileResource[], facilityCertificates: [] as FileResource[] };
      const reportRuns = (data.reportRuns ?? []).map(asFileResource);
      const facilityCertificates = (data.facilityCertificates ?? []).map(asFileResource);
      return { reportRuns, facilityCertificates };
    },
  });
}
