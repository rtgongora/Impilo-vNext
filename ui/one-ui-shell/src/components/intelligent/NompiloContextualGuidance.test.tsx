import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { NompiloContextualGuidance } from "./NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

function renderGuidance(routePath = "/citizen/wallet") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <NompiloContextualGuidance routePath={routePath} />
    </QueryClientProvider>,
  );
}

const TRUST_LOCK = {
  data: {
    context: { userType: "CITIZEN", trustLoa: 1, routePath: "/citizen/wallet", relationship: "SELF" },
    guidance: [
      {
        key: "UPGRADE_TRUST",
        domain: "wallet",
        type: "LOCKED_STATE",
        title: "Verify your identity to unlock your full health wallet",
        body: "Some records and actions require a higher trust level.",
        ctaLabel: "Verify identity",
        ctaRoute: "/citizen/wallet/identity",
        ownerService: "identity-assurance",
        severity: "WARN",
        priority: 10,
        khulumaFollowUpAvailable: false,
        auditRequired: true,
      },
    ],
  },
  meta: {},
};

describe("NompiloContextualGuidance", () => {
  it("renders real guidance with a route-to-owner CTA (no dead buttons)", async () => {
    post.mockResolvedValue(TRUST_LOCK);
    renderGuidance();

    expect(await screen.findByText(/Verify your identity to unlock/i)).toBeInTheDocument();
    // CTA links to the real owning surface.
    const cta = screen.getByRole("link", { name: /Verify identity/i });
    expect(cta).toHaveAttribute("href", "/citizen/wallet/identity");
    // Provenance is shown (no anonymous cards).
    expect(screen.getByText(/via identity-assurance/i)).toBeInTheDocument();
  });

  it("announces in full, then fades to a resting whisper (detail stays accessible)", async () => {
    post.mockResolvedValue(TRUST_LOCK);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <NompiloContextualGuidance routePath="/citizen/wallet" announceMs={30} />
      </QueryClientProvider>,
    );

    // Speaks in full first.
    expect(await screen.findByText(/Verify your identity to unlock/i)).toBeInTheDocument();
    // Then fades to a resting whisper (panel marks itself resting).
    await waitFor(() =>
      expect(screen.getByTestId("nompilo-contextual-guidance")).toHaveAttribute("data-state", "resting"),
    );
    // The detail (CTA) is still in the DOM — collapsed via CSS, revealed on hover/focus, never removed.
    expect(screen.getByRole("link", { name: /Verify identity/i })).toBeInTheDocument();
  });

  it("renders nothing when there is no guidance (never invents filler)", async () => {
    post.mockResolvedValue({ data: { guidance: [] }, meta: {} });
    const { container } = renderGuidance();
    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(container.querySelector('[data-testid="nompilo-contextual-guidance"]')).toBeNull();
  });

  it("renders nothing on a downstream failure (safe, quiet degrade)", async () => {
    post.mockRejectedValue(new Error("guidance down"));
    const { container } = renderGuidance();
    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(container.querySelector('[data-testid="nompilo-contextual-guidance"]')).toBeNull();
  });

  it("forwards the route path so guidance is context-aware", async () => {
    post.mockResolvedValue({ data: { guidance: [] }, meta: {} });
    renderGuidance("/work");
    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/nompilo/context",
      expect.objectContaining({ routePath: "/work" }),
    );
  });
});
