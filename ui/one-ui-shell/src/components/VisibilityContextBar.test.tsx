import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { VisibilityContextBar } from "./VisibilityContextBar";

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ isAuthenticated: true }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const mockUseVisibilityProfile = vi.fn();

vi.mock("@/hooks/queries/useVisibilityProfile", () => ({
  useVisibilityProfile: () => mockUseVisibilityProfile(),
}));

describe("VisibilityContextBar", () => {
  it("renders nothing while loading", () => {
    mockUseVisibilityProfile.mockReturnValue({ isLoading: true, isError: false, data: null });
    const { container } = render(<VisibilityContextBar />);
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing on error", () => {
    mockUseVisibilityProfile.mockReturnValue({ isLoading: false, isError: true, data: null });
    const { container } = render(<VisibilityContextBar />);
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when source is headers-absent (regression: gateway did not attach visibility headers)", () => {
    mockUseVisibilityProfile.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { source: "headers-absent" },
    });
    const { container } = render(<VisibilityContextBar />);
    expect(container.firstChild).toBeNull();
    expect(screen.queryByText("Gateway did not attach visibility headers")).toBeNull();
  });

  it("renders active session view when source is headers", () => {
    mockUseVisibilityProfile.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        source: "headers",
        visibilityTier: "FULL",
        piiAccess: "ALLOWED",
        clinicalAccess: "ALLOWED",
        exportPolicy: "PERMITTED",
        aggregateOnly: false,
      },
    });
    render(<VisibilityContextBar />);
    expect(screen.getByText("Active session view")).toBeInTheDocument();
  });

  it("renders active session view when source is obligations-or-headers", () => {
    mockUseVisibilityProfile.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        source: "obligations-or-headers",
        visibilityTier: "AGGREGATE_ONLY",
        piiAccess: "DENIED",
        clinicalAccess: "DENIED",
        exportPolicy: "BLOCKED",
        aggregateOnly: true,
      },
    });
    render(<VisibilityContextBar />);
    expect(screen.getByText("Active session view")).toBeInTheDocument();
    expect(screen.getByText(/aggregate-only or restricted/i)).toBeInTheDocument();
  });

  it("shows aggregate-only emphasis when aggregateOnly is true", () => {
    mockUseVisibilityProfile.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        source: "headers",
        visibilityTier: "AGGREGATE_ONLY",
        piiAccess: "DENIED",
        clinicalAccess: "DENIED",
        exportPolicy: "BLOCKED",
        aggregateOnly: true,
      },
    });
    render(<VisibilityContextBar />);
    expect(screen.getByText(/aggregate-only or restricted/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /aggregate workspace/i })).toBeInTheDocument();
  });
});
