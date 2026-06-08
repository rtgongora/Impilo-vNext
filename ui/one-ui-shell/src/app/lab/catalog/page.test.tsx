import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LabCatalogPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: () => null }),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: ({ status }: { status: string }) => <span>{status}</span>,
}));

vi.mock("@/hooks/queries/useLabCatalog", () => ({
  useLabCatalog: () => ({
    data: {
      data: {
        summary: { by_category: { Haematology: 2, Chemistry: 1 } },
        items: [
          { catalogId: "c1", code: "FBC", displayName: "Full blood count", category: "Haematology", active: true },
        ],
      },
    },
    isLoading: false,
    isError: false,
  }),
}));

describe("LabCatalogPage", () => {
  it("renders live BFF catalog categories and items (no static shell)", () => {
    render(<LabCatalogPage />);
    expect(screen.getByText("Test Catalog")).toBeInTheDocument();
    expect(screen.getByText("live")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Haematology" })).toBeInTheDocument();
    expect(screen.getByText("Full blood count")).toBeInTheDocument();
    expect(screen.getByText("2 tests")).toBeInTheDocument();
  });
});
