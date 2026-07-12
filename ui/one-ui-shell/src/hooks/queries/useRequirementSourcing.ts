/**
 * HPA requirement → marketplace sourcing hooks.
 *
 * Bridges the regulatory checklist world (inspection modules / findings, keyed by
 * checklist item codes) to the Msika marketplace: which sourcing category serves a
 * requirement, how many published listings exist, and aggregated supplier-side
 * demand from open regulatory processes.
 *
 * All routes live under the experience-bff marketplace sourcing surface
 * (`/internal/v1/marketplace/sourcing/**` plus the seller storefront wholesaler-licence
 * verifier) and share the `{ data, meta }` envelope, mirroring the conventions in
 * `useHpaRegulatory.ts`.
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

const BASE = "/internal/v1/marketplace/sourcing";

/* ─── Types ─── */

export interface SourcingCategoryView {
  code: string;
  label: string;
  description: string | null;
  restricted: boolean;
  requiredCredential: string | null;
  publishedListingCount: number;
}

export interface SourcingCategoriesData {
  categories: SourcingCategoryView[];
}

/** Resolution of one checklist requirement code to a marketplace sourcing category. */
export interface RequirementSourcingResolution {
  requirementCode: string;
  categoryCode: string;
  categoryLabel: string;
  restricted: boolean;
  requiredCredential: string | null;
  publishedListingCount: number;
  marketplaceLink: string | null;
}

/** Keyed by requirement code; unmapped codes are absent by design. */
export interface RequirementSourcingResolutionsData {
  resolutions: Record<string, RequirementSourcingResolution>;
}

export interface SupplierDemandCategory {
  categoryCode: string;
  categoryLabel: string;
  restricted: boolean;
  openFindings: number;
  openComplianceActions: number;
  byProvince: Record<string, number>;
}

export interface SupplierDemandFacilityTypeApplications {
  facilityType: string;
  count: number;
}

export interface SupplierDemandData {
  generatedAt: string | null;
  smallCellFloor: number;
  categories: SupplierDemandCategory[];
  openApplicationsByFacilityType: SupplierDemandFacilityTypeApplications[];
}

export interface WholesalerLicenceVerificationPayload {
  storefrontId: string;
  verificationCode: string;
}

/* ─── Helpers ─── */

/**
 * Stable cache key for a set of requirement codes: de-duplicated, sorted, joined.
 * Guarantees `["A","B"]` and `["B","A","B"]` hit the same query cache entry.
 */
export function requirementCodesKey(codes: string[]): string {
  return [...new Set(codes)].sort().join("|");
}

/* ─── Hooks ─── */

export function useSourcingCategories() {
  return useQuery<ApiResponse<SourcingCategoriesData>>({
    queryKey: ["marketplace", "sourcing", "categories"],
    queryFn: () => apiClient.get<ApiResponse<SourcingCategoriesData>>(`${BASE}/categories`),
  });
}

/**
 * Batch-resolve checklist requirement codes to marketplace sourcing categories.
 * A POST used as a read (the code list can exceed URL limits); cached per stable
 * sorted code set. Unmapped codes are simply absent from `data.resolutions`.
 */
export function useResolveRequirementSourcing(codes: string[]) {
  const key = requirementCodesKey(codes);
  return useQuery<ApiResponse<RequirementSourcingResolutionsData>>({
    queryKey: ["marketplace", "sourcing", "resolve", key],
    queryFn: () =>
      apiClient.post<ApiResponse<RequirementSourcingResolutionsData>>(`${BASE}/requirements/resolve`, {
        codes: [...new Set(codes)].sort(),
      }),
    enabled: codes.length > 0,
  });
}

export function useSupplierDemand() {
  return useQuery<ApiResponse<SupplierDemandData>>({
    queryKey: ["marketplace", "sourcing", "supplier-demand"],
    queryFn: () => apiClient.get<ApiResponse<SupplierDemandData>>(`${BASE}/supplier-demand`),
  });
}

/**
 * Verify a wholesaler licence for a seller storefront. Success returns the
 * storefront passthrough; failure surfaces the BFF's 422 `{ error, message }` body.
 */
export function useVerifyWholesalerLicence() {
  return useMutation<ApiResponse<Record<string, unknown>>, unknown, WholesalerLicenceVerificationPayload>({
    mutationFn: ({ storefrontId, verificationCode }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/marketplace/seller/storefront/${encodeURIComponent(storefrontId)}/verify-wholesaler-licence`,
        { verificationCode },
      ),
  });
}
