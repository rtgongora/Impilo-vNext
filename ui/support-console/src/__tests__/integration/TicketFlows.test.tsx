/**
 * Ticket Flow Tests — CRUD operations, comments, and assignments.
 *
 * Validates that supportApi functions correctly construct API URLs,
 * send the right payloads, and call the expected HTTP methods.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

vi.mock("@/lib/apiClient", () => ({
  apiClient: mockApiClient,
}));

import {
  listTickets,
  getTicket,
  createTicket,
  updateTicket,
  addComment,
  assignTicket,
} from "../../lib/supportApi";

describe("Ticket Flows — API Contract Compliance", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("List tickets", () => {
    it("constructs correct URL with no filters", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [],
        cursor: null,
        limit: 20,
        total_elements: 0,
        has_more: false,
      });

      await listTickets();
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets?cursor=0&limit=20"
      );
    });

    it("constructs correct URL with status and priority filters", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [
          { ticketId: "tkt-1", status: "OPEN", priority: "HIGH" },
        ],
        cursor: null,
        limit: 20,
        total_elements: 1,
        has_more: false,
      });

      await listTickets({ status: "OPEN", priority: "HIGH" });
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets?status=OPEN&priority=HIGH&cursor=0&limit=20"
      );
    });

    it("constructs correct URL with category and assigneeRef filters", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [],
        cursor: null,
        limit: 20,
        total_elements: 0,
        has_more: false,
      });

      await listTickets({ category: "BUG", assigneeRef: "agent-42" });
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets?category=BUG&assigneeRef=agent-42&cursor=0&limit=20"
      );
    });

    it("respects custom cursor and limit", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [],
        cursor: null,
        limit: 10,
        total_elements: 0,
        has_more: false,
      });

      await listTickets({ cursor: 5, limit: 10 });
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets?cursor=5&limit=10"
      );
    });
  });

  describe("Get ticket by ID", () => {
    it("calls correct endpoint for single ticket", async () => {
      mockApiClient.get.mockResolvedValue({
        ticketId: "tkt-100",
        title: "Login broken",
        status: "OPEN",
        priority: "CRITICAL",
        category: "INCIDENT",
      });

      const ticket = await getTicket("tkt-100");
      expect(ticket.ticketId).toBe("tkt-100");
      expect(ticket.priority).toBe("CRITICAL");
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-100"
      );
    });
  });

  describe("Create ticket", () => {
    it("sends correct payload via POST", async () => {
      mockApiClient.post.mockResolvedValue({
        ticketId: "tkt-201",
        title: "Cannot access patient records",
        description: "Error 403 when clicking patient tab",
        category: "ACCESS",
        priority: "HIGH",
        status: "OPEN",
        reporterRef: "agent-7",
        createdAt: "2026-03-15T08:00:00Z",
      });

      const ticket = await createTicket({
        title: "Cannot access patient records",
        description: "Error 403 when clicking patient tab",
        reporterRef: "agent-7",
        category: "ACCESS",
        priority: "HIGH",
      });

      expect(ticket.ticketId).toBe("tkt-201");
      expect(ticket.status).toBe("OPEN");
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/tickets",
        {
          title: "Cannot access patient records",
          description: "Error 403 when clicking patient tab",
          reporterRef: "agent-7",
          category: "ACCESS",
          priority: "HIGH",
        }
      );
    });

    it("sends minimal payload when optional fields omitted", async () => {
      mockApiClient.post.mockResolvedValue({
        ticketId: "tkt-202",
        title: "General inquiry",
        description: "How do I reset my password?",
        status: "OPEN",
        reporterRef: "agent-3",
      });

      await createTicket({
        title: "General inquiry",
        description: "How do I reset my password?",
        reporterRef: "agent-3",
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/tickets",
        {
          title: "General inquiry",
          description: "How do I reset my password?",
          reporterRef: "agent-3",
        }
      );
    });
  });

  describe("Update ticket status", () => {
    it("sends PATCH with correct body", async () => {
      mockApiClient.patch.mockResolvedValue({
        ticketId: "tkt-100",
        status: "IN_PROGRESS",
        assigneeRef: "agent-5",
      });

      const updated = await updateTicket("tkt-100", {
        status: "IN_PROGRESS",
        assigneeRef: "agent-5",
      });

      expect(updated.status).toBe("IN_PROGRESS");
      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-100",
        { status: "IN_PROGRESS", assigneeRef: "agent-5" }
      );
    });

    it("sends PATCH with resolution on close", async () => {
      mockApiClient.patch.mockResolvedValue({
        ticketId: "tkt-100",
        status: "RESOLVED",
        resolution: "User password was reset via admin console",
      });

      await updateTicket("tkt-100", {
        status: "RESOLVED",
        resolution: "User password was reset via admin console",
      });

      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-100",
        {
          status: "RESOLVED",
          resolution: "User password was reset via admin console",
        }
      );
    });
  });

  describe("Add comment", () => {
    it("sends POST to /tickets/{id}/comments", async () => {
      mockApiClient.post.mockResolvedValue({
        commentId: "cmt-1",
        ticketId: "tkt-100",
        authorRef: "agent-5",
        body: "Investigating the root cause now.",
        createdAt: "2026-03-15T09:30:00Z",
      });

      const comment = await addComment(
        "tkt-100",
        "agent-5",
        "Investigating the root cause now."
      );

      expect(comment.commentId).toBe("cmt-1");
      expect(comment.body).toBe("Investigating the root cause now.");
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-100/comments",
        { authorRef: "agent-5", body: "Investigating the root cause now." }
      );
    });
  });

  describe("Assign ticket", () => {
    it("sends POST to /tickets/{id}/assign", async () => {
      mockApiClient.post.mockResolvedValue({
        assignmentId: "asgn-1",
        ticketId: "tkt-100",
        assigneeRef: "agent-12",
        assignedBy: "lead-1",
        assignedAt: "2026-03-15T10:00:00Z",
      });

      const assignment = await assignTicket("tkt-100", "agent-12", "lead-1");

      expect(assignment.assignmentId).toBe("asgn-1");
      expect(assignment.assigneeRef).toBe("agent-12");
      expect(assignment.assignedBy).toBe("lead-1");
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-100/assign",
        { assigneeRef: "agent-12", assignedBy: "lead-1" }
      );
    });
  });
});
