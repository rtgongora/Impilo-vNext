import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PublicHealthInfo } from "./PublicHealthInfo";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

const TOPIC = {
  id: "11111111-1111-1111-1111-111111111111",
  title: "Childhood vaccinations: what the schedule covers",
  summary: "Routine childhood vaccinations are free at public clinics.",
  category: "vaccination",
  domain: "public-health",
};

const ARTICLE = {
  ...TOPIC,
  body: "Vaccines teach the body to fight dangerous infections.\n\nThe schedule starts at birth.",
  updatedAt: "2026-07-12T00:00:00Z",
};

function listResponse(items = [TOPIC], totalElements = items.length) {
  return {
    data: items,
    meta: { page: 0, size: 10, totalElements, totalPages: 1 },
  };
}

function mockRoutes({
  list = listResponse(),
  categories = { data: [{ category: "vaccination", count: 1 }] },
  article = { data: ARTICLE },
  failList = false,
}: {
  list?: unknown;
  categories?: unknown;
  article?: unknown;
  failList?: boolean;
} = {}) {
  get.mockImplementation((path: string) => {
    if (path.includes("/education/categories")) return Promise.resolve(categories);
    if (path.includes(`/education/${TOPIC.id}`)) return Promise.resolve(article);
    if (path.includes("/education?")) {
      return failList ? Promise.reject(new Error("down")) : Promise.resolve(list);
    }
    return Promise.reject(new Error(`unexpected path: ${path}`));
  });
}

describe("PublicHealthInfo (gateway pillar 3, anonymous R0)", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("loads topics and categories from the anonymous gateway lane only", async () => {
    mockRoutes();
    render(<PublicHealthInfo />);

    expect(await screen.findByText(TOPIC.title)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /vaccination \(1\)/i })).toBeInTheDocument();

    for (const [path] of get.mock.calls) {
      expect(path).toMatch(/^\/internal\/v1\/public\/gateway\/guidance\/education/);
    }
  });

  it("filters the list when a category chip is chosen", async () => {
    mockRoutes();
    render(<PublicHealthInfo />);
    await screen.findByText(TOPIC.title);

    fireEvent.click(screen.getByRole("button", { name: /vaccination \(1\)/i }));

    await waitFor(() => {
      const listCalls = get.mock.calls.filter(([p]) => (p as string).includes("/education?"));
      expect(listCalls.some(([p]) => (p as string).includes("category=vaccination"))).toBe(true);
    });
  });

  it("opens the article read view with the plain-language body and a back route", async () => {
    mockRoutes();
    render(<PublicHealthInfo />);
    fireEvent.click(await screen.findByText(TOPIC.title));

    expect(
      await screen.findByText(/Vaccines teach the body to fight dangerous infections/),
    ).toBeInTheDocument();
    expect(screen.getByText(/not personal medical advice/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /back to all topics/i }));
    expect(await screen.findByText("Browse health topics")).toBeInTheDocument();
  });

  it("shows an honest unavailable state (with the emergency route) when the lane is down", async () => {
    mockRoutes({ failList: true, categories: Promise.reject(new Error("down")) as unknown });
    render(<PublicHealthInfo />);

    expect(
      await screen.findByText(/Health information is temporarily unavailable/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /emergency numbers and guidance/i })).toBeInTheDocument();
  });

  it("is honest when a topic has no published articles yet", async () => {
    mockRoutes({ list: listResponse([], 0) });
    render(<PublicHealthInfo />);

    expect(
      await screen.findByText(/No articles are published under this topic yet/i),
    ).toBeInTheDocument();
  });
});
