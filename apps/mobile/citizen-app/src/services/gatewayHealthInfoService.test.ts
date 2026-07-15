import { describe, it, expect, vi, beforeEach } from "vitest";

const publicClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ publicApiClient: publicClient }));

import {
  fetchPublicEducation,
  fetchPublicEducationCategories,
  fetchPublicEducationArticle,
} from "./gatewayHealthInfoService";

describe("gatewayHealthInfoService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("browses/searches education with q/category/page/size on the public guidance lane", async () => {
    publicClient.get.mockResolvedValue({
      data: { data: [{ id: "a1", title: "Malaria", summary: "…", category: "prevention" }], meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 } },
    });
    const res = await fetchPublicEducation({ q: "malaria", category: "prevention", page: 0, size: 10 });
    const url = publicClient.get.mock.calls[0][0] as string;
    expect(url).toContain("/internal/v1/public/gateway/guidance/education?");
    expect(url).toContain("q=malaria");
    expect(url).toContain("category=prevention");
    expect(url).toContain("page=0");
    expect(url).toContain("size=10");
    expect(res.data).toHaveLength(1);
  });

  it("tolerates a malformed page body", async () => {
    publicClient.get.mockResolvedValue({ data: null });
    const res = await fetchPublicEducation();
    expect(res.data).toEqual([]);
  });

  it("reads categories and returns [] when absent", async () => {
    publicClient.get.mockResolvedValueOnce({ data: { data: [{ category: "vaccination", count: 3 }] } });
    expect(await fetchPublicEducationCategories()).toEqual([{ category: "vaccination", count: 3 }]);
    publicClient.get.mockResolvedValueOnce({ data: {} });
    expect(await fetchPublicEducationCategories()).toEqual([]);
  });

  it("reads one article by id", async () => {
    publicClient.get.mockResolvedValue({ data: { data: { id: "a1", title: "T", summary: "S", body: "B", category: "c" } } });
    const article = await fetchPublicEducationArticle("a1");
    expect(publicClient.get).toHaveBeenCalledWith("/internal/v1/public/gateway/guidance/education/a1");
    expect(article.body).toBe("B");
  });
});
