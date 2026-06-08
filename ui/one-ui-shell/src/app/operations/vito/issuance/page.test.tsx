import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import IssuanceQueuePage from "./page";

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
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

const mockUseIssuanceQueue = vi.fn();
const mockUseSubmitIssuance = vi.fn();

vi.mock("@/hooks/queries/useVitoIssuance", () => ({
  useIssuanceQueue: (...args: unknown[]) => mockUseIssuanceQueue(...args),
  useSubmitIssuance: () => mockUseSubmitIssuance(),
}));

describe("IssuanceQueuePage", () => {
  beforeEach(() => {
    mockUseIssuanceQueue.mockReturnValue({
      data: { content: [{ id: "req-1", state: "SUBMITTED", healthId: "HID-1" }] },
      isLoading: false,
      isError: false,
    });
    mockUseSubmitIssuance.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("renders issuance queue heading", () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <IssuanceQueuePage />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("heading")).toBeInTheDocument();
  });
});
