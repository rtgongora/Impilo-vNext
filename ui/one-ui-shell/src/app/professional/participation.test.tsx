import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProfessionalProfilePage from "./page";

/**
 * Registration is not participation.
 *
 * Being on the HPA register makes a practitioner searchable, verifiable and findable; booking
 * gates on a separate, revocable yes. These tests hold the surface to telling those apart, and
 * in particular to never rendering a failed read as a "no" — a practitioner who is accepting
 * requests must not be shown as if they had opted out because a fetch failed.
 */

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/registry/ProviderCertificatesPanel", () => ({
  ProviderCertificatesPanel: () => null,
}));
vi.mock("@/components/intelligent/NompiloHint", () => ({ NompiloHint: () => null }));

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "u-1", providerId: "PRV-1" } }),
}));
vi.mock("@/hooks/queries/useLinkedIds", () => ({
  useLinkedIds: () => ({ data: undefined, isLoading: false, isError: false }),
}));

const claim = vi.hoisted(() => ({
  eligibility: { data: undefined as unknown, isLoading: false, isError: false },
  mutate: vi.fn(),
}));
vi.mock("@/hooks/queries/useProviderClaim", () => ({
  useProviderClaimEligibility: () => claim.eligibility,
  useSetProviderParticipation: () => ({
    mutate: claim.mutate,
    isPending: false,
    isError: false,
    error: null,
  }),
  providerClaimErrorMessage: (_e: unknown, fallback: string) => fallback,
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProfessionalProfilePage />
    </QueryClientProvider>,
  );
}

describe("professional profile — accepting requests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    get.mockResolvedValue({ data: { data: [] } });
    claim.eligibility = { data: undefined, isLoading: false, isError: false };
  });

  it("offers the switch to a claimed provider who has not opted in", () => {
    claim.eligibility = {
      data: { alreadyLinked: true, eligibleForClaim: false, participating: false, canSetParticipation: true },
      isLoading: false,
      isError: false,
    };
    renderPage();

    // The distinction the PO drew, stated to the person: listed and findable, but not bookable.
    expect(screen.getByText(/can be found and verified, but people cannot book you/i)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /Start accepting requests/i }));
    expect(claim.mutate).toHaveBeenCalledWith(true);
  });

  it("lets a participating provider stop", () => {
    claim.eligibility = {
      data: { alreadyLinked: true, eligibleForClaim: false, participating: true, canSetParticipation: true },
      isLoading: false,
      isError: false,
    };
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: /Stop accepting requests/i }));
    expect(claim.mutate).toHaveBeenCalledWith(false);
  });

  it("does not offer the switch on a profile that has not been claimed", () => {
    // Every one of the 4,241 HPA-imported skeletons is in this state. varapi refuses to switch
    // participation on for them, so offering the control would promise something it will reject.
    claim.eligibility = {
      data: {
        alreadyLinked: true,
        eligibleForClaim: false,
        participating: false,
        canSetParticipation: false,
        lifecycleStatus: "PRELOADED",
      },
      isLoading: false,
      isError: false,
    };
    renderPage();

    expect(screen.queryByRole("button", { name: /accepting requests/i })).toBeNull();
    expect(screen.getByText(/has not been claimed yet/i)).toBeTruthy();
  });

  it("says the standing is unavailable rather than showing it as a no", () => {
    claim.eligibility = { data: undefined, isLoading: false, isError: true };
    renderPage();

    expect(screen.getByText(/could not load whether you are accepting requests/i)).toBeTruthy();
    // The failure mode this guards: a provider who IS accepting requests being shown the
    // opt-in button, and turning off something that was already on.
    expect(screen.queryByRole("button", { name: /Start accepting requests/i })).toBeNull();
    expect(screen.queryByRole("button", { name: /Stop accepting requests/i })).toBeNull();
  });
});
