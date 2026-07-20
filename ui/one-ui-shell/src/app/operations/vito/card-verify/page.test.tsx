import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CardVerifyWorkspace from "./page";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("Card verify (POS) page", () => {
  beforeEach(() => post.mockReset());

  it("resolves a presented card and shows patient + status", async () => {
    post.mockResolvedValue({ data: { healthId: "h1", cpid: "h1", cardNumber: "CARD-1", cardStatus: "ACTIVE", resolvedVia: "CARD_NUMBER" } });
    wrap(<CardVerifyWorkspace />);
    fireEvent.change(screen.getByTestId("card-number-input"), { target: { value: "CARD-1" } });
    fireEvent.click(screen.getByTestId("card-resolve-btn"));
    const result = await screen.findByTestId("card-verify-result");
    expect(result.textContent).toContain("ACTIVE");
    expect(result.textContent).toContain("h1");
  });
});
