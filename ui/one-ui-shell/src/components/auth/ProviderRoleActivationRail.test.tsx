import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ProviderRoleActivationRail } from "./ProviderRoleActivationRail";

const mockUseLinkedIds = vi.fn();
const mockUseAuthStore = vi.fn();

vi.mock("@/hooks/queries/useLinkedIds", () => ({
  useLinkedIds: (...args: unknown[]) => mockUseLinkedIds(...args),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector?: (s: unknown) => unknown) => {
    const state = mockUseAuthStore();
    return selector ? selector(state) : state;
  },
}));

describe("ProviderRoleActivationRail", () => {
  beforeEach(() => {
    mockUseAuthStore.mockReturnValue({ user: { id: "health-1", providerActivated: false } });
    mockUseLinkedIds.mockReturnValue({ data: null, isLoading: false, isError: false });
  });

  it("shows activation CTA when provider id is linked but not activated", () => {
    mockUseLinkedIds.mockReturnValue({
      data: { data: { attributes: { providerId: "PRV-99" } } },
      isLoading: false,
      isError: false,
    });
    render(<ProviderRoleActivationRail returnTo="/ehr" />);
    expect(screen.getByText(/Provider capacity detected/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Activate provider role/i })).toHaveAttribute(
      "href",
      "/provider/activate?returnTo=%2Fehr",
    );
  });

  it("shows active state when provider is activated", () => {
    mockUseAuthStore.mockReturnValue({
      user: { id: "health-1", providerId: "PRV-99", providerActivated: true },
    });
    render(<ProviderRoleActivationRail />);
    expect(screen.getByText(/Provider role active/)).toBeInTheDocument();
  });

  it("shows citizen guidance when no provider is linked", () => {
    mockUseLinkedIds.mockReturnValue({
      data: { data: { attributes: {} } },
      isLoading: false,
      isError: false,
    });
    render(<ProviderRoleActivationRail />);
    expect(screen.getByText(/Citizen session/)).toBeInTheDocument();
  });

  it("links the no-provider branch to the claim/recovery journey", () => {
    mockUseLinkedIds.mockReturnValue({
      data: { data: { attributes: {} } },
      isLoading: false,
      isError: false,
    });
    render(<ProviderRoleActivationRail />);
    expect(
      screen.getByRole("link", { name: /Claim or recover your provider profile/i }),
    ).toHaveAttribute("href", "/citizen/provider-claim");
  });
});
