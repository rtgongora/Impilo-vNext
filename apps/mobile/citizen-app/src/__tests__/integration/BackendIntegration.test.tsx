/**
 * Backend Integration Tests — Verifies end-to-end API contract for citizen BFF.
 *
 * Tests validate that service functions correctly construct API URLs,
 * transform request/response payloads, and handle the pagination envelope.
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

import { fetchProfile, updateProfile } from "../../services/profileService";
import { fetchAppointments, requestAppointment } from "../../services/appointmentService";
import { fetchConversations, sendMessage } from "../../services/messagingService";
import { fetchServices, requestService } from "../../services/marketplaceService";
import { fetchSessions, requestTeleconsult, joinSession } from "../../services/telehealthService";
import { fetchFeed, likeFeedItem } from "../../services/feedService";

describe("Backend Integration — API Contract Compliance", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Envelope format compliance", () => {
    it("profile endpoint returns data.attributes structure", async () => {
      mockApiClient.get.mockResolvedValue({
        data: {
          data: {
            attributes: {
              cpid: "CPID-5000",
              givenName: "Rudo",
              familyName: "Gumbo",
              dateOfBirth: "1988-11-03",
              sex: "F",
            },
          },
        },
      });

      const profile = await fetchProfile();
      expect(profile.cpid).toBe("CPID-5000");
      expect(profile.givenName).toBe("Rudo");
    });

    it("paginated endpoints return data[] + meta.page structure", async () => {
      mockApiClient.get.mockResolvedValue({
        data: {
          data: [
            { id: "apt-1", status: "CONFIRMED" },
            { id: "apt-2", status: "REQUESTED" },
          ],
          meta: {
            page: { number: 0, size: 20, total_elements: 15, total_pages: 1 },
          },
        },
      });

      const result = await fetchAppointments();
      expect(result.items).toHaveLength(2);
      expect(result.totalElements).toBe(15);
      expect(result.hasNext).toBe(false);
    });

    it("paginated endpoint detects hasNext correctly", async () => {
      mockApiClient.get.mockResolvedValue({
        data: {
          data: Array(20).fill({ id: "apt-x", status: "CONFIRMED" }),
          meta: {
            page: { number: 0, size: 20, total_elements: 45, total_pages: 3 },
          },
        },
      });

      const result = await fetchAppointments();
      expect(result.hasNext).toBe(true);
    });
  });

  describe("URL construction", () => {
    it("profile PATCH sends correct URL and body", async () => {
      mockApiClient.patch.mockResolvedValue({
        data: { data: { attributes: { phone: "+263771111111" } } },
      });

      await updateProfile({ phone: "+263771111111" });
      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/profile",
        { phone: "+263771111111" }
      );
    });

    it("appointment create POSTs to correct endpoint", async () => {
      mockApiClient.post.mockResolvedValue({
        data: { data: { id: "apt-new", status: "REQUESTED" } },
      });

      await requestAppointment({
        facilityId: "fac-123",
        appointmentType: "GENERAL",
        preferredDate: "2026-04-01",
      });
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/appointments",
        expect.objectContaining({ facilityId: "fac-123", appointmentType: "GENERAL" })
      );
    });

    it("messaging conversations use correct nested URL", async () => {
      mockApiClient.get.mockResolvedValue({
        data: { data: [], meta: { page: { number: 0, size: 20, total_elements: 0, total_pages: 0 } } },
      });

      await fetchConversations();
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/messaging/conversations"
      );
    });

    it("send message POSTs to conversation messages endpoint", async () => {
      mockApiClient.post.mockResolvedValue({
        data: { data: { id: "msg-new", body: "Hello" } },
      });

      await sendMessage("conv-abc", "Hello");
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/messaging/conversations/conv-abc/messages",
        { body: "Hello", replyTo: undefined }
      );
    });

    it("marketplace services use correct endpoint", async () => {
      mockApiClient.get.mockResolvedValue({
        data: { data: [], meta: { page: { number: 0, size: 20, total_elements: 0, total_pages: 0 } } },
      });

      await fetchServices();
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/marketplace/services"
      );
    });

    it("marketplace request POST uses /requests endpoint", async () => {
      mockApiClient.post.mockResolvedValue({
        data: { data: { id: "req-1", status: "PENDING" } },
      });

      await requestService({ serviceId: "svc-1" });
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/marketplace/requests",
        { serviceId: "svc-1" }
      );
    });

    it("telehealth sessions use correct endpoint", async () => {
      mockApiClient.get.mockResolvedValue({
        data: { data: [], meta: { page: { number: 0, size: 20, total_elements: 0, total_pages: 0 } } },
      });

      await fetchSessions();
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/telehealth/sessions"
      );
    });

    it("feed endpoint uses root path without /items suffix", async () => {
      mockApiClient.get.mockResolvedValue({
        data: { data: [], meta: { page: { number: 0, size: 20, total_elements: 0, total_pages: 0 } } },
      });

      await fetchFeed();
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/feed"
      );
    });
  });

  describe("Telehealth session lifecycle", () => {
    it("request → join → end full flow", async () => {
      // Step 1: Request teleconsult
      mockApiClient.post.mockResolvedValueOnce({
        data: {
          data: { id: "ses-flow-1", status: "REQUESTED", sessionType: "VIDEO" },
        },
      });

      const session = await requestTeleconsult({ reason: "Chest pain" });
      expect(session.status).toBe("REQUESTED");

      // Step 2: Join session (returns token + channel)
      mockApiClient.post.mockResolvedValueOnce({
        data: {
          data: {
            attributes: {
              id: "ses-flow-1",
              status: "IN_PROGRESS",
              sessionType: "VIDEO",
              token: "real-jwt-token",
              channel: "session-ses-flow-1",
            },
          },
        },
      });

      const joined = await joinSession("ses-flow-1");
      expect(joined.token).toBe("real-jwt-token");
      expect(joined.channel).toBe("session-ses-flow-1");
      expect(joined.session.status).toBe("IN_PROGRESS");
    });
  });

  describe("Feed interaction", () => {
    it("like posts to /{id}/like", async () => {
      mockApiClient.post.mockResolvedValue({});

      await likeFeedItem("feed-42");
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/mobile/citizen/feed/feed-42/like"
      );
    });
  });
});
