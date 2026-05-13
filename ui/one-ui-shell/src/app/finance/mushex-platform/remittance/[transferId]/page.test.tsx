import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import RemittanceTransferDetailPage from "./page";

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
  useParams: () => ({ transferId: "rem-abc-123" }),
}));

vi.mock("@/hooks/queries/useMushexPlatformAdmin", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useMushexPlatformAdmin")>(
    "@/hooks/queries/useMushexPlatformAdmin",
  );
  return {
    ...actual,
    useMushexPlatformRemittanceTransferById: () => ({
      data: {
        data: {
          remittanceRequestId: "rem-abc-123",
          remittanceCode: "RC-001",
          senderRef: "SENDER-A",
          recipientRef: "RECIPIENT-B",
          amount: "120.00",
          currency: "USD",
          status: "REQUESTED",
        },
      },
      isLoading: false,
      isError: false,
    }),
  };
});

describe("RemittanceTransferDetailPage", () => {
  it("renders transfer identity and key fields", () => {
    render(<RemittanceTransferDetailPage />);
    expect(screen.getByRole("heading", { name: "Remittance transfer" })).toBeInTheDocument();
    expect(screen.getByTestId("subtitle").textContent).toContain("rem-abc-123");
    expect(screen.getByText("RC-001")).toBeInTheDocument();
    expect(screen.getByText("SENDER-A")).toBeInTheDocument();
  });

  it("documents the BFF per-id remittance route and keeps read-only posture", () => {
    render(<RemittanceTransferDetailPage />);
    expect(
      screen.getByText("/internal/v1/finance/mushex-platform/remittance-transfers/rem-abc-123"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});

