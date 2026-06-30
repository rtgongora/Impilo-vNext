/**
 * Msika storefront-lane query hooks (WS#4 — Health Marketplace).
 *
 * Buyer-facing storefront over the Experience BFF /internal/v1/marketplace/store/*
 * and seller-centre /internal/v1/marketplace/seller/* composition endpoints. The BFF
 * composes msika-service listings + msika-flow transaction tracking + Rito + Khuluma;
 * these hooks only read/post the composed envelopes. No fake data — sections that fail
 * upstream arrive as { status: "UNAVAILABLE" } and the UI surfaces that honestly.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface MsikaListing {
  listingId: string;
  title: string;
  summary?: string | null;
  riskClassification: string;
  clinicalRoute?: string | null;
  chargeRef?: string | null;
  priceDisplay?: string | null;
  fulfilmentMode?: string | null;
  fulfilmentOwner?: string | null;
  status: string;
  sellerType: string;
  sellerId: string;
  catalogItemId: string;
  publishedAt?: string | null;
}

interface BffEnvelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

/** The BFF wraps the upstream msika-service body as a JSON string or object per section. */
function unwrapListings(raw: unknown): MsikaListing[] {
  if (raw == null) return [];
  let body: unknown = raw;
  if (typeof raw === "string") {
    try {
      body = JSON.parse(raw);
    } catch {
      return [];
    }
  }
  // msika-service ApiResponse<PagedResponse<ListingView>> -> { data: { items: [...] } }
  const b = body as Record<string, unknown>;
  const inner = (b?.data ?? b) as Record<string, unknown>;
  const items = (inner?.items ?? inner?.content ?? inner) as unknown;
  return Array.isArray(items) ? (items as MsikaListing[]) : [];
}

function unwrapListing(raw: unknown): MsikaListing | null {
  if (raw == null) return null;
  let body: unknown = raw;
  if (typeof raw === "string") {
    try {
      body = JSON.parse(raw);
    } catch {
      return null;
    }
  }
  const b = body as Record<string, unknown>;
  const inner = (b?.data ?? b) as MsikaListing;
  return inner && (inner as MsikaListing).listingId ? inner : null;
}

export function useStoreHome() {
  return useQuery({
    queryKey: ["msika-store", "home"],
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
        "/internal/v1/marketplace/store/home",
      );
      const d = resp.data ?? {};
      return {
        featured: unwrapListings(d.featured),
        favourites: d.favourites,
        activity: d.activity,
      };
    },
  });
}

export function useStoreSearch(q: string, risk: string) {
  return useQuery({
    queryKey: ["msika-store", "search", q, risk],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (q) params.set("q", q);
      if (risk && risk !== "ALL") params.set("risk", risk);
      const qs = params.toString();
      const resp = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
        `/internal/v1/marketplace/store/search${qs ? `?${qs}` : ""}`,
      );
      return unwrapListings(resp.data?.listings);
    },
  });
}

export function useListing(listingId: string) {
  return useQuery({
    queryKey: ["msika-store", "listing", listingId],
    enabled: !!listingId,
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
        `/internal/v1/marketplace/store/listing/${listingId}`,
      );
      return unwrapListing(resp.data?.listing);
    },
  });
}

export function useStoreActivity() {
  return useQuery({
    queryKey: ["msika-store", "activity"],
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
        "/internal/v1/marketplace/store/activity",
      );
      return resp.data?.orders ?? null;
    },
  });
}

export function useFavouriteListing() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (listingId: string) => {
      const resp = await apiClient.put<BffEnvelope<Record<string, unknown>>>(
        `/internal/v1/marketplace/store/listing/${listingId}/favourite`,
        {},
      );
      return resp.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["msika-store", "home"] }),
  });
}

export function useReportListing() {
  return useMutation({
    mutationFn: async (body: { title: string; description: string; listingId?: string }) => {
      const resp = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
        "/internal/v1/marketplace/store/feedback",
        { caseType: "COMPLAINT", ...body },
      );
      return resp.data;
    },
  });
}

// ── Seller centre ───────────────────────────────────────────────────────────────────

export function useSellerListings(sellerType: string, sellerId: string) {
  return useQuery({
    queryKey: ["msika-seller", "listings", sellerType, sellerId],
    enabled: !!sellerType && !!sellerId,
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
        `/internal/v1/marketplace/seller/listings/${sellerType}/${sellerId}`,
      );
      return {
        listings: unwrapListings(resp.data?.listings),
        orders: resp.data?.orders,
      };
    },
  });
}

export function useModerationQueue() {
  return useQuery({
    queryKey: ["msika-seller", "moderation"],
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
        "/internal/v1/marketplace/seller/moderation/queue",
      );
      return unwrapListings(resp.data?.queue);
    },
  });
}

export function useCreateListing() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const resp = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
        "/internal/v1/marketplace/seller/listings",
        body,
      );
      return resp.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["msika-seller"] }),
  });
}

export function useListingDecision() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: { listingId: string; decision: "approve" | "reject" | "suspend"; notes?: string }) => {
      const resp = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
        `/internal/v1/marketplace/seller/moderation/${args.listingId}/${args.decision}`,
        { notes: args.notes ?? "" },
      );
      return resp.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["msika-seller", "moderation"] }),
  });
}
