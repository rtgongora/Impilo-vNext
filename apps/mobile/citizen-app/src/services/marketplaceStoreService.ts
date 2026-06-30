/**
 * Msika Health Marketplace — mobile service (WS#4).
 *
 * Reads the Experience BFF storefront composition (MarketplaceStorefrontController),
 * which composes msika-service listings + msika-flow transaction tracking + Rito
 * (feedback) + Khuluma (updates). The mobile app owns no marketplace data — this
 * mirrors the web useMsikaStore hooks so web↔mobile stay in parity.
 *
 * Owners are never duplicated: orders/fulfilment -> msika-flow, billing -> Costa,
 * payment -> MusheX, stock -> Dura/inventory, clinical -> PCT/OROS.
 */
import { apiClient } from "@impilo/mobile-api-client";

export interface MarketplaceListing {
  listingId: string;
  title: string;
  summary?: string | null;
  riskClassification: string;
  clinicalRoute?: string | null;
  priceDisplay?: string | null;
  fulfilmentMode?: string | null;
  status: string;
  sellerType: string;
  sellerId: string;
  catalogItemId: string;
}

interface BffEnvelope {
  data?: Record<string, unknown>;
}

function unwrapListings(raw: unknown): MarketplaceListing[] {
  if (raw == null) return [];
  let body: unknown = raw;
  if (typeof raw === "string") {
    try {
      body = JSON.parse(raw);
    } catch {
      return [];
    }
  }
  const b = body as Record<string, unknown>;
  const inner = (b?.data ?? b) as Record<string, unknown>;
  const items = (inner?.items ?? inner?.content ?? inner) as unknown;
  return Array.isArray(items) ? (items as MarketplaceListing[]) : [];
}

export interface MarketplaceHome {
  featured: MarketplaceListing[];
  activityUnavailable: boolean;
}

export async function fetchMarketplaceHome(): Promise<MarketplaceHome> {
  const response = await apiClient.get<BffEnvelope>("/internal/v1/marketplace/store/home");
  const d = response.data?.data ?? {};
  const activity = d.activity as Record<string, unknown> | undefined;
  return {
    featured: unwrapListings(d.featured),
    activityUnavailable: !!activity && activity.status === "UNAVAILABLE",
  };
}

export async function searchMarketplace(q: string, risk?: string): Promise<MarketplaceListing[]> {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (risk && risk !== "ALL") params.set("risk", risk);
  const qs = params.toString();
  const response = await apiClient.get<BffEnvelope>(
    `/internal/v1/marketplace/store/search${qs ? `?${qs}` : ""}`,
  );
  return unwrapListings(response.data?.data?.listings);
}

export async function reportListing(body: {
  title: string;
  description: string;
  listingId?: string;
}): Promise<unknown> {
  const response = await apiClient.post<BffEnvelope>("/internal/v1/marketplace/store/feedback", {
    caseType: "COMPLAINT",
    ...body,
  });
  return response.data;
}
