import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MarketplaceStorePage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
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

vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="nompilo" />,
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MarketplaceStorePage />
    </QueryClientProvider>,
  );
}

describe("MarketplaceStorePage", () => {
  beforeEach(() => get.mockReset());

  it("renders featured published listings from the BFF home envelope", async () => {
    get.mockResolvedValue({
      data: {
        featured: {
          data: {
            items: [
              {
                listingId: "L1",
                title: "Blood pressure check",
                summary: "Quick BP screening",
                riskClassification: "LOW_RISK",
                status: "PUBLISHED",
                sellerType: "PROVIDER",
                sellerId: "PROV-1",
                catalogItemId: "ITEM1",
                priceDisplay: "$5",
              },
            ],
          },
        },
        favourites: [],
        activity: { orders: [] },
      },
      meta: { request_id: "r", correlation_id: "c" },
    });

    renderPage();

    await waitFor(() => expect(screen.getByText("Blood pressure check")).toBeInTheDocument());
    expect(screen.getByTestId("nompilo")).toBeInTheDocument();
    // CTA links route to real screens (no dead buttons)
    expect(screen.getByText("Search & browse").closest("a")).toHaveAttribute("href", "/marketplace/store/search");
  });

  it("shows an honest empty state when no listings are published", async () => {
    get.mockResolvedValue({ data: { featured: { data: { items: [] } }, favourites: [], activity: null } });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/No published listings yet/i)).toBeInTheDocument(),
    );
  });
});
