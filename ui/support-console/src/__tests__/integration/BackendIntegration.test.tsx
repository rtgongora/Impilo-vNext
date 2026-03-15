/**
 * Backend Integration Tests — Dashboard stats and Knowledge Article CRUD.
 *
 * Validates that the support API functions correctly call dashboard
 * and article endpoints with the expected URLs and payloads.
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
  getDashboardStats,
  listArticles,
  getArticle,
  createArticle,
  updateArticle,
} from "../../lib/supportApi";

describe("Backend Integration — API Contract Compliance", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Dashboard stats", () => {
    it("calls /dashboard/stats endpoint", async () => {
      mockApiClient.get.mockResolvedValue({
        openCount: 42,
        inProgressCount: 15,
        resolvedCount: 120,
        closedCount: 300,
        criticalCount: 3,
        highCount: 8,
        escalatedCount: 5,
        avgResolutionHours: 4.5,
      });

      const stats = await getDashboardStats();

      expect(stats.openCount).toBe(42);
      expect(stats.criticalCount).toBe(3);
      expect(stats.avgResolutionHours).toBe(4.5);
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/dashboard/stats"
      );
    });

    it("returns all expected stat fields", async () => {
      mockApiClient.get.mockResolvedValue({
        openCount: 0,
        inProgressCount: 0,
        resolvedCount: 0,
        closedCount: 0,
        criticalCount: 0,
        highCount: 0,
        escalatedCount: 0,
        avgResolutionHours: 0,
      });

      const stats = await getDashboardStats();

      expect(stats).toHaveProperty("openCount");
      expect(stats).toHaveProperty("inProgressCount");
      expect(stats).toHaveProperty("resolvedCount");
      expect(stats).toHaveProperty("closedCount");
      expect(stats).toHaveProperty("criticalCount");
      expect(stats).toHaveProperty("highCount");
      expect(stats).toHaveProperty("escalatedCount");
      expect(stats).toHaveProperty("avgResolutionHours");
    });
  });

  describe("Knowledge article CRUD flows", () => {
    it("lists articles with default parameters", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [
          {
            articleId: "art-1",
            title: "How to reset passwords",
            status: "PUBLISHED",
            category: "AUTHENTICATION",
          },
          {
            articleId: "art-2",
            title: "Troubleshooting login issues",
            status: "DRAFT",
            category: "AUTHENTICATION",
          },
        ],
        cursor: null,
        limit: 50,
        total_elements: 2,
        has_more: false,
      });

      const result = await listArticles();

      expect(result.items).toHaveLength(2);
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/articles?cursor=0&limit=50"
      );
    });

    it("lists articles filtered by category and status", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [
          {
            articleId: "art-1",
            title: "How to reset passwords",
            status: "PUBLISHED",
            category: "AUTHENTICATION",
          },
        ],
        cursor: null,
        limit: 50,
        total_elements: 1,
        has_more: false,
      });

      await listArticles("AUTHENTICATION", "PUBLISHED");

      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/articles?category=AUTHENTICATION&status=PUBLISHED&cursor=0&limit=50"
      );
    });

    it("gets a single article by ID", async () => {
      mockApiClient.get.mockResolvedValue({
        articleId: "art-1",
        title: "How to reset passwords",
        body: "Step 1: Go to settings...",
        status: "PUBLISHED",
        category: "AUTHENTICATION",
        authorRef: "agent-10",
        tags: "password,authentication",
        version: 3,
        createdAt: "2026-02-01T08:00:00Z",
        updatedAt: "2026-03-10T12:00:00Z",
        publishedAt: "2026-03-10T12:00:00Z",
      });

      const article = await getArticle("art-1");

      expect(article.articleId).toBe("art-1");
      expect(article.title).toBe("How to reset passwords");
      expect(article.status).toBe("PUBLISHED");
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/articles/art-1"
      );
    });

    it("creates a new article", async () => {
      mockApiClient.post.mockResolvedValue({
        articleId: "art-new",
        title: "Configuring MFA",
        body: "Multi-factor authentication setup guide.",
        status: "DRAFT",
        category: "SECURITY",
        authorRef: "agent-10",
        tags: "mfa,security",
        version: 1,
        createdAt: "2026-03-15T13:00:00Z",
        updatedAt: "2026-03-15T13:00:00Z",
        publishedAt: null,
      });

      const article = await createArticle({
        title: "Configuring MFA",
        body: "Multi-factor authentication setup guide.",
        authorRef: "agent-10",
        category: "SECURITY",
        tags: "mfa,security",
      });

      expect(article.articleId).toBe("art-new");
      expect(article.status).toBe("DRAFT");
      expect(article.publishedAt).toBeNull();
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/articles",
        {
          title: "Configuring MFA",
          body: "Multi-factor authentication setup guide.",
          authorRef: "agent-10",
          category: "SECURITY",
          tags: "mfa,security",
        }
      );
    });

    it("updates an existing article body", async () => {
      mockApiClient.patch.mockResolvedValue({
        articleId: "art-1",
        title: "How to reset passwords",
        body: "Updated step 1: Navigate to security settings...",
        status: "DRAFT",
        version: 4,
        updatedAt: "2026-03-15T14:00:00Z",
      });

      const article = await updateArticle("art-1", {
        body: "Updated step 1: Navigate to security settings...",
      });

      expect(article.version).toBe(4);
      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/internal/v1/support/articles/art-1",
        { body: "Updated step 1: Navigate to security settings..." }
      );
    });
  });

  describe("Article publish flow", () => {
    it("publish updates article status to PUBLISHED", async () => {
      mockApiClient.patch.mockResolvedValue({
        articleId: "art-new",
        title: "Configuring MFA",
        status: "PUBLISHED",
        version: 2,
        publishedAt: "2026-03-15T15:00:00Z",
        updatedAt: "2026-03-15T15:00:00Z",
      });

      const article = await updateArticle("art-new", { status: "PUBLISHED" });

      expect(article.status).toBe("PUBLISHED");
      expect(article.publishedAt).toBe("2026-03-15T15:00:00Z");
      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/internal/v1/support/articles/art-new",
        { status: "PUBLISHED" }
      );
    });

    it("archive updates article status to ARCHIVED", async () => {
      mockApiClient.patch.mockResolvedValue({
        articleId: "art-1",
        title: "How to reset passwords",
        status: "ARCHIVED",
        version: 5,
        updatedAt: "2026-03-15T16:00:00Z",
      });

      const article = await updateArticle("art-1", { status: "ARCHIVED" });

      expect(article.status).toBe("ARCHIVED");
      expect(mockApiClient.patch).toHaveBeenCalledWith(
        "/internal/v1/support/articles/art-1",
        { status: "ARCHIVED" }
      );
    });

    it("full create-then-publish lifecycle", async () => {
      // Step 1: Create as DRAFT
      mockApiClient.post.mockResolvedValue({
        articleId: "art-lifecycle",
        title: "Lifecycle test article",
        body: "Content here",
        status: "DRAFT",
        authorRef: "agent-10",
        version: 1,
        publishedAt: null,
      });

      const draft = await createArticle({
        title: "Lifecycle test article",
        body: "Content here",
        authorRef: "agent-10",
      });
      expect(draft.status).toBe("DRAFT");
      expect(draft.publishedAt).toBeNull();

      // Step 2: Publish
      mockApiClient.patch.mockResolvedValue({
        articleId: "art-lifecycle",
        title: "Lifecycle test article",
        status: "PUBLISHED",
        version: 2,
        publishedAt: "2026-03-15T17:00:00Z",
      });

      const published = await updateArticle("art-lifecycle", { status: "PUBLISHED" });
      expect(published.status).toBe("PUBLISHED");
      expect(published.publishedAt).not.toBeNull();

      expect(mockApiClient.post).toHaveBeenCalledTimes(1);
      expect(mockApiClient.patch).toHaveBeenCalledTimes(1);
    });
  });
});
