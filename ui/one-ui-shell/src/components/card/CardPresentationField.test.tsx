import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CardPresentationField } from "./CardPresentationField";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a) },
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("CardPresentationField", () => {
  beforeEach(() => post.mockReset());

  it("emits a cardNumber presentation as the operator types", () => {
    const onPresented = vi.fn();
    wrap(<CardPresentationField onPresented={onPresented} />);
    fireEvent.change(screen.getByTestId("card-number-input"), { target: { value: "CARD-1" } });
    expect(onPresented).toHaveBeenLastCalledWith({ cardNumber: "CARD-1" });
  });

  it("resolves a presented card and shows the patient + status", async () => {
    post.mockResolvedValue({ data: { healthId: "h1", cpid: "h1", cardStatus: "ACTIVE" } });
    const onResolved = vi.fn();
    wrap(<CardPresentationField onPresented={() => {}} onResolved={onResolved} resolvePreview />);
    fireEvent.change(screen.getByTestId("card-number-input"), { target: { value: "CARD-1" } });
    fireEvent.click(screen.getByTestId("card-resolve-btn"));
    await waitFor(() => expect(screen.getByTestId("card-resolution")).toBeInTheDocument());
    expect(onResolved).toHaveBeenCalledWith(expect.objectContaining({ cardStatus: "ACTIVE" }));
  });

  it("shows a clean rejection for an unresolvable card (never fabricates identity)", async () => {
    // BFF returns no identity for an unknown/unverifiable/expired card.
    post.mockResolvedValue({ data: null });
    const onResolved = vi.fn();
    wrap(<CardPresentationField onPresented={() => {}} onResolved={onResolved} resolvePreview />);
    fireEvent.change(screen.getByTestId("card-number-input"), { target: { value: "NOPE" } });
    fireEvent.click(screen.getByTestId("card-resolve-btn"));
    await waitFor(() => expect(screen.getByTestId("card-resolve-error")).toBeInTheDocument());
    expect(screen.queryByTestId("card-resolution")).not.toBeInTheDocument();
    expect(onResolved).toHaveBeenLastCalledWith(null);
  });
});
