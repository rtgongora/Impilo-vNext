/**
 * Marketplace Flow Tests — Service discovery, booking, order tracking, cancellation.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
};

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
  fetchPage: vi.fn(),
}));

import {
  fetchServices,
  fetchServiceDetail,
  requestService,
  fetchServiceRequests,
  fetchServiceRequest,
  cancelServiceRequest,
} from "../../services/marketplaceService";

import { fetchFeed, fetchFeedItem, likeFeedItem, unlikeFeedItem } from "../../services/feedService";

describe("Marketplace Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches available services with category filter", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "svc-1",
            name: "Lab Testing",
            description: "Comprehensive blood work",
            category: "DIAGNOSTICS",
            facilityName: "Harare Central Lab",
            price: 25.0,
            currency: "USD",
            available: true,
            rating: 4.5,
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchServices({ category: "DIAGNOSTICS" });
    expect(result.items).toHaveLength(1);
    expect(result.items[0].name).toBe("Lab Testing");
    expect(result.items[0].price).toBe(25.0);
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/marketplace/services?category=DIAGNOSTICS"
    );
  });

  it("searches services by keyword", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [{ id: "svc-2", name: "X-Ray", category: "IMAGING" }],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchServices({ search: "x-ray" });
    expect(result.items).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith(
      expect.stringContaining("search=x-ray")
    );
  });

  it("fetches service detail", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "svc-1",
          name: "Lab Testing",
          description: "Comprehensive blood work",
          category: "DIAGNOSTICS",
          facilityName: "Harare Central Lab",
          price: 25.0,
          currency: "USD",
          available: true,
        },
      },
    });

    const svc = await fetchServiceDetail("svc-1");
    expect(svc.name).toBe("Lab Testing");
    expect(svc.category).toBe("DIAGNOSTICS");
  });

  it("requests a service", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "req-1",
          serviceId: "svc-1",
          serviceName: "Lab Testing",
          facilityName: "Harare Central Lab",
          status: "PENDING",
          requestedAt: "2026-03-15T12:00:00Z",
        },
      },
    });

    const req = await requestService({
      serviceId: "svc-1",
      preferredDate: "2026-03-20",
      notes: "Morning preferred",
    });
    expect(req.status).toBe("PENDING");
    expect(req.serviceName).toBe("Lab Testing");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/marketplace/requests",
      { serviceId: "svc-1", preferredDate: "2026-03-20", notes: "Morning preferred" }
    );
  });

  it("fetches service requests with status filter", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          { id: "req-1", serviceName: "Lab Testing", status: "PENDING" },
          { id: "req-2", serviceName: "X-Ray", status: "PENDING" },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 2, total_pages: 1 } },
      },
    });

    const result = await fetchServiceRequests({ status: "PENDING" });
    expect(result.items).toHaveLength(2);
    expect(result.totalElements).toBe(2);
  });

  it("fetches a single service request", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "req-1",
          serviceName: "Lab Testing",
          status: "CONFIRMED",
          trackingNumber: "TRK-001",
        },
      },
    });

    const req = await fetchServiceRequest("req-1");
    expect(req.trackingNumber).toBe("TRK-001");
  });

  it("cancels a service request", async () => {
    mockApiClient.post.mockResolvedValue({});

    await cancelServiceRequest("req-1", "Changed plans");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/marketplace/requests/req-1/cancel",
      { reason: "Changed plans" }
    );
  });
});

describe("Feed Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches social feed", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "feed-1",
            type: "HEALTH_TIP",
            title: "Stay Hydrated",
            body: "Drink at least 8 glasses of water daily",
            author: "MoHCC",
            likesCount: 42,
            commentsCount: 5,
            liked: false,
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchFeed({ category: "HEALTH_TIP" });
    expect(result.items).toHaveLength(1);
    expect(result.items[0].title).toBe("Stay Hydrated");
    expect(result.items[0].liked).toBe(false);
  });

  it("fetches a single feed item", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "feed-1",
          type: "HEALTH_TIP",
          title: "Stay Hydrated",
          body: "Drink at least 8 glasses of water daily",
          likesCount: 42,
        },
      },
    });

    const item = await fetchFeedItem("feed-1");
    expect(item.title).toBe("Stay Hydrated");
  });

  it("likes a feed item", async () => {
    mockApiClient.post.mockResolvedValue({});

    await likeFeedItem("feed-1");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/feed/feed-1/like"
    );
  });

  it("unlikes a feed item", async () => {
    mockApiClient.delete.mockResolvedValue({});

    await unlikeFeedItem("feed-1");
    expect(mockApiClient.delete).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/feed/feed-1/like"
    );
  });
});
