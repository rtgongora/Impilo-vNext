import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn() },
}));
vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

import { apiClient } from "@/lib/api-client";
import WellnessSocialSavedPage from "../saved/page";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <WellnessSocialSavedPage />
    </QueryClientProvider>,
  );
}

describe("WellnessSocialSavedPage", () => {
  beforeEach(() => get.mockReset());

  it("lists saved bookmarks and links each to the post detail route", async () => {
    get.mockResolvedValue({
      data: [{ bookmarkId: "B1", subjectType: "POST", subjectId: "P9", createdAt: new Date().toISOString() }],
    });

    renderPage();

    expect(await screen.findByText("POST")).toBeInTheDocument();
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/wellness/social/posts/P9");
    expect(get).toHaveBeenCalledWith("/internal/v1/wellness/social/saved");
  });

  it("shows an empty state when nothing is saved", async () => {
    get.mockResolvedValue({ data: [] });
    renderPage();
    expect(await screen.findByText(/Nothing saved yet/i)).toBeInTheDocument();
  });
});
