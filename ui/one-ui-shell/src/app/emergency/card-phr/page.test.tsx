import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, MutationCache } from "@tanstack/react-query";
import BreakGlassCardPhrWorkspace from "./page";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    mutationCache: new MutationCache({ onError: () => {} }),
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("Break-glass card PHR page", () => {
  beforeEach(() => post.mockReset());

  it("requires a card + reason, then reads the resolved subject's summary", async () => {
    post.mockResolvedValue({
      data: { subjectCpid: "cpid-9", breakGlassRequestId: "bg-1", summary: { resourceType: "Bundle" }, notice: "logged" },
    });
    wrap(<BreakGlassCardPhrWorkspace />);

    // Submit disabled until both card + reason present.
    expect(screen.getByTestId("break-glass-submit")).toBeDisabled();
    fireEvent.change(screen.getByTestId("card-number-input"), { target: { value: "CARD-1" } });
    fireEvent.change(screen.getByTestId("break-glass-reason"), { target: { value: "unconscious patient" } });
    expect(screen.getByTestId("break-glass-submit")).not.toBeDisabled();

    fireEvent.click(screen.getByTestId("break-glass-submit"));
    await waitFor(() => expect(screen.getByTestId("break-glass-result")).toBeInTheDocument());
    expect(screen.getByTestId("break-glass-result").textContent).toContain("cpid-9");
    expect(post).toHaveBeenCalledWith("/internal/v1/emergency/card-phr",
      { cardNumber: "CARD-1", reason: "unconscious patient" });
  });

  it("surfaces a refusal cleanly", async () => {
    post.mockRejectedValueOnce({ status: 502 });
    wrap(<BreakGlassCardPhrWorkspace />);
    fireEvent.change(screen.getByTestId("card-number-input"), { target: { value: "CARD-1" } });
    fireEvent.change(screen.getByTestId("break-glass-reason"), { target: { value: "emergency" } });
    fireEvent.click(screen.getByTestId("break-glass-submit"));
    expect(await screen.findByTestId("break-glass-error")).toBeInTheDocument();
  });
});
