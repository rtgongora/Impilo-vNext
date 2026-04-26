/**
 * Experience UI - Marketplace Query Hooks
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface MarketplaceOrderApiResource {
  id: string;
  type: string;
  attributes: {
    facility_id: string;
    vendor_id?: string | null;
    order_number: string;
    status: string;
    total_amount?: number | string | null;
    currency?: string | null;
    items: string | Array<{ productId?: string; description?: string; quantity?: number; unitPrice?: number }>;
    ordered_by: string;
    ordered_at?: string | null;
    delivered_at?: string | null;
    created_at: string;
    updated_at?: string | null;
  };
}

interface MarketplaceCatalogApiResource {
  id: string;
  type: string;
  attributes: {
    name: string;
    description: string;
    category: string;
    facility_id?: string | null;
    facility_name?: string | null;
    price?: number | string | null;
    currency?: string | null;
    availability: string;
    rating?: number | string | null;
    image_url?: string | null;
  };
}

interface MarketplacePartnerApiResource {
  id: string;
  type: string;
  attributes: {
    name: string;
    facility_id?: string | null;
    service_count: number | string;
    active_listings: number | string;
    status: string;
    rating?: number | string | null;
  };
}

interface MarketplaceBookingApiResource {
  id: string;
  type: string;
  attributes: {
    booking_number?: string | null;
    service_name: string;
    provider_name: string;
    requested_at?: string | null;
    scheduled_at?: string | null;
    completed_at?: string | null;
    status: string;
  };
}

export interface MarketplaceOrderItem {
  productId: string;
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface MarketplaceOrder {
  id: string;
  facilityId: string;
  vendorId?: string | null;
  orderNumber: string;
  status: string;
  totalAmount: number;
  currency: string;
  items: MarketplaceOrderItem[];
  orderedBy: string;
  orderedAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
}

export interface MarketplaceCatalogItem {
  id: string;
  name: string;
  description: string;
  category: string;
  facilityId: string | null;
  facilityName: string;
  price: number;
  currency: string;
  availability: string;
  rating: number | null;
  imageUrl: string | null;
}

export interface MarketplacePartner {
  id: string;
  name: string;
  facilityId: string | null;
  serviceCount: number;
  activeListings: number;
  status: string;
  rating: number | null;
}

export interface MarketplaceBooking {
  id: string;
  bookingNumber: string;
  serviceName: string;
  providerName: string;
  requestedAt: string | null;
  scheduledAt: string | null;
  completedAt: string | null;
  status: string;
}

export interface CreateOrderPayload {
  facilityId: string;
  orderNumber: string;
  orderedBy: string;
  items: MarketplaceOrderItem[];
  totalAmount: number;
}

type MarketplaceOrdersResponse = ApiResponse<MarketplaceOrderApiResource[]>;
type MarketplaceOrderResponse = ApiResponse<MarketplaceOrderApiResource>;
type MarketplaceCatalogResponse = ApiResponse<MarketplaceCatalogApiResource[]>;
type MarketplacePartnersResponse = ApiResponse<MarketplacePartnerApiResource[]>;
type MarketplaceBookingsResponse = ApiResponse<MarketplaceBookingApiResource[]>;

function toNumber(value: number | string | null | undefined): number {
  if (typeof value === "number") return value;
  if (typeof value === "string" && value.trim()) return Number(value);
  return 0;
}

function parseItems(raw: MarketplaceOrderApiResource["attributes"]["items"]): MarketplaceOrderItem[] {
  const parsed = typeof raw === "string" ? JSON.parse(raw) as Array<Record<string, unknown>> : raw;
  return (parsed ?? []).map((item) => {
    const record = item as Record<string, unknown>;
    return {
      productId: String(record.productId ?? record.product_id ?? ""),
      description: String(record.description ?? ""),
      quantity: Number(record.quantity ?? 0),
      unitPrice: Number(record.unitPrice ?? record.unit_price ?? 0),
    };
  });
}

function normalizeOrder(resource: MarketplaceOrderApiResource): MarketplaceOrder {
  return {
    id: resource.id,
    facilityId: resource.attributes.facility_id,
    vendorId: resource.attributes.vendor_id ?? null,
    orderNumber: resource.attributes.order_number,
    status: resource.attributes.status,
    totalAmount: toNumber(resource.attributes.total_amount),
    currency: resource.attributes.currency ?? "USD",
    items: parseItems(resource.attributes.items),
    orderedBy: resource.attributes.ordered_by,
    orderedAt: resource.attributes.ordered_at ?? null,
    deliveredAt: resource.attributes.delivered_at ?? null,
    createdAt: resource.attributes.created_at,
  };
}

function normalizeCatalogItem(resource: MarketplaceCatalogApiResource): MarketplaceCatalogItem {
  return {
    id: resource.id,
    name: resource.attributes.name,
    description: resource.attributes.description,
    category: resource.attributes.category,
    facilityId: resource.attributes.facility_id ?? null,
    facilityName: resource.attributes.facility_name ?? "Marketplace partner",
    price: toNumber(resource.attributes.price),
    currency: resource.attributes.currency ?? "USD",
    availability: resource.attributes.availability,
    rating: resource.attributes.rating == null ? null : toNumber(resource.attributes.rating),
    imageUrl: resource.attributes.image_url ?? null,
  };
}

function normalizePartner(resource: MarketplacePartnerApiResource): MarketplacePartner {
  return {
    id: resource.id,
    name: resource.attributes.name,
    facilityId: resource.attributes.facility_id ?? null,
    serviceCount: toNumber(resource.attributes.service_count),
    activeListings: toNumber(resource.attributes.active_listings),
    status: resource.attributes.status,
    rating: resource.attributes.rating == null ? null : toNumber(resource.attributes.rating),
  };
}

function normalizeBooking(resource: MarketplaceBookingApiResource): MarketplaceBooking {
  return {
    id: resource.id,
    bookingNumber: resource.attributes.booking_number ?? resource.id.slice(0, 8).toUpperCase(),
    serviceName: resource.attributes.service_name,
    providerName: resource.attributes.provider_name,
    requestedAt: resource.attributes.requested_at ?? null,
    scheduledAt: resource.attributes.scheduled_at ?? null,
    completedAt: resource.attributes.completed_at ?? null,
    status: resource.attributes.status,
  };
}

export function useMarketplaceOrders(facilityId: string) {
  return useQuery<MarketplaceOrder[]>({
    queryKey: ["marketplace-orders", { facilityId }],
    queryFn: async () => {
      const response = await apiClient.get<MarketplaceOrdersResponse>(
        `/internal/v1/marketplace/orders?facility_id=${encodeURIComponent(facilityId)}`,
      );
      return (response.data ?? []).map(normalizeOrder);
    },
    enabled: !!facilityId,
  });
}

export function useMarketplaceOrder(id: string) {
  return useQuery<MarketplaceOrder>({
    queryKey: ["marketplace-orders", id],
    queryFn: async () => {
      const response = await apiClient.get<MarketplaceOrderResponse>(`/internal/v1/marketplace/orders/${id}`);
      return normalizeOrder(response.data);
    },
    enabled: !!id,
  });
}

export function useMarketplaceCatalog(search: string, category: string) {
  return useQuery<MarketplaceCatalogItem[]>({
    queryKey: ["marketplace-catalog", { search, category }],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (search) params.set("search", search);
      if (category && category !== "All") params.set("category", category);
      const query = params.toString();
      const response = await apiClient.get<MarketplaceCatalogResponse>(`/internal/v1/marketplace/catalog${query ? `?${query}` : ""}`);
      return (response.data ?? []).map(normalizeCatalogItem);
    },
  });
}

export function useMarketplacePartners() {
  return useQuery<MarketplacePartner[]>({
    queryKey: ["marketplace-partners"],
    queryFn: async () => {
      const response = await apiClient.get<MarketplacePartnersResponse>("/internal/v1/marketplace/vendors");
      return (response.data ?? []).map(normalizePartner);
    },
  });
}

export function useMarketplaceBookings() {
  return useQuery<MarketplaceBooking[]>({
    queryKey: ["marketplace-bookings"],
    queryFn: async () => {
      const response = await apiClient.get<MarketplaceBookingsResponse>("/internal/v1/marketplace/bookings");
      return (response.data ?? []).map(normalizeBooking);
    },
  });
}

export function useCreateOrder() {
  const queryClient = useQueryClient();

  return useMutation<MarketplaceOrder, unknown, CreateOrderPayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<MarketplaceOrderResponse>("/internal/v1/marketplace/orders", {
        facility_id: payload.facilityId,
        order_number: payload.orderNumber,
        items: JSON.stringify(payload.items),
        ordered_by: payload.orderedBy,
        total_amount: payload.totalAmount.toFixed(2),
      });
      return normalizeOrder(response.data);
    },
    onSuccess: (_data, payload) => {
      queryClient.invalidateQueries({ queryKey: ["marketplace-orders", { facilityId: payload.facilityId }] });
    },
  });
}
