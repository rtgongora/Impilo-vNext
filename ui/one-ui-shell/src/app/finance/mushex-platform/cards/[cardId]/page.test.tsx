import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CardProfileDetailPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div data-testid="app-layout">{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p data-testid="subtitle">{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ cardId: "card-abc-123" }),
}));

vi.mock("@/hooks/queries/useMushexPlatformAdmin", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useMushexPlatformAdmin")>(
    "@/hooks/queries/useMushexPlatformAdmin",
  );
  return {
    ...actual,
    useMushexPlatformCardProfileById: () => ({
      data: {
        data: {
          cardProfileId: "card-abc-123",
          walletId: "wallet-xyz-001",
          ownerRef: "OWNER-1",
          cardType: "VISA",
          cardStatus: "ISSUED",
        },
      },
      isLoading: false,
      isError: false,
    }),
  };
});

describe("CardProfileDetailPage", () => {
  it("renders card profile detail fields", () => {
    render(<CardProfileDetailPage />);
    expect(screen.getByRole("heading", { name: "Card profile" })).toBeInTheDocument();
    expect(screen.getByText("wallet-xyz-001")).toBeInTheDocument();
    expect(screen.getByText("OWNER-1")).toBeInTheDocument();
    expect(screen.getByText("VISA")).toBeInTheDocument();
  });

  it("documents the BFF per-id card route", () => {
    render(<CardProfileDetailPage />);
    expect(
      screen.getByText("/internal/v1/finance/mushex-platform/card-profiles/card-abc-123"),
    ).toBeInTheDocument();
  });
});

